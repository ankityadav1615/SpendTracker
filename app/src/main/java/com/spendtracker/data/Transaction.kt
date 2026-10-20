package com.spendtracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.spendtracker.model.Category
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
    val sender: String,
    val body: String,
    val dedupeKey: String,
)

val Transaction.displayName: String
    get() = counterparty ?: vpa ?: bank?.let { "$it transaction" } ?: "Unknown"

@Entity(tableName = "category_rules")
data class CategoryRule(
    /** "<DEBIT|CREDIT>:<counterpartyKey>" */
    @PrimaryKey val key: String,
    val category: Category,
    val updatedAt: Long,
)
