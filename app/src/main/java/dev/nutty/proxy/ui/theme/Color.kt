package dev.nutty.proxy.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The Nutty Proxy palette, transcribed from `Nutty Proxy.dc.html` / `Phone.dc.html`.
 *
 * Dark-only on purpose. These are instrument-panel neutrals — near-black grounds
 * with a narrow, deliberately cool grey ramp — so a light variant would not be a
 * recolour, it would be a different product. There is no `values-night/`.
 *
 * Two rules the design enforces and this file encodes:
 *  1. Status colour is carried by *containers* (bg + border + text triples), never
 *     by a lone accent on a neutral card. Use the [Status] triples below.
 *  2. Paused is grey, never red. Red means "unusable", amber means "at risk".
 */
object NuttyColor {

    // ── Grounds ────────────────────────────────────────────────────────────────
    /** App background, and the phone frame fill. */
    val Bg = Color(0xFF0B0C0E)
    /** Notification shade — one step below the app so the shade reads as "behind". */
    val BgShade = Color(0xFF08090A)
    /** Standard card / tile surface. */
    val Surface = Color(0xFF131518)
    /** Muted card — revoked servers, retired rows. */
    val SurfaceMuted = Color(0xFF0F1114)
    /** Raised surface: bottom sheets. */
    val SurfaceRaised = Color(0xFF16191D)
    /** Inset surface: code blocks, the QR viewfinder well. */
    val SurfaceInset = Color(0xFF0E1013)
    /** Notification card in the shade. */
    val SurfaceNotification = Color(0xFF1A1D21)
    /** Bottom tab bar. */
    val SurfaceTabBar = Color(0xFF0E0F12)
    /** Inactive chip / badge fill. */
    val SurfaceChip = Color(0xFF17191C)
    /** HTTP method badge fill. */
    val SurfaceMethod = Color(0xFF1B1F24)
    /** Paused status container. */
    val SurfacePaused = Color(0xFF15171A)

    // ── Lines ──────────────────────────────────────────────────────────────────
    /** Default card border. */
    val Outline = Color(0xFF23272D)
    /** Quietest border — tab bar top edge, muted cards. */
    val OutlineSoft = Color(0xFF1C2025)
    val OutlineMuted = Color(0xFF272C33)
    /** Retired/quiet edge — revoked badge, notification action divider. */
    val OutlineDim = Color(0xFF262B31)
    /** Interactive affordance border — (i) buttons, chips, secondary actions. */
    val OutlineStrong = Color(0xFF2A2F36)
    /** Focused / primary-weight border — buttons, active text fields. */
    val OutlineFocus = Color(0xFF363C44)
    /** Divider inside a card. */
    val Divider = Color(0xFF1D2126)
    /** Divider inside a sheet (sheets sit one surface higher). */
    val DividerRaised = Color(0xFF22262B)
    /** Drag handle and home indicator. */
    val Handle = Color(0xFF2E343B)

    // ── Text ───────────────────────────────────────────────────────────────────
    /** Headlines, primary values. */
    val TextPrimary = Color(0xFFEDEFF2)
    /** Body text inside cards. */
    val TextSecondary = Color(0xFFDCE0E4)
    /** Emphasis on a muted row; also links. */
    val TextTertiary = Color(0xFFC9CED4)
    /** Labels, secondary values. */
    val TextMuted = Color(0xFF9AA1A9)
    /** Supporting metadata. */
    val TextDim = Color(0xFF8A8F98)
    /** Section labels (the mono all-caps eyebrows). */
    val TextLabel = Color(0xFF828A93)
    /** Timestamps, hints. */
    val TextFaint = Color(0xFF7A828B)
    /** Deepest readable text — chevrons, inactive tabs. */
    val TextFaintest = Color(0xFF6A717A)
    /** Placeholder em-dash when a metric has no value. */
    val TextDisabled = Color(0xFF4E555D)

    // ── Status: connected / healthy ────────────────────────────────────────────
    val Green = Color(0xFF3ECF8E)
    val GreenContainer = Color(0xFF0F2318)
    val GreenOutline = Color(0xFF1D4832)
    val GreenText = Color(0xFF8FBFA6)

    // ── Status: at risk ────────────────────────────────────────────────────────
    val Amber = Color(0xFFE8B23A)
    val AmberContainer = Color(0xFF221B0E)
    val AmberOutline = Color(0xFF47391B)
    /** Softer amber container for inline notices sitting on the app ground. */
    val AmberContainerSoft = Color(0xFF1D190F)
    val AmberOutlineSoft = Color(0xFF3A3018)
    val AmberText = Color(0xFFE4D8BC)
    val AmberMeta = Color(0xFFC9A967)

    // ── Status: unusable ───────────────────────────────────────────────────────
    val Red = Color(0xFFE5544B)
    val RedContainer = Color(0xFF20100F)
    val RedOutline = Color(0xFF4A2422)
    val RedContainerSoft = Color(0xFF1A1011)
    val RedOutlineSoft = Color(0xFF3A1E1D)
    /** Border for destructive outlined buttons. */
    val RedOutlineButton = Color(0xFF4A2A28)
    val RedText = Color(0xFFC08A85)
    val RedTextSoft = Color(0xFFDFC3C0)
    val RedMeta = Color(0xFFC99A96)

    // ── Status: paused (grey — deliberately not red) ───────────────────────────
    val Grey = Color(0xFF7A828B)
    val GreyOutline = Color(0xFF272C33)

    // ── Data visualisation ─────────────────────────────────────────────────────
    /** Past days in a 7-day bar chart. */
    val BarIdle = Color(0xFF2B3138)
    /** Today — the only emphasised bar. */
    val BarActive = Color(0xFF8A8F98)
    /** Unfilled portion of a share meter. */
    val BarTrack = Color(0xFF1D2126)
    /** Second and third rank in a share meter, so ranking reads without hue. */
    val BarSecondary = Color(0xFF5A616A)
    val BarTertiary = Color(0xFF3A4048)

    // ── Brand / splash ─────────────────────────────────────────────────────────
    // The icon tile's own ramp, reused when the mark is shown at display size.
    val IconTileHigh = Color(0xFF232830)
    val IconTile = Color(0xFF14171B)
    val IconTileLow = Color(0xFF0E1013)
    /** Lit corner of the splash ground — one step above the tile so it reads behind it. */
    val SplashHigh = Color(0xFF1A1E24)

    // ── Overlay ────────────────────────────────────────────────────────────────
    val Scrim = Color(0xB8040506)

    // ── Pressed states ─────────────────────────────────────────────────────────
    // The source design declares these as CSS `:hover`. Android has no hover, so
    // the pressed state is their analogue — and the default ripple (a light
    // overlay on a dark surface) already does roughly what they do. They are kept
    // as named tokens so a custom `indication` can match the design exactly
    // rather than approximating it a second time.
    /** Outlined button, pressed. */
    val PressedSurface = Color(0xFF15181C)
    /** Card border, pressed. */
    val PressedOutlineCard = Color(0xFF333941)
    /** Circular "(i)" border, pressed. */
    val PressedOutlineInfo = Color(0xFF4A5158)

    /**
     * A status container as one unit: fill, border, label colour, dot.
     * Pulling these as a triple is what stops a stray `Green` landing on an
     * amber card.
     */
    data class Status(
        val dot: Color,
        val container: Color,
        val outline: Color,
        val title: Color,
        val meta: Color,
    )

    val StatusConnected = Status(Green, GreenContainer, GreenOutline, TextPrimary, GreenText)
    val StatusReconnecting = Status(Amber, AmberContainer, AmberOutline, TextPrimary, AmberMeta)
    val StatusAttention = Status(Amber, AmberContainer, AmberOutline, TextPrimary, AmberMeta)
    val StatusPaused = Status(Grey, SurfacePaused, GreyOutline, TextTertiary, TextFaintest)
    val StatusDisconnected = Status(Red, RedContainer, RedOutline, TextPrimary, RedText)
}
