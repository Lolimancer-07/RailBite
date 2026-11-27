package com.railbite.core.mesh

/**
 * Computes dispatch arbitration delay for incoming `ORDER_NEW (0x11)` BLE frames so the
 * closest on-zone pantry picker rings immediately (`0ms`) while distant or off-zone pickers
 * back off deterministically and stand down if another picker broadcasts `STATUS_ACK ACCEPTED`.
 */
object BleNearestArbitrator {

    /** Parses stored picker zone strings (e.g. `"B1, B2"` or `"B1-B2"`) into uppercase coach IDs. */
    fun parseZoneCoaches(zoneRaw: String): Set<String> =
        zoneRaw
            .split(',', ' ', ';', '/', '-')
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .toSet()

    /** Returns true when [orderCoach] falls inside [zoneRaw]. */
    fun isInZone(orderCoach: String, zoneRaw: String): Boolean {
        val clean = orderCoach.trim().uppercase()
        if (clean.isEmpty()) return false
        return clean in parseZoneCoaches(zoneRaw)
    }

    /** Weak-signal RSSI penalty applied only to out-of-zone pickers. */
    fun rssiPenaltyMs(rssiDbm: Int): Long = when {
        rssiDbm >= -65 -> 0L
        rssiDbm >= -85 -> 500L
        else -> 1_200L
    }

    /**
     * Exact arbitration delay before waking the picker alert UI:
     * - `inZone && hopCount == 0` -> `0ms`
     * - `inZone && hopCount > 0` -> `1000ms * hopCount`
     * - `outOfZone` -> `3000ms + (1000ms * hopCount) + rssiPenaltyMs(rssiDbm)`
     */
    fun delayMillis(
        orderCoach: String,
        staffZone: String,
        hopCount: Int,
        rssiDbm: Int,
    ): Long {
        val hops = hopCount.coerceAtLeast(0).toLong()
        val inZone = isInZone(orderCoach, staffZone)
        return when {
            inZone && hops == 0L -> 0L
            inZone -> 1_000L * hops
            else -> 3_000L + (1_000L * hops) + rssiPenaltyMs(rssiDbm)
        }
    }
}

