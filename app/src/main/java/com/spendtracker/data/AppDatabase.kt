package com.spendtracker.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.Upsert
import com.spendtracker.model.Category
import com.spendtracker.model.EntrySource
import com.spendtracker.model.PaymentMethod
import com.spendtracker.model.TransactionType
import kotlinx.coroutines.flow.Flow

class Converters {
    @TypeConverter
    fun categoryToString(value: Category): String = value.name

    @TypeConverter
    fun stringToCategory(value: String): Category = Category.fromName(value)

    @TypeConverter
    fun typeToString(value: TransactionType): String = value.name

    @TypeConverter
    fun stringToType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun sourceToString(value: EntrySource): String = value.name

    @TypeConverter
    fun stringToSource(value: String): EntrySource =
        EntrySource.entries.firstOrNull { it.name == value } ?: EntrySource.SMS

    @TypeConverter
    fun methodToString(value: PaymentMethod?): String? = value?.name

    @TypeConverter
    fun stringToMethod(value: String?): PaymentMethod? = PaymentMethod.entries.firstOrNull { it.name == value }
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE ignored = 0 ORDER BY timestamp DESC, id DESC")
    fun observeAll(): Flow<List<Transaction>>

    /** Returns the new row ids, -1 for rows skipped as duplicates. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<Transaction>): List<Long>

    @Insert
    suspend fun insert(item: Transaction): Long

    @Update
    suspend fun update(item: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE transactions SET category = :category WHERE id = :id")
    suspend fun updateCategory(id: Long, category: Category)

    @Query("UPDATE transactions SET category = :category WHERE type = :type AND counterpartyKey = :key")
    suspend fun updateCategoryForCounterparty(type: TransactionType, key: String, category: Category)

    @Query("UPDATE transactions SET ignored = :ignored WHERE id = :id")
    suspend fun setIgnored(id: Long, ignored: Boolean)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

@Dao
interface CategoryRuleDao {
    @Query("SELECT * FROM category_rules")
    suspend fun getAll(): List<CategoryRule>

    @Query("SELECT * FROM category_rules ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<CategoryRule>>

    @Upsert
    suspend fun upsert(rule: CategoryRule)

    @Query("DELETE FROM category_rules WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM category_rules")
    suspend fun deleteAll()
}

@Dao
interface CategoryBudgetDao {
    @Query("SELECT * FROM category_budgets")
    fun observeAll(): Flow<List<CategoryBudget>>

    @Query("SELECT * FROM category_budgets")
    suspend fun getAll(): List<CategoryBudget>

    @Upsert
    suspend fun upsert(budget: CategoryBudget)

    @Query("DELETE FROM category_budgets WHERE category = :category")
    suspend fun delete(category: Category)

    @Query("DELETE FROM category_budgets")
    suspend fun deleteAll()
}

@Database(
    entities = [Transaction::class, CategoryRule::class, CategoryBudget::class],
    version = 4,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryRuleDao(): CategoryRuleDao
    abstract fun categoryBudgetDao(): CategoryBudgetDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "spendtracker.db").build()
    }
}
