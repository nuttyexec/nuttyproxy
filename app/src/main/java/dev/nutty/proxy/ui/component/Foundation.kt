package dev.nutty.proxy.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.nutty.proxy.ui.theme.Dim
import dev.nutty.proxy.ui.theme.NuttyColor
import dev.nutty.proxy.ui.theme.NuttyShape
import dev.nutty.proxy.ui.theme.NuttyType

/**
 * The base card: dark surface, hairline border, 16dp corners.
 *
 * Nearly every block on every screen is one of these. The border — not a
 * shadow — is what separates a card from the ground; elevation shadows would
 * muddy the near-black palette, so the design has none.
 */
@Composable
fun NuttyCard(
    modifier: Modifier = Modifier,
    background: Color = NuttyColor.Surface,
    border: Color = NuttyColor.Outline,
    shape: Shape = NuttyShape.Card,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background, shape)
            .border(1.dp, border, shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        content = content,
    )
}

/** The mono all-caps eyebrow that titles a card: "TUNNEL", "REQUESTS · LAST 5 MIN". */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = NuttyColor.TextLabel,
) {
    Text(text = text, style = NuttyType.SectionLabel, color = color, modifier = modifier)
}

/** Hairline between rows inside a card. */
@Composable
fun CardDivider(color: Color = NuttyColor.Divider) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}

/** The "›" affordance. Text rather than an icon — it matches the type ramp. */
@Composable
fun Chevron(color: Color = NuttyColor.TextFaint) {
    Text("›", style = NuttyType.Row, color = color)
}

/**
 * A label/value row. Label is Sans, value is Mono — the split that lets someone
 * scan a column of values without reading a single word.
 */
@Composable
fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueSuffix: String? = null,
    labelColor: Color = NuttyColor.TextMuted,
    valueColor: Color = NuttyColor.TextPrimary,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = Dim.RowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (leading == null) {
            Text(label, style = NuttyType.Label, color = labelColor)
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                leading()
                Text(label, style = NuttyType.Label, color = labelColor)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(value, style = NuttyType.Value, color = valueColor)
            if (valueSuffix != null) {
                Text(valueSuffix, style = NuttyType.Meta, color = NuttyColor.TextFaint)
            }
            if (chevron) Chevron()
        }
    }
}

/**
 * Status dot. [glow] paints the halo the design gives live states; [blinking]
 * carries "in progress" without a spinner — the pulse *is* the progress.
 */
@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 9.dp,
    glow: Boolean = false,
    blinking: Boolean = false,
) {
    val alpha = if (blinking) {
        val transition = rememberInfiniteTransition(label = "dot")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
            label = "dotAlpha",
        ).value
    } else 1f

    Box(
        modifier = modifier
            .size(size)
            .alpha(alpha)
            .drawBehind {
                if (glow) {
                    val r = this.size.minDimension * 1.9f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color.copy(alpha = 0.55f), Color.Transparent),
                            radius = r,
                        ),
                        radius = r,
                    )
                }
            }
            .background(color, CircleShape)
    )
}

/** Small status badge on a server row: ALLOWED / PAUSED / REVOKED. */
@Composable
fun Badge(
    text: String,
    textColor: Color,
    background: Color,
    border: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = NuttyType.Badge,
        color = textColor,
        modifier = modifier
            .clip(NuttyShape.Badge)
            .background(background, NuttyShape.Badge)
            .border(1.dp, border, NuttyShape.Badge)
            .padding(horizontal = 7.dp, vertical = 4.dp),
    )
}

/**
 * The circular affordance: "(i)" opens a detail sheet, "+" adds a server.
 *
 * The two share a shell but not a type style — "i" is a small semibold label,
 * "+" is a larger regular glyph, so the plus reads as a mark rather than a word.
 */
@Composable
fun InfoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: String = "i",
    glyphStyle: androidx.compose.ui.text.TextStyle = NuttyType.ChipStrong,
) {
    Box(
        modifier = modifier
            .size(Dim.InfoButton)
            .clip(CircleShape)
            .border(1.dp, NuttyColor.OutlineStrong, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = glyphStyle, color = NuttyColor.TextMuted)
    }
}

/** White slab — the single most important action on a screen. Max one per view. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = Dim.ButtonHeight,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(NuttyShape.Button)
            .background(NuttyColor.TextPrimary, NuttyShape.Button)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = NuttyType.Button, color = NuttyColor.Bg)
    }
}

/**
 * Outlined action. The default for anything reversible.
 *
 * Anything shorter than the full 50dp slab is a secondary action, and the design
 * steps it down as a set: 14dp corners instead of 15dp, 14sp label instead of
 * 15sp. Driving both off the height keeps that pairing from drifting apart.
 */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = Dim.ButtonHeight,
    textColor: Color = NuttyColor.TextPrimary,
    borderColor: Color = NuttyColor.OutlineFocus,
    leading: @Composable (RowScope.() -> Unit)? = null,
) {
    val secondary = height < Dim.ButtonHeight
    val shape = if (secondary) NuttyShape.Notice else NuttyShape.Button
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke(this)
        Text(
            text = text,
            style = if (secondary) NuttyType.ButtonSmall else NuttyType.Button,
            color = textColor,
        )
    }
}

/** Destructive action — red text on a red-tinted border, never a filled red slab. */
@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = Dim.ButtonHeightSmall,
) = OutlineButton(
    text = text,
    onClick = onClick,
    modifier = modifier,
    height = height,
    textColor = NuttyColor.Red,
    borderColor = NuttyColor.RedOutlineButton,
)

/** Filter / suggestion chip. Selected inverts to a light slab. */
@Composable
fun NuttyChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
) {
    val shape = NuttyShape.Chip
    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (selected) Modifier.background(NuttyColor.TextPrimary, shape)
                else Modifier.border(1.dp, NuttyColor.OutlineStrong, shape)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            style = if (mono) NuttyType.ValueSmall else NuttyType.Chip,
            color = if (selected) NuttyColor.Bg else NuttyColor.TextMuted,
        )
    }
}

/** The amber "Fix" / "Allow" pill that lives inside a warning notice. */
@Composable
fun AccentPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = NuttyColor.Amber,
    textColor: Color = NuttyColor.Bg,
) {
    Box(
        modifier = modifier
            .clip(NuttyShape.ChipStrong)
            .background(background, NuttyShape.ChipStrong)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(text, style = NuttyType.ChipStrong, color = textColor)
    }
}

/**
 * Outlined counterpart of [AccentPillButton] — "Open", "Share report", "Paste".
 *
 * [strong] promotes it to a real action (brighter border, semibold label) for
 * the one place the design asks for it: the "Open" that leaves for OEM settings.
 */
@Composable
fun GhostPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = NuttyColor.TextMuted,
    strong: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(NuttyShape.ChipStrong)
            .border(
                width = 1.dp,
                color = if (strong) NuttyColor.OutlineFocus else NuttyColor.OutlineStrong,
                shape = NuttyShape.ChipStrong,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            style = if (strong) NuttyType.ChipStrong else NuttyType.Chip,
            color = textColor,
        )
    }
}

/**
 * Switch. Hand-drawn rather than [androidx.compose.material3.Switch] because M3's
 * switch is 52×32 with an outline and a shadowed thumb — three details that fight
 * this palette. Geometry here is the design's 42×24 track / 18dp thumb.
 */
@Composable
fun NuttyToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(width = 42.dp, height = 24.dp)
            .clip(NuttyShape.Toggle)
            .background(
                if (checked) NuttyColor.Green else NuttyColor.OutlineMuted,
                NuttyShape.Toggle,
            )
            .clickable { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .background(if (checked) NuttyColor.Bg else NuttyColor.TextFaintest, CircleShape)
        )
    }
}

/** Onboarding progress — four ticks, filled left to right. */
@Composable
fun StepDots(step: Int, total: Int = 4, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { index ->
            Box(
                Modifier
                    .size(width = 26.dp, height = 3.dp)
                    .background(
                        if (index < step) NuttyColor.TextPrimary else NuttyColor.OutlineMuted,
                        NuttyShape.Bar,
                    )
            )
        }
    }
}

/** The gesture-navigation pill the design reserves space for. */
@Composable
fun HomeIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dim.GestureInset),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 108.dp, height = 4.dp)
                .background(NuttyColor.Handle, NuttyShape.Bar)
        )
    }
}
