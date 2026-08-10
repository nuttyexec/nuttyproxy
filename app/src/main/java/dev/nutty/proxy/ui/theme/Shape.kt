package dev.nutty.proxy.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Corner radii. The design runs a tight ramp — nothing is a stadium, nothing is
 * square — so the family reads as one machined set. Larger surface, larger radius.
 */
object NuttyShape {
    /** Phone frame (design-canvas only; the real app is full-bleed). */
    val Frame = RoundedCornerShape(38.dp)
    /** Bottom sheet — top corners only. */
    val Sheet = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    /** QR viewfinder well, notification card. */
    val Well = RoundedCornerShape(24.dp)
    /** Status pill. */
    val Pill = RoundedCornerShape(17.dp)
    /** Card and metric tile. */
    val Card = RoundedCornerShape(16.dp)
    /** Full-width button, text field. */
    val Button = RoundedCornerShape(15.dp)
    /** Inline notice, half-width button. */
    val Notice = RoundedCornerShape(14.dp)
    /** Pairing-code cell. */
    val Cell = RoundedCornerShape(13.dp)
    /** Code block. */
    val Code = RoundedCornerShape(12.dp)
    /** Toggle track. */
    val Toggle = RoundedCornerShape(12.dp)
    /** Pill button inside a notice ("Fix", "Allow"). */
    val ChipStrong = RoundedCornerShape(10.dp)
    /** Filter chip. */
    val Chip = RoundedCornerShape(9.dp)
    /** Status badge (ALLOWED / PAUSED / REVOKED). */
    val Badge = RoundedCornerShape(6.dp)
    /** HTTP method badge. */
    val Method = RoundedCornerShape(5.dp)
    /** Chart bar. */
    val Bar = RoundedCornerShape(3.dp)
    /** Share meter. */
    val Meter = RoundedCornerShape(3.dp)
}

/**
 * Spacing and fixed sizes. Everything is on a 2dp grid; the common rhythm is
 * 12dp between blocks, 10dp inside a grid, 13–14dp of card padding.
 */
object Dim {
    /** Horizontal padding on the four tabbed screens. */
    val ScreenPadding = 16.dp
    /** Horizontal padding on onboarding screens — wider, fewer elements. */
    val OnboardPadding = 22.dp
    /** Gap between top-level blocks on a screen. */
    val BlockGap = 12.dp
    /** Gap inside a grid or a button pair. */
    val TileGap = 10.dp
    /** Card interior padding. */
    val CardPadding = 14.dp
    /** Vertical padding of a divider-separated row. */
    val RowPadding = 13.dp

    /** Simulated status bar. */
    val StatusBarHeight = 40.dp
    /** Screen title row. */
    val TitleBarHeight = 38.dp
    /** Status pill. */
    val StatusPillHeight = 58.dp
    /** Metric tile. */
    val TileHeight = 94.dp
    /** Full-width button. */
    val ButtonHeight = 50.dp
    /** Half-width / secondary button. */
    val ButtonHeightSmall = 46.dp
    /** Text field. */
    val FieldHeight = 56.dp
    /** Tab bar (excluding the gesture inset). */
    val TabBarHeight = 62.dp
    /** Gesture-navigation inset the design reserves. */
    val GestureInset = 22.dp
    /** The circular (i) affordance. */
    val InfoButton = 30.dp

    /** Design frame — a Pixel-class viewport, so dp map 1:1 from the canvas. */
    val FrameWidth = 412.dp
    val FrameHeight = 916.dp
}
