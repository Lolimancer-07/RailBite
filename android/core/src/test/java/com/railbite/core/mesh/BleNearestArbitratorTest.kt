package com.railbite.core.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleNearestArbitratorTest {

    @Test
    fun `parseZoneCoaches handles comma hyphen and whitespace separators`() {
        assertEquals(setOf("B1", "B2"), BleNearestArbitrator.parseZoneCoaches("B1, B2"))
        assertEquals(setOf("S3", "S4"), BleNearestArbitrator.parseZoneCoaches("s3-s4"))
        assertTrue(BleNearestArbitrator.isInZone(" b2 ", "B1, B2"))
        assertFalse(BleNearestArbitrator.isInZone("B3", "B1, B2"))
    }

    @Test
    fun `direct in-zone order has zero delay`() {
        assertEquals(
            0L,
            BleNearestArbitrator.delayMillis(
                orderCoach = "B2",
                staffZone = "B1, B2",
                hopCount = 0,
                rssiDbm = -95,
            ),
        )
    }

    @Test
    fun `relayed in-zone order waits 1000ms per hop`() {
        assertEquals(
            2_000L,
            BleNearestArbitrator.delayMillis(
                orderCoach = "B1",
                staffZone = "B1, B2",
                hopCount = 2,
                rssiDbm = -72,
            ),
        )
    }

    @Test
    fun `out-of-zone order adds 3000ms base plus hop and rssi penalty`() {
        assertEquals(
            4_500L,
            BleNearestArbitrator.delayMillis(
                orderCoach = "B4",
                staffZone = "B1, B2",
                hopCount = 1,
                rssiDbm = -75, // medium RSSI -> 500ms
            ),
        )
        assertEquals(
            6_200L,
            BleNearestArbitrator.delayMillis(
                orderCoach = "S4",
                staffZone = "B1, B2",
                hopCount = 2,
                rssiDbm = -92, // weak RSSI -> 1200ms
            ),
        )
        assertEquals(
            3_000L,
            BleNearestArbitrator.delayMillis(
                orderCoach = "B3",
                staffZone = "B1, B2",
                hopCount = 0,
                rssiDbm = -58, // strong RSSI -> 0ms
            ),
        )
    }
}

