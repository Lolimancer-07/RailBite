package com.railbite.core.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * UPI payment helper for UPI_QUEUED orders: builds a standard `upi://pay` deep link and
 * launches whichever UPI app (GPay/PhonePe/Paytm…) is installed. Nothing here verifies the
 * payment — the passenger confirms it in-app after delivery (same loop as COD, minus cash).
 */
object Upi {

    /** Demo merchant VPA (the pantry's payment address; a real deploy swaps this). */
    const val VPA = "railbite@upi"
    const val PAYEE_NAME = "RailBite Pantry"

    /** Standard UPI deep link; `tn` note carries the order id for the passenger's statement. */
    fun payUri(orderId: String, totalPaise: Int): String =
        "upi://pay?pa=$VPA&pn=${Uri.encode(PAYEE_NAME)}" +
            "&am=${Money.rupeesPlain(totalPaise)}&cu=INR" +
            "&tn=${Uri.encode("RailBite ${orderId.take(8)}")}"

    /** Launches a UPI app. False when no UPI app is installed (card falls back to instructions). */
    fun launch(context: Context, orderId: String, totalPaise: Int): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(payUri(orderId, totalPaise)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
