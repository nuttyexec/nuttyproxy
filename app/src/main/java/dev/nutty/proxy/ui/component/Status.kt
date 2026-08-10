package dev.nutty.proxy.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.nutty.proxy.ui.model.DemoData
import dev.nutty.proxy.ui.model.HomeState
import dev.nutty.proxy.ui.theme.Dim
import dev.nutty.proxy.ui.theme.NuttyColor
import dev.nutty.proxy.ui.theme.NuttyShape
import dev.nutty.proxy.ui.theme.NuttyType

/**
 * The status pill — the one element that must answer "is it working?" from
 * across the room.
 *
 * Colour, dot behaviour and the mono readout all move together with the state,
 * and nothing else on Home is allowed to compete with it.
 */
@Composable
fun StatusPill(
    state: HomeState,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val status = state.status
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Dim.StatusPillHeight)
            .clip(NuttyShape.Pill)
            .background(status.container, NuttyShape.Pill)
            .border(1.dp, status.outline, NuttyShape.Pill)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        StatusDot(
            color = status.dot,
            glow = state == HomeState.Connected || state == HomeState.Disconnected,
            blinking = state == HomeState.Reconnecting,
        )
        Text(state.title, style = NuttyType.StatusTitle, color = status.title)
        Text(
            text = state.meta,
            style = NuttyType.Value,
            color = status.meta,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The single-line explanation under the pill. One issue, one sentence, one action
 * — the design never stacks two of these.
 */
@Composable
fun InlineNotice(
    notice: DemoData.Notice,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onAction: () -> Unit,
) {
    val background = when (notice.tone) {
        DemoData.Notice.Tone.Neutral -> NuttyColor.Surface
        DemoData.Notice.Tone.Amber -> NuttyColor.AmberContainerSoft
        DemoData.Notice.Tone.Red -> NuttyColor.RedContainerSoft
    }
    val border = when (notice.tone) {
        DemoData.Notice.Tone.Neutral -> NuttyColor.Outline
        DemoData.Notice.Tone.Amber -> NuttyColor.AmberOutlineSoft
        DemoData.Notice.Tone.Red -> NuttyColor.RedOutlineSoft
    }
    val textColor = when (notice.tone) {
        DemoData.Notice.Tone.Neutral -> NuttyColor.TextTertiary
        DemoData.Notice.Tone.Amber -> NuttyColor.AmberText
        DemoData.Notice.Tone.Red -> NuttyColor.RedTextSoft
    }
    // Only the amber notice carries a real button; the others offer a quiet
    // "why?" because there is nothing to fix, only something to understand.
    val hasButton = notice.tone == DemoData.Notice.Tone.Amber

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(NuttyShape.Notice)
            .background(background, NuttyShape.Notice)
            .border(1.dp, border, NuttyShape.Notice)
            .clickable(onClick = onClick)
            .padding(
                start = 14.dp,
                end = if (hasButton) 10.dp else 14.dp,
                top = if (hasButton) 10.dp else 11.dp,
                bottom = if (hasButton) 10.dp else 11.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = notice.text,
            style = NuttyType.Item,
            color = textColor,
            modifier = Modifier.weight(1f),
        )
        if (hasButton) {
            AccentPillButton(notice.action, onAction)
        } else {
            Text(notice.action, style = NuttyType.ValueSmall, color = NuttyColor.TextDim)
        }
    }
}

/**
 * A metric tile: eyebrow, one big Mono number, one line of context.
 *
 * Three lines is the hard ceiling. Anything more goes behind the tile's sheet —
 * that constraint is what keeps Home readable at a glance.
 */
@Composable
fun MetricTile(
    label: String,
    value: String?,
    caption: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    onClick: () -> Unit,
) {
    NuttyCard(modifier = modifier.height(Dim.TileHeight), onClick = onClick) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dim.CardPadding, vertical = 13.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            SectionLabel(label)
            if (value == null) {
                // No live tunnel: an em-dash, not a zero. Zero would be a measurement.
                Text("—", style = NuttyType.Metric, color = NuttyColor.TextDisabled)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(value, style = NuttyType.Metric, color = NuttyColor.TextPrimary)
                    if (unit != null) {
                        Text(
                            text = " $unit",
                            style = NuttyType.MetricUnit,
                            color = NuttyColor.TextDim,
                            modifier = Modifier.padding(bottom = 1.dp),
                        )
                    }
                }
            }
            Text(caption, style = NuttyType.Hint, color = NuttyColor.TextDim)
        }
    }
}

/** The 2×2 tile grid on Home. Every tile opens the sheet that explains it. */
@Composable
fun MetricGrid(
    state: HomeState,
    serverCount: Int,
    activeStreams: Int,
    traffic: String,
    tunnelCaption: String,
    modifier: Modifier = Modifier,
    onTunnel: () -> Unit,
    onNetwork: () -> Unit,
    onServers: () -> Unit,
    onUsage: () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dim.TileGap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dim.TileGap)) {
            MetricTile(
                label = "TUNNEL",
                value = DemoData.tunnelLatency(state),
                unit = "ms",
                caption = tunnelCaption,
                modifier = Modifier.weight(1f),
                onClick = onTunnel,
            )
            MetricTile(
                label = "NETWORK",
                value = DemoData.NETWORK,
                caption = "SKT · battery 84%",
                modifier = Modifier.weight(1f),
                onClick = onNetwork,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dim.TileGap)) {
            MetricTile(
                label = "SERVERS",
                value = serverCount.toString(),
                unit = null,
                caption = "$activeStreams active stream${if (activeStreams == 1) "" else "s"}",
                modifier = Modifier.weight(1f),
                onClick = onServers,
            )
            MetricTile(
                label = "TODAY",
                value = if (state.isLive) traffic else null,
                unit = null,
                caption = "current app session",
                modifier = Modifier.weight(1f),
                onClick = onUsage,
            )
        }
    }
}

/**
 * Simulated status bar, so a rendered frame lines up with the design canvas.
 * Replace with real insets when the app goes edge-to-edge.
 */
@Composable
fun FakeStatusBar(network: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Dim.StatusBarHeight)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(DemoData.CLOCK, style = NuttyType.StatusClock, color = NuttyColor.TextPrimary)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(network, style = NuttyType.MetaStrong, color = NuttyColor.TextMuted)
            Box(
                modifier = Modifier
                    .size(width = 22.dp, height = 11.dp)
                    .border(1.dp, NuttyColor.TextMuted, RoundedCornerShape(3.dp))
                    .padding(1.5.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.72f)
                        .fillMaxHeight()
                        .background(NuttyColor.TextMuted, RoundedCornerShape(1.dp))
                )
            }
        }
    }
}
