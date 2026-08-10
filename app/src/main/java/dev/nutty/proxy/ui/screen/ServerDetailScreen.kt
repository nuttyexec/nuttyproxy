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
import dev.nutty.proxy.ui.component.ChartHeader
import dev.nutty.proxy.ui.component.DangerButton
import dev.nutty.proxy.ui.component.DetailRow
import dev.nutty.proxy.ui.component.LogRow
import dev.nutty.proxy.ui.component.NuttyCard
import dev.nutty.proxy.ui.component.OutlineButton
import dev.nutty.proxy.ui.component.SectionLabel
import dev.nutty.proxy.ui.component.ServerBadge
import dev.nutty.proxy.ui.component.WeekBars
import dev.nutty.proxy.ui.component.tapText
import dev.nutty.proxy.ui.model.ServerState
import dev.nutty.proxy.ui.model.ServerInfo
import dev.nutty.proxy.ui.model.SheetKey
import dev.nutty.proxy.ui.model.LogEntry
import dev.nutty.proxy.ui.theme.Dim
import dev.nutty.proxy.ui.theme.NuttyColor
import dev.nutty.proxy.ui.theme.NuttyType

/**
 * Server detail — usage, identity, history, and the two things you can do to it.
 *
 * "Pause server" and "Revoke access" sit side by side but are not equals: pause
 * is outlined neutral, revoke is outlined red. Neither is a filled slab, because
 * neither should be the thing your thumb finds by accident.
 */
@Composable
fun ServerDetailScreen(
    server: ServerInfo,
    history: List<LogEntry>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenSheet: (SheetKey) -> Unit,
    onPauseOrResume: () -> Unit,
    onRevoke: () -> Unit,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dim.ScreenPadding)
            .padding(top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(Dim.BlockGap),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dim.TitleBarHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "‹",
                style = NuttyType.GlyphLarge,
                color = NuttyColor.TextMuted,
                modifier = Modifier
                    .tapText(onBack)
                    .padding(end = 4.dp),
            )
            Text(server.name, style = NuttyType.ScreenTitle, color = NuttyColor.TextPrimary)
            ServerBadge(server.state)
        }

        NuttyCard {
            Column(modifier = Modifier.padding(Dim.CardPadding)) {
                ChartHeader(
                    label = "USAGE · 7 DAYS",
                    total = server.today,
                    modifier = Modifier.padding(bottom = 14.dp),
                )
                Text(
                    text = "Current app-session usage. Daily history is not collected.",
                    style = NuttyType.Hint,
                    color = NuttyColor.TextFaint,
                )
            }
        }

        NuttyCard {
            Column(modifier = Modifier.padding(horizontal = Dim.CardPadding, vertical = 2.dp)) {
                DetailRow(
                    label = "Name",
                    value = server.name,
                    valueSuffix = "from server",
                    chevron = true,
                    onClick = { onOpenSheet(SheetKey.Naming) },
                )
                listOf(
                    dev.nutty.proxy.ui.model.SheetRow("Connection", server.lastSeen),
                    dev.nutty.proxy.ui.model.SheetRow("Active streams", server.streams),
                    dev.nutty.proxy.ui.model.SheetRow("Errors", server.errors),
                ).forEach { row ->
                    CardDivider()
                    DetailRow(row.key, row.value)
                }
                CardDivider()
                DetailRow(
                    label = "Certificate",
                    value = server.certificatePin.removePrefix("sha256/").take(12).ifBlank { "—" } + if (server.certificatePin.isBlank()) "" else "…",
                    chevron = true,
                    onClick = { onOpenSheet(SheetKey.Certificate) },
                )
            }
        }

        NuttyCard {
            SectionLabel(
                text = "CONNECTION HISTORY",
                modifier = Modifier.padding(
                    start = Dim.CardPadding,
                    end = Dim.CardPadding,
                    top = 13.dp,
                    bottom = 10.dp,
                ),
            )
            Column(
                modifier = Modifier.padding(
                    start = Dim.CardPadding,
                    end = Dim.CardPadding,
                    bottom = 4.dp,
                )
            ) {
                val entries = history.ifEmpty { listOf(LogEntry(NuttyColor.TextDim, "No connection activity yet", "—")) }
                entries.forEach { entry ->
                    CardDivider()
                    // No severity dot here: every line is a normal session event.
                    LogRow(entry, showDot = false)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Dim.TileGap)) {
            OutlineButton(
                text = if (server.state == ServerState.Paused) "Resume server" else "Pause server",
                onClick = onPauseOrResume,
                modifier = Modifier.weight(1f),
                height = Dim.ButtonHeightSmall,
            )
            DangerButton(
                text = "Revoke access",
                onClick = onRevoke,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
