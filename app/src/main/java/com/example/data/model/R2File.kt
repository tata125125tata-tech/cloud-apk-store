package com.example.data.model

data class R2File(
    val key: String,
    val size: Long,
    val uploaded: Long = System.currentTimeMillis(),
    val etag: String? = null,
    val contentType: String? = null,
    val url: String? = null
) {
    val filename: String
        get() = key.substringAfterLast('/')

    val extension: String
        get() = filename.substringAfterLast('.', "").lowercase()

    val fileCategory: FileCategory
        get() = when (extension) {
            "apk", "xapk", "apkm", "apks" -> FileCategory.APK
            "jpg", "jpeg", "png", "webp", "gif", "svg", "bmp" -> FileCategory.IMAGE
            "zip", "rar", "7z", "tar", "gz" -> FileCategory.ARCHIVE
            "pdf", "doc", "docx", "txt", "json", "xml", "csv" -> FileCategory.DOCUMENT
            "mp4", "mkv", "webm", "avi", "mov" -> FileCategory.VIDEO
            "mp3", "wav", "flac", "ogg", "m4a" -> FileCategory.AUDIO
            else -> FileCategory.OTHER
        }

    val formattedSize: String
        get() {
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            val finalIndex = digitGroups.coerceIn(0, units.size - 1)
            val value = size / Math.pow(1024.0, finalIndex.toDouble())
            return String.format("%.2f %s", value, units[finalIndex])
        }
}

enum class FileCategory(val label: String) {
    ALL("All"),
    APK("APKs & Packages"),
    IMAGE("Images"),
    ARCHIVE("Archives"),
    DOCUMENT("Documents"),
    VIDEO("Videos"),
    AUDIO("Audio"),
    OTHER("Other")
}

data class StorageStats(
    val storageUsedBytes: Long = 0L,
    val storageLimitBytes: Long = 10L * 1024 * 1024 * 1024, // 10 GB free tier reference
    val totalFiles: Int = 0,
    val totalImages: Int = 0,
    val totalApks: Int = 0,
    val isConnected: Boolean = true,
    val statusMessage: String = "Online"
) {
    val usedPercentage: Float
        get() = if (storageLimitBytes > 0) {
            ((storageUsedBytes.toDouble() / storageLimitBytes.toDouble()) * 100).toFloat().coerceIn(0f, 100f)
        } else 0f

    val formattedUsed: String
        get() {
            val mb = storageUsedBytes / (1024.0 * 1024.0)
            return if (mb >= 1024) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)
        }

    val formattedLimit: String
        get() {
            val gb = storageLimitBytes / (1024.0 * 1024.0 * 1024.0)
            return String.format("%.0f GB", gb)
        }
}

data class WorkerHealth(
    val isOnline: Boolean,
    val status: String,
    val latencyMs: Long = 0L,
    val workerUrl: String = "",
    val bucket: String? = null,
    val version: String = "v1"
)
