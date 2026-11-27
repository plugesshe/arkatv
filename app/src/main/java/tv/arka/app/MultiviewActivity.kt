package tv.arka.app

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import tv.arka.app.data.Http
import tv.arka.app.data.MultiviewPicks
import tv.arka.app.data.PlayItem
import tv.arka.app.data.PlayerSession
import tv.arka.app.data.Store
import tv.arka.app.ui.ArkaColors
import tv.arka.app.ui.ArkaTheme
import tv.arka.app.ui.isTelevision

/**
 * Two to four live channels on screen at once. The sound comes from the selected one; the arrows
 * (or a touch) change the selection, OK opens the selected channel full screen. Each channel uses
 * one connection of the provider account and one video decoder of the device: when either runs
 * out, the tile says so instead of showing a picture.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class MultiviewActivity : ComponentActivity() {
    private val players = ArrayList<ExoPlayer>()
    private var items: List<PlayItem> = emptyList()
    private var active by mutableIntStateOf(0)
    private val errors = mutableStateListOf<String?>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        items = MultiviewPicks.channels.take(MultiviewPicks.MAX)
        if (items.size < 2) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setUserAgent(Http.USER_AGENT)
        items.forEachIndexed { place, item ->
            errors.add(null)
            val player = ExoPlayer.Builder(this)
                .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)))
                .build()
            player.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    errors[place] = "Lecture impossible\n" + error.errorCodeName
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) errors[place] = null
                }
            })
            player.volume = if (place == 0) 1f else 0f
            player.setMediaItem(MediaItem.fromUri(item.url))
            player.prepare()
            player.playWhenReady = true
            players.add(player)
        }
        val television = isTelevision()
        setContent {
            ArkaTheme(isTv = television) {
                MultiviewScreen(items, players, errors, active) { select(it) }
            }
        }
    }

    private fun select(place: Int) {
        if (place !in players.indices) return
        active = place
        players.forEachIndexed { index, player -> player.volume = if (index == place) 1f else 0f }
    }

    /** Full screen on the selected channel: the multi-view closes first so that its decoders are free. */
    private fun openSelected() {
        PlayerSession.startOver = false
        PlayerSession.queue = items
        PlayerSession.index = active
        startActivity(Intent(this, PlayerActivity::class.java))
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val columns = 2
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (active % columns > 0) select(active - 1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (active % columns < columns - 1) select(active + 1)
            KeyEvent.KEYCODE_DPAD_UP -> select(active - columns)
            KeyEvent.KEYCODE_DPAD_DOWN -> select(active + columns)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> openSelected()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    override fun onStart() {
        super.onStart()
        players.forEach { it.playWhenReady = true }
    }

    override fun onStop() {
        super.onStop()
        players.forEach { it.playWhenReady = false }
    }

    override fun onDestroy() {
        super.onDestroy()
        players.forEach { it.release() }
        players.clear()
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun MultiviewScreen(
    items: List<PlayItem>,
    players: List<ExoPlayer>,
    errors: List<String?>,
    active: Int,
    onSelect: (Int) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Color.Black).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        items.indices.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { place ->
                    val selected = place == active
                    Box(
                        Modifier.weight(1f).aspectRatio(16f / 9f)
                            .border(if (selected) 3.dp else 1.dp, if (selected) ArkaColors.FocusOutline else ArkaColors.Border)
                            .clickable { onSelect(place) },
                    ) {
                        AndroidView(
                            factory = { context ->
                                val view = PlayerView(context)
                                view.useController = false
                                view.isFocusable = false
                                view.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                                view.player = players[place]
                                view
                            },
                            modifier = Modifier.fillMaxSize().padding(3.dp),
                        )
                        Text(
                            (if (selected) "🔊 " else "") + items[place].title,
                            color = Color.White,
                            style = ArkaTheme.type.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                        val error = errors.getOrNull(place)
                        if (error != null) {
                            Text(
                                error,
                                color = Color.White,
                                style = ArkaTheme.type.label,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.7f)).padding(8.dp),
                            )
                        }
                    }
                }
                // An odd number of channels leaves the last cell empty, so tiles keep the same size.
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}
