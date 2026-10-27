package com.example.offline_first.synctesting.scenarios

import com.example.offline_first.data.model.SyncStatus
import com.example.offline_first.synctesting.fakes.NetworkCondition
import com.example.offline_first.synctesting.support.SyncTestHarness
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * **Failed sync — backend unavailable.** The device is online, so the app's connectivity check says
 * "go", but the write is refused: Firestore outage, exhausted quota, or a security rule denying it
 * (for example a report whose `owner` does not match the signed-in user under `firestore.rules`).
 *
 * This is the harder failure of the two — unlike airplane mode the app cannot predict it, so the
 * defence is twofold: a refused write leaves the local queue intact, *and* it is recorded as
 * `FAILED` rather than `PENDING`, so the user is not told "waiting for internet" about a report
 * the server has already rejected.
 */
class SyncWhenBackendUnavailableTest {

    @Test
    fun aRefusedWriteMarksEveryReportFailedWithoutConsumingTheQueue() = runTest {
        val harness = SyncTestHarness(NetworkCondition.BackendUnavailable())
        harness.enqueuePending(count = 8)

        val uploaded = harness.syncManager.sync()

        assertEquals(0, uploaded)
        assertEquals(8, harness.failedCount(), "a rejected write is FAILED, not merely pending")
        assertEquals(0, harness.pendingCount(), "nothing here is waiting for a network")
        assertEquals(8, harness.unsyncedCount(), "a server-side rejection must not consume the queue")
        assertEquals(8, harness.remote.upsertAttempts, "each report was attempted once")
        assertEquals(8, harness.remote.upsertFailures)
        assertEquals(0, harness.remote.documentCount)
    }

    @Test
    fun aFailedUploadChangesNothingButTheStatus() = runTest {
        val harness = SyncTestHarness(NetworkCondition.BackendUnavailable())
        val before = harness.enqueuePending(count = 5)

        harness.syncManager.sync()

        val after = harness.dao.snapshot.sortedBy { it.id }
        assertEquals(before.size, after.size)
        before.sortedBy { it.id }.zip(after).forEach { (expected, actual) ->
            assertEquals(SyncStatus.FAILED, actual.syncStatus)
            assertNull(actual.remoteId, "no remote ID may be invented for a write that was refused")
            assertEquals(
                expected.copy(syncStatus = SyncStatus.FAILED),
                actual,
                "${expected.localId} should be unchanged apart from its status"
            )
        }
    }

    @Test
    fun aFailedReportIsStillRetriedAndTheQueueDrainsOnceTheBackendRecovers() = runTest {
        val harness = SyncTestHarness(NetworkCondition.BackendUnavailable())
        harness.enqueuePending(count = 8)
        harness.syncManager.sync()
        assertEquals(8, harness.failedCount())

        harness.network = NetworkCondition.Online()
        val uploaded = harness.syncManager.sync()

        // FAILED is a label, not a dead end: the next sync picks those reports up again.
        assertEquals(8, uploaded, "the whole queue should recover in one pass")
        assertEquals(8, harness.remote.documentCount)
        assertEquals(0, harness.unsyncedCount())
        assertEquals(8, harness.syncedCount())
    }

    @Test
    fun aFailedReportGoesBackToPendingWhenTheDeviceLosesTheNetwork() = runTest {
        val harness = SyncTestHarness(NetworkCondition.BackendUnavailable())
        harness.enqueuePending(count = 3)
        harness.syncManager.sync()
        assertEquals(3, harness.failedCount())

        // the user walks into a tunnel: the reason the report is still here has changed, and the
        // status has to change with it rather than keep accusing the server
        harness.network = NetworkCondition.AirplaneMode()
        harness.syncManager.sync()

        assertEquals(3, harness.pendingCount(), "with no network the honest status is PENDING again")
        assertEquals(0, harness.failedCount())
    }

    @Test
    fun aRefusedDeleteKeepsTheTombstoneForTheNextAttempt() = runTest {
        val harness = SyncTestHarness(NetworkCondition.Online())
        val reports = harness.enqueuePending(count = 3)
        harness.syncManager.sync()

        harness.network = NetworkCondition.BackendUnavailable()
        harness.markForDeletion(reports[0])
        harness.syncManager.syncDeletes()

        assertEquals(1, harness.deletedPendingCount(), "the tombstone must survive a refused delete")
        assertEquals(1, harness.remote.deleteFailures)
        assertEquals(3, harness.remote.documentCount, "the document is still there")

        harness.network = NetworkCondition.Online()
        harness.syncManager.syncDeletes()

        assertEquals(0, harness.deletedPendingCount())
        assertEquals(2, harness.remote.documentCount, "the retried delete finally lands")
        assertEquals(2, harness.localRowCount())
    }

    @Test
    fun photosAreNotDeletedWhenTheDocumentDeleteIsRefused() = runTest {
        val harness = SyncTestHarness(NetworkCondition.Online())
        val reports = harness.enqueuePending(count = 2, photoPath = "/local/cache/photo.jpg")
        harness.syncManager.sync()

        harness.network = NetworkCondition.BackendUnavailable()
        harness.markForDeletion(reports[0])
        harness.syncManager.syncDeletes()

        // SyncManager deletes the photo only after the document delete succeeds, so a refused
        // delete must not orphan the report by removing its photo first.
        assertEquals(0, harness.photos.deletedLocalIds.size, "the photo must outlive the failed delete")
    }
}
