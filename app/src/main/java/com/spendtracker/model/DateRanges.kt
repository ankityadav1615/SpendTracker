package com.spendtracker.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Short, friendly date-range text for period labels, e.g. "1–20 Oct" or "28 Sep – 3 Oct". */
object DateRanges {

    private val DAY = DateTimeFormatter.ofPattern("d", Locale.ENGLISH)
    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    /** A single date; the year is shown only when it is not [today]'s year. */
    fun date(date: LocalDate, today: LocalDate): String =
        date.format(if (date.year == today.year) DAY_MONTH else DAY_MONTH_YEAR)

    /** Inclusive range from [first] to [last]. Years are shown only when they differ from [today]'s. */
    fun range(first: LocalDate, last: LocalDate, today: LocalDate): String {
        if (first == last) return date(first, today)
        val sameYear = first.year == last.year
        val showYear = !sameYear || last.year != today.year
        return when {
            sameYear && first.month == last.month ->
                first.format(DAY) + "–" + last.format(if (showYear) DAY_MONTH_YEAR else DAY_MONTH)
            sameYear -> first.format(DAY_MONTH) + " – " + last.format(if (showYear) DAY_MONTH_YEAR else DAY_MONTH)
            else -> first.format(DAY_MONTH_YEAR) + " – " + last.format(DAY_MONTH_YEAR)
        }
    }
}
