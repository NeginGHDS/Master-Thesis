package com.example.offline_first.synctesting.scenarios

import com.example.offline_first.data.model.SyncStatus
import com.example.offline_first.synctesting.fakes.NetworkCondition
import com.example.offline_first.synctesting.support.SyncTestHarness
import com.example.offline_first.synctesting.support.TestReports
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **Baseline: internet available.** Establishes what a healthy sync looks like, so the degraded
 * scenarios have something to be compared against.
 */
class SyncWithInternetAvailableTest {

    @Test
    fun allPendingReportsReachTheBackendAndAreMarkedSynced() = runTest {
        val harness = SyncTestHarness(NetworkCondition.Online())
        harness.enqueuePending(count = 5)

        val uploaded = harness.syncManager.sync()

        assertEquals(5, uploaded, "sync() should report every queued upload")
        assertEquals(5, harness.remote.documentCount, "all five reports should exist in the backend")
        assertEquals(5, harness.syncedCount(), "all five local rows should be SYNCED")
        assertEquals(0, harness.pendingCount(), "nothing should still be waiting")
    }

    @Test
    fun eachReportCostsExactlyOneRoundTrip() = runTest {
        val harness = SyncTestHarness(NetworkCondition.Online())
        harness.enqueuePending(count = 5)

        harness.syncManager.sync()

        assertEquals(5, harness.remote.upsertAttempts, "no report should be sent more than once")
        assertEquals(0, harness.remote.upsertFailures)
    }

    @Test
    fun syncedReportsGetTheirRemoteIdFromTheLocalId() = runTest {
        val harness = SyncTestHarness(NetworkCondition.Online())
        harness.enqueuePending(count = 3)

        harness.syncManager.sync()

        // SyncManager uses localId as the Firestore document ID when remoteId is still null —
        // this is what makes a retried upload idempotent instead of creating a duplicate.
        harness.dao.snapshot.forEach { report ->
            assertEquals(report.localId, report.remoteId, "remoteId should be the localId-derived doc ID")
        }
        (1..3).forEach { index ->
            assertTrue(
                harness.remote.documents.containsKey(TestReports.localIdFor(index)),
                "backend should hold a document keyed by ${TestReports.localIdFor(index)}"
            )
        }
    }

    @Test
    fun syncingDoesNotOverwriteTheTimeTheUserLastEditedTheReport() = runTest {
        val harness = SyncTestHarness(NetworkCondition.Online())
        val before = harness.enqueuePending(count = 3)

        harness.syncManager.sync()

        // updatedAt is the edit time, and last-write-wins resolves conflicts by comparing it.
        // If sync stamped its own clock here, every upload would look like a fresh edit and the
        // comparison would be between sync times instead of edit times.
        val editTimes = before.associate { it.localId to it.updatedAt }
        harness.dao.snapshot.forEach { report ->
            assertEquals(
                editTimes[report.localId],
                report.updatedAt,
                "${report.localId} kept its edit time through a successful sync"
            )
        }
        harness.remote.documents.values.forEach { remote ->
            assertEquals(
                editTimes[remote.localId],
                remote.updatedAt,
                "the uploaded document carries the edit time, not the upload time"
            )
        }
    }

    @Test
    fun photosAreUploadedBeforeTheReportAndTheUrlIsPersisted() = runTest {
        val harness = SyncTestHarness(NetworkCondition.Online())
        harness.enqueuePending(count = 2, photoPath = "/local/cache/photo.jpg")

        harness.syncManager.sync()

        assertEquals(2, harness.photos.uploadedLocalIds.size, "both photos should be uploaded")
        harness.dao.snapshot.forEach { report ->
            assertNotNull(report.remotePhotoUrl, "${report.localId} should have kept its download URL")
        }
        harness.remote.documents.values.forEach { remote ->
            assertNotNull(remote.remotePhotoUrl, "the uploaded document should carry the photo URL")
        }
    }

    @Test
    fun aFailedPhotoUploadKeepsTheReportPendingUntilThePhotoIsUp() = runTest {
        val harness = SyncTestHarness(NetworkCondition.Online())
        harness.enqueuePending(count = 2, photoPath = "/local/cache/photo.jpg")
        harness.photos.failUploads = true

        val uploaded = harness.syncManager.sync()

        assertEquals(0, uploaded, "a report without its photo is not a finished upload")
        assertEquals(2, harness.remote.documentCount, "the text should still reach the backend")
        assertEquals(2, harness.pendingCount(), "no green badge while the photo is still on the device")
        assertEquals(0, harness.syncedCount())

        harness.photos.failUploads = false
        val retried = harness.syncManager.sync()

        assertEquals(2, retried, "the next sync should pick the reports up again")
        assertEquals(2, harness.photos.uploadedLocalIds.size, "the photos should be uploaded on retry")
        assertEquals(2, harness.syncedCount())
        assertEquals(2, harness.remote.documentCount, "the retry should update the same documents, not add new ones")
        harness.remote.documents.values.forEach { remote ->
            assertNotNull(remote.remotePhotoUrl, "the document should now carry the photo URL")
        }
    }

    @Test
    fun reportsMarkedForDeletionAreRemovedRemotelyAndLocally() = runTest {
        val harness = SyncTestHarness(NetworkCondition.Online())
        val reports = harness.enqueuePending(count = 3)
        harness.syncManager.sync()

        harness.markForDeletion(reports[0])
        harness.syncManager.sync()

        assertEquals(2, harness.remote.documentCount, "the deleted report should be gone from the backend")
        assertEquals(2, harness.localRowCount(), "the tombstone row should be removed once the delete lands")
        assertEquals(0, harness.deletedPendingCount())
    }

    @Test
    fun syncingWithAnEmptyQueueIsANoOp() = runTest {
        val harness = SyncTestHarness(NetworkCondition.Online())

        val uploaded = harness.syncManager.sync()

        assertEquals(0, uploaded)
        assertEquals(0, harness.remote.upsertAttempts, "an empty queue should not touch the network")
        assertEquals(0, harness.dao.countWithStatus(SyncStatus.PENDING))
    }
}
