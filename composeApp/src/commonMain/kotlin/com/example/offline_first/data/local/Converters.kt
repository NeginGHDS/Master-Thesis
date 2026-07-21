package com.example.offline_first.data.local

import androidx.room.TypeConverter
import com.example.offline_first.data.model.SyncStatus

class Converters {
    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String = value.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)
}
