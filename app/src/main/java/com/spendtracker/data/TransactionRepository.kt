package com.spendtracker.data

import androidx.room.withTransaction
import com.spendtracker.model.Category
import com.spendtracker.parser.RawSms
import com.spendtracker.parser.TransactionBuilder
import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val db: AppDatabase) {

    private val transactionDao = db.transactionDao()
    private val ruleDao = db.categoryRuleDao()

    val transactions: Flow<List<Transaction>> = transactionDao.observeAll()

    /** Parses the messages and stores the transactions. Returns how many new transactions were added. */
    suspend fun ingest(messages: List<RawSms>): Int {
        if (messages.isEmpty()) return 0
        val rules = ruleDao.getAll().associate { it.key to it.category }
        val parsed = messages.mapNotNull { TransactionBuilder.build(it, rules) }
        if (parsed.isEmpty()) return 0
        return transactionDao.insertAll(parsed).count { it != -1L }
    }

    /**
     * Changes the category of [transaction]. With [applyToSimilar] the choice is remembered for the
     * payee, re-categorizing their past transactions and every future one.
     */
    suspend fun updateCategory(transaction: Transaction, category: Category, applyToSimilar: Boolean) {
        val key = transaction.counterpartyKey
        if (!applyToSimilar || key.isEmpty()) {
            transactionDao.updateCategory(transaction.id, category)
            return
        }
        db.withTransaction {
            ruleDao.upsert(
                CategoryRule(TransactionBuilder.ruleKey(transaction.type, key), category, System.currentTimeMillis()),
            )
            transactionDao.updateCategoryForCounterparty(transaction.type, key, category)
        }
    }

    suspend fun setIgnored(transaction: Transaction, ignored: Boolean) =
        transactionDao.setIgnored(transaction.id, ignored)
}
