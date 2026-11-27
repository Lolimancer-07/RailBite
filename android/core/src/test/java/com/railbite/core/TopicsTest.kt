package com.railbite.core

import com.railbite.core.mqtt.RailBiteTopics
import org.junit.Assert.assertEquals
import org.junit.Test

class TopicsTest {

    @Test
    fun order_status_topic_matches_shared_topics_ts() {
        assertEquals(
            "rb/train/12345/orders/2f0c8a7e-1111-4bbb-8ccc-000000000001/status",
            RailBiteTopics.orderStatus("12345", "2f0c8a7e-1111-4bbb-8ccc-000000000001"),
        )
    }

    @Test
    fun order_chat_topic_matches_shared_topics_ts() {
        assertEquals(
            "rb/train/12345/orders/2f0c8a7e-1111-4bbb-8ccc-000000000001/chat",
            RailBiteTopics.orderChat("12345", "2f0c8a7e-1111-4bbb-8ccc-000000000001"),
        )
    }

    @Test
    fun captain_alerts_topic_matches_shared_topics_ts() {
        assertEquals(
            "rb/train/12345/captain/c1/alerts",
            RailBiteTopics.captainAlerts("12345", "c1"),
        )
    }

    @Test
    fun default_train_number_is_valid_5_digits() {
        assertEquals("12345", RailBiteTopics.DEFAULT_TRAIN_NUMBER)
    }

    @Test
    fun topics_normalize_arbitrary_unseeded_train_numbers() {
        assertEquals(
            "rb/train/88412/captain/c1/alerts",
            RailBiteTopics.captainAlerts(" 88412 ", "c1"),
        )
        assertEquals(
            "rb/train/EXP999/orders/ord-1/status",
            RailBiteTopics.orderStatus(" exp 999 ", "ord-1"),
        )
    }
}
