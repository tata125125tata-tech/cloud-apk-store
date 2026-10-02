package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.CosmoApplication
import com.example.data.local.UploadTaskEntity
import com.example.data.model.FileCategory
import com.example.data.model.R2File
import com.example.data.model.StorageStats
import com.example.data.model.WorkerHealth
import com.example.data.prefs.WorkerSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FileSortOption(val label: String) {
    DATE_DESC("Newest first"),
    DATE_ASC("Oldest first"),
    SIZE_DESC("Largest first"),
    SIZE_ASC("Smallest first"),
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)")
}

data class UiNotification(
    val message: String,
    val isError: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as CosmoApplication
    private val apiClient = app.apiClient
    private val uploadManager = app.uploadManager
    private val settingsRepository = app.settingsRepository

    val settings: StateFlow<WorkerSettings> = settingsRepository.settings

    private val _workerHealth = MutableStateFlow(
        WorkerHealth(
            isOnline = false,
            status = "Checking...",
            latencyMs = 0L,
            workerUrl = settings.value.cleanBaseUrl
        )
    )
    val workerHealth: StateFlow<WorkerHealth> = _workerHealth.asStateFlow()

    private val _storageStats = MutableStateFlow(StorageStats())
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    private val _rawFiles = MutableStateFlow<List<R2File>>(emptyList())
    private val _isLoadingFiles = MutableStateFlow(false)
    val isLoadingFiles: StateFlow<Boolean> = _isLoadingFiles.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow(FileCategory.ALL)
    val selectedCategory: StateFlow<FileCategory> = _selectedCategory.asStateFlow()

    private val _sortOption = MutableStateFlow(FileSortOption.DATE_DESC)
    val sortOption: StateFlow<FileSortOption> = _sortOption.asStateFlow()

    private val _notification = MutableStateFlow<UiNotification?>(null)
    val notification: StateFlow<UiNotification?> = _notification.asStateFlow()

    // Filtered and sorted files
    val filteredFiles: StateFlow<List<R2File>> = combine(
        _rawFiles,
        _searchQuery,
        _selectedCategory,
        _sortOption
    ) { files, query, category, sort ->
        files.filter { file ->
            val matchesQuery = query.isBlank() || file.key.contains(query, ignoreCase = true)
            val matchesCategory = category == FileCategory.ALL || file.fileCategory == category
            matchesQuery && matchesCategory
        }.let { filtered ->
            when (sort) {
                FileSortOption.DATE_DESC -> filtered.sortedByDescending { it.uploaded }
                FileSortOption.DATE_ASC -> filtered.sortedBy { it.uploaded }
                FileSortOption.SIZE_DESC -> filtered.sortedByDescending { it.size }
                FileSortOption.SIZE_ASC -> filtered.sortedBy { it.size }
                FileSortOption.NAME_ASC -> filtered.sortedBy { it.key.lowercase() }
                FileSortOption.NAME_DESC -> filtered.sortedByDescending { it.key.lowercase() }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered images list for Gallery
    val imagesList: StateFlow<List<R2File>> = _rawFiles.combine(_searchQuery) { files, query ->
        files.filter { it.fileCategory == FileCategory.IMAGE }
            .filter { query.isBlank() || it.key.contains(query, ignoreCase = true) }
            .sortedByDescending { it.uploaded }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Upload tasks from Room DB
    val allUploads: StateFlow<List<UploadTaskEntity>> = uploadManager.allUploads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshAll()
    }

    fun clearNotification() {
        _notification.value = null
    }

    fun showToast(message: String, isError: Boolean = false) {
        _notification.value = UiNotification(message, isError)
    }

    fun refreshAll() {
        testConnection()
        refreshFiles()
        refreshStorageStats()
    }

    fun testConnection() {
        viewModelScope.launch {
            _workerHealth.value = _workerHealth.value.copy(status = "Testing...")
            val health = apiClient.checkHealth()
            _workerHealth.value = health
            if (health.isOnline) {
                showToast("Worker API online (${health.latencyMs}ms)", false)
            } else {
                showToast("Worker API unreachable: ${health.status}", true)
            }
        }
    }

    fun refreshFiles() {
        viewModelScope.launch {
            _isLoadingFiles.value = true
            val result = apiClient.listFiles()
            if (result.isSuccess) {
                _rawFiles.value = result.getOrDefault(emptyList())
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to load files"
                showToast(err, true)
            }
            _isLoadingFiles.value = false
            refreshStorageStats()
        }
    }

    private fun refreshStorageStats() {
        viewModelScope.launch {
            val statsResult = apiClient.getStorageStats()
            if (statsResult.isSuccess) {
                _storageStats.value = statsResult.getOrDefault(StorageStats())
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: FileCategory) {
        _selectedCategory.value = category
    }

    fun setSortOption(option: FileSortOption) {
        _sortOption.value = option
    }

    fun deleteFile(file: R2File) {
        viewModelScope.launch {
            val result = apiClient.deleteFile(file.key)
            if (result.isSuccess) {
                showToast("Deleted ${file.filename}")
                _rawFiles.value = _rawFiles.value.filter { it.key != file.key }
                refreshStorageStats()
            } else {
                val error = result.exceptionOrNull()?.message ?: "Delete failed"
                showToast("Delete failed: $error", true)
            }
        }
    }

    fun uploadFromUri(uri: Uri) {
        viewModelScope.launch {
            var filename = "upload_${System.currentTimeMillis()}"
            var fileSize = 0L
            val context = getApplication<CosmoApplication>()
            val contentResolver = context.contentResolver

            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        filename = cursor.getString(nameIndex) ?: filename
                    }
                    if (sizeIndex != -1) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }

            if (fileSize <= 0) {
                try {
                    contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        fileSize = pfd.statSize
                    }
                } catch (_: Exception) {}
            }

            if (fileSize <= 0) {
                showToast("Unable to determine file size", true)
                return@launch
            }

            val taskId = uploadManager.enqueueUpload(uri, filename, fileSize, mimeType)
            showToast("Upload queued: $filename")
        }
    }

    fun pauseUpload(id: String) {
        uploadManager.pauseUpload(id)
    }

    fun resumeUpload(id: String) {
        uploadManager.resumeUpload(id)
    }

    fun cancelUpload(id: String) {
        uploadManager.cancelUpload(id)
    }

    fun retryUpload(id: String) {
        uploadManager.retryUpload(id)
    }

    fun deleteUploadRecord(id: String) {
        uploadManager.deleteUploadRecord(id)
    }

    fun clearCompletedUploads() {
        viewModelScope.launch {
            app.database.uploadDao().clearCompleted()
            showToast("Cleared completed uploads")
        }
    }

    fun updateSettings(
        workerUrl: String,
        apiVersion: String,
        chunkSizeMb: Int,
        maxRetries: Int,
        authToken: String,
        customDomainUrl: String
    ) {
        settingsRepository.updateSettings(
            workerUrl = workerUrl,
            apiVersion = apiVersion,
            chunkSizeMb = chunkSizeMb,
            maxRetries = maxRetries,
            authToken = authToken,
            customDomainUrl = customDomainUrl
        )
        showToast("Settings saved")
        refreshAll()
    }

    fun resetSettings() {
        settingsRepository.resetToDefaults()
        showToast("Settings reset to default")
        refreshAll()
    }
}
