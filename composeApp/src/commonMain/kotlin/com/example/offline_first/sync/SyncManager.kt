package com.example.offline_first.sync

import com.example.offline_first.data.local.ReportDao
import com.example.offline_first.data.model.Report
import com.example.offline_first.data.model.SyncStatus
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class SyncManager(
    private val dao: ReportDao,
    private val storageManager: StorageManager
) {

    private val collection = Firebase.firestore.collection("reports")

    /** Returns the number of reports successfully uploaded. */
    suspend fun sync(): Int {
        val pending = dao.getPending()
        val authUser = Firebase.auth.currentUser
        println("SyncManager.sync: pending=${pending.size}, authedAs=${authUser?.email ?: "null (not signed in)"}")
        val uploaded = uploadPending(pending)
        deleteRemotePending()
        return uploaded
    }

    suspend fun syncDeletes() {
        deleteRemotePending()
    }

    private suspend fun uploadPending(pending: List<Report>): Int {
        var count = 0
        for (report in pending) {
            try {
                // Upload photo to Firebase Storage if not yet uploaded
                val synced = if (report.photoPath != null && report.remotePhotoUrl == null) {
                    val url = storageManager.uploadPhoto(report.localId, report.photoPath)
                    if (url != null) {
                        val withPhoto = report.copy(remotePhotoUrl = url)
                        dao.update(withPhoto)
                        withPhoto
                    } else {
                        report
                    }
                } else {
                    report
                }

                val docRef = if (synced.remoteId != null) {
                    collection.document(synced.remoteId)
                } else {
                    collection.document(synced.localId)
                }
                println("SyncManager: uploading ${synced.localId} owner='${synced.owner}'")
                docRef.set(synced.toRemote())
                dao.update(
                    synced.copy(
                        remoteId = docRef.id,
                        syncStatus = SyncStatus.SYNCED,
                        updatedAt = Clock.System.now().toEpochMilliseconds()
                    )
                )
                println("SyncManager: uploaded ${synced.localId} OK")
                count++
            } catch (e: Exception) {
                println("SyncManager: upload FAILED for ${report.localId}: ${e::class.simpleName}: ${e.message}")
            }
        }
        return count
    }

    private suspend fun deleteRemotePending() {
        for (report in dao.getDeletedPending()) {
            try {
                val docId = report.remoteId ?: report.localId
                collection.document(docId).delete()
                if (report.remotePhotoUrl != null) {
                    storageManager.deletePhoto(report.localId)
                }
                dao.delete(report)
            } catch (e: Exception) {
                println("SyncManager: delete FAILED for ${report.localId}: ${e::class.simpleName}: ${e.message}")
            }
        }
    }
}

/** Mirrors the field set the original `Report.toMap()` uploaded (excludes local-only `id`/`syncStatus`). */
@Serializable
private data class RemoteReport(
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

private fun Report.toRemote() = RemoteReport(
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
