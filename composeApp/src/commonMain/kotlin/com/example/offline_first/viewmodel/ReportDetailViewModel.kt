package com.example.offline_first.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offline_first.AppContainer
import com.example.offline_first.data.model.Report
import com.example.offline_first.data.model.SyncStatus
import com.example.offline_first.util.ioDispatcher
import com.example.offline_first.util.isNetworkOnline
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ReportDetailEvent {
    data object Closed : ReportDetailEvent
    data object NotFound : ReportDetailEvent
}

/**
 * Ported from `ReportDetailActivity`. Collects the single report by id (Room `Flow`, so edits and
 * sync-status changes reflect live), and — like the original `onResume` — kicks a sync if the
 * report is `PENDING` and the device is online, so opening the detail screen for a pending report
 * nudges it toward `SYNCED` without waiting for the next connectivity flip.
 */
class ReportDetailViewModel(private val container: AppContainer, private val reportId: Long) : ViewModel() {

    private val dao = container.reportDao
    private val syncManager = container.syncManager

    private val _report = MutableStateFlow<Report?>(null)
    val report: StateFlow<Report?> = _report.asStateFlow()

    private val _events = MutableStateFlow<ReportDetailEvent?>(null)
    val events: StateFlow<ReportDetailEvent?> = _events.asStateFlow()

    init {
        if (reportId == -1L) {
            _events.value = ReportDetailEvent.NotFound
        } else {
            viewModelScope.launch {
                dao.getByIdFlow(reportId)
                    .filterNotNull()
                    .flowOn(ioDispatcher)
                    .collect { report -> _report.value = report }
            }
        }
    }

    /** Mirrors `ReportDetailActivity.onResume`: call when the screen (re)appears. */
    fun onScreenResumed() {
        if (reportId == -1L) return
        viewModelScope.launch {
            val report = withContext(ioDispatcher) { dao.getById(reportId) } ?: return@launch
            if (report.syncStatus == SyncStatus.PENDING && isNetworkOnline()) {
                withContext(ioDispatcher) { syncManager.sync() }
            }
        }
    }

    fun consumeEvent() {
        _events.value = null
    }

    @OptIn(ExperimentalTime::class)
    fun confirmDelete(report: Report) {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                val current = dao.getById(report.id) ?: return@withContext
                dao.update(
                    current.copy(
                        syncStatus = SyncStatus.DELETED_PENDING,
                        updatedAt = Clock.System.now().toEpochMilliseconds()
                    )
                )
            }
            // if sync deleted, finish the screen
            _events.value = ReportDetailEvent.Closed
        }
    }

    @OptIn(ExperimentalTime::class)
    fun restoreReport(report: Report) {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                dao.update(
                    report.copy(
                        syncStatus = SyncStatus.PENDING,
                        updatedAt = Clock.System.now().toEpochMilliseconds()
                    )
                )
            }
        }
    }
}
