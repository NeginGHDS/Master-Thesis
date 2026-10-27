package com.example.offline_first.sync

import com.example.offline_first.data.local.ReportDao
import com.example.offline_first.data.model.Report
import com.example.offline_first.data.model.SyncStatus
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth

class SyncManager(
    private val dao: ReportDao,
    private val photoUploader: PhotoUploader,
    private val remote: RemoteReportStore = FirestoreReportStore()
) {

    /** Returns the number of reports successfully uploaded. */
    suspend fun sync(): Int {
        // PENDING *and* FAILED: a report the server refused is still ours to retry, it is only
        // labelled differently while it waits
        val pending = dao.getUnsynced()
        // diagnostic only — must never take the sync down, and Firebase is absent under test
        val authedAs = runCatching { Firebase.auth.currentUser?.email }.getOrNull()
        println("SyncManager.sync: pending=${pending.size}, authedAs=${authedAs ?: "null (not signed in)"}")
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
            // tracks the newest version of the row, so a failure is recorded on the copy that
            // already carries the photo URL rather than overwriting it with the stale one
            var current = report
            try {
                // Upload photo to Firebase Storage if not yet uploaded
                val photoPath = current.photoPath
                if (photoPath != null && current.remotePhotoUrl == null) {
                    val url = photoUploader.uploadPhoto(current.localId, photoPath)
                    if (url != null) {
                        current = current.copy(remotePhotoUrl = url)
                        dao.update(current)
                    }
                }

                val documentId = current.remoteId ?: current.localId
                println("SyncManager: uploading ${current.localId} owner='${current.owner}'")
                val storedId = remote.upsert(documentId, current.toRemote())
                // The text is on the server, but a photo that did not make it means the report is
                // not done: it stays PENDING so the next sync retries the photo and re-upserts the
                // same document. Marking it SYNCED here would show a green badge for a report
                // whose photo never left the device, and getUnsynced() would never look at it again.
                val photoStillOwed = current.photoPath != null && current.remotePhotoUrl == null
                // updatedAt is deliberately NOT touched here: it records when the *user* last
                // edited the report, and last-write-wins compares exactly that. Stamping the sync
                // time over it would make a report look edited at the moment it was uploaded.
                dao.update(
                    current.copy(
                        remoteId = storedId,
                        syncStatus = if (photoStillOwed) SyncStatus.PENDING else SyncStatus.SYNCED
                    )
                )
                if (photoStillOwed) {
                    println("SyncManager: uploaded ${current.localId} without its photo, kept PENDING")
                    continue
                }
                println("SyncManager: uploaded ${current.localId} OK")
                count++
            } catch (e: Exception) {
                val status = statusAfterFailure(e)
                // Only the status changes: no remoteId is invented and updatedAt is left alone, so
                // a failed attempt never makes the row look newer than the content actually is.
                if (current.syncStatus != status) {
                    dao.update(current.copy(syncStatus = status))
                }
                println(
                    "SyncManager: upload failed for ${current.localId} -> $status: " +
                        "${e::class.simpleName}: ${e.message}"
                )
            }
        }
        return count
    }

    /**
     * Turns the exception an upload died of into the status the user should see.
     *
     * Only "we never reached the server" keeps a report `PENDING`. Everything else — a refused
     * write, or an error this layer did not anticipate — becomes `FAILED`, because the device is
     * online and simply waiting will not fix it. Either way the report stays in the queue and the
     * next sync tries again; the status is what tells the two situations apart on screen.
     */
    private fun statusAfterFailure(error: Exception): SyncStatus = when (error) {
        is NetworkUnavailableException -> SyncStatus.PENDING
        else -> SyncStatus.FAILED
    }

    private suspend fun deleteRemotePending() {
        for (report in dao.getDeletedPending()) {
            try {
                val docId = report.remoteId ?: report.localId
                remote.delete(docId)
                if (report.remotePhotoUrl != null) {
                    photoUploader.deletePhoto(report.localId)
                }
                dao.delete(report)
            } catch (e: Exception) {
                println("SyncManager: delete FAILED for ${report.localId}: ${e::class.simpleName}: ${e.message}")
            }
        }
    }
}
