package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.PriceTrackerDao
import com.example.data.entity.*

@Database(
    entities = [Merchant::class, Product::class, Receipt::class, ReceiptItem::class],
    version = 1,
    exportSchema = false
)
abstract class PriceTrackerDatabase : RoomDatabase() {

    abstract fun priceTrackerDao(): PriceTrackerDao

    companion object {
        @Volatile
        private var INSTANCE: PriceTrackerDatabase? = null

        fun getDatabase(context: Context): PriceTrackerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PriceTrackerDatabase::class.java,
                    "price_tracker_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
