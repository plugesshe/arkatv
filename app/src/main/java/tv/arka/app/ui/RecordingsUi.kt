package tv.arka.app.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import tv.arka.app.RecordingService
import tv.arka.app.data.PlayItem
import tv.arka.app.data.Recordings
import java.io.File

/** "Enregistrements" section of Mon espace. */
@Composable
fun RecordingsSection() {
    val context = LocalContext.current
    val active = Recordings.activeId
    Column(
        Modifier.fillMaxWidth().padding(horizontal = ArkaTheme.margin),
        verticalArrangement = Arrangement.spacedBy(ArkaSpacing.sm),
    ) {
        Text(
            "Pour enregistrer, ouvrez une chaîne en direct puis « Enregistrer » dans le lecteur. " +
                "L'enregistrement utilise une connexion de plus chez votre fournisseur.",
            color = ArkaColors.TextSecondary,
            style = ArkaTheme.type.label,
            fontWeight = FontWeight(400),
        )
        Recordings.lastError?.let { Text(it, color = ArkaColors.Error, style = ArkaTheme.type.label) }
        if (Recordings.entries.isEmpty()) {
            Text("Aucun enregistrement.", color = ArkaColors.TextSecondary, modifier = Modifier.padding(vertical = ArkaSpacing.lg))
        }
        Recordings.entries.forEach { entry ->
            val running = entry.id == active
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(ArkaRadius.md)).background(ArkaColors.Surface).padding(ArkaSpacing.md),
            ) {
                Text(entry.title.ifBlank { "Enregistrement" }, style = ArkaTheme.type.heading, fontWeight = FontWeight(700), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val size = if (running) Recordings.activeBytes else File(entry.path).length()
                Text(
                    formatDateTime(entry.startedAt / 1000) + " · ${size / (1024 * 1024)} Mo" + if (running) " · en cours" else "",
                    color = if (running) ArkaColors.RedBright else ArkaColors.TextSecondary,
                    style = ArkaTheme.type.mono,
                    modifier = Modifier.padding(top = ArkaSpacing.xs),
                )
                Row(Modifier.padding(top = ArkaSpacing.sm), horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm)) {
                    if (running) {
                        ArkaChip("Arrêter", true) { RecordingService.stop(context.applicationContext) }
                    } else {
                        ArkaChip("Lire", true) {
                            val item = PlayItem(
                                key = "rec:" + entry.id,
                                title = entry.title,
                                subtitle = "Enregistré le " + formatDateTime(entry.startedAt / 1000),
                                url = Uri.fromFile(File(entry.path)).toString(),
                                live = false,
                            )
                            play(context, listOf(item), 0)
                        }
                        ArkaChip("Supprimer", false) { Recordings.remove(entry) }
                    }
                }
            }
        }
    }
}
