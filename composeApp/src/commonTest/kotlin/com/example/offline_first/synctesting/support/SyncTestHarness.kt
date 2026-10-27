package com.example.offline_first.synctesting.support

import com.example.offline_first.data.model.Report
import com.example.offline_first.data.model.SyncStatus
import com.example.offline_first.sync.SyncManager
import com.example.offline_first.synctesting.fakes.InMemoryReportDao
import com.example.offline_first.synctesting.fakes.NetworkCondition
import com.example.offline_first.synctesting.fakes.RecordingPhotoUploader
import com.example.offline_first.synctesting.fakes.SimulatedRemoteReportStore

/**
 * Assembles a real [SyncManager] on top of the fakes, so every scenario exercises the production
 * upload/delete logic and only the two I/O edges (Firestore, Storage) are substituted.
 */
class SyncTestHarness(
    initialCondition: NetworkCondition = NetworkCondition.Online()
) {

    val dao = InMemoryReportDao()
    val remote = SimulatedRemoteReportStore(initialCondition)
    val photos = RecordingPhotoUploader()
    val syncManager = SyncManager(dao, photos, remote)

    /** The network the next request will meet. Assign to change conditions mid-scenario. */
    var network: NetworkCondition
        get() = remote.condition
        set(value) {
            remote.condition = value
        }

    // Report indices continue across calls, so a second batch never reuses the localIds of the
    // first — those localIds double as Firestore document IDs, and repeating one would silently
    // overwrite an already-uploaded document instead of adding a new one.
    private var nextIndex = 1

    /** Writes [count] reports to the local DB in `PENDING`, as creating them offline would. */
    suspend fun enqueuePending(
        count: Int,
        owner: String = TestReports.OWNER,
        photoPath: String? = null
    ): List<Report> = (0 until count).map {
        val report = TestReports.pending(nextIndex++, owner = owner, photoPath = photoPath)
        report.copy(id = dao.insert(report))
    }

    /** Marks an already-stored report for deletion, the way `MainViewModel.deleteReport` does. */
    suspend fun markForDeletion(report: Report) {
        val current = dao.getById(report.id) ?: error("report ${report.id} is not in the local DB")
        dao.update(current.copy(syncStatus = SyncStatus.DELETED_PENDING))
    }

    fun pendingCount(): Int = dao.countWithStatus(SyncStatus.PENDING)

    /** Reports whose last upload was refused by the backend — still queued, but visibly rejected. */
    fun failedCount(): Int = dao.countWithStatus(SyncStatus.FAILED)

    /** Everything still owed to the backend, whichever of the two reasons it is waiting for. */
    fun unsyncedCount(): Int = pendingCount() + failedCount()

    fun syncedCount(): Int = dao.countWithStatus(SyncStatus.SYNCED)
    fun deletedPendingCount(): Int = dao.countWithStatus(SyncStatus.DELETED_PENDING)
    fun localRowCount(): Int = dao.snapshot.size
}
