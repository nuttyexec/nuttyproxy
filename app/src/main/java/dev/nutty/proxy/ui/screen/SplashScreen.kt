package dev.nutty.proxy.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import dev.nutty.proxy.ui.component.NuttyMark
import dev.nutty.proxy.ui.theme.NuttyColor
import dev.nutty.proxy.ui.theme.NuttyType

/**
 * Frame 10 — splash.
 *
 * The only screen in the app that shows the mark at display size, and the only
 * one with no chrome at all: no status bar content, no tabs, not even the
 * gesture pill. It is a held breath before the agent reports its state, so it
 * says nothing the next screen will say better.
 *
 * Two radial gradients, lit from the same corner (30% / 22%): a dark ground and
 * the icon tile on top of it. Same light source as the launcher icon, so the app
 * opening out of its own icon reads as continuous.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        0f to NuttyColor.SplashHigh,
                        0.62f to NuttyColor.SurfaceMuted,
                        1f to NuttyColor.Bg,
                        center = Offset(size.width * 0.30f, size.height * 0.22f),
                        radius = maxOf(size.width, size.height) * 1.2f,
                    )
                )
            },
        verticalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(34.dp))
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            0f to NuttyColor.IconTileHigh,
                            0.62f to NuttyColor.IconTile,
                            1f to NuttyColor.IconTileLow,
                            center = Offset(size.width * 0.30f, size.height * 0.22f),
                            radius = maxOf(size.width, size.height) * 1.2f,
                        )
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            NuttyMark(height = 67.dp)
        }

        Text(
            text = "NUTTY PROXY",
            style = NuttyType.WordmarkLarge,
            color = NuttyColor.TextMuted,
        )
    }
}
