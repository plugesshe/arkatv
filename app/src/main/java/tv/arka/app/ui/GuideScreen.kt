package tv.arka.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tv.arka.app.data.Channel
import tv.arka.app.data.Program
import tv.arka.app.data.Repo

private const val GUIDE_HOURS = 12
private const val DP_PER_MINUTE = 4f

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

private fun programState(program: Program, now: Long): String = when {
    now >= program.end -> "TERMINÉ"
    now >= program.start -> "EN COURS"
    else -> "À VENIR"
}

/**
 * Programme guide: a time grid with a red "now" line, past programmes dimmed, the programme on air
 * lighter. Replay and recording marks are absent because the app offers neither yet.
 */
@Composable
fun GuideScreen() {
    val context = LocalContext.current
    val tv = LocalIsTv.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var category by rememberSaveable { mutableStateOf(CATEGORY_ALL) }
    val channels = Repo.channels
    val recent = Repo.recentChannels
    val hidden = Repo.hiddenCategories
    val hiddenChannels = Repo.hiddenChannels
    val favoriteOrder = Repo.favoriteOrder
    val list = remember(channels, category, favoriteOrder, recent, hidden, hiddenChannels) {
        channelsFor(category, channels, favoriteOrder, recent, hidden, hiddenChannels)
    }
    val now = rememberNow()
    val start = remember { (System.currentTimeMillis() / 1000 - 1800) / 1800 * 1800 }
    val end = start + GUIDE_HOURS * 3600
    val scroll = rememberScrollState()
    var info by remember { mutableStateOf<Pair<Channel, Program>?>(null) }
    var focused by remember { mutableStateOf<Pair<Channel, Program>?>(null) }

    val channelColumn = if (tv) 96.dp else 72.dp
    val rowHeight = if (tv) 44.dp else 60.dp
    val nowOffset = with(density) { widthFor((now - start).coerceIn(0, GUIDE_HOURS * 3600L)).toPx() }
    val lineWidth = with(density) { 2.dp.toPx() }
    val backToNow: () -> Unit = {
        scope.launch { scroll.animateScrollTo((nowOffset - with(density) { 48.dp.toPx() }).toInt().coerceAtLeast(0)) }
    }

    Column(Modifier.fillMaxSize()) {
        if (tv) {
            // Details of the programme under focus, as on the TV mock-up.
            val shown = focused?.takeIf { it.first in list }
            Column(Modifier.fillMaxWidth().height(118.dp).padding(horizontal = ArkaSpacing.xl, vertical = ArkaSpacing.md)) {
                if (shown != null) {
                    val (channel, program) = shown
                    Text(
                        channel.name.uppercase() + " · " + formatClock(program.start) + " – " + formatClock(program.end) +
                            " · " + programState(program, now),
                        color = ArkaColors.TextSecondary,
                        style = ArkaTheme.type.mono,
                        maxLines = 1,
                    )
                    Text(
                        program.title.uppercase(),
                        fontFamily = ArkaDisplayFont,
                        fontWeight = FontWeight(800),
                        fontSize = 28.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (program.desc.isNotBlank()) {
                        Text(program.desc, color = ArkaColors.TextTertiary, style = ArkaTheme.type.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Text("GUIDE TV", fontFamily = ArkaDisplayFont, fontWeight = FontWeight(800), fontSize = 28.sp)
                    if (Repo.epgLoading) Text("Mise à jour du guide…", color = ArkaColors.TextSecondary, style = ArkaTheme.type.body)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(end = ArkaTheme.margin), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ScreenHeader("Guide TV", onBack = null) }
                SecondaryButton("Maintenant", backToNow)
            }
            if (Repo.epgLoading) {
                Text(
                    "Mise à jour du guide…",
                    color = ArkaColors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = ArkaTheme.margin),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { CategoryChips(category) { category = it } }
            if (tv) {
                Box(Modifier.padding(end = ArkaSpacing.xl)) { SecondaryButton("Revenir à maintenant", backToNow) }
            }
        }

        if (list.isEmpty()) {
            LiveStatus()
        } else {
            Row(Modifier.fillMaxWidth().padding(top = ArkaSpacing.sm)) {
                Spacer(Modifier.width(channelColumn))
                Row(Modifier.horizontalScroll(scroll)) {
                    for (slot in 0 until GUIDE_HOURS * 2) {
                        Text(
                            formatClock(start + slot * 1800L),
                            color = ArkaColors.TextSecondary,
                            style = ArkaTheme.type.mono,
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
                        Row(
                            Modifier.horizontalScroll(scroll).drawWithContent {
                                drawContent()
                                // The "now" line runs through every row at the same position.
                                drawLine(
                                    ArkaColors.Red,
                                    Offset(nowOffset, 0f),
                                    Offset(nowOffset, size.height),
                                    strokeWidth = lineWidth,
                                )
                            }
                        ) {
                            val cells = guideCells(Repo.programs(channel.epgId), start, end)
                            if (cells.isEmpty()) {
                                ProgramCell(
                                    title = "Pas d'information",
                                    time = "guide indisponible",
                                    width = widthFor(GUIDE_HOURS * 3600L),
                                    state = CELL_FUTURE,
                                    onClick = { playChannel(context, list, channel) },
                                    onFocused = { focused = null },
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
                                            time = formatClock(program.start),
                                            width = widthFor(cell.seconds),
                                            state = if (current) CELL_CURRENT else if (now >= program.end) CELL_PAST else CELL_FUTURE,
                                            onClick = {
                                                if (current) {
                                                    playChannel(context, list, channel)
                                                } else {
                                                    info = channel to program
                                                }
                                            },
                                            onFocused = { focused = channel to program },
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
        ArkaSheet(program.title, onDismiss = { info = null }) {
            Text(
                channel.name.uppercase() + " · " + formatClock(program.start) + " – " + formatClock(program.end) +
                    " · " + programState(program, now),
                color = ArkaColors.TextSecondary,
                style = ArkaTheme.type.mono,
            )
            if (program.desc.isNotBlank()) {
                Text(
                    program.desc,
                    color = ArkaColors.TextTertiary,
                    style = ArkaTheme.type.body,
                    modifier = Modifier.padding(top = ArkaSpacing.sm),
                )
            }
            SecondaryButton(
                "Aller à la chaîne",
                {
                    info = null
                    playChannel(context, list, channel)
                },
                modifier = Modifier.padding(top = ArkaSpacing.lg).fillMaxWidth(),
            )
        }
    }
}

private const val CELL_PAST = 0
private const val CELL_CURRENT = 1
private const val CELL_FUTURE = 2

@Composable
private fun ProgramCell(title: String, time: String, width: Dp, state: Int, onClick: () -> Unit, onFocused: () -> Unit) {
    val shape = RoundedCornerShape(ArkaRadius.sm)
    Box(
        Modifier.width(width).fillMaxHeight().padding(2.dp)
            .focusHighlight(ArkaRadius.sm, scale = 1f)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .clip(shape)
            .then(
                when (state) {
                    CELL_CURRENT -> Modifier.background(ArkaColors.SurfaceHigh)
                    CELL_PAST -> Modifier.border(1.dp, ArkaColors.SurfaceHigh, shape)
                    else -> Modifier.background(ArkaColors.Surface)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
            // Past programmes are dimmed, not hidden: they are still part of the day.
            .alpha(if (state == CELL_PAST) 0.55f else 1f),
    ) {
        Column {
            Text(
                title,
                style = ArkaTheme.type.label,
                fontWeight = if (state == CELL_CURRENT) FontWeight(700) else FontWeight(500),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(time, color = ArkaColors.TextSecondary, style = ArkaTheme.type.mono, maxLines = 1)
        }
    }
}
