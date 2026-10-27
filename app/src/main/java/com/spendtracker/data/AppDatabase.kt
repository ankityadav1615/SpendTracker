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
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
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

    @Query("UPDATE transactions SET category = :category, customCategory = :customCategory WHERE id = :id")
    suspend fun updateCategory(id: Long, category: Category, customCategory: String?)

    @Query(
        "UPDATE transactions SET category = :category, customCategory = :customCategory " +
            "WHERE type = :type AND counterpartyKey = :key",
    )
    suspend fun updateCategoryForCounterparty(type: TransactionType, key: String, category: Category, customCategory: String?)

    @Query("UPDATE transactions SET ignored = :ignored WHERE id = :id")
    suspend fun setIgnored(id: Long, ignored: Boolean)

    @Query("UPDATE transactions SET ignored = :ignored WHERE id IN (:ids)")
    suspend fun setIgnored(ids: List<Long>, ignored: Boolean)

    /** Every SMS transaction's sender, hidden ones included, for blocking and unblocking a sender. */
    @Query("SELECT id, sender FROM transactions WHERE source = 'SMS'")
    suspend fun smsSenders(): List<SenderRow>

    @Query("UPDATE transactions SET customCategory = :newName WHERE customCategory = :oldName")
    suspend fun renameCustomCategory(oldName: String, newName: String)

    @Query("UPDATE transactions SET customCategory = NULL WHERE customCategory = :name")
    suspend fun clearCustomCategory(name: String)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

data class SenderRow(val id: Long, val sender: String)

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

    @Query("UPDATE category_rules SET customCategory = :newName WHERE customCategory = :oldName")
    suspend fun renameCustomCategory(oldName: String, newName: String)

    @Query("DELETE FROM category_rules WHERE customCategory = :name")
    suspend fun deleteForCustomCategory(name: String)

    @Query("DELETE FROM category_rules")
    suspend fun deleteAll()
}

@Dao
interface CustomCategoryDao {
    @Query("SELECT * FROM custom_categories ORDER BY createdAt")
    fun observeAll(): Flow<List<CustomCategory>>

    @Query("SELECT * FROM custom_categories")
    suspend fun getAll(): List<CustomCategory>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CustomCategory): Long

    @Query("UPDATE custom_categories SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM custom_categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM custom_categories")
    suspend fun deleteAll()
}

@Dao
interface BlockedSenderDao {
    @Query("SELECT * FROM blocked_senders ORDER BY blockedAt DESC")
    fun observeAll(): Flow<List<BlockedSender>>

    @Query("SELECT `key` FROM blocked_senders")
    suspend fun keys(): List<String>

    @Upsert
    suspend fun upsert(sender: BlockedSender)

    @Query("DELETE FROM blocked_senders WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM blocked_senders")
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
    entities = [
        Transaction::class,
        CategoryRule::class,
        CategoryBudget::class,
        CustomCategory::class,
        BlockedSender::class,
    ],
    version = 6,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6, spec = AppDatabase.AdoptCustomLabels::class),
    ],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryRuleDao(): CategoryRuleDao
    abstract fun categoryBudgetDao(): CategoryBudgetDao
    abstract fun customCategoryDao(): CustomCategoryDao
    abstract fun blockedSenderDao(): BlockedSenderDao

    /**
     * Version 5 let an added entry carry a free-text category name next to its built-in category. Each of those
     * names becomes one of the user's own categories, and its entries move to that category's base.
     */
    class AdoptCustomLabels : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT OR IGNORE INTO custom_categories (name, type, createdAt) " +
                    "SELECT TRIM(customCategory), type, MIN(timestamp) FROM transactions " +
                    "WHERE customCategory IS NOT NULL AND TRIM(customCategory) != '' GROUP BY TRIM(customCategory)",
            )
            db.execSQL("UPDATE transactions SET customCategory = NULL WHERE TRIM(customCategory) = ''")
            db.execSQL("UPDATE transactions SET customCategory = TRIM(customCategory) WHERE customCategory IS NOT NULL")
            db.execSQL("UPDATE transactions SET category = 'DAILY' WHERE customCategory IS NOT NULL AND type = 'DEBIT'")
            db.execSQL("UPDATE transactions SET category = 'OTHER_INCOME' WHERE customCategory IS NOT NULL AND type = 'CREDIT'")
        }
    }

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "spendtracker.db").build()
    }
}
