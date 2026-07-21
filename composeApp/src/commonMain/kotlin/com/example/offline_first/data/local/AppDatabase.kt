package com.example.offline_first.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.example.offline_first.data.model.Report
import com.example.offline_first.data.model.User
import com.example.offline_first.util.ioDispatcher

const val DATABASE_NAME = "offline_first.db"

//   Room requires this to be abstract because Room generates the implementation for you at compile
//  time. You declare what you want (a ReportDao), and Room (via KSP, on every target including iOS)
//  generates the actual implementation that knows how to execute the SQL queries you defined in the DAO.
@Database(entities = [Report::class, User::class], version = 6, exportSchema = false)
@TypeConverters(Converters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reportDao(): ReportDao
    abstract fun userDao(): UserDao
}

// On Kotlin/Native, Room can't use reflection to instantiate the generated implementation, so the
// KSP compiler generates an `actual object AppDatabaseConstructor` per target that does it directly.
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

/**
 * Finishes configuring a platform-supplied builder identically everywhere: bundled SQLite driver,
 * queries dispatched on IO, and the same "can't migrate? wipe and start fresh" fallback the
 * original single-module Android app used (`fallbackToDestructiveMigration(true)`).
 */
fun RoomDatabase.Builder<AppDatabase>.buildAppDatabase(): AppDatabase =
    fallbackToDestructiveMigration(dropAllTables = true)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(ioDispatcher)
        .build()
