package dev.nutty.proxy.agent

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.Base64
import java.util.UUID

data class PairingPayload(
    val gatewayUrl: String,
    val certificatePin: String,
    val agentId: String,
    val serverName: String,
    val enrollmentToken: String,
)

data class AgentProfile(
    val id: String,
    val agentId: String,
    val serverName: String,
    val gatewayUrl: String,
    val certificatePin: String,
    val enabled: Boolean = true,
)

enum class ConnectionPhase { Connecting, Connected, Attention, Reconnecting, Paused, Disconnected }

data class ConnectionStatus(
    val profileId: String,
    val phase: ConnectionPhase,
    val detail: String = "",
    val activeStreams: Int = 0,
    val bytesUp: Long = 0,
    val bytesDown: Long = 0,
    val lastChangedAt: Long = System.currentTimeMillis(),
)

/** A deliberately small, credential-free audit event retained on this device. */
data class AgentEvent(
    val at: Long,
    val profileId: String? = null,
    val kind: Kind,
    val detail: String,
) {
    enum class Kind { Connection, Request, Warning, Error }
}

data class AgentSnapshot(
    val deviceName: String = "Phone",
    val servingEnabled: Boolean = false,
    val profiles: List<AgentProfile> = emptyList(),
    val statuses: Map<String, ConnectionStatus> = emptyMap(),
    val events: List<AgentEvent> = emptyList(),
) {
    val activeStreams: Int get() = statuses.values.sumOf { it.activeStreams }
    val bytesUp: Long get() = statuses.values.sumOf { it.bytesUp }
    val bytesDown: Long get() = statuses.values.sumOf { it.bytesDown }
    val primaryPhase: ConnectionPhase
        get() = when {
            !servingEnabled -> ConnectionPhase.Paused
            statuses.values.any { it.phase == ConnectionPhase.Connected } -> ConnectionPhase.Connected
            statuses.values.any { it.phase == ConnectionPhase.Attention } -> ConnectionPhase.Attention
            statuses.values.any { it.phase == ConnectionPhase.Connecting || it.phase == ConnectionPhase.Reconnecting } -> ConnectionPhase.Reconnecting
            else -> ConnectionPhase.Disconnected
        }
}

/** In-process observable state for Compose. Durable configuration stays in [AgentStore]. */
object AgentRuntimeState {
    private val mutable = MutableStateFlow(AgentSnapshot())
    val snapshot: StateFlow<AgentSnapshot> = mutable

    fun load(context: Context) {
        val store = AgentStore(context)
        mutable.value = mutable.value.copy(
            deviceName = store.deviceName(),
            servingEnabled = store.isServingEnabled(),
            profiles = store.profiles(),
            events = store.events(),
        )
    }

    fun updateStatus(status: ConnectionStatus) {
        mutable.value = mutable.value.copy(statuses = mutable.value.statuses + (status.profileId to status))
    }

    fun removeStatus(profileId: String) {
        mutable.value = mutable.value.copy(statuses = mutable.value.statuses - profileId)
    }

    fun record(context: Context, event: AgentEvent) {
        val events = (listOf(event) + mutable.value.events).take(MAX_EVENTS)
        AgentStore(context).setEvents(events)
        mutable.value = mutable.value.copy(events = events)
    }

    fun refresh(context: Context) = load(context)

    private const val MAX_EVENTS = 100
}

class AgentStore(context: Context) {
    private val prefs = context.getSharedPreferences("nutty_proxy_agent", Context.MODE_PRIVATE)

    fun profiles(): List<AgentProfile> = runCatching {
        val values = JSONArray(prefs.getString("profiles", "[]"))
        buildList {
            for (index in 0 until values.length()) {
                val item = values.getJSONObject(index)
                add(
                    AgentProfile(
                        id = item.getString("id"),
                        agentId = item.getString("agentId"),
                        serverName = item.getString("serverName"),
                        gatewayUrl = item.getString("gatewayUrl"),
                        certificatePin = item.getString("certificatePin"),
                        enabled = item.optBoolean("enabled", true),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun upsert(profile: AgentProfile) {
        // `p1` is a natural agent id for many independent projects. It is only
        // unique inside one gateway, never globally on a phone.
        val updated = profiles().filterNot {
            it.id == profile.id || (it.agentId == profile.agentId && it.gatewayUrl == profile.gatewayUrl)
        } + profile
        prefs.edit().putString("profiles", JSONArray().apply {
            updated.forEach { entry ->
                put(JSONObject().apply {
                    put("id", entry.id)
                    put("agentId", entry.agentId)
                    put("serverName", entry.serverName)
                    put("gatewayUrl", entry.gatewayUrl)
                    put("certificatePin", entry.certificatePin)
                    put("enabled", entry.enabled)
                })
            }
        }.toString()).apply()
    }

    fun remove(profileId: String) {
        val updated = profiles().filterNot { it.id == profileId }
        prefs.edit().putString("profiles", JSONArray().apply {
            updated.forEach { entry -> put(JSONObject().apply {
                put("id", entry.id); put("agentId", entry.agentId); put("serverName", entry.serverName)
                put("gatewayUrl", entry.gatewayUrl); put("certificatePin", entry.certificatePin); put("enabled", entry.enabled)
            }) }
        }.toString()).apply()
    }

    fun setProfileEnabled(profileId: String, enabled: Boolean) {
        profiles().firstOrNull { it.id == profileId }?.let { upsert(it.copy(enabled = enabled)) }
    }

    fun isServingEnabled(): Boolean = prefs.getBoolean("serving_enabled", false)
    fun setServingEnabled(enabled: Boolean) = prefs.edit().putBoolean("serving_enabled", enabled).apply()

    fun deviceName(): String = prefs.getString("device_name", "Phone") ?: "Phone"
    fun setDeviceName(name: String) = prefs.edit().putString("device_name", name.take(32)).apply()

    fun setBootObserved() = prefs.edit().putBoolean("boot_observed", true).apply()
    fun bootObserved(): Boolean = prefs.getBoolean("boot_observed", false)

    fun events(): List<AgentEvent> = runCatching {
        val values = JSONArray(prefs.getString("events", "[]"))
        buildList {
            for (index in 0 until values.length()) {
                val item = values.getJSONObject(index)
                add(
                    AgentEvent(
                        at = item.getLong("at"),
                        profileId = item.optString("profileId").ifBlank { null },
                        kind = AgentEvent.Kind.valueOf(item.getString("kind")),
                        detail = item.getString("detail"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    fun setEvents(events: List<AgentEvent>) {
        prefs.edit().putString("events", JSONArray().apply {
            events.take(100).forEach { event ->
                put(JSONObject().apply {
                    put("at", event.at)
                    put("profileId", event.profileId)
                    put("kind", event.kind.name)
                    put("detail", event.detail)
                })
            }
        }.toString()).apply()
    }
}

object PairingParser {
    fun parse(payload: String): Result<PairingPayload> = runCatching {
        val json = JSONObject(payload.trim())
        require(json.optInt("version") == 1) { "Unsupported pairing version" }
        val gatewayUrl = json.getString("gatewayUrl")
        val uri = Uri.parse(gatewayUrl)
        require(
            uri.scheme == "wss" && !uri.host.isNullOrBlank() && uri.userInfo.isNullOrBlank() &&
                uri.query.isNullOrBlank() && uri.fragment.isNullOrBlank(),
        ) { "Gateway must be a clean wss:// URL" }
        val certificatePin = json.getString("certificatePin")
        require(certificatePin.startsWith("sha256/") && runCatching {
            Base64.getDecoder().decode(certificatePin.removePrefix("sha256/")).size == 32
        }.getOrDefault(false)) { "Invalid TLS certificate pin" }
        val agentId = json.getString("agentId")
        require(agentId.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{1,63}"))) { "Invalid agent id" }
        val token = json.getString("enrollmentToken")
        require(runCatching { Base64.getUrlDecoder().decode(token).size == 32 }.getOrDefault(false)) { "Invalid pairing token" }
        require(Instant.parse(json.getString("expiresAt")).isAfter(Instant.now())) { "Pairing token has expired" }
        PairingPayload(
            gatewayUrl = gatewayUrl,
            certificatePin = certificatePin,
            agentId = agentId,
            serverName = json.optString("serverName").ifBlank {
                // Accept the first development payload spelling as well; the
                // canonical server contract is `serverName`.
                json.optString("displayName").ifBlank { uri.host ?: "Server" }
            },
            enrollmentToken = token,
        )
    }

    fun profile(payload: PairingPayload): AgentProfile = AgentProfile(
        id = UUID.randomUUID().toString(),
        agentId = payload.agentId,
        serverName = payload.serverName,
        gatewayUrl = payload.gatewayUrl,
        certificatePin = payload.certificatePin,
    )
}
