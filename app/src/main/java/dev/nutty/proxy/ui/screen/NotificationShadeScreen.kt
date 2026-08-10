package dev.nutty.proxy.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.nutty.proxy.ui.model.DemoData
import dev.nutty.proxy.ui.model.NotificationPreview
import dev.nutty.proxy.ui.theme.NuttyColor
import dev.nutty.proxy.ui.theme.NuttyShape
import dev.nutty.proxy.ui.theme.NuttyType

/**
 * Frame 15 — the four ongoing-notification states, on a mock lock screen.
 *
 * This is a *specification surface*, not a screen the app navigates to: the real
 * notifications are built by `ProxyNotifications`. It exists so the four states
 * can be reviewed side by side, which is the only way to catch the thing that
 * matters — that every one of them carries state, connection count and exactly
 * one action.
 *
 * The rule the design states outright: colour lives in the app icon tile only,
 * so the shade stays legible whatever the system theme does around it.
 */
@Composable
fun NotificationShadeScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NuttyColor.BgShade)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
            .padding(top = 6.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = DemoData.CLOCK,
                style = NuttyType.Clock,
                color = NuttyColor.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Mon, Aug 10",
                style = NuttyType.Item,
                color = NuttyColor.TextDim,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        DemoData.notifications.forEach { NotificationCard(it) }
    }
}

@Composable
private fun NotificationCard(notification: NotificationPreview, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(NuttyShape.Well)
            .background(NuttyColor.SurfaceNotification, NuttyShape.Well)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The only coloured element — state as a 17dp chip, not as text colour.
            Box(
                modifier = Modifier
                    .size(17.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(notification.tile, RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("N", style = NuttyType.Marker, color = NuttyColor.Bg)
            }
            Text(notification.source, style = NuttyType.NotifSource, color = NuttyColor.TextMuted)
        }

        Text(notification.title, style = NuttyType.CardTitle, color = NuttyColor.TextPrimary)
        Text(notification.body, style = NuttyType.Item, color = NuttyColor.TextMuted)

        Column(modifier = Modifier.padding(top = 8.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(NuttyColor.OutlineDim)
            )
            Row(
                modifier = Modifier.padding(top = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Exactly one meaningful action per state, plus a way to look closer.
                Text(
                    text = notification.primaryAction,
                    style = NuttyType.Action,
                    color = notification.primaryColor,
                )
                Text(
                    text = notification.secondaryAction,
                    style = NuttyType.Action,
                    color = NuttyColor.TextTertiary,
                )
            }
        }
    }
}
