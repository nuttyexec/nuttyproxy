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
import dev.nutty.proxy.ui.component.InfoButton
import dev.nutty.proxy.ui.component.ServerCard
import dev.nutty.proxy.ui.component.dashedBorder
import dev.nutty.proxy.ui.component.tapText
import dev.nutty.proxy.ui.model.ServerInfo
import dev.nutty.proxy.ui.theme.Dim
import dev.nutty.proxy.ui.theme.NuttyColor
import dev.nutty.proxy.ui.theme.NuttyType

/**
 * Servers — the list of machines allowed to reach this phone.
 *
 * Ordering is deliberate: active first, then paused, then revoked. A revoked
 * server is kept visible rather than deleted, because "this used to have access"
 * is a security fact worth being able to see.
 */
@Composable
fun ServersScreen(
    servers: List<ServerInfo>,
    modifier: Modifier = Modifier,
    onOpenServer: (ServerInfo) -> Unit,
    onAddServer: () -> Unit,
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
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Servers", style = NuttyType.ScreenTitle, color = NuttyColor.TextPrimary)
            InfoButton(onClick = onAddServer, glyph = "+", glyphStyle = NuttyType.GlyphMedium)
        }

        if (servers.isEmpty()) {
            Text(
                text = "No servers paired yet",
                style = NuttyType.Item,
                color = NuttyColor.TextDim,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        servers.forEach { server ->
            ServerCard(
                server = server,
                onClick = { onOpenServer(server) },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .dashedBorder(NuttyColor.OutlineStrong, cornerRadius = 14.dp)
                .tapText(onAddServer)
                .padding(horizontal = Dim.CardPadding, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Add server by QR or pairing code",
                style = NuttyType.Item,
                color = NuttyColor.TextDim,
                modifier = Modifier.weight(1f),
            )
            Text("+", style = NuttyType.Glyph, color = NuttyColor.TextFaintest)
        }
    }
}
