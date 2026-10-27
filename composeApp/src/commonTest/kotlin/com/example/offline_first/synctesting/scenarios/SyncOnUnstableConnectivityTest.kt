package com.example.offline_first.synctesting.scenarios

import com.example.offline_first.data.model.SyncStatus
import com.example.offline_first.synctesting.fakes.NetworkCondition
import com.example.offline_first.synctesting.support.SyncTestHarness
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **Unstable connectivity.** A link that drops some requests but not others — the case that
 * separates a real offline-first design from one that only handles "fully on" and "fully off".
 *
 * The link fails every Nth request deterministically, so the expected split between uploaded and
 * still-pending reports is exact rather than approximate.
 */
class SyncOnUnstableConnectivityTest {

    @Test
    fun aDroppedRequestOnlyStrandsItsOwnReport() = runTest {
        // 12 reports, every 3rd request dropped -> attempts 3, 6, 9 and 12 fail
        val harness = SyncTestHarness(NetworkCondition.Unstable(failEveryNth = 3))
        harness.enqueuePending(count = 12)

        val uploaded = harness.syncManager.sync()

        assertEquals(8, uploaded, "the eight requests that got through should have been applied")
        assertEquals(4, harness.remote.upsertFailures)
        assertEquals(8, harness.syncedCount())
        assertEquals(4, harness.pendingCount(), "a dropped request must not abort the rest of the queue")
        assertEquals(0, harness.failedCount(), "a dropped request is a network problem, not a rejection")
        assertEquals(8, harness.remote.documentCount)
    }

    @Test
    fun theNextSyncPicksUpExactlyTheReportsThatFailed() = runTest {
        val harness = SyncTestHarness(NetworkCondition.Unstable(failEveryNth = 3))
        harness.enqueuePending(count = 12)
        harness.syncManager.sync()

        // link recovers
        harness.network = NetworkCondition.Online()
        val uploadedOnRetry = harness.syncManager.sync()

        assertEquals(4, uploadedOnRetry, "only the four stranded reports should be retried")
        assertEquals(12, harness.syncedCount())
        assertEquals(0, harness.pendingCount())
        assertEquals(12, harness.remote.documentCount, "and no duplicates should have been created")
    }

    @Test
    fun connectivityLostPartWayThroughAQueueIsResumedLater() = runTest {
        val harness = SyncTestHarness(NetworkCondition.Online())
        harness.enqueuePending(count = 6)

        // network holds for the first sync, then drops entirely before more reports are added
        harness.syncManager.sync()
        harness.network = NetworkCondition.AirplaneMode()
        harness.enqueuePending(count = 4) // four more created while offline
        val duringOutage = harness.syncManager.sync()

        assertEquals(0, duringOutage)
        assertEquals(6, harness.remote.documentCount)
        assertEquals(4, harness.pendingCount())

        harness.network = NetworkCondition.Online()
        val afterRecovery = harness.syncManager.sync()

        assertEquals(4, afterRecovery)
        assertEquals(10, harness.remote.documentCount)
        assertEquals(0, harness.pendingCount())
    }

    @Test
    fun aPhotoUploadedBeforeAFailedReportUploadIsNotUploadedTwice() = runTest {
        // Attempt 1 (the report upsert for report-001) is fine; the second request is dropped.
        // The photo, however, is pushed to Storage *before* the report - and its URL is persisted
        // immediately - so the retry must reuse it rather than paying for the upload again.
        val harness = SyncTestHarness(NetworkCondition.Unstable(failEveryNth = 2))
        harness.enqueuePending(count = 2, photoPath = "/local/cache/photo.jpg")

        harness.syncManager.sync()

        assertEquals(2, harness.photos.uploadedLocalIds.size, "both photos reached Storage")
        assertEquals(1, harness.pendingCount(), "but the second report's upsert was dropped")
        val stranded = harness.dao.snapshot.single { it.syncStatus == SyncStatus.PENDING }
        assertNotNull(stranded.remotePhotoUrl, "its photo URL must be persisted so the retry can skip Storage")

        harness.network = NetworkCondition.Online()
        harness.syncManager.sync()

        assertEquals(2, harness.photos.uploadedLocalIds.size, "the retry must not re-upload the photo")
        assertEquals(2, harness.syncedCount())
    }

    @Test
    fun aReportIsNeverMarkedSyncedUnlessItsRequestActuallySucceeded() = runTest {
        val harness = SyncTestHarness(NetworkCondition.Unstable(failEveryNth = 2))
        harness.enqueuePending(count = 10)

        harness.syncManager.sync()

        val syncedLocalIds = harness.dao.snapshot
            .filter { it.syncStatus == SyncStatus.SYNCED }
            .map { it.localId }
        assertTrue(syncedLocalIds.isNotEmpty(), "some reports should have got through")
        syncedLocalIds.forEach { localId ->
            assertTrue(
                harness.remote.containsLocalId(localId),
                "$localId claims to be SYNCED but is not in the backend"
            )
        }
    }
}
