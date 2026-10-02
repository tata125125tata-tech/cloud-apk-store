package com.example.data.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WorkerSettings(
    val workerUrl: String = "https://tight-surf-5eff.play125store.workers.dev",
    val apiVersion: String = "v1",
    val chunkSizeMb: Int = 10,
    val maxRetries: Int = 3,
    val authToken: String = "",
    val customDomainUrl: String = ""
) {
    val cleanBaseUrl: String
        get() = workerUrl.trimEnd('/')

    val chunkSizeBytes: Int
        get() = chunkSizeMb * 1024 * 1024
}

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("cosmo_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<WorkerSettings> = _settings.asStateFlow()

    private fun loadSettings(): WorkerSettings {
        return WorkerSettings(
            workerUrl = prefs.getString(KEY_WORKER_URL, "https://tight-surf-5eff.play125store.workers.dev")
                ?.takeIf { it.isNotBlank() } ?: "https://tight-surf-5eff.play125store.workers.dev",
            apiVersion = prefs.getString(KEY_API_VERSION, "v1") ?: "v1",
            chunkSizeMb = prefs.getInt(KEY_CHUNK_SIZE, 10).coerceIn(1, 100),
            maxRetries = prefs.getInt(KEY_MAX_RETRIES, 3).coerceIn(1, 10),
            authToken = prefs.getString(KEY_AUTH_TOKEN, "") ?: "",
            customDomainUrl = prefs.getString(KEY_CUSTOM_DOMAIN, "") ?: ""
        )
    }

    fun updateSettings(
        workerUrl: String,
        apiVersion: String,
        chunkSizeMb: Int,
        maxRetries: Int,
        authToken: String,
        customDomainUrl: String
    ) {
        val sanitizedUrl = workerUrl.trim().trimEnd('/')
        prefs.edit()
            .putString(KEY_WORKER_URL, sanitizedUrl)
            .putString(KEY_API_VERSION, apiVersion.trim())
            .putInt(KEY_CHUNK_SIZE, chunkSizeMb)
            .putInt(KEY_MAX_RETRIES, maxRetries)
            .putString(KEY_AUTH_TOKEN, authToken.trim())
            .putString(KEY_CUSTOM_DOMAIN, customDomainUrl.trim().trimEnd('/'))
            .apply()

        _settings.value = loadSettings()
    }

    fun resetToDefaults() {
        prefs.edit().clear().apply()
        _settings.value = loadSettings()
    }

    companion object {
        private const val KEY_WORKER_URL = "worker_url"
        private const val KEY_API_VERSION = "api_version"
        private const val KEY_CHUNK_SIZE = "chunk_size_mb"
        private const val KEY_MAX_RETRIES = "max_retries"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_CUSTOM_DOMAIN = "custom_domain"
    }
}
