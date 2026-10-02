package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadDao {
    @Query("SELECT * FROM upload_tasks ORDER BY createdAt DESC")
    fun getAllUploads(): Flow<List<UploadTaskEntity>>

    @Query("SELECT * FROM upload_tasks WHERE id = :id LIMIT 1")
    suspend fun getUploadById(id: String): UploadTaskEntity?

    @Query("SELECT * FROM upload_tasks WHERE status IN ('QUEUED', 'UPLOADING') ORDER BY createdAt ASC")
    suspend fun getActiveUploads(): List<UploadTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUpload(task: UploadTaskEntity)

    @Update
    suspend fun updateUpload(task: UploadTaskEntity)

    @Query("UPDATE upload_tasks SET status = :status, errorMessage = :error, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, error: String? = null, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE upload_tasks SET uploadedBytes = :uploadedBytes, currentPart = :currentPart, partsJson = :partsJson, speedBytesPerSec = :speed, etaSeconds = :eta, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateProgress(id: String, uploadedBytes: Long, currentPart: Int, partsJson: String, speed: Long, eta: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE upload_tasks SET status = 'COMPLETED', publicUrl = :publicUrl, uploadedBytes = fileSize, updatedAt = :timestamp WHERE id = :id")
    suspend fun markCompleted(id: String, publicUrl: String, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteUpload(task: UploadTaskEntity)

    @Query("DELETE FROM upload_tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM upload_tasks WHERE status = 'COMPLETED'")
    suspend fun clearCompleted()
}
