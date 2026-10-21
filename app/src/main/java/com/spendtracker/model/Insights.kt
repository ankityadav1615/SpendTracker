package com.spendtracker.model

/** A dismissible nudge surfaced on Home, built from category spending, budgets, and recurring payments. */
sealed interface Insight {
    /** Stable across rebuilds so the UI can remember a dismissed nudge until its underlying cause changes. */
    val id: String
    val message: String
}

data class CategorySpike(
    override val id: String,
    override val message: String,
    val category: Category,
    val percent: Int,
) : Insight

data class OverBudget(
    override val id: String,
    override val message: String,
    val category: Category,
) : Insight

data class UpcomingBill(
    override val id: String,
    override val message: String,
    val payment: RecurringPayment,
) : Insight

object Insights {
    private const val SPIKE_THRESHOLD_PERCENT = 30
    private const val UPCOMING_WINDOW_DAYS = 3L
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /**
     * Builds nudges, most actionable first: bills already due or due within [UPCOMING_WINDOW_DAYS], then
     * categories over their budget, then categories spending noticeably more than last period.
     */
    fun build(
        reportLines: List<ReportLine>,
        budgetStatuses: List<BudgetStatus>,
        recurring: List<RecurringPayment>,
        nowMillis: Long,
    ): List<Insight> {
        val upcoming = recurring
            .filter { it.type == TransactionType.DEBIT && it.nextDueMillis <= nowMillis + UPCOMING_WINDOW_DAYS * DAY_MILLIS }
            .map {
                UpcomingBill(
                    id = "due:${it.counterpartyKey}",
                    message = "${it.displayName} payment of ${Money.format(it.lastAmountPaise)} is due soon",
                    payment = it,
                )
            }

        val overBudget = budgetStatuses
            .filter { it.overBudget }
            .map {
                OverBudget(
                    id = "budget:${it.category.name}",
                    message = "${it.category.label} is over budget by ${Money.format(it.spentPaise - it.budgetPaise)}",
                    category = it.category,
                )
            }

        val spikes = reportLines.mapNotNull { line ->
            val change = line.change
            if (change is Change.Up && change.percent >= SPIKE_THRESHOLD_PERCENT) {
                CategorySpike(
                    id = "spike:${line.category.name}",
                    message = "${line.category.label} is up ${change.percent}% vs last period",
                    category = line.category,
                    percent = change.percent,
                )
            } else {
                null
            }
        }

        return upcoming + overBudget + spikes
    }
}
