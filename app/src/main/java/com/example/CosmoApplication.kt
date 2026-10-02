package com.example

import android.app.Application
import com.example.data.api.WorkerApiClient
import com.example.data.local.AppDatabase
import com.example.data.prefs.SettingsRepository
import com.example.upload.UploadManager

class CosmoApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var apiClient: WorkerApiClient
        private set

    lateinit var uploadManager: UploadManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getDatabase(this)
        settingsRepository = SettingsRepository(this)
        apiClient = WorkerApiClient(settingsRepository)
        uploadManager = UploadManager(this, database, apiClient, settingsRepository)
    }

    companion object {
        lateinit var instance: CosmoApplication
            private set
    }
}
