package com.railbite.core.mqtt

import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import com.railbite.core.api.ApiClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Cloud-native MQTT-over-WebSocket wrapper around HiveMQ MQTT v3 async client.
 *
 * - Single port: attaches over WebSocket on path "/mqtt"
 * - Supports wss:// (TLS) for https cloud endpoints and ws:// for local/prototype
 * - Automatic reconnect with default config
 * - Re-subscribes all topics after every reconnect
 * - QoS 1
 */
class MqttConnector(
    private val host: String,
    private val clientId: String,
    private val port: Int = 443,
    private val isSsl: Boolean = false,
    private val webSocketPath: String = "/mqtt",
    private val onConnected: (() -> Unit)? = null,
    private val onDisconnected: (() -> Unit)? = null,
) {
    /** Backwards-compatible constructor for host + clientId */
    constructor(
        host: String,
        clientId: String,
        onConnected: (() -> Unit)? = null,
        onDisconnected: (() -> Unit)? = null,
    ) : this(
        host = host,
        clientId = clientId,
        port = 443,
        isSsl = false,
        webSocketPath = "/mqtt",
        onConnected = onConnected,
        onDisconnected = onDisconnected,
    )

    companion object {
        private const val TAG = "RailBiteMqtt"

        /**
         * SUBSCRIBE is the only MQTT call that used to block the caller while it waited for the
         * SUBACK. 1500ms keeps the call bounded and it always runs on [Dispatchers.IO] now, so a
         * slow broker can never freeze the Main thread (the previous 10s wait did exactly that
         * every 5s while the Picker dashboard polled chat subscriptions).
         */
        private const val SUBSCRIBE_TIMEOUT_MS = 1_500L
        data class Endpoint(
            val host: String,
            val port: Int,
            val isSsl: Boolean,
            val webSocketPath: String = "/mqtt",
        )

        fun parseServerUrl(serverUrl: String, mqttWsUrlOverride: String = ""): Endpoint {
            val wsOverride = mqttWsUrlOverride.trim()
            if (wsOverride.isNotEmpty()) {
                val normalizedWs = ApiClient.deriveMqttWsUrl(serverUrl, wsOverride)
                return parseUri(normalizedWs)
            }
            val normalized = ApiClient.normalizeServerUrl(serverUrl)
            if (normalized.isBlank()) return Endpoint("", 0, false, "/mqtt")
            return parseUri(normalized)
        }

        private fun parseUri(rawUri: String): Endpoint {
            if (rawUri.isBlank()) return Endpoint("", 0, false, "/mqtt")
            return try {
                val uri = URI(rawUri)
                val host = uri.host ?: ""
                val isSsl = uri.scheme?.equals("https", ignoreCase = true) == true ||
                    uri.scheme?.equals("wss", ignoreCase = true) == true
                val port = when {
                    uri.port != -1 -> uri.port
                    isSsl -> 443
                    else -> 80
                }
                val path = uri.path?.takeIf { it.isNotBlank() && it != "/" } ?: "/mqtt"
                Endpoint(host, port, isSsl, path)
            } catch (_: Exception) {
                Endpoint("", 0, false, "/mqtt")
            }
        }

        fun fromServerUrl(
            serverUrl: String,
            clientId: String,
            webSocketPath: String = "/mqtt",
            mqttWsUrlOverride: String = "",
            onConnected: (() -> Unit)? = null,
            onDisconnected: (() -> Unit)? = null,
        ): MqttConnector {
            val ep = parseServerUrl(serverUrl, mqttWsUrlOverride)
            val resolvedPath = if (mqttWsUrlOverride.isNotBlank()) ep.webSocketPath else webSocketPath
            return MqttConnector(
                host = ep.host,
                clientId = clientId,
                port = ep.port,
                isSsl = ep.isSsl,
                webSocketPath = resolvedPath,
                onConnected = onConnected,
                onDisconnected = onDisconnected,
            )
        }
    }

    private val _connected = MutableStateFlow(false)
    val connected = _connected

    private val subscriptions = ConcurrentHashMap<String, (ByteArray) -> Unit>()
    private var client: Mqtt3AsyncClient? = null
    private var stopped = false

    /**
     * Subscribes (or registers for later auto-subscribe) and receives payloads as raw bytes.
     *
     * Idempotent: repeated calls for an already-registered topic do not emit another SUBSCRIBE
     * frame. The SUBACK wait is bounded to [SUBSCRIBE_TIMEOUT_MS] and dispatched to
     * [Dispatchers.IO] so it can never block a ViewModel/Main caller.
     */
    suspend fun subscribe(topic: String, callback: (ByteArray) -> Unit) {
        val isNewTopic = subscriptions.put(topic, callback) == null
        if (!isNewTopic) return
        val c = client ?: return
        if (!_connected.value) return
        withContext(Dispatchers.IO) {
            runCatching {
                c.subscribeWith()
                    .topicFilter(topic)
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .send()
                    .get(SUBSCRIBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }.onFailure {
                Log.w(TAG, "subscribe failed $topic: ${it.message}")
            }
        }
    }

    fun unsubscribe(topic: String) {
        subscriptions.remove(topic)
        client?.let { c ->
            runCatching { c.unsubscribeWith().topicFilter(topic).send() }
        }
    }

    /**
     * Builds and connects the WebSocket client.
     * Retries automatically in the background if initially unreachable.
     */
    suspend fun start(connectTimeoutMs: Long = 20_000): Boolean = withContext(Dispatchers.IO) {
        if (host.isBlank() || port <= 0) return@withContext false
        stopped = false
        var ok = tryConnect(timeoutMs = connectTimeoutMs)
        if (!ok && !stopped) {
            // Keep background reconnect hammering
            buildClient()
            runCatching { connectAsync() }
        }
        ok
    }

    private suspend fun tryConnect(timeoutMs: Long): Boolean {
        buildClient()
        val deferred = CompletableDeferred<Boolean>()
        val c = client ?: return false

        val future = connectAsync()
        future.whenComplete { _, error ->
            if (error == null) {
                deferred.complete(true)
            } else {
                Log.w(TAG, "connect error: ${error.javaClass.simpleName}: ${error.message}")
                deferred.complete(false)
            }
        }
        val connected = withTimeoutOrNull(timeoutMs) { deferred.await() } ?: false
        if (connected) {
            _connected.value = true
            Log.d(TAG, "connected $host:$port path=$webSocketPath ssl=$isSsl")
            resubscribeAll()
        } else {
            Log.w(TAG, "connect FAILED $host:$port (timeout or error above)")
            runCatching { future.cancel(true) }
            runCatching { c.disconnect() }
            client = null
        }
        return connected
    }

    private fun connectAsync() =
        (client ?: throw IllegalStateException("client not built")).connectWith()
            .cleanSession(true)
            .keepAlive(30)
            .send()

    private fun buildClient() {
        var builder = MqttClient.builder()
            .useMqttVersion3()
            .identifier(clientId)
            .serverHost(host)
            .serverPort(port)
            .automaticReconnectWithDefaultConfig()
            .addConnectedListener {
                Log.d(TAG, "client CONNECTED listener fired")
                _connected.value = true
                resubscribeAll()
                onConnected?.invoke()
            }
            .addDisconnectedListener {
                Log.d(TAG, "client DISCONNECTED listener fired")
                _connected.value = false
                onDisconnected?.invoke()
            }

        if (isSsl) {
            builder = builder.sslWithDefaultConfig()
        }

        builder = builder.useWebSocket(
            com.hivemq.client.mqtt.MqttWebSocketConfig.builder()
                .serverPath(webSocketPath)
                .build(),
        )

        val built = builder.buildAsync()
        // Always register global publish callback on every client instance (including background reconnect fallback).
        built.publishes(MqttGlobalPublishFilter.SUBSCRIBED) { publish -> dispatch(publish) }
        client = built
    }

    private fun resubscribeAll() {
        val c = client ?: return
        for ((topic, _) in subscriptions) {
            Log.d(TAG, "subscribing $topic")
            runCatching {
                c.subscribeWith()
                    .topicFilter(topic)
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .send()
            }.onFailure { Log.w(TAG, "subscribe failed $topic: ${it.message}") }
        }
    }

    private fun dispatch(publish: Mqtt3Publish) {
        val topic = publish.topic.toString()
        val cb = subscriptions[topic]
        if (cb == null) {
            Log.w(TAG, "publish on unsubscribed topic: $topic")
            return
        }
        Log.d(TAG, "publish received on $topic (${publish.payloadAsBytes.size} bytes)")
        runCatching {
            cb(publish.payloadAsBytes)
        }
    }

    fun stop() {
        stopped = true
        _connected.value = false
        client?.let { runCatching { it.disconnect() } }
        client = null
    }
}
