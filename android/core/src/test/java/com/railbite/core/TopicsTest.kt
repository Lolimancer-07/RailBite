package com.railbite.core

import com.railbite.core.mqtt.RailBiteTopics
import org.junit.Assert.assertEquals
import org.junit.Test

class TopicsTest {

    @Test
    fun order_status_topic_matches_shared_topics_ts() {
        assertEquals(
            "rb/TRIP_001/orders/2f0c8a7e-1111-4bbb-8ccc-000000000001/status",
            RailBiteTopics.orderStatus("TRIP_001", "2f0c8a7e-1111-4bbb-8ccc-000000000001"),
        )
    }

    @Test
    fun order_chat_topic_matches_shared_topics_ts() {
        assertEquals(
            "rb/TRIP_001/orders/2f0c8a7e-1111-4bbb-8ccc-000000000001/chat",
            RailBiteTopics.orderChat("TRIP_001", "2f0c8a7e-1111-4bbb-8ccc-000000000001"),
        )
    }

    @Test
    fun captain_alerts_topic_matches_shared_topics_ts() {
        assertEquals(
            "rb/TRIP_001/captain/c1/alerts",
            RailBiteTopics.captainAlerts("TRIP_001", "c1"),
        )
    }

    @Test
    fun default_trip_id_is_used_when_health_unreachable() {
        assertEquals("TRIP_001", RailBiteTopics.DEFAULT_TRIP_ID)
    }
}
