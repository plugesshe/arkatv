package tv.arka.app

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import tv.arka.app.data.Http
import tv.arka.app.data.PlayItem
import tv.arka.app.data.PlayerSession
import tv.arka.app.data.Repo
import tv.arka.app.data.Store
import tv.arka.app.ui.ArkaCard
import tv.arka.app.ui.ArkaMuted
import tv.arka.app.ui.ArkaRadius
import tv.arka.app.ui.ArkaRed
import tv.arka.app.ui.ArkaSurface
import tv.arka.app.ui.ArkaTheme
import tv.arka.app.ui.formatClock
import tv.arka.app.ui.isTelevision
import tv.arka.app.ui.formatDuration
import tv.arka.app.ui.rememberNow
import java.util.Locale
import kotlin.math.roundToInt

private const val NEXT_EPISODE_DELAY_S = 8

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var queue: List<PlayItem> = emptyList()
    private var index by mutableIntStateOf(0)
    private var overlay by mutableStateOf(true)
    private var inPip by mutableStateOf(false)
    private var errorText by mutableStateOf<String?>(null)

    /** True once an episode has ended and the next one is about to start. */
    private var pendingNext by mutableStateOf(false)

    /** Position in the queue of the live channel watched just before the current one. */
    private var lastChannelIndex by mutableIntStateOf(-1)
    private var triedAlternative = false
    private var currentUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        queue = PlayerSession.queue
        if (queue.isEmpty()) {
            finish()
            return
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(Http.USER_AGENT)
        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)))
            .build()
        player = exo

        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val item = queue[index]
                val alternative = alternativeUrl(currentUrl)
                when {
                    error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                        exo.seekToDefaultPosition()
                        exo.prepare()
                    }
                    item.live && !triedAlternative && alternative != null -> {
                        triedAlternative = true
                        start(alternative, 0)
                    }
                    else -> {
                        errorText = describe(error)
                        overlay = true
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) errorText = null
                if (playbackState == Player.STATE_ENDED && !queue[index].live) {
                    persist()
                    if (index < queue.lastIndex) pendingNext = true else finish()
                }
            }
        })

        load(PlayerSession.index.coerceIn(0, queue.lastIndex))

        val television = isTelevision()
        setContent {
            ArkaTheme(isTv = television) {
                PlayerScreen(
                    player = exo,
                    item = queue[index],
                    nextItem = queue.getOrNull(index + 1),
                    queue = queue,
                    index = index,
                    onSelect = { load(it) },
                    hasQueue = queue.size > 1,
                    hasLastChannel = lastChannelIndex in queue.indices && lastChannelIndex != index,
                    overlay = overlay && !inPip,
                    inPip = inPip,
                    error = errorText,
                    pendingNext = pendingNext,
                    onOverlay = { overlay = it },
                    onBack = { finish() },
                    onPrevious = { load(index - 1) },
                    onNext = { load(index + 1) },
                    onLastChannel = { goToLastChannel() },
                    onCancelNext = { pendingNext = false },
                    onRetry = { retry() },
                    onPip = { enterPip() },
                )
            }
        }
    }

    private fun load(target: Int) {
        val exo = player ?: return
        persist()
        val previous = index
        index = ((target % queue.size) + queue.size) % queue.size
        triedAlternative = false
        errorText = null
        pendingNext = false
        val item = queue[index]
        if (item.live) {
            if (previous != index && queue[previous].live) lastChannelIndex = previous
            if (item.key.startsWith("c:")) Repo.recordRecentChannel(item.key.removePrefix("c:"))
        }
        val resume = if (item.live) 0L else Repo.progressFor(item.key)?.position ?: 0L
        start(item.url, resume)
        exo.playWhenReady = true
    }

    private fun start(url: String, position: Long) {
        val exo = player ?: return
        currentUrl = url
        exo.setMediaItem(MediaItem.fromUri(url), position)
        exo.prepare()
        exo.playWhenReady = true
    }

    private fun retry() {
        val exo = player ?: return
        val item = queue[index]
        triedAlternative = false
        errorText = null
        start(item.url, if (item.live) 0L else exo.currentPosition.coerceAtLeast(0))
    }

    private fun goToLastChannel() {
        if (lastChannelIndex in queue.indices && lastChannelIndex != index) load(lastChannelIndex)
    }

    /** Xtream live streams exist as both .ts and .m3u8: try the other one when the first fails. */
    private fun alternativeUrl(url: String): String? = when {
        "/live/" !in url -> null
        url.endsWith(".ts") -> url.removeSuffix(".ts") + ".m3u8"
        url.endsWith(".m3u8") -> url.removeSuffix(".m3u8") + ".ts"
        else -> null
    }

    /** A message the viewer can act on; the technical code stays visible for support. */
    private fun describe(error: PlaybackException): String {
        val message = when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            -> "Connexion au fournisseur impossible. Vérifiez votre réseau."
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            -> "Le fournisseur refuse ce flux ou ne le propose plus."
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            -> "Ce flux est dans un format que le lecteur ne sait pas lire."
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            -> "Cet appareil ne peut pas décoder cette vidéo."
            else -> "Lecture impossible."
        }
        return message + "\n" + error.errorCodeName
    }

    private fun persist() {
        val exo = player ?: return
        val item = queue.getOrNull(index) ?: return
        if (item.live) return
        val duration = exo.duration
        if (duration > 0) Repo.saveProgress(item, exo.currentPosition, duration)
    }

    private fun enterPip() {
        try {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            )
        } catch (e: Exception) {
            // Picture-in-picture is not available on every device (some TVs).
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (player?.isPlaying == true) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val exo = player ?: return super.onKeyDown(keyCode, event)
        val live = queue.getOrNull(index)?.live == true
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (!overlay) overlay = true else exo.playWhenReady = !exo.playWhenReady
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_DPAD_UP -> if (live) {
                load(index + 1)
                overlay = true
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> if (live) {
                load(index - 1)
                overlay = true
                return true
            }
            KeyEvent.KEYCODE_LAST_CHANNEL -> if (live) {
                goToLastChannel()
                overlay = true
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> if (!live) {
                exo.seekTo(exo.currentPosition + 10_000)
                overlay = true
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> if (!live) {
                exo.seekTo((exo.currentPosition - 10_000).coerceAtLeast(0))
                overlay = true
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        super.onStop()
        persist()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}

private class TrackOption(val label: String, val group: Tracks.Group, val index: Int, val selected: Boolean)

@androidx.annotation.OptIn(UnstableApi::class)
private fun trackOptions(player: Player, type: Int): List<TrackOption> {
    val options = ArrayList<TrackOption>()
    for (group in player.currentTracks.groups) {
        if (group.type != type) continue
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            val format = group.getTrackFormat(i)
            val language = format.language?.let { Locale.forLanguageTag(it).displayLanguage }?.ifBlank { null }
            val label = listOfNotNull(language, format.label).distinct().joinToString(" · ")
                .ifBlank { "Piste ${options.size + 1}" }
            options.add(TrackOption(label, group, i, group.isTrackSelected(i)))
        }
    }
    return options
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun selectTrack(player: Player, type: Int, option: TrackOption?) {
    val builder = player.trackSelectionParameters.buildUpon()
    if (option == null) {
        builder.setTrackTypeDisabled(type, true)
    } else {
        builder.setTrackTypeDisabled(type, false)
        builder.setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.index))
    }
    player.trackSelectionParameters = builder.build()
}

/** Video qualities carried by the stream itself (adaptive streams), best first. */
@androidx.annotation.OptIn(UnstableApi::class)
private fun videoOptions(player: Player): List<TrackOption> {
    val options = ArrayList<Pair<Int, TrackOption>>()
    for (group in player.currentTracks.groups) {
        if (group.type != C.TRACK_TYPE_VIDEO) continue
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            val format = group.getTrackFormat(i)
            val label = listOfNotNull(
                if (format.height > 0) "${format.height}p" else null,
                if (format.bitrate > 0) "${format.bitrate / 1000} kb/s" else null,
            ).joinToString(" · ").ifBlank { "Piste ${options.size + 1}" }
            options.add(format.height to TrackOption(label, group, i, group.isTrackSelected(i)))
        }
    }
    return options.sortedByDescending { it.first }.map { it.second }
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun automaticQuality(player: Player): Boolean =
    player.trackSelectionParameters.overrides.keys.none { it.type == C.TRACK_TYPE_VIDEO }

@androidx.annotation.OptIn(UnstableApi::class)
private fun clearVideoOverride(player: Player) {
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        .build()
}

/** What the player actually reports about the stream being decoded: nothing is guessed. */
@androidx.annotation.OptIn(UnstableApi::class)
private fun diagnostics(player: ExoPlayer, item: PlayItem): List<Pair<String, String>> {
    val video = player.videoFormat
    val audio = player.audioFormat
    val videoText = if (video == null) {
        "—"
    } else {
        listOfNotNull(
            video.sampleMimeType ?: video.codecs,
            if (video.width > 0 && video.height > 0) "${video.width}×${video.height}" else null,
            if (video.frameRate > 0) "${video.frameRate.roundToInt()} i/s" else null,
            if (video.bitrate > 0) "${video.bitrate / 1000} kb/s" else null,
        ).joinToString(" · ").ifBlank { "—" }
    }
    val audioText = if (audio == null) {
        "—"
    } else {
        listOfNotNull(
            audio.sampleMimeType ?: audio.codecs,
            if (audio.channelCount > 0) "${audio.channelCount} canaux" else null,
            if (audio.sampleRate > 0) "${audio.sampleRate} Hz" else null,
            if (audio.bitrate > 0) "${audio.bitrate / 1000} kb/s" else null,
        ).joinToString(" · ").ifBlank { "—" }
    }
    val extension = item.url.substringBefore('?').substringAfterLast('/', "").substringAfterLast('.', "")
    return listOf(
        "Flux" to (if (item.live) "Direct" else "À la demande") + if (extension.isNotBlank()) " · $extension" else "",
        "Vidéo" to videoText,
        "Audio" to audioText,
        "Tampon" to "${player.totalBufferedDuration / 1000} s",
        "Images perdues" to (player.videoDecoderCounters?.droppedBufferCount ?: 0).toString(),
    )
}

private val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

private fun speedLabel(speed: Float): String = if (speed == 1f) "Normale" else "×" + speed.toString().removeSuffix(".0")

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun PlayerScreen(
    player: ExoPlayer,
    item: PlayItem,
    nextItem: PlayItem?,
    queue: List<PlayItem>,
    index: Int,
    onSelect: (Int) -> Unit,
    hasQueue: Boolean,
    hasLastChannel: Boolean,
    overlay: Boolean,
    inPip: Boolean,
    error: String?,
    pendingNext: Boolean,
    onOverlay: (Boolean) -> Unit,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onLastChannel: () -> Unit,
    onCancelNext: () -> Unit,
    onRetry: () -> Unit,
    onPip: () -> Unit,
) {
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var zoom by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf<Float?>(null) }
    var showTracks by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var speed by remember { mutableFloatStateOf(1f) }
    var activity by remember { mutableIntStateOf(0) }
    val now = rememberNow()

    LaunchedEffect(player, showStats, item.key) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0)
            duration = player.duration.coerceAtLeast(0)
            playing = player.playWhenReady
            buffering = player.playbackState == Player.STATE_BUFFERING
            if (showStats) stats = diagnostics(player, item)
            delay(400)
        }
    }
    LaunchedEffect(item.live, speed) {
        // Live streams always play at normal speed.
        player.setPlaybackSpeed(if (item.live) 1f else speed)
    }
    val dialogOpen = showTracks || showSpeed || showQueue
    LaunchedEffect(overlay, activity, dialogOpen, error, pendingNext) {
        if (overlay && !dialogOpen && error == null && !pendingNext) {
            delay(5000)
            onOverlay(false)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { context ->
                val view = PlayerView(context)
                view.useController = false
                view.player = player
                view
            },
            update = { view ->
                view.resizeMode = if (zoom) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onOverlay(!overlay) }
        )

        if (buffering && error == null && !pendingNext) {
            CircularProgressIndicator(color = ArkaRed, modifier = Modifier.align(Alignment.Center))
        }

        if (overlay) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
            )

            Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val programs = if (item.live) Repo.programs(item.epgId) else emptyList()
                    val current = programs.firstOrNull { it.start <= now && now < it.end }
                    if (current != null) {
                        Text(
                            formatClock(current.start) + " – " + formatClock(current.end) + " · " + current.title,
                            color = Color.White,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val span = (current.end - current.start).coerceAtLeast(1)
                        LinearProgressIndicator(
                            progress = { ((now - current.start).toFloat() / span).coerceIn(0f, 1f) },
                            color = ArkaRed,
                            trackColor = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.padding(vertical = 4.dp).widthIn(max = 320.dp).fillMaxWidth().height(2.dp),
                        )
                        val following = programs.firstOrNull { it.start >= current.end }
                        if (following != null) {
                            Text(
                                "Ensuite : " + formatClock(following.start) + " · " + following.title,
                                color = ArkaMuted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else if (item.subtitle.isNotBlank()) {
                        Text(item.subtitle, color = ArkaMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (item.live) {
                    Text(
                        "EN DIRECT",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 12.dp).background(ArkaRed).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            if (error == null && !pendingNext) {
                Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    if (hasQueue) {
                        ControlButton(Icons.Filled.SkipPrevious, "Précédent") {
                            activity++
                            onPrevious()
                        }
                    }
                    if (!item.live) {
                        ControlButton(Icons.Filled.Replay10, "Reculer de 10 secondes") {
                            activity++
                            player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                        }
                    }
                    ControlButton(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Lecture / pause", large = true) {
                        activity++
                        player.playWhenReady = !player.playWhenReady
                    }
                    if (!item.live) {
                        ControlButton(Icons.Filled.Forward10, "Avancer de 10 secondes") {
                            activity++
                            player.seekTo(player.currentPosition + 10_000)
                        }
                    }
                    if (hasQueue) {
                        ControlButton(Icons.Filled.SkipNext, "Suivant") {
                            activity++
                            onNext()
                        }
                    }
                }
            }

            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (!item.live && duration > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val fraction = drag ?: (position.toFloat() / duration).coerceIn(0f, 1f)
                        Text(formatDuration((fraction * duration).toLong()), color = Color.White, fontSize = 12.sp)
                        Slider(
                            value = fraction,
                            onValueChange = {
                                drag = it
                                activity++
                            },
                            onValueChangeFinished = {
                                drag?.let { player.seekTo((it * duration).toLong()) }
                                drag = null
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = ArkaRed,
                                activeTrackColor = ArkaRed,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                            ),
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                        )
                        Text(formatDuration(duration), color = Color.White, fontSize = 12.sp)
                    }
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.live && hasLastChannel) {
                        OptionButton(Icons.Filled.SwapHoriz, "Chaîne précédente") {
                            activity++
                            onLastChannel()
                        }
                    }
                    if (hasQueue) {
                        OptionButton(Icons.Filled.FormatListBulleted, if (item.live) "Chaînes" else "Épisodes") { showQueue = true }
                    }
                    OptionButton(Icons.Filled.Subtitles, "Audio, sous-titres, qualité") { showTracks = true }
                    if (!item.live) {
                        OptionButton(Icons.Filled.Speed, "Vitesse : " + speedLabel(speed)) { showSpeed = true }
                    }
                    OptionButton(Icons.Filled.AspectRatio, if (zoom) "Plein cadre" else "Format d'origine") {
                        zoom = !zoom
                        activity++
                    }
                    OptionButton(Icons.Filled.Info, if (showStats) "Masquer les infos" else "Infos techniques") {
                        showStats = !showStats
                        activity++
                    }
                    OptionButton(Icons.Filled.PictureInPictureAlt, "Mini-lecteur", onPip)
                }
            }
        }

        if (showStats && !inPip) {
            Column(
                Modifier.align(Alignment.TopEnd).padding(top = 64.dp, end = 12.dp)
                    .clip(RoundedCornerShape(ArkaRadius.md)).background(Color.Black.copy(alpha = 0.7f)).padding(10.dp),
            ) {
                stats.forEach { (label, value) ->
                    Row {
                        Text(label, color = ArkaMuted, fontSize = 11.sp, modifier = Modifier.width(96.dp))
                        Text(value, color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }

        if (error != null && !inPip) {
            Column(
                Modifier.align(Alignment.Center).widthIn(max = 420.dp)
                    .clip(RoundedCornerShape(ArkaRadius.lg)).background(Color.Black.copy(alpha = 0.8f)).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(error.substringBefore('\n'), color = Color.White, textAlign = TextAlign.Center)
                Text(error.substringAfter('\n', ""), color = ArkaMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = ArkaRed, contentColor = Color.White),
                    ) { Text("Réessayer") }
                    if (item.live && hasQueue) {
                        Button(
                            onClick = onNext,
                            colors = ButtonDefaults.buttonColors(containerColor = ArkaCard, contentColor = Color.White),
                        ) { Text("Chaîne suivante") }
                    }
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = ArkaCard, contentColor = Color.White),
                    ) { Text("Fermer") }
                }
            }
        }

        if (pendingNext && nextItem != null && !inPip) {
            var remaining by remember(nextItem.key) { mutableIntStateOf(NEXT_EPISODE_DELAY_S) }
            LaunchedEffect(nextItem.key) {
                while (remaining > 0) {
                    delay(1000)
                    remaining--
                }
                onNext()
            }
            Column(
                Modifier.align(Alignment.BottomEnd).padding(24.dp).widthIn(max = 360.dp)
                    .clip(RoundedCornerShape(ArkaRadius.lg)).background(Color.Black.copy(alpha = 0.85f)).padding(16.dp),
            ) {
                Text("Épisode suivant dans $remaining s", color = Color.White, fontWeight = FontWeight.Bold)
                if (nextItem.subtitle.isNotBlank()) {
                    Text(nextItem.subtitle, color = ArkaMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onNext,
                        colors = ButtonDefaults.buttonColors(containerColor = ArkaRed, contentColor = Color.White),
                    ) { Text("Lire maintenant") }
                    Button(
                        onClick = onCancelNext,
                        colors = ButtonDefaults.buttonColors(containerColor = ArkaCard, contentColor = Color.White),
                    ) { Text("Annuler") }
                }
            }
        }
    }

    if (showTracks) {
        val audio = trackOptions(player, C.TRACK_TYPE_AUDIO)
        val subtitles = trackOptions(player, C.TRACK_TYPE_TEXT)
        val qualities = videoOptions(player)
        val automatic = automaticQuality(player)
        AlertDialog(
            onDismissRequest = { showTracks = false },
            containerColor = ArkaSurface,
            title = { Text("Audio, sous-titres, qualité") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Audio", color = ArkaMuted, fontSize = 12.sp)
                    if (audio.isEmpty()) Text("Aucune piste disponible", fontSize = 14.sp)
                    audio.forEach { option ->
                        ChoiceLine(option.label, option.selected) {
                            selectTrack(player, C.TRACK_TYPE_AUDIO, option)
                            showTracks = false
                        }
                    }
                    Text("Sous-titres", color = ArkaMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                    ChoiceLine("Désactivés", subtitles.none { it.selected }) {
                        selectTrack(player, C.TRACK_TYPE_TEXT, null)
                        showTracks = false
                    }
                    subtitles.forEach { option ->
                        ChoiceLine(option.label, option.selected) {
                            selectTrack(player, C.TRACK_TYPE_TEXT, option)
                            showTracks = false
                        }
                    }
                    // Only offered when the stream really carries several video qualities.
                    if (qualities.size > 1) {
                        Text("Qualité", color = ArkaMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                        ChoiceLine("Automatique", automatic) {
                            clearVideoOverride(player)
                            showTracks = false
                        }
                        qualities.forEach { option ->
                            ChoiceLine(option.label, !automatic && option.selected) {
                                selectTrack(player, C.TRACK_TYPE_VIDEO, option)
                                showTracks = false
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTracks = false }) { Text("Fermer") } },
        )
    }

    if (showQueue) {
        AlertDialog(
            onDismissRequest = { showQueue = false },
            containerColor = ArkaSurface,
            title = { Text(if (item.live) "Chaînes" else "Épisodes") },
            text = {
                LazyColumn(state = rememberLazyListState(initialFirstVisibleItemIndex = index)) {
                    itemsIndexed(queue) { position, entry ->
                        val label = if (entry.live) entry.title else entry.subtitle.ifBlank { entry.title }
                        ChoiceLine(label, position == index) {
                            showQueue = false
                            if (position != index) onSelect(position)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showQueue = false }) { Text("Fermer") } },
        )
    }

    if (showSpeed) {
        AlertDialog(
            onDismissRequest = { showSpeed = false },
            containerColor = ArkaSurface,
            title = { Text("Vitesse de lecture") },
            text = {
                Column {
                    speeds.forEach { value ->
                        ChoiceLine(speedLabel(value), value == speed) {
                            speed = value
                            showSpeed = false
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSpeed = false }) { Text("Fermer") } },
        )
    }
}

@Composable
private fun ChoiceLine(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        (if (selected) "● " else "○ ") + label,
        color = if (selected) ArkaRed else Color.White,
        fontSize = 15.sp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
    )
}

@Composable
private fun OptionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, contentDescription = null, tint = Color.White)
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun ControlButton(icon: ImageVector, description: String, large: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(if (large) 72.dp else 52.dp)) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(if (large) 56.dp else 36.dp))
    }
}
