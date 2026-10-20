package com.spendtracker.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {

    @Test
    fun indianGrouping() {
        assertEquals("₹0", Money.format(0))
        assertEquals("₹999", Money.format(999_00))
        assertEquals("₹1,000", Money.format(1_000_00))
        assertEquals("₹85,000", Money.format(85_000_00))
        assertEquals("₹1,02,346", Money.format(1_02_345_67))
        assertEquals("₹12,34,567", Money.format(12_34_567_00))
        assertEquals("₹1,23,45,678", Money.format(1_23_45_678_00))
    }

    @Test
    fun paiseAndNegatives() {
        assertEquals("₹1,02,345.67", Money.format(1_02_345_67, withPaise = true))
        assertEquals("₹500", Money.format(500_00, withPaise = true))
        assertEquals("₹250.05", Money.format(250_05, withPaise = true))
        assertEquals("-₹500", Money.format(-500_00))
    }
}
