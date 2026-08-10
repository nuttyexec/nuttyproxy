package dev.nutty.proxy.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nutty.proxy.ui.model.LogEntry
import dev.nutty.proxy.ui.model.ReadinessItem
import dev.nutty.proxy.ui.model.ReadinessState
import dev.nutty.proxy.ui.model.RequestInfo
import dev.nutty.proxy.ui.model.ServerInfo
import dev.nutty.proxy.ui.model.ServerState
import dev.nutty.proxy.ui.theme.Dim
import dev.nutty.proxy.ui.theme.NuttyColor
import dev.nutty.proxy.ui.theme.NuttyShape
import dev.nutty.proxy.ui.theme.NuttyType

/**
 * One line of the event log: a colour dot, a sentence, a time.
 *
 * The dot is the only colour in the row, and it is the severity. Everything else
 * stays neutral so a screen full of log lines never looks alarming by accident.
 */
@Composable
fun LogRow(entry: LogEntry, modifier: Modifier = Modifier, showDot: Boolean = true) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showDot) {
            Box(Modifier.size(6.dp).background(entry.color, CircleShape))
        }
        Text(
            text = entry.text,
            style = NuttyType.Item,
            color = NuttyColor.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(entry.at, style = NuttyType.Meta, color = NuttyColor.TextFaintest)
    }
}

/**
 * A captured request. Method badge, URL, metadata, status code.
 *
 * The status code is the only coloured element — green for success, amber for a
 * rate limit, red for failure — because that is what a person scans for.
 */
@Composable
fun RequestRow(request: RequestInfo, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            text = request.method,
            style = NuttyType.Method,
            color = NuttyColor.TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(42.dp)
                .clip(NuttyShape.Method)
                .background(NuttyColor.SurfaceMethod, NuttyShape.Method)
                .border(1.dp, NuttyColor.OutlineMuted, NuttyShape.Method)
                .padding(vertical = 4.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = request.url,
                style = NuttyType.Url,
                color = NuttyColor.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(request.meta, style = NuttyType.MetaSmall, color = NuttyColor.TextFaintest)
        }
        Text(request.status, style = NuttyType.MetaStrong, color = request.statusColor)
    }
}

/**
 * A paired server.
 *
 * Revoked servers collapse to a single dim line — they are history, not
 * inventory, and giving them the full three-stat card would imply they still
 * carry traffic.
 */
@Composable
fun ServerCard(server: ServerInfo, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    if (server.state == ServerState.Revoked) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(NuttyShape.Card)
                .background(NuttyColor.SurfaceMuted, NuttyShape.Card)
                .border(1.dp, NuttyColor.OutlineSoft, NuttyShape.Card)
                .padding(Dim.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(server.name, style = NuttyType.CardTitle, color = NuttyColor.TextFaintest)
            ServerBadge(server.state)
            Text(
                text = server.lastSeen,
                style = NuttyType.Meta,
                color = NuttyColor.TextFaint,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    NuttyCard(modifier = modifier, onClick = onClick) {
        Column(
            modifier = Modifier.padding(Dim.CardPadding),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text(
                    text = server.name,
                    style = NuttyType.CardTitle,
                    color = if (server.state == ServerState.Paused) NuttyColor.TextTertiary
                    else NuttyColor.TextPrimary,
                )
                ServerBadge(server.state)
                Text(
                    text = server.lastSeen,
                    style = NuttyType.Meta,
                    color = NuttyColor.TextFaintest,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }

            Row {
                // A paused server's numbers are history, not readings, so the
                // whole row dims. An active server keeps its stats bright even
                // when a value happens to be zero — that zero is a measurement.
                val statColor =
                    if (server.state == ServerState.Paused) NuttyColor.TextFaintest
                    else NuttyColor.TextPrimary
                ServerStat("STREAMS", server.streams, Modifier.weight(1f), statColor)
                ServerStat("TODAY", server.today, Modifier.weight(1f), statColor)
                ServerStat(
                    label = "ERRORS",
                    value = server.errors,
                    modifier = Modifier.weight(1f),
                    // Zero errors is good news, and good news stays quiet.
                    valueColor = if (server.errors != "0") NuttyColor.Red else NuttyColor.TextFaintest,
                )
            }

            if (server.errorNote != null) {
                CardDivider()
                Row(
                    modifier = Modifier.padding(top = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(6.dp).background(NuttyColor.Red, CircleShape))
                    Text(
                        text = server.errorNote,
                        style = NuttyType.Caption,
                        color = NuttyColor.RedMeta,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = server.errorAt.orEmpty(),
                        style = NuttyType.Meta,
                        color = NuttyColor.TextFaintest,
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = NuttyColor.TextPrimary,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = NuttyType.MicroLabel, color = NuttyColor.TextFaintest)
        Text(value, style = NuttyType.Stat, color = valueColor)
    }
}

@Composable
fun ServerBadge(state: ServerState, modifier: Modifier = Modifier) = when (state) {
    ServerState.Allowed -> Badge(
        text = state.label,
        textColor = NuttyColor.Green,
        background = NuttyColor.GreenContainer,
        border = NuttyColor.GreenOutline,
        modifier = modifier,
    )
    ServerState.Paused -> Badge(
        text = state.label,
        textColor = NuttyColor.TextMuted,
        background = NuttyColor.SurfaceChip,
        border = NuttyColor.OutlineStrong,
        modifier = modifier,
    )
    ServerState.Revoked -> Badge(
        text = state.label,
        textColor = NuttyColor.TextFaintest,
        background = NuttyColor.SurfaceChip,
        border = NuttyColor.OutlineDim,
        modifier = modifier,
    )
}

/** The circular ✓ / ! / step-number marker on a readiness row. */
@Composable
fun ReadinessMarker(
    state: ReadinessState,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 16.dp,
    pendingLabel: String = "",
) {
    val (glyph, textColor, background, border) = when (state) {
        ReadinessState.Done -> Quad("✓", NuttyColor.Green, NuttyColor.GreenContainer, NuttyColor.GreenOutline)
        ReadinessState.Warning -> Quad("!", NuttyColor.Amber, NuttyColor.AmberContainer, NuttyColor.AmberOutline)
        ReadinessState.Pending -> Quad(pendingLabel, NuttyColor.TextFaintest, Color.Transparent, NuttyColor.OutlineStrong)
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background, CircleShape)
            .border(1.dp, border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        // Marker styles carry no tracking — a single glyph with letter-spacing
        // sits off-centre inside a circle.
        Text(
            text = glyph,
            style = if (size >= 18.dp) NuttyType.MarkerLarge else NuttyType.Marker,
            color = textColor,
        )
    }
}

private data class Quad(
    val glyph: String,
    val textColor: Color,
    val background: Color,
    val border: Color,
)

/** A readiness row in Settings — marker, label, chevron. */
@Composable
fun ReadinessRow(item: ReadinessItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        ReadinessMarker(item.state)
        Text(
            text = item.label,
            style = NuttyType.Row,
            color = if (item.state == ReadinessState.Warning) NuttyColor.AmberText
            else NuttyColor.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Chevron()
    }
}
