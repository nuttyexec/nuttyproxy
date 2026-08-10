package dev.nutty.proxy.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
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
import dev.nutty.proxy.ui.component.ReadinessRow
import dev.nutty.proxy.ui.component.SectionLabel
import dev.nutty.proxy.ui.model.ReadinessItem
import dev.nutty.proxy.ui.theme.Dim
import dev.nutty.proxy.ui.theme.NuttyColor
import dev.nutty.proxy.ui.theme.NuttyType
import dev.nutty.proxy.ReleaseUpdate

/** Settings only exposes controls that change real agent or Android state. */
@Composable
fun SettingsScreen(
    deviceName: String,
    readiness: List<ReadinessItem>,
    onNotifications: () -> Unit,
    onBattery: () -> Unit,
    onData: () -> Unit,
    onAppSettings: () -> Unit,
    onRename: (String) -> Unit,
    onDisconnectAll: () -> Unit,
    update: ReleaseUpdate,
    onCheckForUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingName by remember { mutableStateOf(false) }
    var confirmingDisconnect by remember { mutableStateOf(false) }
    var nameDraft by remember(deviceName) { mutableStateOf(deviceName) }

    if (editingName) {
        AlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text("Device name") },
            text = {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it.take(32) },
                    label = { Text("Name visible to paired servers") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(nameDraft.trim().ifBlank { "Phone" })
                    editingName = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingName = false }) { Text("Cancel") } },
        )
    }
    if (confirmingDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmingDisconnect = false },
            title = { Text("Remove local pairings?") },
            text = { Text("This stops the proxy and deletes this phone's local keys. Remove each phone from the server separately with `nuttyproxy agents revoke`.") },
            confirmButton = {
                TextButton(onClick = { onDisconnectAll(); confirmingDisconnect = false }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmingDisconnect = false }) { Text("Cancel") } },
        )
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dim.ScreenPadding)
            .padding(top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(Dim.BlockGap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(Dim.TitleBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) { Text("Settings", style = NuttyType.ScreenTitle, color = NuttyColor.TextPrimary) }

        NuttyCard {
            SectionLabel(
                text = "ALWAYS-ON READINESS",
                modifier = Modifier.padding(start = Dim.CardPadding, end = Dim.CardPadding, top = 13.dp, bottom = 8.dp),
            )
            Column(modifier = Modifier.padding(start = Dim.CardPadding, end = Dim.CardPadding, bottom = 2.dp)) {
                readiness.forEach { item ->
                    CardDivider()
                    ReadinessRow(item, onClick = when (item.label) {
                        "Notifications" -> onNotifications
                        "Battery background access" -> onBattery
                        "Background data" -> onData
                        else -> null
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
                    onClick = { nameDraft = deviceName; editingName = true },
                )
                CardDivider()
                DetailRow(
                    label = "Heartbeat",
                    value = "Server controlled",
                    labelColor = NuttyColor.TextSecondary,
                    valueColor = NuttyColor.TextMuted,
                )
                CardDivider()
                DetailRow(
                    label = "System app settings",
                    value = "Open",
                    labelColor = NuttyColor.TextSecondary,
                    valueColor = NuttyColor.TextMuted,
                    chevron = true,
                    onClick = onAppSettings,
                )
                CardDivider()
                DetailRow(
                    label = "App update",
                    value = update.message,
                    labelColor = NuttyColor.TextSecondary,
                    valueColor = if (update.available) NuttyColor.Green else NuttyColor.TextMuted,
                    chevron = !update.checking,
                    onClick = if (update.checking) null else if (update.available) onDownloadUpdate else onCheckForUpdate,
                )
            }
        }

        DangerButton(
            text = "Stop proxy & remove local pairings",
            onClick = { confirmingDisconnect = true },
            height = 48.dp,
        )
    }
}
