package dev.nutty.proxy.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.nutty.proxy.R
import dev.nutty.proxy.ui.theme.NuttyColor
import dev.nutty.proxy.ui.theme.NuttyType

/** The mark's authored aspect: a 60 x 90 box, so width is two thirds of height. */
private const val MARK_ASPECT = 60f / 90f

/**
 * The Nutty acorn, two-tone.
 *
 * Sized by [height] because the mark is taller than it is wide and every place
 * the design uses it is aligned to a text baseline or a row height, never to a
 * square. Width follows at 2:3.
 *
 * [capColor] is the half that carries meaning — in the notification shade it is
 * the agent's state. [shellColor] stays neutral and only shifts tone with the
 * surface it sits on. Passing the same colour twice gives the flat monochrome
 * treatment the launcher's themed icon uses.
 */
@Composable
fun NuttyMark(
    height: Dp,
    modifier: Modifier = Modifier,
    capColor: Color = NuttyColor.Green,
    shellColor: Color = NuttyColor.TextPrimary,
) {
    Box(
        modifier = modifier.size(width = height * MARK_ASPECT, height = height),
        contentAlignment = Alignment.Center,
    ) {
        // Shell first: the cap overlaps it, and the design has the cap on top.
        Icon(
            painter = painterResource(R.drawable.ic_nutty_mark_shell),
            contentDescription = null,
            tint = shellColor,
            modifier = Modifier.height(height),
        )
        Icon(
            painter = painterResource(R.drawable.ic_nutty_mark_cap),
            contentDescription = null,
            tint = capColor,
            modifier = Modifier.height(height),
        )
    }
}

/**
 * Mark + "NUTTY PROXY" lockup.
 *
 * Wide tracking on the wordmark is what keeps it reading as a mark rather than
 * as a heading — it is set in Mono at label size and never competes with the
 * screen title beneath it.
 */
@Composable
fun NuttyWordmark(
    modifier: Modifier = Modifier,
    markHeight: Dp = 22.dp,
    gap: Dp = 9.dp,
    textStyle: TextStyle = NuttyType.Wordmark,
    textColor: Color = NuttyColor.TextMuted,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        NuttyMark(height = markHeight)
        Text("NUTTY PROXY", style = textStyle, color = textColor)
    }
}
