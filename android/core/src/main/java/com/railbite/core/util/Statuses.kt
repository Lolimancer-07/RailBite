package com.railbite.core.util

/**
 * Order status handling for the prototype READY track:
 * PLACED -> ASSIGNED -> ACCEPTED -> PACKING -> OUT_FOR_DELIVERY -> DELIVERED
 * (+ CANCELLED / EXPIRED exits).
 */
object Statuses {
    const val BROADCASTING = "BROADCASTING"
    const val PLACED = "PLACED"
    const val ASSIGNED = "ASSIGNED"
    const val ACCEPTED = "ACCEPTED"
    const val PACKING = "PACKING"
    const val COOKING = "COOKING"
    const val READY = "READY"
    const val OUT_FOR_DELIVERY = "OUT_FOR_DELIVERY"
    const val DELIVERED = "DELIVERED"
    const val CANCELLED = "CANCELLED"
    const val EXPIRED = "EXPIRED"

    /** Step index for the 5-step passenger timeline; -1 for terminal failure states. */
    fun timelineStep(status: String): Int = when (status) {
        BROADCASTING -> 0
        PLACED -> 0
        ASSIGNED -> 0
        ACCEPTED -> 1
        PACKING -> 2
        OUT_FOR_DELIVERY -> 3
        DELIVERED -> 4
        else -> -1 // CANCELLED, EXPIRED, COOKING, READY
    }

    fun isTerminal(status: String): Boolean =
        status == DELIVERED || status == CANCELLED || status == EXPIRED

    fun isFailed(status: String): Boolean = status == CANCELLED || status == EXPIRED

    /** Short human label used on cards. */
    fun label(status: String): String = when (status) {
        BROADCASTING -> "Searching via BLE mesh"
        PLACED -> "Order placed"
        ASSIGNED -> "Finding picker"
        ACCEPTED -> "Picker accepted"
        PACKING -> "Packing"
        COOKING -> "Cooking"
        READY -> "Ready"
        OUT_FOR_DELIVERY -> "Out for delivery"
        DELIVERED -> "Delivered"
        CANCELLED -> "Cancelled"
        EXPIRED -> "Expired"
        else -> status
    }
}
