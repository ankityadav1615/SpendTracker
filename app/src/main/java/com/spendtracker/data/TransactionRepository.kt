package com.spendtracker.data

import androidx.room.withTransaction
import com.spendtracker.model.Category
import com.spendtracker.model.CategoryChoice
import com.spendtracker.model.ManualEntries
import com.spendtracker.model.ManualEntry
import com.spendtracker.model.TransactionType
import com.spendtracker.parser.RawSms
import com.spendtracker.parser.SmsParser
import com.spendtracker.parser.TransactionBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.withLock

class TransactionRepository(private val db: AppDatabase, private val importState: SmsImportState) {

    private val transactionDao = db.transactionDao()
    private val ruleDao = db.categoryRuleDao()
    private val budgetDao = db.categoryBudgetDao()
    private val customDao = db.customCategoryDao()
    private val blockedDao = db.blockedSenderDao()

    val transactions: Flow<List<Transaction>> = transactionDao.observeAll()

    /** Remembered payee → category corrections, newest first. */
    val categoryRules: Flow<List<CategoryRule>> = ruleDao.observeAll()

    val categoryBudgets: Flow<List<CategoryBudget>> = budgetDao.observeAll()

    val customCategories: Flow<List<CustomCategory>> = customDao.observeAll()

    val blockedSenders: Flow<List<BlockedSender>> = blockedDao.observeAll()

    /** Parses the messages and stores the transactions. Returns how many new transactions were added. */
    suspend fun ingest(messages: List<RawSms>): Int {
        if (messages.isEmpty()) return 0
        val blocked = blockedDao.keys().toSet()
        val wanted = if (blocked.isEmpty()) messages else messages.filterNot { SmsParser.senderKey(it.sender) in blocked }
        if (wanted.isEmpty()) return 0
        val rules = ruleDao.getAll().associate { it.key to CategoryChoice(it.category, it.customCategory?.takeIf(String::isNotBlank)) }
        val parsed = wanted.mapNotNull { TransactionBuilder.build(it, rules) }
        if (parsed.isEmpty()) return 0
        return transactionDao.insertAll(parsed).count { it != -1L }
    }

    /**
     * Changes the category of [transaction]. With [applyToSimilar] the choice is remembered for the
     * payee, re-categorizing their past transactions and every future one.
     */
    suspend fun updateCategory(transaction: Transaction, choice: CategoryChoice, applyToSimilar: Boolean) {
        val key = transaction.counterpartyKey
        if (!applyToSimilar || key.isEmpty()) {
            transactionDao.updateCategory(transaction.id, choice.category, choice.customName)
            return
        }
        db.withTransaction {
            ruleDao.upsert(
                CategoryRule(
                    key = TransactionBuilder.ruleKey(transaction.type, key),
                    category = choice.category,
                    updatedAt = System.currentTimeMillis(),
                    customCategory = choice.customName,
                ),
            )
            transactionDao.updateCategoryForCounterparty(transaction.type, key, choice.category, choice.customName)
        }
    }

    suspend fun setIgnored(transaction: Transaction, ignored: Boolean) =
        transactionDao.setIgnored(transaction.id, ignored)

    /**
     * Hides [transaction] because it isn't money that moved (an offer, a reward point credit, a failed payment).
     * With [blockSender] every message from its sender is hidden too, now and in future. Returns how many
     * transactions were hidden.
     */
    suspend fun markNotReal(transaction: Transaction, blockSender: Boolean): Int = db.withTransaction {
        transactionDao.setIgnored(transaction.id, true)
        val key = SmsParser.senderKey(transaction.sender)
        if (!blockSender || key.isEmpty()) return@withTransaction 1
        blockedDao.upsert(BlockedSender(key, transaction.displayName, System.currentTimeMillis()))
        val ids = transactionDao.smsSenders().filter { SmsParser.senderKey(it.sender) == key }.map { it.id }
        if (ids.isNotEmpty()) transactionDao.setIgnored(ids, true)
        maxOf(ids.size, 1)
    }

    /**
     * Reads messages from [key] again: its hidden transactions come back. Messages skipped while it was
     * blocked are picked up by the next full inbox scan.
     */
    suspend fun unblockSender(key: String) = db.withTransaction {
        blockedDao.delete(key)
        val ids = transactionDao.smsSenders().filter { SmsParser.senderKey(it.sender) == key }.map { it.id }
        if (ids.isNotEmpty()) transactionDao.setIgnored(ids, false)
    }

    /** Adds one of the user's own categories. Returns false when the name is taken. */
    suspend fun addCustomCategory(name: String, type: TransactionType): Boolean =
        customDao.insert(CustomCategory(name = name, type = type, createdAt = System.currentTimeMillis())) != -1L

    /** Renames a category of the user's, carrying its payments and remembered payees along. */
    suspend fun renameCustomCategory(id: Long, newName: String) = db.withTransaction {
        val old = customDao.getAll().firstOrNull { it.id == id } ?: return@withTransaction
        if (old.name == newName) return@withTransaction
        customDao.rename(id, newName)
        transactionDao.renameCustomCategory(old.name, newName)
        ruleDao.renameCustomCategory(old.name, newName)
    }

    /**
     * Deletes a category of the user's. Its payments stay, back in [com.spendtracker.model.CustomCategories.baseFor],
     * and its remembered payees are forgotten.
     */
    suspend fun deleteCustomCategory(id: Long) = db.withTransaction {
        val old = customDao.getAll().firstOrNull { it.id == id } ?: return@withTransaction
        transactionDao.clearCustomCategory(old.name)
        ruleDao.deleteForCustomCategory(old.name)
        customDao.delete(id)
    }

    /** Forgets a remembered payee → category rule; past and future transactions keep whatever category they have. */
    suspend fun deleteCategoryRule(key: String) = ruleDao.delete(key)

    /** Sets or, with a null or non-positive amount, removes the monthly budget for [category]. */
    suspend fun setCategoryBudget(category: Category, amountPaise: Long?) {
        if (amountPaise == null || amountPaise <= 0) budgetDao.delete(category) else budgetDao.upsert(CategoryBudget(category, amountPaise))
    }

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
                budgetDao.deleteAll()
                customDao.deleteAll()
                blockedDao.deleteAll()
            }
            importState.clear()
        }
    }
}
