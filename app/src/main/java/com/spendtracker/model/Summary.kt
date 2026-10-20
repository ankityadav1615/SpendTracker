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
        /**
         * Totals for [transactions]. [cashCovered] (from [CashLedger.covered]) removes the part of each cash
         * withdrawal already counted through cash spends the user added; fully used withdrawals drop out.
         */
        fun of(transactions: List<Transaction>, cashCovered: Map<Long, Long> = emptyMap()): Summary {
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
                        val amount = CashLedger.countedPaise(txn, cashCovered)
                        if (amount <= 0) continue
                        spent += amount
                        val current = perCategory[txn.category] ?: CategoryTotal(txn.category, 0, 0)
                        perCategory[txn.category] = current.copy(
                            amountPaise = current.amountPaise + amount,
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

        /**
         * The most recent balance each account reported in its SMS, newest first. A bank does not always
         * identify an account the same way, so SMS are merged per account before taking the newest one:
         * - Banks are compared with [Accounts.bankKey]; different banks never merge.
         * - Within a bank, account numbers where one ends with the other ("485", "3485") are one account,
         *   shown with the longest number.
         * - SMS without an account number belong to the bank's account when the bank has exactly one.
         * - SMS that can't be tied to one account (no account number, or a short one that fits several
         *   accounts, at a bank with 2+ accounts) get a separate row, but only while it may still be
         *   current: once every account it could belong to has reported a newer balance, it is dropped.
         */
        fun latestBalances(transactions: List<Transaction>): List<AccountBalance> =
            transactions
                .filter { it.balancePaise != null }
                .groupBy { Accounts.bankKey(it.bank) }
                .values
                .flatMap(::bankBalances)
                .sortedByDescending { it.timestamp }

        private fun bankBalances(transactions: List<Transaction>): List<AccountBalance> {
            val bank = transactions.maxBy { it.timestamp }.bank
            val tails = transactions.mapNotNull { Accounts.normalizeTail(it.accountTail) }
                .distinct()
                .sortedByDescending { it.length }

            // Longest numbers first, so each account is represented by its longest known number.
            val accounts = mutableListOf<String>()
            val accountOfTail = mutableMapOf<String, String>()
            val candidatesOfTail = mutableMapOf<String, List<String>>()
            for (tail in tails) {
                val matches = accounts.filter { Accounts.tailsMatch(it, tail) }
                when (matches.size) {
                    0 -> {
                        accounts += tail
                        accountOfTail[tail] = tail
                    }
                    1 -> accountOfTail[tail] = matches.single()
                    else -> candidatesOfTail[tail] = matches
                }
            }

            val byAccount = linkedMapOf<String?, MutableList<Transaction>>()
            val unattributed = linkedMapOf<String?, MutableList<Transaction>>()
            for (txn in transactions) {
                val tail = Accounts.normalizeTail(txn.accountTail)
                val account = when {
                    tail != null -> accountOfTail[tail]
                    accounts.size <= 1 -> accounts.singleOrNull()
                    else -> null
                }
                if (account != null || (tail == null && accounts.isEmpty())) {
                    byAccount.getOrPut(account) { mutableListOf() } += txn
                } else {
                    unattributed.getOrPut(tail) { mutableListOf() } += txn
                }
            }

            val latestOfAccount = byAccount.mapValues { (_, txns) -> txns.maxBy { it.timestamp } }
            val balances = latestOfAccount.map { (account, latest) -> latest.toBalance(bank, account) }
            val stillCurrent = unattributed.mapNotNull { (tail, txns) ->
                val latest = txns.maxBy { it.timestamp }
                val candidates = tail?.let { candidatesOfTail.getValue(it) } ?: accounts
                val oldestCandidate = candidates.minOf { latestOfAccount.getValue(it).timestamp }
                if (latest.timestamp > oldestCandidate) latest.toBalance(bank, tail) else null
            }
            return balances + stillCurrent
        }

        private fun Transaction.toBalance(bank: String?, accountTail: String?) =
            AccountBalance(bank, accountTail, balancePaise!!, timestamp)
    }
}
