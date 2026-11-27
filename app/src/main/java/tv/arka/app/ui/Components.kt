@file:OptIn(ExperimentalMaterial3Api::class)

package tv.arka.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import tv.arka.app.data.Profile
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tv.arka.app.R

/** An icon of the ArkaTV set (24 dp grid, 2 dp stroke). */
@Composable
fun ArkaIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = ArkaColors.TextPrimary,
    size: Dp = 24.dp,
) {
    Icon(painterResource(icon), contentDescription = contentDescription, tint = tint, modifier = modifier.size(size))
}

/** Height of buttons and other controls: 48 dp for touch, 36 dp (72 px) on TV. */
private val controlHeight: Dp
    @Composable get() = if (LocalIsTv.current) 36.dp else ArkaSize.touchMin

/** The one main action of a screen. There is never more than one red button per screen. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val background = when {
        !enabled -> ArkaColors.SurfaceHigh
        pressed -> ArkaColors.RedPressed
        else -> ArkaColors.Red
    }
    val content = if (enabled) Color.White else ArkaColors.TextDisabled
    Row(
        modifier
            .height(controlHeight)
            .focusHighlight(ArkaRadius.md, ArkaMotion.FOCUS_SCALE_BUTTON)
            .clip(RoundedCornerShape(ArkaRadius.md))
            .background(background)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = ArkaSpacing.xl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            ArkaIcon(icon, null, tint = content, size = if (LocalIsTv.current) 16.dp else 20.dp)
            Spacer(Modifier.width(ArkaSpacing.sm))
        }
        Text(text, color = content, style = ArkaTheme.type.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Secondary actions: surface background, never red. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    Row(
        modifier
            .height(controlHeight)
            .focusHighlight(ArkaRadius.md, ArkaMotion.FOCUS_SCALE_BUTTON)
            .clip(RoundedCornerShape(ArkaRadius.md))
            .background(ArkaColors.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = ArkaSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            ArkaIcon(icon, null, size = if (LocalIsTv.current) 16.dp else 20.dp)
            Spacer(Modifier.width(ArkaSpacing.sm))
        }
        Text(text, color = ArkaColors.TextPrimary, style = ArkaTheme.type.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A square icon button, such as "Ma liste" or "Plus d'informations" next to the main action. */
@Composable
fun SquareButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    Box(
        modifier
            .size(controlHeight)
            .focusHighlight(ArkaRadius.md, ArkaMotion.FOCUS_SCALE_BUTTON)
            .clip(RoundedCornerShape(ArkaRadius.md))
            .background(ArkaColors.SurfaceHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ArkaIcon(
            icon,
            contentDescription,
            tint = if (active) ArkaColors.RedBright else ArkaColors.TextPrimary,
            size = if (LocalIsTv.current) 16.dp else 22.dp,
        )
    }
}

/**
 * Filter chip. Selection is a light background plus a check mark, never colour alone.
 * Chips that open something (sort order, group manager) are simply never selected.
 */
@Composable
fun ArkaChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(ArkaRadius.pill)
    Row(
        Modifier
            .defaultMinSize(minHeight = if (LocalIsTv.current) 28.dp else 36.dp)
            .focusHighlight(18.dp, ArkaMotion.FOCUS_SCALE_BUTTON)
            .clip(shape)
            .background(if (selected) ArkaColors.TextPrimary else ArkaColors.Surface)
            .then(if (selected) Modifier else Modifier.border(1.dp, ArkaColors.Border, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            ArkaIcon(R.drawable.ic_arka_valide, null, tint = ArkaColors.Background, size = 14.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            color = if (selected) ArkaColors.Background else ArkaColors.TextPrimary,
            style = ArkaTheme.type.label,
            maxLines = 1,
        )
    }
}

/** A small rectangular badge: NOUVEAU, HD, 4K. */
@Composable
fun ArkaBadge(text: String, modifier: Modifier = Modifier, filled: Boolean = true) {
    val shape = RoundedCornerShape(ArkaRadius.xs)
    Text(
        text,
        color = if (filled) ArkaColors.Background else ArkaColors.TextPrimary,
        style = ArkaTheme.type.mono,
        maxLines = 1,
        modifier = modifier
            .clip(shape)
            .then(if (filled) Modifier.background(ArkaColors.TextPrimary) else Modifier.border(1.dp, ArkaColors.TextPrimary, shape))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** Loading placeholder: the surface colour with a soft light sweeping across every 1.6 s. */
fun Modifier.skeleton(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    drawBehind {
        val band = size.width
        val start = -band + phase * (size.width + band)
        drawRect(
            Brush.linearGradient(
                colors = listOf(ArkaColors.Surface, ArkaColors.SurfaceHigh, ArkaColors.Surface),
                start = Offset(start, 0f),
                end = Offset(start + band, 0f),
            )
        )
    }
}

/** A remote image that fades in over 150 ms once loaded; the caller draws the fallback underneath. */
@Composable
fun ArkaImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    contentDescription: String? = null,
    onError: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val tv = LocalIsTv.current
    // Built once per image; no cross-fade on TV, where it costs frames while rows scroll.
    val request = remember(url, tv) {
        ImageRequest.Builder(context).data(url).crossfade(if (tv) 0 else 150).build()
    }
    AsyncImage(
        model = request,
        onError = { onError?.invoke() },
        contentDescription = contentDescription,
        contentScale = contentScale,
        alignment = alignment,
        modifier = modifier,
    )
}

/** A panel of choices: a bottom sheet on handsets, a dialog on TV where sheets are awkward with a remote. */
@Composable
fun ArkaSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    if (LocalIsTv.current) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = ArkaColors.SurfaceHigh,
            title = { Text(title, style = ArkaTheme.type.heading) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), content = content) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
        )
    } else {
        ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ArkaColors.SurfaceHigh) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding()
                    .padding(start = ArkaSpacing.lg, end = ArkaSpacing.lg, bottom = ArkaSpacing.xl)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(title, style = ArkaTheme.type.heading, fontWeight = FontWeight(700), modifier = Modifier.padding(bottom = ArkaSpacing.sm))
                content()
            }
        }
    }
}

/** One line of an [ArkaSheet]: the selected one is bold and carries a red check mark. */
@Composable
fun SheetOption(label: String, selected: Boolean, onClick: () -> Unit, trailing: String? = null) {
    Row(
        Modifier.fillMaxWidth()
            .defaultMinSize(minHeight = controlHeight)
            .focusHighlight(ArkaRadius.sm, 1f)
            .clip(RoundedCornerShape(ArkaRadius.sm))
            .clickable(onClick = onClick)
            .padding(horizontal = ArkaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (selected) ArkaColors.TextPrimary else ArkaColors.TextTertiary,
            style = ArkaTheme.type.body,
            fontWeight = if (selected) FontWeight(700) else FontWeight(400),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(trailing, color = ArkaColors.TextSecondary, style = ArkaTheme.type.mono, modifier = Modifier.padding(start = ArkaSpacing.sm))
        }
        if (selected) {
            ArkaIcon(R.drawable.ic_arka_valide, "Sélectionné", tint = ArkaColors.RedBright, size = 20.dp, modifier = Modifier.padding(start = ArkaSpacing.sm))
        }
    }
}

/** A segmented pair such as Décroissant / Croissant: the active side is light, as on the mock-up. */
@Composable
fun SegmentButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(controlHeight)
            .focusHighlight(ArkaRadius.md, ArkaMotion.FOCUS_SCALE_BUTTON)
            .clip(RoundedCornerShape(ArkaRadius.md))
            .background(if (selected) ArkaColors.TextPrimary else ArkaColors.Surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) ArkaColors.Background else ArkaColors.TextPrimary, style = ArkaTheme.type.label)
    }
}

/** A profile shown as its initial on a tinted tile. */
@Composable
fun ProfileAvatar(profile: Profile, size: Dp, modifier: Modifier = Modifier, radius: Dp = ArkaRadius.md) {
    val (background, foreground) = ArkaAvatarColors[profile.color.mod(ArkaAvatarColors.size)]
    Box(
        modifier.size(size).clip(RoundedCornerShape(radius)).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            profile.name.trim().take(1).uppercase().ifBlank { "?" },
            color = foreground,
            fontWeight = FontWeight(700),
            fontSize = (size.value * 0.42f).sp,
        )
    }
}

/** A violet-outlined action: the accent reserved for the Discovery mode. */
@Composable
fun AccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(ArkaRadius.md)
    Box(
        modifier
            .height(controlHeight)
            .focusHighlight(ArkaRadius.md, ArkaMotion.FOCUS_SCALE_BUTTON)
            .clip(shape)
            .background(ArkaColors.Surface)
            .border(2.dp, ArkaColors.Violet, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = ArkaSpacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = ArkaColors.TextPrimary, style = ArkaTheme.type.label, maxLines = 1)
    }
}
