package com.railbite.core

import com.railbite.core.util.Money
import com.railbite.core.util.Validators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyAndValidatorsTest {

    @Test
    fun rupees_whole_rupees_have_no_decimals() {
        assertEquals("₹15", Money.rupees(1500))
        assertEquals("₹0", Money.rupees(0))
    }

    @Test
    fun rupees_shows_paise_when_present() {
        assertEquals("₹15.50", Money.rupees(1550))
        assertEquals("₹0.05", Money.rupees(5))
        assertEquals("₹399", Money.rupees(39900))
    }

    @Test
    fun validators_train_number_requires_5_digits() {
        assertTrue(Validators.isValidTrainNumber("12345"))
        assertTrue(Validators.isValidTrainNumber("12423"))
        assertFalse(Validators.isValidTrainNumber("1234"))
        assertFalse(Validators.isValidTrainNumber("123456"))
        assertFalse(Validators.isValidTrainNumber("123AB"))
        assertFalse(Validators.isValidTrainNumber(""))
    }

    @Test
    fun validators_pnr() {
        assertTrue(Validators.isValidPnr("8492000001"))
        assertFalse(Validators.isValidPnr("849200000"))
        assertFalse(Validators.isValidPnr("84920a0001"))
        assertFalse(Validators.isValidPnr(""))
    }

    @Test
    fun validators_coach_accepts_one_or_two_letters_plus_digits() {
        assertTrue(Validators.isValidCoach("B3"))
        assertTrue(Validators.isValidCoach("SL7"))
        assertTrue(Validators.isValidCoach("b3")) // lower-case allowed before trim/uppercase
        assertFalse(Validators.isValidCoach("3B"))
        assertFalse(Validators.isValidCoach("B"))
        assertFalse(Validators.isValidCoach("B3A"))
    }

    @Test
    fun validators_berth_and_diners() {
        assertTrue(Validators.isValidBerth(1))
        assertTrue(Validators.isValidBerth(72))
        assertFalse(Validators.isValidBerth(0))
        assertFalse(Validators.isValidBerth(101))
        assertTrue(Validators.isValidDiners(1))
        assertFalse(Validators.isValidDiners(0))
        assertFalse(Validators.isValidDiners(9))
    }
}
