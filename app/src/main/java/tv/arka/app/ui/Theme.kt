@file:OptIn(ExperimentalTextApi::class)

package tv.arka.app.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import tv.arka.app.R

/**
 * ArkaTV design tokens, taken from the design handoff (handoff/tokens/tokens.json).
 * Screens take every colour, size and duration from here; see docs/DESIGN_SYSTEM.md.
 */
object ArkaColors {
    val Background = Color(0xFF08080B)
    val BackgroundAlt = Color(0xFF101014)
    val Surface = Color(0xFF18181F)
    val SurfaceHigh = Color(0xFF22222B)
    val SurfaceHigher = Color(0xFF2E2E38)
    val Border = Color(0xFF3A3A45)

    /** Main action, selection, progress, live. Never decoration. */
    val Red = Color(0xFFE50935)

    /** Red text on dark backgrounds: always this one, for contrast. */
    val RedBright = Color(0xFFFF3457)
    val RedPressed = Color(0xFFB8062A)
    val RedContainer = Color(0xFF4A0A18)

    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFFA1A1AA)
    val TextTertiary = Color(0xFFD4D4D8)
    val TextDisabled = Color(0xFF6B6B75)
    val IconDisabled = Color(0xFF4A4A55)

    val Violet = Color(0xFF7957FF)
    val VioletText = Color(0xFFC9B8FF)
    val Ok = Color(0xFF3DD68C)
    val Warning = Color(0xFFF2B53A)
    val Error = Color(0xFFFF6B6B)
    val FocusOutline = Color(0xFFF5F5F7)
    val Outline = Border
    val Scrim = Color(0xFF000000)
}

object ArkaSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val x3 = 48.dp
    val x4 = 80.dp
}

/** xs: badges · sm: cards · md: buttons, fields · lg: dialogs. */
object ArkaRadius {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 20.dp
    const val pill = 50
}

object ArkaSize {
    val touchMin = 48.dp
    val bottomBar = 64.dp

    // TV mock-ups are in px at 1920×1080; Android TV is 960×540 dp, so every value is halved.
    val tvOverscan = 40.dp
    val tvRowGap = 24.dp
    val tvSidebarCollapsed = 48.dp
    val tvSidebarExpanded = 160.dp
    val tvControlMin = 32.dp
}

object ArkaMotion {
    val easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    const val FOCUS_MS = 200
    const val PRESSED_MS = 120
    const val FOCUS_SCALE_CARD = 1.06f
    const val FOCUS_SCALE_BUTTON = 1.04f
    val focusOutline = 3.dp
    val focusOutlineOffset = 3.dp
}

private fun archivo(weight: Int, width: Float = 100f) = Font(
    resId = R.font.archivo,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
)

/** Interface text: Archivo at normal width. */
val ArkaFont = FontFamily(archivo(400), archivo(500), archivo(600), archivo(700), archivo(800))

/** Poster-style titles: Archivo 800 condensed to 72 %. */
val ArkaDisplayFont = FontFamily(archivo(800, 72f))

/** Wordmark: Archivo widened to 112 %. */
val ArkaLogoFont = FontFamily(archivo(800, 112f), archivo(500, 112f))

/** Times and diagnostics. */
val ArkaMonoFont = FontFamily(
    Font(
        resId = R.font.jetbrains_mono,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    )
)

/** The two type scales of the design system: handset (dp) and television (halved px). */
class ArkaTypeScale(
    val display: TextStyle,
    val title: TextStyle,
    val heading: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val mono: TextStyle,
)

private val mobileType = ArkaTypeScale(
    display = TextStyle(fontFamily = ArkaDisplayFont, fontSize = 44.sp, lineHeight = 41.8.sp, fontWeight = FontWeight(800)),
    title = TextStyle(fontFamily = ArkaFont, fontSize = 24.sp, lineHeight = 27.6.sp, fontWeight = FontWeight(700)),
    heading = TextStyle(fontFamily = ArkaFont, fontSize = 18.sp, lineHeight = 22.5.sp, fontWeight = FontWeight(600)),
    body = TextStyle(fontFamily = ArkaFont, fontSize = 15.sp, lineHeight = 22.5.sp, fontWeight = FontWeight(400)),
    label = TextStyle(fontFamily = ArkaFont, fontSize = 13.sp, lineHeight = 15.6.sp, fontWeight = FontWeight(600)),
    mono = TextStyle(fontFamily = ArkaMonoFont, fontSize = 12.sp, lineHeight = 16.8.sp, fontWeight = FontWeight(500)),
)

private val tvType = ArkaTypeScale(
    display = TextStyle(fontFamily = ArkaDisplayFont, fontSize = 48.sp, lineHeight = 44.1.sp, fontWeight = FontWeight(800)),
    title = TextStyle(fontFamily = ArkaFont, fontSize = 20.sp, lineHeight = 22.sp, fontWeight = FontWeight(700)),
    heading = TextStyle(fontFamily = ArkaFont, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight(600)),
    body = TextStyle(fontFamily = ArkaFont, fontSize = 13.sp, lineHeight = 18.9.sp, fontWeight = FontWeight(400)),
    label = TextStyle(fontFamily = ArkaFont, fontSize = 11.sp, lineHeight = 13.2.sp, fontWeight = FontWeight(600)),
    mono = TextStyle(fontFamily = ArkaMonoFont, fontSize = 11.sp, lineHeight = 15.4.sp, fontWeight = FontWeight(500)),
)

/** True on Android TV: screens then use the remote-control layouts and the TV type scale. */
val LocalIsTv = compositionLocalOf { false }

object ArkaTheme {
    val type: ArkaTypeScale
        @Composable get() = if (LocalIsTv.current) tvType else mobileType

    /** Left and right screen margin: 16 dp on handsets, the 5 % overscan area on TV. */
    val margin: Dp
        @Composable get() = if (LocalIsTv.current) ArkaSize.tvOverscan else ArkaSpacing.lg

    val rowGap: Dp
        @Composable get() = if (LocalIsTv.current) ArkaSize.tvRowGap else ArkaSpacing.xl
}

/** Profile avatar tints (background, initial), as drawn on the "Qui regarde ?" mock-up. */
val ArkaAvatarColors = listOf(
    Color(0xFF2A2340) to Color(0xFFC9B8FF),
    Color(0xFF3A1F24) to Color(0xFFFF9AA8),
    Color(0xFF1F3A33) to Color(0xFF8FE3C5),
    Color(0xFF3A311F) to Color(0xFFF2D08A),
    Color(0xFF1F2E3A) to Color(0xFF9CCBF2),
)

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
    surfaceContainerHigh = ArkaColors.SurfaceHigh,
    surfaceContainerHighest = ArkaColors.SurfaceHigher,
    outline = ArkaColors.Border,
    error = ArkaColors.Error,
)

private fun typography(scale: ArkaTypeScale): Typography {
    val base = Typography()
    fun TextStyle.arka() = copy(fontFamily = ArkaFont)
    return Typography(
        displayLarge = scale.display,
        displayMedium = scale.display,
        displaySmall = scale.display,
        headlineLarge = scale.title,
        headlineMedium = scale.title,
        headlineSmall = scale.title,
        titleLarge = scale.heading,
        titleMedium = scale.heading,
        titleSmall = scale.label,
        bodyLarge = scale.body,
        bodyMedium = scale.body,
        bodySmall = base.bodySmall.arka(),
        labelLarge = scale.label,
        labelMedium = base.labelMedium.arka(),
        labelSmall = base.labelSmall.arka(),
    )
}

@Composable
fun ArkaTheme(isTv: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIsTv provides isTv) {
        MaterialTheme(
            colorScheme = colors,
            typography = typography(if (isTv) tvType else mobileType),
            content = content,
        )
    }
}

/**
 * Remote-control focus: a sharp light outline drawn 3 dp outside the element, plus a slight
 * enlargement done with a transform so that neighbours never move. Must come before `clickable`
 * in the modifier chain. Has no effect on touch navigation.
 */
fun Modifier.focusHighlight(
    radius: Dp = ArkaRadius.sm,
    scale: Float = ArkaMotion.FOCUS_SCALE_CARD,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val animated by animateFloatAsState(
        targetValue = if (focused) scale else 1f,
        animationSpec = tween(ArkaMotion.FOCUS_MS, easing = ArkaMotion.easing),
        label = "focus",
    )
    this
        .zIndex(if (focused) 1f else 0f)
        .onFocusChanged { focused = it.hasFocus }
        .graphicsLayer {
            scaleX = animated
            scaleY = animated
        }
        .drawWithContent {
            drawContent()
            if (focused) {
                val stroke = ArkaMotion.focusOutline.toPx()
                val gap = ArkaMotion.focusOutlineOffset.toPx() + stroke / 2
                drawRoundRect(
                    color = ArkaColors.FocusOutline,
                    topLeft = Offset(-gap, -gap),
                    size = Size(size.width + 2 * gap, size.height + 2 * gap),
                    cornerRadius = CornerRadius(radius.toPx() + gap),
                    style = Stroke(stroke),
                )
            }
        }
}
