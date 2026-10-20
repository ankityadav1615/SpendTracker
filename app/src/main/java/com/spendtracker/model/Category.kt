package com.spendtracker.model

enum class TransactionType { DEBIT, CREDIT }

/** How a category affects the salary summary. */
enum class CategoryKind {
    SPEND,
    INCOME,

    /** Money moving between your own accounts/cards; counting it would double count. */
    EXCLUDED,
}

enum class Category(val label: String, val kind: CategoryKind, val color: Long) {
    EMI("EMI & Loans", CategoryKind.SPEND, 0xFFE53935),
    RENT("Rent", CategoryKind.SPEND, 0xFF8E24AA),
    GROCERIES("Groceries", CategoryKind.SPEND, 0xFF43A047),
    FUEL("Fuel", CategoryKind.SPEND, 0xFFFB8C00),
    FOOD("Food & Dining", CategoryKind.SPEND, 0xFFF4511E),
    BILLS("Bills & Recharges", CategoryKind.SPEND, 0xFF1E88E5),
    SHOPPING("Shopping", CategoryKind.SPEND, 0xFFD81B60),
    TRAVEL("Travel & Transport", CategoryKind.SPEND, 0xFF00ACC1),
    HEALTH("Health", CategoryKind.SPEND, 0xFF00897B),
    ENTERTAINMENT("Entertainment", CategoryKind.SPEND, 0xFF5E35B1),
    INVESTMENTS("Savings & Investments", CategoryKind.SPEND, 0xFF3949AB),
    CASH("Cash withdrawal", CategoryKind.SPEND, 0xFF6D4C41),
    TRANSFERS("Sent to people", CategoryKind.SPEND, 0xFF546E7A),
    DAILY("Daily & Misc", CategoryKind.SPEND, 0xFF757575),
    SALARY("Salary", CategoryKind.INCOME, 0xFF2E7D32),
    REFUND("Refunds & Cashback", CategoryKind.INCOME, 0xFF7CB342),
    OTHER_INCOME("Other income", CategoryKind.INCOME, 0xFF26A69A),
    SELF_TRANSFER("Own account transfer", CategoryKind.EXCLUDED, 0xFF90A4AE),
    CARD_BILL("Credit card bill", CategoryKind.EXCLUDED, 0xFF78909C);

    companion object {
        /** Categories that make sense for a transaction of the given direction. */
        fun choicesFor(type: TransactionType): List<Category> {
            val wanted = if (type == TransactionType.DEBIT) CategoryKind.SPEND else CategoryKind.INCOME
            return entries.filter { it.kind == wanted || it.kind == CategoryKind.EXCLUDED }
        }

        fun fromName(name: String): Category = entries.firstOrNull { it.name == name } ?: DAILY
    }
}
