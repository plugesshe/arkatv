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
import tv.arka.app.data.ArkaConnect
import tv.arka.app.player.DeviceVideo
import tv.arka.app.data.PairingServer
import tv.arka.app.data.Store
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

/** "ArkaTV Connect" block of the settings: turns the phone remote on, and shows where to open it. */
@Composable
fun ConnectSettings() {
    var enabled by remember { mutableStateOf(Store.connectEnabled) }
    val address = remember(ArkaConnect.running) { PairingServer.localAddress().orEmpty() }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = ArkaTheme.margin).padding(bottom = ArkaSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(ArkaSpacing.sm),
    ) {
        Text("ArkaTV Connect", style = ArkaTheme.type.heading, fontWeight = FontWeight(700))
        Text(
            "Une télécommande dans le navigateur de votre téléphone : flèches, OK, Retour, lecture, et saisie de texte au clavier. " +
                "Elle ne commande qu'ArkaTV, sur votre réseau local.",
            color = ArkaColors.TextSecondary,
            style = ArkaTheme.type.label,
            fontWeight = FontWeight(400),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm)) {
            ArkaChip(if (enabled) "Télécommande depuis un téléphone : oui" else "Télécommande depuis un téléphone : non", enabled) {
                enabled = !enabled
                Store.connectEnabled = enabled
                if (enabled) ArkaConnect.start() else ArkaConnect.stop()
            }
        }
        if (enabled && ArkaConnect.running && address.isNotEmpty()) {
            Text(
                "Ouvrez  http://$address:${ArkaConnect.port}  sur un appareil du même réseau, code  ${ArkaConnect.code}.",
                style = ArkaTheme.type.body,
            )
        } else if (enabled) {
            Text(
                "La télécommande n'a pas pu démarrer : vérifiez que cet appareil est connecté à votre réseau local.",
                color = ArkaColors.TextSecondary,
                style = ArkaTheme.type.label,
            )
        }
    }
}

/** "Cet appareil" block of the settings: what it decodes, and whether what it cannot play is hidden. */
@Composable
fun DeviceSettings() {
    val capabilities = remember { DeviceVideo.capabilities }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = ArkaTheme.margin).padding(bottom = ArkaSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(ArkaSpacing.sm),
    ) {
        Text("Cet appareil", style = ArkaTheme.type.heading, fontWeight = FontWeight(700))
        Text(
            "ArkaTV demande à l'appareil ce qu'il sait décoder et ne montre que ce qu'il peut lire. " +
                "Un titre proposé en plusieurs versions pointe vers la meilleure version lisible ici.",
            color = ArkaColors.TextSecondary,
            style = ArkaTheme.type.label,
            fontWeight = FontWeight(400),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm)) {
            ArkaChip(
                if (Repo.hideIncompatible) "Masquer les contenus incompatibles : oui" else "Masquer les contenus incompatibles : non",
                Repo.hideIncompatible,
            ) { Repo.updateHideIncompatible(!Repo.hideIncompatible) }
            if (Repo.unplayable.isNotEmpty()) {
                ArkaChip("Réafficher les ${Repo.unplayable.size} titres en échec", false) { Repo.clearUnplayable() }
            }
        }
        if (!capabilities.known) {
            Text("L'appareil n'a pas répondu : rien n'est masqué, le lecteur s'adapte à la lecture.", style = ArkaTheme.type.body)
        } else {
            Text("Image (décodeurs matériels)", color = ArkaColors.TextSecondary, style = ArkaTheme.type.label, fontWeight = FontWeight(500))
            capabilities.video.forEach { Text(it, style = ArkaTheme.type.mono) }
            Text(
                "Écran : " + if (capabilities.hdr.isEmpty()) "SDR (pas de HDR)" else capabilities.hdr.joinToString(", "),
                style = ArkaTheme.type.mono,
            )
            Text("Son décodé par l'appareil", color = ArkaColors.TextSecondary, style = ArkaTheme.type.label, fontWeight = FontWeight(500))
            Text(
                if (capabilities.audio.isEmpty()) "Aucun format Dolby ou DTS" else capabilities.audio.joinToString(" · "),
                style = ArkaTheme.type.mono,
            )
            Text(
                "Les autres formats audio (TrueHD, DTS…) sont décodés par ArkaTV.",
                color = ArkaColors.TextSecondary,
                style = ArkaTheme.type.label,
                fontWeight = FontWeight(400),
            )
        }
    }
}
