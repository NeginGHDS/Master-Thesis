package com.example.offline_first.synctesting.fakes

import com.example.offline_first.data.local.ReportDao
import com.example.offline_first.data.model.Report
import com.example.offline_first.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * A [ReportDao] backed by a list instead of Room/SQLite.
 *
 * The sync scenarios care about *which rows change state*, not about SQL, and a real Room database
 * would drag an Android/iOS runtime into what are otherwise plain JVM unit tests. Query semantics
 * are reproduced faithfully: `REPLACE` on insert, `createdAt DESC` ordering, and flows that re-emit
 * on every write (Room's table-observation behaviour).
 */
class InMemoryReportDao : ReportDao {

    private val rows = MutableStateFlow<List<Report>>(emptyList())
    private var nextId = 1L

    /** Current table contents — the assertion surface for the scenario tests. */
    val snapshot: List<Report> get() = rows.value

    fun countWithStatus(status: SyncStatus): Int = rows.value.count { it.syncStatus == status }

    override suspend fun insert(report: Report): Long {
        val id = if (report.id != 0L) report.id else nextId++
        val stored = report.copy(id = id)
        // OnConflictStrategy.REPLACE
        rows.value = rows.value.filterNot { it.id == id } + stored
        return id
    }

    override suspend fun update(report: Report) {
        rows.value = rows.value.map { if (it.id == report.id) report else it }
    }

    override suspend fun delete(report: Report) {
        rows.value = rows.value.filterNot { it.id == report.id }
    }

    override suspend fun getAll(): List<Report> = rows.value.sortedByDescending { it.createdAt }

    override suspend fun getByOwner(owner: String): List<Report> =
        rows.value.filter { it.owner == owner }.sortedByDescending { it.createdAt }

    override fun getByOwnerFlow(owner: String): Flow<List<Report>> =
        rows.map { all -> all.filter { it.owner == owner }.sortedByDescending { it.createdAt } }

    override suspend fun getById(id: Long): Report? = rows.value.firstOrNull { it.id == id }

    override fun getByIdFlow(id: Long): Flow<Report?> =
        rows.map { all -> all.firstOrNull { it.id == id } }

    override suspend fun getPending(): List<Report> =
        rows.value.filter { it.syncStatus == SyncStatus.PENDING }

    override suspend fun getUnsynced(): List<Report> =
        rows.value.filter { it.syncStatus == SyncStatus.PENDING || it.syncStatus == SyncStatus.FAILED }

    override suspend fun getDeletedPending(): List<Report> =
        rows.value.filter { it.syncStatus == SyncStatus.DELETED_PENDING }
}
