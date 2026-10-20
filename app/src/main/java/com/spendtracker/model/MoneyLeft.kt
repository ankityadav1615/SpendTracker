package com.spendtracker.model

/**
 * The answer to "how much is left?" for one period, measured against a monthly budget when one applies,
 * otherwise against the money that came in. Without either, only what went out is known.
 */
data class MoneyLeft(val basis: Basis, val basePaise: Long, val spentPaise: Long) {

    enum class Basis { BUDGET, INCOME, SPENT_ONLY }

    val leftPaise: Long get() = basePaise - spentPaise
    val overspent: Boolean get() = basis != Basis.SPENT_ONLY && leftPaise < 0

    /**
     * Share of the strip each category takes. The strip spans the base, or what went out when that is
     * larger (overspent), so the shares never add up to more than 1.
     */
    fun shareOf(amountPaise: Long): Float {
        val whole = maxOf(basePaise, spentPaise)
        return if (whole <= 0) 0f else (amountPaise.toDouble() / whole).toFloat()
    }

    /** Share of the strip that is still left; 0 when nothing is left. */
    val leftShare: Float
        get() = if (basis == Basis.SPENT_ONLY || leftPaise <= 0) 0f else shareOf(leftPaise)

    companion object {
        /** [budgetPaise] is only passed for periods a monthly budget fits (a month or a pay cycle). */
        fun of(summary: Summary, budgetPaise: Long?): MoneyLeft = when {
            budgetPaise != null && budgetPaise > 0 -> MoneyLeft(Basis.BUDGET, budgetPaise, summary.spentPaise)
            summary.incomePaise > 0 -> MoneyLeft(Basis.INCOME, summary.incomePaise, summary.spentPaise)
            else -> MoneyLeft(Basis.SPENT_ONLY, 0, summary.spentPaise)
        }
    }
}
