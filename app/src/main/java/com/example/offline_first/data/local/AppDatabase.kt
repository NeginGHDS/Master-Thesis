package com.example.offline_first.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.offline_first.data.model.Report
import com.example.offline_first.data.model.User

@Database(entities = [Report::class, User::class], version = 6, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    //   Room requires this to be abstract because Room generates the implementation for you at compile time — just like View Binding generates the binding classes from XML.
    // You declare what you want (a ReportDao), and Room generates the actual code that knows how to execute the SQL queries you defined in the DAO.
    //
    //  At runtime, when you call:
    //  AppDatabase.getInstance(this).reportDao()
    //
    //  Room returns its generated implementation of ReportDao that contains the real SQLite code behind insert() and getAll(). You never write that code yourself.
    abstract fun reportDao(): ReportDao
    abstract fun userDao(): UserDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "offline_first.db"
                    //  "If you can't find a migration path to upgrade the old database, just delete it and start fresh."
                    //
                    //  So it drops all tables and recreates them with the new schema. All existing data is lost.
                    //
                    //  The true parameter means this also applies when downgrading (going from a higher version to a lower one)
                ).fallbackToDestructiveMigration(true)
                    .build().also { instance = it }
            }
    }
}