package com.spendtracker.parser

import com.spendtracker.data.Transaction
import com.spendtracker.model.Category
import com.spendtracker.model.TransactionType
import java.security.MessageDigest

data class RawSms(val sender: String, val body: String, val timestamp: Long)

object TransactionBuilder {

    private val MONTHS = setOf(
        "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "sept", "oct", "nov", "dec",
        "january", "february", "march", "april", "june", "july", "august", "september", "october",
        "november", "december",
    )

    fun build(sms: RawSms, rules: Map<String, Category>): Transaction? {
        val parsed = SmsParser.parse(sms.sender, sms.body) ?: return null
        val key = counterpartyKey(parsed.counterparty, parsed.vpa)
        val remembered = if (key.isNotEmpty()) rules[ruleKey(parsed.type, key)] else null
        return Transaction(
            type = parsed.type,
            amountPaise = parsed.amountPaise,
            counterparty = parsed.counterparty,
            counterpartyKey = key,
            vpa = parsed.vpa,
            accountTail = parsed.accountTail,
            bank = parsed.bank,
            refNo = parsed.refNo,
            balancePaise = parsed.balancePaise,
            timestamp = sms.timestamp,
            category = remembered ?: Categorizer.categorize(parsed, sms.body),
            sender = sms.sender,
            body = sms.body,
            dedupeKey = dedupeKey(parsed.type, parsed.refNo, parsed.amountPaise, sms.body),
        )
    }

    /** Same payee => same key, so a category correction can be applied to all of their transactions. */
    fun counterpartyKey(counterparty: String?, vpa: String?): String {
        if (!vpa.isNullOrBlank()) return vpa.lowercase()
        if (counterparty.isNullOrBlank()) return ""
        return counterparty.lowercase()
            .split(Regex("[^a-z]+"))
            .filter { it.length > 1 && it !in MONTHS }
            .take(4)
            .joinToString(" ")
    }

    fun ruleKey(type: TransactionType, counterpartyKey: String) = "${type.name}:$counterpartyKey"

    /**
     * The same transaction can arrive twice (live SMS + inbox rescan, or an SMS and its copy from a
     * second bank alert). The bank reference number is the most reliable identity; otherwise the text.
     */
    fun dedupeKey(type: TransactionType, refNo: String?, amountPaise: Long, body: String): String {
        if (!refNo.isNullOrBlank()) return "ref:${type.name}:$refNo:$amountPaise"
        val normalized = body.replace(Regex("""\s+"""), " ").trim().lowercase()
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
        return "sms:" + digest.joinToString("") { "%02x".format(it) }
    }
}
