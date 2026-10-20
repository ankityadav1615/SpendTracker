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
)

val Transaction.isManual: Boolean get() = source == EntrySource.MANUAL

val Transaction.displayName: String
    get() = counterparty ?: vpa ?: bank?.let { "$it transaction" } ?: if (isManual) category.label else "Unknown"

@Entity(tableName = "category_rules")
data class CategoryRule(
    /** "<DEBIT|CREDIT>:<counterpartyKey>" */
    @PrimaryKey val key: String,
    val category: Category,
    val updatedAt: Long,
)
