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
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import tv.arka.app.ui.ArkaBadge
import tv.arka.app.ui.ArkaChip
import tv.arka.app.ui.ArkaColors
import tv.arka.app.ui.ArkaIcon
import tv.arka.app.ui.ArkaImage
import tv.arka.app.ui.ArkaMotion
import tv.arka.app.ui.ArkaSize
import tv.arka.app.ui.ArkaSpacing
import tv.arka.app.ui.LocalIsTv
import tv.arka.app.ui.PrimaryButton
import tv.arka.app.ui.SecondaryButton
import tv.arka.app.ui.SheetOption
import tv.arka.app.ui.focusHighlight
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.media3.exoplayer.SeekParameters

/** The next episode is offered this long before the end of the one playing. */
private const val NEXT_EPISODE_LEAD_MS = 60_000L

/** Shorter videos (trailers, clips) never show the next-episode card. */
private const val NEXT_EPISODE_MIN_DURATION_MS = 180_000L

/** Seek step of the remote: it grows while the key is held. */
private fun seekStep(repeatCount: Int): Long = when {
    repeatCount < 20 -> 10_000L
    repeatCount < 60 -> 30_000L
    else -> 60_000L
}

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var queue by mutableStateOf<List<PlayItem>>(emptyList())
    private val scope = MainScope()
    private var index by mutableIntStateOf(0)
    private var overlay by mutableStateOf(true)
    private var inPip by mutableStateOf(false)
    private var errorText by mutableStateOf<String?>(null)

    /** Panel open over the picture (tracks, settings, channels or episodes). */
    private val panelState = mutableIntStateOf(PANEL_NONE)

    /** True while something on screen owns the arrows and OK: a panel, a card, an error. */
    private var modal = false

    /** Incremented at each seek made with the remote while the controls are hidden. */
    private var seekTick by mutableIntStateOf(0)

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
        // Remote-control seeking lands on the nearest key frame: much faster on set-top boxes.
        exo.setSeekParameters(SeekParameters.CLOSEST_SYNC)
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
                    if (index < queue.lastIndex) load(index + 1) else finish()
                }
            }
        })

        load(PlayerSession.index.coerceIn(0, queue.lastIndex))

        // An episode resumed on its own: fetch the rest of its season, without touching playback,
        // so that "Épisodes", previous / next and the automatic next episode are available.
        val single = queue.singleOrNull()
        if (single != null && single.key.startsWith("e:")) {
            scope.launch {
                val season = Repo.episodeQueue(single)
                if (season != null && queue.size == 1 && queue[0].key == single.key && !isFinishing) {
                    queue = season.first
                    index = season.second
                }
            }
        }

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
                    panelState = panelState,
                    seekTick = seekTick,
                    onModal = { modal = it },
                    onOverlay = { overlay = it },
                    onBack = { finish() },
                    onPrevious = { load(index - 1) },
                    onNext = { load(index + 1) },
                    onLastChannel = { goToLastChannel() },
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
        val item = queue[index]
        if (item.live) {
            if (previous != index && queue[previous].live) lastChannelIndex = previous
            if (item.key.startsWith("c:")) Repo.recordRecentChannel(item.key.removePrefix("c:"))
        }
        val startOver = PlayerSession.startOver
        PlayerSession.startOver = false
        val resume = if (item.live || startOver) 0L else Repo.progressFor(item.key)?.position ?: 0L
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

    private fun seekBy(delta: Long) {
        val exo = player ?: return
        val duration = exo.duration
        val target = exo.currentPosition + delta
        exo.seekTo(if (duration > 0) target.coerceIn(0, duration) else target.coerceAtLeast(0))
        seekTick++
    }

    /**
     * Remote control. The media keys always act. The arrows and OK belong to whatever has the focus
     * while the controls, a panel or a card are on screen; with a bare picture they are shortcuts:
     * film or episode — left / right seek (faster while held), OK pauses, up shows the controls,
     * down opens the episode selector of a series;
     * live — up / down change channel, left opens the channel list, OK and right show the controls.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val exo = player ?: return super.onKeyDown(keyCode, event)
        val live = queue.getOrNull(index)?.live == true
        val repeat = event?.repeatCount ?: 0
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (repeat == 0) {
                    exo.playWhenReady = !exo.playWhenReady
                    if (!modal) overlay = true
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                exo.playWhenReady = true
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                exo.playWhenReady = false
                if (!modal) overlay = true
                return true
            }
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                finish()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> if (!live) {
                if (repeat % 4 == 0) seekBy(seekStep(repeat))
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> if (!live) {
                if (repeat % 4 == 0) seekBy(-seekStep(repeat))
                return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_CHANNEL_UP -> if (queue.size > 1 && (live || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT)) {
                if (repeat == 0) {
                    load(index + 1)
                    if (!modal) overlay = true
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_CHANNEL_DOWN -> if (queue.size > 1 && (live || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS)) {
                if (repeat == 0) {
                    load(index - 1)
                    if (!modal) overlay = true
                }
                return true
            }
            KeyEvent.KEYCODE_LAST_CHANNEL -> if (live) {
                if (repeat == 0) {
                    goToLastChannel()
                    if (!modal) overlay = true
                }
                return true
            }
        }
        if (overlay || modal) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (repeat == 0) {
                    if (!live) exo.playWhenReady = !exo.playWhenReady
                    overlay = true
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (repeat == 0) {
                    if (live && queue.size > 1) load(index + 1)
                    overlay = true
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (repeat == 0) {
                    when {
                        live && queue.size > 1 -> {
                            load(index - 1)
                            overlay = true
                        }
                        // Down on a series opens the episode selector directly.
                        !live && queue.size > 1 -> panelState.intValue = PANEL_QUEUE
                        else -> overlay = true
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (live) {
                    if (repeat == 0) overlay = true
                } else if (repeat % 4 == 0) {
                    seekBy(seekStep(repeat))
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (live) {
                    if (repeat == 0) {
                        if (queue.size > 1) panelState.intValue = PANEL_QUEUE else overlay = true
                    }
                } else if (repeat % 4 == 0) {
                    seekBy(-seekStep(repeat))
                }
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
        scope.cancel()
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

private fun speedLabel(speed: Float): String = if (speed == 1f) "1×" else speed.toString().removeSuffix(".0") + "×"

private const val PANEL_NONE = 0
private const val PANEL_TRACKS = 1
private const val PANEL_SETTINGS = 2
private const val PANEL_QUEUE = 3

private const val DRAG_NONE = 0
private const val DRAG_SEEK = 1
private const val DRAG_VOLUME = 2
private const val DRAG_BRIGHTNESS = 3

/**
 * Player controls. Minimal on screen, everything else in panels.
 * Touch gestures (handsets): tap shows or hides the controls, double tap seeks 10 s (cumulative),
 * long press plays at 2× while held, horizontal drag seeks, vertical drag sets the volume (right half)
 * or the brightness of the app (left half).
 */
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
    panelState: MutableIntState,
    seekTick: Int,
    onModal: (Boolean) -> Unit,
    onOverlay: (Boolean) -> Unit,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onLastChannel: () -> Unit,
    onRetry: () -> Unit,
    onPip: () -> Unit,
) {
    val tv = LocalIsTv.current
    val context = LocalContext.current
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var zoom by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf<Float?>(null) }
    var panel by panelState
    // Handsets: the screen can be locked against accidental touches.
    var locked by remember { mutableStateOf(false) }
    var unlockTick by remember { mutableIntStateOf(0) }
    var unlockShown by remember { mutableStateOf(false) }
    // The next episode is offered one minute before the end; Back or "Masquer" puts the card away.
    var nextDismissed by remember(item.key) { mutableStateOf(false) }
    var seekFlash by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var speed by remember { mutableFloatStateOf(1f) }
    var activity by remember { mutableIntStateOf(0) }
    // Short feedback for a gesture: "−30 s", "Volume 60 %", "2×"…
    var hint by remember { mutableStateOf<String?>(null) }
    var hintTick by remember { mutableIntStateOf(0) }
    var tapSeek by remember { mutableLongStateOf(0L) }
    val now = rememberNow()

    val overlayNow by rememberUpdatedState(overlay)
    val speedNow by rememberUpdatedState(speed)
    val durationNow by rememberUpdatedState(duration)

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
    val nextVisible = !item.live && nextItem != null && !nextDismissed && !inPip && error == null &&
        panel == PANEL_NONE && duration >= NEXT_EPISODE_MIN_DURATION_MS && duration - position <= NEXT_EPISODE_LEAD_MS
    val controls = overlay && panel == PANEL_NONE && error == null && !locked
    SideEffect { onModal(panel != PANEL_NONE || error != null || nextVisible || locked) }

    // The controls stay while paused, and every key press on the remote restarts the delay.
    LaunchedEffect(overlay, activity, panel, error, playing) {
        if (overlay && panel == PANEL_NONE && error == null && playing) {
            delay(if (tv) 5000 else 3000)
            onOverlay(false)
        }
    }
    LaunchedEffect(seekTick) {
        if (seekTick > 0) {
            position = player.currentPosition.coerceAtLeast(0)
            seekFlash = true
            delay(1500)
            seekFlash = false
        }
    }
    LaunchedEffect(unlockTick) {
        if (unlockTick > 0) {
            unlockShown = true
            delay(3000)
            unlockShown = false
        }
    }
    LaunchedEffect(hintTick) {
        if (hintTick > 0) {
            delay(800)
            hint = null
            tapSeek = 0
        }
    }
    // Back, innermost first: lock, panel, next-episode card, controls (TV), then the player itself.
    BackHandler(enabled = tv && controls) { onOverlay(false) }
    BackHandler(enabled = nextVisible) { nextDismissed = true }
    BackHandler(enabled = panel != PANEL_NONE) {
        panel = PANEL_NONE
        if (tv) onOverlay(true)
    }
    BackHandler(enabled = locked) { unlockTick++ }

    val playFocus = remember { FocusRequester() }
    if (tv && controls && !nextVisible) {
        LaunchedEffect(Unit) {
            try {
                playFocus.requestFocus()
            } catch (e: IllegalStateException) {
                // Controls not laid out yet.
            }
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent {
            if (tv && overlayNow) activity++
            false
        }
    ) {
        AndroidView(
            factory = { viewContext ->
                val view = PlayerView(viewContext)
                view.useController = false
                // The remote is handled by the activity and by the controls, never by the video view.
                view.isFocusable = false
                view.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                view.player = player
                view
            },
            update = { view ->
                view.resizeMode = if (zoom) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Gesture surface, under the controls.
        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    // Two fingers: spread to fill the screen, pinch to fit the picture.
                    awaitEachGesture {
                        var startDistance = 0f
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                val distance = (pressed[0].position - pressed[1].position).getDistance()
                                if (startDistance == 0f) {
                                    startDistance = distance
                                } else if (distance > startDistance * 1.25f && !zoom) {
                                    zoom = true
                                    hint = "Remplir l'écran"
                                    hintTick++
                                } else if (distance < startDistance * 0.8f && zoom) {
                                    zoom = false
                                    hint = "Image entière"
                                    hintTick++
                                }
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
                .pointerInput(item.key, item.live) {
                    var holding = false
                    detectTapGestures(
                        onTap = { onOverlay(!overlayNow) },
                        onDoubleTap = { offset ->
                            if (!item.live) {
                                val step = if (offset.x < size.width / 2) -10_000L else 10_000L
                                // Taps in the same direction add up; changing side starts again.
                                tapSeek = if ((tapSeek < 0) == (step < 0)) tapSeek + step else step
                                player.seekTo((player.currentPosition + step).coerceAtLeast(0))
                                hint = (if (tapSeek < 0) "−" else "+") + (kotlin.math.abs(tapSeek) / 1000) + " s"
                                hintTick++
                            }
                        },
                        onLongPress = {
                            if (!item.live) {
                                holding = true
                                player.setPlaybackSpeed(2f)
                                hint = "2×"
                            }
                        },
                        onPress = {
                            tryAwaitRelease()
                            if (holding) {
                                holding = false
                                player.setPlaybackSpeed(speedNow)
                                hint = null
                            }
                        },
                    )
                }
                .pointerInput(item.key, item.live) {
                    var mode = DRAG_NONE
                    var totalX = 0f
                    var totalY = 0f
                    var startPosition = 0L
                    var startVolume = 0
                    var startBrightness = 0.5f
                    var rightHalf = false
                    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    val window = (context as? Activity)?.window
                    detectDragGestures(
                        onDragStart = { start ->
                            mode = DRAG_NONE
                            totalX = 0f
                            totalY = 0f
                            rightHalf = start.x > size.width / 2
                            startPosition = player.currentPosition
                            startVolume = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                            val current = window?.attributes?.screenBrightness ?: -1f
                            startBrightness = if (current < 0f) 0.5f else current
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            totalX += amount.x
                            totalY += amount.y
                            if (mode == DRAG_NONE) {
                                mode = when {
                                    kotlin.math.abs(totalX) > kotlin.math.abs(totalY) -> if (item.live) DRAG_NONE else DRAG_SEEK
                                    rightHalf -> DRAG_VOLUME
                                    else -> DRAG_BRIGHTNESS
                                }
                            }
                            when (mode) {
                                DRAG_SEEK -> {
                                    // The full width of the screen covers two minutes.
                                    val delta = (totalX / size.width * 120_000f).toLong()
                                    val target = (startPosition + delta).coerceIn(0, durationNow.coerceAtLeast(0))
                                    val sign = if (delta < 0) "−" else "+"
                                    hint = formatDuration(target) + "  " + sign + formatDuration(kotlin.math.abs(delta))
                                }
                                DRAG_VOLUME -> {
                                    if (audio != null) {
                                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                        val level = (startVolume - totalY / size.height * max * 1.5f).roundToInt().coerceIn(0, max)
                                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
                                        hint = "Volume ${level * 100 / max} %"
                                    }
                                }
                                DRAG_BRIGHTNESS -> {
                                    if (window != null) {
                                        val level = (startBrightness - totalY / size.height * 1.5f).coerceIn(0.02f, 1f)
                                        val attributes = window.attributes
                                        attributes.screenBrightness = level
                                        window.attributes = attributes
                                        hint = "Luminosité ${(level * 100).roundToInt()} %"
                                    }
                                }
                                else -> {}
                            }
                        },
                        onDragEnd = {
                            if (mode == DRAG_SEEK) {
                                val delta = (totalX / size.width * 120_000f).toLong()
                                player.seekTo((startPosition + delta).coerceIn(0, durationNow.coerceAtLeast(0)))
                            }
                            mode = DRAG_NONE
                            hint = null
                        },
                        onDragCancel = {
                            mode = DRAG_NONE
                            hint = null
                        },
                    )
                }
        )

        if (locked) {
            // Swallows every touch; a tap only offers to unlock.
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { unlockTick++ }) })
            if (unlockShown && !inPip) {
                PlayerPill("Déverrouiller", Modifier.align(Alignment.BottomCenter).padding(bottom = ArkaSpacing.x3), light = true) {
                    locked = false
                    unlockShown = false
                    onOverlay(true)
                }
            }
        }

        if (buffering && error == null) {
            CircularProgressIndicator(color = ArkaColors.Red, modifier = Modifier.align(Alignment.Center))
        }

        if (controls) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.75f),
                        0.3f to Color.Transparent,
                        0.6f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.85f),
                    )
                )
            )

            // Top: back, title, and the few actions that are always needed.
            Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = if (tv) ArkaSize.tvOverscan else ArkaSpacing.md, vertical = ArkaSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!tv) {
                    Box(
                        Modifier.size(ArkaSize.touchMin).clip(CircleShape).clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) { ArkaIcon(R.drawable.ic_arka_retour, "Retour") }
                }
                Column(Modifier.weight(1f).padding(horizontal = ArkaSpacing.sm)) {
                    if (item.live) {
                        Text(
                            "● EN DIRECT",
                            color = Color.White,
                            style = ArkaTheme.type.mono,
                            modifier = Modifier.clip(RoundedCornerShape(ArkaRadius.xs)).background(ArkaColors.Red).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Text(item.title, color = Color.White, style = ArkaTheme.type.heading, fontWeight = FontWeight(700), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!item.live && item.subtitle.isNotBlank()) {
                        Text(item.subtitle, color = ArkaColors.TextSecondary, style = ArkaTheme.type.label, fontWeight = FontWeight(500), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (!tv) {
                    Row(horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm)) {
                        if (item.live && hasQueue) PlayerPill("Chaînes") { panel = PANEL_QUEUE }
                        PlayerPill("Audio · ST") { panel = PANEL_TRACKS }
                        PlayerPill("⋯") { panel = PANEL_SETTINGS }
                    }
                }
            }

            // Centre (handsets): −10, play / pause, +10.
            if (!tv) {
                Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.x3),
                ) {
                    if (!item.live) {
                        RoundControl(R.drawable.ic_arka_reculer_10, "Reculer de 10 secondes") {
                            activity++
                            player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                        }
                    }
                    Box(
                        Modifier.size(72.dp).clip(CircleShape).background(ArkaColors.TextPrimary).clickable {
                            activity++
                            player.playWhenReady = !player.playWhenReady
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        ArkaIcon(
                            if (playing) R.drawable.ic_arka_pause else R.drawable.ic_arka_lire,
                            if (playing) "Pause" else "Lecture",
                            tint = ArkaColors.Background,
                            size = 32.dp,
                        )
                    }
                    if (!item.live) {
                        RoundControl(R.drawable.ic_arka_avancer_10, "Avancer de 10 secondes") {
                            activity++
                            player.seekTo(player.currentPosition + 10_000)
                        }
                    }
                }
            }

            // Bottom: timeline for films and episodes, programme for live channels.
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .padding(horizontal = if (tv) ArkaSize.tvOverscan else ArkaSpacing.lg, vertical = if (tv) ArkaSpacing.xl else ArkaSpacing.sm),
            ) {
                if (item.live) {
                    val programs = Repo.programs(item.epgId)
                    val current = programs.firstOrNull { it.start <= now && now < it.end }
                    if (current != null) {
                        Text(current.title, color = Color.White, style = ArkaTheme.type.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = ArkaSpacing.sm)) {
                            Text(formatClock(current.start), color = ArkaColors.TextTertiary, style = ArkaTheme.type.mono)
                            val span = (current.end - current.start).coerceAtLeast(1)
                            LinearProgressIndicator(
                                progress = { ((now - current.start).toFloat() / span).coerceIn(0f, 1f) },
                                color = ArkaColors.Red,
                                trackColor = Color.White.copy(alpha = 0.25f),
                                modifier = Modifier.weight(1f).padding(horizontal = ArkaSpacing.md).height(3.dp),
                            )
                            Text(formatClock(current.end), color = ArkaColors.TextTertiary, style = ArkaTheme.type.mono)
                        }
                        val following = programs.firstOrNull { it.start >= current.end }
                        if (following != null) {
                            Text(
                                "Ensuite · " + formatClock(following.start) + " " + following.title,
                                color = ArkaColors.TextSecondary,
                                style = ArkaTheme.type.label,
                                fontWeight = FontWeight(500),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = ArkaSpacing.xs),
                            )
                        }
                    }
                } else if (duration > 0 && tv) {
                    TvSeekBar(position, duration) { delta ->
                        val target = (player.currentPosition + delta).coerceIn(0, duration)
                        player.seekTo(target)
                        position = target
                    }
                } else if (duration > 0) {
                    val fraction = drag ?: (position.toFloat() / duration).coerceIn(0f, 1f)
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
                            thumbColor = ArkaColors.TextPrimary,
                            activeTrackColor = ArkaColors.Red,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = ArkaSpacing.xs)) {
                    if (!item.live && duration > 0) {
                        val fraction = drag ?: (position.toFloat() / duration).coerceIn(0f, 1f)
                        Text(formatDuration((fraction * duration).toLong()), color = Color.White, style = ArkaTheme.type.mono)
                        Text("  / " + formatDuration(duration), color = ArkaColors.TextSecondary, style = ArkaTheme.type.mono)
                    }
                    if (tv) {
                        Spacer(Modifier.width(ArkaSpacing.xl))
                        Row(horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            if (hasQueue) PlayerPill(if (item.live) "‹ Chaîne" else "‹ Précédent") { activity++; onPrevious() }
                            if (!item.live) PlayerPill("−10") { activity++; player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) }
                            PlayerPill(if (playing) "Pause" else "Lecture", Modifier.focusRequester(playFocus), light = true) {
                                activity++
                                player.playWhenReady = !player.playWhenReady
                            }
                            if (!item.live) PlayerPill("+10") { activity++; player.seekTo(player.currentPosition + 10_000) }
                            if (hasQueue) PlayerPill(if (item.live) "Chaîne ›" else "Suivant ›") { activity++; onNext() }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        if (item.live && hasLastChannel) PlayerPill("Chaîne précédente") { activity++; onLastChannel() }
                        if (!item.live && hasQueue) PlayerPill("Épisodes") { panel = PANEL_QUEUE }
                        if (!tv && !item.live && nextItem != null) PlayerPill("Suivant ›") { activity++; onNext() }
                        if (!tv) PlayerPill(if (zoom) "⤢ Remplir" else "⤢ Format") { zoom = !zoom; activity++ }
                        if (tv) {
                            if (item.live && hasQueue) PlayerPill("Chaînes") { panel = PANEL_QUEUE }
                            PlayerPill("Audio · ST") { panel = PANEL_TRACKS }
                            PlayerPill("Réglages") { panel = PANEL_SETTINGS }
                        }
                    }
                }
            }
        }

        if (seekFlash && !controls && !item.live && duration > 0 && panel == PANEL_NONE && !inPip) {
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f)))
                    .padding(horizontal = if (tv) ArkaSize.tvOverscan else ArkaSpacing.lg, vertical = ArkaSpacing.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatDuration(position), color = Color.White, style = ArkaTheme.type.mono)
                LinearProgressIndicator(
                    progress = { (position.toFloat() / duration).coerceIn(0f, 1f) },
                    color = ArkaColors.Red,
                    trackColor = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.weight(1f).padding(horizontal = ArkaSpacing.md).height(4.dp),
                )
                Text(formatDuration(duration), color = ArkaColors.TextSecondary, style = ArkaTheme.type.mono)
            }
        }

        if (hint != null && !inPip) {
            Text(
                hint.orEmpty(),
                color = Color.White,
                style = ArkaTheme.type.heading,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp)
                    .clip(RoundedCornerShape(ArkaRadius.pill)).background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = ArkaSpacing.lg, vertical = ArkaSpacing.sm),
            )
        }

        if (showStats && !inPip) {
            Column(
                Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = ArkaSpacing.md)
                    .clip(RoundedCornerShape(ArkaRadius.md)).background(Color.Black.copy(alpha = 0.75f)).padding(ArkaSpacing.md),
            ) {
                Text("Diagnostics", color = Color.White, style = ArkaTheme.type.label, modifier = Modifier.padding(bottom = 4.dp))
                stats.forEach { (label, value) ->
                    Row {
                        Text(label, color = ArkaColors.TextSecondary, style = ArkaTheme.type.mono, modifier = Modifier.width(110.dp))
                        Text(value, color = Color.White, style = ArkaTheme.type.mono)
                    }
                }
            }
        }

        if (error != null && !inPip) {
            Column(
                Modifier.align(Alignment.Center).widthIn(max = 440.dp)
                    .clip(RoundedCornerShape(ArkaRadius.lg)).background(ArkaColors.BackgroundAlt.copy(alpha = 0.95f)).padding(ArkaSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ArkaIcon(R.drawable.ic_arka_alerte, null, tint = ArkaColors.Warning, size = 28.dp)
                Text(error.substringBefore('\n'), color = Color.White, style = ArkaTheme.type.body, textAlign = TextAlign.Center, modifier = Modifier.padding(top = ArkaSpacing.sm))
                Text(error.substringAfter('\n', ""), color = ArkaColors.TextSecondary, style = ArkaTheme.type.mono, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = ArkaSpacing.lg), horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm)) {
                    FocusOnShow(tv) { PrimaryButton("Réessayer", onRetry) }
                    if (item.live && hasQueue) SecondaryButton("Chaîne suivante", onNext)
                    SecondaryButton("Fermer", onBack)
                }
            }
        }

        if (nextVisible && nextItem != null) {
            val remaining = ((duration - position) / 1000).coerceAtLeast(0)
            Row(
                Modifier.align(Alignment.BottomEnd)
                    .padding(end = if (tv) ArkaSize.tvOverscan else ArkaSpacing.xl, bottom = if (controls) 120.dp else ArkaSpacing.xl)
                    .widthIn(max = 440.dp)
                    .clip(RoundedCornerShape(ArkaRadius.md)).background(ArkaColors.BackgroundAlt.copy(alpha = 0.95f)).padding(ArkaSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(110.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(ArkaRadius.sm)).background(ArkaColors.Surface)) {
                    if (nextItem.poster.isNotBlank()) ArkaImage(nextItem.poster, Modifier.fillMaxSize())
                }
                Column(Modifier.padding(start = ArkaSpacing.md)) {
                    Text("Épisode suivant dans $remaining s", color = ArkaColors.TextSecondary, style = ArkaTheme.type.mono)
                    Text(
                        nextItem.subtitle.ifBlank { nextItem.title },
                        color = Color.White,
                        style = ArkaTheme.type.label,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(Modifier.padding(top = ArkaSpacing.sm), horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm)) {
                        // On TV the card takes the focus only when the controls are not being used.
                        FocusOnShow(tv && !controls) { PrimaryButton("Lire maintenant", onNext) }
                        SecondaryButton("Masquer", { nextDismissed = true })
                    }
                }
            }
        }

        // ----- Panels

        if (panel != PANEL_NONE && !inPip) {
            // Touching outside a panel closes it.
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
                    .pointerInput(Unit) { detectTapGestures(onTap = { panel = PANEL_NONE }) }
            )
        }

        if (panel == PANEL_TRACKS && !inPip) {
            val audio = trackOptions(player, C.TRACK_TYPE_AUDIO)
            val subtitles = trackOptions(player, C.TRACK_TYPE_TEXT)
            SidePanel(Modifier.align(Alignment.CenterEnd), widthFraction = 0.5f) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Text("Audio", color = Color.White, style = ArkaTheme.type.label, modifier = Modifier.padding(bottom = ArkaSpacing.sm))
                        if (audio.isEmpty()) Text("Aucune piste", color = ArkaColors.TextSecondary, style = ArkaTheme.type.body)
                        audio.forEachIndexed { place, option ->
                            FocusOnShow(tv && (option.selected || (place == 0 && audio.none { it.selected }))) {
                                SheetOption(option.label, option.selected, onClick = {
                                    selectTrack(player, C.TRACK_TYPE_AUDIO, option)
                                    panel = PANEL_NONE
                                })
                            }
                        }
                    }
                    Spacer(Modifier.width(ArkaSpacing.lg))
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Text("Sous-titres", color = Color.White, style = ArkaTheme.type.label, modifier = Modifier.padding(bottom = ArkaSpacing.sm))
                        FocusOnShow(tv && audio.isEmpty()) {
                            SheetOption("Désactivés", subtitles.none { it.selected }, onClick = {
                                selectTrack(player, C.TRACK_TYPE_TEXT, null)
                                panel = PANEL_NONE
                            })
                        }
                        subtitles.forEach { option ->
                            SheetOption(option.label, option.selected, onClick = {
                                selectTrack(player, C.TRACK_TYPE_TEXT, option)
                                panel = PANEL_NONE
                            })
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = ArkaSpacing.md), horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm)) {
                    val automatic = automaticQuality(player)
                    SecondaryButton("Qualité : " + if (automatic) "Auto" else "Manuelle", { panel = PANEL_SETTINGS }, Modifier.weight(1f))
                    if (!item.live) {
                        SecondaryButton("Vitesse : " + speedLabel(speed), { panel = PANEL_SETTINGS }, Modifier.weight(1f))
                    }
                }
            }
        }

        if (panel == PANEL_SETTINGS && !inPip) {
            val qualities = videoOptions(player)
            val automatic = automaticQuality(player)
            SidePanel(Modifier.align(Alignment.CenterEnd), widthFraction = 0.42f) {
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                    if (!item.live) {
                        Text("Vitesse", color = Color.White, style = ArkaTheme.type.label)
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(vertical = ArkaSpacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm),
                        ) {
                            speeds.forEach { value -> ArkaChip(speedLabel(value), value == speed) { speed = value } }
                        }
                    }
                    // Only offered when the stream really carries several video qualities.
                    if (qualities.size > 1) {
                        Text("Qualité", color = Color.White, style = ArkaTheme.type.label, modifier = Modifier.padding(top = ArkaSpacing.sm))
                        SheetOption("Automatique", automatic, onClick = { clearVideoOverride(player) })
                        qualities.forEach { option ->
                            SheetOption(option.label, !automatic && option.selected, onClick = { selectTrack(player, C.TRACK_TYPE_VIDEO, option) })
                        }
                    }
                    Text("Affichage", color = Color.White, style = ArkaTheme.type.label, modifier = Modifier.padding(top = ArkaSpacing.sm))
                    FocusOnShow(tv) { SheetOption("Remplir l'écran", zoom, onClick = { zoom = !zoom }) }
                    if (!tv) {
                        SheetOption("Verrouiller l'écran", false, onClick = {
                            panel = PANEL_NONE
                            locked = true
                            unlockTick++
                            onOverlay(false)
                        })
                    }
                    SheetOption("Diagnostics", showStats, onClick = { showStats = !showStats })
                    SheetOption("Mini-lecteur", false, onClick = {
                        panel = PANEL_NONE
                        onPip()
                    })
                }
            }
        }

        if (panel == PANEL_QUEUE && !inPip) {
            if (item.live) {
                // Quick zapping: the list of the group being watched, on the left.
                SidePanel(Modifier.align(Alignment.CenterStart), widthFraction = 0.36f) {
                    Text("CHAÎNES", color = ArkaColors.TextSecondary, style = ArkaTheme.type.mono, modifier = Modifier.padding(bottom = ArkaSpacing.sm))
                    LazyColumn(state = rememberLazyListState(initialFirstVisibleItemIndex = index)) {
                        itemsIndexed(queue) { place, entry ->
                            val onAir = Repo.nowPlaying(entry.epgId, now)?.title
                            FocusOnShow(tv && place == index) {
                                SheetOption(entry.title + if (onAir != null) " · $onAir" else "", place == index, onClick = {
                                    panel = PANEL_NONE
                                    if (place != index) onSelect(place)
                                })
                            }
                        }
                    }
                }
            } else {
                // Episode selector: thumbnails along the bottom, the one playing is marked.
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(ArkaColors.BackgroundAlt.copy(alpha = 0.97f))
                        .padding(vertical = ArkaSpacing.lg),
                ) {
                    Text(
                        item.title,
                        color = Color.White,
                        style = ArkaTheme.type.label,
                        modifier = Modifier.padding(horizontal = ArkaSpacing.xl, vertical = ArkaSpacing.xs),
                    )
                    LazyRow(
                        state = rememberLazyListState(initialFirstVisibleItemIndex = index),
                        contentPadding = PaddingValues(horizontal = ArkaSpacing.xl, vertical = ArkaSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.md),
                    ) {
                        itemsIndexed(queue) { place, entry ->
                            Column(Modifier.width(170.dp)) {
                                FocusOnShow(tv && place == index) {
                                Box(
                                    Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                                        .focusHighlight()
                                        .clip(RoundedCornerShape(ArkaRadius.sm))
                                        .background(ArkaColors.Surface)
                                        .clickable {
                                            panel = PANEL_NONE
                                            if (place != index) onSelect(place)
                                        },
                                ) {
                                    if (entry.poster.isNotBlank()) ArkaImage(entry.poster, Modifier.fillMaxSize())
                                    when {
                                        place == index -> Text(
                                            "EN COURS",
                                            color = Color.White,
                                            style = ArkaTheme.type.mono,
                                            modifier = Modifier.padding(6.dp).clip(RoundedCornerShape(ArkaRadius.xs))
                                                .background(ArkaColors.Red).padding(horizontal = 5.dp, vertical = 1.dp),
                                        )
                                        entry.key in Repo.watched -> ArkaBadge("VU", Modifier.padding(6.dp))
                                        else -> {}
                                    }
                                }
                                }
                                Text(
                                    entry.subtitle.ifBlank { entry.title },
                                    color = if (place == index) Color.White else ArkaColors.TextSecondary,
                                    style = ArkaTheme.type.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A small translucent action of the player bar. [light] marks the one that holds the initial focus on TV. */
@Composable
private fun PlayerPill(text: String, modifier: Modifier = Modifier, light: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .defaultMinSize(minHeight = if (LocalIsTv.current) ArkaSize.tvControlMin else 36.dp)
            .focusHighlight(ArkaRadius.md, ArkaMotion.FOCUS_SCALE_BUTTON)
            .clip(RoundedCornerShape(ArkaRadius.md))
            .background(if (light) ArkaColors.TextPrimary else ArkaColors.SurfaceHigh.copy(alpha = 0.85f))
            .clickable(onClick = onClick)
            .padding(horizontal = ArkaSpacing.md, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (light) ArkaColors.Background else Color.White, style = ArkaTheme.type.label, maxLines = 1)
    }
}

/** Outlined round control around the main play / pause button (handsets). */
@Composable
private fun RoundControl(icon: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ArkaIcon(icon, description, tint = Color.White, size = 24.dp)
    }
}

/** A panel that slides over one side of the picture; the rest of the screen closes it. */
@Composable
private fun SidePanel(modifier: Modifier, widthFraction: Float, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxHeight().fillMaxWidth(widthFraction)
            .background(ArkaColors.BackgroundAlt.copy(alpha = 0.97f))
            // Swallow touches so that they do not reach the scrim underneath.
            .pointerInput(Unit) { detectTapGestures(onTap = {}) }
            .padding(ArkaSpacing.xl),
        content = content,
    )
}

/**
 * Gives the remote-control focus to [content] as soon as it is on screen, so that a panel or a card
 * opened on TV is immediately usable. Does nothing when [enabled] is false.
 */
@Composable
private fun FocusOnShow(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
    } else {
        val requester = remember { FocusRequester() }
        Box(Modifier.focusRequester(requester)) { content() }
        LaunchedEffect(Unit) {
            try {
                requester.requestFocus()
            } catch (e: IllegalStateException) {
                // Not laid out yet: the first arrow press will give the focus.
            }
        }
    }
}

/**
 * Timeline for the remote: when focused, left and right seek, faster while the key is held.
 * [onSeek] receives the signed step in milliseconds.
 */
@Composable
private fun TvSeekBar(position: Long, duration: Long, onSeek: (Long) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fraction = (position.toFloat() / duration.coerceAtLeast(1)).coerceIn(0f, 1f)
    val thickness = if (focused) 6.dp else 3.dp
    Box(
        Modifier.fillMaxWidth().height(24.dp)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                val direction = when (event.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> 0
                }
                if (direction == 0) {
                    false
                } else {
                    if (event.type == KeyEventType.KeyDown) {
                        val repeat = event.nativeKeyEvent.repeatCount
                        if (repeat % 4 == 0) onSeek(direction * seekStep(repeat))
                    }
                    true
                }
            }
            .focusable(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(thickness).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)))
        Box(Modifier.fillMaxWidth(fraction).height(thickness).clip(CircleShape).background(ArkaColors.Red))
        if (focused) {
            Box(Modifier.fillMaxWidth(fraction), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(Color.White))
            }
        }
    }
}
