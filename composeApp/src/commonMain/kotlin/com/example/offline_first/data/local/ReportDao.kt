package com.example.offline_first.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.offline_first.data.model.Report
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: Report): Long

    @Update
    suspend fun update(report: Report)

    @Delete
    suspend fun delete(report: Report)

    @Query("SELECT * FROM reports ORDER BY createdAt DESC")
    suspend fun getAll(): List<Report>

    @Query("SELECT * FROM reports WHERE owner = :owner ORDER BY createdAt DESC")
    suspend fun getByOwner(owner: String): List<Report>

    @Query("SELECT * FROM reports WHERE owner = :owner ORDER BY createdAt DESC")
    fun getByOwnerFlow(owner: String): Flow<List<Report>> // Room watches the reports table; any write re-queries and emits a new list automatically.

    @Query("SELECT * FROM reports WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Report?

    @Query("SELECT * FROM reports WHERE id = :id LIMIT 1")
    fun getByIdFlow(id: Long): Flow<Report?>

    @Query("SELECT * FROM reports WHERE syncStatus = 'PENDING'")
    suspend fun getPending(): List<Report>

    @Query("SELECT * FROM reports WHERE syncStatus = 'DELETED_PENDING'")
    suspend fun getDeletedPending(): List<Report>
}
