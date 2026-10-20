package com.spendtracker.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountsTest {

    @Test
    fun bankKeyIgnoresCaseSpacingAndTrailingBank() {
        assertEquals("icici", Accounts.bankKey("ICICI Bank"))
        assertEquals("icici", Accounts.bankKey("  icici   BANK. "))
        assertEquals("icici", Accounts.bankKey("ICICI"))
        assertEquals("idfc first", Accounts.bankKey("IDFC FIRST Bank"))
        assertEquals(Accounts.bankKey("SBI"), Accounts.bankKey("State Bank of India"))
        assertEquals(Accounts.bankKey("Kotak Bank"), Accounts.bankKey("Kotak Mahindra Bank"))
        assertNull(Accounts.bankKey(null))
        assertNull(Accounts.bankKey(" "))
    }

    @Test
    fun similarlyNamedBanksStayDifferent() {
        assertNotEquals(Accounts.bankKey("Indian Bank"), Accounts.bankKey("Bank of India"))
        assertNotEquals(Accounts.bankKey("Indian Bank"), Accounts.bankKey("Indian Overseas Bank"))
        assertNotEquals(Accounts.bankKey("Central Bank"), Accounts.bankKey("Bank of India"))
    }

    @Test
    fun tailsAreDigitsAndMatchBySuffix() {
        assertEquals("3485", Accounts.normalizeTail("XX3485"))
        assertNull(Accounts.normalizeTail("XX"))
        assertNull(Accounts.normalizeTail(null))
        assertTrue(Accounts.tailsMatch("85", "3485"))
        assertTrue(Accounts.tailsMatch("3485", "485"))
        assertFalse(Accounts.tailsMatch("3485", "1285"))
    }
}
