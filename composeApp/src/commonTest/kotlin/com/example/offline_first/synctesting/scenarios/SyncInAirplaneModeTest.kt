package com.example.offline_first.synctesting.scenarios

import com.example.offline_first.data.model.SyncStatus
import com.example.offline_first.synctesting.fakes.NetworkCondition
import com.example.offline_first.synctesting.support.SyncTestHarness
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Airplane mode.** The core offline-first claim: with no network at all, creating reports must
 * still work and nothing may be lost. Every assertion here is about *absence of damage* — the queue
 * survives intact and is still exactly as long as it was.
 */
class SyncInAirplaneModeTest {

    @Test
    fun creatingTenReportsOfflineKeepsThemAllQueuedLocally() = runTest {
        val harness = SyncTestHarness(NetworkCondition.AirplaneMode())

        harness.enqueuePending(count = 10)

        assertEquals(10, harness.localRowCount(), "all ten reports are stored on the device")
        assertEquals(10, harness.pendingCount(), "and all ten are waiting to be uploaded")
    }

    @Test
    fun syncingInAirplaneModeUploadsNothingAndLosesNothing() = runTest {
        val harness = SyncTestHarness(NetworkCondition.AirplaneMode())
        harness.enqueuePending(count = 10)

        val uploaded = harness.syncManager.sync()

        assertEquals(0, uploaded, "nothing can be uploaded with the radio off")
        assertEquals(0, harness.remote.documentCount, "the backend must be untouched")
        assertEquals(10, harness.pendingCount(), "every report stays PENDING for the next attempt")
        assertEquals(10, harness.localRowCount(), "no report may be dropped")
        assertEquals(0, harness.syncedCount(), "nothing may be falsely marked as synced")
        assertEquals(
            0,
            harness.failedCount(),
            "having no network is not a failure the user can act on — FAILED is reserved for a " +
                "write the server actually refused"
        )
    }

    @Test
    fun repeatedFailedSyncsDoNotDegradeTheQueue() = runTest {
        val harness = SyncTestHarness(NetworkCondition.AirplaneMode())
        harness.enqueuePending(count = 10)

        repeat(5) { harness.syncManager.sync() }

        assertEquals(10, harness.pendingCount(), "five failed syncs must leave the queue unchanged")
        assertEquals(50, harness.remote.upsertAttempts, "each sync retries the whole queue")
        assertEquals(50, harness.remote.upsertFailures, "and every one of those attempts failed")
        assertEquals(0, harness.remote.documentCount)
    }

    @Test
    fun failedUploadsLeaveRemoteIdUnsetSoTheRetryStaysIdempotent() = runTest {
        val harness = SyncTestHarness(NetworkCondition.AirplaneMode())
        harness.enqueuePending(count = 4)

        harness.syncManager.sync()

        harness.dao.snapshot.forEach { report ->
            assertNull(report.remoteId, "${report.localId} must not claim a remote ID it never got")
            assertEquals(SyncStatus.PENDING, report.syncStatus)
        }
    }

    @Test
    fun deletionsMadeOfflineSurviveAsTombstonesUntilTheyCanBeSent() = runTest {
        val harness = SyncTestHarness(NetworkCondition.Online())
        val reports = harness.enqueuePending(count = 3)
        harness.syncManager.sync()

        // user deletes a report, then loses the network before it can be pushed
        harness.network = NetworkCondition.AirplaneMode()
        harness.markForDeletion(reports[0])
        harness.syncManager.sync()

        assertEquals(1, harness.deletedPendingCount(), "the tombstone must be kept, not discarded")
        assertEquals(3, harness.localRowCount(), "the row stays on device until the remote delete lands")
        assertEquals(3, harness.remote.documentCount, "the backend still holds the document")
    }

    @Test
    fun reportsCreatedOfflineAreStillReadableOnTheDeviceForTheirOwner() = runTest {
        val harness = SyncTestHarness(NetworkCondition.AirplaneMode())
        harness.enqueuePending(count = 10)
        harness.syncManager.sync()

        val visible = harness.dao.getByOwner(com.example.offline_first.synctesting.support.TestReports.OWNER)

        assertEquals(10, visible.size, "the list screen must still show everything created offline")
        assertTrue(
            visible.zipWithNext().all { (a, b) -> a.createdAt >= b.createdAt },
            "newest first, as the list screen expects"
        )
    }
}
