package dev.nutty.proxy.ui.model

import androidx.compose.ui.graphics.Color
import dev.nutty.proxy.ui.theme.NuttyColor

/** The eleven frames in the design. */
enum class Screen {
    Home, Servers, ServerDetail, Activity, Settings,
    Pair, Manual, Name, Ready, Test,
    NotificationShade;

    /** Only these five carry the bottom tab bar. */
    val isTabbed: Boolean
        get() = this == Home || this == Servers || this == ServerDetail ||
            this == Activity || this == Settings
}

/**
 * The five home states.
 *
 * The distinction that matters: [Paused] is the *user's* decision and is grey;
 * [Disconnected] is a failure and is red. Never colour a deliberate pause as an
 * error — that is the rule the whole status system hangs on.
 */
enum class HomeState {
    Connected, Reconnecting, Attention, Paused, Disconnected;

    /** True when the tunnel is not carrying traffic, so live metrics show "—". */
    val isLive: Boolean get() = this == Connected || this == Attention

    val status: NuttyColor.Status
        get() = when (this) {
            Connected -> NuttyColor.StatusConnected
            Reconnecting -> NuttyColor.StatusReconnecting
            Attention -> NuttyColor.StatusAttention
            Paused -> NuttyColor.StatusPaused
            Disconnected -> NuttyColor.StatusDisconnected
        }

    val title: String
        get() = when (this) {
            Connected -> "Connected"
            Reconnecting -> "Reconnecting"
            Attention -> "Attention needed"
            Paused -> "Paused by you"
            Disconnected -> "Disconnected"
        }

    /** The right-hand mono readout on the status pill. */
    val meta: String
        get() = when (this) {
            Connected -> "00:42:18"
            Reconnecting -> "retry 4s"
            Attention -> "1 issue"
            Paused -> "not serving"
            Disconnected -> "unusable"
        }
}

/** Which detail sheet is open, if any. Sheets are the *only* place detail lives. */
enum class SheetKey {
    Status, Network, Servers, Usage, Certificate, Attention, Disconnected,
    Pairing, Naming, Capture, Request1, Request2, Request3,
}

/** One key/value line in a detail sheet. Key is Sans, value is Mono. */
data class SheetRow(val key: String, val value: String)

/**
 * A detail sheet: a title, rows of facts, an optional payload block, and a note.
 *
 * The note is doing real work — it is where the design puts the thing a person
 * would otherwise have to ask support about ("bodies are truncated and auth
 * headers dropped"), which is why the surface itself can stay so bare.
 */
data class SheetSpec(
    val title: String,
    val rows: List<SheetRow>,
    val code: String? = null,
    val note: String,
)

/** A row in the activity log. */
data class LogEntry(val color: Color, val text: String, val at: String)

/** A paired server. */
data class ServerInfo(
    val id: String = "",
    val name: String,
    val state: ServerState,
    val lastSeen: String,
    val streams: String,
    val today: String,
    val errors: String,
    val errorNote: String? = null,
    val errorAt: String? = null,
)

enum class ServerState {
    Allowed, Paused, Revoked;

    val label: String get() = name.uppercase()
}

/** One captured request in the activity feed. */
data class RequestInfo(
    val method: String,
    val url: String,
    val meta: String,
    val status: String,
    val statusColor: Color,
    val sheet: SheetKey,
)

/**
 * One row of the always-on readiness checklist.
 *
 * [marker] is the glyph for a [ReadinessState.Pending] row. It comes from the
 * data rather than the row index because it counts *outstanding* grants, not
 * position in the list.
 */
data class ReadinessItem(
    val label: String,
    val state: ReadinessState,
    val action: String? = null,
    val marker: String = "",
)

enum class ReadinessState { Done, Warning, Pending }

/** The four ongoing-notification states. */
data class NotificationPreview(
    val tile: Color,
    val source: String,
    val title: String,
    val body: String,
    val primaryAction: String,
    val primaryColor: Color,
    val secondaryAction: String,
)
