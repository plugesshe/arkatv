package tv.arka.app.ui

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import tv.arka.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import tv.arka.app.PlayerActivity
import tv.arka.app.data.Channel
import tv.arka.app.data.PlayItem
import tv.arka.app.data.PlayerSession
import tv.arka.app.data.Vod
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val hourMinute: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun formatClock(epochSeconds: Long): String =
    Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).format(hourMinute)

private val dayHourMinute: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

fun formatDateTime(epochSeconds: Long): String =
    Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).format(dayHourMinute)

private val dayOnly: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

fun formatDate(epochSeconds: Long): String =
    Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).format(dayOnly)

fun formatDuration(millis: Long): String {
    val total = millis / 1000
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

/** Current time in epoch seconds, refreshed every 30 seconds. */
@Composable
fun rememberNow(): Long {
    val now by produceState(initialValue = System.currentTimeMillis() / 1000) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis() / 1000
        }
    }
    return now
}

/** True on Android TV and other leanback devices driven by a remote control. */
fun Context.isTelevision(): Boolean {
    val uiMode = (getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType
    return uiMode == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}

fun Channel.toPlayItem(): PlayItem =
    PlayItem(key = "c:$id", title = name, subtitle = "", url = url, live = true, poster = logo, epgId = epgId)

fun Vod.toPlayItem(): PlayItem =
    PlayItem(key = "m:$id", title = name, subtitle = "", url = url, live = false, poster = poster)

fun play(context: Context, queue: List<PlayItem>, index: Int) {
    if (queue.isEmpty()) return
    PlayerSession.queue = queue
    PlayerSession.index = index.coerceIn(0, queue.lastIndex)
    context.startActivity(Intent(context, PlayerActivity::class.java))
}

fun playChannel(context: Context, list: List<Channel>, channel: Channel) {
    val index = list.indexOf(channel).coerceAtLeast(0)
    val queue = if (list.isEmpty()) listOf(channel.toPlayItem()) else list.map { it.toPlayItem() }
    play(context, queue, index)
}

/** The arch mark, optionally followed by the wordmark (Archivo 800 at 112 % width, "TV" lighter). */
@Composable
fun Logo(modifier: Modifier = Modifier, wordmark: Boolean = true, height: Dp = 28.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.arka_mark), contentDescription = "ArkaTV", modifier = Modifier.size(height))
        if (wordmark) {
            Spacer(Modifier.width(8.dp))
            Text("ARKA", color = ArkaColors.TextPrimary, fontFamily = ArkaLogoFont, fontWeight = FontWeight(800), fontSize = 20.sp)
            Text("TV", color = ArkaColors.TextSecondary, fontFamily = ArkaLogoFont, fontWeight = FontWeight(500), fontSize = 20.sp)
        }
    }
}

@Composable
fun SectionTitle(text: String, onMore: (() -> Unit)? = null) {
    val margin = ArkaTheme.margin
    Row(
        Modifier.fillMaxWidth().padding(start = margin, end = margin, top = ArkaTheme.rowGap, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text,
            style = ArkaTheme.type.heading,
            fontWeight = FontWeight(700),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onMore != null) {
            Text(
                "Tout",
                color = ArkaColors.TextSecondary,
                style = ArkaTheme.type.label,
                modifier = Modifier.focusHighlight(ArkaRadius.sm, ArkaMotion.FOCUS_SCALE_BUTTON)
                    .clip(RoundedCornerShape(ArkaRadius.sm)).clickable(onClick = onMore).padding(8.dp),
            )
        }
    }
}

/** "2024 · ★ 7,8" — only the parts the source actually provides. */
fun vodMeta(vod: Vod): String {
    val rating = vod.rating.replace(',', '.').toFloatOrNull()?.takeIf { it > 0f }
    return listOfNotNull(
        vod.year.takeIf { it.isNotBlank() },
        rating?.let { "★ " + String.format(Locale.FRANCE, "%.1f", it) },
    ).joinToString(" · ")
}

/**
 * Poster card: 2:3 image, title and metadata underneath so the artwork stays clean.
 * Without an image, the title is set typographically on the surface colour.
 */
@Composable
fun Poster(vod: Vod, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .focusHighlight()
                .clip(RoundedCornerShape(ArkaRadius.sm))
                .background(ArkaColors.Surface)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.BottomStart,
        ) {
            Text(
                vod.name.uppercase(),
                color = ArkaColors.TextSecondary,
                fontFamily = ArkaDisplayFont,
                fontWeight = FontWeight(800),
                fontSize = 18.sp,
                lineHeight = 18.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
            if (vod.poster.isNotBlank()) {
                AsyncImage(
                    model = vod.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            vod.name,
            style = ArkaTheme.type.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        val meta = vodMeta(vod)
        if (meta.isNotBlank()) {
            Text(
                meta,
                color = ArkaColors.TextSecondary,
                fontSize = if (LocalIsTv.current) 11.sp else 12.sp,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Width of a poster in a horizontal row. */
val posterWidth: Dp
    @Composable get() = if (LocalIsTv.current) 104.dp else 112.dp

@Composable
fun VodRow(title: String, items: List<Vod>, onOpen: (Vod) -> Unit, onMore: (() -> Unit)? = null) {
    val width = posterWidth
    Column {
        SectionTitle(title, onMore)
        LazyRow(
            // Vertical padding leaves room for the focus outline and enlargement.
            contentPadding = PaddingValues(horizontal = ArkaTheme.margin, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(if (LocalIsTv.current) 14.dp else 10.dp),
        ) {
            items(items) { vod ->
                Poster(vod, Modifier.width(width)) { onOpen(vod) }
            }
        }
    }
}

@Composable
fun ChannelLogo(url: String, name: String, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(ArkaRadius.sm)).background(ArkaColors.Surface),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
            )
        } else {
            Text(name.take(3).uppercase(), color = ArkaColors.TextSecondary, style = ArkaTheme.type.label)
        }
    }
}
