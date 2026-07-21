package com.example.offline_first.sync

//import android.net.Uri
import com.example.offline_first.data.local.ReportDao
import com.example.offline_first.data.model.Report
import com.example.offline_first.data.model.SyncStatus
import com.google.firebase.firestore.FirebaseFirestore
//import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
//import java.io.File

class SyncManager(private val dao: ReportDao) {

    private val collection = FirebaseFirestore.getInstance().collection("reports")
    //private val storage = FirebaseStorage.getInstance().reference

    /** Returns the number of reports successfully uploaded. */
    suspend fun sync(): Int {
        val uploaded = uploadPending()
        deleteRemotePending()
        return uploaded
    }

    suspend fun syncDeletes() {
        deleteRemotePending()
    }

    private suspend fun uploadPending(): Int {
        var count = 0
        for (report in dao.getPending()) {
            try {
                val reportWithPhoto = report
//                val reportWithPhoto = if (report.photoPath != null && report.remotePhotoUrl == null) {
//                    val url = uploadPhoto(report.localId, report.photoPath)
//                    if (url != null) report.copy(remotePhotoUrl = url) else report
//                } else {
//                    report
//                }

                val docRef = if (reportWithPhoto.remoteId != null) {
                    collection.document(reportWithPhoto.remoteId)
                } else {
                    collection.document(reportWithPhoto.localId)
                }
                docRef.set(reportWithPhoto.toMap()).await()
                dao.update(
                    reportWithPhoto.copy(
                        remoteId = docRef.id,
                        syncStatus = SyncStatus.SYNCED,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                count++
            } catch (e: Exception) {
                // leave as PENDING — will retry on next sync
            }
        }
        return count
    }

//    private suspend fun uploadPhoto(localId: String, photoPath: String): String? {
//        return try {
//            val file = File(photoPath)
//            if (!file.exists()) return null
//            val ref = storage.child(localId).child(file.name)
//            ref.putFile(Uri.fromFile(file)).await()
//            ref.downloadUrl.await().toString()
//        } catch (e: Exception) {
//            null
//        }
//    }

    private suspend fun deleteRemotePending() {
        for (report in dao.getDeletedPending()) {
            try {
                val docId = report.remoteId ?: report.localId
                collection.document(docId).delete().await()
                dao.delete(report)
            } catch (e: Exception) {
                // leave as DELETED_PENDING — will retry on next sync
            }
        }
    }
}

private fun Report.toMap(): Map<String, Any?> = mapOf(
    "localId"        to localId,
    "title"          to title,
    "description"    to description,
    "category"       to category,
    "location"       to location,
    "photoPath"      to photoPath,
    "remotePhotoUrl" to remotePhotoUrl,
    "owner"          to owner,
    "createdAt"      to createdAt,
    "updatedAt"      to updatedAt
)