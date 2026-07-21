package com.example.offline_first.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.offline_first.data.model.Report
import com.example.offline_first.viewmodel.MainViewModel
import com.example.offline_first.viewmodel.SessionResult
import com.example.offline_first.viewmodel.SyncOutcome
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportListScreen(
    viewModel: MainViewModel,
    onOpenReport: (Long) -> Unit,
    onAddReport: () -> Unit,
    onLoggedOut: () -> Unit,
    onSessionExpired: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var menuExpanded by remember { mutableStateOf(false) }
    var aboutDialogVisible by remember { mutableStateOf(false) }
    var reportPendingRemoval by remember { mutableStateOf<Report?>(null) }

    LaunchedEffect(Unit) {
        when (val result = viewModel.checkSession()) {
            is SessionResult.LoggedIn -> viewModel.loadHome(result.displayName, result.email)
            is SessionResult.SessionExpired -> {
                snackbarHostState.showSnackbar("Session expired. Please log in again.")
                onSessionExpired()
            }
            is SessionResult.NotLoggedIn -> onSessionExpired()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(uiState.displayName.ifBlank { "Offline-First" }, style = MaterialTheme.typography.titleMedium)
                        if (uiState.email.isNotBlank()) {
                            Text(uiState.email, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            when (val outcome = viewModel.syncNow()) {
                                is SyncOutcome.UpToDate -> snackbarHostState.showSnackbar("Everything is up to date")
                                is SyncOutcome.Offline -> snackbarHostState.showSnackbar("No internet connection")
                                is SyncOutcome.Synced -> {
                                    snackbarHostState.showSnackbar("Syncing…")
                                    snackbarHostState.showSnackbar("${outcome.count} report(s) synced")
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync now")
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("About") },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                aboutDialogVisible = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Logout") },
                            leadingIcon = { Icon(Icons.Default.Logout, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                viewModel.logout()
                                onLoggedOut()
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddReport) {
                Icon(Icons.Default.Add, contentDescription = "Add report")
            }
        }
    ) { padding ->
        if (uiState.reports.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No reports yet. Tap + to add one.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.reports, key = { it.id }) { report ->
                    ReportListItem(
                        report = report,
                        onClick = { onOpenReport(it.id) },
                        onDeleteClick = { reportPendingRemoval = it },
                        onRestoreClick = { viewModel.restoreReport(it) }
                    )
                }
            }
        }
    }

    val reportToRemove = reportPendingRemoval
    if (reportToRemove != null) {
        AlertDialog(
            onDismissRequest = { reportPendingRemoval = null },
            title = { Text("Remove Report") },
            text = { Text("Are you sure you want to remove \"${reportToRemove.title}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteReport(reportToRemove)
                    reportPendingRemoval = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { reportPendingRemoval = null }) { Text("Cancel") }
            }
        )
    }

    if (aboutDialogVisible) {
        AlertDialog(
            onDismissRequest = { aboutDialogVisible = false },
            title = { Text("Offline-First") },
            text = { Text("Version 1.0\n\nReport local issues and sync them to the cloud when online.") },
            confirmButton = {
                TextButton(onClick = { aboutDialogVisible = false }) { Text("OK") }
            }
        )
    }
}
