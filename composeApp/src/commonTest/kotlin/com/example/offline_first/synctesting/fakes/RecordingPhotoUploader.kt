package com.example.offline_first.synctesting.fakes

import com.example.offline_first.sync.PhotoUploader

/**
 * Stand-in for Firebase Storage. Records what was uploaded and deleted, and can be told to fail —
 * which exercises the branch in `SyncManager.uploadPending` where the photo upload returns null: the
 * report is still pushed to Firestore without a `remotePhotoUrl`, but stays `PENDING` for a retry.
 */
class RecordingPhotoUploader(
    private val urlPrefix: String = "https://storage.test/photos/"
) : PhotoUploader {

    val uploadedLocalIds = mutableListOf<String>()
    val deletedLocalIds = mutableListOf<String>()

    /** When true, [uploadPhoto] returns null the way the real one does on any Storage error. */
    var failUploads = false

    override suspend fun uploadPhoto(localId: String, localPath: String): String? {
        if (failUploads) return null
        uploadedLocalIds += localId
        return "$urlPrefix$localId.jpg"
    }

    override suspend fun deletePhoto(localId: String) {
        deletedLocalIds += localId
    }
}
