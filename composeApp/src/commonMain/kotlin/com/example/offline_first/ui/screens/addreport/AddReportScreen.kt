package com.example.offline_first.ui.screens.addreport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.offline_first.viewmodel.AddReportEvent
import com.example.offline_first.viewmodel.AddReportViewModel
import com.example.offline_first.viewmodel.REPORT_CATEGORIES

private val categoryIcons: Map<String, ImageVector> = mapOf(
    "Infrastructure" to Icons.Default.Construction,
    "Public Safety"  to Icons.Default.LocalPolice,
    "Vandalism"      to Icons.Default.Report,
    "Sanitation"     to Icons.Default.Delete,
    "Environment"    to Icons.Default.Eco,
    "Other"          to Icons.Default.Category
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReportScreen(
    viewModel: AddReportViewModel,
    editingReportId: Long?,
    onPickFromGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onSaved: (message: String) -> Unit,
    onClosed: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val event by viewModel.events.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(editingReportId) {
        if (editingReportId != null) viewModel.loadForEditing(editingReportId)
    }

    LaunchedEffect(event) {
        when (val e = event) {
            is AddReportEvent.Saved -> {
                viewModel.consumeEvent()
                onSaved(e.message)
            }
            is AddReportEvent.NotFound -> {
                viewModel.consumeEvent()
                onClosed()
            }
            null -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditing) "Edit Report" else "Add Report") },
                navigationIcon = {
                    IconButton(onClick = onClosed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::onTitleChanged,
                label = { Text("Title") },
                isError = uiState.titleError != null,
                supportingText = { uiState.titleError?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::onDescriptionChanged,
                label = { Text("Description") },
                isError = uiState.descriptionError != null,
                supportingText = { uiState.descriptionError?.let { Text(it) } },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            ExposedDropdownMenuBox(
                expanded = categoryMenuExpanded,
                onExpandedChange = { categoryMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = uiState.category,
                    onValueChange = viewModel::onCategoryChanged,
                    label = { Text("Category") },
                    isError = uiState.categoryError != null,
                    supportingText = { uiState.categoryError?.let { Text(it) } },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                )
                ExposedDropdownMenu(
                    expanded = categoryMenuExpanded,
                    onDismissRequest = { categoryMenuExpanded = false }
                ) {
                    REPORT_CATEGORIES.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category) },
                            leadingIcon = {
                                categoryIcons[category]?.let { icon ->
                                    Icon(icon, contentDescription = null)
                                }
                            },
                            onClick = {
                                viewModel.onCategoryChanged(category)
                                categoryMenuExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = uiState.location,
                onValueChange = viewModel::onLocationChanged,
                label = { Text("Location") },
                isError = uiState.locationError != null,
                supportingText = { uiState.locationError?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text("Photo (optional)", style = MaterialTheme.typography.labelLarge)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickFromGallery) { Text("Gallery") }
                OutlinedButton(onClick = onTakePhoto) { Text("Camera") }
            }

            if (uiState.photoPath != null) {
                AsyncImage(
                    model = "file://${uiState.photoPath}",
                    contentDescription = "Selected photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                )
                IconButton(
                    onClick = { viewModel.setPhoto(null) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Remove photo")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.submitReport() },
                enabled = uiState.submitEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (uiState.isEditing) "Save Changes" else "Submit")
            }
        }
    }
}
