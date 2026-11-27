@file:OptIn(ExperimentalMaterial3Api::class)

package tv.arka.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.arka.app.data.Channel
import tv.arka.app.data.Program
import tv.arka.app.data.Repo

private const val GUIDE_HOURS = 12
private const val DP_PER_MINUTE = 4f
private val channelColumn = 76.dp
private val rowHeight = 60.dp

private fun widthFor(seconds: Long): Dp = (seconds / 60f * DP_PER_MINUTE).dp

/** One slot of a guide row: a programme, or a gap when [program] is null. */
private class GuideCell(val program: Program?, val seconds: Long)

private fun guideCells(programs: List<Program>, start: Long, end: Long): List<GuideCell> {
    val cells = ArrayList<GuideCell>()
    var cursor = start
    for (program in programs) {
        val from = maxOf(program.start, cursor)
        val to = minOf(program.end, end)
        if (to <= from) continue
        if (from > cursor) cells.add(GuideCell(null, from - cursor))
        cells.add(GuideCell(program, to - from))
        cursor = to
    }
    if (cells.isNotEmpty() && cursor < end) cells.add(GuideCell(null, end - cursor))
    return cells
}

@Composable
fun GuideScreen() {
    val context = LocalContext.current
    var category by rememberSaveable { mutableStateOf(CATEGORY_ALL) }
    val channels = Repo.channels
    val favorites = Repo.favorites
    val list = remember(channels, category, favorites) { channelsFor(category, channels, favorites) }
    val now = rememberNow()
    val start = remember { (System.currentTimeMillis() / 1000 - 1800) / 1800 * 1800 }
    val end = start + GUIDE_HOURS * 3600
    val scroll = rememberScrollState()
    var info by remember { mutableStateOf<Pair<Channel, Program>?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Guide TV", fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            if (Repo.epgLoading) Text("Mise à jour du guide…", color = ArkaMuted, fontSize = 12.sp)
        }
        CategoryChips(category) { category = it }

        if (list.isEmpty()) {
            LiveStatus()
        } else {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Spacer(Modifier.width(channelColumn))
                Row(Modifier.horizontalScroll(scroll)) {
                    for (slot in 0 until GUIDE_HOURS * 2) {
                        Text(
                            formatClock(start + slot * 1800L),
                            color = ArkaMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.width(widthFor(1800)).padding(start = 6.dp, bottom = 6.dp),
                        )
                    }
                }
            }

            LazyColumn(Modifier.fillMaxSize()) {
                items(list) { channel ->
                    Row(Modifier.fillMaxWidth().height(rowHeight)) {
                        Box(
                            Modifier.width(channelColumn).fillMaxHeight()
                                .clickable { playChannel(context, list, channel) }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        ) {
                            ChannelLogo(channel.logo, channel.name, Modifier.fillMaxSize())
                        }
                        Row(Modifier.horizontalScroll(scroll)) {
                            val cells = guideCells(Repo.programs(channel.epgId), start, end)
                            if (cells.isEmpty()) {
                                ProgramCell(
                                    title = channel.name,
                                    time = "Programme indisponible",
                                    width = widthFor(GUIDE_HOURS * 3600L),
                                    current = false,
                                    onClick = { playChannel(context, list, channel) },
                                )
                            } else {
                                cells.forEach { cell ->
                                    val program = cell.program
                                    if (program == null) {
                                        Spacer(Modifier.width(widthFor(cell.seconds)))
                                    } else {
                                        val current = program.start <= now && now < program.end
                                        ProgramCell(
                                            title = program.title,
                                            time = formatClock(program.start) + " – " + formatClock(program.end),
                                            width = widthFor(cell.seconds),
                                            current = current,
                                            onClick = {
                                                if (current) {
                                                    playChannel(context, list, channel)
                                                } else {
                                                    info = channel to program
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    info?.let { (channel, program) ->
        AlertDialog(
            onDismissRequest = { info = null },
            containerColor = ArkaSurface,
            title = { Text(program.title) },
            text = {
                Column {
                    Text(
                        channel.name + " · " + formatClock(program.start) + " – " + formatClock(program.end),
                        color = ArkaRed,
                        fontSize = 13.sp,
                    )
                    if (program.desc.isNotBlank()) {
                        Text(program.desc, color = ArkaMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    info = null
                    playChannel(context, list, channel)
                }) { Text("Regarder la chaîne") }
            },
            dismissButton = { TextButton(onClick = { info = null }) { Text("Fermer") } },
        )
    }
}

@Composable
private fun ProgramCell(title: String, time: String, width: Dp, current: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.width(width).fillMaxHeight().padding(2.dp)
            .focusHighlight(RoundedCornerShape(ArkaRadius.sm), scale = 1f)
            .clip(RoundedCornerShape(ArkaRadius.sm))
            .background(if (current) ArkaRedDark else ArkaCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Column {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(time, color = ArkaMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
