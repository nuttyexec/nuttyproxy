package dev.nutty.proxy.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.nutty.proxy.ui.component.CardDivider
import dev.nutty.proxy.ui.component.InfoButton
import dev.nutty.proxy.ui.component.LogRow
import dev.nutty.proxy.ui.component.MetricGrid
import dev.nutty.proxy.ui.component.NuttyCard
import dev.nutty.proxy.ui.component.OutlineButton
import dev.nutty.proxy.ui.component.PrimaryButton
import dev.nutty.proxy.ui.component.SectionLabel
import dev.nutty.proxy.ui.component.StatusDot
import dev.nutty.proxy.ui.component.StatusPill
import dev.nutty.proxy.ui.component.tapText
import dev.nutty.proxy.ui.model.HomeState
import dev.nutty.proxy.ui.model.LogEntry
import dev.nutty.proxy.ui.model.SheetKey
import dev.nutty.proxy.ui.theme.Dim
import dev.nutty.proxy.ui.theme.NuttyColor
import dev.nutty.proxy.ui.theme.NuttyType

/**
 * Home — five states, one layout.
 *
 * The layout never reflows between states: pill, notice, action, tiles, recent.
 * Only the *contents* change. That is what makes a state change readable — the
 * eye lands on the same spot every time and only the colour and the words moved.
 */
@Composable
fun HomeScreen(
    state: HomeState,
    deviceName: String,
    serverCount: Int,
    activeStreams: Int,
    traffic: String,
    tunnelCaption: String,
    networkValue: String,
    networkCaption: String,
    recent: List<LogEntry>,
    modifier: Modifier = Modifier,
    onOpenSheet: (SheetKey) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onViewActivity: () -> Unit,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dim.ScreenPadding)
            .padding(top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(Dim.BlockGap),
    ) {
        // Title row: device name, agent id, and the (i) into the connection sheet.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dim.TitleBarHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text(deviceName, style = NuttyType.ScreenTitle, color = NuttyColor.TextPrimary)
                Text(
                    text = "$serverCount paired server${if (serverCount == 1) "" else "s"}",
                    style = NuttyType.Meta,
                    color = NuttyColor.TextFaintest,
                )
            }
            InfoButton(onClick = { onOpenSheet(SheetKey.Status) })
        }

        val sheetForState = when (state) {
            HomeState.Attention -> SheetKey.Attention
            HomeState.Disconnected -> SheetKey.Disconnected
            else -> SheetKey.Status
        }

        StatusPill(
            state = state,
            meta = tunnelCaption,
            // Paused is the one state with nothing to explain — the user did it.
            onClick = if (state == HomeState.Paused) null else ({ onOpenSheet(sheetForState) }),
        )

        when (state) {
            HomeState.Connected, HomeState.Attention ->
                OutlineButton("Pause proxy", onPause)

            HomeState.Reconnecting ->
                OutlineButton(
                    text = "Cancel reconnect",
                    onClick = onPause,
                    textColor = NuttyColor.TextTertiary,
                    leading = { StatusDot(NuttyColor.Amber, size = 7.dp, blinking = true) },
                )

            // Paused and Disconnected both need one obvious way back to serving,
            // so they get the white slab. The other three do not.
            HomeState.Paused -> PrimaryButton("Start proxy", onResume)
            HomeState.Disconnected -> PrimaryButton("Retry now", onRetry)
        }

        MetricGrid(
            state = state,
            serverCount = serverCount,
            activeStreams = activeStreams,
            traffic = traffic,
            tunnelCaption = tunnelCaption,
            networkValue = networkValue,
            networkCaption = networkCaption,
            onTunnel = { onOpenSheet(SheetKey.Status) },
            onNetwork = { onOpenSheet(SheetKey.Network) },
            onServers = { onOpenSheet(SheetKey.Servers) },
            onUsage = { onOpenSheet(SheetKey.Usage) },
        )

        NuttyCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Dim.CardPadding, end = Dim.CardPadding, top = 13.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SectionLabel("RECENT")
                Text(
                    text = "View all",
                    style = NuttyType.Chip,
                    color = NuttyColor.TextDim,
                    modifier = Modifier.tapText(onViewActivity),
                )
            }
            Column(
                modifier = Modifier.padding(
                    start = Dim.CardPadding,
                    end = Dim.CardPadding,
                    bottom = 4.dp,
                )
            ) {
                // The state-specific line goes first, then the shared history —
                // so "what just changed" is always the top row.
                val entries = recent.take(6).ifEmpty { listOf(LogEntry(NuttyColor.TextDim, "No activity yet", "—")) }
                entries.forEach { entry ->
                    CardDivider()
                    LogRow(entry)
                }
            }
        }
    }
}
