package com.spendtracker.model

import com.spendtracker.data.CustomCategory
import com.spendtracker.data.Transaction
import com.spendtracker.data.customName

/** A built-in category, or one of the user's own categories on top of its built-in base. */
data class CategoryChoice(val category: Category, val customName: String? = null) {
    val label: String get() = customName ?: category.label
}

/** One of the user's own categories, with how many payments are in it. */
data class CustomCategoryInfo(val id: Long, val name: String, val type: TransactionType, val count: Int)

object CustomCategories {

    const val MAX_COUNT = 20
    const val MAX_NAME_LENGTH = 24

    /** How many of the user's own categories the picker shows before folding the rest behind "More". */
    const val VISIBLE_IN_PICKER = 6

    /**
     * Budgets, reports and the widget understand built-in categories, so a custom category spends as "Daily
     * spends" (or receives as "Money received") underneath.
     */
    fun baseFor(type: TransactionType): Category =
        if (type == TransactionType.DEBIT) Category.DAILY else Category.OTHER_INCOME

    fun choice(type: TransactionType, name: String): CategoryChoice = CategoryChoice(baseFor(type), name)

    fun normalize(name: String): String = name.trim().replace(Regex("""\s+"""), " ")

    /** Why [name] can't be used, or null when it can. [existing] are the other categories' names. */
    fun validate(name: String, existing: Collection<String>, adding: Boolean): String? {
        val clean = normalize(name)
        return when {
            clean.isEmpty() -> "Type a name"
            clean.length > MAX_NAME_LENGTH -> "Keep it under $MAX_NAME_LENGTH letters"
            Category.entries.any { it.label.equals(clean, ignoreCase = true) } -> "\"$clean\" already exists"
            existing.any { it.equals(clean, ignoreCase = true) } -> "You already have \"$clean\""
            adding && existing.size >= MAX_COUNT -> "You can have up to $MAX_COUNT. Delete one you don't use first"
            else -> null
        }
    }

    /** Most used first, so the picker's first chips are the ones the user reaches for. */
    fun infos(categories: List<CustomCategory>, transactions: List<Transaction>): List<CustomCategoryInfo> {
        val counts = transactions.mapNotNull { it.customName }.groupingBy { it.lowercase() }.eachCount()
        return categories
            .map { CustomCategoryInfo(it.id, it.name, it.type, counts[it.name.lowercase()] ?: 0) }
            .sortedWith(compareByDescending<CustomCategoryInfo> { it.count }.thenBy { it.name.lowercase() })
    }

    /**
     * The chips the picker shows without expanding: the most used ones, plus the [selected] one even when it would
     * otherwise be folded away.
     */
    fun visible(all: List<CustomCategoryInfo>, selected: String?, limit: Int = VISIBLE_IN_PICKER): List<CustomCategoryInfo> {
        val top = all.take(limit)
        val chosen = all.firstOrNull { it.name == selected }
        return if (chosen == null || chosen in top) top else top + chosen
    }
}
