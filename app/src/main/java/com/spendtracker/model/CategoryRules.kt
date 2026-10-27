package com.spendtracker.model

import com.spendtracker.data.CategoryRule
import com.spendtracker.data.Transaction
import com.spendtracker.parser.TransactionBuilder

/** A remembered category rule with a human-readable payee, for the rule management screen. */
data class CategoryRuleInfo(
    val key: String,
    val type: TransactionType,
    val category: Category,
    val displayName: String,
    val updatedAt: Long,
    val customCategory: String? = null,
) {
    val label: String get() = customCategory ?: category.label
}

object CategoryRules {

    /** Pairs each [rules] row with the payee name from the newest matching transaction, newest rule first. */
    fun infos(rules: List<CategoryRule>, transactions: List<Transaction>): List<CategoryRuleInfo> {
        val latestByKey = transactions
            .filter { it.counterpartyKey.isNotEmpty() }
            .groupBy { TransactionBuilder.ruleKey(it.type, it.counterpartyKey) }
            .mapValues { (_, group) -> group.maxBy { it.timestamp } }
        return rules
            .map { rule ->
                val sample = latestByKey[rule.key]
                CategoryRuleInfo(
                    key = rule.key,
                    type = sample?.type ?: TransactionType.valueOf(rule.key.substringBefore(":")),
                    category = rule.category,
                    displayName = sample?.counterparty?.takeIf { it.isNotBlank() } ?: sample?.vpa ?: "Unknown payee",
                    updatedAt = rule.updatedAt,
                    customCategory = rule.customCategory?.takeIf { it.isNotBlank() },
                )
            }
            .sortedByDescending { it.updatedAt }
    }
}
