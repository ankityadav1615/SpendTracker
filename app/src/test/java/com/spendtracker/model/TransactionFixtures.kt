package com.spendtracker.model

import com.spendtracker.data.Transaction
import java.time.LocalDateTime
import java.time.ZoneId

val IST: ZoneId = ZoneId.of("Asia/Kolkata")

fun istMillis(year: Int, month: Int, day: Int, hour: Int = 10, minute: Int = 0): Long =
    LocalDateTime.of(year, month, day, hour, minute).atZone(IST).toInstant().toEpochMilli()

private var fixtureId = 1L

fun testTxn(
    type: TransactionType,
    rupees: Long,
    category: Category,
    timestamp: Long = 0,
    counterparty: String? = null,
    vpa: String? = null,
    bank: String? = "HDFC Bank",
    tail: String? = "1234",
    source: EntrySource = EntrySource.SMS,
    method: PaymentMethod? = null,
    note: String? = null,
    paise: Long = rupees * 100,
): Transaction {
    val id = fixtureId++
    val manual = source == EntrySource.MANUAL
    return Transaction(
        id = id,
        type = type,
        amountPaise = paise,
        counterparty = counterparty,
        counterpartyKey = "",
        vpa = vpa,
        accountTail = if (manual) null else tail,
        bank = if (manual) null else bank,
        refNo = null,
        balancePaise = null,
        timestamp = timestamp,
        category = category,
        sender = if (manual) "" else "VM-HDFCBK",
        body = "",
        dedupeKey = "fixture-$id",
        source = source,
        method = method,
        note = note,
    )
}

/** A spend the user added by hand. */
fun manualTxn(
    rupees: Long,
    category: Category,
    timestamp: Long = 0,
    method: PaymentMethod? = PaymentMethod.CASH,
    type: TransactionType = TransactionType.DEBIT,
    counterparty: String? = null,
    note: String? = null,
): Transaction = testTxn(
    type = type,
    rupees = rupees,
    category = category,
    timestamp = timestamp,
    counterparty = counterparty,
    source = EntrySource.MANUAL,
    method = method,
    note = note,
)
