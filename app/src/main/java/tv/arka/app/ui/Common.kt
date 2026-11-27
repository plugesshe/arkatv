package tv.arka.app.ui

import android.content.Context
import android.content.Intent
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

@Composable
fun Logo(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("ARKA", color = ArkaRed, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 22.sp)
        Text("TV", color = Color.White, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 22.sp)
    }
}

@Composable
fun SectionTitle(text: String, onMore: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onMore != null) {
            Text(
                "Tout voir",
                color = ArkaMuted,
                fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onMore).padding(6.dp),
            )
        }
    }
}

@Composable
fun Poster(vod: Vod, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .focusHighlight()
            .clip(RoundedCornerShape(ArkaRadius.md))
            .background(ArkaCard)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            vod.name,
            color = ArkaMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(6.dp),
        )
        if (vod.poster.isNotBlank()) {
            AsyncImage(
                model = vod.poster,
                contentDescription = vod.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun VodRow(title: String, items: List<Vod>, onOpen: (Vod) -> Unit, onMore: (() -> Unit)? = null) {
    Column {
        SectionTitle(title, onMore)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items) { vod ->
                Poster(vod, Modifier.width(112.dp)) { onOpen(vod) }
            }
        }
    }
}

@Composable
fun ChannelLogo(url: String, name: String, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(6.dp)).background(ArkaCard),
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
            Text(name.take(3).uppercase(), color = ArkaMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
