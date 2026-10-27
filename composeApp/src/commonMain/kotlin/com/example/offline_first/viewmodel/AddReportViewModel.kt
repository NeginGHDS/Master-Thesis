package com.example.offline_first.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offline_first.AppContainer
import com.example.offline_first.data.model.Report
import com.example.offline_first.data.model.SyncStatus
import com.example.offline_first.util.ioDispatcher
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The faults a campus maintenance report can be filed under.
 *
 * These are deliberately the things that break in teaching rooms and labs, not city-wide civic
 * problems: the reporter is a student or member of staff standing in front of a dead projector,
 * and the list has to let them say so in one tap.
 */
val REPORT_CATEGORIES = listOf(
    "Projector/AV",
    "Furniture",
    "Network/Wi-Fi",
    "Air conditioning",
    "Lab equipment",
    "Other"
)

data class AddReportUiState(
    val isEditing: Boolean = false,
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val location: String = "",
    val photoPath: String? = null,
    val titleError: String? = null,
    val descriptionError: String? = null,
    val categoryError: String? = null,
    val locationError: String? = null,
    val submitEnabled: Boolean = true
)

sealed interface AddReportEvent {
    data class Saved(val message: String) : AddReportEvent
    data object NotFound : AddReportEvent
}

/**
 * Ported from `AddReportActivity`. Owns form state, validation and the create/edit save logic
 * (new reports get a random UUID `localId` and `PENDING` status — unchanged so the auto-sync loop
 * in [MainViewModel] picks them up the moment the device is back online). Photo capture/picking is
 * a platform UI concern (camera/gallery); this ViewModel only stores the resulting local file path.
 */
@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class AddReportViewModel(private val container: AppContainer) : ViewModel() {

    private val dao = container.reportDao
    private val auth: FirebaseAuth = container.auth

    private val _uiState = MutableStateFlow(AddReportUiState())
    val uiState: StateFlow<AddReportUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<AddReportEvent?>(null)
    val events: StateFlow<AddReportEvent?> = _events.asStateFlow()

    private var editingReport: Report? = null

    fun consumeEvent() {
        _events.value = null
    }

    /** Mirrors `AddReportActivity.loadForEditing`. */
    fun loadForEditing(reportId: Long) {
        viewModelScope.launch {
            val report = withContext(ioDispatcher) { dao.getById(reportId) }
            if (report == null) {
                _events.value = AddReportEvent.NotFound
                return@launch
            }
            editingReport = report
            _uiState.value = AddReportUiState(
                isEditing = true,
                title = report.title,
                description = report.description,
                category = report.category,
                location = report.location,
                photoPath = report.photoPath
            )
        }
    }

    fun onTitleChanged(value: String) {
        _uiState.value = _uiState.value.copy(title = value, titleError = null)
    }

    fun onDescriptionChanged(value: String) {
        _uiState.value = _uiState.value.copy(description = value, descriptionError = null)
    }

    fun onCategoryChanged(value: String) {
        _uiState.value = _uiState.value.copy(category = value, categoryError = null)
    }

    fun onLocationChanged(value: String) {
        _uiState.value = _uiState.value.copy(location = value, locationError = null)
    }

    fun setPhoto(path: String?) {
        _uiState.value = _uiState.value.copy(photoPath = path)
    }

    fun submitReport() {
        val state = _uiState.value
        val title = state.title.trim()
        val description = state.description.trim()
        val category = state.category.trim()
        val location = state.location.trim()

        val titleError = if (title.isEmpty()) "Title is required" else null
        val descriptionError = if (description.isEmpty()) "Description is required" else null
        val categoryError = if (category.isEmpty()) "Category is required" else null
        val locationError = if (location.isEmpty()) "Location is required" else null

        if (titleError != null || descriptionError != null || categoryError != null || locationError != null) {
            _uiState.value = state.copy(
                titleError = titleError,
                descriptionError = descriptionError,
                categoryError = categoryError,
                locationError = locationError
            )
            return
        }

        _uiState.value = state.copy(submitEnabled = false)

        val existing = editingReport
        if (existing != null) {
            val updated = existing.copy(
                title = title,
                description = description,
                category = category,
                location = location,
                photoPath = state.photoPath,
                updatedAt = Clock.System.now().toEpochMilliseconds(),
                syncStatus = SyncStatus.PENDING
            )
            viewModelScope.launch {
                withContext(ioDispatcher) { dao.update(updated) }
                _events.value = AddReportEvent.Saved("Report updated.")
            }
        } else {
            val now = Clock.System.now().toEpochMilliseconds()
            val owner = auth.currentUser?.email.orEmpty()
            val report = Report(
                localId = Uuid.random().toString(),
                remoteId = null,
                title = title,
                description = description,
                category = category,
                location = location,
                photoPath = state.photoPath,
                owner = owner,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.PENDING
            )
            viewModelScope.launch {
                withContext(ioDispatcher) { dao.insert(report) }
                _events.value = AddReportEvent.Saved("Report saved locally.")
            }
        }
    }
}
