package com.spendtracker.model

import com.spendtracker.data.CategoryBudget

/** How a category's spending this period compares to the limit the user set for it. */
data class BudgetStatus(
    val category: Category,
    val budgetPaise: Long,
    val spentPaise: Long,
) {
    val remainingPaise: Long get() = budgetPaise - spentPaise
    val overBudget: Boolean get() = spentPaise > budgetPaise

    /** Share of the budget spent, 0f..1f+; not clamped so callers can detect overflow past 100%. */
    val share: Float get() = if (budgetPaise <= 0) 0f else spentPaise.toFloat() / budgetPaise.toFloat()
}

object Budgets {

    /** Matches each budget against what was actually spent in [categoryTotals], worst overspend first. */
    fun statuses(budgets: List<CategoryBudget>, categoryTotals: List<CategoryTotal>): List<BudgetStatus> {
        val spentByCategory = categoryTotals.associate { it.category to it.amountPaise }
        return budgets
            .map { BudgetStatus(it.category, it.amountPaise, spentByCategory[it.category] ?: 0L) }
            .sortedWith(compareByDescending<BudgetStatus> { it.share }.thenBy { it.category.label })
    }
}
