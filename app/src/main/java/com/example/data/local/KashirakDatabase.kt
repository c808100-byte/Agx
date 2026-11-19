package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ShopSettingsEntity::class,
        ProductEntity::class,
        CustomerEntity::class,
        WorkerEntity::class,
        SaleEntity::class,
        ExpenseEntity::class,
        LedgerEventEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class KashirakDatabase : RoomDatabase() {
    abstract fun kashirakDao(): KashirakDao

    companion object {
        @Volatile
        private var INSTANCE: KashirakDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN barcode TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN minStock INTEGER NOT NULL DEFAULT 5")
            }
        }

        fun getInstance(context: Context): KashirakDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KashirakDatabase::class.java,
                    "kashirak_offline.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
