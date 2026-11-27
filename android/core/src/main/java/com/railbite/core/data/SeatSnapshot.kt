package com.railbite.core.data

import com.railbite.core.data.RailBiteSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Small helper exposing the saved seat (PNR / coach / berth / diners) as a combined flow,
 * used by the header chip in both apps.
 */
class SeatSnapshot(private val settings: RailBiteSettings) {

    data class Seat(
        val trainNumber: String,
        val pnr: String,
        val coach: String,
        val berth: Int,
        val diners: Int,
    ) {
        val chip: String
            get() = if (trainNumber.isNotBlank()) "$trainNumber • $coach-$berth" else "Coach $coach · Seat $berth"
        val valid: Boolean get() = com.railbite.core.util.Validators.isValidTrainNumber(trainNumber) && coach.isNotBlank() && berth > 0
    }

    val seat: Flow<Seat?> = combine(
        settings.trainNumber, settings.pnr, settings.coach, settings.berth, settings.diners,
    ) { trainNumber, pnr, coach, berth, diners ->
        if (coach.isBlank() || berth == 0) {
            null
        } else {
            Seat(
                trainNumber = com.railbite.core.util.Validators.normalizeTrainNumber(trainNumber),
                pnr = pnr,
                coach = coach,
                berth = berth,
                diners = diners,
            )
        }
    }
}
