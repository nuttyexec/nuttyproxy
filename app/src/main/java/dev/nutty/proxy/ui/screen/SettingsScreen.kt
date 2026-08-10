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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.nutty.proxy.ui.component.CardDivider
import dev.nutty.proxy.ui.component.DangerButton
import dev.nutty.proxy.ui.component.DetailRow
import dev.nutty.proxy.ui.component.NuttyCard
import dev.nutty.proxy.ui.component.NuttyToggle
import dev.nutty.proxy.ui.component.ReadinessRow
import dev.nutty.proxy.ui.component.SectionLabel
import dev.nutty.proxy.ui.model.DemoData
import dev.nutty.proxy.ui.model.ReadinessItem
import dev.nutty.proxy.ui.theme.Dim
import dev.nutty.proxy.ui.theme.NuttyColor
import dev.nutty.proxy.ui.theme.NuttyType

/**
 * Settings — readiness first, preferences second.
 *
 * The always-on checklist is pinned above everything else because it is the only
 * part of Settings that can silently break the product. A background-data
 * restriction is not a preference; it is an outage waiting for the screen to
 * lock, so it sits at the top with a warning marker rather than buried below.
 */
@Composable
fun SettingsScreen(
    deviceName: String,
    readiness: List<ReadinessItem>,
    onNotifications: () -> Unit,
    onBattery: () -> Unit,
    onData: () -> Unit,
    onAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var useMobileData by remember { mutableStateOf(true) }
    var wifiOnly by remember { mutableStateOf(false) }
    var appLock by remember { mutableStateOf(true) }
    var diagnostics by remember { mutableStateOf(false) }

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
        ) {
            Text("Settings", style = NuttyType.ScreenTitle, color = NuttyColor.TextPrimary)
        }

        NuttyCard {
            SectionLabel(
                text = "ALWAYS-ON READINESS",
                modifier = Modifier.padding(
                    start = Dim.CardPadding,
                    end = Dim.CardPadding,
                    top = 13.dp,
                    bottom = 8.dp,
                ),
            )
            Column(
                modifier = Modifier.padding(
                    start = Dim.CardPadding,
                    end = Dim.CardPadding,
                    bottom = 2.dp,
                )
            ) {
                readiness.forEach { item ->
                    CardDivider()
                    ReadinessRow(item, onClick = when (item.label) {
                        "Notifications" -> onNotifications
                        "Battery unrestricted" -> onBattery
                        "Background data" -> onData
                        else -> onAppSettings
                    })
                }
            }
        }

        NuttyCard {
            Column(modifier = Modifier.padding(horizontal = Dim.CardPadding, vertical = 2.dp)) {
                DetailRow(
                    label = "Device name",
                    value = deviceName,
                    labelColor = NuttyColor.TextSecondary,
                    valueColor = NuttyColor.TextMuted,
                    chevron = true,
                    onClick = {},
                )
                CardDivider()
                DetailRow(
                    label = "Heartbeat",
                    value = "Default",
                    labelColor = NuttyColor.TextSecondary,
                    valueColor = NuttyColor.TextMuted,
                    chevron = true,
                    onClick = {},
                )
                CardDivider()
                ToggleRow("Use mobile data", useMobileData) { useMobileData = it }
                CardDivider()
                ToggleRow("Wi-Fi only", wifiOnly) { wifiOnly = it }
            }
        }

        NuttyCard {
            Column(modifier = Modifier.padding(horizontal = Dim.CardPadding, vertical = 2.dp)) {
                DetailRow(
                    label = "Usage warning",
                    value = "8 GB / mo",
                    labelColor = NuttyColor.TextSecondary,
                    valueColor = NuttyColor.TextMuted,
                    chevron = true,
                    onClick = {},
                )
                CardDivider()
                DetailRow(
                    label = "Log retention",
                    value = "7 days",
                    labelColor = NuttyColor.TextSecondary,
                    valueColor = NuttyColor.TextMuted,
                    chevron = true,
                    onClick = {},
                )
                CardDivider()
                ToggleRow("App lock · biometric", appLock) { appLock = it }
                CardDivider()
                ToggleRow("Send diagnostics", diagnostics) { diagnostics = it }
                CardDivider()
                DetailRow(
                    label = "Version",
                    value = "1.4.2 · agent 0.9.1",
                    labelColor = NuttyColor.TextSecondary,
                    valueColor = NuttyColor.TextMuted,
                    chevron = true,
                    onClick = {},
                )
            }
        }

        DangerButton(
            text = "Disconnect all & revoke device",
            onClick = {},
            height = 48.dp,
        )
    }
}

/** A settings row whose control is a switch rather than a value + chevron. */
@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dim.RowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = NuttyType.Row, color = NuttyColor.TextSecondary)
        NuttyToggle(checked, onCheckedChange)
    }
}
