package com.spendtracker.data

import androidx.room.withTransaction
import com.spendtracker.model.Category
import com.spendtracker.model.ManualEntries
import com.spendtracker.model.ManualEntry
import com.spendtracker.parser.RawSms
import com.spendtracker.parser.TransactionBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.withLock

class TransactionRepository(private val db: AppDatabase, private val importState: SmsImportState) {

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

    /** Saves an entry the user added. Returns its id. */
    suspend fun addManual(entry: ManualEntry): Long =
        transactionDao.insert(ManualEntries.toTransaction(entry))

    suspend fun updateManual(existing: Transaction, entry: ManualEntry) {
        require(existing.isManual) { "Only entries the user added can be edited" }
        transactionDao.update(ManualEntries.toTransaction(entry, existing))
    }

    suspend fun deleteManual(transaction: Transaction) {
        require(transaction.isManual) { "Only entries the user added can be deleted" }
        transactionDao.deleteById(transaction.id)
    }

    /** Puts back an entry removed with [deleteManual], keeping its id. */
    suspend fun restore(transaction: Transaction) {
        transactionDao.insertAll(listOf(transaction))
    }

    /**
     * Permanently deletes all transactions (hidden ones included) and remembered categories, and forgets
     * how far the SMS inbox was imported, so the next import starts from scratch. A running inbox import
     * finishes first.
     */
    suspend fun deleteAllData() {
        importState.lock.withLock {
            db.withTransaction {
                transactionDao.deleteAll()
                ruleDao.deleteAll()
            }
            importState.clear()
        }
    }
}
