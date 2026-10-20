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
): Transaction {
    val id = fixtureId++
    return Transaction(
        id = id,
        type = type,
        amountPaise = rupees * 100,
        counterparty = counterparty,
        counterpartyKey = "",
        vpa = vpa,
        accountTail = tail,
        bank = bank,
        refNo = null,
        balancePaise = null,
        timestamp = timestamp,
        category = category,
        sender = "VM-HDFCBK",
        body = "",
        dedupeKey = "fixture-$id",
    )
}
