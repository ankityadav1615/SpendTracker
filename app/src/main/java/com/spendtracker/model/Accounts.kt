package com.spendtracker.model

/** Normalizes how banks and account numbers appear in SMS so the same account is recognised everywhere. */
object Accounts {

    private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
    private val TRAILING_WORDS = setOf("bank", "ltd", "limited")
    private val BANK_ALIASES = mapOf(
        "state bank of india" to "sbi",
        "punjab national" to "pnb",
        "kotak mahindra" to "kotak",
        "central bank of india" to "central",
        "au small finance" to "au",
    )

    /**
     * Case, spacing and punctuation insensitive bank identity: "ICICI Bank", "icici  bank." and "ICICI" all
     * give "icici". A trailing "Bank" is ignored, so "Indian Bank" ("indian") and "Bank of India"
     * ("bank of india") stay different. Null when the bank is unknown.
     */
    fun bankKey(bank: String?): String? {
        val words = bank.orEmpty().lowercase().split(NON_ALPHANUMERIC).filter { it.isNotEmpty() }.toMutableList()
        while (words.size > 1 && words.last() in TRAILING_WORDS) words.removeAt(words.lastIndex)
        val key = words.joinToString(" ").ifEmpty { return null }
        return BANK_ALIASES[key] ?: key
    }

    /** The digits of a masked account/card number ("XX3485" → "3485"); null when there are none. */
    fun normalizeTail(tail: String?): String? = tail?.filter { it.isDigit() }?.ifEmpty { null }

    /**
     * Banks mask the same account with a different number of trailing digits ("XX85", "XX485", "XX3485"),
     * so two tails are compatible when one ends with the other.
     */
    fun tailsMatch(a: String, b: String): Boolean = a.endsWith(b) || b.endsWith(a)
}
