@file:OptIn(ExperimentalMaterial3Api::class)

package tv.arka.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.arka.app.data.Channel
import tv.arka.app.data.Repo
import tv.arka.app.data.Store
import tv.arka.app.data.TextNormalizer
import tv.arka.app.data.Vod
import tv.arka.app.data.VodKind

private const val HISTORY_SIZE = 8

@Composable
fun SearchScreen(onOpen: (Vod) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    val normalized = remember(query) { TextNormalizer.normalize(query) }

    val channels = remember { mutableStateListOf<Channel>() }
    val movies = remember { mutableStateListOf<Vod>() }
    val series = remember { mutableStateListOf<Vod>() }
    var moreChannels by remember { mutableStateOf(false) }
    var moreMovies by remember { mutableStateOf(false) }
    var moreSeries by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    // Identifies the current search so that a late "load more" never lands in newer results.
    var token by remember { mutableIntStateOf(0) }
    var history by remember { mutableStateOf(Store.loadSearchHistory()) }

    val favorites = Repo.favorites
    val now = rememberNow()
    val catalogVersion = Repo.catalogVersion
    val sourceId = Repo.active?.id

    LaunchedEffect(normalized, catalogVersion, sourceId) {
        token++
        if (normalized.length < 2) {
            channels.clear()
            movies.clear()
            series.clear()
            moreChannels = false
            moreMovies = false
            moreSeries = false
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(200)
        val foundChannels = Repo.searchChannels(normalized, 0)
        val foundMovies = Repo.searchVods(VodKind.MOVIE, normalized, 0)
        val foundSeries = Repo.searchVods(VodKind.SERIES, normalized, 0)
        channels.clear()
        channels.addAll(foundChannels)
        movies.clear()
        movies.addAll(foundMovies)
        series.clear()
        series.addAll(foundSeries)
        moreChannels = foundChannels.size == Repo.SEARCH_PAGE
        moreMovies = foundMovies.size == Repo.SEARCH_PAGE
        moreSeries = foundSeries.size == Repo.SEARCH_PAGE
        searching = false
    }

    fun recordSearch() {
        val text = query.trim()
        if (text.length < 2) return
        history = (listOf(text) + history.filterNot { it.equals(text, ignoreCase = true) }).take(HISTORY_SIZE)
        Store.saveSearchHistory(history)
    }

    fun loadMore(kind: VodKind?) {
        if (loadingMore) return
        loadingMore = true
        val mine = token
        scope.launch {
            try {
                when (kind) {
                    null -> {
                        val next = Repo.searchChannels(normalized, channels.size)
                        if (mine == token) {
                            channels.addAll(next)
                            moreChannels = next.size == Repo.SEARCH_PAGE
                        }
                    }
                    VodKind.MOVIE -> {
                        val next = Repo.searchVods(kind, normalized, movies.size)
                        if (mine == token) {
                            movies.addAll(next)
                            moreMovies = next.size == Repo.SEARCH_PAGE
                        }
                    }
                    VodKind.SERIES -> {
                        val next = Repo.searchVods(kind, normalized, series.size)
                        if (mine == token) {
                            series.addAll(next)
                            moreSeries = next.size == Repo.SEARCH_PAGE
                        }
                    }
                }
            } finally {
                loadingMore = false
            }
        }
    }

    val openVod: (Vod) -> Unit = {
        recordSearch()
        onOpen(it)
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Recherche",
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Un titre, une chaîne…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Effacer")
                    }
                }
            },
            shape = RoundedCornerShape(ArkaRadius.lg),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (normalized.length < 2) {
                if (history.isNotEmpty()) {
                    item { SectionTitle("Recherches récentes") }
                    items(history) { entry ->
                        androidx.compose.foundation.layout.Row(
                            Modifier.fillMaxWidth().focusHighlight(scale = 1f).clickable { query = entry }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.History, contentDescription = null, tint = ArkaMuted)
                            Text(entry, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                    item {
                        TextButton(
                            onClick = {
                                history = emptyList()
                                Store.saveSearchHistory(history)
                            },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        ) { Text("Effacer l'historique") }
                    }
                }
            } else {
                val empty = channels.isEmpty() && movies.isEmpty() && series.isEmpty()
                if (empty) {
                    item {
                        Text(
                            if (searching) "Recherche…" else "Aucun résultat.",
                            color = ArkaMuted,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                if (channels.isNotEmpty()) {
                    item { SectionTitle("Chaînes") }
                    items(channels) { channel ->
                        ChannelRow(
                            channel = channel,
                            now = now,
                            favorite = channel.id in favorites,
                            onClick = {
                                recordSearch()
                                playChannel(context, channels.toList(), channel)
                            },
                            onFavorite = { Repo.toggleFavorite(channel.id) },
                        )
                    }
                    if (moreChannels) {
                        item {
                            TextButton(onClick = { loadMore(null) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text("Afficher plus de chaînes")
                            }
                        }
                    }
                }
                if (movies.isNotEmpty()) {
                    item {
                        PagedVodRow("Films", movies, openVod) { if (moreMovies) loadMore(VodKind.MOVIE) }
                    }
                }
                if (series.isNotEmpty()) {
                    item {
                        PagedVodRow("Séries", series, openVod) { if (moreSeries) loadMore(VodKind.SERIES) }
                    }
                }
            }
        }
    }
}

/** A poster row that asks for the next page when its last poster comes into view. */
@Composable
private fun PagedVodRow(title: String, items: List<Vod>, onOpen: (Vod) -> Unit, onEndReached: () -> Unit) {
    Column {
        SectionTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(items) { index, vod ->
                Poster(vod, Modifier.width(112.dp)) { onOpen(vod) }
                if (index == items.lastIndex) {
                    LaunchedEffect(items.size) { onEndReached() }
                }
            }
        }
    }
}
