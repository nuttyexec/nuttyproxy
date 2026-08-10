package dev.nutty.proxy.ui.model

import dev.nutty.proxy.ui.theme.NuttyColor

/**
 * Every string in the design, in one place.
 *
 * This is the seam where the real agent plugs in: swap [DemoData] for a
 * ViewModel exposing the same shapes and no composable has to change. Keeping
 * the sample copy verbatim from the design also means a rendered screen can be
 * diffed against the source frames pixel for pixel.
 */
object DemoData {

    const val DEVICE_NAME = "P1"
    const val AGENT_LABEL = "nutty-agent"
    const val NETWORK = "5G"
    const val CLOCK = "9:41"

    // ── Home ───────────────────────────────────────────────────────────────────

    /** Inline notice under the status pill. Only three states carry one. */
    fun notice(state: HomeState): Notice? = when (state) {
        HomeState.Reconnecting -> Notice(
            text = "Mobile network changed · retry in 4s",
            action = "why?",
            tone = Notice.Tone.Neutral,
        )
        HomeState.Attention -> Notice(
            text = "Battery optimization is on",
            action = "Fix",
            tone = Notice.Tone.Amber,
        )
        HomeState.Disconnected -> Notice(
            text = "Mobile data is off · retry stopped after 5 tries",
            action = "why?",
            tone = Notice.Tone.Red,
        )
        else -> null
    }

    data class Notice(val text: String, val action: String, val tone: Tone) {
        enum class Tone { Neutral, Amber, Red }
    }

    /** Primary action under the notice — the single most useful verb per state. */
    fun primaryAction(state: HomeState): String = when (state) {
        HomeState.Connected, HomeState.Attention -> "Pause proxy"
        HomeState.Reconnecting -> "Cancel reconnect"
        HomeState.Paused -> "Start proxy"
        HomeState.Disconnected -> "Retry now"
    }

    /** Latency on the TUNNEL tile — absent unless traffic is actually flowing. */
    fun tunnelLatency(state: HomeState): String? = when (state) {
        HomeState.Connected -> "18"
        HomeState.Attention -> "24"
        else -> null
    }

    /** State-specific first line of RECENT, prepended to [recentLog]. */
    fun recentHead(state: HomeState): LogEntry? = when (state) {
        HomeState.Attention -> LogEntry(NuttyColor.Amber, "Battery optimization re-enabled", "09:12")
        HomeState.Paused -> LogEntry(NuttyColor.Grey, "Proxy paused by you", "09:44")
        HomeState.Disconnected -> LogEntry(NuttyColor.Red, "Retry stopped · 5 attempts failed", "09:46")
        else -> null
    }

    val recentLog = listOf(
        LogEntry(NuttyColor.Green, "Tunnel reconnected", "09:41"),
        LogEntry(NuttyColor.TextDim, "Network changed Wi-Fi → 5G", "09:38"),
        LogEntry(NuttyColor.TextDim, "3 streams started · sample-prod", "09:31"),
    )

    /** Full day log on the Activity screen. */
    val fullLog = listOf(
        LogEntry(NuttyColor.Green, "Tunnel reconnected", "09:41"),
        LogEntry(NuttyColor.TextDim, "Network changed Wi-Fi → 5G", "09:38"),
        LogEntry(NuttyColor.TextDim, "3 streams started · sample-prod", "09:31"),
        LogEntry(NuttyColor.Amber, "Battery optimization re-enabled", "09:12"),
        LogEntry(NuttyColor.Red, "Auth failed · runner-eu", "08:52"),
        LogEntry(NuttyColor.Green, "Agent started after boot", "07:04"),
    )

    // ── Servers ────────────────────────────────────────────────────────────────

    val servers = listOf(
        ServerInfo(
            name = "sample-prod", state = ServerState.Allowed, lastSeen = "2m ago",
            streams = "2", today = "862 MB", errors = "0",
        ),
        ServerInfo(
            name = "sample-dev", state = ServerState.Paused, lastSeen = "yesterday",
            streams = "0", today = "0 B", errors = "0",
        ),
        ServerInfo(
            name = "runner-eu", state = ServerState.Allowed, lastSeen = "18m ago",
            streams = "0", today = "402 MB", errors = "1",
            errorNote = "Auth failed · key rotated", errorAt = "08:52",
        ),
        ServerInfo(
            name = "ci-old", state = ServerState.Revoked, lastSeen = "Aug 2",
            streams = "—", today = "—", errors = "—",
        ),
    )

    // ── Server detail ──────────────────────────────────────────────────────────

    /** Seven-day usage, newest last. The final bar is today and is emphasised. */
    val serverWeek = listOf(0.38f, 0.62f, 0.29f, 0.71f, 0.46f, 0.84f, 0.55f)
    const val SERVER_WEEK_TOTAL = "4.8 GB"

    val serverDetailRows = listOf(
        SheetRow("Last connected", "09:39:04"),
        SheetRow("Active streams", "2"),
        SheetRow("Allowed ports", "443 · 80"),
    )

    val serverHistory = listOf(
        LogEntry(NuttyColor.TextDim, "Session opened", "09:39"),
        LogEntry(NuttyColor.TextDim, "Session closed · 41m", "08:58"),
        LogEntry(NuttyColor.TextDim, "Session opened", "08:17"),
    )

    // ── Activity ───────────────────────────────────────────────────────────────

    val activityFilters = listOf("All", "Requests", "Connection", "Network", "Auth")
    val activityWeek = listOf(0.44f, 0.70f, 0.33f, 0.88f, 0.52f, 0.61f, 0.40f)
    const val ACTIVITY_WEEK_TOTAL = "18.4 GB"

    /** Per-server share of the week. Rank is carried by value, not by hue. */
    val usageShare = listOf(
        Triple("sample-prod", 0.62f, "11.4 GB"),
        Triple("runner-eu", 0.31f, "5.7 GB"),
        Triple("sample-dev", 0.07f, "1.3 GB"),
    )

    val requests = listOf(
        RequestInfo(
            method = "POST",
            url = "api.openai.com/v1/chat/completions",
            meta = "sample-prod · 18 KB ↑ 4.2 MB ↓ · 1.8s",
            status = "200", statusColor = NuttyColor.Green, sheet = SheetKey.Request1,
        ),
        RequestInfo(
            method = "GET",
            url = "cdn.example.net/media/sample.m4a",
            meta = "sample-prod · 88 MB ↓ · streaming",
            status = "206", statusColor = NuttyColor.Green, sheet = SheetKey.Request2,
        ),
        RequestInfo(
            method = "GET",
            url = "registry.npmjs.org/-/package/vite",
            meta = "runner-eu · 1.1 MB ↓ · 240ms",
            status = "429", statusColor = NuttyColor.Amber, sheet = SheetKey.Request3,
        ),
    )

    // ── Settings ───────────────────────────────────────────────────────────────

    val readiness = listOf(
        ReadinessItem("Notifications allowed", ReadinessState.Done),
        ReadinessItem("Battery: unrestricted", ReadinessState.Done),
        ReadinessItem("Background data restricted", ReadinessState.Warning),
        ReadinessItem("Auto-start after boot", ReadinessState.Done),
    )

    /** Onboarding version of the same checklist — same facts, actionable. */
    val readinessOnboarding = listOf(
        ReadinessItem("Notifications", ReadinessState.Done, action = "DONE"),
        ReadinessItem("Battery unrestricted", ReadinessState.Done, action = "DONE"),
        ReadinessItem("Background data", ReadinessState.Warning, action = "Allow"),
        ReadinessItem("Auto-start · Samsung", ReadinessState.Pending, action = "Open", marker = "3"),
    )

    // ── Notification shade ─────────────────────────────────────────────────────

    val notifications = listOf(
        NotificationPreview(
            tile = NuttyColor.Green, source = "Nutty Proxy · now",
            title = "Connected · 2 connections", body = "sample-prod · 1.24 GB today",
            primaryAction = "Pause", primaryColor = NuttyColor.TextTertiary,
            secondaryAction = "Details",
        ),
        NotificationPreview(
            tile = NuttyColor.Amber, source = "Nutty Proxy · 4m",
            title = "Serving, but at risk", body = "Battery optimization was re-enabled",
            primaryAction = "Fix", primaryColor = NuttyColor.Amber,
            secondaryAction = "Pause",
        ),
        NotificationPreview(
            tile = NuttyColor.Amber, source = "Nutty Proxy · now",
            title = "Reconnecting · retry in 4s", body = "Mobile network changed",
            primaryAction = "Cancel", primaryColor = NuttyColor.TextTertiary,
            secondaryAction = "Details",
        ),
        NotificationPreview(
            tile = NuttyColor.Grey, source = "Nutty Proxy · 12m",
            title = "Paused by you", body = "Servers cannot reach this phone",
            primaryAction = "Resume", primaryColor = NuttyColor.Green,
            secondaryAction = "Details",
        ),
    )

    // ── Detail sheets ──────────────────────────────────────────────────────────

    fun sheet(key: SheetKey): SheetSpec = when (key) {
        SheetKey.Status -> SheetSpec(
            title = "Connection",
            rows = listOf(
                SheetRow("Last handshake", "09:41:06"),
                SheetRow("Uptime", "00:42:18"),
                SheetRow("Gateway", "gw-sel-01"),
                SheetRow("Round-trip", "18 ms"),
                SheetRow("Reconnects today", "2"),
            ),
            note = "Handshake renews every 20 s. Request URLs and auth headers are never stored.",
        )

        SheetKey.Network -> SheetSpec(
            title = "Network",
            rows = listOf(
                SheetRow("Type", NETWORK),
                SheetRow("Carrier", "SKT"),
                SheetRow("Public IP", "100.82.4.19"),
                SheetRow("Battery", "84% · charging"),
                SheetRow("Data saver", "Off"),
            ),
            note = "Battery optimization must stay disabled for the agent to survive doze.",
        )

        SheetKey.Servers -> SheetSpec(
            title = "Server usage",
            rows = listOf(
                SheetRow("sample-prod", "2 streams"),
                SheetRow("sample-dev", "paused"),
                SheetRow("runner-eu", "0 streams"),
                SheetRow("Allowed servers", "3"),
            ),
            note = "Servers are added by QR or one-time pairing code only — never by raw IP.",
        )

        SheetKey.Usage -> SheetSpec(
            title = "Today",
            rows = listOf(
                SheetRow("Upload", "312 MB"),
                SheetRow("Download", "952 MB"),
                SheetRow("Mobile only", "1.02 GB"),
                SheetRow("This month", "18.4 GB"),
                SheetRow("Active streams", "3"),
            ),
            note = "Warning threshold is 8 GB per month.",
        )

        SheetKey.Certificate -> SheetSpec(
            title = "Certificate",
            rows = listOf(
                SheetRow("Fingerprint", "9F:2C:AE:41:D0:8B"),
                SheetRow("Algorithm", "Ed25519"),
                SheetRow("Issued", "2026-07-02"),
                SheetRow("Expires", "2027-07-02"),
            ),
            note = "Compare this fingerprint on the server before allowing traffic.",
        )

        SheetKey.Attention -> SheetSpec(
            title = "1 issue",
            rows = listOf(
                SheetRow("Battery optimization", "Re-enabled"),
                SheetRow("Effect", "Tunnel drops when idle"),
                SheetRow("Since", "09:12"),
                SheetRow("Reconnects since", "2"),
            ),
            note = "Fix opens Android battery settings for Nutty Proxy.",
        )

        SheetKey.Disconnected -> SheetSpec(
            title = "Why it stopped",
            rows = listOf(
                SheetRow("Last error", "no route to host"),
                SheetRow("Attempts", "5 · backoff 60 s"),
                SheetRow("Last success", "08:52"),
                SheetRow("Mobile data", "Off"),
                SheetRow("Wi-Fi", "Not connected"),
            ),
            note = "The agent stops retrying after 5 failures and waits for a network " +
                "change or a manual retry.",
        )

        SheetKey.Pairing -> SheetSpec(
            title = "Pairing",
            rows = listOf(
                SheetRow("Transport", "TLS 1.3"),
                SheetRow("Code lifetime", "10 min"),
                SheetRow("Code use", "Single"),
                SheetRow("Key", "Generated on device"),
            ),
            note = "Run nutty pair on the server: it prints the address and a one-time " +
                "code. The private key never leaves the phone.",
        )

        SheetKey.Naming -> SheetSpec(
            title = "Server name",
            rows = listOf(
                SheetRow("Sent by server", "sample-prod"),
                SheetRow("Source", "NUTTY_SERVER_NAME"),
                SheetRow("Fallback", "host:port"),
                SheetRow("Local label", "not set"),
            ),
            note = "The server declares its own name at pairing. Rename it here and only " +
                "this phone sees the new label.",
        )

        SheetKey.Capture -> SheetSpec(
            title = "What is recorded",
            rows = listOf(
                SheetRow("Method", "kept"),
                SheetRow("Host + path", "kept"),
                SheetRow("Query string", "redacted"),
                SheetRow("Headers", "auth stripped"),
                SheetRow("Body", "first 512 B"),
                SheetRow("Retention", "7 days"),
            ),
            note = "Bodies are truncated and cookies, tokens and Authorization headers " +
                "are dropped before anything is written to disk.",
        )

        SheetKey.Request1 -> SheetSpec(
            title = "POST /v1/chat/completions",
            rows = listOf(
                SheetRow("Host", "api.openai.com"),
                SheetRow("Status", "200"),
                SheetRow("Server", "sample-prod"),
                SheetRow("Started", "09:41:52"),
                SheetRow("Duration", "1.84 s"),
                SheetRow("Sent / received", "18 KB / 4.2 MB"),
                SheetRow("Content-type", "application/json"),
            ),
            code = "{ \"model\": \"gpt-5.1\", \"stream\": true,\n" +
                "  \"messages\": [ { \"role\": \"user\", … } ] }\n\n" +
                "… truncated at 512 B · auth header stripped",
            note = "Tap and hold a request to copy it as curl, without credentials.",
        )

        SheetKey.Request2 -> SheetSpec(
            title = "GET /media/ep-4412.m4a",
            rows = listOf(
                SheetRow("Host", "cdn.example.net"),
                SheetRow("Status", "206 partial"),
                SheetRow("Server", "sample-prod"),
                SheetRow("Started", "09:40:07"),
                SheetRow("Duration", "streaming · 2m"),
                SheetRow("Received", "88 MB"),
                SheetRow("Range", "bytes=0-"),
            ),
            code = "no request body",
            note = "Long-lived streams keep counting until the server closes them.",
        )

        SheetKey.Request3 -> SheetSpec(
            title = "GET /-/package/vite",
            rows = listOf(
                SheetRow("Host", "registry.npmjs.org"),
                SheetRow("Status", "429 rate limited"),
                SheetRow("Server", "runner-eu"),
                SheetRow("Started", "09:39:31"),
                SheetRow("Duration", "240 ms"),
                SheetRow("Received", "1.1 MB"),
                SheetRow("Retry-after", "60 s"),
            ),
            code = "no request body",
            note = "Rate limits are the destination talking, not the tunnel. The proxy " +
                "passed the response through untouched.",
        )
    }
}
