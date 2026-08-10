package dev.nutty.proxy.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.nutty.proxy.ui.theme.NuttyColor
import dev.nutty.proxy.ui.theme.NuttyShape
import dev.nutty.proxy.ui.theme.NuttyType

private val WEEKDAYS = listOf("M", "T", "W", "T", "F", "S", "S")

/**
 * Seven-day usage bars.
 *
 * There is no y-axis, no gridline and no value label — the chart answers "is
 * today unusual?" and nothing else. Only the final bar (today) is lifted out of
 * the idle grey, which is the entire encoding.
 */
@Composable
fun WeekBars(
    values: List<Float>,
    modifier: Modifier = Modifier,
    barHeight: Dp = 76.dp,
    showAxis: Boolean = true,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEachIndexed { index, fraction ->
                val isToday = index == values.lastIndex
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction.coerceIn(0f, 1f))
                        .background(
                            if (isToday) NuttyColor.BarActive else NuttyColor.BarIdle,
                            NuttyShape.Bar,
                        )
                )
            }
        }
        if (showAxis) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WEEKDAYS.forEachIndexed { index, day ->
                    Text(
                        text = day,
                        style = NuttyType.Axis,
                        color = if (index == WEEKDAYS.lastIndex) NuttyColor.TextMuted
                        else NuttyColor.TextFaint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * A per-server share meter: name, filled track, value.
 *
 * Rank is carried by bar length *and* by a descending grey ramp, so the ordering
 * survives on a dim screen — no hue needed, and no legend to look up.
 */
@Composable
fun ShareMeter(
    label: String,
    fraction: Float,
    value: String,
    rank: Int,
    modifier: Modifier = Modifier,
) {
    val fill = when (rank) {
        0 -> NuttyColor.BarActive
        1 -> NuttyColor.BarSecondary
        else -> NuttyColor.BarTertiary
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            style = NuttyType.Meta,
            color = NuttyColor.TextMuted,
            modifier = Modifier.width(72.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(5.dp)
                .background(NuttyColor.BarTrack, NuttyShape.Meter),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(fill, NuttyShape.Meter)
            )
        }
        Text(
            text = value,
            style = NuttyType.Meta,
            color = NuttyColor.TextSecondary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(56.dp),
        )
    }
}

/** A card header with an eyebrow on the left and a total on the right. */
@Composable
fun ChartHeader(
    label: String,
    total: String,
    modifier: Modifier = Modifier,
    totalStyle: androidx.compose.ui.text.TextStyle = NuttyType.ValueSmall,
    totalColor: Color = NuttyColor.TextMuted,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(label)
        Text(total, style = totalStyle, color = totalColor)
    }
}
