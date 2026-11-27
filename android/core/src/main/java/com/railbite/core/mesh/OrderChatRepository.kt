package com.railbite.core.mesh

import com.railbite.core.model.OrderMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** Which physical transport delivered a quick-chat message. */
enum class ChatTransport { LAN, BLE }

/** Thrown when neither LAN nor the BLE mesh can accept a quick-chat message. */
class QuickChatUnavailableException(message: String) : Exception(message)

/**
 * Process-wide, single source of truth for order quick-chat messages.
 *
 * Both transports feed this repository:
 * - LAN/MQTT payloads call [ingest] / [ingestAll] with [ChatTransport.LAN]
 * - Incoming 23-byte `0x13 QUICK_MSG` frames are collected from [BleMeshHub.quickMessages]
 *   and converted with [ChatTransport.BLE]
 *
 * ViewModels collect [messages] only, so MQTT and BLE always land in the same ordered flow and
 * are de-duplicated by message id (`ble-role-seq` for BLE, server UUID for LAN).
 */
object OrderChatRepository {

    private const val MAX_MESSAGES_PER_ORDER = 200

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _messages = MutableStateFlow<Map<String, List<OrderMessage>>>(emptyMap())
    val messages: StateFlow<Map<String, List<OrderMessage>>> = _messages.asStateFlow()

    private val transports = ConcurrentHashMap<String, ChatTransport>()
    private val bleTimestamps = ConcurrentHashMap<String, Long>()

    init {
        scope.launch {
            BleMeshHub.quickMessages.collect { byOrder ->
                byOrder.forEach { (shortId, packets) ->
                    val orderId = BlePacketCodec.shortIdToUuid(shortId)
                    packets.forEach { ingestBle(orderId, it) }
                }
            }
        }
    }

    fun ingest(orderId: String, message: OrderMessage, transport: ChatTransport) {
        if (orderId.isBlank() || message.id.isBlank() || message.body.isBlank()) return
        transports[message.id] = transport
        _messages.update { prev ->
            val current = prev[orderId].orEmpty()
            if (current.any { it.id == message.id }) return@update prev
            val merged = (current + message)
                .sortedBy { it.createdAt }
                .takeLast(MAX_MESSAGES_PER_ORDER)
            prev + (orderId to merged)
        }
    }

    fun ingestAll(orderId: String, list: List<OrderMessage>, transport: ChatTransport) {
        list.forEach { ingest(orderId, it, transport) }
    }

    fun transportOf(messageId: String): ChatTransport? = transports[messageId]

    fun clear(orderId: String) {
        val removed = _messages.value[orderId].orEmpty().map { it.id }
        _messages.update { it - orderId }
        removed.forEach { transports.remove(it) }
    }

    private fun ingestBle(orderId: String, packet: BlePacketCodec.QuickMsg) {
        val preset = BlePacketCodec.presetForCode(packet.presetCode) ?: return
        val id = "ble-${packet.senderRole}-${packet.msgSeq}"
        val createdAt = bleTimestamps.getOrPut(id) { System.currentTimeMillis() }
        ingest(
            orderId = orderId,
            message = OrderMessage(
                id = id,
                orderId = orderId,
                senderRole = if (packet.senderRole == BlePacketCodec.ROLE_PICKER) "PICKER" else "CUSTOMER",
                senderId = null,
                presetCode = preset.code,
                body = preset.body,
                createdAt = createdAt,
            ),
            transport = ChatTransport.BLE,
        )
    }
}
