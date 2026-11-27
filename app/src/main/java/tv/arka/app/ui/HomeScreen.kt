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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import tv.arka.app.R
import tv.arka.app.data.Category
import tv.arka.app.data.Progress
import tv.arka.app.data.Repo
import tv.arka.app.data.Vod
import tv.arka.app.data.VodKind
import tv.arka.app.data.VodSort

/**
 * Home: a hero that fades into the background, then rows.
 * On handsets, Films and Séries are reached from the chips at the top; on TV, from the side bar.
 */
@Composable
fun HomeScreen(onOpen: (Vod) -> Unit, onCatalog: (String, VodKind, String?, VodSort) -> Unit) {
    val context = LocalContext.current
    val tv = LocalIsTv.current
    val latestMovies = Repo.latestMovies
    val latestSeries = Repo.latestSeries
    val featured = remember(latestMovies, latestSeries) {
        val pool = latestSeries.take(5) + latestMovies.take(5)
        pool.firstOrNull { it.poster.isNotBlank() } ?: pool.firstOrNull()
    }
    val resume = Repo.progress
    val myList = Repo.myList

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = ArkaSpacing.xxl)) {
        if (featured != null) {
            item {
                Hero(
                    vod = featured,
                    onPlay = {
                        if (featured.kind == VodKind.MOVIE) play(context, listOf(featured.toPlayItem()), 0)
                        else onOpen(featured)
                    },
                    onOpen = { onOpen(featured) },
                    onCatalog = onCatalog,
                )
            }
        } else {
            item {
                Column(Modifier.statusBarsPadding().padding(horizontal = ArkaTheme.margin, vertical = ArkaSpacing.md)) {
                    Logo()
                }
            }
        }

        if (Repo.movieCount == 0 && Repo.seriesCount == 0) {
            item { EmptyState() }
        }

        if (resume.isNotEmpty()) {
            item {
                SectionTitle("Continuer à regarder")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = ArkaTheme.margin, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (tv) 14.dp else 10.dp),
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
        if (latestMovies.isNotEmpty()) {
            item {
                VodRow("Nouveaux films", latestMovies, onOpen) {
                    onCatalog("Films", VodKind.MOVIE, null, VodSort.RECENT)
                }
            }
        }
        if (latestSeries.isNotEmpty()) {
            item {
                VodRow("Nouvelles séries", latestSeries, onOpen) {
                    onCatalog("Séries", VodKind.SERIES, null, VodSort.RECENT)
                }
            }
        }
        items(Repo.vodCategories) { category ->
            CategoryVodRow(VodKind.MOVIE, category, onOpen) {
                onCatalog(category.name, VodKind.MOVIE, category.id, VodSort.PROVIDER)
            }
        }
        items(Repo.seriesCategories) { category ->
            CategoryVodRow(VodKind.SERIES, category, onOpen) {
                onCatalog(category.name, VodKind.SERIES, category.id, VodSort.PROVIDER)
            }
        }
    }
}

/** A home row whose posters are read from the stored catalogue when the row comes into view. */
@Composable
private fun CategoryVodRow(kind: VodKind, category: Category, onOpen: (Vod) -> Unit, onMore: () -> Unit) {
    val version = Repo.catalogVersion
    var items by remember(kind, category.id, version) { mutableStateOf(Repo.cachedRow(kind, category.id)) }
    LaunchedEffect(kind, category.id, version) {
        if (items == null) items = Repo.categoryRow(kind, category.id)
    }
    val loaded = items
    if (loaded == null) {
        // Skeleton: same footprint as the loaded row, so nothing jumps when it arrives.
        Column {
            SectionTitle(category.name)
            Row(
                Modifier.padding(horizontal = ArkaTheme.margin, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(3) {
                    Box(
                        Modifier.width(posterWidth).aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(ArkaRadius.sm)).background(ArkaColors.Surface)
                    )
                }
            }
        }
    } else if (loaded.isNotEmpty()) {
        VodRow(category.name, loaded, onOpen, onMore)
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(ArkaSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val error = Repo.error
        when {
            Repo.loading -> {
                CircularProgressIndicator(color = ArkaColors.Red)
                Spacer(Modifier.height(ArkaSpacing.md))
                Text("Chargement de vos sources…", color = ArkaColors.TextSecondary)
            }
            error != null -> {
                Text(error, color = ArkaColors.Error, textAlign = TextAlign.Center)
                TextButton(onClick = { Repo.reload() }) { Text("Réessayer") }
            }
            else -> Text(
                "Cette source ne contient ni films ni séries. Les chaînes sont dans Direct.",
                color = ArkaColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Hero(
    vod: Vod,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    onCatalog: (String, VodKind, String?, VodSort) -> Unit,
) {
    val tv = LocalIsTv.current
    val margin = ArkaTheme.margin
    val playLabel = if (vod.kind == VodKind.SERIES) "Voir" else "Lire"
    val inList = Repo.inMyList(vod)
    // On TV the main action takes the initial focus when the screen opens.
    val mainAction = remember { FocusRequester() }
    if (tv) {
        LaunchedEffect(vod.id) {
            try {
                mainAction.requestFocus()
            } catch (e: IllegalStateException) {
                // The button is not laid out yet: the first key press will focus it instead.
            }
        }
    }

    Box(Modifier.fillMaxWidth().height(if (tv) 300.dp else 520.dp)) {
        if (vod.poster.isNotBlank()) {
            AsyncImage(
                model = vod.poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = if (tv) {
                    Modifier.align(Alignment.TopEnd).fillMaxHeight().fillMaxWidth(0.6f)
                } else {
                    Modifier.fillMaxSize()
                },
            )
        }
        // Legibility veil under the text: the only shadow the design system allows.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to ArkaColors.Background.copy(alpha = 0.55f),
                    0.25f to Color.Transparent,
                    0.55f to ArkaColors.Background.copy(alpha = 0.6f),
                    1f to ArkaColors.Background,
                )
            )
        )
        if (tv) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to ArkaColors.Background,
                        0.45f to ArkaColors.Background.copy(alpha = 0.7f),
                        1f to Color.Transparent,
                    )
                )
            )
        }

        if (!tv) {
            Row(
                Modifier.statusBarsPadding().padding(horizontal = margin, vertical = ArkaSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm),
            ) {
                Logo(wordmark = false)
                Spacer(Modifier.width(ArkaSpacing.xs))
                if (Repo.movieCount > 0) {
                    ArkaChip("Films", false) { onCatalog("Films", VodKind.MOVIE, null, VodSort.PROVIDER) }
                }
                if (Repo.seriesCount > 0) {
                    ArkaChip("Séries", false) { onCatalog("Séries", VodKind.SERIES, null, VodSort.PROVIDER) }
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomStart).padding(horizontal = margin, vertical = ArkaSpacing.lg)
                .then(if (tv) Modifier.widthIn(max = 420.dp) else Modifier.fillMaxWidth()),
        ) {
            Text(
                if (vod.kind == VodKind.SERIES) "SÉRIE" else "FILM",
                color = ArkaColors.TextSecondary,
                style = ArkaTheme.type.label,
                letterSpacing = 1.5.sp,
            )
            Text(
                vod.name.uppercase(),
                style = ArkaTheme.type.display,
                maxLines = if (tv) 2 else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = ArkaSpacing.sm),
            )
            val meta = vodMeta(vod)
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    color = ArkaColors.TextTertiary,
                    style = ArkaTheme.type.label,
                    modifier = Modifier.padding(top = ArkaSpacing.sm),
                )
            }
            if (tv && vod.plot.isNotBlank()) {
                Text(
                    vod.plot,
                    color = ArkaColors.TextTertiary,
                    style = ArkaTheme.type.body,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = ArkaSpacing.sm),
                )
            }
            Row(
                Modifier.padding(top = ArkaSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm),
            ) {
                PrimaryButton(
                    playLabel,
                    onPlay,
                    modifier = (if (tv) Modifier else Modifier.weight(1f)).focusRequester(mainAction),
                    icon = R.drawable.ic_arka_lire,
                )
                if (tv) {
                    SecondaryButton("Plus d'informations", onOpen)
                }
                SquareButton(
                    icon = if (inList) R.drawable.ic_arka_valide else R.drawable.ic_arka_ajouter,
                    contentDescription = if (inList) "Dans ma liste" else "Ajouter à ma liste",
                    onClick = { Repo.toggleMyList(vod) },
                    active = inList,
                )
                if (!tv) {
                    SquareButton(R.drawable.ic_arka_info, "Plus d'informations", onOpen)
                }
            }
        }
    }
}

/** "Ma liste" toggle used on detail screens: shows a check once the title is in the list. */
@Composable
fun MyListButton(vod: Vod) {
    val inList = Repo.inMyList(vod)
    SecondaryButton(
        if (inList) "Dans ma liste" else "Ma liste",
        onClick = { Repo.toggleMyList(vod) },
        icon = if (inList) R.drawable.ic_arka_valide else R.drawable.ic_arka_ajouter,
    )
}

/** 16:9 thumbnail with the playback progress along its bottom edge. */
@Composable
private fun ResumeCard(entry: Progress, onClick: () -> Unit) {
    val tv = LocalIsTv.current
    Column(Modifier.width(if (tv) 190.dp else 220.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .focusHighlight()
                .clip(RoundedCornerShape(ArkaRadius.sm))
                .background(ArkaColors.Surface)
                .clickable(onClick = onClick),
        ) {
            if (entry.item.poster.isNotBlank()) {
                AsyncImage(
                    model = entry.item.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            LinearProgressIndicator(
                progress = { (entry.position.toFloat() / entry.duration.coerceAtLeast(1)).coerceIn(0f, 1f) },
                color = ArkaColors.Red,
                trackColor = ArkaColors.Border,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
            )
        }
        Text(
            entry.item.title,
            style = ArkaTheme.type.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        val remaining = ((entry.duration - entry.position) / 60_000).coerceAtLeast(1)
        val detail = listOfNotNull(entry.item.subtitle.takeIf { it.isNotBlank() }, "Reste $remaining min")
            .joinToString(" · ")
        Text(
            detail,
            color = ArkaColors.TextSecondary,
            fontSize = if (tv) 11.sp else 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** A fixed list of titles shown as a grid (Ma liste). */
@Composable
fun GridScreen(title: String, items: List<Vod>, onBack: () -> Unit, onOpen: (Vod) -> Unit) {
    val tv = LocalIsTv.current
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenHeader(title, onBack)
        if (items.isEmpty()) {
            Centered { Text("Votre liste est vide.", color = ArkaColors.TextSecondary) }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (tv) 104.dp else 100.dp),
                contentPadding = PaddingValues(horizontal = ArkaTheme.margin, vertical = ArkaSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(if (tv) 14.dp else 10.dp),
                verticalArrangement = Arrangement.spacedBy(ArkaSpacing.lg),
            ) {
                items(items) { vod ->
                    Poster(vod) { onOpen(vod) }
                }
            }
        }
    }
}

/** Screen title in the condensed display face, with a back arrow on handsets. */
@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)?, trailing: String? = null) {
    val tv = LocalIsTv.current
    Row(
        Modifier.fillMaxWidth()
            .padding(start = if (tv || onBack == null) ArkaTheme.margin else 4.dp, end = ArkaTheme.margin, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null && !tv) {
            Box(
                Modifier.width(ArkaSize.touchMin).height(ArkaSize.touchMin)
                    .clip(RoundedCornerShape(ArkaRadius.pill)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                ArkaIcon(R.drawable.ic_arka_retour, "Retour")
            }
        }
        Text(
            title.uppercase(),
            fontFamily = ArkaDisplayFont,
            fontWeight = FontWeight(800),
            fontSize = if (tv) 24.sp else 28.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(trailing, color = ArkaColors.TextSecondary, style = ArkaTheme.type.mono)
        }
    }
}
