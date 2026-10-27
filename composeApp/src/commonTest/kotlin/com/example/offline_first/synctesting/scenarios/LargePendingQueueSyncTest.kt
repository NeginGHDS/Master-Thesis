package com.example.offline_first.synctesting.scenarios

import com.example.offline_first.synctesting.fakes.NetworkCondition
import com.example.offline_first.synctesting.support.SyncTestHarness
import com.example.offline_first.synctesting.support.TestReports
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Large queue.** A user who has been offline for a long stretch comes back with 50–100 reports
 * waiting. The risk at this size is not failure but *waste*: re-sending reports that already
 * landed, or losing part of the queue when the network gives out halfway through.
 */
class LargePendingQueueSyncTest {

    @Test
    fun aQueueOfFiftyReportsDrainsCompletely() = runTest {
        val harness = SyncTestHarness(NetworkCondition.Online())
        harness.enqueuePending(count = 50)

        val uploaded = harness.syncManager.sync()

        assertEquals(50, uploaded)
        assertEquals(50, harness.remote.documentCount)
        assertEquals(50, harness.syncedCount())
        assertEquals(0, harness.pendingCount())
    }

    @Test
    fun aQueueOfOneHundredReportsDrainsCompletely() = runTest {
        val harness = SyncTestHarness(NetworkCondition.Online())
        harness.enqueuePending(count = 100)

        val uploaded = harness.syncManager.sync()

        assertEquals(100, uploaded)
        assertEquals(100, harness.remote.documentCount)
        assertEquals(100, harness.syncedCount())
        assertEquals(0, harness.pendingCount())
    }

    @Test
    fun oneHundredReportsCostExactlyOneHundredRequests() = runTest {
        val harness = SyncTestHarness(NetworkCondition.Online())
        harness.enqueuePending(count = 100)

        harness.syncManager.sync()

        // Uploads are one document write each — there is no batching. This is the number the
        // benchmark turns into a time, and the reason drain time is linear in queue length.
        assertEquals(100, harness.remote.upsertAttempts)
        assertEquals(0, harness.remote.upsertFailures)
    }

    @Test
    fun everyLocalIdInALargeQueueArrivesExactlyOnce() = runTest {
        val harness = SyncTestHarness(NetworkCondition.Online())
        harness.enqueuePending(count = 100)

        harness.syncManager.sync()

        val arrivedLocalIds = harness.remote.documents.values.map { it.localId }
        assertEquals(100, arrivedLocalIds.distinct().size, "no localId may arrive twice")
        (1..100).forEach { index ->
            assertTrue(
                TestReports.localIdFor(index) in arrivedLocalIds,
                "${TestReports.localIdFor(index)} is missing from the backend"
            )
        }
    }

    @Test
    fun aSecondSyncOverALargeDrainedQueueSendsNothing() = runTest {
        val harness = SyncTestHarness(NetworkCondition.Online())
        harness.enqueuePending(count = 100)
        harness.syncManager.sync()

        val uploaded = harness.syncManager.sync()

        assertEquals(0, uploaded)
        assertEquals(
            100,
            harness.remote.upsertAttempts,
            "already-SYNCED reports must never be re-sent"
        )
    }

    @Test
    fun aLargeQueueInterruptedPartWayThroughResumesWithoutDuplicates() = runTest {
        // Every 4th request on a weak link is dropped: 100 attempts -> 25 failures, 75 through.
        val harness = SyncTestHarness(NetworkCondition.Unstable(failEveryNth = 4))
        harness.enqueuePending(count = 100)

        val firstPass = harness.syncManager.sync()

        assertEquals(75, firstPass)
        assertEquals(25, harness.pendingCount())
        assertEquals(75, harness.remote.documentCount)

        harness.network = NetworkCondition.Online()
        val secondPass = harness.syncManager.sync()

        assertEquals(25, secondPass, "only the stranded reports are retried")
        assertEquals(100, harness.remote.documentCount, "and the total is exactly 100, not 175")
        assertEquals(0, harness.pendingCount())
        assertEquals(125, harness.remote.upsertAttempts, "100 first-pass + 25 retries")
    }

    @Test
    fun aLargeQueueSurvivesLosingTheNetworkEntirely() = runTest {
        val harness = SyncTestHarness(NetworkCondition.AirplaneMode())
        harness.enqueuePending(count = 100)

        harness.syncManager.sync()

        assertEquals(100, harness.pendingCount(), "not one of the hundred may be lost")
        assertEquals(100, harness.localRowCount())
        assertEquals(0, harness.remote.documentCount)
    }
}
