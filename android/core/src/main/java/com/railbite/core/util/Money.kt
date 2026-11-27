package com.railbite.core.util

import java.text.DecimalFormat

/**
 * Money helpers. The server stores everything in integer paise (1 INR = 100 paise);
 * the UI divides by 100. Never floats in models.
 */
object Money {

    /** 1500 -> "₹15", 1550 -> "₹15.50" */
    fun rupees(paise: Int): String {
        val whole = paise / 100
        val frac = paise % 100
        return if (frac == 0) "₹$whole" else "₹$whole.${DecimalFormat("00").format(frac)}"
    }

    fun rupeesLong(paise: Int): String {
        val whole = paise / 100
        val frac = paise % 100
        return "₹" + DecimalFormat("#,##0.##").format(whole + frac / 100.0)
    }

    /** 1500 -> "15.00" — plain decimal for machine-facing strings (UPI `am` parameter). */
    fun rupeesPlain(paise: Int): String {
        val whole = paise / 100
        val frac = paise % 100
        return "$whole.${DecimalFormat("00").format(frac)}"
    }
}

/** Simple validation shared by both apps. */
object Validators {
    private val TRAIN_NUMBER_REGEX = Regex("""^[A-Z0-9]{1,10}$""")
    private val PNR_REGEX = Regex("""^\d{10}$""")
    private val COACH_REGEX = Regex("""^[A-Za-z]{1,2}\d{1,2}$""")

    /** Normalizes any train identifier (`trim`, strip spaces, uppercase) identically to `@railbite/shared`. */
    fun normalizeTrainNumber(raw: String): String =
        raw.filterNot(Char::isWhitespace).uppercase()

    fun isValidTrainNumber(trainNumber: String): Boolean =
        TRAIN_NUMBER_REGEX.matches(normalizeTrainNumber(trainNumber))

    fun isValidPnr(pnr: String): Boolean = PNR_REGEX.matches(pnr.trim())
    fun isValidCoach(coach: String): Boolean = COACH_REGEX.matches(coach.trim())
    fun isValidBerth(berth: Int): Boolean = berth in 1..100
    fun isValidDiners(diners: Int): Boolean = diners in 1..8
}
