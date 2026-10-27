package com.example.offline_first.synctesting.fakes

import com.example.offline_first.sync.BackendUnavailableException
import com.example.offline_first.sync.NetworkUnavailableException
import com.example.offline_first.sync.RemoteReport
import com.example.offline_first.sync.RemoteReportStore
import kotlinx.coroutines.delay

/**
 * Stand-in for the `reports` Firestore collection whose behaviour is driven by [condition].
 *
 * Tests flip [condition] mid-run to reproduce the situations the app is built for: losing the
 * network halfway through a queue, coming back online, or hitting a backend that is up but refusing
 * writes. It also records attempt/failure counts, which is what the benchmark reports on.
 */
class SimulatedRemoteReportStore(
    condition: NetworkCondition = NetworkCondition.Online()
) : RemoteReportStore {

    /** Mutable so a test can change the weather between — or during — syncs. */
    var condition: NetworkCondition = condition

    private val docs = linkedMapOf<String, RemoteReport>()

    /** Attempt counter driving [NetworkCondition.Unstable]'s failure pattern; spans upserts and deletes. */
    private var attempts = 0

    var upsertAttempts = 0
        private set
    var upsertFailures = 0
        private set
    var deleteAttempts = 0
        private set
    var deleteFailures = 0
        private set

    /** Documents currently "stored in Firestore", keyed by document ID. */
    val documents: Map<String, RemoteReport> get() = docs

    val documentCount: Int get() = docs.size

    fun containsLocalId(localId: String): Boolean = docs.values.any { it.localId == localId }

    override suspend fun upsert(documentId: String, report: RemoteReport): String {
        upsertAttempts++
        try {
            transmit()
        } catch (e: Exception) {
            upsertFailures++
            throw e
        }
        docs[documentId] = report
        return documentId
    }

    override suspend fun delete(documentId: String) {
        deleteAttempts++
        try {
            transmit()
        } catch (e: Exception) {
            deleteFailures++
            throw e
        }
        docs.remove(documentId)
    }

    /** Applies the current [condition] to one request: charge its latency, then fail it or let it through. */
    private suspend fun transmit() {
        attempts++
        val current = condition
        delay(current.roundTripLatency)
        when (current) {
            is NetworkCondition.Online -> Unit

            is NetworkCondition.AirplaneMode ->
                throw NetworkUnavailableException("airplane mode: no route to Firestore")

            is NetworkCondition.BackendUnavailable ->
                throw BackendUnavailableException("backend reached but unavailable (attempt $attempts)")

            is NetworkCondition.Unstable ->
                if (attempts % current.failEveryNth == 0) {
                    throw NetworkUnavailableException("request dropped on unstable link (attempt $attempts)")
                }
        }
    }
}
