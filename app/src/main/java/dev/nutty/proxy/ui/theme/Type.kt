package dev.nutty.proxy.ui.theme

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.nutty.proxy.R

/** IBM Plex Sans — everything a person reads as prose. */
val PlexSans = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_bold, FontWeight.Bold),
)

/** IBM Plex Mono — everything the *machine* reports: values, IDs, timings, codes. */
val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

private fun sans(
    size: Int,
    weight: FontWeight,
    lineHeight: Int = size,
    letterSpacing: Float = 0f,
) = TextStyle(
    fontFamily = PlexSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.em,
    platformStyle = NoFontPadding,
)

private fun mono(
    size: Int,
    weight: FontWeight,
    lineHeight: Int = size,
    letterSpacing: Float = 0f,
) = TextStyle(
    fontFamily = PlexMono,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.em,
    platformStyle = NoFontPadding,
)

/**
 * The type scale, one entry per role that actually appears in the design.
 *
 * The split is the whole point: **Sans says what happened, Mono says the number.**
 * A latency, a fingerprint, a timestamp, a gateway id and a status code are all
 * Mono; every sentence around them is Sans. Keep that boundary and the screens
 * stay scannable without any extra colour.
 */
object NuttyType {

    // ── Sans: headings ─────────────────────────────────────────────────────────
    /** Notification-shade clock. */
    val Clock = sans(56, FontWeight.Light)
    /** Onboarding step headline ("Scan the pairing QR"). */
    val OnboardTitle = sans(28, FontWeight.SemiBold, lineHeight = 34)
    /** Success headline on the connection test. */
    val ResultTitle = sans(26, FontWeight.SemiBold, lineHeight = 31)
    /** Screen title ("Home", "Servers", "Activity", "Settings"). */
    val ScreenTitle = sans(21, FontWeight.SemiBold)
    /** Sheet title. */
    val SheetTitle = sans(17, FontWeight.SemiBold)
    /** Status pill title ("Connected"). */
    val StatusTitle = sans(16, FontWeight.SemiBold)

    // ── Sans: body & controls ──────────────────────────────────────────────────
    /** Full-width button label. */
    val Button = sans(15, FontWeight.SemiBold)
    /** Card title — a server name, a notification headline. */
    val CardTitle = sans(15, FontWeight.SemiBold)
    /** Half-width / inline button label. */
    val ButtonSmall = sans(14, FontWeight.SemiBold)
    /** Settings and checklist row label. */
    val Row = sans(14, FontWeight.Normal)
    /** Body copy under a headline. */
    val Body = sans(14, FontWeight.Normal, lineHeight = 21)
    /** List item text, inline notices. */
    val Item = sans(13, FontWeight.Normal, lineHeight = 17)
    /** Sheet key, detail label. */
    val Label = sans(13, FontWeight.Normal)
    /** Chip and small action label. */
    val Chip = sans(12, FontWeight.Medium)
    /** Pill-button label sitting inside a coloured notice ("Fix", "Allow"). */
    val ChipStrong = sans(12, FontWeight.SemiBold)
    /** Supporting caption. */
    val Caption = sans(12, FontWeight.Normal, lineHeight = 18)
    /** Smallest supporting line. */
    val Hint = sans(11, FontWeight.Normal, lineHeight = 16)
    /** Notification action ("Pause", "Fix", "Resume"). */
    val Action = sans(13, FontWeight.SemiBold)
    /** Notification source line. */
    val NotifSource = sans(11, FontWeight.Medium)
    /** Tab bar label. */
    val Tab = sans(10, FontWeight.Medium)

    // ── Sans: glyphs ───────────────────────────────────────────────────────────
    // Drawn as text, not icons, so they sit on the same ramp as everything else.
    // All Regular weight: a semibold "+" or "‹" reads as a button, and these are
    // affordances inside something else.
    /** Inline "+" in the dashed add-server slot. */
    val Glyph = sans(15, FontWeight.Normal)
    /** "+" inside the circular button on Servers. */
    val GlyphMedium = sans(17, FontWeight.Normal)
    /** Back "‹". */
    val GlyphLarge = sans(20, FontWeight.Normal)
    /** The success ✓ on the connection test. */
    val GlyphHero = sans(34, FontWeight.Normal)

    // ── Mono: values ───────────────────────────────────────────────────────────
    /** The big number on a metric tile ("18 ms", "1.24 GB"). */
    val Metric = mono(25, FontWeight.SemiBold)
    /** Unit suffix riding alongside [Metric]. */
    val MetricUnit = mono(14, FontWeight.SemiBold)
    /** Device-name text field. */
    val InputLarge = mono(18, FontWeight.SemiBold)
    /** Pairing-code cell. */
    val InputCode = mono(20, FontWeight.SemiBold)
    /** Server card stat ("2", "862 MB"). */
    val Stat = mono(16, FontWeight.SemiBold)
    /** Chart total in a card header ("18.4 GB"). */
    val Total = mono(15, FontWeight.SemiBold)
    /** Text-field value — one step up from a detail row's value. */
    val Field = mono(14, FontWeight.Medium)
    /** Status-bar clock. */
    val StatusClock = mono(13, FontWeight.Medium)
    /** Right-hand value in a detail row; uptime on the status pill. */
    val Value = mono(13, FontWeight.Medium)
    /** Inline mono in a sentence. */
    val ValueSmall = mono(12, FontWeight.Medium)
    /** Section eyebrow — "TUNNEL", "RECENT", "USAGE · 7 DAYS". */
    val SectionLabel = mono(10, FontWeight.Medium, letterSpacing = 0.12f)
    /** Status badge on a server row — ALLOWED / PAUSED / REVOKED. No tracking. */
    val Badge = mono(10, FontWeight.Medium)
    /** Smallest eyebrow — the STREAMS/TODAY/ERRORS row on a server card. */
    val MicroLabel = mono(9, FontWeight.Medium, letterSpacing = 0.10f)
    /** HTTP method badge. */
    val Method = mono(9, FontWeight.Medium)
    /** Timestamp, URL, request metadata. */
    val Meta = mono(11, FontWeight.Normal)
    /** Request sub-line — the smallest mono in the design. */
    val MetaSmall = mono(10, FontWeight.Normal)
    /** Readiness marker glyph (16dp marker). No tracking: it is one character. */
    val Marker = mono(9, FontWeight.SemiBold)
    /** Readiness marker glyph (18dp marker, onboarding). */
    val MarkerLarge = mono(10, FontWeight.SemiBold)
    /** Status code, live counter. */
    val MetaStrong = mono(11, FontWeight.Medium)
    /** Request URL. */
    val Url = mono(12, FontWeight.Normal, lineHeight = 14)
    /** Code / payload block inside a sheet. */
    val Code = mono(11, FontWeight.Normal, lineHeight = 18)
    /** Chart axis letter. */
    val Axis = mono(9, FontWeight.Normal)
}
