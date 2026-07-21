package com.example.offline_first.ui.screens.reportdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.offline_first.data.model.Report
import com.example.offline_first.data.model.SyncStatus
import com.example.offline_first.ui.util.formatEpochMillis
import com.example.offline_first.ui.util.syncStatusInfo
import com.example.offline_first.viewmodel.ReportDetailEvent
import com.example.offline_first.viewmodel.ReportDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailScreen(
    viewModel: ReportDetailViewModel,
    onModifyReport: (Long) -> Unit,
    onClosed: () -> Unit
) {
    val report by viewModel.report.collectAsState()
    val event by viewModel.events.collectAsState()
    var removalDialogVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.onScreenResumed()
    }

    LaunchedEffect(event) {
        when (event) {
            is ReportDetailEvent.Closed, is ReportDetailEvent.NotFound -> {
                viewModel.consumeEvent()
                onClosed()
            }
            null -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report Detail") },
                navigationIcon = {
                    IconButton(onClick = onClosed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val current = report
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Loading…")
            }
        } else {
            ReportDetailContent(
                report = current,
                modifier = Modifier.padding(padding),
                onModify = { onModifyReport(current.id) },
                onActionClick = {
                    if (current.syncStatus == SyncStatus.DELETED_PENDING) {
                        viewModel.restoreReport(current)
                    } else {
                        removalDialogVisible = true
                    }
                }
            )
        }
    }

    val reportToRemove = report
    if (removalDialogVisible && reportToRemove != null) {
        AlertDialog(
            onDismissRequest = { removalDialogVisible = false },
            title = { Text("Remove Report") },
            text = { Text("Are you sure you want to remove \"${reportToRemove.title}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    removalDialogVisible = false
                    viewModel.confirmDelete(reportToRemove)
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { removalDialogVisible = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ReportDetailContent(
    report: Report,
    modifier: Modifier = Modifier,
    onModify: () -> Unit,
    onActionClick: () -> Unit
) {
    val statusInfo = syncStatusInfo(report.syncStatus)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(report.title, style = MaterialTheme.typography.headlineSmall)
        Text(report.category, style = MaterialTheme.typography.labelLarge)

        Spacer(modifier = Modifier.height(8.dp))
        Text(report.description, style = MaterialTheme.typography.bodyLarge)

        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(report.location, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Created: ${formatEpochMillis(report.createdAt)}\nUpdated: ${formatEpochMillis(report.updatedAt)}",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = statusInfo.icon, contentDescription = null, tint = statusInfo.color, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(statusInfo.label, color = statusInfo.color, style = MaterialTheme.typography.labelLarge)
        }

        if (report.photoPath != null) {
            Spacer(modifier = Modifier.height(8.dp))
            AsyncImage(
                model = "file://${report.photoPath}",
                contentDescription = "Report photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(220.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(8.dp))
            Text("No attachments", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (report.syncStatus == SyncStatus.DELETED_PENDING) {
            Button(
                onClick = onActionClick,
                colors = ButtonDefaults.buttonColors(containerColor = statusInfoForDeletePending().color),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Restore") }
        } else {
            OutlinedButton(onClick = onModify, modifier = Modifier.fillMaxWidth()) { Text("Modify") }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onActionClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Delete") }
        }
    }
}

@Composable
private fun statusInfoForDeletePending() = syncStatusInfo(SyncStatus.PENDING)
