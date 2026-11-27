package tv.arka.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.arka.app.R
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.DisposableEffect
import tv.arka.app.data.PairingServer
import tv.arka.app.data.Repo
import tv.arka.app.data.Source
import tv.arka.app.data.XtreamApi
import java.util.UUID

private const val STEP_TYPE = 1
private const val STEP_FORM = 2
private const val STEP_READY = 3

/**
 * Adding a source in three steps: kind of source, connection details (checked before anything is
 * saved), then a summary while the catalogue loads. ArkaTV never ships any content of its own.
 */
@Composable
fun AddSourceScreen(onBack: (() -> Unit)?, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val tv = LocalIsTv.current
    var step by rememberSaveable { mutableIntStateOf(STEP_TYPE) }
    var xtream by rememberSaveable { mutableStateOf(true) }
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    // Never written to the saved instance state.
    var pass by remember { mutableStateOf("") }
    var epg by rememberSaveable { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    // "credentials" when the server answered but refused them, otherwise the connection problem.
    var errorKind by remember { mutableStateOf<String?>(null) }
    var errorDetail by remember { mutableStateOf("") }
    var added by remember { mutableStateOf<Source?>(null) }
    // Typing an address and a password with a remote is tedious: a page on the local network lets
    // a phone or a computer fill this form instead.
    var pairing by remember { mutableStateOf<PairingServer?>(null) }
    var pairingAddress by remember { mutableStateOf("") }
    DisposableEffect(Unit) {
        onDispose { pairing?.stop() }
    }

    BackHandler(enabled = step == STEP_FORM) { step = STEP_TYPE }

    val stepBack: (() -> Unit)? = when (step) {
        STEP_FORM -> ({ step = STEP_TYPE })
        STEP_TYPE -> onBack
        else -> null
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = ArkaTheme.margin, vertical = ArkaSpacing.lg),
        horizontalAlignment = if (tv) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ArkaSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (stepBack != null && !tv) {
                    Box(
                        Modifier.size(ArkaSize.touchMin).clip(CircleShape).clickable(onClick = stepBack),
                        contentAlignment = Alignment.Center,
                    ) { ArkaIcon(R.drawable.ic_arka_retour, "Retour") }
                }
                Text(
                    when (step) {
                        STEP_TYPE -> "ÉTAPE 1 SUR 3"
                        STEP_FORM -> (if (xtream) "XTREAM CODES" else "PLAYLIST M3U") + " · ÉTAPE 2 SUR 3"
                        else -> "ÉTAPE 3 SUR 3"
                    },
                    color = ArkaColors.TextSecondary,
                    style = ArkaTheme.type.mono,
                )
            }

            when (step) {
                STEP_TYPE -> {
                    Text("CONNECTEZ VOTRE SOURCE", style = ArkaTheme.type.display, fontSize = if (tv) 34.sp else 36.sp, lineHeight = 34.sp)
                    Text(
                        "ArkaTV lit les contenus de votre fournisseur. Aucun contenu n'est inclus dans l'application.",
                        color = ArkaColors.TextSecondary,
                        style = ArkaTheme.type.body,
                    )
                    KindCard(
                        "Xtream Codes",
                        "Adresse du serveur, identifiant et mot de passe. Films, séries, direct et guide récupérés automatiquement.",
                        xtream,
                    ) { xtream = true }
                    KindCard("Playlist M3U", "Lien .m3u, avec guide XMLTV facultatif.", !xtream) { xtream = false }
                    PrimaryButton("Continuer", { step = STEP_FORM }, Modifier.fillMaxWidth())
                }

                STEP_FORM -> {
                    if (errorKind != null) {
                        Notice(
                            title = if (errorKind == "credentials") "Identifiants refusés" else "Connexion impossible",
                            text = if (errorKind == "credentials") {
                                "Le serveur est joignable mais refuse ce couple identifiant / mot de passe. " +
                                    "Respectez les majuscules : les identifiants viennent de votre fournisseur, pas d'ArkaTV."
                            } else {
                                errorDetail
                            },
                        )
                    }
                    ArkaField("Nom de la source", name, "Maison") { name = it }
                    ArkaField(
                        if (xtream) "Adresse du serveur" else "Lien de la playlist",
                        url,
                        if (xtream) "https://serveur.exemple:8080" else "https://exemple/playlist.m3u",
                        keyboard = KeyboardType.Uri,
                    ) { url = it }
                    if (url.trim().startsWith("http://", ignoreCase = true)) {
                        // Shown as soon as an http:// address is typed; it never blocks.
                        Notice(
                            title = "Connexion non chiffrée",
                            text = "Vos identifiants peuvent être lus sur le réseau. Utilisez https:// si votre fournisseur le propose.",
                        )
                    }
                    if (xtream) {
                        ArkaField(
                            "Nom d'utilisateur" + if (errorKind == "credentials") " · à vérifier" else "",
                            user,
                            "",
                        ) { user = it }
                        ArkaField(
                            "Mot de passe" + if (errorKind == "credentials") " · à vérifier" else "",
                            pass,
                            "",
                            keyboard = KeyboardType.Password,
                            hidden = !showPassword,
                            action = if (showPassword) "Masquer" else "Afficher",
                            onAction = { showPassword = !showPassword },
                        ) { pass = it }
                    }
                    ArkaField(
                        "Guide XMLTV personnalisé",
                        epg,
                        if (xtream) "Sinon, celui du serveur" else "Sinon, celui indiqué par la playlist",
                        keyboard = KeyboardType.Uri,
                    ) { epg = it }

                    val remote = pairing
                    ArkaChip(if (remote == null) "Remplir depuis un téléphone" else "Fermer la page de saisie", remote != null) {
                        if (remote != null) {
                            remote.stop()
                            pairing = null
                        } else {
                            val main = Handler(Looper.getMainLooper())
                            val fields = listOfNotNull(
                                PairingServer.Field("name", "Nom de la source (facultatif)", minLength = 0),
                                PairingServer.Field("url", if (xtream) "Adresse du serveur" else "Lien de la playlist", minLength = 8),
                                if (xtream) PairingServer.Field("user", "Nom d'utilisateur") else null,
                                if (xtream) PairingServer.Field("pass", "Mot de passe", secret = true) else null,
                            )
                            val created = PairingServer(fields) { values ->
                                main.post {
                                    values["name"]?.takeIf { it.isNotBlank() }?.let { name = it }
                                    url = values["url"].orEmpty()
                                    if (xtream) {
                                        user = values["user"].orEmpty()
                                        pass = values["pass"].orEmpty()
                                    }
                                    pairing = null
                                }
                            }
                            pairingAddress = PairingServer.localAddress().orEmpty()
                            if (pairingAddress.isNotEmpty() && created.start()) pairing = created
                        }
                    }
                    if (remote != null) {
                        Text(
                            "Sur un appareil du même réseau, ouvrez  http://$pairingAddress:${remote.port}  et saisissez le code  ${remote.code}. " +
                                "La page n'est pas chiffrée : à utiliser sur votre réseau domestique uniquement.",
                            style = ArkaTheme.type.body,
                        )
                    }

                    val complete = url.isNotBlank() && (!xtream || (user.isNotBlank() && pass.isNotBlank()))
                    if (busy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = ArkaColors.Red, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            Text("Vérification…", color = ArkaColors.TextSecondary, modifier = Modifier.padding(start = ArkaSpacing.md))
                        }
                    } else {
                        PrimaryButton(
                            if (errorKind != null) "Réessayer" else "Vérifier la connexion",
                            {
                                val source = Source(
                                    id = UUID.randomUUID().toString(),
                                    type = if (xtream) "xtream" else "m3u",
                                    name = name.trim().ifBlank { if (xtream) "Xtream" else "Playlist" },
                                    url = url.trim(),
                                    user = user.trim(),
                                    pass = pass.trim(),
                                    epgUrl = epg.trim(),
                                )
                                errorKind = null
                                if (xtream) {
                                    busy = true
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) { XtreamApi(source).authenticate() }
                                            Repo.addSource(source)
                                            added = source
                                            step = STEP_READY
                                        } catch (e: Exception) {
                                            val message = e.message ?: "Le serveur ne répond pas."
                                            errorKind = if (message.startsWith("Identifiants refusés")) "credentials" else "connection"
                                            errorDetail = message
                                        } finally {
                                            busy = false
                                        }
                                    }
                                } else {
                                    // A playlist is checked by loading it: the next step shows the outcome.
                                    Repo.addSource(source)
                                    added = source
                                    step = STEP_READY
                                }
                            },
                            Modifier.fillMaxWidth(),
                            enabled = complete,
                        )
                    }
                    if (tv) SecondaryButton("Retour", { step = STEP_TYPE })
                }

                else -> {
                    val source = added
                    val failed = Repo.error
                    val loaded = !Repo.loading && failed == null
                    Text(
                        if (failed != null) "SOURCE INACCESSIBLE" else "« " + (source?.name ?: "").uppercase() + " » " + if (loaded) "EST PRÊTE" else "SE CHARGE",
                        style = ArkaTheme.type.display,
                        fontSize = if (tv) 34.sp else 36.sp,
                        lineHeight = 34.sp,
                    )
                    if (failed != null) {
                        Notice("Chargement impossible", failed)
                        PrimaryButton("Modifier la source", {
                            source?.let { Repo.removeSource(it) }
                            added = null
                            errorKind = "connection"
                            errorDetail = failed
                            step = STEP_FORM
                        }, Modifier.fillMaxWidth())
                    } else {
                        CheckLine("Serveur joint", true)
                        if (xtream) CheckLine("Identifiants acceptés", true)
                        CheckLine(if (loaded) "Catalogue chargé" else "Chargement du catalogue…", loaded)
                        if (loaded) {
                            CheckLine(
                                if (Repo.epgLoading) "Guide TV en cours…" else if (Repo.epg.isEmpty()) "Guide TV : aucun pour l'instant" else "Guide TV chargé",
                                !Repo.epgLoading && Repo.epg.isNotEmpty(),
                            )
                            Row(Modifier.fillMaxWidth().padding(vertical = ArkaSpacing.sm), horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.xl)) {
                                Counter(Repo.movieCount, "films")
                                Counter(Repo.seriesCount, "séries")
                                Counter(Repo.channels.size, "chaînes")
                            }
                            val account = Repo.account
                            if (account != null) {
                                val parts = listOfNotNull(
                                    if (account.expiresAt > 0) "Expire le " + formatDate(account.expiresAt) else null,
                                    if (account.maxConnections > 0) "${account.maxConnections} connexion" + (if (account.maxConnections > 1) "s" else "") else null,
                                )
                                if (parts.isNotEmpty()) {
                                    Text(parts.joinToString(" · "), color = ArkaColors.TextSecondary, style = ArkaTheme.type.label, fontWeight = FontWeight(500))
                                }
                            }
                        }
                        PrimaryButton("Commencer", onDone, Modifier.fillMaxWidth(), enabled = loaded)
                    }
                }
            }
        }
    }
}

@Composable
private fun KindCard(title: String, text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(ArkaRadius.md)
    Column(
        Modifier.fillMaxWidth()
            .focusHighlight(ArkaRadius.md, ArkaMotion.FOCUS_SCALE_BUTTON)
            .clip(shape)
            .background(if (selected) ArkaColors.TextPrimary else ArkaColors.Surface)
            .clickable(onClick = onClick)
            .padding(ArkaSpacing.lg),
    ) {
        Text(title, color = if (selected) ArkaColors.Background else ArkaColors.TextPrimary, style = ArkaTheme.type.heading, fontWeight = FontWeight(700))
        Text(
            text,
            color = if (selected) ArkaColors.Background.copy(alpha = 0.75f) else ArkaColors.TextSecondary,
            style = ArkaTheme.type.body,
            modifier = Modifier.padding(top = ArkaSpacing.xs),
        )
    }
}

/** A warning that informs without blocking: icon, title and explanation, never colour alone. */
@Composable
fun Notice(title: String, text: String) {
    val shape = RoundedCornerShape(ArkaRadius.md)
    Row(Modifier.fillMaxWidth().clip(shape).border(1.dp, ArkaColors.Warning, shape).padding(ArkaSpacing.md)) {
        ArkaIcon(R.drawable.ic_arka_alerte, null, tint = ArkaColors.Warning, size = 20.dp)
        Column(Modifier.padding(start = ArkaSpacing.md)) {
            Text(title, style = ArkaTheme.type.label)
            if (text.isNotBlank()) {
                Text(text, color = ArkaColors.TextSecondary, style = ArkaTheme.type.label, fontWeight = FontWeight(400), modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** Labelled field of the design system: label above, surface box, light outline when focused. */
@Composable
fun ArkaField(
    label: String,
    value: String,
    placeholder: String,
    keyboard: KeyboardType = KeyboardType.Text,
    hidden: Boolean = false,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    onChange: (String) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(ArkaRadius.md)
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = ArkaColors.TextSecondary, style = ArkaTheme.type.label, fontWeight = FontWeight(500), modifier = Modifier.padding(bottom = 6.dp))
        Row(
            Modifier.fillMaxWidth().height(ArkaSize.touchMin).clip(shape).background(ArkaColors.Surface)
                .then(if (focused) Modifier.border(1.5.dp, ArkaColors.FocusOutline, shape) else Modifier)
                .padding(horizontal = ArkaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, color = ArkaColors.TextDisabled, style = ArkaTheme.type.body, maxLines = 1)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = ArkaTheme.type.body.copy(color = ArkaColors.TextPrimary),
                    cursorBrush = SolidColor(ArkaColors.RedBright),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                    visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                )
            }
            if (action != null && onAction != null) {
                Text(
                    action,
                    color = ArkaColors.TextSecondary,
                    style = ArkaTheme.type.label,
                    modifier = Modifier.clip(RoundedCornerShape(ArkaRadius.sm)).clickable(onClick = onAction).padding(8.dp),
                )
            }
        }
    }
}

/** One line of the set-up summary: a check once done, a spinner while in progress. */
@Composable
private fun CheckLine(text: String, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (done) {
            ArkaIcon(R.drawable.ic_arka_valide, null, tint = ArkaColors.Ok, size = 18.dp)
        } else {
            CircularProgressIndicator(color = ArkaColors.TextSecondary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        }
        Text(
            text,
            color = if (done) ArkaColors.TextPrimary else ArkaColors.TextSecondary,
            style = ArkaTheme.type.body,
            modifier = Modifier.padding(start = ArkaSpacing.md),
        )
    }
}

@Composable
fun Counter(value: Int, label: String) {
    Column {
        Text(String.format(java.util.Locale.FRANCE, "%,d", value), fontFamily = ArkaDisplayFont, fontWeight = FontWeight(800), fontSize = 26.sp)
        Text(label, color = ArkaColors.TextSecondary, style = ArkaTheme.type.label, fontWeight = FontWeight(500))
    }
}
