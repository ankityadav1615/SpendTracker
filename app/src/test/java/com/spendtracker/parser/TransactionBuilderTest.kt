package com.spendtracker.parser

import com.spendtracker.model.Category
import com.spendtracker.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionBuilderTest {

    private val imps = RawSms(
        sender = "VM-HDFCBK",
        body = "Rs 18000.00 debited from A/c XX1234 for IMPS to RAJESH GUPTA on 01-10-24. IMPS Ref no 427412345678",
        timestamp = 1_727_740_800_000,
    )

    @Test
    fun buildsTransactionWithDefaultCategory() {
        val txn = TransactionBuilder.build(imps, emptyMap())
        assertNotNull(txn)
        txn!!
        assertEquals(TransactionType.DEBIT, txn.type)
        assertEquals(18_000_00L, txn.amountPaise)
        assertEquals("Rajesh Gupta", txn.counterparty)
        assertEquals("rajesh gupta", txn.counterpartyKey)
        assertEquals(Category.TRANSFERS, txn.category)
        assertEquals(imps.timestamp, txn.timestamp)
        assertEquals("ref:DEBIT:427412345678:1800000", txn.dedupeKey)
    }

    @Test
    fun rememberedCorrectionWins() {
        val rules = mapOf(TransactionBuilder.ruleKey(TransactionType.DEBIT, "rajesh gupta") to Category.RENT)
        assertEquals(Category.RENT, TransactionBuilder.build(imps, rules)!!.category)
    }

    @Test
    fun correctionIsPerDirection() {
        val rules = mapOf(TransactionBuilder.ruleKey(TransactionType.CREDIT, "rajesh gupta") to Category.SALARY)
        assertEquals(Category.TRANSFERS, TransactionBuilder.build(imps, rules)!!.category)
    }

    @Test
    fun nonTransactionSmsIsSkipped() {
        assertNull(TransactionBuilder.build(RawSms("VM-HDFCBK", "Your OTP is 123456", 0), emptyMap()))
    }

    @Test
    fun counterpartyKeyPrefersVpaAndIgnoresDates() {
        assertEquals("netflix@hdfcbank", TransactionBuilder.counterpartyKey("Netflix", "Netflix@HDFCBank"))
        assertEquals("salary acme corp", TransactionBuilder.counterpartyKey("Salary SEP24 Acme Corp", null))
        assertEquals(
            TransactionBuilder.counterpartyKey("Salary SEP24 Acme Corp", null),
            TransactionBuilder.counterpartyKey("SALARY OCT24 ACME CORP", null),
        )
        assertEquals("", TransactionBuilder.counterpartyKey(null, null))
    }

    @Test
    fun dedupeKeyUsesReferenceOrNormalizedText() {
        assertEquals("ref:CREDIT:123456:500", TransactionBuilder.dedupeKey(TransactionType.CREDIT, "123456", 500, "x"))
        val a = TransactionBuilder.dedupeKey(TransactionType.DEBIT, null, 500, "Rs 5 debited\n from A/c XX1")
        val b = TransactionBuilder.dedupeKey(TransactionType.DEBIT, null, 500, "rs 5 debited from a/c xx1 ")
        val c = TransactionBuilder.dedupeKey(TransactionType.DEBIT, null, 500, "Rs 6 debited from A/c XX1")
        assertEquals(a, b)
        assertNotEquals(a, c)
    }
}
