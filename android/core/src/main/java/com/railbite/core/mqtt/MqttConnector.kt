package com.railbite.core.mqtt

import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper around the HiveMQ MQTT v3 async client.
 *
 * - connects over plain TCP :1883 (preferred); falls back to WebSocket :8883 once if TCP fails
 * - automatic reconnect with default config after the first successful connect
 * - re-subscribes all topics after every (re)connect (cleanSession = true)
 * - QoS 1 everywhere
 *
 * Callbacks are invoked on HiveMQ's netty threads — hop to your own dispatcher if needed.
 */
class MqttConnector(
    private val host: String,
    private val clientId: String,
    private val onConnected: (() -> Unit)? = null,
    private val onDisconnected: (() -> Unit)? = null,
) {
    private val _connected = kotlinx.coroutines.flow.MutableStateFlow(false)
    val connected = _connected

    private val subscriptions = ConcurrentHashMap<String, (ByteArray) -> Unit>()
    private var client: Mqtt3AsyncClient? = null
    private var useWebSocket = false
    private var stopped = false

    /** Subscribes (or registers for later auto-subscribe) and receives payloads as raw bytes. */
    suspend fun subscribe(topic: String, callback: (ByteArray) -> Unit) {
        subscriptions[topic] = callback
        client?.let { c ->
            if (_connected.value) {
                runCatching {
                    c.subscribeWith()
                        .topicFilter(topic)
                        .qos(MqttQos.AT_LEAST_ONCE)
                        .send()
                        .get(10, TimeUnit.SECONDS)
                }
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
     * Builds and connects the client. Tries TCP first; on failure tries the WebSocket
     * transport once; if that also fails, rebuilds TCP and lets automatic reconnect retry forever.
     * Returns true when connected (within the timeout).
     */
    suspend fun start(connectTimeoutMs: Long = 6_000): Boolean = withContext(Dispatchers.IO) {
        stopped = false
        useWebSocket = false
        var ok = tryConnect(useWebSocket = false, timeoutMs = connectTimeoutMs)
        if (!ok) {
            // One WebSocket attempt (broker also listens on 8883/mqtt-over-ws)
            ok = tryConnect(useWebSocket = true, timeoutMs = connectTimeoutMs)
            if (ok) useWebSocket = true
        }
        if (!ok) {
            // Give up for now; auto-reconnect keeps hammering TCP in the background.
            buildClient(tcp = true)
            connectAsync()
        }
        ok
    }

    private suspend fun tryConnect(useWebSocket: Boolean, timeoutMs: Long): Boolean {
        buildClient(tcp = !useWebSocket, webSocket = useWebSocket)
        val deferred = CompletableDeferred<Boolean>()
        val c = client ?: return false

        c.publishes(MqttGlobalPublishFilter.SUBSCRIBED) { publish -> dispatch(publish) }

        val future = connectAsync()
        future.whenComplete { _, error ->
            if (error == null) deferred.complete(true) else deferred.complete(false)
        }
        val connected = withTimeoutOrNull(timeoutMs) { deferred.await() } ?: false
        if (connected) {
            _connected.value = true
            resubscribeAll()
        } else {
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

    private fun buildClient(tcp: Boolean, webSocket: Boolean = false) {
        var builder = MqttClient.builder()
            .useMqttVersion3()
            .identifier(clientId)
            .serverHost(host)
            .serverPort(if (webSocket) 8883 else 1883)
            .automaticReconnectWithDefaultConfig()
            .addConnectedListener {
                _connected.value = true
                resubscribeAll()
                onConnected?.invoke()
            }
            .addDisconnectedListener {
                _connected.value = false
                onDisconnected?.invoke()
            }
        if (webSocket) {
            // Aedes websocket-stream accepts any upgrade path; "/" is what the broker exposes.
            builder = builder.webSocketConfig(
                com.hivemq.client.mqtt.MqttWebSocketConfig.builder().serverPath("/").build(),
            )
        }
        client = builder.buildAsync()
    }

    private fun resubscribeAll() {
        val c = client ?: return
        for ((topic, _) in subscriptions) {
            runCatching {
                c.subscribeWith()
                    .topicFilter(topic)
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .send()
            }
        }
    }

    private fun dispatch(publish: Mqtt3Publish) {
        val topic = publish.topic.toString()
        val cb = subscriptions[topic] ?: return
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
