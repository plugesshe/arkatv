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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
import tv.arka.app.ui.ArkaMuted
import tv.arka.app.ui.ArkaRed
import tv.arka.app.ui.ArkaSurface
import tv.arka.app.ui.ArkaTheme
import tv.arka.app.ui.formatClock
import tv.arka.app.ui.formatDuration
import tv.arka.app.ui.rememberNow
import java.util.Locale

class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var queue: List<PlayItem> = emptyList()
    private var index by mutableIntStateOf(0)
    private var overlay by mutableStateOf(true)
    private var inPip by mutableStateOf(false)
    private var errorText by mutableStateOf<String?>(null)
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
                    else -> errorText = "Lecture impossible (${error.errorCodeName})"
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) errorText = null
                if (playbackState == Player.STATE_ENDED && !queue[index].live) {
                    if (index < queue.lastIndex) load(index + 1) else finish()
                }
            }
        })

        load(PlayerSession.index.coerceIn(0, queue.lastIndex))

        setContent {
            ArkaTheme {
                PlayerScreen(
                    player = exo,
                    item = queue[index],
                    hasQueue = queue.size > 1,
                    overlay = overlay && !inPip,
                    error = errorText,
                    onOverlay = { overlay = it },
                    onBack = { finish() },
                    onPrevious = { load(index - 1) },
                    onNext = { load(index + 1) },
                    onPip = { enterPip() },
                )
            }
        }
    }

    private fun load(target: Int) {
        val exo = player ?: return
        persist()
        index = ((target % queue.size) + queue.size) % queue.size
        triedAlternative = false
        errorText = null
        val item = queue[index]
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

    /** Xtream live streams exist as both .ts and .m3u8: try the other one when the first fails. */
    private fun alternativeUrl(url: String): String? = when {
        "/live/" !in url -> null
        url.endsWith(".ts") -> url.removeSuffix(".ts") + ".m3u8"
        url.endsWith(".m3u8") -> url.removeSuffix(".m3u8") + ".ts"
        else -> null
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

@Composable
private fun PlayerScreen(
    player: ExoPlayer,
    item: PlayItem,
    hasQueue: Boolean,
    overlay: Boolean,
    error: String?,
    onOverlay: (Boolean) -> Unit,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPip: () -> Unit,
) {
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var zoom by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf<Float?>(null) }
    var showTracks by remember { mutableStateOf(false) }
    var activity by remember { mutableIntStateOf(0) }
    val now = rememberNow()

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0)
            duration = player.duration.coerceAtLeast(0)
            playing = player.playWhenReady
            buffering = player.playbackState == Player.STATE_BUFFERING
            delay(400)
        }
    }
    LaunchedEffect(overlay, activity, showTracks) {
        if (overlay && !showTracks) {
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

        if (buffering && error == null) {
            CircularProgressIndicator(color = ArkaRed, modifier = Modifier.align(Alignment.Center))
        }
        if (error != null) {
            Text(
                error,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.7f)).padding(16.dp),
            )
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
                    val program = if (item.live) Repo.nowPlaying(item.epgId, now) else null
                    val subtitle = when {
                        program != null -> formatClock(program.start) + " – " + formatClock(program.end) + " · " + program.title
                        else -> item.subtitle
                    }
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, color = ArkaMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showTracks = true }) {
                        Icon(Icons.Filled.Subtitles, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("Audio et sous-titres", color = Color.White, fontSize = 13.sp)
                    }
                    TextButton(onClick = {
                        zoom = !zoom
                        activity++
                    }) {
                        Icon(Icons.Filled.AspectRatio, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text(if (zoom) "Plein cadre" else "Format d'origine", color = Color.White, fontSize = 13.sp)
                    }
                    TextButton(onClick = onPip) {
                        Icon(Icons.Filled.PictureInPictureAlt, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("Mini-lecteur", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    if (showTracks) {
        val audio = trackOptions(player, C.TRACK_TYPE_AUDIO)
        val subtitles = trackOptions(player, C.TRACK_TYPE_TEXT)
        AlertDialog(
            onDismissRequest = { showTracks = false },
            containerColor = ArkaSurface,
            title = { Text("Audio et sous-titres") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Audio", color = ArkaMuted, fontSize = 12.sp)
                    if (audio.isEmpty()) Text("Aucune piste disponible", fontSize = 14.sp)
                    audio.forEach { option ->
                        TrackLine(option.label, option.selected) {
                            selectTrack(player, C.TRACK_TYPE_AUDIO, option)
                            showTracks = false
                        }
                    }
                    Text("Sous-titres", color = ArkaMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                    TrackLine("Désactivés", subtitles.none { it.selected }) {
                        selectTrack(player, C.TRACK_TYPE_TEXT, null)
                        showTracks = false
                    }
                    subtitles.forEach { option ->
                        TrackLine(option.label, option.selected) {
                            selectTrack(player, C.TRACK_TYPE_TEXT, option)
                            showTracks = false
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTracks = false }) { Text("Fermer") } },
        )
    }
}

@Composable
private fun TrackLine(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        (if (selected) "● " else "○ ") + label,
        color = if (selected) ArkaRed else Color.White,
        fontSize = 15.sp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
    )
}

@Composable
private fun ControlButton(icon: ImageVector, description: String, large: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(if (large) 72.dp else 52.dp)) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(if (large) 56.dp else 36.dp))
    }
}
