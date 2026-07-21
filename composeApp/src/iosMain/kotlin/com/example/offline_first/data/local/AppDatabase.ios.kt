package com.example.offline_first.data.local

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
fun createDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val documentsDirectory: NSURL? = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null
    )
    val dbFilePath = requireNotNull(documentsDirectory?.path) { "Could not resolve iOS documents directory" } +
        "/$DATABASE_NAME"
    return Room.databaseBuilder<AppDatabase>(name = dbFilePath)
}
