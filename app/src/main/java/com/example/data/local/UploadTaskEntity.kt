package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.UploadStatus

@Entity(tableName = "upload_tasks")
data class UploadTaskEntity(
    @PrimaryKey
    val id: String,
    val filename: String,
    val fileUri: String,
    val fileSize: Long,
    val mimeType: String,
    val chunkSize: Int,
    val totalParts: Int,
    val currentPart: Int = 1,
    val uploadedBytes: Long = 0L,
    val uploadId: String? = null,
    val r2Key: String? = null,
    val status: String = UploadStatus.QUEUED.name,
    val partsJson: String = "[]", // JSON array of {"partNumber": 1, "etag": "..."}
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = 0L,
    val publicUrl: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val progressPercent: Int
        get() = if (fileSize > 0) {
            ((uploadedBytes.toDouble() / fileSize.toDouble()) * 100).toInt().coerceIn(0, 100)
        } else 0

    val formattedSpeed: String
        get() = when {
            speedBytesPerSec <= 0 -> "--"
            speedBytesPerSec < 1024 * 1024 -> String.format("%.1f KB/s", speedBytesPerSec / 1024.0)
            else -> String.format("%.1f MB/s", speedBytesPerSec / (1024.0 * 1024.0))
        }

    val formattedEta: String
        get() = when {
            etaSeconds <= 0 -> "--"
            etaSeconds < 60 -> "${etaSeconds}s"
            etaSeconds < 3600 -> "${etaSeconds / 60}m ${etaSeconds % 60}s"
            else -> "${etaSeconds / 3600}h ${(etaSeconds % 3600) / 60}m"
        }

    val formattedSize: String
        get() {
            if (fileSize <= 0) return "0 B"
            val mb = fileSize / (1024.0 * 1024.0)
            return if (mb >= 1024) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)
        }

    val formattedUploaded: String
        get() {
            if (uploadedBytes <= 0) return "0 B"
            val mb = uploadedBytes / (1024.0 * 1024.0)
            return if (mb >= 1024) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)
        }
}
