@file:OptIn(ExperimentalMaterial3Api::class)

package tv.arka.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tv.arka.app.data.Repo
import tv.arka.app.data.Vod
import tv.arka.app.data.VodKind
import tv.arka.app.data.VodSort

/**
 * Lives in the navigation stack entry, so the loaded pages, the chosen order and the scroll
 * position are still there when the user comes back from a detail screen.
 */
class CatalogGridState(initialSort: VodSort = VodSort.PROVIDER) {
    var sort by mutableStateOf(initialSort)
    val items = mutableStateListOf<Vod>()
    var more by mutableStateOf(false)
    var loading by mutableStateOf(true)
    val grid = LazyGridState()

    internal var loadedKey: String? = null
    internal var loadedSort: VodSort? = null
    internal var token = 0
    internal var loadingMore = false
}

private val sortLabels = listOf(
    VodSort.PROVIDER to "Par défaut",
    VodSort.RECENT to "Ajouts récents",
    VodSort.NAME to "A – Z",
    VodSort.YEAR to "Année",
    VodSort.RATING to "Note",
)

@Composable
fun CatalogGridScreen(
    title: String,
    kind: VodKind,
    categoryId: String?,
    state: CatalogGridState,
    onBack: () -> Unit,
    onOpen: (Vod) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val key = "${Repo.active?.id}|${Repo.catalogVersion}|${state.sort}"

    LaunchedEffect(key) {
        if (state.loadedKey == key) return@LaunchedEffect
        state.token++
        state.loading = true
        val first = Repo.vodPage(kind, categoryId, state.sort, 0)
        val sortChanged = state.loadedSort != null && state.loadedSort != state.sort
        state.items.clear()
        state.items.addAll(first)
        state.more = first.size == Repo.GRID_PAGE
        state.loading = false
        state.loadedKey = key
        state.loadedSort = state.sort
        if (sortChanged) state.grid.scrollToItem(0)
    }

    fun loadMore() {
        if (state.loadingMore || !state.more) return
        state.loadingMore = true
        val mine = state.token
        scope.launch {
            try {
                val next = Repo.vodPage(kind, categoryId, state.sort, state.items.size)
                if (mine == state.token) {
                    state.items.addAll(next)
                    state.more = next.size == Repo.GRID_PAGE
                }
            } finally {
                state.loadingMore = false
            }
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
            }
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(sortLabels) { (sort, label) ->
                ArkaChip(label, state.sort == sort) { state.sort = sort }
            }
        }
        if (state.items.isEmpty()) {
            Centered {
                if (state.loading) {
                    CircularProgressIndicator(color = ArkaRed)
                } else {
                    Text("Aucun contenu dans cette sélection.", color = ArkaMuted)
                }
            }
        } else {
            LazyVerticalGrid(
                state = state.grid,
                columns = GridCells.Adaptive(108.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(state.items) { index, vod ->
                    Poster(vod) { onOpen(vod) }
                    if (index == state.items.lastIndex) {
                        LaunchedEffect(state.items.size) { loadMore() }
                    }
                }
            }
        }
    }
}
