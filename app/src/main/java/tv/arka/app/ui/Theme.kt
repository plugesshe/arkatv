package tv.arka.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ArkaBg = Color(0xFF0B0B0F)
val ArkaSurface = Color(0xFF15151B)
val ArkaCard = Color(0xFF1F1F28)
val ArkaRed = Color(0xFFE5202E)
val ArkaRedDark = Color(0xFF5A1219)
val ArkaMuted = Color(0xFF9A9AA6)

private val colors = darkColorScheme(
    primary = ArkaRed,
    onPrimary = Color.White,
    secondary = ArkaRed,
    onSecondary = Color.White,
    secondaryContainer = ArkaRedDark,
    onSecondaryContainer = Color.White,
    background = ArkaBg,
    onBackground = Color.White,
    surface = ArkaBg,
    onSurface = Color.White,
    surfaceVariant = ArkaCard,
    onSurfaceVariant = ArkaMuted,
    surfaceContainer = ArkaSurface,
    surfaceContainerHigh = ArkaCard,
    surfaceContainerHighest = ArkaCard,
    outline = Color(0xFF3A3A46),
    error = Color(0xFFFF6B6B),
)

@Composable
fun ArkaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
