package com.example.offline_first.sync

/**
 * The photo half of sync, behind an interface for the same reason as [RemoteReportStore]: it lets
 * [SyncManager] run in tests without Firebase Storage. Production uses [StorageManager].
 *
 * Unlike [RemoteReportStore], both methods swallow their own failures. When [uploadPhoto] returns
 * null, [SyncManager] still uploads the report's text without a `remotePhotoUrl`, but keeps the row
 * `PENDING` so the next sync retries the photo; it only becomes `SYNCED` once the photo is up too.
 */
interface PhotoUploader {

    /** Uploads the local file at [localPath] and returns its public download URL, or null on failure. */
    suspend fun uploadPhoto(localId: String, localPath: String): String?

    /** Deletes the photo uploaded for [localId], if any. Safe to call when nothing was uploaded. */
    suspend fun deletePhoto(localId: String)
}
