package dev.nutty.proxy.agent

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import dev.nutty.proxy.notification.ProxyNotifications
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the long-lived foreground tunnel(s). UI process death must not stop this
 * service; user pause is the only normal way to stop serving.
 */
class ProxyAgentService : Service(), AgentConnection.Listener {
    private val store by lazy { AgentStore(this) }
    private val identity = AgentIdentity()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val connections = ConcurrentHashMap<String, AgentConnection>()
    private val retryAttempts = ConcurrentHashMap<String, Int>()
    // Enrollment is intentionally not persisted. It survives in-memory retries
    // until the first successful challenge, then the long-lived device key takes over.
    private val pendingEnrollmentTokens = ConcurrentHashMap<String, String>()
    private val pendingEnrollmentScopes = ConcurrentHashMap<String, String>()
    private var stopping = false
    private lateinit var connectivity: ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (!stopping && store.isServingEnabled()) ensureConnections()
        }
    }

    override fun onCreate() {
        super.onCreate()
        connectivity = getSystemService(ConnectivityManager::class.java)
        connectivity.registerDefaultNetworkCallback(networkCallback)
        ProxyNotifications.ensureChannel(this)
        AgentRuntimeState.load(this)
        showForeground(ConnectionPhase.Reconnecting, "Starting secure proxy agent")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE, ACTION_CANCEL_RETRY -> pauseServing()
            ACTION_RESUME, ACTION_RETRY -> resumeServing()
            ACTION_ENROLL -> intent.getStringExtra(EXTRA_PAIRING)?.let(::enroll)
            ACTION_PAUSE_PROFILE -> intent.getStringExtra(EXTRA_PROFILE_ID)?.let(::pauseProfile)
            ACTION_RESUME_PROFILE -> intent.getStringExtra(EXTRA_PROFILE_ID)?.let(::resumeProfile)
            ACTION_REVOKE_PROFILE -> intent.getStringExtra(EXTRA_PROFILE_ID)?.let(::revokeProfile)
            else -> if (store.isServingEnabled()) ensureConnections()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopping = true
        connectivity.unregisterNetworkCallback(networkCallback)
        connections.values.forEach { it.stop() }
        connections.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onReady(profile: AgentProfile) {
        store.upsert(profile)
        store.setServingEnabled(true)
        retryAttempts.remove(profile.id)
        pendingEnrollmentTokens.remove(profile.id)
        pendingEnrollmentScopes.remove(identityScope(profile))
        AgentRuntimeState.refresh(this)
        record(profile, AgentEvent.Kind.Connection, "Secure tunnel connected")
        showForeground(ConnectionPhase.Connected, "Connected · ${profile.serverName}")
    }

    override fun onStatus(profile: AgentProfile, phase: ConnectionPhase, detail: String) {
        val previous = AgentRuntimeState.snapshot.value.statuses[profile.id]
        AgentRuntimeState.updateStatus(
            ConnectionStatus(
                profileId = profile.id,
                phase = phase,
                detail = detail,
                activeStreams = previous?.activeStreams ?: 0,
                bytesUp = previous?.bytesUp ?: 0,
                bytesDown = previous?.bytesDown ?: 0,
            ),
        )
        if (previous?.phase != phase && phase != ConnectionPhase.Connected) {
            record(profile, if (phase == ConnectionPhase.Attention) AgentEvent.Kind.Warning else AgentEvent.Kind.Connection, detail)
        }
        showForeground(AgentRuntimeState.snapshot.value.primaryPhase, detail)
    }

    override fun onStream(profile: AgentProfile, active: Int, sent: Long, received: Long) {
        val previous = AgentRuntimeState.snapshot.value.statuses[profile.id]
        AgentRuntimeState.updateStatus(
            ConnectionStatus(
                profileId = profile.id,
                phase = previous?.phase ?: ConnectionPhase.Connected,
                detail = previous?.detail.orEmpty(),
                activeStreams = active,
                bytesUp = sent,
                bytesDown = received,
            ),
        )
        showForeground(AgentRuntimeState.snapshot.value.primaryPhase, "${AgentRuntimeState.snapshot.value.activeStreams} connections")
    }

    override fun onRequest(profile: AgentProfile, host: String, port: Int, method: String) {
        // Only destination + method are logged. URLs, headers and proxy payloads
        // never enter app storage.
        record(profile, AgentEvent.Kind.Request, "$method $host:$port")
    }

    override fun onDisconnected(profile: AgentProfile, reason: String) {
        connections.remove(profile.id)?.stop()
        if (stopping || !store.isServingEnabled() || !store.profiles().firstOrNull { it.id == profile.id }?.enabled.orFalse()) return
        val attempt = (retryAttempts[profile.id] ?: 0) + 1
        retryAttempts[profile.id] = attempt
        val delay = minOf(60_000L, 1_000L shl minOf(attempt - 1, 6))
        onStatus(profile, ConnectionPhase.Reconnecting, "Retrying in ${delay / 1000}s · $reason")
        record(profile, AgentEvent.Kind.Error, "Tunnel disconnected · $reason")
        mainHandler.postDelayed({
            if (!stopping && store.isServingEnabled()) startProfile(profile, pendingEnrollmentTokens[profile.id])
        }, delay)
    }

    private fun enroll(rawPayload: String) {
        PairingParser.parse(rawPayload).onSuccess { payload ->
            val profile = PairingParser.profile(payload)
            if (store.profiles().any { it.gatewayUrl == profile.gatewayUrl && it.agentId == profile.agentId }) {
                showForeground(ConnectionPhase.Attention, "This server is already paired")
                return@onSuccess
            }
            if (pendingEnrollmentScopes.putIfAbsent(identityScope(profile), profile.id) != null) {
                showForeground(ConnectionPhase.Attention, "Pairing is already in progress")
                return@onSuccess
            }
            store.setServingEnabled(true)
            AgentRuntimeState.refresh(this)
            pendingEnrollmentTokens[profile.id] = payload.enrollmentToken
            startProfile(profile, payload.enrollmentToken)
        }.onFailure {
            showForeground(ConnectionPhase.Attention, "Invalid pairing code")
        }
    }

    private fun resumeServing() {
        store.setServingEnabled(true)
        AgentRuntimeState.refresh(this)
        ensureConnections()
    }

    private fun pauseServing() {
        store.setServingEnabled(false)
        connections.values.forEach { it.stop() }
        connections.clear()
        AgentRuntimeState.refresh(this)
        AgentRuntimeState.record(this, AgentEvent(System.currentTimeMillis(), kind = AgentEvent.Kind.Connection, detail = "Proxy paused by you"))
        showForeground(ConnectionPhase.Paused, "Servers cannot reach this phone")
    }

    private fun ensureConnections() {
        store.profiles().filter { it.enabled }.forEach { startProfile(it) }
        if (store.profiles().isEmpty()) showForeground(ConnectionPhase.Attention, "Add a server to start serving")
    }

    private fun startProfile(profile: AgentProfile, enrollmentToken: String? = null) {
        if (connections.containsKey(profile.id)) return
        val connection = AgentConnection(profile, enrollmentToken, store.deviceName(), identity, this)
        connections[profile.id] = connection
        connection.connect()
    }

    private fun pauseProfile(profileId: String) {
        store.setProfileEnabled(profileId, false)
        pendingEnrollmentTokens.remove(profileId)
        connections.remove(profileId)?.stop()
        pendingEnrollmentTokens.remove(profileId)
        AgentRuntimeState.removeStatus(profileId)
        AgentRuntimeState.refresh(this)
    }

    private fun resumeProfile(profileId: String) {
        store.setProfileEnabled(profileId, true)
        AgentRuntimeState.refresh(this)
        store.profiles().firstOrNull { it.id == profileId }?.let(::startProfile)
    }

    private fun revokeProfile(profileId: String) {
        val profile = store.profiles().firstOrNull { it.id == profileId }
        connections.remove(profileId)?.stop()
        store.remove(profileId)
        profile?.let { identity.remove(identityScope(it)) }
        AgentRuntimeState.removeStatus(profileId)
        AgentRuntimeState.refresh(this)
        if (store.profiles().isEmpty()) pauseServing()
    }

    private fun showForeground(phase: ConnectionPhase, detail: String) {
        try {
            val snapshot = AgentRuntimeState.snapshot.value
            val notificationState = when (phase) {
                ConnectionPhase.Connected -> ProxyNotifications.State.Connected
                ConnectionPhase.Attention, ConnectionPhase.Disconnected -> ProxyNotifications.State.Attention
                ConnectionPhase.Paused -> ProxyNotifications.State.Paused
                ConnectionPhase.Connecting, ConnectionPhase.Reconnecting -> ProxyNotifications.State.Reconnecting
            }
            val notification = ProxyNotifications.build(
                context = this,
                state = notificationState,
                connections = snapshot.activeStreams,
                detail = detail,
            )
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(ProxyNotifications.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(ProxyNotifications.NOTIFICATION_ID, notification)
            }
        } catch (error: RuntimeException) {
            // A device-specific foreground-service policy must never take down
            // the onboarding activity. Keep the reason in the local log and
            // stop this service cleanly instead of crashing the process.
            AgentRuntimeState.record(
                this,
                AgentEvent(System.currentTimeMillis(), kind = AgentEvent.Kind.Error, detail = "Could not start persistent proxy · ${error.javaClass.simpleName}"),
            )
            stopSelf()
        }
    }

    private fun record(profile: AgentProfile, kind: AgentEvent.Kind, detail: String) {
        AgentRuntimeState.record(this, AgentEvent(System.currentTimeMillis(), profile.id, kind, detail))
    }

    private fun identityScope(profile: AgentProfile) = "${profile.gatewayUrl}\n${profile.agentId}"

    companion object {
        const val ACTION_PAUSE = "dev.nutty.proxy.agent.PAUSE"
        const val ACTION_RESUME = "dev.nutty.proxy.agent.RESUME"
        const val ACTION_CANCEL_RETRY = "dev.nutty.proxy.agent.CANCEL_RETRY"
        const val ACTION_RETRY = "dev.nutty.proxy.agent.RETRY"
        const val ACTION_ENROLL = "dev.nutty.proxy.agent.ENROLL"
        const val ACTION_PAUSE_PROFILE = "dev.nutty.proxy.agent.PAUSE_PROFILE"
        const val ACTION_RESUME_PROFILE = "dev.nutty.proxy.agent.RESUME_PROFILE"
        const val ACTION_REVOKE_PROFILE = "dev.nutty.proxy.agent.REVOKE_PROFILE"
        const val EXTRA_PAIRING = "pairing_payload"
        const val EXTRA_PROFILE_ID = "profile_id"

        fun start(context: Context) = ContextCompat.startForegroundService(
            context, Intent(context, ProxyAgentService::class.java),
        )

        fun enroll(context: Context, rawPayload: String) = ContextCompat.startForegroundService(
            context, Intent(context, ProxyAgentService::class.java).apply {
                action = ACTION_ENROLL
                putExtra(EXTRA_PAIRING, rawPayload)
            },
        )

        fun command(context: Context, action: String) = ContextCompat.startForegroundService(
            context,
            Intent(context, ProxyAgentService::class.java).setAction(action),
        )

        fun profileCommand(context: Context, action: String, profileId: String) = ContextCompat.startForegroundService(
            context,
            Intent(context, ProxyAgentService::class.java).setAction(action).putExtra(EXTRA_PROFILE_ID, profileId),
        )
    }
}

private fun Boolean?.orFalse(): Boolean = this ?: false
