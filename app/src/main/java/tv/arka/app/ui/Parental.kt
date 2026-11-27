package tv.arka.app.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import tv.arka.app.data.Repo
import tv.arka.app.data.Store

private const val PIN_LENGTH = 4

/**
 * Four-digit code entry, usable with a touch screen, a remote (arrows and OK) or number keys.
 * [onPin] receives each complete code and returns whether it was accepted; the caller closes the dialog.
 */
@Composable
fun PinDialog(title: String, hint: String = "", onPin: (String) -> Boolean, onDismiss: () -> Unit) {
    var entered by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val firstKey = remember { FocusRequester() }

    fun press(digit: Int) {
        if (entered.length >= PIN_LENGTH) return
        wrong = false
        val next = entered + digit
        if (next.length == PIN_LENGTH) {
            wrong = !onPin(next)
            entered = ""
        } else {
            entered = next
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(ArkaRadius.lg)).background(ArkaColors.SurfaceHigh)
                .onPreviewKeyEvent { event ->
                    val code = event.nativeKeyEvent.keyCode
                    val digit = when (code) {
                        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> code - KeyEvent.KEYCODE_0
                        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> code - KeyEvent.KEYCODE_NUMPAD_0
                        else -> -1
                    }
                    if (digit < 0) {
                        false
                    } else {
                        if (event.type == KeyEventType.KeyDown) press(digit)
                        true
                    }
                }
                .padding(ArkaSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ArkaSpacing.md),
        ) {
            Text(title, style = ArkaTheme.type.heading, fontWeight = FontWeight(700))
            if (hint.isNotBlank()) Text(hint, color = ArkaColors.TextSecondary, style = ArkaTheme.type.label, fontWeight = FontWeight(400))
            Row(horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.md)) {
                for (place in 0 until PIN_LENGTH) {
                    Box(
                        Modifier.size(14.dp).clip(CircleShape)
                            .background(if (place < entered.length) ArkaColors.TextPrimary else ArkaColors.Border)
                    )
                }
            }
            Text(
                if (wrong) "Code incorrect" else " ",
                color = ArkaColors.Error,
                style = ArkaTheme.type.label,
            )
            listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm)) {
                    row.forEach { digit ->
                        PinKey(digit.toString(), if (digit == 1) Modifier.focusRequester(firstKey) else Modifier) { press(digit) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm)) {
                PinKey("Effacer", small = true) {
                    entered = entered.dropLast(1)
                    wrong = false
                }
                PinKey("0") { press(0) }
                PinKey("Annuler", small = true, onClick = onDismiss)
            }
        }
        LaunchedEffect(Unit) {
            try {
                firstKey.requestFocus()
            } catch (e: IllegalStateException) {
                // The first arrow press will give the focus.
            }
        }
    }
}

@Composable
private fun PinKey(label: String, modifier: Modifier = Modifier, small: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.size(width = 84.dp, height = 52.dp)
            .focusHighlight(ArkaRadius.md, ArkaMotion.FOCUS_SCALE_BUTTON)
            .clip(RoundedCornerShape(ArkaRadius.md))
            .background(ArkaColors.Surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = if (small) ArkaTheme.type.label else ArkaTheme.type.heading, maxLines = 1)
    }
}

private const val PIN_NONE = 0
private const val PIN_NEW = 1
private const val PIN_FOR_CATEGORIES = 2
private const val PIN_FOR_CHANGE = 3
private const val PIN_FOR_REMOVAL = 4

/** "Contrôle parental" block of the settings: the code, and the way to the hidden categories. */
@Composable
fun ParentalSettings(onCategories: () -> Unit) {
    var hasPin by remember { mutableStateOf(Store.hasPin()) }
    var asking by remember { mutableIntStateOf(PIN_NONE) }
    var candidate by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = ArkaTheme.margin).padding(bottom = ArkaSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(ArkaSpacing.sm),
    ) {
        Text("Contrôle parental", style = ArkaTheme.type.heading, fontWeight = FontWeight(700))
        Text(
            if (hasPin) {
                "Un code protège la sortie des profils enfant, la gestion des profils et ces réglages. " +
                    "${Repo.lockedCategories.size} catégorie(s) masquée(s) aux profils enfant pour cette source."
            } else {
                "Définissez un code à quatre chiffres pour créer des profils enfant et leur masquer des catégories."
            },
            color = ArkaColors.TextSecondary,
            style = ArkaTheme.type.label,
            fontWeight = FontWeight(400),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm)) {
            if (!hasPin) {
                ArkaChip("Définir un code", false) {
                    candidate = null
                    asking = PIN_NEW
                }
            } else {
                ArkaChip("Catégories masquées", false) { asking = PIN_FOR_CATEGORIES }
                ArkaChip("Changer le code", false) { asking = PIN_FOR_CHANGE }
                ArkaChip("Supprimer le code", false) { asking = PIN_FOR_REMOVAL }
            }
        }
    }

    when (asking) {
        PIN_NEW -> PinDialog(
            title = if (candidate == null) "Nouveau code" else "Confirmez le code",
            hint = "Quatre chiffres. Notez-le : il ne peut pas être retrouvé.",
            onPin = { pin ->
                val first = candidate
                if (first == null) {
                    candidate = pin
                    true
                } else if (first == pin) {
                    Store.setPin(pin)
                    hasPin = true
                    candidate = null
                    asking = PIN_NONE
                    true
                } else {
                    // The two entries differ: start again.
                    candidate = null
                    false
                }
            },
            onDismiss = { asking = PIN_NONE },
        )
        PIN_FOR_CATEGORIES, PIN_FOR_CHANGE, PIN_FOR_REMOVAL -> PinDialog(
            title = "Code parental",
            onPin = { pin ->
                if (!Store.checkPin(pin)) {
                    false
                } else {
                    when (asking) {
                        PIN_FOR_CATEGORIES -> {
                            asking = PIN_NONE
                            onCategories()
                        }
                        PIN_FOR_CHANGE -> {
                            candidate = null
                            asking = PIN_NEW
                        }
                        else -> {
                            // Without a code the child profiles stay, but nothing protects them any more.
                            Store.setPin(null)
                            hasPin = false
                            asking = PIN_NONE
                        }
                    }
                    true
                }
            },
            onDismiss = { asking = PIN_NONE },
        )
        else -> {}
    }
}

private val lockKinds = listOf("live" to "Direct", "movie" to "Films", "series" to "Séries")

/** The categories of the active source, by kind; the ticked ones are hidden from every child profile. */
@Composable
fun ParentalScreen(onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val kind = lockKinds[tab].first
    val categories = when (tab) {
        0 -> Repo.liveCategories
        1 -> Repo.vodCategories
        else -> Repo.seriesCategories
    }
    val locked = Repo.lockedCategories

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenHeader("Catégories masquées", onBack, trailing = locked.size.toString())
        Text(
            "Les catégories cochées disparaissent des profils enfant : listes, accueil, recherche et découverte.",
            color = ArkaColors.TextSecondary,
            style = ArkaTheme.type.label,
            fontWeight = FontWeight(400),
            modifier = Modifier.padding(horizontal = ArkaTheme.margin, vertical = ArkaSpacing.sm),
        )
        Row(
            Modifier.padding(horizontal = ArkaTheme.margin, vertical = ArkaSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(ArkaSpacing.sm),
        ) {
            lockKinds.forEachIndexed { place, entry ->
                val count = locked.count { it.startsWith(entry.first + ":") }
                ArkaChip(entry.second + if (count > 0) " · $count" else "", tab == place) { tab = place }
            }
        }
        if (categories.isEmpty()) {
            Text(
                "Aucune catégorie de ce type dans la source active.",
                color = ArkaColors.TextSecondary,
                modifier = Modifier.padding(ArkaTheme.margin),
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = ArkaTheme.margin, vertical = ArkaSpacing.sm),
            ) {
                items(categories) { category ->
                    val key = kind + ":" + category.id
                    SheetOption(category.name, key in locked, onClick = {
                        Repo.setCategoryLocked(kind, category.id, key !in locked)
                    })
                }
            }
        }
    }
}
