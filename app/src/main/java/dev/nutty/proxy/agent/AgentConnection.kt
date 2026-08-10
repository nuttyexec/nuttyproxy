package dev.nutty.proxy.agent

import okhttp3.CertificatePinner
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** One encrypted, certificate-pinned tunnel for one enrolled server. */
class AgentConnection(
    private val profile: AgentProfile,
    private val pairingToken: String?,
    private val deviceName: String,
    private val identity: AgentIdentity,
    private val listener: Listener,
) {
    interface Listener {
        fun onReady(profile: AgentProfile)
        fun onStatus(profile: AgentProfile, phase: ConnectionPhase, detail: String)
        fun onStream(profile: AgentProfile, active: Int, sent: Long, received: Long)
        fun onRequest(profile: AgentProfile, host: String, port: Int, method: String)
        fun onDisconnected(profile: AgentProfile, reason: String)
    }

    private val sockets = ConcurrentHashMap<Int, Socket>()
    private val io = Executors.newCachedThreadPool()
    private val scheduler = ScheduledThreadPoolExecutor(1)
    private var heartbeat: ScheduledFuture<*>? = null
    @Volatile private var socket: WebSocket? = null
    @Volatile private var stopped = false
    @Volatile private var enrolled = pairingToken == null
    @Volatile private var bytesSent = 0L
    @Volatile private var bytesReceived = 0L
    private val disconnectedReported = AtomicBoolean(false)
    private var client: OkHttpClient? = null
    private val identityScope = "${profile.gatewayUrl}\n${profile.agentId}"

    fun connect() {
        runCatching {
            stopped = false
            disconnectedReported.set(false)
            heartbeat?.cancel(false)
            socket?.cancel()
            listener.onStatus(profile, ConnectionPhase.Connecting, "Opening secure tunnel")
            val host = profile.gatewayUrl.toHttpUrl().host
            client = OkHttpClient.Builder()
                .pingInterval(45, TimeUnit.SECONDS)
                .certificatePinner(CertificatePinner.Builder().add(host, profile.certificatePin).build())
                .build()
            socket = client!!.newWebSocket(Request.Builder().url(profile.gatewayUrl).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                runCatching {
                    val hello = JSONObject()
                        .put("type", "hello")
                        .put("version", 1)
                        .put("agentId", profile.agentId)
                        .put("deviceName", deviceName)
                    if (!enrolled) {
                        hello.put("enrollmentToken", pairingToken)
                        hello.put("publicKeyJwk", JSONObject(identity.publicJwk(identityScope).value))
                    }
                    webSocket.send(hello.toString())
                }.onFailure { closeWithError("Could not create device identity") }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { handleControl(webSocket, JSONObject(text)) }
                    .onFailure { closeWithError("Invalid gateway message") }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                runCatching { handleFrame(bytes.toByteArray()) }
                    .onFailure { closeWithError("Invalid gateway frame") }
            }

            override fun onFailure(webSocket: WebSocket, throwable: Throwable, response: Response?) {
                reportDisconnected(throwable.message ?: "Network error")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                reportDisconnected(if (reason.isBlank()) "Connection closed ($code)" else reason)
            }
            })
        }.onFailure { error ->
            listener.onStatus(profile, ConnectionPhase.Attention, "Could not open secure tunnel")
            reportDisconnected(error.message ?: "Tunnel setup error")
        }
    }

    fun stop() {
        stopped = true
        heartbeat?.cancel(true)
        sockets.values.forEach { runCatching { it.close() } }
        sockets.clear()
        socket?.close(1000, "stopped")
        socket = null
        client?.dispatcher?.executorService?.shutdown()
        client?.connectionPool?.evictAll()
        client = null
        io.shutdownNow()
        scheduler.shutdownNow()
    }

    private fun handleControl(webSocket: WebSocket, message: JSONObject) {
        when (message.optString("type")) {
            "challenge" -> {
                val payload = "phone-proxy-agent/v1\n${profile.agentId}\n${message.getString("challenge")}".toByteArray()
                val signature = Base64.getUrlEncoder().withoutPadding().encodeToString(identity.sign(identityScope, payload))
                webSocket.send(JSONObject().put("type", "authenticate").put("signature", signature).toString())
            }
            "ready" -> {
                enrolled = true
                listener.onReady(profile)
                listener.onStatus(profile, ConnectionPhase.Connected, "Secure tunnel connected")
                val interval = message.optLong("heartbeatIntervalMs", 90_000L).coerceIn(30_000L, 300_000L)
                heartbeat?.cancel(false)
                heartbeat = scheduler.scheduleAtFixedRate(
                    { socket?.send(JSONObject().put("type", "heartbeat").toString()) },
                    interval,
                    interval,
                    TimeUnit.MILLISECONDS,
                )
            }
            "heartbeat_ack" -> listener.onStatus(profile, ConnectionPhase.Connected, "Secure tunnel connected")
            "open" -> openRemoteSocket(message)
            "close" -> closeStream(message.optInt("streamId", -1), notifyGateway = false)
            "error" -> closeWithError(message.optString("code", "Gateway error"))
        }
    }

    private fun openRemoteSocket(message: JSONObject) {
        val streamId = message.optInt("streamId", -1)
        val host = message.optString("host")
        val port = message.optInt("port", -1)
        if (streamId < 0 || host.isBlank() || port !in 1..65535) return
        listener.onRequest(profile, host, port, message.optString("method", "CONNECT"))
        io.execute {
            try {
                val remote = Socket().apply {
                    tcpNoDelay = true
                    connect(InetSocketAddress(host, port), 15_000)
                }
                sockets[streamId] = remote
                socket?.send(JSONObject().put("type", "opened").put("streamId", streamId).toString())
                emitStreamState()
                val input = remote.getInputStream()
                val buffer = ByteArray(16 * 1024)
                while (!remote.isClosed) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    bytesSent += count
                    sendFrame(2, streamId, buffer.copyOf(count))
                    emitStreamState()
                }
                socket?.send(JSONObject().put("type", "closed").put("streamId", streamId).toString())
            } catch (error: Exception) {
                socket?.send(
                    JSONObject().put("type", "stream_error").put("streamId", streamId)
                        .put("message", error.message ?: "Socket error").toString(),
                )
            } finally {
                closeStream(streamId, notifyGateway = false)
            }
        }
    }

    private fun handleFrame(frame: ByteArray) {
        if (frame.size < 5 || frame[0].toInt() != 1) return
        val streamId = ((frame[1].toInt() and 0xff) shl 24) or
            ((frame[2].toInt() and 0xff) shl 16) or
            ((frame[3].toInt() and 0xff) shl 8) or (frame[4].toInt() and 0xff)
        val remote = sockets[streamId] ?: return
        runCatching {
            remote.getOutputStream().write(frame, 5, frame.size - 5)
            remote.getOutputStream().flush()
            bytesReceived += frame.size - 5
            emitStreamState()
        }.onFailure { closeStream(streamId, notifyGateway = true) }
    }

    private fun sendFrame(type: Int, streamId: Int, payload: ByteArray) {
        val frame = ByteArray(5 + payload.size)
        frame[0] = type.toByte()
        frame[1] = (streamId ushr 24).toByte()
        frame[2] = (streamId ushr 16).toByte()
        frame[3] = (streamId ushr 8).toByte()
        frame[4] = streamId.toByte()
        payload.copyInto(frame, 5)
        socket?.send(frame.toByteString())
    }

    private fun closeStream(streamId: Int, notifyGateway: Boolean) {
        if (streamId < 0) return
        sockets.remove(streamId)?.let { runCatching { it.close() } }
        if (notifyGateway) socket?.send(JSONObject().put("type", "closed").put("streamId", streamId).toString())
        emitStreamState()
    }

    private fun emitStreamState() = listener.onStream(profile, sockets.size, bytesSent, bytesReceived)

    private fun closeWithError(detail: String) {
        socket?.close(1008, detail)
        reportDisconnected(detail)
    }

    private fun reportDisconnected(reason: String) {
        if (!stopped && disconnectedReported.compareAndSet(false, true)) {
            listener.onDisconnected(profile, reason)
        }
    }
}
