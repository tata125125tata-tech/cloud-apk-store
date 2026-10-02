package com.example.upload

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.data.api.WorkerApiClient
import com.example.data.local.AppDatabase
import com.example.data.local.UploadTaskEntity
import com.example.data.model.UploadStatus
import com.example.data.model.UploadedPart
import com.example.data.prefs.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class UploadManager(
    private val context: Context,
    private val database: AppDatabase,
    private val apiClient: WorkerApiClient,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    val allUploads: Flow<List<UploadTaskEntity>> = database.uploadDao().getAllUploads()

    fun enqueueUpload(uri: Uri, filename: String, fileSize: Long, mimeType: String): String {
        val taskId = UUID.randomUUID().toString()
        val chunkSize = settingsRepository.settings.value.chunkSizeBytes
        val totalParts = Math.max(1, Math.ceil(fileSize.toDouble() / chunkSize.toDouble()).toInt())

        val task = UploadTaskEntity(
            id = taskId,
            filename = filename,
            fileUri = uri.toString(),
            fileSize = fileSize,
            mimeType = if (mimeType.isBlank()) "application/octet-stream" else mimeType,
            chunkSize = chunkSize,
            totalParts = totalParts,
            currentPart = 1,
            uploadedBytes = 0L,
            status = UploadStatus.QUEUED.name,
            partsJson = "[]"
        )

        scope.launch {
            database.uploadDao().insertUpload(task)
            startUpload(taskId)
        }

        return taskId
    }

    fun startUpload(taskId: String) {
        if (activeJobs[taskId]?.isActive == true) {
            return
        }

        val job = scope.launch {
            val task = database.uploadDao().getUploadById(taskId) ?: return@launch
            runUploadProcess(task)
        }
        activeJobs[taskId] = job
        triggerForegroundService()
    }

    fun pauseUpload(taskId: String) {
        val job = activeJobs.remove(taskId)
        job?.cancel()
        scope.launch {
            database.uploadDao().updateStatus(taskId, UploadStatus.PAUSED.name)
            checkActiveJobsAndNotifyService()
        }
    }

    fun resumeUpload(taskId: String) {
        startUpload(taskId)
    }

    fun cancelUpload(taskId: String) {
        val job = activeJobs.remove(taskId)
        job?.cancel()
        scope.launch {
            val task = database.uploadDao().getUploadById(taskId)
            if (task != null && task.uploadId != null && task.r2Key != null) {
                try {
                    apiClient.abortMultipartUpload(task.uploadId, task.r2Key)
                } catch (e: Exception) {
                    Log.w("UploadManager", "Error calling abort on Worker", e)
                }
            }
            database.uploadDao().updateStatus(taskId, UploadStatus.CANCELLED.name)
            checkActiveJobsAndNotifyService()
        }
    }

    fun retryUpload(taskId: String) {
        scope.launch {
            database.uploadDao().updateStatus(taskId, UploadStatus.QUEUED.name, null)
            startUpload(taskId)
        }
    }

    fun deleteUploadRecord(taskId: String) {
        val job = activeJobs.remove(taskId)
        job?.cancel()
        scope.launch {
            database.uploadDao().deleteById(taskId)
            checkActiveJobsAndNotifyService()
        }
    }

    private suspend fun runUploadProcess(initialTask: UploadTaskEntity) {
        var currentTask = initialTask
        val taskId = currentTask.id
        val uri = Uri.parse(currentTask.fileUri)

        try {
            database.uploadDao().updateStatus(taskId, UploadStatus.UPLOADING.name)
            triggerForegroundService()

            // Step 1: Init upload if not already initiated
            var uploadId = currentTask.uploadId
            var r2Key = currentTask.r2Key

            if (uploadId.isNullOrBlank() || r2Key.isNullOrBlank()) {
                val initResult = apiClient.initMultipartUpload(
                    filename = currentTask.filename,
                    fileSize = currentTask.fileSize,
                    contentType = currentTask.mimeType
                )

                if (initResult.isFailure) {
                    val error = initResult.exceptionOrNull()?.message ?: "Failed to initialize upload"
                    database.uploadDao().updateStatus(taskId, UploadStatus.FAILED.name, error)
                    activeJobs.remove(taskId)
                    checkActiveJobsAndNotifyService()
                    return
                }

                val initData = initResult.getOrThrow()
                uploadId = initData.uploadId
                r2Key = initData.key
                currentTask = currentTask.copy(uploadId = uploadId, r2Key = r2Key)
                database.uploadDao().updateUpload(currentTask)
            }

            // Step 2: Parse already uploaded parts
            val partsList = parsePartsJson(currentTask.partsJson).toMutableList()
            val uploadedPartNumbers = partsList.map { it.partNumber }.toSet()

            val chunkSize = currentTask.chunkSize
            val totalParts = currentTask.totalParts
            val maxRetries = settingsRepository.settings.value.maxRetries

            var totalUploadedBytes = partsList.size.toLong() * chunkSize
            if (totalUploadedBytes > currentTask.fileSize) {
                totalUploadedBytes = currentTask.fileSize
            }

            var lastProgressTime = System.currentTimeMillis()
            var bytesSinceLastTime = 0L

            for (partNumber in 1..totalParts) {
                // If part is already uploaded, skip
                if (uploadedPartNumbers.contains(partNumber)) {
                    continue
                }

                // Check for cancellation
                if (activeJobs[taskId]?.isActive != true) {
                    return
                }

                // Read chunk bytes without loading the whole file into RAM
                val chunkBytes = readChunkBytes(uri, partNumber, chunkSize, currentTask.fileSize)
                if (chunkBytes == null || chunkBytes.isEmpty()) {
                    throw Exception("Could not read chunk $partNumber from storage")
                }

                // Upload part with retry
                var partSuccess = false
                var lastException: Exception? = null

                for (attempt in 1..maxRetries) {
                    if (activeJobs[taskId]?.isActive != true) return

                    val partResult = apiClient.uploadPart(
                        uploadId = uploadId,
                        key = r2Key,
                        partNumber = partNumber,
                        chunkData = chunkBytes
                    )

                    if (partResult.isSuccess) {
                        val partResp = partResult.getOrThrow()
                        partsList.add(UploadedPart(partNumber, partResp.etag))
                        partSuccess = true

                        // Update speed and ETA
                        val now = System.currentTimeMillis()
                        val elapsed = Math.max(1, now - lastProgressTime)
                        bytesSinceLastTime += chunkBytes.size
                        totalUploadedBytes = Math.min(currentTask.fileSize, totalUploadedBytes + chunkBytes.size)

                        val speed = (bytesSinceLastTime * 1000L) / elapsed
                        val remainingBytes = Math.max(0L, currentTask.fileSize - totalUploadedBytes)
                        val eta = if (speed > 0) remainingBytes / speed else 0L

                        lastProgressTime = now
                        bytesSinceLastTime = 0L

                        // Persist progress to DB
                        val updatedPartsJson = serializeParts(partsList)
                        database.uploadDao().updateProgress(
                            id = taskId,
                            uploadedBytes = totalUploadedBytes,
                            currentPart = partNumber,
                            partsJson = updatedPartsJson,
                            speed = speed,
                            eta = eta
                        )

                        triggerForegroundService()
                        break
                    } else {
                        lastException = partResult.exceptionOrNull() as? Exception ?: Exception("Part upload failed")
                        Log.w("UploadManager", "Part $partNumber attempt $attempt failed: ${lastException.message}")
                        if (attempt < maxRetries) {
                            delay(1000L * attempt) // Exponential backoff
                        }
                    }
                }

                if (!partSuccess) {
                    throw lastException ?: Exception("Part $partNumber failed after $maxRetries attempts")
                }
            }

            // Step 3: Complete upload
            partsList.sortBy { it.partNumber }
            val completeResult = apiClient.completeMultipartUpload(
                uploadId = uploadId,
                key = r2Key,
                parts = partsList
            )

            if (completeResult.isSuccess) {
                val completeResp = completeResult.getOrThrow()
                val finalUrl = completeResp.url ?: "${settingsRepository.settings.value.cleanBaseUrl}/files/$r2Key"
                database.uploadDao().markCompleted(taskId, finalUrl)
                Log.i("UploadManager", "Upload completed successfully! Public URL: $finalUrl")
            } else {
                val completeError = completeResult.exceptionOrNull()?.message ?: "Failed to complete upload on Worker"
                database.uploadDao().updateStatus(taskId, UploadStatus.FAILED.name, completeError)
            }

        } catch (e: CancellationException) {
            Log.d("UploadManager", "Task $taskId was cancelled or paused")
        } catch (e: Exception) {
            Log.e("UploadManager", "Upload error for task $taskId", e)
            database.uploadDao().updateStatus(taskId, UploadStatus.FAILED.name, e.message ?: "Upload failed")
        } finally {
            activeJobs.remove(taskId)
            checkActiveJobsAndNotifyService()
        }
    }

    private fun readChunkBytes(uri: Uri, partNumber: Int, chunkSize: Int, fileSize: Long): ByteArray? {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val offset = (partNumber - 1).toLong() * chunkSize.toLong()
            var skipped = 0L
            while (skipped < offset) {
                val s = inputStream.skip(offset - skipped)
                if (s <= 0) break
                skipped += s
            }

            val remainingInFile = fileSize - offset
            val currentChunkSize = Math.min(chunkSize.toLong(), remainingInFile).toInt()
            val buffer = ByteArray(currentChunkSize)

            var bytesRead = 0
            while (bytesRead < currentChunkSize) {
                val r = inputStream.read(buffer, bytesRead, currentChunkSize - bytesRead)
                if (r == -1) break
                bytesRead += r
            }

            return if (bytesRead == currentChunkSize) {
                buffer
            } else {
                buffer.copyOf(bytesRead)
            }
        } catch (e: Exception) {
            Log.e("UploadManager", "Error reading chunk $partNumber", e)
            return null
        } finally {
            try {
                inputStream?.close()
            } catch (_: Exception) {}
        }
    }

    private fun parsePartsJson(json: String): List<UploadedPart> {
        val list = mutableListOf<UploadedPart>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(UploadedPart(obj.getInt("partNumber"), obj.getString("etag")))
            }
        } catch (_: Exception) {}
        return list
    }

    private fun serializeParts(parts: List<UploadedPart>): String {
        val array = JSONArray()
        for (p in parts) {
            array.put(JSONObject().apply {
                put("partNumber", p.partNumber)
                put("etag", p.etag)
            })
        }
        return array.toString()
    }

    private fun triggerForegroundService() {
        try {
            val serviceIntent = Intent(context, UploadForegroundService::class.java).apply {
                action = UploadForegroundService.ACTION_UPDATE_PROGRESS
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("UploadManager", "Failed to start foreground service", e)
        }
    }

    private suspend fun checkActiveJobsAndNotifyService() {
        val activeTasks = database.uploadDao().getActiveUploads()
        if (activeTasks.isEmpty() && activeJobs.isEmpty()) {
            try {
                val serviceIntent = Intent(context, UploadForegroundService::class.java).apply {
                    action = UploadForegroundService.ACTION_STOP_SERVICE
                }
                context.startService(serviceIntent)
            } catch (_: Exception) {}
        } else {
            triggerForegroundService()
        }
    }
}
