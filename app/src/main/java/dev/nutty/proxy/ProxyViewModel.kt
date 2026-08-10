package dev.nutty.proxy

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.compose.ui.graphics.Color
import dev.nutty.proxy.agent.AgentEvent
import dev.nutty.proxy.agent.AgentRuntimeState
import dev.nutty.proxy.agent.AgentSnapshot
import dev.nutty.proxy.agent.AgentStore
import dev.nutty.proxy.agent.AlwaysOnSetup
import dev.nutty.proxy.agent.ConnectionPhase
import dev.nutty.proxy.agent.ProxyAgentService
import dev.nutty.proxy.ui.model.HomeState
import dev.nutty.proxy.ui.model.LogEntry
import dev.nutty.proxy.ui.model.ReadinessItem
import dev.nutty.proxy.ui.model.ReadinessState
import dev.nutty.proxy.ui.model.RequestInfo
import dev.nutty.proxy.ui.model.ServerInfo
import dev.nutty.proxy.ui.model.ServerState
import dev.nutty.proxy.ui.model.SheetKey
import dev.nutty.proxy.ui.model.SheetRow
import dev.nutty.proxy.ui.model.SheetSpec
import dev.nutty.proxy.ui.theme.NuttyColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class NetworkSummary(val label: String, val caption: String)
data class ReleaseUpdate(
    val checking: Boolean = false,
    val available: Boolean = false,
    val version: String? = null,
    val downloadUrl: String? = null,
    val message: String = "Check for updates",
)

class ProxyViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    val snapshot: StateFlow<AgentSnapshot> = AgentRuntimeState.snapshot
    private val mutableUpdate = MutableStateFlow(ReleaseUpdate())
    val update: StateFlow<ReleaseUpdate> = mutableUpdate
    private val updateExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        AgentRuntimeState.load(app)
        if (AgentStore(app).isServingEnabled()) ProxyAgentService.start(app)
    }

    fun homeState(snapshot: AgentSnapshot): HomeState = when (snapshot.primaryPhase) {
        ConnectionPhase.Connected -> HomeState.Connected
        ConnectionPhase.Attention -> HomeState.Attention
        ConnectionPhase.Connecting, ConnectionPhase.Reconnecting -> HomeState.Reconnecting
        ConnectionPhase.Paused -> HomeState.Paused
        ConnectionPhase.Disconnected -> HomeState.Disconnected
    }

    fun saveDeviceName(name: String) {
        AgentStore(app).setDeviceName(name)
        AgentRuntimeState.refresh(app)
        // The name is authenticated in the next tunnel hello. Reconnect now so
        // a rename is visible to every paired server immediately, not only
        // after a later network interruption or reboot.
        if (AgentStore(app).isServingEnabled()) {
            ProxyAgentService.command(app, ProxyAgentService.ACTION_RECONNECT)
        }
    }

    fun enroll(rawPayload: String) = ProxyAgentService.enroll(app, rawPayload)
    fun pause() = ProxyAgentService.command(app, ProxyAgentService.ACTION_PAUSE)
    fun resume() = ProxyAgentService.command(app, ProxyAgentService.ACTION_RESUME)
    fun retry() = ProxyAgentService.command(app, ProxyAgentService.ACTION_RETRY)
    fun pauseServer(profileId: String) = ProxyAgentService.profileCommand(app, ProxyAgentService.ACTION_PAUSE_PROFILE, profileId)
    fun resumeServer(profileId: String) = ProxyAgentService.profileCommand(app, ProxyAgentService.ACTION_RESUME_PROFILE, profileId)
    fun revokeServer(profileId: String) = ProxyAgentService.profileCommand(app, ProxyAgentService.ACTION_REVOKE_PROFILE, profileId)
    fun removeAllPairings() = ProxyAgentService.command(app, ProxyAgentService.ACTION_REMOVE_ALL)

    fun servers(snapshot: AgentSnapshot): List<ServerInfo> = snapshot.profiles.map { profile ->
        val status = snapshot.statuses[profile.id]
        val errors = snapshot.events.count { it.profileId == profile.id && it.kind == AgentEvent.Kind.Error }
        ServerInfo(
            id = profile.id,
            name = profile.serverName,
            state = if (snapshot.servingEnabled && profile.enabled) ServerState.Allowed else ServerState.Paused,
            lastSeen = when (status?.phase) {
                ConnectionPhase.Connected -> "connected"
                ConnectionPhase.Reconnecting, ConnectionPhase.Connecting -> status.detail
                ConnectionPhase.Attention -> "attention needed"
                else -> "not connected"
            },
            streams = (status?.activeStreams ?: 0).toString(),
            today = formatBytes((status?.bytesUp ?: 0) + (status?.bytesDown ?: 0)),
            errors = errors.toString(),
            errorNote = snapshot.events.firstOrNull { it.profileId == profile.id && it.kind == AgentEvent.Kind.Error }?.detail,
            errorAt = snapshot.events.firstOrNull { it.profileId == profile.id && it.kind == AgentEvent.Kind.Error }?.let(::time),
            certificatePin = profile.certificatePin,
        )
    }

    fun logs(snapshot: AgentSnapshot): List<LogEntry> = snapshot.events.map { event ->
        LogEntry(colorFor(event.kind), event.detail, time(event))
    }

    fun logsForServer(snapshot: AgentSnapshot, profileId: String): List<LogEntry> = snapshot.events
        .filter { it.profileId == profileId }
        .map { event -> LogEntry(colorFor(event.kind), event.detail, time(event)) }

    fun requests(snapshot: AgentSnapshot): List<RequestInfo> = snapshot.events
        .filter { it.kind == AgentEvent.Kind.Request }
        .map { event ->
            val parts = event.detail.split(" ", limit = 2)
            val profileName = snapshot.profiles.firstOrNull { it.id == event.profileId }?.serverName ?: "server"
            RequestInfo(
                method = parts.firstOrNull().orEmpty(),
                url = parts.getOrElse(1) { "destination" },
                meta = "$profileName · destination only",
                status = "OPEN",
                statusColor = NuttyColor.Green,
                sheet = SheetKey.Capture,
            )
        }

    fun readiness(context: Context = app): List<ReadinessItem> {
        val notifications = AlwaysOnSetup.notificationsAllowed(context)
        val batteryRestricted = AlwaysOnSetup.backgroundBatteryRestricted(context)
        val backgroundData = AlwaysOnSetup.backgroundDataRestricted(context)
        return listOf(
            ReadinessItem("Notifications", if (notifications) ReadinessState.Done else ReadinessState.Warning, if (notifications) null else "Allow"),
            ReadinessItem("Battery background access", if (batteryRestricted) ReadinessState.Warning else ReadinessState.Done, if (batteryRestricted) "Open" else null),
            ReadinessItem("Background data", if (backgroundData) ReadinessState.Warning else ReadinessState.Done, if (backgroundData) "Open" else null),
            // Android delivers BOOT_COMPLETED without a user-facing setting on
            // stock devices. OEM launch managers cannot be read or enabled by
            // a normal app, so sending users to the generic app-info page is
            // misleading and does not improve boot behaviour.
            ReadinessItem("Restart after boot", ReadinessState.Done),
        )
    }

    fun traffic(snapshot: AgentSnapshot): String = formatBytes(snapshot.bytesUp + snapshot.bytesDown)

    fun networkSummary(context: Context = app): NetworkSummary {
        val capabilities = context.getSystemService(ConnectivityManager::class.java)
            .getNetworkCapabilities(context.getSystemService(ConnectivityManager::class.java).activeNetwork)
            ?: return NetworkSummary("Offline", "No active network")
        val label = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Online"
        }
        return NetworkSummary(label, if (AlwaysOnSetup.backgroundDataRestricted(context)) "Background data restricted" else "Background data allowed")
    }

    fun sheet(snapshot: AgentSnapshot, key: SheetKey, selectedProfileId: String? = null): SheetSpec {
        val selected = snapshot.profiles.firstOrNull { it.id == selectedProfileId }
        val status = selected?.let { snapshot.statuses[it.id] }
            ?: snapshot.statuses.values.firstOrNull()
        val connectionRows = listOfNotNull(
            SheetRow("State", snapshot.primaryPhase.name.lowercase()),
            status?.let { SheetRow("Detail", it.detail.ifBlank { "—" }) },
            SheetRow("Paired servers", snapshot.profiles.size.toString()),
            SheetRow("Active streams", snapshot.activeStreams.toString()),
        )
        return when (key) {
            SheetKey.Status, SheetKey.Attention, SheetKey.Disconnected -> SheetSpec(
                title = "Connection",
                rows = connectionRows,
                note = "The tunnel uses a certificate-pinned WSS connection. Open Activity for the local event log.",
            )
            SheetKey.Network -> {
                val network = networkSummary()
                SheetSpec("Network", listOf(
                    SheetRow("Active network", network.label),
                    SheetRow("Background data", network.caption),
                ), note = "Network transport is managed by Android. The agent reconnects when a usable network returns.")
            }
            SheetKey.Servers -> SheetSpec(
                "Paired servers",
                snapshot.profiles.map { profile ->
                    val state = snapshot.statuses[profile.id]?.detail ?: if (profile.enrollmentPending) "Pairing incomplete" else "Not connected"
                    SheetRow(profile.serverName, state)
                }.ifEmpty { listOf(SheetRow("Servers", "None paired")) },
                note = "Each server is added by a one-time QR or pairing payload and has a separate phone key.",
            )
            SheetKey.Usage -> SheetSpec(
                "Current app session",
                listOf(
                    SheetRow("Phone → server", formatBytes(snapshot.bytesUp)),
                    SheetRow("Server → phone", formatBytes(snapshot.bytesDown)),
                    SheetRow("Active streams", snapshot.activeStreams.toString()),
                ),
                note = "Usage resets when the Android proxy service restarts; no request bodies, headers, or URLs are stored.",
            )
            SheetKey.Certificate -> SheetSpec(
                "Pinned server certificate",
                listOf(
                    SheetRow("Server", selected?.serverName ?: "—"),
                    SheetRow("SPKI pin", selected?.certificatePin ?: "—"),
                ),
                note = "The app rejects a server certificate that does not match this pin.",
            )
            SheetKey.Pairing -> SheetSpec(
                "Pairing",
                listOf(
                    SheetRow("Transport", "Certificate-pinned WSS"),
                    SheetRow("Code", "One-time enrollment token"),
                    SheetRow("Device key", "Generated in Android Keystore"),
                ),
                note = "Run `nuttyproxy pair` on the server, then scan its QR or paste its payload here.",
            )
            SheetKey.Naming -> SheetSpec(
                "Server name",
                listOf(SheetRow("Server", selected?.serverName ?: "—")),
                note = "The server supplies this name during pairing. The phone name can be changed in Settings.",
            )
            SheetKey.Capture, SheetKey.Request1, SheetKey.Request2, SheetKey.Request3 -> SheetSpec(
                "Activity privacy",
                listOf(
                    SheetRow("Recorded", "Method and destination host:port"),
                    SheetRow("Not recorded", "Request URL, headers, cookies, body"),
                    SheetRow("Retention", "Last 100 local events"),
                ),
                note = "Activity is a local operational log, not a traffic capture.",
            )
        }
    }

    fun requestNotifications(activity: Activity) = AlwaysOnSetup.requestNotifications(activity)
    fun requestBatteryUnrestricted(activity: Activity) = AlwaysOnSetup.requestBatteryUnrestricted(activity)
    fun openDataSettings(activity: Activity) = AlwaysOnSetup.openDataSettings(activity)
    fun openAppSettings(activity: Activity) = AlwaysOnSetup.openAppSettings(activity)

    /** Check the public GitHub release feed. APK installation is still confirmed
     * by Android; sideloaded apps must never silently replace themselves. */
    fun checkForUpdate() {
        if (mutableUpdate.value.checking) return
        mutableUpdate.value = mutableUpdate.value.copy(checking = true, message = "Checking…")
        updateExecutor.execute {
            val result: Result<Pair<String, String>> = runCatching {
                val connection = (URL("https://api.github.com/repos/nuttyexec/nuttyproxy/releases/latest").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "Nutty-Proxy-Android")
                }
                try {
                    require(connection.responseCode == HttpURLConnection.HTTP_OK) { "Release check failed (${connection.responseCode})" }
                    val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    val version = release.getString("tag_name")
                    val assets = release.getJSONArray("assets")
                    val downloadUrl = (0 until assets.length())
                        .map { assets.getJSONObject(it) }
                        .firstOrNull { it.optString("name") == "Nutty-Proxy.apk" }
                        ?.getString("browser_download_url")
                        ?: error("Release APK is missing")
                    version to downloadUrl
                } finally {
                    connection.disconnect()
                }
            }
            mainHandler.post {
                result.onSuccess { (version, url) ->
                    val currentVersion = currentVersion()
                    val available = isNewerVersion(version, currentVersion)
                    mutableUpdate.value = ReleaseUpdate(
                        available = available,
                        version = version,
                        downloadUrl = if (available) url else null,
                        message = if (available) "$version available" else "Up to date · $currentVersion",
                    )
                }.onFailure { error ->
                    mutableUpdate.value = ReleaseUpdate(message = error.message ?: "Could not check for updates")
                }
            }
        }
    }

    fun downloadUpdate(activity: Activity, update: ReleaseUpdate) {
        val url = update.downloadUrl ?: return
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun copyErrorLog(context: Context, snapshot: AgentSnapshot) {
        val text = diagnosticReport(snapshot)
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Nutty Proxy diagnostic log", text))
    }

    fun shareReport(activity: Activity, snapshot: AgentSnapshot) {
        activity.startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, diagnosticReport(snapshot)),
            "Share diagnostic report",
        ))
    }

    private fun diagnosticReport(snapshot: AgentSnapshot): String = buildString {
        appendLine("Nutty Proxy diagnostic report")
        appendLine("Generated: ${DateFormat.getDateTimeInstance().format(Date())}")
        appendLine("Proxy state: ${snapshot.primaryPhase}")
        appendLine("Paired servers: ${snapshot.profiles.size}")
        snapshot.events.take(100).forEach { event ->
            appendLine("${time(event)} ${event.kind}: ${event.detail}")
        }
    }

    override fun onCleared() {
        updateExecutor.shutdownNow()
        super.onCleared()
    }

    private fun isNewerVersion(candidate: String, current: String): Boolean {
        val candidateParts = candidate.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val currentParts = current.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val max = maxOf(candidateParts.size, currentParts.size)
        for (index in 0 until max) {
            val difference = (candidateParts.getOrElse(index) { 0 }).compareTo(currentParts.getOrElse(index) { 0 })
            if (difference != 0) return difference > 0
        }
        return false
    }

    private fun currentVersion(): String = app.packageManager
        .getPackageInfo(app.packageName, 0)
        .versionName
        ?: "0"

    private fun colorFor(kind: AgentEvent.Kind): Color = when (kind) {
        AgentEvent.Kind.Connection, AgentEvent.Kind.Request -> NuttyColor.Green
        AgentEvent.Kind.Warning -> NuttyColor.Amber
        AgentEvent.Kind.Error -> NuttyColor.Red
    }

    private fun time(event: AgentEvent): String = time(event.at)
    private fun time(at: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(at))

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(Locale.US, bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(Locale.US, bytes / (1024.0 * 1024.0))
        else -> "%.2f GB".format(Locale.US, bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
