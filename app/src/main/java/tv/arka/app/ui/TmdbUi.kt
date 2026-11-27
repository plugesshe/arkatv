package tv.arka.app.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import tv.arka.app.data.PairingServer
import tv.arka.app.data.Repo
import tv.arka.app.data.Vod

/** "Catalogue enrichi" block of the settings: the user's TMDB key, the progress, the home choice. */
@Composable
fun TmdbSettings() {
    val tv = LocalIsTv.current
    var draft by remember { mutableStateOf("") }
    var server by remember { mutableStateOf<PairingServer?>(null) }
    var address by remember { mutableStateOf("") }
    DisposableEffect(Unit) {
        onDispose { server?.stop() }
    }
    val hasKey = Repo.tmdbToken.isNotBlank()
    // The page is no longer needed once a key has arrived.
    LaunchedEffect(hasKey) {
        if (hasKey) {
            server?.stop()
            server = null
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = ArkaTheme.margin).padding(bottom = ArkaSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(ArkaSpacing.sm),
    ) {
        Text("Catalogue enrichi (TMDB)", style = ArkaTheme.type.heading, fontWeight = FontWeight(700))
        Text(
            "Avec votre clé TMDB, ArkaTV reconnaît les films et séries de votre source, regroupe leurs versions " +
                "et construit ses propres rangées à l'accueil. Seuls des titres et des années sont envoyés à TMDB.",
            color = ArkaColors.TextSecondary,
            style = ArkaTheme.type.label,
            fontWeight = FontWeight(400),
        )
        if (!hasKey) {
            if (!tv) {
                ArkaField("Jeton d'accès en lecture TMDB", draft, "eyJ…") { draft = it.trim() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm)) {
                if (!tv) ArkaChip("Enregistrer", draft.length >= 20) { if (draft.length >= 20) Repo.updateTmdbToken(draft) }
                ArkaChip(if (server == null) "Saisir depuis un autre appareil" else "Fermer la page de saisie", server != null) {
                    val running = server
                    if (running != null) {
                        running.stop()
                        server = null
                    } else {
                        val main = Handler(Looper.getMainLooper())
                        val created = PairingServer("Jeton d'accès en lecture TMDB") { value ->
                            main.post { Repo.updateTmdbToken(value) }
                        }
                        address = PairingServer.localAddress().orEmpty()
                        if (address.isNotEmpty() && created.start()) server = created
                    }
                }
            }
            val running = server
            if (running != null) {
                Text(
                    "Sur un téléphone ou un ordinateur du même réseau, ouvrez  http://$address:${running.port}  " +
                        "et saisissez le code  ${running.code}.",
                    style = ArkaTheme.type.body,
                )
            } else if (address.isEmpty() && tv) {
                Text("Cette TV doit être connectée à votre réseau local pour la saisie à distance.", color = ArkaColors.TextSecondary, style = ArkaTheme.type.label)
            }
        } else {
            val status = Repo.tmdbStatus
            Text(
                "${Repo.tmdbMatched} titres reconnus sur ${Repo.tmdbChecked} analysés" + if (status.isNotBlank()) " · $status" else "",
                style = ArkaTheme.type.body,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm)) {
                ArkaChip(if (Repo.smartHome) "Accueil organisé par ArkaTV : oui" else "Accueil organisé par ArkaTV : non", Repo.smartHome) {
                    Repo.updateSmartHome(!Repo.smartHome)
                }
                ArkaChip("Supprimer la clé", false) { Repo.updateTmdbToken("") }
            }
        }
        Text(
            "Ce produit utilise l'API TMDB mais n'est ni approuvé ni certifié par TMDB.",
            color = ArkaColors.TextSecondary,
            style = ArkaTheme.type.label,
            fontWeight = FontWeight(400),
        )
    }
}

/** On a detail screen: the other versions of the same title that the source offers. */
@Composable
fun VariantChips(vod: Vod, onOpen: (Vod) -> Unit) {
    var variants by remember(vod.id) { mutableStateOf(listOf<Vod>()) }
    LaunchedEffect(vod.id, Repo.tmdbMatched > 0) {
        variants = Repo.variants(vod)
    }
    if (variants.size < 2) return
    Column(Modifier.padding(top = ArkaSpacing.sm)) {
        Text("Versions", color = ArkaColors.TextSecondary, style = ArkaTheme.type.label, fontWeight = FontWeight(500))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm), modifier = Modifier.padding(top = ArkaSpacing.xs)) {
            items(variants) { variant ->
                val label = variant.tags.ifBlank { "Version ${variants.indexOf(variant) + 1}" }
                ArkaChip(label, variant.id == vod.id) { if (variant.id != vod.id) onOpen(variant) }
            }
        }
    }
}
