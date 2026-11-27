@file:OptIn(ExperimentalMaterial3Api::class)

package tv.arka.app.ui

import androidx.compose.foundation.clickable
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
import tv.arka.app.data.Repo

const val CATEGORY_ALL = "*all*"
const val CATEGORY_FAVORITES = "*fav*"
const val CATEGORY_RECENT = "*recent*"

/** Group filter shared by the live list and the guide. The last chip opens the group manager. */
@Composable
fun CategoryChips(selected: String, onSelect: (String) -> Unit) {
    var managing by remember { mutableStateOf(false) }
    val hidden = Repo.hiddenCategories
    val visible = Repo.liveCategories.filter { it.id !in hidden }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ArkaChip("Favoris", selected == CATEGORY_FAVORITES) { onSelect(CATEGORY_FAVORITES) } }
        if (Repo.recentChannels.isNotEmpty()) {
            item { ArkaChip("Récentes", selected == CATEGORY_RECENT) { onSelect(CATEGORY_RECENT) } }
        }
        item { ArkaChip("Toutes", selected == CATEGORY_ALL) { onSelect(CATEGORY_ALL) } }
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

fun channelsFor(
    category: String,
    channels: List<Channel>,
    favorites: Set<String>,
    recent: List<String>,
    hidden: Set<String>,
): List<Channel> = when (category) {
    CATEGORY_ALL -> if (hidden.isEmpty()) channels else channels.filter { it.categoryId !in hidden }
    CATEGORY_FAVORITES -> channels.filter { it.id in favorites }
    CATEGORY_RECENT -> {
        val byId = channels.associateBy { it.id }
        recent.mapNotNull { byId[it] }
    }
    else -> channels.filter { it.categoryId == category }
}

@Composable
fun LiveStatus() {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val error = Repo.error
        when {
            Repo.loading -> {
                CircularProgressIndicator(color = ArkaRed)
                Spacer(Modifier.height(12.dp))
                Text("Chargement des chaînes…", color = ArkaMuted)
            }
            error != null -> {
                Text(error, color = ArkaError, textAlign = TextAlign.Center)
                TextButton(onClick = { Repo.reload() }) { Text("Réessayer") }
            }
            else -> Text("Aucune chaîne dans cette sélection.", color = ArkaMuted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun LiveScreen() {
    val context = LocalContext.current
    var category by rememberSaveable { mutableStateOf(CATEGORY_ALL) }
    val channels = Repo.channels
    val favorites = Repo.favorites
    val recent = Repo.recentChannels
    val hidden = Repo.hiddenCategories
    val list = remember(channels, category, favorites, recent, hidden) {
        channelsFor(category, channels, favorites, recent, hidden)
    }
    val now = rememberNow()

    Column(Modifier.fillMaxSize()) {
        Text(
            "En direct",
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp),
        )
        CategoryChips(category) { category = it }
        if (list.isEmpty()) {
            LiveStatus()
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(list) { channel ->
                    ChannelRow(
                        channel = channel,
                        now = now,
                        favorite = channel.id in favorites,
                        onClick = { playChannel(context, list, channel) },
                        onFavorite = { Repo.toggleFavorite(channel.id) },
                    )
                }
            }
        }
    }
}

@Composable
fun ChannelRow(channel: Channel, now: Long, favorite: Boolean, onClick: () -> Unit, onFavorite: () -> Unit) {
    val program = Repo.nowPlaying(channel.epgId, now)
    Row(
        Modifier.fillMaxWidth().focusHighlight(scale = 1f).clickable(onClick = onClick)
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(channel.logo, channel.name, Modifier.size(width = 64.dp, height = 44.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(channel.number.toString(), color = ArkaMuted, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                Text(channel.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (program != null) {
                Text(
                    program.title,
                    color = ArkaMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val span = (program.end - program.start).coerceAtLeast(1)
                LinearProgressIndicator(
                    progress = { ((now - program.start).toFloat() / span).coerceIn(0f, 1f) },
                    color = ArkaRed,
                    trackColor = ArkaCard,
                    modifier = Modifier.padding(top = 4.dp).fillMaxWidth().height(2.dp),
                )
            }
        }
        IconButton(onClick = onFavorite) {
            Icon(
                if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = "Favori",
                tint = if (favorite) ArkaRed else ArkaMuted,
            )
        }
    }
}

@Composable
fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
