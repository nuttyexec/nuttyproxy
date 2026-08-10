package dev.nutty.proxy.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.nutty.proxy.ui.model.Screen
import dev.nutty.proxy.ui.theme.Dim
import dev.nutty.proxy.ui.theme.NuttyColor
import dev.nutty.proxy.ui.theme.NuttyShape
import dev.nutty.proxy.ui.theme.NuttyType

/**
 * Bottom navigation.
 *
 * The icons are drawn from primitives rather than imported from a vector set:
 * at 15dp on this palette, a stroked square, two stacked bars, three columns and
 * a ring stay unambiguous, and they inherit the exact border weights the rest of
 * the design uses. Selection is carried by colour alone — no pill, no indicator.
 */
@Composable
fun BottomTabs(
    current: Screen,
    modifier: Modifier = Modifier,
    onSelect: (Screen) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(NuttyColor.SurfaceTabBar),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(NuttyColor.OutlineSoft)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dim.TabBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Server detail is a child of Servers, so it keeps that tab lit.
            TabItem("Home", current == Screen.Home, Modifier.weight(1f), { onSelect(Screen.Home) }) {
                HomeGlyph(it)
            }
            TabItem(
                label = "Servers",
                selected = current == Screen.Servers || current == Screen.ServerDetail,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(Screen.Servers) },
            ) { ServersGlyph(it) }
            TabItem("Activity", current == Screen.Activity, Modifier.weight(1f), { onSelect(Screen.Activity) }) {
                ActivityGlyph(it)
            }
            TabItem("Settings", current == Screen.Settings, Modifier.weight(1f), { onSelect(Screen.Settings) }) {
                SettingsGlyph(it)
            }
        }
    }
}

@Composable
private fun TabItem(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    glyph: @Composable (Color) -> Unit,
) {
    val tint = if (selected) NuttyColor.TextPrimary else NuttyColor.TextFaintest
    Column(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(15.dp), contentAlignment = Alignment.Center) { glyph(tint) }
        Text(label, style = NuttyType.Tab, color = tint)
    }
}

@Composable
private fun HomeGlyph(tint: Color) {
    Box(
        Modifier
            .size(15.dp)
            .border(1.6.dp, tint, RoundedCornerShape(4.dp))
    )
}

@Composable
private fun ServersGlyph(tint: Color) {
    Column(
        modifier = Modifier.size(15.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        repeat(2) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .border(1.6.dp, tint, RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
private fun ActivityGlyph(tint: Color) {
    Row(
        modifier = Modifier.size(15.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        listOf(7.dp, 14.dp, 10.dp).forEach { barHeight ->
            Box(
                Modifier
                    .size(width = 3.dp, height = barHeight)
                    .background(tint, NuttyShape.Bar)
            )
        }
    }
}

@Composable
private fun SettingsGlyph(tint: Color) {
    Box(
        Modifier
            .size(15.dp)
            .border(1.6.dp, tint, CircleShape)
    )
}
