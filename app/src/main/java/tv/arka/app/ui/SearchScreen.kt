@file:OptIn(ExperimentalMaterial3Api::class)

package tv.arka.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import tv.arka.app.data.Channel
import tv.arka.app.data.Repo
import tv.arka.app.data.Vod

private class SearchResults(
    val channels: List<Channel> = emptyList(),
    val movies: List<Vod> = emptyList(),
    val series: List<Vod> = emptyList(),
) {
    val isEmpty: Boolean get() = channels.isEmpty() && movies.isEmpty() && series.isEmpty()
}

@Composable
fun SearchScreen(onOpen: (Vod) -> Unit) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val channels = Repo.channels
    val movies = Repo.movies
    val series = Repo.series
    val favorites = Repo.favorites
    val now = rememberNow()

    val results by produceState(initialValue = SearchResults(), query, channels, movies, series) {
        val text = query.trim()
        if (text.length < 2) {
            value = SearchResults()
        } else {
            delay(250)
            value = withContext(Dispatchers.Default) {
                SearchResults(
                    channels = channels.asSequence().filter { it.name.contains(text, ignoreCase = true) }.take(30).toList(),
                    movies = movies.asSequence().filter { it.name.contains(text, ignoreCase = true) }.take(40).toList(),
                    series = series.asSequence().filter { it.name.contains(text, ignoreCase = true) }.take(40).toList(),
                )
            }
        }
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
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (query.trim().length >= 2 && results.isEmpty) {
                item {
                    Text("Aucun résultat.", color = ArkaMuted, modifier = Modifier.padding(16.dp))
                }
            }
            if (results.channels.isNotEmpty()) {
                item { SectionTitle("Chaînes") }
                items(results.channels) { channel ->
                    ChannelRow(
                        channel = channel,
                        now = now,
                        favorite = channel.id in favorites,
                        onClick = { playChannel(context, results.channels, channel) },
                        onFavorite = { Repo.toggleFavorite(channel.id) },
                    )
                }
            }
            if (results.movies.isNotEmpty()) {
                item { VodRow("Films", results.movies, onOpen) }
            }
            if (results.series.isNotEmpty()) {
                item { VodRow("Séries", results.series, onOpen) }
            }
        }
    }
}
