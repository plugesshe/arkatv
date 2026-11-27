@file:OptIn(ExperimentalMaterial3Api::class)

package tv.arka.app.ui

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
import tv.arka.app.data.Repo
import tv.arka.app.data.Source
import tv.arka.app.data.Store
import tv.arka.app.data.Vod
import tv.arka.app.data.XtreamApi
import java.util.UUID

@Composable
fun ProfileScreen(onAddSource: () -> Unit, onOpen: (Vod) -> Unit, onList: (String, List<Vod>) -> Unit) {
    var liveFormat by remember { mutableStateOf(Store.liveFormat) }
    var toDelete by remember { mutableStateOf<Source?>(null) }
    var toEdit by remember { mutableStateOf<Source?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text(
            "Mon espace",
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
        )

        SectionTitle("Sources")
        Repo.sources.forEach { source ->
            val active = Repo.active?.id == source.id
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) ArkaRedDark else ArkaCard)
                    .clickable { if (!active) Repo.select(source) }
                    .padding(start = 14.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(source.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (if (source.type == "xtream") "Xtream Codes" else "Playlist M3U") + if (active) " · active" else "",
                        color = ArkaMuted,
                        fontSize = 12.sp,
                    )
                    val account = if (active) Repo.account else null
                    if (account != null) {
                        val parts = listOfNotNull(
                            if (account.expiresAt > 0) "Expire le " + formatDate(account.expiresAt) else null,
                            if (account.maxConnections > 0) {
                                "${account.activeConnections} connexion(s) utilisée(s) sur ${account.maxConnections}"
                            } else {
                                null
                            },
                        )
                        if (parts.isNotEmpty()) {
                            Text(parts.joinToString(" · "), color = ArkaMuted, fontSize = 12.sp)
                        }
                    }
                }
                if (active) {
                    IconButton(onClick = { Repo.reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Recharger")
                    }
                }
                IconButton(onClick = { toEdit = source }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Renommer ou déplacer", tint = ArkaMuted)
                }
                IconButton(onClick = { toDelete = source }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Supprimer", tint = ArkaMuted)
                }
            }
        }
        TextButton(onClick = onAddSource, modifier = Modifier.padding(horizontal = 8.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Ajouter une source")
        }

        val status = when {
            Repo.loading -> "Chargement en cours…"
            Repo.error != null -> "Erreur : ${Repo.error}"
            else -> "${Repo.channels.size} chaînes · ${Repo.movieCount} films · ${Repo.seriesCount} séries" +
                if (Repo.epgLoading) " · guide en cours de mise à jour" else " · guide : ${Repo.epg.size} chaînes"
        }
        Text(status, color = ArkaMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
        val sync = when {
            Repo.refreshing -> "Actualisation du catalogue en arrière-plan…"
            Repo.syncError != null ->
                "Actualisation impossible (${Repo.syncError}). Le dernier catalogue enregistré reste affiché ; " +
                    "la lecture demande toujours une connexion."
            Repo.lastSync > 0 -> "Catalogue actualisé le " + formatDateTime(Repo.lastSync / 1000)
            else -> ""
        }
        if (sync.isNotBlank()) {
            Text(
                sync,
                color = if (Repo.syncError != null && !Repo.refreshing) ArkaError else ArkaMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (Repo.myList.isNotEmpty()) {
            VodRow("Ma liste", Repo.myList, onOpen) { onList("Ma liste", Repo.myList) }
        }

        SectionTitle("Réglages")
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

        SectionTitle("À propos")
        Text(
            "ArkaTV 0.1.0 — lecteur pour vos propres playlists. L'application ne fournit aucun contenu.",
            color = ArkaMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
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

@Composable
fun AddSourceScreen(onBack: (() -> Unit)?, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var type by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var epg by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                }
            }
            Logo()
        }
        Text("Ajouter une source", fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text(
            "Renseignez l'accès fourni par votre opérateur. Tout reste stocké sur cet appareil.",
            color = ArkaMuted,
            fontSize = 13.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ArkaChip("Xtream Codes", type == 0) { type = 0 }
            ArkaChip("Playlist M3U", type == 1) { type = 1 }
        }

        Field("Nom (libre)", name) { name = it }
        if (type == 0) {
            Field("URL du serveur (http://exemple.com:8080)", url, KeyboardType.Uri) { url = it }
            Field("Identifiant", user) { user = it }
            Field("Mot de passe", pass, KeyboardType.Password, password = true) { pass = it }
            Field("URL du guide XMLTV (facultatif)", epg, KeyboardType.Uri) { epg = it }
        } else {
            Field("URL de la playlist M3U", url, KeyboardType.Uri) { url = it }
            Field("URL du guide XMLTV (facultatif)", epg, KeyboardType.Uri) { epg = it }
        }

        if (url.trim().startsWith("http://", ignoreCase = true)) {
            Text(
                "Cette adresse utilise HTTP : la connexion avec le fournisseur, identifiants compris, " +
                    "n'est pas chiffrée. Utilisez l'adresse https:// si votre fournisseur en propose une.",
                color = ArkaMuted,
                fontSize = 12.sp,
            )
        }
        error?.let { Text(it, color = ArkaError, fontSize = 13.sp) }

        Button(
            enabled = !busy && url.isNotBlank() && (type == 1 || (user.isNotBlank() && pass.isNotBlank())),
            onClick = {
                val source = Source(
                    id = UUID.randomUUID().toString(),
                    type = if (type == 0) "xtream" else "m3u",
                    name = name.trim().ifBlank { if (type == 0) "Xtream" else "Playlist" },
                    url = url.trim(),
                    user = user.trim(),
                    pass = pass.trim(),
                    epgUrl = epg.trim(),
                )
                error = null
                if (type == 0) {
                    busy = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { XtreamApi(source).authenticate() }
                            Repo.addSource(source)
                            onDone()
                        } catch (e: Exception) {
                            error = e.message ?: "Connexion impossible"
                        } finally {
                            busy = false
                        }
                    }
                } else {
                    Repo.addSource(source)
                    onDone()
                }
            },
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ArkaRed, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.height(20.dp).width(20.dp))
            } else {
                Text("Se connecter")
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}
