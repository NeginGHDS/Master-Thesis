package com.example.offline_first.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.offline_first.data.model.Report
import com.example.offline_first.data.model.SyncStatus
import com.example.offline_first.ui.util.syncStatusInfo

@Composable
fun ReportListItem(
    report: Report,
    onClick: (Report) -> Unit,
    onDeleteClick: (Report) -> Unit,
    onRestoreClick: (Report) -> Unit
) {
    val statusInfo = syncStatusInfo(report.syncStatus)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(report) }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(report.title, style = MaterialTheme.typography.titleMedium)
                    if (report.photoPath != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Has attachment",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(report.description, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                Spacer(modifier = Modifier.height(4.dp))
                Text(report.category, style = MaterialTheme.typography.labelMedium)

                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(report.location, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = statusInfo.icon,
                        contentDescription = null,
                        tint = statusInfo.color,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(statusInfo.label, color = statusInfo.color, style = MaterialTheme.typography.labelMedium)
                }
            }

            if (report.syncStatus == SyncStatus.DELETED_PENDING) {
                IconButton(onClick = { onRestoreClick(report) }) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = "Restore report",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                IconButton(onClick = { onDeleteClick(report) }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove report",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
