package com.spendtracker.model

import com.spendtracker.data.CategoryRule
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryRulesTest {

    @Test
    fun derivesPayeeNameFromTheNewestMatchingTransaction() {
        val txns = listOf(
            testTxn(TransactionType.DEBIT, 200, Category.FOOD, timestamp = 100, counterparty = "Swiggy").copy(counterpartyKey = "swiggy"),
            testTxn(TransactionType.DEBIT, 300, Category.FOOD, timestamp = 200, counterparty = "Swiggy Ltd").copy(counterpartyKey = "swiggy"),
        )
        val rules = listOf(CategoryRule("DEBIT:swiggy", Category.FOOD, updatedAt = 500))

        val infos = CategoryRules.infos(rules, txns)

        val info = infos.single()
        assertEquals("Swiggy Ltd", info.displayName)
        assertEquals(TransactionType.DEBIT, info.type)
        assertEquals(Category.FOOD, info.category)
    }

    @Test
    fun fallsBackToUnknownPayeeWhenNoTransactionMatches() {
        val infos = CategoryRules.infos(listOf(CategoryRule("CREDIT:ghost", Category.OTHER_INCOME, updatedAt = 1)), emptyList())

        val info = infos.single()
        assertEquals("Unknown payee", info.displayName)
        assertEquals(TransactionType.CREDIT, info.type)
    }

    @Test
    fun sortsNewestRuleFirst() {
        val rules = listOf(
            CategoryRule("DEBIT:a", Category.FOOD, updatedAt = 1),
            CategoryRule("DEBIT:b", Category.FUEL, updatedAt = 5),
        )

        val infos = CategoryRules.infos(rules, emptyList())

        assertEquals(listOf("DEBIT:b", "DEBIT:a"), infos.map { it.key })
    }
}
