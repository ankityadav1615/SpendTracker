package com.spendtracker.model

import java.util.Locale
import kotlin.math.abs

object Money {
    /** Formats paise as Indian rupees with lakh/crore grouping, e.g. 1234567_00 -> "₹12,34,567". */
    fun format(paise: Long, withPaise: Boolean = false): String {
        val negative = paise < 0
        val absolute = abs(paise)
        val rupees = if (withPaise) absolute / 100 else (absolute + 50) / 100
        val fraction = absolute % 100
        val digits = rupees.toString()
        val grouped = if (digits.length <= 3) {
            digits
        } else {
            val head = digits.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
            "$head,${digits.takeLast(3)}"
        }
        val decimals = if (withPaise && fraction != 0L) String.format(Locale.ROOT, ".%02d", fraction) else ""
        return (if (negative) "-" else "") + "₹" + grouped + decimals
    }
}
