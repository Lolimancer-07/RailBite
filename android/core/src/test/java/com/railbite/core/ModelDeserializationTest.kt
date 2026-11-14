package com.railbite.core

import com.railbite.core.api.ApiClient
import com.railbite.core.model.Health
import com.railbite.core.model.LedgerSummaryRow
import com.railbite.core.model.MqttOrderAlert
import com.railbite.core.model.MqttOrderStatus
import com.railbite.core.model.Order
import com.railbite.core.model.OrderCreateRequest
import com.railbite.core.model.PickerStats
import com.railbite.core.model.StaffLoginResponse
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decodes the exact JSON shapes the edge server emits (services/edge-server/src):
 * orderRepo.findById/findByPnr, staff routes, ledgerRepo, dispatcher.alertPayload and
 * orderService.publishOrderStatus. Money fields are integer paise.
 */
class ModelDeserializationTest {

    private val json = ApiClient.json

    // GET /orders/:id (orderRepo.findById) — includes items; deliveryCode may be absent
    private val orderWithItems = """
        {
          "id": "2f0c8a7e-1111-4bbb-8ccc-000000000001",
          "pnr": "8492000001",
          "coach": "B3",
          "berth": 12,
          "diners": 2,
          "track": "READY",
          "status": "OUT_FOR_DELIVERY",
          "paymentMode": "COD",
          "totalPaise": 27000,
          "source": "WIFI",
          "verified": 1,
          "createdAt": "2026-11-13T10:00:00.000Z",
          "updatedAt": "2026-11-13T10:05:00.000Z",
          "items": [
            { "itemId": "3c2a1b0d-2222-4ccc-9ddd-000000000002", "qty": 2, "unitPricePaise": 12000, "name": "Veg Biryani" },
            { "itemId": "3c2a1b0d-2222-4ccc-9ddd-000000000003", "qty": 1, "unitPricePaise": 3000 }
          ]
        }
    """.trimIndent()

    // GET /orders (orderRepo.findByPnr) — no items/feedback keys at all
    private val orderListItem = """
        {
          "id": "2f0c8a7e-1111-4bbb-8ccc-000000000001",
          "pnr": "8492000001",
          "coach": "B3",
          "berth": 12,
          "diners": 2,
          "track": "READY",
          "status": "PLACED",
          "paymentMode": "COD",
          "totalPaise": 27000,
          "source": "WIFI",
          "verified": 1,
          "createdAt": "2026-11-13T10:00:00.000Z",
          "updatedAt": "2026-11-13T10:00:00.000Z"
        }
    """.trimIndent()

    @Test
    fun order_with_items_decodes() {
        val order = json.decodeFromString(Order.serializer(), orderWithItems)
        assertEquals("OUT_FOR_DELIVERY", order.status)
        assertEquals(27000, order.totalPaise)
        assertEquals("B3", order.coach)
        assertEquals(2, order.items!!.size)
        assertEquals("Veg Biryani", order.items!![0].name)
        assertNull(order.items!![1].name)
        assertNull(order.deliveryCode) // picker view (?role=picker) strips it
        assertNull(order.feedback)
    }

    @Test
    fun order_list_item_without_items_key_decodes() {
        val order = json.decodeFromString(Order.serializer(), orderListItem)
        assertEquals("PLACED", order.status)
        assertNull(order.items)
    }

    @Test
    fun order_with_delivery_code_and_feedback_decodes() {
        val payload = """
            {
              "id": "2f0c8a7e-1111-4bbb-8ccc-000000000001",
              "pnr": "8492000001",
              "coach": "B3",
              "berth": 12,
              "diners": 2,
              "track": "READY",
              "status": "DELIVERED",
              "paymentMode": "COD",
              "totalPaise": 27000,
              "source": "WIFI",
              "verified": 1,
              "createdAt": "2026-11-13T10:00:00.000Z",
              "updatedAt": "2026-11-13T10:00:00.000Z",
              "deliveryCode": "4711",
              "feedback": { "received": true, "rating": 5, "reasons": ["On time"] }
            }
        """.trimIndent()
        val order = json.decodeFromString(Order.serializer(), payload)
        assertEquals("4711", order.deliveryCode)
        assertEquals(5, order.feedback!!.rating)
        assertTrue(order.feedback!!.received)
        assertEquals(listOf("On time"), order.feedback!!.reasons)
    }

    @Test
    fun menu_decodes() {
        val menu = """
            [
              { "id": "3c2a1b0d-2222-4ccc-9ddd-000000000002", "name": "Veg Biryani", "category": "MEALS",
                "track": "READY", "mrpPaise": 12000, "stock": 12, "prepMinutes": 0, "isVeg": true, "active": true }
            ]
        """.trimIndent()
        val items = json.decodeFromString(
            ListSerializer(com.railbite.core.model.MenuItem.serializer()),
            menu,
        )
        assertEquals(1, items.size)
        assertEquals(12000, items[0].mrpPaise)
        assertEquals("MEALS", items[0].category)
        assertTrue(items[0].isVeg)
    }

    @Test
    fun health_decodes() {
        val payload = """{"ok":true,"tripId":"TRIP_001","time":"2026-11-13T10:00:00.000Z","version":"0.1.0"}"""
        val health = json.decodeFromString(Health.serializer(), payload)
        assertEquals("TRIP_001", health.tripId)
        assertTrue(health.ok)
    }

    @Test
    fun staff_login_response_decodes() {
        val payload = """
            {
              "staffId": "c1", "name": "Captain One", "role": "PICKER",
              "zoneCoaches": ["B1", "B2", "B3"],
              "token": "rb-tok-c1-1731482400000",
              "expiresAt": "2026-11-13T22:00:00.000Z"
            }
        """.trimIndent()
        val login = json.decodeFromString(StaffLoginResponse.serializer(), payload)
        assertEquals("c1", login.staffId)
        assertEquals(listOf("B1", "B2", "B3"), login.zoneCoaches)
        assertTrue(login.token.startsWith("rb-tok-c1"))
    }

    @Test
    fun picker_stats_decodes_with_and_without_rating() {
        val withRating = """
            { "staffId": "c1", "staffName": "Captain One", "date": "2026-11-13",
              "ordersDelivered": 4, "expectedPaise": 50000, "collectedPaise": 50000,
              "passengerConfirmedPaise": 45000, "avgRating": 4.5, "feedbackCount": 3 }
        """.trimIndent()
        val s1 = json.decodeFromString(PickerStats.serializer(), withRating)
        assertEquals(4, s1.ordersDelivered)
        assertEquals(4.5, s1.avgRating!!, 0.0001)

        // fresh day, no feedback yet — avgRating/feedbackCount absent server-side
        val withoutRating = """
            { "staffId": "c1", "staffName": "Captain One", "date": "2026-11-13",
              "ordersDelivered": 0, "expectedPaise": 0, "collectedPaise": 0,
              "passengerConfirmedPaise": 0 }
        """.trimIndent()
        val s2 = json.decodeFromString(PickerStats.serializer(), withoutRating)
        assertNull(s2.avgRating)
        assertEquals(0, s2.feedbackCount)
    }

    @Test
    fun ledger_summary_rows_decode() {
        val rows = """
            [
              { "captainId": "c1", "captainName": "Captain One", "expectedPaise": 27000,
                "collectedPaise": 27000, "passengerConfirmedPaise": 27000, "mismatch": false },
              { "captainId": "c2", "captainName": "Captain Two", "expectedPaise": 12000,
                "collectedPaise": 11000, "passengerConfirmedPaise": 0, "mismatch": true }
            ]
        """.trimIndent()
        val parsed = json.decodeFromString(ListSerializer(LedgerSummaryRow.serializer()), rows)
        assertEquals(2, parsed.size)
        assertTrue(parsed[1].mismatch)
        assertEquals(11000, parsed[1].collectedPaise)
    }

    // dispatcher.alertPayload — rb/{tripId}/captain/{captainId}/alerts
    @Test
    fun mqtt_order_alert_decodes_exactly_as_dispatcher_publishes() {
        val alert = """
            {
              "orderId": "2f0c8a7e-1111-4bbb-8ccc-000000000001",
              "coach": "B3", "berth": 12,
              "items": [
                { "name": "Veg Biryani", "qty": 2, "unitPricePaise": 12000 },
                { "name": "Item", "qty": 1, "unitPricePaise": 3000 }
              ],
              "total": 27000, "track": "READY", "attempt": 2,
              "expiresAt": "2026-11-13T10:01:00.000Z",
              "pnr": "8492000001", "diners": 2, "verified": 1
            }
        """.trimIndent()
        val parsed = json.decodeFromString(MqttOrderAlert.serializer(), alert)
        assertEquals(2, parsed.items.size)
        assertEquals(2, parsed.attempt)
        assertEquals(27000, parsed.total)
        assertEquals(12000, parsed.items[0].unitPricePaise)
    }

    // orderService.publishOrderStatus — rb/{tripId}/orders/{orderId}/status (retained)
    @Test
    fun mqtt_order_status_decodes_with_and_without_captain() {
        val accepted = """
            { "status": "ACCEPTED", "updatedAt": "2026-11-13T10:01:00.000Z",
              "captainName": "Captain One" }
        """.trimIndent()
        val s1 = json.decodeFromString(MqttOrderStatus.serializer(), accepted)
        assertEquals("ACCEPTED", s1.status)
        assertEquals("Captain One", s1.captainName)
        assertNull(s1.etaMin)

        val placed = """{"status": "PLACED", "updatedAt": "2026-11-13T10:00:00.000Z"}"""
        val s2 = json.decodeFromString(MqttOrderStatus.serializer(), placed)
        assertEquals("PLACED", s2.status)
        assertNull(s2.captainName)
    }

    // POST /orders request encoding — OrderCreateSchema (zod) field names must match exactly
    @Test
    fun order_create_request_encodes_server_shape() {
        val body = OrderCreateRequest(
            id = "2f0c8a7e-1111-4bbb-8ccc-000000000001",
            pnr = "8492000001",
            coach = "B3",
            berth = 12,
            diners = 2,
            items = listOf(OrderCreateRequest.CreateItem("3c2a1b0d-2222-4ccc-9ddd-000000000002", 2)),
            createdAt = "2026-11-13T10:00:00.000Z",
        )
        val encoded = json.encodeToString(OrderCreateRequest.serializer(), body)
        assertTrue(encoded.contains("\"paymentMode\":\"COD\""))
        assertTrue(encoded.contains("\"source\":\"WIFI\""))
        assertTrue(encoded.contains("\"itemId\":\"3c2a1b0d"))
        assertTrue(encoded.contains("\"qty\":2"))
        // no prices ever leave the client
        assertTrue(!encoded.contains("Price"))
        assertTrue(!encoded.contains("paise"))
    }
}
