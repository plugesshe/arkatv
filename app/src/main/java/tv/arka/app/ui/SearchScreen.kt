package tv.arka.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.arka.app.R
import tv.arka.app.data.Channel
import tv.arka.app.data.Repo
import tv.arka.app.data.Store
import tv.arka.app.data.TextNormalizer
import tv.arka.app.data.Vod
import tv.arka.app.data.VodKind
import tv.arka.app.data.VodSort

private const val HISTORY_SIZE = 8
private const val FILTER_ALL = 0
private const val FILTER_MOVIES = 1
private const val FILTER_SERIES = 2
private const val FILTER_CHANNELS = 3

private val keyboardRows = listOf("ABCDEF", "GHIJKL", "MNOPQR", "STUVWX", "YZ0123", "456789")

/**
 * Universal search over films, series and channels of the active source. Handsets type in the field;
 * TV uses the on-screen letter grid, which a remote can drive without a system keyboard.
 */
@Composable
fun SearchScreen(onOpen: (Vod) -> Unit, onCatalog: (String, VodKind, String?, VodSort) -> Unit) {
    val context = LocalContext.current
    val tv = LocalIsTv.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(FILTER_ALL) }
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
    val profileId = Repo.activeProfile.id

    LaunchedEffect(profileId) { history = Store.loadSearchHistory() }
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
    fun count(size: Int, more: Boolean): String = if (more) "$size+" else size.toString()

    val results: LazyListScope.() -> Unit = {
        if (normalized.length < 2) {
            idleContent(history, onPick = { query = it }, onRemove = { entry ->
                history = history - entry
                Store.saveSearchHistory(history)
            }, onCatalog = onCatalog)
        } else {
            val empty = channels.isEmpty() && movies.isEmpty() && series.isEmpty()
            if (empty && searching) {
                item { Text("Recherche…", color = ArkaColors.TextSecondary, modifier = Modifier.padding(ArkaTheme.margin)) }
            } else if (empty) {
                item {
                    Column(Modifier.padding(horizontal = ArkaTheme.margin, vertical = ArkaSpacing.xl)) {
                        Text("Aucun résultat pour « ${query.trim()} »", style = ArkaTheme.type.heading, fontWeight = FontWeight(700))
                        Text(
                            "Vérifiez l'orthographe ou cherchez un autre titre ou une chaîne. " +
                                "La recherche couvre la source active.",
                            color = ArkaColors.TextSecondary,
                            style = ArkaTheme.type.body,
                            modifier = Modifier.padding(top = ArkaSpacing.sm),
                        )
                    }
                }
                idleContent(history, onPick = { query = it }, onRemove = { entry ->
                    history = history - entry
                    Store.saveSearchHistory(history)
                }, onCatalog = onCatalog)
            } else {
                if (!tv) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = ArkaTheme.margin, vertical = ArkaSpacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm),
                        ) {
                            item { ArkaChip("Tout", filter == FILTER_ALL) { filter = FILTER_ALL } }
                            if (movies.isNotEmpty()) {
                                item { ArkaChip("Films · " + count(movies.size, moreMovies), filter == FILTER_MOVIES) { filter = FILTER_MOVIES } }
                            }
                            if (series.isNotEmpty()) {
                                item { ArkaChip("Séries · " + count(series.size, moreSeries), filter == FILTER_SERIES) { filter = FILTER_SERIES } }
                            }
                            if (channels.isNotEmpty()) {
                                item { ArkaChip("Chaînes · " + count(channels.size, moreChannels), filter == FILTER_CHANNELS) { filter = FILTER_CHANNELS } }
                            }
                        }
                    }
                }
                if (movies.isNotEmpty() && (filter == FILTER_ALL || filter == FILTER_MOVIES)) {
                    item { PagedVodRow("Films", movies, query, openVod) { if (moreMovies) loadMore(VodKind.MOVIE) } }
                }
                if (series.isNotEmpty() && (filter == FILTER_ALL || filter == FILTER_SERIES)) {
                    item { PagedVodRow("Séries", series, query, openVod) { if (moreSeries) loadMore(VodKind.SERIES) } }
                }
                if (channels.isNotEmpty() && (filter == FILTER_ALL || filter == FILTER_CHANNELS)) {
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
                        )
                    }
                    if (moreChannels) {
                        item {
                            Box(Modifier.padding(horizontal = ArkaTheme.margin, vertical = ArkaSpacing.sm)) {
                                SecondaryButton("Afficher plus de chaînes", { loadMore(null) })
                            }
                        }
                    }
                }
            }
        }
    }

    if (tv) {
        Row(Modifier.fillMaxSize().padding(vertical = ArkaSpacing.xl)) {
            Column(Modifier.width(250.dp).fillMaxHeight().padding(start = ArkaTheme.margin)) {
                Box(
                    Modifier.fillMaxWidth().height(ArkaSize.tvControlMin + 4.dp).clip(RoundedCornerShape(ArkaRadius.md))
                        .background(ArkaColors.Surface).padding(horizontal = ArkaSpacing.md),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        query.ifEmpty { "Un titre, une chaîne…" },
                        color = if (query.isEmpty()) ArkaColors.TextSecondary else ArkaColors.TextPrimary,
                        style = ArkaTheme.type.body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(Modifier.padding(top = ArkaSpacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    keyboardRows.forEach { letters ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            letters.forEach { letter ->
                                KeyCap(letter.toString(), Modifier.width(30.dp)) { query += letter.lowercaseChar() }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        KeyCap("Espace", Modifier.width(98.dp)) { if (query.isNotEmpty() && !query.endsWith(" ")) query += " " }
                        KeyCap("⌫ Effacer", Modifier.width(98.dp)) { query = query.dropLast(1) }
                    }
                }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = ArkaSpacing.xl), content = results)
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            val shape = RoundedCornerShape(ArkaRadius.md)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = ArkaTheme.margin, vertical = ArkaSpacing.md)
                    .height(ArkaSize.touchMin).clip(shape).background(ArkaColors.Surface)
                    .then(if (query.isNotEmpty()) Modifier.border(1.5.dp, ArkaColors.TextPrimary, shape) else Modifier)
                    .padding(horizontal = ArkaSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArkaIcon(R.drawable.ic_arka_recherche, null, tint = ArkaColors.TextSecondary, size = 20.dp)
                Box(Modifier.weight(1f).padding(horizontal = ArkaSpacing.md), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text("Un titre, une chaîne…", color = ArkaColors.TextSecondary, style = ArkaTheme.type.body)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = ArkaTheme.type.body.copy(color = ArkaColors.TextPrimary),
                        cursorBrush = SolidColor(ArkaColors.RedBright),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    Text(
                        "Effacer",
                        color = ArkaColors.TextSecondary,
                        style = ArkaTheme.type.label,
                        modifier = Modifier.clip(RoundedCornerShape(ArkaRadius.sm)).clickable { query = "" }.padding(8.dp),
                    )
                }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = ArkaSpacing.xl), content = results)
        }
    }
}

/** Shown before typing and under "no result": recent searches, then the source's categories as genres. */
private fun LazyListScope.idleContent(
    history: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onCatalog: (String, VodKind, String?, VodSort) -> Unit,
) {
    if (history.isNotEmpty()) {
        item { SectionTitle("Recherches récentes") }
        items(history) { entry ->
            Row(
                Modifier.fillMaxWidth().focusHighlight(ArkaRadius.sm, 1f).clickable { onPick(entry) }
                    .padding(horizontal = ArkaTheme.margin),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(entry, style = ArkaTheme.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(vertical = ArkaSpacing.md))
                Box(Modifier.clip(RoundedCornerShape(ArkaRadius.pill)).clickable { onRemove(entry) }.padding(ArkaSpacing.sm)) {
                    ArkaIcon(R.drawable.ic_arka_fermer, "Retirer de l'historique", tint = ArkaColors.TextSecondary, size = 16.dp)
                }
            }
        }
    }
    val genres = Repo.vodCategories.take(8)
    if (genres.isNotEmpty()) {
        item { SectionTitle("Genres") }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = ArkaTheme.margin, vertical = ArkaSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm),
            ) {
                items(genres) { genre ->
                    ArkaChip(genre.name, false) { onCatalog("Films", VodKind.MOVIE, genre.id, VodSort.RECENT) }
                }
            }
        }
    }
}

@Composable
private fun KeyCap(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(30.dp)
            .focusHighlight(ArkaRadius.sm, ArkaMotion.FOCUS_SCALE_BUTTON)
            .clip(RoundedCornerShape(ArkaRadius.sm))
            .background(ArkaColors.SurfaceHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = ArkaTheme.type.label, maxLines = 1)
    }
}

/** A poster row that asks for the next page when its last poster comes into view. */
@Composable
private fun PagedVodRow(title: String, items: List<Vod>, highlight: String, onOpen: (Vod) -> Unit, onEndReached: () -> Unit) {
    val width = posterWidth
    Column {
        SectionTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = ArkaTheme.margin, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(if (LocalIsTv.current) 14.dp else 10.dp),
        ) {
            itemsIndexed(items) { index, vod ->
                Poster(vod, Modifier.width(width), highlight = highlight) { onOpen(vod) }
                if (index == items.lastIndex) {
                    LaunchedEffect(items.size) { onEndReached() }
                }
            }
        }
    }
}
