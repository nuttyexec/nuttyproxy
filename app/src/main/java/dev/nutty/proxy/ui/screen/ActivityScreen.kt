package dev.nutty.proxy.ui.screen

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.nutty.proxy.ui.component.CardDivider
import dev.nutty.proxy.ui.component.ChartHeader
import dev.nutty.proxy.ui.component.GhostPillButton
import dev.nutty.proxy.ui.component.LogRow
import dev.nutty.proxy.ui.component.NuttyCard
import dev.nutty.proxy.ui.component.NuttyChip
import dev.nutty.proxy.ui.component.OutlineButton
import dev.nutty.proxy.ui.component.RequestRow
import dev.nutty.proxy.ui.component.SectionLabel
import dev.nutty.proxy.ui.component.ShareMeter
import dev.nutty.proxy.ui.component.WeekBars
import dev.nutty.proxy.ui.component.tapText
import dev.nutty.proxy.ui.model.LogEntry
import dev.nutty.proxy.ui.model.RequestInfo
import dev.nutty.proxy.ui.model.SheetKey
import dev.nutty.proxy.ui.theme.Dim
import dev.nutty.proxy.ui.theme.NuttyColor
import dev.nutty.proxy.ui.theme.NuttyType

/**
 * Activity — what went through the tunnel.
 *
 * This is the screen most likely to leak something sensitive, so the design
 * points at its own limits: the "3 live · i" control opens the sheet that spells
 * out exactly what is captured, redacted and dropped. Telling the truth about
 * capture is a design element here, not a legal footnote.
 */
@Composable
fun ActivityScreen(
    traffic: String,
    requests: List<RequestInfo>,
    logs: List<LogEntry>,
    modifier: Modifier = Modifier,
    onOpenSheet: (SheetKey) -> Unit,
    onShareReport: () -> Unit,
    onCopyErrorLog: () -> Unit,
) {
    var selectedFilter by remember { mutableStateOf(0) }
    val filters = listOf("All", "Requests", "Connection", "Errors")
    val selected = filters[selectedFilter]
    val shownRequests = if (selected == "All" || selected == "Requests") requests else emptyList()
    val shownLogs = when (selected) {
        "All" -> logs
        "Requests" -> logs.filter { it.text.startsWith("CONNECT ") || it.text.startsWith("GET ") || it.text.startsWith("POST ") || it.text.startsWith("PUT ") || it.text.startsWith("DELETE ") }
        "Connection" -> logs.filter { it.text.contains("tunnel", ignoreCase = true) || it.text.contains("proxy", ignoreCase = true) || it.text.contains("pair", ignoreCase = true) }
        else -> logs.filter { it.color == NuttyColor.Red || it.color == NuttyColor.Amber }
    }

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
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Activity", style = NuttyType.ScreenTitle, color = NuttyColor.TextPrimary)
            GhostPillButton("Share report", onClick = onShareReport)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            filters.forEachIndexed { index, filter ->
                NuttyChip(
                    text = filter,
                    selected = index == selectedFilter,
                    onClick = { selectedFilter = index },
                )
            }
        }

        NuttyCard {
            Column(modifier = Modifier.padding(Dim.CardPadding)) {
                ChartHeader(
                    label = "DATA · CURRENT SESSION",
                    total = traffic,
                    totalStyle = NuttyType.Total,
                    totalColor = NuttyColor.TextPrimary,
                    modifier = Modifier.padding(bottom = 14.dp),
                )
                WeekBars(List(7) { 0f }.toMutableList().also { it[6] = if (traffic == "0 B") 0f else 0.8f }, barHeight = 64.dp, showAxis = false)

                Column(modifier = Modifier.padding(top = 16.dp)) {
                    CardDivider()
                    Column(
                        modifier = Modifier.padding(top = 13.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Text("Per-server totals appear after traffic is recorded.", style = NuttyType.Hint, color = NuttyColor.TextFaint)
                    }
                }
            }
        }

        NuttyCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Dim.CardPadding,
                        end = Dim.CardPadding,
                        top = 13.dp,
                        bottom = 10.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SectionLabel("REQUESTS · LAST 5 MIN")
                Text(
                    text = "${shownRequests.size} recent · i",
                    style = NuttyType.MetaStrong,
                    color = NuttyColor.TextDim,
                    modifier = Modifier.tapText { onOpenSheet(SheetKey.Capture) },
                )
            }
            Column(
                modifier = Modifier.padding(
                    start = Dim.CardPadding,
                    end = Dim.CardPadding,
                    bottom = 4.dp,
                )
            ) {
                shownRequests.take(20).forEach { request ->
                    CardDivider()
                    RequestRow(request) { onOpenSheet(request.sheet) }
                }
            }
        }

        NuttyCard {
            SectionLabel(
                text = "TODAY",
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
                shownLogs.take(100).forEach { entry ->
                    CardDivider()
                    LogRow(entry)
                }
            }
        }

        OutlineButton(
            text = "Copy error log",
            onClick = onCopyErrorLog,
            height = Dim.ButtonHeightSmall,
        )
    }
}
