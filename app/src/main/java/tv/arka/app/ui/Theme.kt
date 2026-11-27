package tv.arka.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

/**
 * ArkaTV design tokens. Screens take every colour, spacing and radius from here;
 * see docs/DESIGN_SYSTEM.md for the usage rules.
 */
object ArkaColors {
    val Background = Color(0xFF08080B)
    val BackgroundAlt = Color(0xFF101014)
    val Surface = Color(0xFF18181F)
    val SurfaceHigh = Color(0xFF22222B)
    val Red = Color(0xFFE50935)
    val RedBright = Color(0xFFFF3457)
    val RedContainer = Color(0xFF4A0A18)
    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFFA1A1AA)
    val Violet = Color(0xFF7957FF)
    val Error = ArkaError
    val Outline = Color(0xFF34343F)
    val Scrim = Color(0xFF000000)
}

object ArkaSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object ArkaRadius {
    val sm = 6.dp
    val md = 8.dp
    val lg = 12.dp
    val pill = 50
}

object ArkaMotion {
    const val FOCUS_MS = 120
    const val FOCUS_SCALE = 1.06f
}

// Short aliases used throughout the screens.
val ArkaBg = ArkaColors.Background
val ArkaSurface = ArkaColors.BackgroundAlt
val ArkaCard = ArkaColors.Surface
val ArkaRed = ArkaColors.Red
val ArkaRedDark = ArkaColors.RedContainer
val ArkaMuted = ArkaColors.TextSecondary
val ArkaError = ArkaColors.Error

private val colors = darkColorScheme(
    primary = ArkaColors.Red,
    onPrimary = Color.White,
    secondary = ArkaColors.Red,
    onSecondary = Color.White,
    secondaryContainer = ArkaColors.RedContainer,
    onSecondaryContainer = ArkaColors.TextPrimary,
    tertiary = ArkaColors.Violet,
    background = ArkaColors.Background,
    onBackground = ArkaColors.TextPrimary,
    surface = ArkaColors.Background,
    onSurface = ArkaColors.TextPrimary,
    surfaceVariant = ArkaColors.Surface,
    onSurfaceVariant = ArkaColors.TextSecondary,
    surfaceContainer = ArkaColors.BackgroundAlt,
    surfaceContainerHigh = ArkaColors.Surface,
    surfaceContainerHighest = ArkaColors.SurfaceHigh,
    outline = ArkaColors.Outline,
    error = ArkaColors.Error,
)

private val typography = Typography(
    displaySmall = TextStyle(fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold),
)

@Composable
fun ArkaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}

/**
 * Makes the focused element obvious from across the room when navigating with a remote:
 * a light outline and an optional slight enlargement that does not move its neighbours.
 * Must come before `clickable` in the modifier chain. Has no effect on touch navigation.
 */
fun Modifier.focusHighlight(
    shape: Shape = RoundedCornerShape(ArkaRadius.md),
    scale: Float = ArkaMotion.FOCUS_SCALE,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val animated by animateFloatAsState(
        targetValue = if (focused) scale else 1f,
        animationSpec = tween(ArkaMotion.FOCUS_MS),
        label = "focus",
    )
    this
        .zIndex(if (focused) 1f else 0f)
        .onFocusChanged { focused = it.hasFocus }
        .graphicsLayer {
            scaleX = animated
            scaleY = animated
        }
        .then(if (focused) Modifier.border(2.dp, ArkaColors.TextPrimary, shape) else Modifier)
}
