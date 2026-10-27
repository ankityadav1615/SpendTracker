package com.spendtracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.spendtracker.model.Category
import com.spendtracker.model.EntrySource
import com.spendtracker.model.PaymentMethod
import com.spendtracker.model.TransactionType

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["dedupeKey"], unique = true),
        Index(value = ["timestamp"]),
        Index(value = ["counterpartyKey"]),
    ],
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    val amountPaise: Long,
    val counterparty: String?,
    /** Normalized payee/payer identity used to remember category corrections. Empty when unknown. */
    val counterpartyKey: String,
    val vpa: String?,
    val accountTail: String?,
    val bank: String?,
    val refNo: String?,
    val balancePaise: Long?,
    val timestamp: Long,
    val category: Category,
    /** Hidden by the user ("not a transaction"). Kept so a rescan does not import it again. */
    val ignored: Boolean = false,
    /** SMS sender; empty for entries the user added. */
    val sender: String,
    /** Original SMS text; empty for entries the user added. */
    val body: String,
    val dedupeKey: String,
    @ColumnInfo(defaultValue = "SMS") val source: EntrySource = EntrySource.SMS,
    /** How an added entry was paid; null for SMS transactions. */
    val method: PaymentMethod? = null,
    val note: String? = null,
    /** User-defined category label (for example "Pets"), null when using the built-in label. */
    @ColumnInfo(defaultValue = "NULL") val customCategory: String? = null,
    /** Absolute path to a receipt photo the user attached; null when there is none. */
    @ColumnInfo(defaultValue = "NULL") val receiptPath: String? = null,
)

val Transaction.isManual: Boolean get() = source == EntrySource.MANUAL

/** The user's own category name, or null when the transaction is in a built-in category. */
val Transaction.customName: String?
    get() = customCategory?.trim()?.takeIf { it.isNotEmpty() }

val Transaction.categoryLabel: String
    get() = customName ?: category.label

val Transaction.displayName: String
    get() = counterparty ?: vpa ?: bank?.let { "$it transaction" } ?: if (isManual) categoryLabel else "Unknown"

@Entity(tableName = "category_rules")
data class CategoryRule(
    /** "<DEBIT|CREDIT>:<counterpartyKey>" */
    @PrimaryKey val key: String,
    val category: Category,
    val updatedAt: Long,
    /** Name of the user's own category the payee goes to; null for a built-in category. */
    @ColumnInfo(defaultValue = "NULL") val customCategory: String? = null,
)

/**
 * A category the user made, like "Pets". Transactions in it keep [CustomCategories.baseFor] as their built-in
 * category and carry the name in [Transaction.customCategory].
 */
@Entity(tableName = "custom_categories", indices = [Index(value = ["name"], unique = true)])
data class CustomCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** DEBIT for an expense category, CREDIT for an income one. */
    val type: TransactionType,
    val createdAt: Long,
)

/** An SMS sender the user blocked: its messages are never read as transactions. */
@Entity(tableName = "blocked_senders")
data class BlockedSender(
    /** Sender id without the operator prefix and route suffix, see [com.spendtracker.parser.SmsParser.senderKey]. */
    @PrimaryKey val key: String,
    /** The payee or name of the message the user blocked it from, to recognise it later. */
    val example: String?,
    val blockedAt: Long,
)

/** A monthly spending limit the user set for one category, shown on Home and used for over-budget nudges. */
@Entity(tableName = "category_budgets")
data class CategoryBudget(
    @PrimaryKey val category: Category,
    val amountPaise: Long,
)
