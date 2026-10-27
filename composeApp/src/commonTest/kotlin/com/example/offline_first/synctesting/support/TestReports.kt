package com.example.offline_first.synctesting.support

import com.example.offline_first.data.model.Report
import com.example.offline_first.data.model.SyncStatus

/**
 * Builds the report rows the scenarios queue up. Everything is derived from an index so identifiers
 * and timestamps are stable across runs — a failing assertion always names the same report.
 */
object TestReports {

    const val OWNER = "tester@example.test"

    /** Fixed epoch base (2025-01-01T00:00:00Z) so `createdAt` ordering is deterministic. */
    private const val BASE_CREATED_AT = 1_735_689_600_000L

    fun localIdFor(index: Int): String = "report-" + index.toString().padStart(3, '0')

    /** One report awaiting upload, as `AddReportViewModel` would have written it while offline. */
    fun pending(
        index: Int,
        owner: String = OWNER,
        photoPath: String? = null,
        remoteId: String? = null,
        syncStatus: SyncStatus = SyncStatus.PENDING
    ): Report {
        val createdAt = BASE_CREATED_AT + index * 1_000L
        return Report(
            localId = localIdFor(index),
            remoteId = remoteId,
            title = "Report $index",
            description = "Description of report $index",
            category = if (index % 2 == 0) "Projector/AV" else "Network/Wi-Fi",
            location = "52.52,13.40",
            photoPath = photoPath,
            remotePhotoUrl = null,
            owner = owner,
            createdAt = createdAt,
            updatedAt = createdAt,
            syncStatus = syncStatus
        )
    }

    /** [count] pending reports, indexed 1..count. */
    fun queue(
        count: Int,
        owner: String = OWNER,
        photoPath: String? = null
    ): List<Report> = (1..count).map { pending(it, owner = owner, photoPath = photoPath) }
}
