package dev.nutty.proxy.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.nutty.proxy.ui.model.SheetSpec
import dev.nutty.proxy.ui.theme.NuttyColor
import dev.nutty.proxy.ui.theme.NuttyShape
import dev.nutty.proxy.ui.theme.NuttyType

/**
 * The detail sheet — the load-bearing idea of this design.
 *
 * Every screen stays bare because anything a person only occasionally needs
 * lives one tap away behind an "(i)". That is why Home fits five states without
 * a single paragraph of explanation on the surface.
 *
 * Hand-built rather than `ModalBottomSheet`: no experimental opt-in, and the
 * scrim/handle/geometry come straight from the design instead of from M3
 * defaults that would have to be overridden one by one.
 */
@Composable
fun SheetHost(
    spec: SheetSpec?,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
) {
    // Hold the last spec so the sheet can animate *out* after the state clears.
    var lastSpec by remember { mutableStateOf<SheetSpec?>(null) }
    if (spec != null) lastSpec = spec
    val visible = spec != null

    BackHandler(enabled = visible, onBack = onDismiss)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(140)),
        exit = fadeOut(tween(140)),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NuttyColor.Scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(tween(220)) { it },
                exit = slideOutVertically(tween(180)) { it },
            ) {
                lastSpec?.let { SheetContent(it) }
            }
        }
    }
}

@Composable
private fun SheetContent(spec: SheetSpec) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(NuttyShape.Sheet)
            .background(NuttyColor.SurfaceRaised, NuttyShape.Sheet)
            .border(1.dp, NuttyColor.OutlineStrong, NuttyShape.Sheet)
            // Swallow taps so touching the sheet does not dismiss it.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 24.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 16.dp)
                .size(width = 38.dp, height = 4.dp)
                .background(NuttyColor.Handle, NuttyShape.Bar)
        )

        Text(
            text = spec.title,
            style = NuttyType.SheetTitle,
            color = NuttyColor.TextPrimary,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        spec.rows.forEach { row ->
            CardDivider(NuttyColor.DividerRaised)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(row.key, style = NuttyType.Label, color = NuttyColor.TextMuted)
                Text(
                    text = row.value,
                    style = NuttyType.Value,
                    color = NuttyColor.TextPrimary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (spec.code != null) {
            Text(
                text = spec.code,
                style = NuttyType.Code,
                color = NuttyColor.TextTertiary,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .clip(NuttyShape.Code)
                    .background(NuttyColor.SurfaceInset, NuttyShape.Code)
                    .border(1.dp, NuttyColor.DividerRaised, NuttyShape.Code)
                    .padding(horizontal = 13.dp, vertical = 12.dp),
            )
        }

        // The note is the sheet's reason for existing: the sentence that stops a
        // support ticket. Quietest text on screen, and never omitted.
        Text(
            text = spec.note,
            style = NuttyType.Caption,
            color = NuttyColor.TextFaintest,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}
