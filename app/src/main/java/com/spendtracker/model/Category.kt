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
    EMI("EMI & loans", CategoryKind.SPEND, 0xFF8A5F86),
    RENT("Rent", CategoryKind.SPEND, 0xFFB9773F),
    GROCERIES("Groceries", CategoryKind.SPEND, 0xFF6E8F55),
    FUEL("Fuel", CategoryKind.SPEND, 0xFF4F7896),
    FOOD("Food & dining", CategoryKind.SPEND, 0xFFC58A2E),
    BILLS("Bills & recharges", CategoryKind.SPEND, 0xFF8C6A4F),
    SHOPPING("Shopping", CategoryKind.SPEND, 0xFFB5597A),
    TRAVEL("Travel & transport", CategoryKind.SPEND, 0xFF4D8A8A),
    HEALTH("Health", CategoryKind.SPEND, 0xFFB5524A),
    ENTERTAINMENT("Entertainment", CategoryKind.SPEND, 0xFF6B67A8),
    INVESTMENTS("Savings & investments", CategoryKind.SPEND, 0xFF3F7F6E),
    CASH("Cash withdrawal", CategoryKind.SPEND, 0xFF7A8B4F),
    TRANSFERS("Sent to people", CategoryKind.SPEND, 0xFF7D7D7D),
    DAILY("Daily spends", CategoryKind.SPEND, 0xFF8F7D63),
    SALARY("Salary", CategoryKind.INCOME, 0xFF2F7A4B),
    REFUND("Refunds & cashback", CategoryKind.INCOME, 0xFF5E9A6B),
    OTHER_INCOME("Money received", CategoryKind.INCOME, 0xFF3F8C77),
    SELF_TRANSFER("Own account transfer", CategoryKind.EXCLUDED, 0xFF8A8A8A),
    CARD_BILL("Credit card bill", CategoryKind.EXCLUDED, 0xFF6F7A86);

    companion object {
        /** Categories that make sense for a transaction of the given direction. */
        fun choicesFor(type: TransactionType): List<Category> {
            val wanted = if (type == TransactionType.DEBIT) CategoryKind.SPEND else CategoryKind.INCOME
            return entries.filter { it.kind == wanted || it.kind == CategoryKind.EXCLUDED }
        }

        fun fromName(name: String): Category = entries.firstOrNull { it.name == name } ?: DAILY
    }
}
