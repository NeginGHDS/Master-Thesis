package com.example.offline_first.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun createDatabaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> {
    val dbFile = context.applicationContext.getDatabasePath(DATABASE_NAME)
    return Room.databaseBuilder<AppDatabase>(
        context = context.applicationContext,
        name = dbFile.absolutePath
    )
}
