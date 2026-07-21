package com.example.offline_first.sync

import com.example.offline_first.util.readFileBytes
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.storage.Data
import dev.gitlive.firebase.storage.storage

class StorageManager {

    private val storage = Firebase.storage

    /** Uploads the local file at [localPath] and returns its public download URL, or null on failure. */
    suspend fun uploadPhoto(localId: String, localPath: String): String? {
        val bytes = readFileBytes(localPath) ?: return null
        return try {
            val ref = storage.reference.child("photos/$localId.jpg")
            ref.putData(Data(bytes))
            ref.getDownloadUrl()
        } catch (e: Exception) {
            println("StorageManager: upload failed for $localId: ${e.message}")
            null
        }
    }

    /** Deletes the photo uploaded for [localId], if any. Safe to call when nothing was uploaded. */
    suspend fun deletePhoto(localId: String) {
        try {
            storage.reference.child("photos/$localId.jpg").delete()
        } catch (e: Exception) {
            println("StorageManager: delete failed for $localId: ${e.message}")
        }
    }
}
