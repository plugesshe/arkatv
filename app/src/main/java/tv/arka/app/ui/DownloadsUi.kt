package tv.arka.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import tv.arka.app.data.DownloadState
import tv.arka.app.data.Downloads
import tv.arka.app.data.PlayItem
import java.util.Locale

private fun megabytes(bytes: Long): String =
    if (bytes >= 1024L * 1024 * 1024) String.format(Locale.FRANCE, "%.1f Go", bytes / (1024.0 * 1024 * 1024))
    else "${bytes / (1024 * 1024)} Mo"

/** "Téléchargements" section of Mon espace: progress, offline playback, removal, space left. */
@Composable
fun DownloadsSection() {
    val context = LocalContext.current
    val app = context.applicationContext
    var states by remember { mutableStateOf(listOf<DownloadState>()) }
    val entries = Downloads.entries
    LaunchedEffect(entries) {
        while (true) {
            states = withContext(Dispatchers.IO) { Downloads.states(app) }
            delay(1500)
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = ArkaTheme.margin),
        verticalArrangement = Arrangement.spacedBy(ArkaSpacing.sm),
    ) {
        Text(
            "Espace libre : " + megabytes(Downloads.freeSpace(app)) +
                ". Pour télécharger un film ou un épisode, lancez-le puis ouvrez Réglages dans le lecteur.",
            color = ArkaColors.TextSecondary,
            style = ArkaTheme.type.label,
            fontWeight = FontWeight(400),
        )
        if (entries.isEmpty()) {
            Text("Aucun téléchargement.", color = ArkaColors.TextSecondary, modifier = Modifier.padding(vertical = ArkaSpacing.lg))
        }
        states.forEach { state ->
            val entry = state.entry
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(ArkaRadius.md)).background(ArkaColors.Surface).padding(ArkaSpacing.md),
            ) {
                Text(entry.title, style = ArkaTheme.type.heading, fontWeight = FontWeight(700), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (entry.subtitle.isNotBlank()) {
                    Text(entry.subtitle, color = ArkaColors.TextSecondary, style = ArkaTheme.type.label, fontWeight = FontWeight(500), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val status = when (state.status) {
                    Downloads.DONE -> "Terminé · " + megabytes(state.done)
                    Downloads.FAILED -> "Échec du téléchargement"
                    Downloads.RUNNING -> if (state.total > 0) {
                        "${state.done * 100 / state.total} % · " + megabytes(state.done) + " sur " + megabytes(state.total)
                    } else {
                        megabytes(state.done) + " reçus"
                    }
                    else -> "En attente"
                }
                Text(status, style = ArkaTheme.type.mono, color = if (state.status == Downloads.FAILED) ArkaColors.Error else ArkaColors.TextPrimary, modifier = Modifier.padding(top = ArkaSpacing.xs))
                Row(Modifier.padding(top = ArkaSpacing.sm), horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm)) {
                    if (state.status == Downloads.DONE) {
                        ArkaChip("Lire", true) {
                            // The address is replaced by the local file when playback starts.
                            play(context, listOf(PlayItem(entry.key, entry.title, entry.subtitle, "", live = false, poster = entry.poster)), 0)
                        }
                    }
                    ArkaChip("Supprimer", false) { Downloads.remove(app, entry) }
                }
            }
        }
    }
}
