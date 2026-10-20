package com.spendtracker.model

import com.spendtracker.data.Transaction

data class CategoryTotal(val category: Category, val amountPaise: Long, val count: Int)

data class AccountBalance(val bank: String?, val accountTail: String?, val balancePaise: Long, val timestamp: Long)

data class Summary(
    val salaryPaise: Long,
    val otherIncomePaise: Long,
    val spentPaise: Long,
    /** Own-account transfers and credit card bill payments: shown, but not counted as spending. */
    val excludedPaise: Long,
    /** Spending per category, largest first. */
    val byCategory: List<CategoryTotal>,
    val count: Int,
) {
    val incomePaise: Long get() = salaryPaise + otherIncomePaise
    val leftPaise: Long get() = incomePaise - spentPaise

    companion object {
        fun of(transactions: List<Transaction>): Summary {
            var salary = 0L
            var otherIncome = 0L
            var spent = 0L
            var excluded = 0L
            val perCategory = linkedMapOf<Category, CategoryTotal>()
            for (txn in transactions) {
                when {
                    txn.category.kind == CategoryKind.EXCLUDED -> excluded += txn.amountPaise
                    txn.type == TransactionType.CREDIT ->
                        if (txn.category == Category.SALARY) salary += txn.amountPaise else otherIncome += txn.amountPaise
                    else -> {
                        spent += txn.amountPaise
                        val current = perCategory[txn.category] ?: CategoryTotal(txn.category, 0, 0)
                        perCategory[txn.category] = current.copy(
                            amountPaise = current.amountPaise + txn.amountPaise,
                            count = current.count + 1,
                        )
                    }
                }
            }
            return Summary(
                salaryPaise = salary,
                otherIncomePaise = otherIncome,
                spentPaise = spent,
                excludedPaise = excluded,
                byCategory = perCategory.values.sortedByDescending { it.amountPaise },
                count = transactions.size,
            )
        }

        /** The most recent balance each account reported in its SMS. */
        fun latestBalances(transactions: List<Transaction>): List<AccountBalance> =
            transactions
                .filter { it.balancePaise != null }
                .groupBy { it.bank to it.accountTail }
                .map { (_, txns) ->
                    val latest = txns.maxBy { it.timestamp }
                    AccountBalance(latest.bank, latest.accountTail, latest.balancePaise!!, latest.timestamp)
                }
                .sortedByDescending { it.timestamp }
    }
}
