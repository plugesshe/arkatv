package tv.arka.app.ui

import androidx.annotation.DrawableRes
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
