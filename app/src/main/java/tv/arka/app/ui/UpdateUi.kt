package tv.arka.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import tv.arka.app.data.Store
import tv.arka.app.data.Updater

/** Checks once when the app opens (unless turned off) and offers the new build in a dialog. */
@Composable
fun UpdatePrompt() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var dismissed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (Store.autoUpdateCheck) Updater.check(context, silent = true)
    }
    val state = Updater.state
    val info = when (state) {
        is Updater.State.Available -> state.info
        is Updater.State.Downloading -> state.info
        is Updater.State.Ready -> state.info
        else -> null
    }
    if (dismissed || info == null) return

    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = { dismissed = true }) {
        Column(
            Modifier.widthIn(max = 440.dp).fillMaxWidth()
                .clip(RoundedCornerShape(ArkaRadius.lg))
                .background(ArkaColors.SurfaceHigh)
                .padding(ArkaSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(ArkaSpacing.md),
        ) {
            Text("Mise à jour disponible", style = ArkaTheme.type.heading, fontWeight = FontWeight(700))
            Text(
                "ArkaTV ${info.versionName} · build ${info.versionCode}" + if (info.size > 0) " · ${info.size / (1024 * 1024)} Mo" else "",
                color = ArkaColors.TextSecondary,
                style = ArkaTheme.type.mono,
            )
            if (info.notes.isNotBlank()) Text(info.notes, color = ArkaColors.TextSecondary, style = ArkaTheme.type.body)
            when (state) {
                is Updater.State.Downloading ->
                    Text("Téléchargement… ${state.percent} %", style = ArkaTheme.type.body)
                else -> Row(horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.md)) {
                    PrimaryButton(
                        "Installer",
                        {
                            if (state is Updater.State.Ready) Updater.install(context)
                            else scope.launch { Updater.download(context, info) }
                        },
                        Modifier.focusRequester(focus),
                    )
                    SecondaryButton("Plus tard", { dismissed = true })
                }
            }
        }
        LaunchedEffect(state is Updater.State.Downloading) {
            try {
                focus.requestFocus()
            } catch (e: IllegalStateException) {
                // The buttons are not on screen while downloading.
            }
        }
    }
}

/** "Mises à jour" block of the settings: installed build, manual check, automatic check on or off. */
@Composable
fun UpdateSettings() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var auto by remember { mutableStateOf(Store.autoUpdateCheck) }
    val state = Updater.state
    val installedName = remember { Updater.installedVersionName(context) }
    val installedCode = remember { Updater.installedVersionCode(context) }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = ArkaTheme.margin).padding(bottom = ArkaSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(ArkaSpacing.sm),
    ) {
        Text("Mises à jour", style = ArkaTheme.type.heading, fontWeight = FontWeight(700))
        Text("Version installée : $installedName · build $installedCode", color = ArkaColors.TextSecondary, style = ArkaTheme.type.mono)
        val status = when (state) {
            is Updater.State.Checking -> "Vérification…"
            is Updater.State.UpToDate -> "ArkaTV est à jour."
            is Updater.State.Available -> "Build ${state.info.versionCode} disponible."
            is Updater.State.Downloading -> "Téléchargement… ${state.percent} %"
            is Updater.State.Ready -> "Build ${state.info.versionCode} téléchargé, en attente d'installation."
            is Updater.State.Failed -> state.message
            else -> null
        }
        if (status != null) Text(status, style = ArkaTheme.type.body)
        Row(horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm)) {
            when (state) {
                is Updater.State.Available -> ArkaChip("Télécharger et installer", true) {
                    scope.launch { Updater.download(context, state.info) }
                }
                is Updater.State.Ready -> ArkaChip("Installer", true) { Updater.install(context) }
                is Updater.State.Checking, is Updater.State.Downloading -> Unit
                else -> ArkaChip("Vérifier maintenant", false) {
                    scope.launch { Updater.check(context, silent = false) }
                }
            }
            ArkaChip(if (auto) "Vérification au lancement : oui" else "Vérification au lancement : non", auto) {
                auto = !auto
                Store.autoUpdateCheck = auto
            }
        }
        Text(
            "Les mises à jour viennent des versions publiées du projet sur GitHub. Rien n'est installé sans votre accord.",
            color = ArkaColors.TextSecondary,
            style = ArkaTheme.type.label,
            fontWeight = FontWeight(400),
        )
    }
}
