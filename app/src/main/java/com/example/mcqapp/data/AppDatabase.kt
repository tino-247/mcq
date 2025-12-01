package com.example.mcqapp.data

import android.content.Context
import android.util.Log
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Question::class],
    version = 1,
    exportSchema = true, // Good practice to export schema for migrations
    autoMigrations = [
//        AutoMigration(from = 1, to = 2), // Simple column addition can often use AutoMigration
    ]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        const val DATABASE_NAME = "mcq_database" // Expose DB name

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mcq_database"
                )
                    // If AutoMigration isn't sufficient or you prefer manual:
                    // .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        // New method to close and clear the instance
        fun closeAndClearInstance() {
            INSTANCE?.let {
                if (it.isOpen) {
                    it.close()
                }
                INSTANCE = null
                Log.d("AppDatabase", "Database instance closed and cleared.")
            }
        }

        // Example of a manual migration if AutoMigration doesn't work or for more complex changes
        // val MIGRATION_1_2 = object : Migration(1, 2) {
        //     override fun migrate(db: SupportSQLiteDatabase) {
        //         db.execSQL("ALTER TABLE questions ADD COLUMN timesCorrectRecent INTEGER NOT NULL DEFAULT 0")
        //     }
        // }
    }
}