package com.example.offline_first.synctesting.fakes

import com.example.offline_first.sync.BackendUnavailableException
import com.example.offline_first.sync.NetworkUnavailableException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

/**
 * The network conditions the sync scenarios are run under.
 *
 * Every condition is **deterministic** — no randomness, no wall-clock sleeping — so a thesis run
 * produces the same numbers on every machine. Latencies are modelled with `delay`, which under
 * `runTest` advances virtual time instead of really waiting; see `SyncDurationByReportCountBenchmark`.
 */
sealed interface NetworkCondition {

    /** Latency charged to a request that reaches the backend, or to one that fails. */
    val roundTripLatency: Duration

    /** Connected, backend healthy. Every request succeeds after [roundTripLatency]. */
    data class Online(
        override val roundTripLatency: Duration = ZERO
    ) : NetworkCondition

    /**
     * Radio off. Requests fail fast with [NetworkUnavailableException] and never reach the backend —
     * this is what the device reports with airplane mode engaged.
     */
    data class AirplaneMode(
        override val roundTripLatency: Duration = ZERO
    ) : NetworkCondition

    /**
     * Device *is* connected, but the backend rejects the write ([BackendUnavailableException]) —
     * Firestore down, project quota exhausted, or a security rule denying the write. Distinct from
     * [AirplaneMode] because the app cannot avoid it by checking connectivity first, and because
     * `SyncManager` records it as `FAILED` rather than `PENDING`.
     */
    data class BackendUnavailable(
        override val roundTripLatency: Duration = ZERO
    ) : NetworkCondition

    /**
     * A weak link that drops some requests: every [failEveryNth] attempt throws
     * [NetworkUnavailableException], the rest succeed. Counting attempts (rather than rolling dice)
     * keeps the failure count exactly predictable — with `failEveryNth = 3`, attempts 3, 6, 9 … fail.
     */
    data class Unstable(
        val failEveryNth: Int,
        override val roundTripLatency: Duration = ZERO
    ) : NetworkCondition {
        init {
            require(failEveryNth >= 2) { "failEveryNth must be >= 2, otherwise every request fails" }
        }
    }
}

// The two failure types the conditions throw live in production code
// (`com.example.offline_first.sync.SyncFailures`), because `SyncManager` has to tell them apart to
// choose between PENDING and FAILED. The simulated store throws exactly what a real
// `FirestoreReportStore` would.
