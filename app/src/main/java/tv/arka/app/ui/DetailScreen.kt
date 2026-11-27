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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.arka.app.R
import tv.arka.app.data.Episode
import tv.arka.app.data.PlayItem
import tv.arka.app.data.Progress
import tv.arka.app.data.Repo
import tv.arka.app.data.Vod
import tv.arka.app.data.TitleCleaner
import tv.arka.app.data.VodDetails
import tv.arka.app.data.toPlayItem
import tv.arka.app.data.VodKind

private fun episodeItem(vod: Vod, episode: Episode): PlayItem = episode.toPlayItem(vod)

private fun remainingMinutes(progress: Progress): Long = ((progress.duration - progress.position) / 60_000).coerceAtLeast(1)

/** The episode to offer first: the one last watched, else the first not yet seen, else the very first. */
private fun nextEpisode(seasons: Map<Int, List<Episode>>, progress: List<Progress>, watched: Set<String>): Episode? {
    val all = seasons.keys.sorted().flatMap { seasons[it].orEmpty() }
    if (all.isEmpty()) return null
    val byKey = all.associateBy { "e:${it.id}" }
    progress.sortedByDescending { it.updated }.firstNotNullOfOrNull { byKey[it.item.key] }?.let { return it }
    return all.firstOrNull { "e:${it.id}" !in watched } ?: all.first()
}

/** Film or series sheet. Actions that the source cannot serve (download, trailer) are simply absent. */
@Composable
fun DetailScreen(vod: Vod, onBack: () -> Unit, onOpen: (Vod) -> Unit) {
    val context = LocalContext.current
    val tv = LocalIsTv.current
    var details by remember(vod.kind, vod.id) { mutableStateOf<VodDetails?>(null) }
    var similar by remember(vod.kind, vod.id) { mutableStateOf(listOf<Vod>()) }
    LaunchedEffect(vod.kind, vod.id, Repo.catalogVersion) {
        similar = Repo.similar(vod)
    }
    LaunchedEffect(vod.kind, vod.id) {
        details = try {
            Repo.details(vod)
        } catch (e: Exception) {
            VodDetails(plot = vod.plot, year = vod.year)
        }
    }
    val loaded = details
    val series = vod.kind == VodKind.SERIES
    val seasons = loaded?.seasons.orEmpty()
    val seasonNumbers = seasons.keys.sorted()
    val progress = Repo.progress
    val watched = Repo.watched
    val upNext = remember(seasons, progress, watched) { nextEpisode(seasons, progress, watched) }
    var selectedSeason by remember(vod.id) { mutableIntStateOf(-1) }
    val season = when {
        selectedSeason in seasonNumbers -> selectedSeason
        upNext != null -> upNext.season
        else -> seasonNumbers.firstOrNull()
    }
    val episodes = if (season != null) seasons[season].orEmpty() else emptyList()

    val movieKey = "m:${vod.id}"
    val movieProgress = if (series) null else Repo.progressFor(movieKey)
    val movieWatched = movieKey in watched
    val playEpisode: (Episode, Boolean) -> Unit = { episode, startOver ->
        val list = seasons[episode.season].orEmpty()
        play(context, list.map { episodeItem(vod, it) }, list.indexOf(episode).coerceAtLeast(0), startOver)
    }

    val meta = listOf(
        loaded?.year.orEmpty().ifBlank { vod.year },
        if (seasonNumbers.isNotEmpty()) "${seasonNumbers.size} saison" + (if (seasonNumbers.size > 1) "s" else "") else "",
        loaded?.duration.orEmpty(),
        loaded?.genre.orEmpty(),
        vodMeta(vod.copy(year = "")),
    ).filter { it.isNotBlank() }.joinToString(" · ")

    // The main action: there is exactly one red button on the sheet.
    val mainLabel: String
    val mainProgress: Progress?
    val onMain: () -> Unit
    if (series) {
        val episodeProgress = upNext?.let { Repo.progressFor("e:${it.id}") }
        mainProgress = episodeProgress
        mainLabel = when {
            upNext == null -> "Lire"
            episodeProgress != null -> "Reprendre S${upNext.season} · É${upNext.number}"
            else -> "Lire S${upNext.season} · É${upNext.number}"
        }
        onMain = { upNext?.let { playEpisode(it, false) } }
    } else {
        mainProgress = movieProgress
        mainLabel = if (movieProgress != null) "Reprendre · reste ${remainingMinutes(movieProgress)} min" else "Lire"
        onMain = { play(context, listOf(vod.toPlayItem()), 0) }
    }
    val mainEnabled = !series || upNext != null

    val mainFocus = remember { FocusRequester() }
    if (tv) {
        LaunchedEffect(vod.id, mainEnabled) {
            try {
                if (mainEnabled) mainFocus.requestFocus()
            } catch (e: IllegalStateException) {
                // Not laid out yet: the first key press focuses it.
            }
        }
    }

    val headline: @Composable () -> Unit = {
        Text(
            vod.name.uppercase(),
            style = ArkaTheme.type.display,
            fontSize = if (tv) 36.sp else 38.sp,
            lineHeight = if (tv) 34.sp else 36.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (meta.isNotBlank()) {
            Text(
                meta,
                color = ArkaColors.TextTertiary,
                style = ArkaTheme.type.label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = ArkaSpacing.sm),
            )
        }
    }
    val mainButton: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier) {
            PrimaryButton(
                mainLabel,
                onMain,
                modifier = Modifier.fillMaxWidth().focusRequester(mainFocus),
                icon = R.drawable.ic_arka_lire,
                enabled = mainEnabled,
            )
            if (mainProgress != null) {
                LinearProgressIndicator(
                    progress = { (mainProgress.position.toFloat() / mainProgress.duration.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    color = ArkaColors.Red,
                    trackColor = ArkaColors.Border,
                    modifier = Modifier.padding(top = ArkaSpacing.sm).fillMaxWidth().height(3.dp),
                )
            }
        }
    }
    val credits: @Composable () -> Unit = {
        if (loaded != null) {
            if (loaded.plot.isNotBlank()) {
                Text(
                    loaded.plot,
                    color = ArkaColors.TextTertiary,
                    style = ArkaTheme.type.body,
                    maxLines = if (tv) 4 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (loaded.director.isNotBlank()) Credit("Réalisation", loaded.director)
            if (loaded.cast.isNotBlank()) Credit("Avec", loaded.cast)
        } else {
            Box(Modifier.fillMaxWidth().height(if (tv) 40.dp else 64.dp).clip(RoundedCornerShape(ArkaRadius.sm)).skeleton())
        }
    }

    if (tv) {
        if (series) {
            Row(Modifier.fillMaxSize().padding(horizontal = ArkaTheme.margin, vertical = ArkaSpacing.xl)) {
                Column(Modifier.width(300.dp).fillMaxHeight()) {
                    headline()
                    Spacer(Modifier.height(ArkaSpacing.sm))
                    credits()
                    mainButton(Modifier.padding(top = ArkaSpacing.md))
                    Row(
                        Modifier.padding(top = ArkaSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm),
                    ) {
                        FollowButton(vod)
                        MyListButton(vod)
                    }
                    Spacer(Modifier.height(ArkaSpacing.md))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(seasonNumbers) { number ->
                            SeasonLine(number, seasons[number].orEmpty(), watched, number == season) { selectedSeason = number }
                        }
                    }
                }
                LazyColumn(
                    Modifier.fillMaxSize().padding(start = ArkaSpacing.xl),
                    contentPadding = PaddingValues(vertical = ArkaSpacing.sm, horizontal = ArkaSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(ArkaSpacing.sm),
                ) {
                    items(episodes) { episode ->
                        EpisodeRow(vod, episode, "e:${episode.id}" in watched) { playEpisode(episode, false) }
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                Backdrop(loaded, vod, Modifier.align(Alignment.TopEnd).fillMaxHeight(0.75f).fillMaxWidth(0.6f))
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(0f to ArkaColors.Background, 0.5f to ArkaColors.Background.copy(alpha = 0.8f), 1f to Color.Transparent)
                    )
                )
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = ArkaSpacing.xl)) {
                    item {
                        Column(Modifier.padding(horizontal = ArkaTheme.margin).width(440.dp)) {
                            headline()
                            Spacer(Modifier.height(ArkaSpacing.sm))
                            credits()
                            Row(
                                Modifier.padding(top = ArkaSpacing.md),
                                horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm),
                                verticalAlignment = Alignment.Top,
                            ) {
                                mainButton(Modifier.width(190.dp))
                                if (movieProgress != null) {
                                    SecondaryButton("Recommencer", { play(context, listOf(vod.toPlayItem()), 0, startOver = true) })
                                }
                                MyListButton(vod)
                                SecondaryButton(
                                    if (movieWatched) "Vu" else "Marquer vu",
                                    { Repo.setWatched(movieKey, !movieWatched) },
                                    icon = if (movieWatched) R.drawable.ic_arka_valide else R.drawable.ic_arka_vu,
                                )
                            }
                        }
                    }
                    similarRow(similar, onOpen)
                }
            }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize().systemBarsPadding(), contentPadding = PaddingValues(bottom = ArkaSpacing.xxl)) {
            item {
                Box(Modifier.fillMaxWidth().height(380.dp)) {
                    Backdrop(loaded, vod, Modifier.fillMaxSize())
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(0.3f to Color.Transparent, 0.75f to ArkaColors.Background.copy(alpha = 0.8f), 1f to ArkaColors.Background)
                        )
                    )
                    Box(
                        Modifier.padding(ArkaSpacing.md).size(40.dp).clip(CircleShape)
                            .background(ArkaColors.Background.copy(alpha = 0.6f)).clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        ArkaIcon(R.drawable.ic_arka_retour, "Retour", size = 22.dp)
                    }
                    Column(Modifier.align(Alignment.BottomStart).padding(horizontal = ArkaTheme.margin)) {
                        headline()
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = ArkaTheme.margin)) {
                    mainButton(Modifier.padding(top = ArkaSpacing.md).fillMaxWidth())
                    if (series) {
                        Row(
                            Modifier.padding(top = ArkaSpacing.md),
                            horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm),
                        ) {
                            FollowButton(vod)
                            MyListButton(vod)
                        }
                    } else {
                        // Labelled icon actions; an action that does not apply is not drawn.
                        Row(Modifier.fillMaxWidth().padding(top = ArkaSpacing.md), horizontalArrangement = Arrangement.SpaceAround) {
                            if (movieProgress != null) {
                                IconAction(R.drawable.ic_arka_reculer_10, "Recommencer", false) {
                                    play(context, listOf(vod.toPlayItem()), 0, startOver = true)
                                }
                            }
                            val inList = Repo.inMyList(vod)
                            IconAction(
                                if (inList) R.drawable.ic_arka_valide else R.drawable.ic_arka_ajouter,
                                if (inList) "Dans ma liste" else "Ma liste",
                                inList,
                            ) { Repo.toggleMyList(vod) }
                            IconAction(R.drawable.ic_arka_vu, if (movieWatched) "Vu" else "Marquer vu", movieWatched) {
                                Repo.setWatched(movieKey, !movieWatched)
                            }
                        }
                    }
                    Spacer(Modifier.height(ArkaSpacing.lg))
                    credits()
                }
            }
            if (series) {
                if (seasonNumbers.isNotEmpty()) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = ArkaTheme.margin, vertical = ArkaSpacing.md),
                            horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.lg),
                        ) {
                            items(seasonNumbers) { number -> SeasonTab(number, number == season) { selectedSeason = number } }
                        }
                    }
                }
                items(episodes) { episode ->
                    EpisodeRow(vod, episode, "e:${episode.id}" in watched) { playEpisode(episode, false) }
                }
            }
            similarRow(similar, onOpen)
        }
    }
}

private fun LazyListScope.similarRow(similar: List<Vod>, onOpen: (Vod) -> Unit) {
    if (similar.isNotEmpty()) {
        item { VodRow("Contenus similaires", similar, onOpen) }
    }
}

@Composable
private fun Backdrop(details: VodDetails?, vod: Vod, modifier: Modifier) {
    val image = details?.backdrop.orEmpty().ifBlank { vod.poster }
    Box(modifier.background(ArkaColors.Surface)) {
        if (image.isNotBlank()) ArkaImage(image, Modifier.fillMaxSize(), alignment = Alignment.TopCenter)
    }
}

@Composable
private fun Credit(label: String, value: String) {
    Row(Modifier.padding(top = ArkaSpacing.sm)) {
        Text("$label ", style = ArkaTheme.type.label, color = ArkaColors.TextPrimary)
        Text(value, style = ArkaTheme.type.label, fontWeight = FontWeight(400), color = ArkaColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** "Suivie" keeps the series in the "Séries suivies" row of the home screen. */
@Composable
private fun FollowButton(vod: Vod) {
    val followed = Repo.isFollowed(vod)
    SecondaryButton(
        if (followed) "Suivie" else "Suivre",
        { Repo.toggleFollow(vod) },
        icon = if (followed) R.drawable.ic_arka_valide else R.drawable.ic_arka_ajouter,
    )
}

@Composable
private fun IconAction(icon: Int, label: String, active: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(ArkaRadius.sm)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArkaIcon(icon, null, tint = if (active) ArkaColors.RedBright else ArkaColors.TextPrimary)
        Text(
            label,
            color = ArkaColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight(700) else FontWeight(500),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Handset season tab: the selected one is bold and underlined in red. */
@Composable
private fun SeasonTab(number: Int, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Saison $number",
            color = if (selected) ArkaColors.TextPrimary else ArkaColors.TextSecondary,
            style = ArkaTheme.type.body,
            fontWeight = if (selected) FontWeight(700) else FontWeight(400),
            modifier = Modifier.padding(vertical = ArkaSpacing.sm),
        )
        Box(Modifier.height(2.dp).width(56.dp).background(if (selected) ArkaColors.Red else Color.Transparent))
    }
}

/** TV season line: red bar and bold label when selected, with the number of episodes. */
@Composable
private fun SeasonLine(number: Int, episodes: List<Episode>, watched: Set<String>, selected: Boolean, onClick: () -> Unit) {
    val seen = episodes.isNotEmpty() && episodes.all { "e:${it.id}" in watched }
    Row(
        Modifier.fillMaxWidth().height(ArkaSize.tvControlMin)
            .focusHighlight(ArkaRadius.sm, 1f)
            .clip(RoundedCornerShape(ArkaRadius.sm))
            .background(if (selected) ArkaColors.SurfaceHigh else Color.Transparent)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(if (selected) ArkaColors.Red else Color.Transparent))
        Text(
            "Saison $number · ${episodes.size} ép." + if (seen) " · vue" else "",
            color = if (selected) ArkaColors.TextPrimary else ArkaColors.TextSecondary,
            style = ArkaTheme.type.label,
            fontWeight = if (selected) FontWeight(700) else FontWeight(500),
            maxLines = 1,
            modifier = Modifier.padding(start = ArkaSpacing.sm),
        )
    }
}

@Composable
private fun EpisodeRow(vod: Vod, episode: Episode, watched: Boolean, onClick: () -> Unit) {
    val tv = LocalIsTv.current
    val progress = Repo.progressFor("e:${episode.id}")
    Row(
        Modifier.fillMaxWidth()
            .focusHighlight(ArkaRadius.sm, 1f)
            .clip(RoundedCornerShape(ArkaRadius.sm))
            .clickable(onClick = onClick)
            .padding(horizontal = if (tv) ArkaSpacing.sm else ArkaTheme.margin, vertical = ArkaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(if (tv) 150.dp else 128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(ArkaRadius.sm)).background(ArkaColors.Surface)) {
            val image = episode.image.ifBlank { vod.poster }
            if (image.isNotBlank()) ArkaImage(image, Modifier.fillMaxSize())
            if (watched) {
                ArkaBadge("VU", Modifier.padding(6.dp))
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { (progress.position.toFloat() / progress.duration.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    color = ArkaColors.Red,
                    trackColor = ArkaColors.Border,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = ArkaSpacing.md)) {
            Text(
                "${episode.number}. " + TitleCleaner.episode(episode.title).ifBlank { "Épisode ${episode.number}" },
                color = if (watched) ArkaColors.TextSecondary else ArkaColors.TextPrimary,
                style = ArkaTheme.type.label,
                fontSize = if (tv) 13.sp else 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val state = when {
                progress != null -> "reste ${remainingMinutes(progress)} min"
                watched -> "Vu"
                else -> ""
            }
            val line = listOf(if (episode.durationMin > 0) "${episode.durationMin} min" else "", state)
                .filter { it.isNotBlank() }.joinToString(" · ")
            if (line.isNotBlank()) {
                Text(line, color = ArkaColors.TextSecondary, fontSize = if (tv) 11.sp else 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            if (tv && episode.plot.isNotBlank()) {
                Text(
                    episode.plot,
                    color = ArkaColors.TextTertiary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
