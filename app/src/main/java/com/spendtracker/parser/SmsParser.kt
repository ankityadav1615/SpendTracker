package com.spendtracker.parser

import com.spendtracker.model.TransactionType
import java.math.RoundingMode

data class ParsedSms(
    val type: TransactionType,
    val amountPaise: Long,
    /** Payee for debits / payer for credits, cleaned up for display. */
    val counterparty: String?,
    val vpa: String?,
    val accountTail: String?,
    val bank: String?,
    val refNo: String?,
    val balancePaise: Long?,
)

/**
 * Regex based parser for the transaction alert SMS that Indian banks send for UPI, IMPS/NEFT,
 * card and ATM transactions. Returns null for anything that is not a completed debit/credit
 * (OTPs, payment requests, reminders, failed transactions, offers...).
 */
object SmsParser {

    private val IC = RegexOption.IGNORE_CASE

    private val PHONE_NUMBER_SENDER = Regex("""^\+?\d{10,}$""")

    // Evaluated against the lowercased message.
    private val NOISE = listOf(
        Regex("""\b(otp|one[ -]?time password|verification code|security code)\b"""),
        Regex("""\b(has requested|is requesting|requested money|collect request|payment request)\b"""),
        Regex("""\bwill be (debited|deducted|charged|auto[ -]?debited|processed)\b"""),
        Regex("""\b(is due|are due|due on|due by|due date|amount due|amt due|overdue|payment due|minimum due)\b"""),
        Regex("""\b(pre[ -]?approved|apply now|eligible for|congratulations|limited period|click here to avail)\b"""),
        Regex("""\bmandate\b.{0,60}\b(created|registered|set up|successfully set|revoked|paused)\b"""),
    )
    private val FAILED = Regex("""\b(failed|declined|unsuccessful|could not be|not been processed|insufficient (funds|balance))\b""")
    private val REVERSAL = Regex("""\b(refund|refunded|reversal|reversed)\b""")

    private val DEBIT_WORDS = Regex(
        """\b(debited|debit|deducted|spent|paid|sent|withdrawn|withdrawal|purchased?|dr|charged|used|thank you for using)\b""",
    )
    private val CREDIT_WORDS = Regex("""\b(credited|credit|received|deposited|cr|refunded|refund|reversed|reversal)\b""")
    private val CARD_WORDS = Regex("""\b(credit|debit)\s+card\b""")
    private val CREDIT_LIMIT = Regex("""\b(credit|cr)\s+(limit|lmt)\b""")
    private val CARD_PAYMENT_ACK = Regex("""payment.{0,60}received|received.{0,60}payment""")

    private val CURRENCY_AMOUNT = Regex("""(?<![a-z])(?:rs\.?|inr|₹|rupees)\s*:?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", IC)
    private val BARE_AMOUNT = Regex("""\b(?:debited|credited)\s+(?:by|with|for)\s+([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", IC)
    private val BALANCE_CONTEXT = Regex("""\b(bal|balance|limit|lmt)\b[^0-9]{0,12}$""")
    private val BALANCE = Regex(
        """\b(?:bal|balance)\b[^0-9]{0,25}?(?<![a-z])(?:rs\.?|inr|₹)\s*:?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        IC,
    )

    private val ACCOUNT = Regex(
        """(?:a/c|acct|account|\bac\b|card)(?:\s*(?:no|number)\b)?[\s.:\-]*(?:ending\s*(?:with|in)?[\s.:\-]*)?[x*#]+[\s.\-]*(\d{3,6})""",
        IC,
    )
    private val ACCOUNT_ENDING = Regex("""\bending\s*(?:with|in)?\s*[x*]*\s*(\d{3,6})""", IC)

    private val VPA = Regex("""(?<![a-z0-9._-])([a-z0-9][a-z0-9._-]+@[a-z][a-z0-9]+)(?![a-z0-9]*\.[a-z])""", IC)

    private val REF = Regex(
        """\b(?:utr|rrn|ref(?:erence)?|refno|upi|txn|transaction|imps)\b\s*(?:ref\.?|no\.?|num(?:ber)?|id|#)?\s*(?:no\.?)?\s*[:.#/\-]?\s*([0-9]{6,})""",
        IC,
    )
    private val UPI_PATH = Regex("""\bupi/(?:[a-z0-9]{2,4}/)?([0-9]{6,})/([^/]+)""", IC)
    // "NEFT Cr-CITI0000001-ACME TECHNOLOGIES PVT LTD-RAHUL SHARMA-CITIN2412345"
    // "UPI-SWIGGY-swiggy@icici-ICIC0DC0099-429512345678-Payment": name, VPA, (IFSC), reference.
    private val UPI_NARRATION = Regex(
        """\bupi-([^-]{2,60}?)-([a-z0-9][a-z0-9._-]*@[a-z][a-z0-9]+)-(?:[a-z]{4}0[a-z0-9]{6}-)?([0-9]{6,})""",
        IC,
    )
    private val NEFT_NARRATION = Regex("""\b(?:neft|imps|rtgs)\s*(?:cr|dr)?\s*-\s*[a-z]{4}0[a-z0-9]{6}\s*-\s*([^-]{2,60}?)\s*-""", IC)

    private const val NAME = """([A-Za-z0-9@&._'*/\- ]{2,60}?)"""
    private const val END =
        """(?=\s+(?:on|via|using|ref|refno|upi|avl|avbl|bal|not|if|from|txn|info|dated|at|with|is|has|for|by|in|and|to|thru|through)\b|\s*[,;()\[\]]|\.\s|\.$|:\s|\s+-\s|$)"""

    private val DEBIT_NAME_PATTERNS = listOf(
        Regex("""\b(?:to|towards)\s+(?:vpa\s*[:\-]?\s*)?$NAME$END""", IC),
        Regex("""\bat\s+$NAME$END""", IC),
        Regex("""\binfo\s*[:\-]?\s*$NAME$END""", IC),
        Regex(""";\s*$NAME\s+credited""", IC),
    )
    private val DEBIT_FALLBACK_NAME_PATTERNS = listOf(
        Regex("""\bon\s+(?=[A-Za-z])$NAME$END""", IC),
    )
    private val CREDIT_NAME_PATTERNS = listOf(
        Regex("""\b(?:from|by)\s+(?:vpa\s*[:\-]?\s*)?$NAME$END""", IC),
        Regex("""\binfo\s*[:\-]?\s*$NAME$END""", IC),
        Regex("""\bfor\s+(?=(?:neft|imps|rtgs|upi)\b)$NAME$END""", IC),
    )

    private val NAME_TAIL = Regex("""[.\s]+(avl|avbl|bal|not you|if not|call|sms|ref|refno|info|upi ref)\b.*$""", IC)
    private val NAME_VPA_PREFIX = Regex("""^vpa\s*[:\-]?\s*""", IC)
    private val NAME_RAIL_PREFIX = Regex("""^(?:neft|imps|rtgs|upi|ach|nach|ecs)(?:[\s\-:/]*(?:cr|dr)\b)?[\s\-:/]+""", IC)
    private val NAME_IFSC_PREFIX = Regex("""^[A-Z]{4}0[A-Z0-9]{6}[\s\-/]*""", IC)
    // Card payment gateways prefix the merchant: "IND*FLIPKART", "PAYU*SWIGGY", "RAZ*ZOMATO".
    private val NAME_GATEWAY_PREFIX = Regex("""^[A-Za-z]{2,6}\s*\*\s*(?=[A-Za-z])""")
    private val ACRONYMS = setOf(
        "LTD", "PVT", "LLP", "KFC", "ATM", "SBI", "HDFC", "ICICI", "IRCTC", "BSNL", "MTNL", "PVR", "LIC", "IOCL",
        "HPCL", "BPCL", "NHAI", "DTH", "EMI", "UPI", "NEFT", "IMPS", "IDFC", "BESCOM", "MSEDCL",
    )
    private const val MONTH = "(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)"
    private val NAME_REJECT = Regex(
        "^(?:(?:rs\\.?|inr|₹)\\s*\\d|\\d+\\s*(?:am|pm|hrs)\\b|\\d{1,2}[\\s\\-/]*$MONTH|$MONTH[a-z]*[\\s\\-]*\\d|" +
            "(?:your|ur|you|the|a/c|ac|acct|account|cust|date|report|block|dispute|know|avoid|stop|check|prevent|" +
            "call|sms|customer|helpline|dial|unsubscribe|beneficiary)\\b)",
        IC,
    )
    private val NAME_REJECT_ANYWHERE = Regex("""\b(card|your|ur)\b""", IC)

    private val BANK_SENDER_CODES = listOf(
        "HDFC" to "HDFC Bank", "ICICI" to "ICICI Bank", "SBI" to "SBI", "AXIS" to "Axis Bank",
        "KOTAK" to "Kotak Bank", "IDFC" to "IDFC FIRST Bank", "INDUS" to "IndusInd Bank", "YESB" to "Yes Bank",
        "PNB" to "PNB", "BOB" to "Bank of Baroda", "BARODA" to "Bank of Baroda", "CANBNK" to "Canara Bank",
        "CANARA" to "Canara Bank", "UNION" to "Union Bank", "UBOI" to "Union Bank", "IOB" to "Indian Overseas Bank",
        "INDBNK" to "Indian Bank", "BOIIND" to "Bank of India", "CBOI" to "Central Bank", "CENTBK" to "Central Bank",
        "FEDBNK" to "Federal Bank", "FEDERAL" to "Federal Bank", "AUBANK" to "AU Bank", "RBL" to "RBL Bank",
        "IDBI" to "IDBI Bank", "SCBANK" to "Standard Chartered", "HSBC" to "HSBC", "CITI" to "Citi",
        "DBS" to "DBS Bank", "PAYTM" to "Paytm Payments Bank", "AIRBNK" to "Airtel Payments Bank",
        "KVB" to "Karur Vysya Bank", "BANDHN" to "Bandhan Bank", "EQUITAS" to "Equitas Bank", "UJJIVN" to "Ujjivan Bank",
    )
    private val BANK_BODY_NAMES = listOf(
        "hdfc bank" to "HDFC Bank", "icici bank" to "ICICI Bank", "axis bank" to "Axis Bank", "kotak" to "Kotak Bank",
        "idfc first" to "IDFC FIRST Bank", "indusind" to "IndusInd Bank", "yes bank" to "Yes Bank",
        "bank of baroda" to "Bank of Baroda", "canara bank" to "Canara Bank", "union bank" to "Union Bank",
        "punjab national" to "PNB", "federal bank" to "Federal Bank", "sbi" to "SBI", "state bank" to "SBI",
    )

    fun parse(sender: String, body: String): ParsedSms? {
        if (PHONE_NUMBER_SENDER.matches(sender.replace(" ", ""))) return null
        val text = body.replace(Regex("""\s+"""), " ").trim()
        if (text.isEmpty()) return null
        val lower = text.lowercase()

        if (NOISE.any { it.containsMatchIn(lower) }) return null
        val isReversalCredit = lower.contains("credited") && REVERSAL.containsMatchIn(lower)
        if (FAILED.containsMatchIn(lower) && !isReversalCredit) return null

        val type = detectType(lower) ?: return null
        if (type == TransactionType.CREDIT && lower.contains("card") && CARD_PAYMENT_ACK.containsMatchIn(lower)) {
            // "Payment received towards your credit card": already tracked as a debit from the bank account.
            return null
        }

        val amount = findAmount(text, lower) ?: return null
        val upiNarration = UPI_NARRATION.find(text)
        val vpa = (upiNarration?.groupValues?.get(2) ?: VPA.find(text)?.groupValues?.get(1))?.lowercase()
        val upiPath = UPI_PATH.find(text)
        val refNo = upiPath?.groupValues?.get(1)
            ?: upiNarration?.groupValues?.get(3)
            ?: REF.find(text)?.groupValues?.get(1)

        return ParsedSms(
            type = type,
            amountPaise = amount,
            counterparty = findCounterparty(text, type, vpa, upiPath, upiNarration),
            vpa = vpa,
            accountTail = (ACCOUNT.find(text) ?: ACCOUNT_ENDING.find(text))?.groupValues?.get(1),
            bank = detectBank(sender, lower),
            refNo = refNo,
            balancePaise = BALANCE.find(text)?.groupValues?.get(1)?.let(::toPaise),
        )
    }

    private fun detectType(lower: String): TransactionType? {
        val cleaned = lower.replace(CARD_WORDS, "card").replace(CREDIT_LIMIT, "limit")
        val debitAt = DEBIT_WORDS.find(cleaned)?.range?.first
        val creditAt = CREDIT_WORDS.find(cleaned)?.range?.first
        return when {
            debitAt == null && creditAt == null -> null
            creditAt == null -> TransactionType.DEBIT
            debitAt == null -> TransactionType.CREDIT
            debitAt < creditAt -> TransactionType.DEBIT
            else -> TransactionType.CREDIT
        }
    }

    private fun findAmount(text: String, lower: String): Long? {
        for (match in CURRENCY_AMOUNT.findAll(text)) {
            val before = lower.substring(maxOf(0, match.range.first - 20), match.range.first)
            if (BALANCE_CONTEXT.containsMatchIn(before)) continue
            toPaise(match.groupValues[1])?.let { return it }
        }
        return BARE_AMOUNT.find(text)?.groupValues?.get(1)?.let(::toPaise)
    }

    private fun toPaise(raw: String): Long? = raw.replace(",", "").trimEnd('.')
        .toBigDecimalOrNull()
        ?.movePointRight(2)
        ?.setScale(0, RoundingMode.HALF_UP)
        ?.toLong()
        ?.takeIf { it > 0 }

    private fun findCounterparty(
        text: String,
        type: TransactionType,
        vpa: String?,
        upiPath: MatchResult?,
        upiNarration: MatchResult?,
    ): String? {
        upiPath?.let { cleanName(it.groupValues[2], vpa) }?.let { return it }
        upiNarration?.let { cleanName(it.groupValues[1], vpa) }?.let { return it }
        NEFT_NARRATION.find(text)?.let { cleanName(it.groupValues[1], vpa) }?.let { return it }

        val primary = if (type == TransactionType.DEBIT) DEBIT_NAME_PATTERNS else CREDIT_NAME_PATTERNS
        val fallback = if (type == TransactionType.DEBIT) DEBIT_FALLBACK_NAME_PATTERNS else emptyList()
        for (patterns in listOf(primary, fallback)) {
            val candidates = patterns.flatMap { it.findAll(text).toList() }.sortedBy { it.range.first }
            for (candidate in candidates) {
                val raw = candidate.groupValues[1]
                val cleaned = cleanName(raw, vpa)
                if (cleaned != null) return cleaned
                // The payee was only identified by its VPA; that is shown instead of a name.
                if (vpa != null && raw.contains(vpa, ignoreCase = true)) return null
            }
        }
        return null
    }

    private fun cleanName(raw: String, vpa: String?): String? {
        var name = raw.replace(NAME_TAIL, "").trim()
        name = name.replace(NAME_VPA_PREFIX, "")
        repeat(3) {
            name = name.replace(NAME_RAIL_PREFIX, "").replace(NAME_IFSC_PREFIX, "")
        }
        if (vpa != null) name = name.replace(vpa, "", ignoreCase = true)
        name = name.replace(Regex("""\s+"""), " ").trim(' ', '-', '/', '.', ',', ':', '*', '\'')
        name = name.replace(NAME_GATEWAY_PREFIX, "")
        if (name.length > 40) name = name.take(40).trim()
        if (name.length < 2 || NAME_REJECT.containsMatchIn(name) || NAME_REJECT_ANYWHERE.containsMatchIn(name)) return null
        if (name.none { it.isLetter() }) return null
        return prettify(name)
    }

    /** "AMAZON PAY INDIA" -> "Amazon Pay India". Known acronyms and codes such as "S1ANBG01" are kept. */
    private fun prettify(name: String): String {
        if (name.any { it.isLowerCase() }) return name
        return name.split(' ').joinToString(" ") { word ->
            when {
                word.length <= 2 || word in ACRONYMS || !word.all { it.isLetter() } -> word
                else -> word.lowercase().replaceFirstChar { it.uppercase() }
            }
        }
    }

    private fun detectBank(sender: String, lower: String): String? {
        val parts = sender.uppercase().split('-').filter { it.isNotBlank() }
        val code = if (parts.size >= 2) parts[1] else parts.firstOrNull().orEmpty()
        BANK_SENDER_CODES.firstOrNull { code.contains(it.first) }?.let { return it.second }
        return BANK_BODY_NAMES.firstOrNull { Regex("""\b${Regex.escape(it.first)}\b""").containsMatchIn(lower) }?.second
    }
}
