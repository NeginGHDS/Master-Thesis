package com.example.offline_first.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.offline_first.data.model.SyncStatus

private val SyncSynced = Color(0xFF4CAF50)
private val SyncPending = Color(0xFFFF9800)
private val SyncDeleting = Color(0xFFF44336)

data class SyncStatusInfo(val label: String, val color: Color, val icon: ImageVector)

fun syncStatusInfo(status: SyncStatus): SyncStatusInfo = when (status) {
    SyncStatus.SYNCED -> SyncStatusInfo("Synced", SyncSynced, Icons.Default.CloudDone)
    SyncStatus.PENDING -> SyncStatusInfo("Pending", SyncPending, Icons.Default.CloudUpload)
    SyncStatus.FAILED -> SyncStatusInfo("Failed", SyncDeleting, Icons.Default.CloudOff)
    SyncStatus.DELETED_PENDING -> SyncStatusInfo("Delete Pending", SyncDeleting, Icons.Default.Restore)
}
