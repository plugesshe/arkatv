@file:OptIn(ExperimentalMaterial3Api::class)

package tv.arka.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import tv.arka.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.arka.app.data.Downloads
import tv.arka.app.data.Recordings
import tv.arka.app.data.Repo
import tv.arka.app.data.Source
import tv.arka.app.data.Store
import tv.arka.app.data.Vod
import tv.arka.app.data.XtreamApi
import java.util.UUID

private class MenuEntry(val label: String, val detail: String, val action: () -> Unit)

private const val SECTION_MENU = 0
private const val SECTION_SOURCES = 1
private const val SECTION_HISTORY = 2
private const val SECTION_SETTINGS = 3
private const val SECTION_DOWNLOADS = 4
private const val SECTION_RECORDINGS = 5

/** Mon espace entry screen: the profile, followed series, then the sections. TV shows tiles instead of a list. */
@Composable
private fun ProfileMenu(
    onOpen: (Vod) -> Unit,
    onList: (String, List<Vod>) -> Unit,
    onProfiles: () -> Unit,
    onSection: (Int) -> Unit,
) {
    val tv = LocalIsTv.current
    val margin = ArkaTheme.margin
    val profile = Repo.activeProfile
    Row(
        Modifier.fillMaxWidth().padding(horizontal = margin, vertical = ArkaSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(profile, size = if (tv) 44.dp else 56.dp)
        Column(Modifier.weight(1f).padding(horizontal = ArkaSpacing.md)) {
            Text(
                if (tv) profile.name.uppercase() else profile.name,
                fontFamily = if (tv) ArkaDisplayFont else ArkaFont,
                style = ArkaTheme.type.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                (if (profile.isMain) "Profil principal" else "Profil") + " · ${Repo.sources.size} source" + if (Repo.sources.size > 1) "s" else "",
                color = ArkaColors.TextSecondary,
                style = ArkaTheme.type.label,
                fontWeight = FontWeight(500),
            )
        }
        SecondaryButton("Changer de profil", onProfiles)
    }

    // A child profile has no access to the sources nor to the settings.
    val kids = Repo.activeProfile.kids
    val entries = listOfNotNull(
        MenuEntry("Ma liste", if (Repo.myList.isEmpty()) "vide" else "${Repo.myList.size} titres") { onList("Ma liste", Repo.myList) },
        MenuEntry("Séries suivies", if (Repo.followed.isEmpty()) "aucune" else "${Repo.followed.size} séries") {
            onList("Séries suivies", Repo.followed)
        },
        MenuEntry("Historique", if (Repo.progress.isEmpty()) "vide" else "${Repo.progress.size} en cours") { onSection(SECTION_HISTORY) },
        if (kids) null else MenuEntry("Téléchargements", if (Downloads.entries.isEmpty()) "aucun" else Downloads.entries.size.toString()) { onSection(SECTION_DOWNLOADS) },
        if (kids) null else MenuEntry("Enregistrements", if (Recordings.entries.isEmpty()) "aucun" else Recordings.entries.size.toString()) { onSection(SECTION_RECORDINGS) },
        if (kids) null else MenuEntry("Sources", Repo.sources.size.toString()) { onSection(SECTION_SOURCES) },
        if (kids) null else MenuEntry("Paramètres", "") { onSection(SECTION_SETTINGS) },
    )

    if (tv) {
        entries.chunked(4).forEach { row ->
            Row(
                Modifier.padding(horizontal = margin, vertical = ArkaSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.md),
            ) {
                row.forEach { entry ->
                    val label = entry.label
                    val detail = entry.detail
                    val action = entry.action
                    Column(
                        Modifier.width(180.dp).height(84.dp)
                            .focusHighlight(ArkaRadius.md, ArkaMotion.FOCUS_SCALE_BUTTON)
                            .clip(RoundedCornerShape(ArkaRadius.md))
                            .background(ArkaColors.Surface)
                            .clickable(onClick = action)
                            .padding(ArkaSpacing.md),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(label, style = ArkaTheme.type.heading, fontWeight = FontWeight(700))
                        Text(detail, color = ArkaColors.TextSecondary, style = ArkaTheme.type.mono)
                    }
                }
            }
        }
    } else {
        if (Repo.followed.isNotEmpty()) {
            Text(
                "Séries suivies",
                style = ArkaTheme.type.heading,
                fontWeight = FontWeight(700),
                modifier = Modifier.padding(horizontal = margin, vertical = ArkaSpacing.sm),
            )
            Repo.followed.take(3).forEach { series ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = margin, vertical = 4.dp)
                        .clip(RoundedCornerShape(ArkaRadius.md))
                        .background(ArkaColors.Surface)
                        .clickable { onOpen(series) }
                        .padding(ArkaSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(72.dp).height(42.dp).clip(RoundedCornerShape(ArkaRadius.sm)).background(ArkaColors.SurfaceHigh)) {
                        if (series.poster.isNotBlank()) ArkaImage(series.poster, Modifier.fillMaxSize())
                    }
                    Text(
                        series.name,
                        style = ArkaTheme.type.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = ArkaSpacing.md),
                    )
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = margin, vertical = ArkaSpacing.lg)
                .clip(RoundedCornerShape(ArkaRadius.md)).background(ArkaColors.Surface),
        ) {
            entries.forEach { entry ->
                val label = entry.label
                val detail = entry.detail
                val action = entry.action
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clickable(onClick = action).padding(horizontal = ArkaSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = ArkaTheme.type.label, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text("$detail ›".trim(), color = ArkaColors.TextSecondary, style = ArkaTheme.type.label, fontWeight = FontWeight(500))
                }
            }
        }
    }
}

/** What the profile was watching: resume from here, or clear the list. */
@Composable
private fun HistorySection() {
    val context = LocalContext.current
    val margin = ArkaTheme.margin
    if (Repo.progress.isEmpty()) {
        Text("Rien en cours de lecture.", color = ArkaColors.TextSecondary, modifier = Modifier.padding(margin))
        return
    }
    Repo.progress.forEach { entry ->
        Row(
            Modifier.fillMaxWidth()
                .focusHighlight(ArkaRadius.sm, 1f)
                .clickable { play(context, listOf(entry.item), 0) }
                .padding(horizontal = margin, vertical = ArkaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(96.dp).height(54.dp).clip(RoundedCornerShape(ArkaRadius.sm)).background(ArkaColors.Surface)) {
                if (entry.item.poster.isNotBlank()) ArkaImage(entry.item.poster, Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f).padding(start = ArkaSpacing.md)) {
                Text(entry.item.title, style = ArkaTheme.type.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val left = ((entry.duration - entry.position) / 60_000).coerceAtLeast(1)
                Text(
                    listOfNotNull(entry.item.subtitle.takeIf { it.isNotBlank() }, "reste $left min").joinToString(" · "),
                    color = ArkaColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    Box(Modifier.padding(margin)) {
        SecondaryButton("Effacer l'historique", { Repo.clearProgress() })
    }
}

@Composable
fun ProfileScreen(
    onAddSource: () -> Unit,
    onOpen: (Vod) -> Unit,
    onList: (String, List<Vod>) -> Unit,
    onProfiles: () -> Unit,
    onParental: () -> Unit,
) {
    var liveFormat by remember { mutableStateOf(Store.liveFormat) }
    var livePause by remember { mutableStateOf(Store.livePause) }
    var toDelete by remember { mutableStateOf<Source?>(null) }
    var refreshHours by remember { mutableIntStateOf(Store.catalogRefreshHours) }
    var toEdit by remember { mutableStateOf<Source?>(null) }
    var chosenSection by rememberSaveable { mutableIntStateOf(SECTION_MENU) }
    // Switching to a child profile while a protected section is open falls back to the menu.
    val protectedSection = chosenSection == SECTION_SOURCES || chosenSection == SECTION_SETTINGS ||
        chosenSection == SECTION_DOWNLOADS || chosenSection == SECTION_RECORDINGS
    val section = if (Repo.activeProfile.kids && protectedSection) SECTION_MENU else chosenSection
    BackHandler(enabled = section != SECTION_MENU) { chosenSection = SECTION_MENU }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        if (section == SECTION_MENU) {
            ProfileMenu(onOpen, onList, onProfiles) { chosenSection = it }
        } else {
            ScreenHeader(
                when (section) {
                    SECTION_SOURCES -> "Sources"
                    SECTION_HISTORY -> "Historique"
                    SECTION_DOWNLOADS -> "Téléchargements"
                    SECTION_RECORDINGS -> "Enregistrements"
                    else -> "Paramètres"
                },
                onBack = { chosenSection = SECTION_MENU },
            )
        }

        if (section == SECTION_HISTORY) {
            HistorySection()
        }
        if (section == SECTION_DOWNLOADS) {
            DownloadsSection()
        }
        if (section == SECTION_RECORDINGS) {
            RecordingsSection()
        }

        if (section == SECTION_SOURCES) {
        Repo.sources.forEach { source ->
            val active = Repo.active?.id == source.id
            val shape = RoundedCornerShape(ArkaRadius.md)
            Column(
                Modifier.fillMaxWidth().padding(horizontal = ArkaTheme.margin, vertical = 6.dp)
                    .focusHighlight(ArkaRadius.md, 1f)
                    .clip(shape)
                    .background(ArkaColors.Surface)
                    .then(if (active) Modifier.border(1.dp, ArkaColors.Border, shape) else Modifier)
                    .clickable { if (!active) Repo.select(source) }
                    .padding(ArkaSpacing.lg),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(source.name, style = ArkaTheme.type.heading, fontWeight = FontWeight(700), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        // State: a coloured dot always comes with its label.
                        val kind = if (source.type == "xtream") "Xtream" else "M3U"
                        val (dot, state) = when {
                            !active -> ArkaColors.TextDisabled to "Non active"
                            Repo.loading || Repo.refreshing -> ArkaColors.Warning to "Actualisation…"
                            Repo.error != null || Repo.syncError != null -> ArkaColors.Error to "Injoignable"
                            else -> ArkaColors.Ok to "Connectée"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                            Box(Modifier.size(8.dp).clip(RoundedCornerShape(ArkaRadius.pill)).background(dot))
                            Text("  $state · $kind", color = ArkaColors.TextSecondary, style = ArkaTheme.type.label, fontWeight = FontWeight(500))
                        }
                    }
                    if (active) {
                        Box(Modifier.size(ArkaSize.touchMin).clip(RoundedCornerShape(ArkaRadius.pill)).clickable { Repo.reload() }, contentAlignment = Alignment.Center) {
                            ArkaIcon(R.drawable.ic_arka_actualiser, "Actualiser")
                        }
                    }
                    Box(Modifier.size(ArkaSize.touchMin).clip(RoundedCornerShape(ArkaRadius.pill)).clickable { toEdit = source }, contentAlignment = Alignment.Center) {
                        ArkaIcon(R.drawable.ic_arka_plus_options, "Renommer ou déplacer", tint = ArkaColors.TextSecondary)
                    }
                    Box(Modifier.size(ArkaSize.touchMin).clip(RoundedCornerShape(ArkaRadius.pill)).clickable { toDelete = source }, contentAlignment = Alignment.Center) {
                        ArkaIcon(R.drawable.ic_arka_fermer, "Supprimer", tint = ArkaColors.TextSecondary)
                    }
                }
                if (active) {
                    Row(Modifier.padding(top = ArkaSpacing.md), horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.xl)) {
                        Counter(Repo.movieCount, "films")
                        Counter(Repo.seriesCount, "séries")
                        Counter(Repo.channels.size, "chaînes")
                    }
                    val account = Repo.account
                    val details = listOfNotNull(
                        account?.takeIf { it.expiresAt > 0 }?.let { "Expire le " + formatDate(it.expiresAt) },
                        account?.takeIf { it.maxConnections > 0 }?.let { "${it.activeConnections} connexion(s) utilisée(s) sur ${it.maxConnections}" },
                        if (Repo.lastSync > 0) "Actualisée le " + formatDateTime(Repo.lastSync / 1000) else null,
                        if (Repo.epgLoading) "Guide en cours de mise à jour" else "Guide : ${Repo.epg.size} chaînes",
                    )
                    Text(
                        details.joinToString(" · "),
                        color = ArkaColors.TextSecondary,
                        style = ArkaTheme.type.label,
                        fontWeight = FontWeight(500),
                        modifier = Modifier.padding(top = ArkaSpacing.sm),
                    )
                    val problem = Repo.error ?: Repo.syncError
                    if (problem != null && !Repo.refreshing && !Repo.loading) {
                        Text(
                            "Actualisation impossible ($problem). Le dernier catalogue enregistré reste affiché ; la lecture demande toujours une connexion.",
                            color = ArkaColors.Error,
                            style = ArkaTheme.type.label,
                            fontWeight = FontWeight(500),
                            modifier = Modifier.padding(top = ArkaSpacing.xs),
                        )
                    }
                }
            }
        }
        Box(Modifier.padding(horizontal = ArkaTheme.margin, vertical = ArkaSpacing.md)) {
            SecondaryButton("Ajouter une source", onAddSource, icon = R.drawable.ic_arka_ajouter)
        }
        }

        if (section == SECTION_SETTINGS) {
        UpdateSettings()
        TmdbSettings()
        ParentalSettings(onParental)
        Text(
            "Format des chaînes en direct (Xtream)",
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("ts" to "MPEG-TS", "m3u8" to "HLS").forEach { (value, label) ->
                ArkaChip(label, liveFormat == value) {
                    if (liveFormat != value) {
                        liveFormat = value
                        Repo.applyLiveFormat(value)
                    }
                }
            }
        }
        Text(
            "Si une chaîne ne démarre pas, l'autre format est essayé automatiquement.",
            color = ArkaMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            ArkaChip(if (livePause) "Pause du direct (expérimental) : oui" else "Pause du direct (expérimental) : non", livePause) {
                livePause = !livePause
                Store.livePause = livePause
            }
        }
        Text(
            "En pause, la chaîne (format MPEG-TS) est conservée sur l'appareil pour reprendre là où vous étiez. " +
                "Si le direct ne démarre plus, désactivez cette option.",
            color = ArkaMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Text(
            "Actualisation automatique du catalogue",
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(6 to "6 h", 12 to "12 h", 24 to "24 h", 0 to "Jamais").forEach { (hours, label) ->
                ArkaChip(label, refreshHours == hours) {
                    refreshHours = hours
                    Store.catalogRefreshHours = hours
                }
            }
        }
        Text(
            "Vérifiée à l'ouverture de l'application, jamais en arrière-plan. " +
                "L'actualisation manuelle reste toujours possible.",
            color = ArkaMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        SectionTitle("À propos")
        Text(
            "ArkaTV 0.1.0 — lecteur pour vos propres playlists. L'application ne fournit aucun contenu.",
            color = ArkaMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        }
    }

    toEdit?.let { source ->
        var name by remember(source.id) { mutableStateOf(source.name) }
        val index = Repo.sources.indexOfFirst { it.id == source.id }
        AlertDialog(
            onDismissRequest = { toEdit = null },
            containerColor = ArkaSurface,
            title = { Text("Modifier la source") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nom") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row {
                        TextButton(enabled = index > 0, onClick = { Repo.moveSource(source, -1) }) { Text("Monter") }
                        TextButton(enabled = index in 0 until Repo.sources.lastIndex, onClick = { Repo.moveSource(source, 1) }) {
                            Text("Descendre")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    Repo.renameSource(source, name)
                    toEdit = null
                }) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { toEdit = null }) { Text("Fermer") } },
        )
    }

    toDelete?.let { source ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = ArkaSurface,
            title = { Text("Supprimer « ${source.name} » ?") },
            text = { Text("Ses favoris, sa liste et sa progression de lecture seront aussi supprimés.") },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    Repo.removeSource(source)
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annuler") } },
        )
    }
}
