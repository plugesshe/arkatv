package tv.arka.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tv.arka.app.data.Category
import tv.arka.app.data.Repo
import tv.arka.app.data.Vod
import tv.arka.app.data.VodKind
import tv.arka.app.data.VodSort

/**
 * Lives in the navigation stack entry, so the loaded pages, the chosen category and order and the
 * scroll position are still there when the user comes back from a detail screen.
 */
class CatalogGridState(initialSort: VodSort = VodSort.RECENT, initialCategory: String? = null) {
    var sort by mutableStateOf(initialSort)

    /** null = the natural direction of the order (A → Z for names, newest or best first otherwise). */
    var descending by mutableStateOf<Boolean?>(null)
    var categoryId by mutableStateOf(initialCategory)
    val items = mutableStateListOf<Vod>()
    var more by mutableStateOf(false)
    var loading by mutableStateOf(true)
    val grid = LazyGridState()

    internal var loadedKey: String? = null
    internal var loadedQuery: String? = null
    internal var token = 0
    internal var loadingMore = false
}

private val sortOptions = listOf(
    VodSort.RECENT to "Date d'ajout",
    VodSort.NAME to "Alphabétique (A → Z)",
    VodSort.YEAR to "Année",
    VodSort.RATING to "Note",
)

private fun sortLabel(sort: VodSort): String = when (sort) {
    VodSort.RECENT -> "Date d'ajout"
    VodSort.NAME -> "Alphabétique"
    VodSort.YEAR -> "Année"
    VodSort.RATING -> "Note"
    VodSort.PROVIDER -> "Ordre de la source"
}

private fun isDescending(state: CatalogGridState): Boolean =
    state.descending ?: (state.sort != VodSort.NAME && state.sort != VodSort.PROVIDER)

/**
 * Films or series of the source: categories, sort order and direction, adaptive grid read page by page.
 * Handsets pick the category and the order from bottom sheets; TV shows the categories in a side panel.
 */
@Composable
fun CatalogGridScreen(
    title: String,
    kind: VodKind,
    state: CatalogGridState,
    onBack: () -> Unit,
    onOpen: (Vod) -> Unit,
) {
    val tv = LocalIsTv.current
    val scope = rememberCoroutineScope()
    val categories = if (kind == VodKind.MOVIE) Repo.vodCategories else Repo.seriesCategories
    val counts = if (kind == VodKind.MOVIE) Repo.movieCategoryCounts else Repo.seriesCategoryCounts
    val total = if (kind == VodKind.MOVIE) Repo.movieCount else Repo.seriesCount
    val category = categories.firstOrNull { it.id == state.categoryId }
    var showSort by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }

    val query = "${state.sort}|${state.descending}|${state.categoryId}"
    val key = "${Repo.active?.id}|${Repo.catalogVersion}|$query"

    LaunchedEffect(key) {
        if (state.loadedKey == key) return@LaunchedEffect
        state.token++
        state.loading = true
        val first = Repo.vodPage(kind, state.categoryId, state.sort, state.descending, 0)
        val queryChanged = state.loadedQuery != null && state.loadedQuery != query
        state.items.clear()
        state.items.addAll(first)
        state.more = first.size == Repo.GRID_PAGE
        state.loading = false
        state.loadedKey = key
        state.loadedQuery = query
        // A new order or category starts from the top; a background refresh keeps the position.
        if (queryChanged) state.grid.scrollToItem(0)
    }

    fun loadMore() {
        if (state.loadingMore || !state.more) return
        state.loadingMore = true
        val mine = state.token
        scope.launch {
            try {
                val next = Repo.vodPage(kind, state.categoryId, state.sort, state.descending, state.items.size)
                if (mine == state.token) {
                    state.items.addAll(next)
                    state.more = next.size == Repo.GRID_PAGE
                }
            } finally {
                state.loadingMore = false
            }
        }
    }

    val shown = if (category != null) counts[category.id] ?: 0 else total
    val countLabel = if (category != null) "$shown / $total" else total.toString()
    val sortChip = "Tri : " + sortLabel(state.sort) + if (isDescending(state)) " ↓" else " ↑"

    val grid: @Composable () -> Unit = {
        val columns = GridCells.Adaptive(if (tv) 104.dp else 100.dp)
        val padding = PaddingValues(horizontal = if (tv) ArkaSpacing.xl else ArkaTheme.margin, vertical = ArkaSpacing.md)
        val gap = if (tv) 14.dp else 10.dp
        if (state.items.isEmpty() && state.loading) {
            // Skeleton grid: same cells as the loaded grid.
            LazyVerticalGrid(
                columns = columns,
                contentPadding = padding,
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(ArkaSpacing.lg),
                userScrollEnabled = false,
            ) {
                items(12) {
                    Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(ArkaRadius.sm)).skeleton())
                }
            }
        } else if (state.items.isEmpty()) {
            Centered { Text("Aucun contenu dans cette sélection.", color = ArkaColors.TextSecondary) }
        } else {
            LazyVerticalGrid(
                state = state.grid,
                columns = columns,
                contentPadding = padding,
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(ArkaSpacing.lg),
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

    if (tv) {
        Row(Modifier.fillMaxSize()) {
            CategoryPanel(title, categories, counts, total, state.categoryId) { state.categoryId = it }
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = ArkaSpacing.xl, vertical = ArkaSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        (category?.name ?: "Tout le catalogue") + " · " + countLabel,
                        color = ArkaColors.TextSecondary,
                        style = ArkaTheme.type.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    ArkaChip(sortChip, false) { showSort = true }
                }
                grid()
            }
        }
    } else {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            ScreenHeader(title, onBack, trailing = countLabel)
            LazyRow(
                contentPadding = PaddingValues(horizontal = ArkaTheme.margin, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item { ArkaChip("Catégorie : " + (category?.name ?: "Toutes"), category != null) { showCategories = true } }
                item { ArkaChip(sortChip, false) { showSort = true } }
                if (category != null) {
                    item {
                        Text(
                            "Effacer le filtre",
                            color = ArkaColors.TextSecondary,
                            style = ArkaTheme.type.label,
                            modifier = Modifier.clip(RoundedCornerShape(ArkaRadius.sm))
                                .clickable { state.categoryId = null }.padding(8.dp),
                        )
                    }
                }
            }
            grid()
        }
    }

    if (showSort) {
        ArkaSheet("Trier par", onDismiss = { showSort = false }) {
            sortOptions.forEach { (sort, label) ->
                SheetOption(label, state.sort == sort, onClick = {
                    if (state.sort != sort) {
                        state.sort = sort
                        state.descending = null
                    }
                })
            }
            Row(
                Modifier.fillMaxWidth().padding(top = ArkaSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm),
            ) {
                val down = isDescending(state)
                SegmentButton("Décroissant", down, { state.descending = true }, Modifier.weight(1f))
                SegmentButton("Croissant", !down, { state.descending = false }, Modifier.weight(1f))
            }
        }
    }

    if (showCategories) {
        ArkaSheet("Catégorie", onDismiss = { showCategories = false }) {
            SheetOption("Toutes", state.categoryId == null, trailing = total.toString(), onClick = {
                state.categoryId = null
                showCategories = false
            })
            categories.forEach { item ->
                SheetOption(item.name, state.categoryId == item.id, trailing = (counts[item.id] ?: 0).toString(), onClick = {
                    state.categoryId = item.id
                    showCategories = false
                })
            }
        }
    }
}

/** TV: the categories of the source with their counts, always visible left of the grid. */
@Composable
private fun CategoryPanel(
    title: String,
    categories: List<Category>,
    counts: Map<String, Int>,
    total: Int,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    LazyColumn(
        Modifier.width(168.dp).fillMaxHeight().background(ArkaColors.BackgroundAlt),
        contentPadding = PaddingValues(horizontal = ArkaSpacing.sm, vertical = ArkaSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Text(
                title.uppercase(),
                fontFamily = ArkaDisplayFont,
                fontWeight = FontWeight(800),
                style = ArkaTheme.type.title,
                modifier = Modifier.padding(start = ArkaSpacing.sm, bottom = ArkaSpacing.sm),
            )
        }
        item { CategoryLine("Toutes", total, selected == null) { onSelect(null) } }
        items(categories) { category ->
            CategoryLine(category.name, counts[category.id] ?: 0, selected == category.id) { onSelect(category.id) }
        }
    }
}

@Composable
private fun CategoryLine(name: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(ArkaSize.tvControlMin)
            .focusHighlight(ArkaRadius.sm, 1f)
            .clip(RoundedCornerShape(ArkaRadius.sm))
            .background(if (selected) ArkaColors.SurfaceHigh else ArkaColors.BackgroundAlt)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Selection is marked by the red bar and the bold label, not by colour alone.
        Box(Modifier.width(3.dp).fillMaxHeight().background(if (selected) ArkaColors.Red else ArkaColors.BackgroundAlt))
        Text(
            name,
            color = if (selected) ArkaColors.TextPrimary else ArkaColors.TextSecondary,
            style = ArkaTheme.type.label,
            fontWeight = if (selected) FontWeight(700) else FontWeight(500),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = ArkaSpacing.sm),
        )
        Text(count.toString(), color = ArkaColors.TextSecondary, style = ArkaTheme.type.mono, modifier = Modifier.padding(end = ArkaSpacing.sm))
    }
}
