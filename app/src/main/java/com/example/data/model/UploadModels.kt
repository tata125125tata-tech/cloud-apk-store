package com.example.data.model

data class InitUploadResponse(
    val uploadId: String,
    val key: String
)

data class PartUploadResponse(
    val partNumber: Int,
    val etag: String,
    val success: Boolean = true
)

data class UploadedPart(
    val partNumber: Int,
    val etag: String
)

data class CompleteUploadResponse(
    val success: Boolean,
    val url: String?,
    val key: String,
    val size: Long? = null
)

data class AbortUploadResponse(
    val success: Boolean
)

enum class UploadStatus {
    QUEUED,
    UPLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}
