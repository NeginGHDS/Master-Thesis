package com.example.offline_first.synctesting.scenarios

import com.example.offline_first.synctesting.fakes.FakeConnectivityObserver
import com.example.offline_first.synctesting.fakes.NetworkCondition
import com.example.offline_first.synctesting.support.SyncTestHarness
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * **Delayed sync: ten reports created in airplane mode, then reconnect.** The end-to-end story the
 * whole design exists for — work offline, and have the queue drain by itself when the network
 * returns, with no user action.
 *
 * The auto-sync subscription is *mirrored* here rather than invoked: the real one lives in
 * `MainViewModel.loadHome`'s `syncJob`, which cannot be constructed in a unit test because
 * `AppContainer` resolves `Firebase.auth` eagerly. The shape below (collect `isOnline`, sync on
 * `true`) is copied from it verbatim; if that subscription is ever reshaped, this test must follow.
 */
class DelayedSyncAfterReconnectTest {

    private val roundTrip = 40.milliseconds

    /**
     * Lets the auto-sync subscription make progress. Note `advanceUntilIdle()` is deliberately not
     * used: it skips `backgroundScope` work, and the subscription — a `collect` that never
     * completes — lives there.
     */
    private fun TestScope.settle(within: Duration = 5.seconds) {
        advanceTimeBy(within)
        runCurrent()
    }

    @Test
    fun tenReportsCreatedOfflineUploadThemselvesWhenConnectivityReturns() = runTest {
        val harness = SyncTestHarness(NetworkCondition.AirplaneMode())
        val connectivity = FakeConnectivityObserver(initiallyOnline = false)
        harness.enqueuePending(count = 10)

        val syncResults = mutableListOf<Int>()
        backgroundScope.launch {
            connectivity.isOnline.collect { online ->
                if (online) syncResults += harness.syncManager.sync()
            }
        }
        settle()

        // still in airplane mode: the subscription is live but has had nothing to do
        assertEquals(0, harness.remote.documentCount, "nothing may be uploaded while offline")
        assertEquals(10, harness.pendingCount())
        assertTrue(syncResults.isEmpty(), "sync must not even be attempted while offline")

        // airplane mode off — the OS reports the network back
        harness.network = NetworkCondition.Online(roundTrip)
        connectivity.goOnline()
        settle()

        assertEquals(listOf(10), syncResults, "one sync pass should have drained all ten reports")
        assertEquals(10, harness.remote.documentCount)
        assertEquals(10, harness.syncedCount())
        assertEquals(0, harness.pendingCount())
    }

    @Test
    fun theQueueDrainsWithoutAnyUserAction() = runTest {
        val harness = SyncTestHarness(NetworkCondition.AirplaneMode())
        val connectivity = FakeConnectivityObserver(initiallyOnline = false)
        harness.enqueuePending(count = 10)

        // stamped from inside the subscription, so it is the moment the drain actually finished
        // rather than however far the test then chose to advance the clock
        var drainFinishedAt: Long? = null
        backgroundScope.launch {
            connectivity.isOnline.collect { online ->
                if (online) {
                    harness.syncManager.sync()
                    drainFinishedAt = testScheduler.currentTime
                }
            }
        }
        settle()

        harness.network = NetworkCondition.Online(roundTrip)
        val reconnectedAt = testScheduler.currentTime
        connectivity.goOnline()
        settle()

        val drainDuration = assertNotNull(drainFinishedAt, "the drain never completed") - reconnectedAt
        // Ten reports, one sequential round trip each -> 10 x 40 ms of modelled network time.
        assertEquals(
            10 * roundTrip.inWholeMilliseconds,
            drainDuration,
            "uploads are sequential, so drain time is reports x round trip"
        )
        assertEquals(0, harness.pendingCount())
    }

    @Test
    fun aBriefConnectivityFlapDoesNotDuplicateUploads() = runTest {
        val harness = SyncTestHarness(NetworkCondition.AirplaneMode())
        val connectivity = FakeConnectivityObserver(initiallyOnline = false)
        harness.enqueuePending(count = 10)

        backgroundScope.launch {
            connectivity.isOnline.collect { online -> if (online) harness.syncManager.sync() }
        }
        settle()

        harness.network = NetworkCondition.Online(roundTrip)
        connectivity.goOnline()
        settle()

        // network drops and comes straight back — a very common pattern on mobile
        connectivity.goOffline()
        settle()
        connectivity.goOnline()
        settle()

        assertEquals(10, harness.remote.documentCount, "the flap must not create a second copy")
        assertEquals(
            10,
            harness.remote.upsertAttempts,
            "the second sync should find an empty queue and send nothing"
        )
    }

    @Test
    fun reportsCreatedWhileStillOfflineJoinTheSameDrain() = runTest {
        val harness = SyncTestHarness(NetworkCondition.AirplaneMode())
        val connectivity = FakeConnectivityObserver(initiallyOnline = false)
        harness.enqueuePending(count = 10)

        backgroundScope.launch {
            connectivity.isOnline.collect { online -> if (online) harness.syncManager.sync() }
        }
        settle()

        // three more reports written while the radio is still off
        harness.enqueuePending(count = 3)
        assertEquals(13, harness.pendingCount())

        harness.network = NetworkCondition.Online(roundTrip)
        connectivity.goOnline()
        settle()

        assertEquals(13, harness.remote.documentCount, "all thirteen should go up in the one pass")
        assertEquals(0, harness.pendingCount())
    }
}
