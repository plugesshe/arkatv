@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package tv.arka.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.arka.app.data.Channel
import tv.arka.app.data.ChannelGroup
import tv.arka.app.data.Repo

const val CATEGORY_ALL = "*all*"
const val CATEGORY_FAVORITES = "*fav*"
const val CATEGORY_RECENT = "*recent*"

/** Prefix of the category id of a group made by the user. */
const val CATEGORY_GROUP = "*group*:"

/** Group filter shared by the live list and the guide. The last chip opens the group manager. */
@Composable
fun CategoryChips(selected: String, onSelect: (String) -> Unit) {
    var managing by remember { mutableStateOf(false) }
    val hidden = Repo.hiddenCategories
    val visible = Repo.liveCategories.filter { it.id !in hidden }
    LazyRow(
        contentPadding = PaddingValues(horizontal = ArkaTheme.margin, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ArkaChip("★ Favoris", selected == CATEGORY_FAVORITES) { onSelect(CATEGORY_FAVORITES) } }
        if (Repo.recentChannels.isNotEmpty()) {
            item { ArkaChip("Récentes", selected == CATEGORY_RECENT) { onSelect(CATEGORY_RECENT) } }
        }
        item { ArkaChip("Toutes", selected == CATEGORY_ALL) { onSelect(CATEGORY_ALL) } }
        items(Repo.channelGroups) { group ->
            ArkaChip("◆ " + group.name, selected == CATEGORY_GROUP + group.name) { onSelect(CATEGORY_GROUP + group.name) }
        }
        items(visible) { category ->
            ArkaChip(category.name, selected == category.id) { onSelect(category.id) }
        }
        if (Repo.liveCategories.isNotEmpty()) {
            item {
                val label = if (hidden.isEmpty()) "Gérer les groupes" else "Gérer les groupes (${hidden.size} masqués)"
                ArkaChip(label, false) { managing = true }
            }
        }
    }
    if (managing) {
        AlertDialog(
            onDismissRequest = { managing = false },
            containerColor = ArkaSurface,
            title = { Text("Groupes affichés") },
            text = {
                LazyColumn {
                    if (Repo.hiddenChannels.isNotEmpty()) {
                        item {
                            TextButton(onClick = { Repo.showAllChannels() }) {
                                Text("Réafficher les ${Repo.hiddenChannels.size} chaînes masquées")
                            }
                        }
                    }
                    items(Repo.liveCategories) { category ->
                        val shown = category.id !in Repo.hiddenCategories
                        Row(
                            Modifier.fillMaxWidth().focusHighlight(scale = 1f)
                                .clickable { Repo.setCategoryHidden(category.id, shown) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = shown, onCheckedChange = { Repo.setCategoryHidden(category.id, !it) })
                            Text(category.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { managing = false }) { Text("Terminé") } },
        )
    }
}

/**
 * Channels shown for a group. Hidden channels never appear; hidden groups are left out of "Toutes".
 * Favourites and recent channels keep the user's order.
 */
fun channelsFor(
    category: String,
    channels: List<Channel>,
    favoriteOrder: List<String>,
    recent: List<String>,
    hiddenCategories: Set<String>,
    hiddenChannels: Set<String>,
    groups: List<ChannelGroup> = emptyList(),
): List<Channel> {
    val visible = if (hiddenChannels.isEmpty()) channels else channels.filter { it.id !in hiddenChannels }
    return when (category) {
        CATEGORY_ALL -> if (hiddenCategories.isEmpty()) visible else visible.filter { it.categoryId !in hiddenCategories }
        CATEGORY_FAVORITES -> {
            val byId = visible.associateBy { it.id }
            favoriteOrder.mapNotNull { byId[it] }
        }
        CATEGORY_RECENT -> {
            val byId = visible.associateBy { it.id }
            recent.mapNotNull { byId[it] }
        }
        else -> if (category.startsWith(CATEGORY_GROUP)) {
            val byId = visible.associateBy { it.id }
            groups.firstOrNull { it.name == category.removePrefix(CATEGORY_GROUP) }?.ids.orEmpty().mapNotNull { byId[it] }
        } else {
            visible.filter { it.categoryId == category }
        }
    }
}

@Composable
fun LiveStatus() {
    Column(
        Modifier.fillMaxWidth().padding(ArkaSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val error = Repo.error
        when {
            Repo.loading -> {
                CircularProgressIndicator(color = ArkaColors.Red)
                Spacer(Modifier.height(ArkaSpacing.md))
                Text("Chargement des chaînes…", color = ArkaColors.TextSecondary)
            }
            error != null -> {
                Text(error, color = ArkaColors.Error, textAlign = TextAlign.Center)
                TextButton(onClick = { Repo.reload() }) { Text("Réessayer") }
            }
            else -> Text("Aucune chaîne dans cette sélection.", color = ArkaColors.TextSecondary, textAlign = TextAlign.Center)
        }
    }
}

private fun groupLabel(category: String): String = when (category) {
    CATEGORY_ALL -> "Toutes"
    CATEGORY_FAVORITES -> "Favoris"
    CATEGORY_RECENT -> "Récentes"
    else -> if (category.startsWith(CATEGORY_GROUP)) {
        category.removePrefix(CATEGORY_GROUP)
    } else {
        Repo.liveCategories.firstOrNull { it.id == category }?.name.orEmpty()
    }
}

/**
 * Live TV. Handsets: groups as chips above the channel list. TV: three zones — groups, channels,
 * and the programme details of the channel under focus.
 */
@Composable
fun LiveScreen() {
    val context = LocalContext.current
    val tv = LocalIsTv.current
    var category by rememberSaveable { mutableStateOf(CATEGORY_ALL) }
    val channels = Repo.channels
    val favorites = Repo.favorites
    val recent = Repo.recentChannels
    val hidden = Repo.hiddenCategories
    val hiddenChannels = Repo.hiddenChannels
    val favoriteOrder = Repo.favoriteOrder
    val groups = Repo.channelGroups
    val list = remember(channels, category, favoriteOrder, recent, hidden, hiddenChannels, groups) {
        channelsFor(category, channels, favoriteOrder, recent, hidden, hiddenChannels, groups)
    }
    var menuFor by remember { mutableStateOf<Channel?>(null) }
    var newGroupFor by remember { mutableStateOf<Channel?>(null) }
    var focused by remember { mutableStateOf<Channel?>(null) }
    val now = rememberNow()

    val rows: @Composable (Modifier) -> Unit = { modifier ->
        if (list.isEmpty()) {
            Box(modifier) { LiveStatus() }
        } else {
            LazyColumn(modifier, contentPadding = PaddingValues(vertical = ArkaSpacing.sm)) {
                items(list) { channel ->
                    ChannelRow(
                        channel = channel,
                        now = now,
                        favorite = channel.id in favorites,
                        onClick = { playChannel(context, list, channel) },
                        onLongClick = { menuFor = channel },
                        onFocused = { focused = channel },
                    )
                }
            }
        }
    }

    if (tv) {
        Row(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.width(160.dp).fillMaxHeight().background(ArkaColors.BackgroundAlt),
                contentPadding = PaddingValues(horizontal = ArkaSpacing.sm, vertical = ArkaSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item {
                    Text(
                        "EN DIRECT",
                        fontFamily = ArkaDisplayFont,
                        fontWeight = FontWeight(800),
                        style = ArkaTheme.type.title,
                        modifier = Modifier.padding(start = ArkaSpacing.sm, bottom = ArkaSpacing.sm),
                    )
                }
                item { GroupLine("★ Favoris", category == CATEGORY_FAVORITES) { category = CATEGORY_FAVORITES } }
                if (recent.isNotEmpty()) {
                    item { GroupLine("Récentes", category == CATEGORY_RECENT) { category = CATEGORY_RECENT } }
                }
                item { GroupLine("Toutes", category == CATEGORY_ALL) { category = CATEGORY_ALL } }
                items(groups) { group ->
                    GroupLine("◆ " + group.name, category == CATEGORY_GROUP + group.name) { category = CATEGORY_GROUP + group.name }
                }
                items(Repo.liveCategories.filter { it.id !in hidden }) { group ->
                    GroupLine(group.name, category == group.id) { category = group.id }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(
                    (groupLabel(category) + " · ${list.size} chaînes").uppercase(),
                    color = ArkaColors.TextSecondary,
                    style = ArkaTheme.type.mono,
                    modifier = Modifier.padding(start = ArkaSpacing.lg, top = ArkaSpacing.lg),
                )
                rows(Modifier.fillMaxSize())
            }
            ChannelInfoPane(focused?.takeIf { it in list } ?: list.firstOrNull(), now)
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Direct", onBack = null, trailing = list.size.toString())
            CategoryChips(category) { category = it }
            rows(Modifier.fillMaxSize())
        }
    }

    menuFor?.let { channel ->
        val favorite = channel.id in favorites
        val position = favoriteOrder.indexOf(channel.id)
        ArkaSheet(channel.name, onDismiss = { menuFor = null }) {
            SheetOption(if (favorite) "Retirer des favoris" else "Ajouter aux favoris", false, onClick = { Repo.toggleFavorite(channel.id) })
            if (favorite && position > 0) {
                SheetOption("Monter dans les favoris", false, onClick = { Repo.moveFavorite(channel.id, -1) })
            }
            if (favorite && position in 0 until favoriteOrder.lastIndex) {
                SheetOption("Descendre dans les favoris", false, onClick = { Repo.moveFavorite(channel.id, 1) })
            }
            // The user's own groups: tick to add, untick to remove.
            groups.forEach { group ->
                val inside = channel.id in group.ids
                SheetOption("Groupe « ${group.name} »", inside, onClick = {
                    if (inside) Repo.removeFromGroup(group.name, channel.id) else Repo.addToGroup(group.name, channel.id)
                })
            }
            SheetOption("Nouveau groupe…", false, onClick = {
                newGroupFor = channel
                menuFor = null
            })
            if (category.startsWith(CATEGORY_GROUP)) {
                SheetOption("Supprimer ce groupe", false, onClick = {
                    Repo.deleteGroup(category.removePrefix(CATEGORY_GROUP))
                    category = CATEGORY_ALL
                    menuFor = null
                })
            }
            SheetOption("Masquer cette chaîne", false, onClick = {
                Repo.setChannelHidden(channel.id, true)
                menuFor = null
            })
        }
    }

    newGroupFor?.let { channel ->
        var name by remember(channel.id) { mutableStateOf("") }
        ArkaSheet("Nouveau groupe", onDismiss = { newGroupFor = null }) {
            ArkaField("Nom du groupe", name, "Sport, Enfants, Infos…") { name = it.take(30) }
            Box(Modifier.padding(top = ArkaSpacing.md)) {
                PrimaryButton(
                    "Créer avec « ${channel.name} »",
                    {
                        Repo.addToGroup(name, channel.id)
                        newGroupFor = null
                    },
                    enabled = name.isNotBlank(),
                )
            }
        }
    }
}

@Composable
private fun GroupLine(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(ArkaSize.tvControlMin)
            .focusHighlight(ArkaRadius.sm, 1f)
            .clip(RoundedCornerShape(ArkaRadius.sm))
            .background(if (selected) ArkaColors.SurfaceHigh else ArkaColors.BackgroundAlt)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(if (selected) ArkaColors.Red else ArkaColors.BackgroundAlt))
        Text(
            name,
            color = if (selected) ArkaColors.TextPrimary else ArkaColors.TextSecondary,
            style = ArkaTheme.type.label,
            fontWeight = if (selected) FontWeight(700) else FontWeight(500),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = ArkaSpacing.sm),
        )
    }
}

/** TV: what is on the channel under focus, and what comes next. No video preview is started. */
@Composable
private fun ChannelInfoPane(channel: Channel?, now: Long) {
    if (channel == null) {
        Spacer(Modifier.width(250.dp))
        return
    }
    Column(Modifier.width(250.dp).fillMaxHeight().padding(ArkaSpacing.lg)) {
        val programs = Repo.programs(channel.epgId)
        val current = programs.firstOrNull { it.start <= now && now < it.end }
        ChannelLogo(channel.logo, channel.name, Modifier.fillMaxWidth().height(110.dp))
        Text(
            current?.title ?: channel.name,
            style = ArkaTheme.type.heading,
            fontWeight = FontWeight(700),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = ArkaSpacing.md),
        )
        if (current != null) {
            val left = ((current.end - now) / 60).coerceAtLeast(1)
            Text(
                formatClock(current.start) + " – " + formatClock(current.end) + " · reste $left min",
                color = ArkaColors.TextSecondary,
                style = ArkaTheme.type.mono,
                modifier = Modifier.padding(top = ArkaSpacing.xs),
            )
            if (current.desc.isNotBlank()) {
                Text(
                    current.desc,
                    color = ArkaColors.TextTertiary,
                    style = ArkaTheme.type.body,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = ArkaSpacing.sm),
                )
            }
            val next = programs.firstOrNull { it.start >= current.end }
            if (next != null) {
                Text(
                    "Ensuite · " + formatClock(next.start),
                    color = ArkaColors.TextSecondary,
                    style = ArkaTheme.type.mono,
                    modifier = Modifier.padding(top = ArkaSpacing.md),
                )
                Text(next.title, style = ArkaTheme.type.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Text(
                "Guide indisponible pour cette chaîne.",
                color = ArkaColors.TextSecondary,
                style = ArkaTheme.type.body,
                modifier = Modifier.padding(top = ArkaSpacing.xs),
            )
        }
        Spacer(Modifier.weight(1f))
        Text("OK : regarder · appui long : favori, masquer", color = ArkaColors.TextDisabled, style = ArkaTheme.type.mono)
    }
}

/**
 * Channel line: number, logo, then the programme on air with its progress and end time.
 * Without guide data the channel name takes the programme's place.
 */
@Composable
fun ChannelRow(
    channel: Channel,
    now: Long,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
) {
    val tv = LocalIsTv.current
    val program = Repo.nowPlaying(channel.epgId, now)
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = if (tv) ArkaSpacing.sm else 0.dp)
            .focusHighlight(ArkaRadius.sm, 1f)
            .onFocusChanged { if (it.isFocused) onFocused?.invoke() }
            .clip(RoundedCornerShape(ArkaRadius.sm))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = if (tv) ArkaSpacing.sm else ArkaTheme.margin, vertical = if (tv) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            channel.number.toString(),
            color = ArkaColors.TextSecondary,
            style = ArkaTheme.type.mono,
            maxLines = 1,
            modifier = Modifier.width(if (tv) 30.dp else 36.dp),
        )
        ChannelLogo(channel.logo, channel.name, Modifier.size(width = if (tv) 44.dp else 52.dp, height = if (tv) 28.dp else 34.dp))
        Column(Modifier.weight(1f).padding(start = ArkaSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    program?.title ?: channel.name,
                    style = ArkaTheme.type.label,
                    fontSize = if (tv) 12.sp else 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (favorite) {
                    Text(" ★", color = ArkaColors.TextSecondary, fontSize = 11.sp)
                }
            }
            if (program != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    val span = (program.end - program.start).coerceAtLeast(1)
                    LinearProgressIndicator(
                        progress = { ((now - program.start).toFloat() / span).coerceIn(0f, 1f) },
                        color = ArkaColors.Red,
                        trackColor = ArkaColors.Border,
                        modifier = Modifier.weight(1f).height(2.dp),
                    )
                    Text(
                        formatClock(program.end),
                        color = ArkaColors.TextSecondary,
                        style = ArkaTheme.type.mono,
                        modifier = Modifier.padding(start = ArkaSpacing.sm),
                    )
                }
                Text(channel.name, color = ArkaColors.TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
