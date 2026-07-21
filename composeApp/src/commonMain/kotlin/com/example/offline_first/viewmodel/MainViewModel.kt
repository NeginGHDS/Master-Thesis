package com.example.offline_first.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offline_first.AppContainer
import com.example.offline_first.data.model.Report
import com.example.offline_first.data.model.SyncStatus
import com.example.offline_first.util.ioDispatcher
import dev.gitlive.firebase.auth.FirebaseUser
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Result of [MainViewModel.checkSession] — mirrors the branches of the original `MainActivity.checkSession`. */
sealed interface SessionResult {
    data object SessionExpired : SessionResult
    data class LoggedIn(val displayName: String, val email: String) : SessionResult
    data object NotLoggedIn : SessionResult
}

data class HomeUiState(
    val displayName: String = "",
    val email: String = "",
    val reports: List<Report> = emptyList()
)

/**
 * Shared brain of the report-list ("home") screen — ported from `MainActivity`.
 *
 * Carries the single most important behavior in this app: the **auto-sync subscription**.
 * The instant [AppContainer.connectivityObserver] reports the device back online, [loadHome]'s
 * `syncJob` calls `syncManager.sync()`, which uploads `PENDING` reports and removes
 * `DELETED_PENDING` ones — all driven by Room's `Flow`, so [reports] updates itself with no
 * further action from the UI. This subscription is carried over **verbatim** from
 * `MainActivity.showHome`'s `syncJob`; do not change its shape.
 */
@OptIn(ExperimentalTime::class)
class MainViewModel(private val container: AppContainer) : ViewModel() {

    private val dao = container.reportDao
    private val userDao = container.userDao
    private val auth = container.auth
    private val syncManager = container.syncManager
    private val connectivityObserver = container.connectivityObserver
    private val sessionStore = container.sessionStore

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var reportFlowJob: Job? = null
    private var syncJob: Job? = null

    suspend fun checkSession(): SessionResult {
        val loginTime = sessionStore.loginTimeMillis
        val elapsed = Clock.System.now().toEpochMilliseconds() - loginTime

        if (loginTime > 0L && elapsed > com.example.offline_first.data.local.SessionStore.SESSION_DURATION_MS) {
            auth.signOut()
            sessionStore.clear()
            return SessionResult.SessionExpired
        }

        val firebaseUser: FirebaseUser? = auth.currentUser
        if (firebaseUser != null) {
            return SessionResult.LoggedIn(firebaseUser.displayName.orEmpty(), firebaseUser.email.orEmpty())
        }

        val localEmail = sessionStore.localAuthEmail
        if (localEmail != null && loginTime > 0L) {
            val localUser = userDao.findByEmail(localEmail)
            return SessionResult.LoggedIn(localUser?.displayName.orEmpty(), localEmail)
        }

        return SessionResult.NotLoggedIn
    }

    /** Mirrors `MainActivity.showHome`: (re)starts the report-list collector and the auto-sync loop. */
    fun loadHome(displayName: String, email: String) {
        _uiState.value = HomeUiState(displayName = displayName, email = email, reports = _uiState.value.reports)

        reportFlowJob?.cancel()
        reportFlowJob = viewModelScope.launch {
            dao.getByOwnerFlow(email)
                .flowOn(ioDispatcher)
                .collect { reports ->
                    _uiState.value = _uiState.value.copy(reports = reports)
                }
        }

        // auto-sync whenever network becomes available, even without any user action
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                if (online) {
                    withContext(ioDispatcher) { syncManager.sync() }
                }
            }
        }
    }

    fun deleteReport(report: Report) {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                val current = dao.getById(report.id) ?: return@withContext
                // always go through DELETED_PENDING — even if remoteId is null, the upload may
                // have partially reached Firestore (localId was used as the doc ID), so sync will
                // attempt delete using localId as fallback
                dao.update(
                    current.copy(
                        syncStatus = SyncStatus.DELETED_PENDING,
                        updatedAt = Clock.System.now().toEpochMilliseconds()
                    )
                )
                if (com.example.offline_first.util.isNetworkOnline()) {
                    syncManager.syncDeletes()
                }
            }
        }
    }

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

    /** Returns "Syncing…" style outcome of a manual sync request — mirrors `R.id.action_sync`. */
    suspend fun syncNow(): SyncOutcome {
        val hasPending = withContext(ioDispatcher) {
            dao.getPending().isNotEmpty() || dao.getDeletedPending().isNotEmpty()
        }
        return when {
            !hasPending -> SyncOutcome.UpToDate
            !com.example.offline_first.util.isNetworkOnline() -> SyncOutcome.Offline
            else -> SyncOutcome.Synced(withContext(ioDispatcher) { syncManager.sync() })
        }
    }

    fun logout() {
        viewModelScope.launch {
            auth.signOut()
            sessionStore.clear()
        }
    }
}

sealed interface SyncOutcome {
    data object UpToDate : SyncOutcome
    data object Offline : SyncOutcome
    data class Synced(val count: Int) : SyncOutcome
}
