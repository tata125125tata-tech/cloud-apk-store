package com.example.data.api

import android.util.Log
import com.example.data.model.AbortUploadResponse
import com.example.data.model.CompleteUploadResponse
import com.example.data.model.FileCategory
import com.example.data.model.InitUploadResponse
import com.example.data.model.PartUploadResponse
import com.example.data.model.R2File
import com.example.data.model.StorageStats
import com.example.data.model.UploadedPart
import com.example.data.model.WorkerHealth
import com.example.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class WorkerApiException(
    val statusCode: Int,
    override val message: String,
    val isRetryable: Boolean = false
) : Exception(message)

class WorkerApiClient(
    private val settingsRepository: SettingsRepository
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val octetMediaType = "application/octet-stream".toMediaType()

    private fun getBaseUrl(): String {
        return settingsRepository.settings.value.cleanBaseUrl
    }

    private fun buildRequest(url: String): Request.Builder {
        val builder = Request.Builder().url(url)
        val token = settingsRepository.settings.value.authToken
        if (token.isNotBlank()) {
            builder.addHeader("Authorization", "Bearer $token")
            builder.addHeader("X-Worker-Auth", token)
        }
        builder.addHeader("User-Agent", "CosmoCloudManager/1.0 (Android)")
        return builder
    }

    suspend fun checkHealth(): WorkerHealth = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val baseUrl = getBaseUrl()
        val candidateEndpoints = listOf(
            "$baseUrl/health",
            "$baseUrl/status",
            "$baseUrl/"
        )

        for (endpoint in candidateEndpoints) {
            try {
                val request = buildRequest(endpoint).get().build()
                val response = client.newCall(request).execute()
                val latency = System.currentTimeMillis() - start
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    var bucketName: String? = null
                    var version = "v1"
                    try {
                        val json = JSONObject(bodyString)
                        bucketName = json.optString("bucket", null)
                        version = json.optString("version", "v1")
                    } catch (_: Exception) {}

                    return@withContext WorkerHealth(
                        isOnline = true,
                        status = "Connected (${response.code})",
                        latencyMs = latency,
                        workerUrl = baseUrl,
                        bucket = bucketName,
                        version = version
                    )
                }
            } catch (_: Exception) {
                // Try next endpoint
            }
        }

        // If none returned success
        val latency = System.currentTimeMillis() - start
        WorkerHealth(
            isOnline = false,
            status = "Unreachable",
            latencyMs = latency,
            workerUrl = baseUrl
        )
    }

    suspend fun listFiles(): Result<List<R2File>> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getBaseUrl()
            val request = buildRequest("$baseUrl/files").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw mapHttpError(response)
                }
                val body = response.body?.string() ?: "[]"
                val files = parseFilesResponse(body, baseUrl)
                Result.success(files)
            }
        } catch (e: Exception) {
            Log.e("WorkerApiClient", "Error listing files", e)
            Result.failure(e)
        }
    }

    private fun parseFilesResponse(body: String, baseUrl: String): List<R2File> {
        val files = mutableListOf<R2File>()
        try {
            val customPrefix = settingsRepository.settings.value.customDomainUrl.ifBlank { baseUrl }
            if (body.trim().startsWith("[")) {
                val array = JSONArray(body)
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    files.add(parseSingleFile(item, customPrefix))
                }
            } else {
                val root = JSONObject(body)
                val array = root.optJSONArray("files") ?: root.optJSONArray("objects") ?: JSONArray()
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    files.add(parseSingleFile(item, customPrefix))
                }
            }
        } catch (e: Exception) {
            Log.e("WorkerApiClient", "Error parsing files JSON", e)
        }
        return files
    }

    private fun parseSingleFile(item: JSONObject, customPrefix: String): R2File {
        val key = item.optString("key", item.optString("name", "untitled"))
        val size = item.optLong("size", 0L)
        val uploaded = item.optLong("uploaded", item.optLong("uploadedAt", System.currentTimeMillis()))
        val etag = item.optString("etag", null)
        val contentType = item.optString("contentType", item.optString("type", null))
        val rawUrl = item.optString("url", "")
        val finalUrl = if (rawUrl.isNotBlank()) {
            rawUrl
        } else {
            "$customPrefix/files/$key"
        }
        return R2File(
            key = key,
            size = size,
            uploaded = uploaded,
            etag = etag,
            contentType = contentType,
            url = finalUrl
        )
    }

    suspend fun getStorageStats(): Result<StorageStats> = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl()
        // Try /storage or /stats endpoint first
        try {
            val request = buildRequest("$baseUrl/storage").get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val used = json.optLong("storageUsedBytes", json.optLong("used", 0L))
                    val limit = json.optLong("storageLimitBytes", json.optLong("limit", 10L * 1024 * 1024 * 1024))
                    val totalFiles = json.optInt("totalFiles", 0)
                    val totalImages = json.optInt("totalImages", 0)
                    val totalApks = json.optInt("totalApks", 0)
                    return@withContext Result.success(
                        StorageStats(
                            storageUsedBytes = used,
                            storageLimitBytes = limit,
                            totalFiles = totalFiles,
                            totalImages = totalImages,
                            totalApks = totalApks,
                            isConnected = true,
                            statusMessage = "Online"
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Fall back to computing from listFiles
        }

        // Compute from file list
        val fileListResult = listFiles()
        if (fileListResult.isSuccess) {
            val list = fileListResult.getOrDefault(emptyList())
            var totalUsed = 0L
            var totalImages = 0
            var totalApks = 0
            for (f in list) {
                totalUsed += f.size
                when (f.fileCategory) {
                    FileCategory.IMAGE -> totalImages++
                    FileCategory.APK -> totalApks++
                    else -> {}
                }
            }
            Result.success(
                StorageStats(
                    storageUsedBytes = totalUsed,
                    storageLimitBytes = 10L * 1024 * 1024 * 1024,
                    totalFiles = list.size,
                    totalImages = totalImages,
                    totalApks = totalApks,
                    isConnected = true,
                    statusMessage = "Online"
                )
            )
        } else {
            Result.failure(fileListResult.exceptionOrNull() ?: Exception("Cannot connect to Worker"))
        }
    }

    suspend fun deleteFile(key: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getBaseUrl()
            val encodedKey = java.net.URLEncoder.encode(key, "UTF-8").replace("+", "%20")
            val request = buildRequest("$baseUrl/files/$encodedKey").delete().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    // Try fallback POST /delete
                    val fallbackPayload = JSONObject().apply { put("key", key) }.toString()
                    val fallbackReq = buildRequest("$baseUrl/delete")
                        .post(fallbackPayload.toRequestBody(jsonMediaType))
                        .build()
                    client.newCall(fallbackReq).execute().use { fallbackResp ->
                        if (fallbackResp.isSuccessful) {
                            Result.success(true)
                        } else {
                            throw mapHttpError(fallbackResp)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("WorkerApiClient", "Error deleting file $key", e)
            Result.failure(e)
        }
    }

    suspend fun initMultipartUpload(
        filename: String,
        fileSize: Long,
        contentType: String
    ): Result<InitUploadResponse> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getBaseUrl()
            val payload = JSONObject().apply {
                put("filename", filename)
                put("fileSize", fileSize)
                put("contentType", contentType)
            }.toString()

            val request = buildRequest("$baseUrl/init")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw mapHttpError(response)
                }
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val uploadId = json.optString("uploadId", "")
                val key = json.optString("key", filename)
                if (uploadId.isBlank()) {
                    throw WorkerApiException(response.code, "Worker returned empty uploadId")
                }
                Result.success(InitUploadResponse(uploadId = uploadId, key = key))
            }
        } catch (e: Exception) {
            Log.e("WorkerApiClient", "Error initializing upload", e)
            Result.failure(e)
        }
    }

    suspend fun uploadPart(
        uploadId: String,
        key: String,
        partNumber: Int,
        chunkData: ByteArray
    ): Result<PartUploadResponse> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getBaseUrl()
            val encodedKey = java.net.URLEncoder.encode(key, "UTF-8")
            val encodedUploadId = java.net.URLEncoder.encode(uploadId, "UTF-8")

            val url = "$baseUrl/part?uploadId=$encodedUploadId&partNumber=$partNumber&key=$encodedKey"

            val body = chunkData.toRequestBody(octetMediaType)

            val request = buildRequest(url)
                .put(body)
                .addHeader("X-Upload-Id", uploadId)
                .addHeader("X-Part-Number", partNumber.toString())
                .addHeader("X-Key", key)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw mapHttpError(response)
                }

                var etag = response.header("ETag")?.trim('"', ' ')
                val bodyString = response.body?.string() ?: ""
                if (etag.isNullOrBlank() && bodyString.isNotBlank()) {
                    try {
                        val json = JSONObject(bodyString)
                        etag = json.optString("etag", "")
                    } catch (_: Exception) {}
                }

                if (etag.isNullOrBlank()) {
                    etag = "part-$partNumber-ok"
                }

                Result.success(PartUploadResponse(partNumber = partNumber, etag = etag))
            }
        } catch (e: Exception) {
            Log.e("WorkerApiClient", "Error uploading part $partNumber", e)
            Result.failure(e)
        }
    }

    suspend fun completeMultipartUpload(
        uploadId: String,
        key: String,
        parts: List<UploadedPart>
    ): Result<CompleteUploadResponse> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getBaseUrl()
            val partsArray = JSONArray()
            for (p in parts) {
                partsArray.put(JSONObject().apply {
                    put("partNumber", p.partNumber)
                    put("etag", p.etag)
                })
            }

            val payload = JSONObject().apply {
                put("uploadId", uploadId)
                put("key", key)
                put("parts", partsArray)
            }.toString()

            val request = buildRequest("$baseUrl/complete")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw mapHttpError(response)
                }
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val success = json.optBoolean("success", true)
                var url = json.optString("url", null)
                val customPrefix = settingsRepository.settings.value.customDomainUrl.ifBlank { baseUrl }
                if (url.isNullOrBlank()) {
                    url = "$customPrefix/files/$key"
                }
                val returnedKey = json.optString("key", key)
                val size = if (json.has("size")) json.optLong("size") else null

                Result.success(CompleteUploadResponse(success = success, url = url, key = returnedKey, size = size))
            }
        } catch (e: Exception) {
            Log.e("WorkerApiClient", "Error completing multipart upload", e)
            Result.failure(e)
        }
    }

    suspend fun abortMultipartUpload(
        uploadId: String,
        key: String
    ): Result<AbortUploadResponse> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getBaseUrl()
            val payload = JSONObject().apply {
                put("uploadId", uploadId)
                put("key", key)
            }.toString()

            val request = buildRequest("$baseUrl/abort")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val success = response.isSuccessful
                Result.success(AbortUploadResponse(success = success))
            }
        } catch (e: Exception) {
            Log.e("WorkerApiClient", "Error aborting upload", e)
            Result.failure(e)
        }
    }

    private fun mapHttpError(response: Response): WorkerApiException {
        val code = response.code
        val message = when (code) {
            401 -> "401 Unauthorized: Worker authentication failed. Check your API token in Settings."
            403 -> "403 Forbidden: Access denied by Cloudflare Worker or R2 bucket."
            404 -> "404 Not Found: Worker endpoint or file does not exist."
            413 -> "413 Payload Too Large: Chunk size exceeds Worker limits (try 5 MB or 10 MB in Settings)."
            429 -> "429 Rate Limit: Too many requests. Cloudflare Worker rate limit exceeded."
            500 -> "500 Server Error: Internal Worker error while communicating with R2 bucket."
            502 -> "502 Bad Gateway: Worker could not reach Cloudflare R2."
            503 -> "503 Service Unavailable: Cloudflare R2 or Worker temporarily unavailable."
            504 -> "504 Gateway Timeout: Request timed out."
            else -> "HTTP $code: ${response.message}"
        }
        val isRetryable = code in listOf(408, 429, 500, 502, 503, 504)
        return WorkerApiException(code, message, isRetryable)
    }
}
