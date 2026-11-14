package com.railbite.core.data

import com.railbite.core.data.RailBiteSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Small helper exposing the saved seat (PNR / coach / berth / diners) as a combined flow,
 * used by the header chip in both apps.
 */
class SeatSnapshot(private val settings: RailBiteSettings) {

    data class Seat(val pnr: String, val coach: String, val berth: Int, val diners: Int) {
        val chip: String get() = "Coach $coach · Seat $berth"
        val valid: Boolean get() = coach.isNotBlank() && berth > 0
    }

    val seat: Flow<Seat?> = combine(
        settings.pnr, settings.coach, settings.berth, settings.diners,
    ) { pnr, coach, berth, diners ->
        if (coach.isBlank() || berth == 0) null else Seat(pnr, coach, berth, diners)
    }
}
