package com.spendtracker.model

import com.spendtracker.data.CategoryBudget
import com.spendtracker.data.CustomCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomCategoriesTest {

    private fun category(id: Long, name: String, type: TransactionType = TransactionType.DEBIT) =
        CustomCategory(id = id, name = name, type = type, createdAt = id)

    private fun info(name: String, count: Int = 0) = CustomCategoryInfo(name.hashCode().toLong(), name, TransactionType.DEBIT, count)

    @Test
    fun customCategoriesSitOnTheGeneralBuiltInOnes() {
        assertEquals(Category.DAILY, CustomCategories.baseFor(TransactionType.DEBIT))
        assertEquals(Category.OTHER_INCOME, CustomCategories.baseFor(TransactionType.CREDIT))
        assertEquals(CategoryChoice(Category.DAILY, "Pets"), CustomCategories.choice(TransactionType.DEBIT, "Pets"))
        assertEquals("Pets", CustomCategories.choice(TransactionType.DEBIT, "Pets").label)
        assertEquals("Rent", CategoryChoice(Category.RENT).label)
    }

    @Test
    fun namesAreTidied() {
        assertEquals("Kids school", CustomCategories.normalize("  Kids   school "))
    }

    @Test
    fun validationExplainsWhatIsWrong() {
        assertNull(CustomCategories.validate("Pets", listOf("Gym"), adding = true))
        assertEquals("Type a name", CustomCategories.validate("   ", emptyList(), adding = true))
        assertEquals(
            "Keep it under ${CustomCategories.MAX_NAME_LENGTH} letters",
            CustomCategories.validate("x".repeat(CustomCategories.MAX_NAME_LENGTH + 1), emptyList(), adding = true),
        )
        assertEquals("\"rent\" already exists", CustomCategories.validate("rent", emptyList(), adding = true))
        assertEquals("You already have \"pets\"", CustomCategories.validate("pets", listOf("Pets"), adding = true))
        val full = (1..CustomCategories.MAX_COUNT).map { "Cat $it" }
        assertEquals(
            "You can have up to ${CustomCategories.MAX_COUNT}. Delete one you don't use first",
            CustomCategories.validate("Pets", full, adding = true),
        )
        // Renaming one of a full list is still fine.
        assertNull(CustomCategories.validate("Pets", full.drop(1), adding = false))
    }

    @Test
    fun infosCountPaymentsAndPutTheMostUsedFirst() {
        val txns = listOf(
            testTxn(TransactionType.DEBIT, 100, Category.DAILY).copy(customCategory = "Gym"),
            testTxn(TransactionType.DEBIT, 100, Category.DAILY).copy(customCategory = "gym "),
            testTxn(TransactionType.DEBIT, 100, Category.DAILY).copy(customCategory = "Pets"),
            testTxn(TransactionType.DEBIT, 100, Category.DAILY),
        )
        val infos = CustomCategories.infos(
            listOf(category(1, "Pets"), category(2, "Gym"), category(3, "Books"), category(4, "Side job", TransactionType.CREDIT)),
            txns,
        )
        assertEquals(listOf("Gym" to 2, "Pets" to 1, "Books" to 0, "Side job" to 0), infos.map { it.name to it.count })
        assertEquals(TransactionType.CREDIT, infos.last().type)
    }

    @Test
    fun pickerShowsTheTopFewAndKeepsTheSelectedOne() {
        val all = (1..8).map { info("Cat $it", count = 10 - it) }
        assertEquals(all.take(3), CustomCategories.visible(all, selected = null, limit = 3))
        assertEquals(all.take(3), CustomCategories.visible(all, selected = "Cat 2", limit = 3))
        assertEquals(all.take(3) + all[6], CustomCategories.visible(all, selected = "Cat 7", limit = 3))
        assertEquals(emptyList<CustomCategoryInfo>(), CustomCategories.visible(emptyList(), selected = "Gone"))
    }

    @Test
    fun summaryAndActivityKeepCustomCategoriesApart() {
        val pets = testTxn(TransactionType.DEBIT, 900, Category.DAILY).copy(customCategory = "Pets")
        val daily = testTxn(TransactionType.DEBIT, 300, Category.DAILY)
        val summary = Summary.of(listOf(pets, daily))
        val petsTotal = summary.byCategory.single { it.label == "Pets" }
        assertEquals(true, petsTotal.isCustom)
        assertEquals("Pets", petsTotal.customName)
        assertEquals(Category.DAILY, petsTotal.category)
        assertEquals(false, summary.byCategory.single { it.label == Category.DAILY.label }.isCustom)

        val custom = ActivityFilter.forCategory(Category.DAILY, "Pets")
        assertEquals(ActivityFilter.Custom("Pets"), custom)
        assertEquals(custom, ActivityFilter.fromKey(custom.key))
        assertEquals(true, custom.matches(pets))
        assertEquals(false, custom.matches(daily))
        val builtIn = ActivityFilter.forCategory(Category.DAILY, Category.DAILY.label)
        assertEquals(ActivityFilter.Of(Category.DAILY), builtIn)
        assertEquals(false, builtIn.matches(pets))
        assertEquals(true, builtIn.matches(daily))
        assertEquals(
            listOf(ActivityFilter.Custom("Pets"), ActivityFilter.Of(Category.DAILY)),
            Activity.filtersFor(listOf(pets, daily)).drop(3),
        )
    }

    @Test
    fun budgetsIgnoreCustomCategories() {
        val statuses = Budgets.statuses(
            listOf(CategoryBudget(Category.DAILY, 1_000_00)),
            listOf(CategoryTotal(Category.DAILY, 900_00, 1, label = "Pets"), CategoryTotal(Category.DAILY, 300_00, 1)),
        )
        assertEquals(300_00L, statuses.single().spentPaise)
    }
}
