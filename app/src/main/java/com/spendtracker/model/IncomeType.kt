package com.spendtracker.model

/** Where the user's money mostly comes from; decides how time is split by default. */
enum class IncomeType(val label: String, val hint: String, val defaultMode: PeriodMode) {
    SALARY("A monthly salary", "See each pay cycle, payday to payday", PeriodMode.INCOME_CYCLE),
    BUSINESS("My own business", "See each calendar month", PeriodMode.MONTHLY),
    FREELANCE("Freelance or gig work", "See each calendar month", PeriodMode.MONTHLY),
    ALLOWANCE("Pocket money or allowance", "See each month against a budget", PeriodMode.MONTHLY),
    MIXED("A mix of things", "See each calendar month", PeriodMode.MONTHLY);

    companion object {
        fun fromName(name: String?): IncomeType? = entries.firstOrNull { it.name == name }
    }
}
