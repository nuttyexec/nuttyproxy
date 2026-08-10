package dev.nutty.proxy

import android.app.Activity
import android.app.Application
import android.content.Context
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
import dev.nutty.proxy.ui.theme.NuttyColor
import kotlinx.coroutines.flow.StateFlow
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class ProxyViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    val snapshot: StateFlow<AgentSnapshot> = AgentRuntimeState.snapshot

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
    }

    fun enroll(rawPayload: String) = ProxyAgentService.enroll(app, rawPayload)
    fun pause() = ProxyAgentService.command(app, ProxyAgentService.ACTION_PAUSE)
    fun resume() = ProxyAgentService.command(app, ProxyAgentService.ACTION_RESUME)
    fun retry() = ProxyAgentService.command(app, ProxyAgentService.ACTION_RETRY)
    fun pauseServer(profileId: String) = ProxyAgentService.profileCommand(app, ProxyAgentService.ACTION_PAUSE_PROFILE, profileId)
    fun resumeServer(profileId: String) = ProxyAgentService.profileCommand(app, ProxyAgentService.ACTION_RESUME_PROFILE, profileId)
    fun revokeServer(profileId: String) = ProxyAgentService.profileCommand(app, ProxyAgentService.ACTION_REVOKE_PROFILE, profileId)

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
        )
    }

    fun logs(snapshot: AgentSnapshot): List<LogEntry> = snapshot.events.map { event ->
        LogEntry(colorFor(event.kind), event.detail, time(event))
    }

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
        val battery = AlwaysOnSetup.batteryUnrestricted(context)
        val backgroundData = AlwaysOnSetup.backgroundDataRestricted(context)
        return listOf(
            ReadinessItem("Notifications", if (notifications) ReadinessState.Done else ReadinessState.Warning, if (notifications) null else "Allow"),
            ReadinessItem("Battery unrestricted", if (battery) ReadinessState.Done else ReadinessState.Warning, if (battery) null else "Allow"),
            ReadinessItem("Background data", if (backgroundData) ReadinessState.Warning else ReadinessState.Done, if (backgroundData) "Open" else null),
            // Android delivers BOOT_COMPLETED without a user-facing setting on
            // stock devices. OEM launch managers cannot be read or enabled by
            // a normal app, so sending users to the generic app-info page is
            // misleading and does not improve boot behaviour.
            ReadinessItem("Restart after boot", ReadinessState.Done),
        )
    }

    fun traffic(snapshot: AgentSnapshot): String = formatBytes(snapshot.bytesUp + snapshot.bytesDown)

    fun requestNotifications(activity: Activity) = AlwaysOnSetup.requestNotifications(activity)
    fun requestBatteryUnrestricted(activity: Activity) = AlwaysOnSetup.requestBatteryUnrestricted(activity)
    fun openDataSettings(activity: Activity) = AlwaysOnSetup.openDataSettings(activity)
    fun openAppSettings(activity: Activity) = AlwaysOnSetup.openAppSettings(activity)

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
