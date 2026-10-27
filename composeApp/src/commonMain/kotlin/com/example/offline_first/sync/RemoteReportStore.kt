package com.example.offline_first.sync

import com.example.offline_first.data.model.Report
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import dev.gitlive.firebase.firestore.code
import dev.gitlive.firebase.firestore.firestore
import kotlinx.serialization.Serializable

/** Mirrors the field set the original `Report.toMap()` uploaded (excludes local-only `id`/`syncStatus`). */
@Serializable
data class RemoteReport(
    val localId: String,
    val title: String,
    val description: String,
    val category: String,
    val location: String,
    val photoPath: String?,
    val remotePhotoUrl: String?,
    val owner: String,
    val createdAt: Long,
    val updatedAt: Long
)

fun Report.toRemote() = RemoteReport(
    localId = localId,
    title = title,
    description = description,
    category = category,
    location = location,
    photoPath = photoPath,
    remotePhotoUrl = remotePhotoUrl,
    owner = owner,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/**
 * The remote half of sync, behind an interface purely so [SyncManager] can be driven without a live
 * Firestore. Production always uses [FirestoreReportStore]; the test source set substitutes a
 * simulated store that can model airplane mode, an unavailable backend and a flaky link.
 *
 * Both methods are expected to **throw** on failure — that is the signal [SyncManager] relies on to
 * keep the report in the queue so a later sync retries it. *Which* exception is thrown matters: a
 * [NetworkUnavailableException] means "we never reached the server", anything else means the write
 * was refused, and [SyncManager] turns that difference into `PENDING` versus `FAILED`.
 */
interface RemoteReportStore {

    /** Creates or overwrites the document at [documentId]; returns the stored document's ID. */
    suspend fun upsert(documentId: String, report: RemoteReport): String

    /** Removes the document at [documentId]. Deleting a document that is not there is not an error. */
    suspend fun delete(documentId: String)
}

/** Production [RemoteReportStore]: the `reports` collection in Cloud Firestore. */
class FirestoreReportStore(private val collectionPath: String = "reports") : RemoteReportStore {

    // lazy so that merely constructing the store does not require Firebase to be initialized yet
    private val collection by lazy { Firebase.firestore.collection(collectionPath) }

    override suspend fun upsert(documentId: String, report: RemoteReport): String = classifying {
        val docRef = collection.document(documentId)
        docRef.set(report)
        docRef.id
    }

    override suspend fun delete(documentId: String) = classifying {
        collection.document(documentId).delete()
    }

    /**
     * Runs [block] and re-throws whatever it failed with as one of this layer's two failure types,
     * so [SyncManager] never has to know about Firestore's error codes.
     */
    private inline fun <T> classifying(block: () -> T): T = try {
        block()
    } catch (e: FirebaseFirestoreException) {
        throw when (e.code) {
            // the request never got an answer from the server: retry when the network is back
            FirestoreExceptionCode.UNAVAILABLE,
            FirestoreExceptionCode.DEADLINE_EXCEEDED,
            FirestoreExceptionCode.CANCELLED ->
                NetworkUnavailableException("could not reach Firestore (${e.code})", e)

            // the server answered, and the answer was no — a security rule, a quota, bad data
            else -> BackendUnavailableException("Firestore refused the write (${e.code})", e)
        }
    }
}
