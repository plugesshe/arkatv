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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import tv.arka.app.data.Progress
import tv.arka.app.data.Repo
import tv.arka.app.data.Vod
import tv.arka.app.data.VodKind

@Composable
fun ArkaChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = ArkaCard,
            labelColor = Color.White,
            selectedContainerColor = ArkaRed,
            selectedLabelColor = Color.White,
        ),
        border = null,
    )
}

@Composable
fun HomeScreen(onOpen: (Vod) -> Unit, onGrid: (String, List<Vod>) -> Unit) {
    val context = LocalContext.current
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val movies = Repo.movies
    val series = Repo.series

    val latestMovies = remember(movies) { movies.sortedByDescending { it.added }.take(20) }
    val latestSeries = remember(series) { series.sortedByDescending { it.added }.take(20) }
    val moviesByCategory = remember(movies) { movies.groupBy { it.categoryId } }
    val seriesByCategory = remember(series) { series.groupBy { it.categoryId } }
    val featured = remember(latestMovies, latestSeries, filter) {
        val pool = when (filter) {
            1 -> latestMovies
            2 -> latestSeries
            else -> latestSeries.take(5) + latestMovies.take(5)
        }
        pool.firstOrNull { it.poster.isNotBlank() } ?: pool.firstOrNull()
    }
    val resume = Repo.progress
    val myList = Repo.myList.filter {
        filter == 0 || (filter == 1 && it.kind == VodKind.MOVIE) || (filter == 2 && it.kind == VodKind.SERIES)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                Logo()
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ArkaChip("Accueil", filter == 0) { filter = 0 }
                    ArkaChip("Films", filter == 1) { filter = 1 }
                    ArkaChip("Séries", filter == 2) { filter = 2 }
                }
            }
        }

        if (movies.isEmpty() && series.isEmpty()) {
            item { EmptyState() }
        }

        if (featured != null) {
            item {
                Hero(
                    vod = featured,
                    onPlay = {
                        if (featured.kind == VodKind.MOVIE) play(context, listOf(featured.toPlayItem()), 0)
                        else onOpen(featured)
                    },
                    onOpen = { onOpen(featured) },
                )
            }
        }

        if (resume.isNotEmpty() && filter == 0) {
            item {
                SectionTitle("Reprendre la lecture")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(resume) { entry ->
                        ResumeCard(entry) { play(context, listOf(entry.item), 0) }
                    }
                }
            }
        }

        if (myList.isNotEmpty()) {
            item { VodRow("Ma liste", myList, onOpen) }
        }
        if (filter != 2 && latestMovies.isNotEmpty()) {
            item { VodRow("Films récemment ajoutés", latestMovies, onOpen) { onGrid("Films", movies) } }
        }
        if (filter != 1 && latestSeries.isNotEmpty()) {
            item { VodRow("Séries récemment ajoutées", latestSeries, onOpen) { onGrid("Séries", series) } }
        }
        if (filter != 2) {
            items(Repo.vodCategories) { category ->
                val list = moviesByCategory[category.id]
                if (!list.isNullOrEmpty()) {
                    VodRow(category.name, list.take(20), onOpen) { onGrid(category.name, list) }
                }
            }
        }
        if (filter != 1) {
            items(Repo.seriesCategories) { category ->
                val list = seriesByCategory[category.id]
                if (!list.isNullOrEmpty()) {
                    VodRow(category.name, list.take(20), onOpen) { onGrid(category.name, list) }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val error = Repo.error
        when {
            Repo.loading -> {
                CircularProgressIndicator(color = ArkaRed)
                Spacer(Modifier.height(12.dp))
                Text("Chargement du catalogue…", color = ArkaMuted)
            }
            error != null -> {
                Text(error, color = ArkaError, textAlign = TextAlign.Center)
                TextButton(onClick = { Repo.reload() }) { Text("Réessayer") }
            }
            else -> Text(
                "Cette source ne contient ni films ni séries. Les chaînes sont dans l'onglet Direct.",
                color = ArkaMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Hero(vod: Vod, onPlay: () -> Unit, onOpen: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(440.dp).padding(top = 8.dp).clickable(onClick = onOpen)) {
        if (vod.poster.isNotBlank()) {
            AsyncImage(
                model = vod.poster,
                contentDescription = vod.name,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, ArkaBg.copy(alpha = 0.6f), ArkaBg), startY = 250f)
            )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(
                if (vod.kind == VodKind.SERIES) "SÉRIE" else "FILM",
                color = ArkaRed,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                vod.name,
                fontSize = 28.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPlay,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ArkaRed, contentColor = Color.White),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(if (vod.kind == VodKind.SERIES) "Voir" else "Lire")
                }
                MyListButton(vod)
            }
        }
    }
}

@Composable
fun MyListButton(vod: Vod) {
    val inList = Repo.inMyList(vod)
    Button(
        onClick = { Repo.toggleMyList(vod) },
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ArkaCard, contentColor = Color.White),
    ) {
        Icon(if (inList) Icons.Filled.Check else Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.width(4.dp))
        Text("Ma liste")
    }
}

@Composable
private fun ResumeCard(entry: Progress, onClick: () -> Unit) {
    Column(Modifier.width(200.dp).focusHighlight().clip(RoundedCornerShape(ArkaRadius.md)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(ArkaCard)) {
            if (entry.item.poster.isNotBlank()) {
                AsyncImage(
                    model = entry.item.poster,
                    contentDescription = entry.item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(36.dp),
            )
            LinearProgressIndicator(
                progress = { (entry.position.toFloat() / entry.duration.coerceAtLeast(1)).coerceIn(0f, 1f) },
                color = ArkaRed,
                trackColor = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
            )
        }
        Text(
            entry.item.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (entry.item.subtitle.isNotBlank()) {
            Text(entry.item.subtitle, color = ArkaMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun GridScreen(title: String, items: List<Vod>, onBack: () -> Unit, onOpen: (Vod) -> Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
            }
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(108.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items) { vod ->
                Poster(vod) { onOpen(vod) }
            }
        }
    }
}
