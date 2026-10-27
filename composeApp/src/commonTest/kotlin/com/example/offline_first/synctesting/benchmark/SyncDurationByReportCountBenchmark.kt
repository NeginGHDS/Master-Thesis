package com.example.offline_first.synctesting.benchmark

import com.example.offline_first.synctesting.fakes.NetworkCondition
import com.example.offline_first.synctesting.support.SyncTestHarness
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * **Sync time versus queue length.**
 *
 * Read the numbers for what they are. There is no real network here: each request is charged a
 * fixed modelled [ROUND_TRIP] under `runTest`'s virtual clock, which the test framework advances
 * instantly. So *modelled* time answers "given a round trip of R, how long does a queue of N take,
 * and how does that scale?" — a property of `SyncManager`'s algorithm, reproducible on any machine.
 * It is **not** a measurement of Firestore, and must not be presented as one. Real end-to-end
 * figures have to come from a device run against a live project; see `synctesting/README.md`.
 *
 * *Overhead* time is the real wall clock spent on everything that is not modelled network — the DAO
 * writes, the copies, the loop itself.
 */
class SyncDurationByReportCountBenchmark {

    private companion object {
        /** Modelled cost of one Firestore document write. */
        val ROUND_TRIP = 40.milliseconds

        val QUEUE_SIZES = listOf(1, 10, 25, 50, 100)
    }

    private data class Measurement(
        val reportCount: Int,
        val uploaded: Int,
        val requests: Int,
        val modelled: Duration,
        val overhead: Duration
    ) {
        val modelledPerReport: Double
            get() = modelled.inWholeMicroseconds / 1000.0 / reportCount
    }

    private suspend fun TestScope.measure(reportCount: Int): Measurement {
        val harness = SyncTestHarness(NetworkCondition.Online(ROUND_TRIP))
        harness.enqueuePending(count = reportCount)

        val virtualStart = testScheduler.currentTime
        val wallStart = TimeSource.Monotonic.markNow()
        val uploaded = harness.syncManager.sync()
        val wallElapsed = wallStart.elapsedNow()
        val modelled = (testScheduler.currentTime - virtualStart).milliseconds

        assertEquals(reportCount, uploaded, "benchmark run for N=$reportCount did not fully drain")

        return Measurement(
            reportCount = reportCount,
            uploaded = uploaded,
            requests = harness.remote.upsertAttempts,
            modelled = modelled,
            // wall clock minus the delays the virtual clock skipped is just the wall clock here,
            // because `delay` under runTest returns immediately without sleeping
            overhead = wallElapsed
        )
    }

    @Test
    fun measureSyncDurationAcrossQueueSizes() = runTest {
        val measurements = QUEUE_SIZES.map { measure(it) }

        println()
        println("Sync duration by queue length (modelled round trip = $ROUND_TRIP/request)")
        println("| reports | requests | modelled sync time | per report | local overhead (real) |")
        println("|--------:|---------:|-------------------:|-----------:|----------------------:|")
        measurements.forEach { m ->
            println(
                "| ${m.reportCount} | ${m.requests} | ${m.modelled} | " +
                    "${m.modelledPerReport} ms | ${m.overhead} |"
            )
        }
        println()

        measurements.forEach { m ->
            assertEquals(
                m.reportCount,
                m.requests,
                "N=${m.reportCount} should cost exactly one request per report"
            )
            assertEquals(
                (m.reportCount * ROUND_TRIP.inWholeMilliseconds).milliseconds,
                m.modelled,
                "N=${m.reportCount} should take reports x round trip"
            )
        }
    }

    @Test
    fun syncTimeGrowsLinearlyWithQueueLength() = runTest {
        val ten = measure(10)
        val hundred = measure(100)

        // Uploads run sequentially in a single loop with no batching and no concurrency, so ten
        // times the reports costs ten times the time. This is the benchmark's actual finding, and
        // the obvious place to look if sync latency ever needs to come down.
        assertEquals(
            ten.modelled.inWholeMilliseconds * 10,
            hundred.modelled.inWholeMilliseconds,
            "sync cost should be linear in queue length"
        )
    }

    @Test
    fun localBookkeepingIsCheapComparedToTheNetwork() = runTest {
        val hundred = measure(100)

        // Sanity guard, deliberately loose so it does not flake on a slow CI machine: the work the
        // app itself does per report must stay far below one modelled round trip.
        val overheadPerReport = hundred.overhead / 100
        assertTrue(
            overheadPerReport < ROUND_TRIP,
            "per-report local overhead ($overheadPerReport) should be well under one round trip ($ROUND_TRIP)"
        )
    }

    @Test
    fun aQueueThatFailsCostsTheSameNumberOfRequestsAsOneThatSucceeds() = runTest {
        val harness = SyncTestHarness(NetworkCondition.AirplaneMode(ROUND_TRIP))
        harness.enqueuePending(count = 50)

        val start = testScheduler.currentTime
        harness.syncManager.sync()
        val modelled = (testScheduler.currentTime - start).milliseconds

        // Worth stating in the write-up: a doomed sync is not cheaper. The queue is walked in full
        // and every report is attempted, so an offline device pays the same 50 timeouts each pass.
        assertEquals(50, harness.remote.upsertAttempts)
        assertEquals((50 * ROUND_TRIP.inWholeMilliseconds).milliseconds, modelled)
    }
}
