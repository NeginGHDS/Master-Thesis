package com.example.offline_first.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reports")
data class Report(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val localId: String,       // UUID, generated on creation
    val remoteId: String?,     // Firestore document ID, null until synced

    val title: String,
    val description: String,
    val category: String,
    val location: String,
    val photoPath: String?,      // absolute path to local photo file, null if no photo
    val remotePhotoUrl: String? = null, // Firebase Storage download URL, null until uploaded

    val owner: String,         // email of the user who created the report

    val createdAt: Long,       // epoch millis
    val updatedAt: Long,       // epoch millis

    val syncStatus: SyncStatus
)