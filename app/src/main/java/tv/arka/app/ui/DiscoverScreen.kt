package tv.arka.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.arka.app.R
import tv.arka.app.data.Repo
import tv.arka.app.data.Vod
import tv.arka.app.data.VodDetails
import tv.arka.app.data.VodKind
import kotlin.random.Random

/**
 * Discovery: pick a kind and any number of the source's categories, then draw a title at random
 * among the matches. Durations are not offered: sources do not list them, so the choice would be empty.
 */
@Composable
fun DiscoverScreen(onBack: () -> Unit, onOpen: (Vod) -> Unit) {
    val context = LocalContext.current
    val tv = LocalIsTv.current
    val hasMovies = Repo.movieCount > 0
    val hasSeries = Repo.seriesCount > 0
    var kindName by rememberSaveable { mutableStateOf(if (hasMovies) VodKind.MOVIE.name else VodKind.SERIES.name) }
    val kind = if (kindName == VodKind.SERIES.name) VodKind.SERIES else VodKind.MOVIE
    var chosen by remember(kind) { mutableStateOf(setOf<String>()) }
    val categories = if (kind == VodKind.MOVIE) Repo.vodCategories else Repo.seriesCategories

    var matches by remember { mutableIntStateOf(0) }
    var drawNumber by remember { mutableIntStateOf(0) }
    var position by remember { mutableIntStateOf(-1) }
    var pick by remember { mutableStateOf<Vod?>(null) }
    var details by remember { mutableStateOf<VodDetails?>(null) }
    // Bumped by "Tirer au sort" / "Autre tirage" to ask for a new title.
    var request by remember { mutableIntStateOf(0) }
    val version = Repo.catalogVersion

    LaunchedEffect(kind, chosen, version) {
        // New choices: recount and start a fresh series of draws.
        matches = Repo.discoverCount(kind, chosen)
        drawNumber = 0
        position = -1
        pick = null
        request++
    }
    LaunchedEffect(request) {
        if (request == 0 || matches == 0) return@LaunchedEffect
        var next = Random.nextInt(matches)
        if (matches > 1 && next == position) next = (next + 1) % matches
        val drawn = Repo.discoverPick(kind, chosen, next)
        if (drawn != null) {
            position = next
            pick = drawn
            drawNumber++
        }
    }
    LaunchedEffect(pick) {
        details = null
        val current = pick ?: return@LaunchedEffect
        details = try {
            Repo.details(current)
        } catch (e: Exception) {
            null
        }
    }

    val choices: @Composable () -> Unit = {
        Text(
            "CE SOIR, QUELQUE CHOSE DE…",
            style = ArkaTheme.type.display,
            fontSize = if (tv) 32.sp else 34.sp,
            lineHeight = if (tv) 30.sp else 32.sp,
            modifier = Modifier.padding(horizontal = ArkaTheme.margin),
        )
        if (hasMovies && hasSeries) {
            Row(
                Modifier.padding(start = ArkaTheme.margin, end = ArkaTheme.margin, top = ArkaSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm),
            ) {
                SegmentButton("Film", kind == VodKind.MOVIE, { kindName = VodKind.MOVIE.name }, Modifier.width(if (tv) 90.dp else 110.dp))
                SegmentButton("Série", kind == VodKind.SERIES, { kindName = VodKind.SERIES.name }, Modifier.width(if (tv) 90.dp else 110.dp))
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = ArkaTheme.margin, vertical = ArkaSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm),
        ) {
            items(categories) { category ->
                val selected = category.id in chosen
                ArkaChip(category.name, selected) {
                    chosen = if (selected) chosen - category.id else chosen + category.id
                }
            }
        }
    }

    val card: @Composable (Modifier) -> Unit = { modifier ->
        val current = pick
        Box(modifier.clip(RoundedCornerShape(ArkaRadius.md)).background(ArkaColors.Surface)) {
            if (current == null) {
                if (matches == 0 && request > 0) {
                    Centered {
                        Text(
                            "Aucun titre ne correspond à ces choix.",
                            color = ArkaColors.TextSecondary,
                            modifier = Modifier.padding(ArkaSpacing.lg),
                        )
                    }
                } else {
                    Box(Modifier.fillMaxSize().skeleton())
                }
            } else {
                val image = details?.backdrop.orEmpty().ifBlank { current.poster }
                if (image.isNotBlank()) ArkaImage(image, Modifier.fillMaxSize(), alignment = Alignment.TopCenter)
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.35f to Color.Transparent, 1f to ArkaColors.Background.copy(alpha = 0.95f))
                    )
                )
                Column(Modifier.align(Alignment.BottomStart).padding(ArkaSpacing.lg)) {
                    Text(
                        "TIRAGE $drawNumber SUR $matches CORRESPONDANCE" + if (matches > 1) "S" else "",
                        color = ArkaColors.VioletText,
                        style = ArkaTheme.type.mono,
                    )
                    Text(
                        current.name.uppercase(),
                        style = ArkaTheme.type.display,
                        fontSize = if (tv) 30.sp else 30.sp,
                        lineHeight = 29.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = ArkaSpacing.xs),
                    )
                    val meta = listOf(
                        details?.year.orEmpty().ifBlank { current.year },
                        details?.duration.orEmpty(),
                        details?.genre.orEmpty(),
                        vodMeta(current.copy(year = "")),
                    ).filter { it.isNotBlank() }.joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(
                            meta,
                            color = ArkaColors.TextTertiary,
                            style = ArkaTheme.type.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = ArkaSpacing.xs),
                        )
                    }
                    if (tv) {
                        Row(
                            Modifier.padding(top = ArkaSpacing.md),
                            horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm),
                        ) {
                            PrimaryButton(if (current.kind == VodKind.SERIES) "Voir" else "Lire", {
                                if (current.kind == VodKind.MOVIE) play(context, listOf(current.toPlayItem()), 0) else onOpen(current)
                            }, icon = R.drawable.ic_arka_lire)
                            SecondaryButton("Plus d'informations", { onOpen(current) })
                        }
                    }
                }
            }
        }
    }

    if (tv) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxHeight().fillMaxWidth(0.45f).padding(vertical = ArkaSpacing.xl)) {
                choices()
                if (matches > 0) {
                    AccentButton("Tirer au sort", { request++ }, Modifier.padding(horizontal = ArkaTheme.margin))
                }
            }
            card(Modifier.fillMaxSize().padding(ArkaSpacing.lg))
        }
    } else {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            ScreenHeader("Découvrir", onBack)
            choices()
            card(Modifier.weight(1f).fillMaxWidth().padding(horizontal = ArkaTheme.margin))
            val current = pick
            if (current != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = ArkaTheme.margin, vertical = ArkaSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm),
                ) {
                    PrimaryButton(
                        if (current.kind == VodKind.SERIES) "Voir" else "Lire",
                        {
                            if (current.kind == VodKind.MOVIE) play(context, listOf(current.toPlayItem()), 0) else onOpen(current)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    SquareButton(R.drawable.ic_arka_info, "Plus d'informations", { onOpen(current) })
                    AccentButton("Autre tirage", { request++ }, Modifier.weight(1f))
                }
            }
        }
    }
}
