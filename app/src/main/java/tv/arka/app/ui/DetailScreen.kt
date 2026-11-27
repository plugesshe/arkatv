@file:OptIn(ExperimentalMaterial3Api::class)

package tv.arka.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import tv.arka.app.data.Episode
import tv.arka.app.data.PlayItem
import tv.arka.app.data.Repo
import tv.arka.app.data.Vod
import tv.arka.app.data.VodDetails
import tv.arka.app.data.VodKind

private fun episodeItem(vod: Vod, episode: Episode): PlayItem = PlayItem(
    key = "e:${episode.id}",
    title = vod.name,
    subtitle = "S${episode.season} · É${episode.number}" + if (episode.title.isNotBlank()) " · ${episode.title}" else "",
    url = episode.url,
    live = false,
    poster = episode.image.ifBlank { vod.poster },
)

@Composable
fun DetailScreen(vod: Vod, onBack: () -> Unit) {
    val context = LocalContext.current
    val details by produceState<VodDetails?>(initialValue = null, vod.kind, vod.id) {
        value = try {
            Repo.details(vod)
        } catch (e: Exception) {
            VodDetails(plot = vod.plot, year = vod.year)
        }
    }
    var selectedSeason by remember(vod.id) { mutableIntStateOf(-1) }
    val loaded = details
    val seasons = loaded?.seasons.orEmpty()
    val seasonNumbers = seasons.keys.sorted()
    val season = if (selectedSeason in seasonNumbers) selectedSeason else seasonNumbers.firstOrNull()
    val episodes = if (season != null) seasons[season].orEmpty() else emptyList()
    val movieProgress = Repo.progressFor("m:${vod.id}")

    LazyColumn(Modifier.fillMaxSize().systemBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                val image = loaded?.backdrop.orEmpty().ifBlank { vod.poster }
                if (image.isNotBlank()) {
                    AsyncImage(
                        model = image,
                        contentDescription = vod.name,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, ArkaBg), startY = 200f)
                    )
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White)
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(vod.name, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black)
                val meta = listOf(
                    if (vod.kind == VodKind.SERIES) "Série" else "Film",
                    loaded?.year.orEmpty().ifBlank { vod.year },
                    if (seasonNumbers.isNotEmpty()) "${seasonNumbers.size} saison" + (if (seasonNumbers.size > 1) "s" else "") else "",
                    loaded?.duration.orEmpty(),
                    if (vod.rating.isNotBlank() && vod.rating != "0") "★ ${vod.rating}" else "",
                    loaded?.genre.orEmpty(),
                ).filter { it.isNotBlank() }.joinToString(" · ")
                Text(meta, color = ArkaMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (vod.kind == VodKind.MOVIE) {
                        Button(
                            onClick = { play(context, listOf(vod.toPlayItem()), 0) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ArkaRed, contentColor = Color.White),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (movieProgress != null) "Reprendre" else "Lire")
                        }
                    } else if (episodes.isNotEmpty()) {
                        Button(
                            onClick = { play(context, episodes.map { episodeItem(vod, it) }, 0) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ArkaRed, contentColor = Color.White),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Lire la saison $season")
                        }
                    }
                    MyListButton(vod)
                }
                if (loaded == null) {
                    CircularProgressIndicator(color = ArkaRed, modifier = Modifier.padding(top = 20.dp))
                } else {
                    if (loaded.plot.isNotBlank()) {
                        Text(loaded.plot, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 14.dp))
                    }
                    if (loaded.cast.isNotBlank()) {
                        Text("Avec : ${loaded.cast}", color = ArkaMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    if (loaded.director.isNotBlank()) {
                        Text("Réalisation : ${loaded.director}", color = ArkaMuted, fontSize = 12.sp)
                    }
                }
            }
        }
        if (seasonNumbers.size > 1) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(seasonNumbers) { number ->
                        ArkaChip("Saison $number", number == season) { selectedSeason = number }
                    }
                }
            }
        }
        if (episodes.isNotEmpty()) {
            item { SectionTitle("Épisodes") }
            items(episodes) { episode ->
                EpisodeRow(vod, episode) {
                    play(context, episodes.map { episodeItem(vod, it) }, episodes.indexOf(episode))
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(vod: Vod, episode: Episode, onClick: () -> Unit) {
    val progress = Repo.progressFor("e:${episode.id}")
    Row(
        Modifier.fillMaxWidth().focusHighlight(scale = 1f).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)).background(ArkaCard)) {
            val image = episode.image.ifBlank { vod.poster }
            if (image.isNotBlank()) {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { (progress.position.toFloat() / progress.duration.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    color = ArkaRed,
                    trackColor = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${episode.number}. " + episode.title.ifBlank { "Épisode ${episode.number}" },
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (episode.plot.isNotBlank()) {
                Text(episode.plot, color = ArkaMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
