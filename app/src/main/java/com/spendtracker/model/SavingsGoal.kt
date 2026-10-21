package com.spendtracker.model

import com.spendtracker.data.Transaction

/** Progress of a user-set savings target, since it was created. */
data class SavingsGoalStatus(
    val name: String,
    val targetPaise: Long,
    val savedPaise: Long,
    val deadlineMillis: Long?,
) {
    val percent: Int get() = if (targetPaise <= 0) 0 else ((savedPaise * 100) / targetPaise).toInt().coerceIn(0, 100)
    val reached: Boolean get() = savedPaise >= targetPaise
    val remainingPaise: Long get() = (targetPaise - savedPaise).coerceAtLeast(0)
}

object SavingsGoals {
    /**
     * Progress toward a goal is the money moved into [Category.INVESTMENTS] since [startedAtMillis] (when
     * the goal was set); it is not split across multiple goals, so running more than one goal at a time
     * double counts the same savings transactions.
     */
    fun status(
        name: String,
        targetPaise: Long,
        deadlineMillis: Long?,
        startedAtMillis: Long,
        transactions: List<Transaction>,
    ): SavingsGoalStatus {
        val saved = transactions
            .filter {
                !it.ignored && it.type == TransactionType.DEBIT && it.category == Category.INVESTMENTS &&
                    it.timestamp >= startedAtMillis
            }
            .sumOf { it.amountPaise }
        return SavingsGoalStatus(name, targetPaise, saved, deadlineMillis)
    }
}
