package com.example.upload

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.CosmoApplication
import com.example.MainActivity
import com.example.R
import com.example.data.local.UploadTaskEntity
import com.example.data.model.UploadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class UploadForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CosmoCloud:UploadWakeLock")
        wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24 hours max safety timeout

        observeUploadProgress()
    }

    private fun observeUploadProgress() {
        val app = application as CosmoApplication
        serviceScope.launch {
            app.uploadManager.allUploads.collectLatest { uploads ->
                val active = uploads.firstOrNull { it.status == UploadStatus.UPLOADING.name }
                if (active != null) {
                    val notification = buildProgressNotification(active)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } else {
                    val queued = uploads.firstOrNull { it.status == UploadStatus.QUEUED.name }
                    if (queued == null) {
                        stopForeground(STOP_FOREGROUND_DETACH)
                        stopSelf()
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val taskId = intent?.getStringExtra(EXTRA_TASK_ID)

        val app = application as? CosmoApplication
        when (action) {
            ACTION_PAUSE -> {
                if (taskId != null && app != null) {
                    app.uploadManager.pauseUpload(taskId)
                }
            }
            ACTION_CANCEL -> {
                if (taskId != null && app != null) {
                    app.uploadManager.cancelUpload(taskId)
                }
            }
            ACTION_RETRY -> {
                if (taskId != null && app != null) {
                    app.uploadManager.retryUpload(taskId)
                }
            }
            ACTION_STOP_SERVICE -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        // Default initial notification to satisfy startForeground immediately
        val initialNotif = buildInitialNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, initialNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, initialNotif)
        }

        return START_STICKY
    }

    private fun buildInitialNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cosmo Cloud Uploads")
            .setContentText("Uploading in progress...")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun buildProgressNotification(task: UploadTaskEntity): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseIntent = Intent(this, UploadForegroundService::class.java).apply {
            action = ACTION_PAUSE
            putExtra(EXTRA_TASK_ID, task.id)
        }
        val pausePendingIntent = PendingIntent.getService(
            this,
            1,
            pauseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val cancelIntent = Intent(this, UploadForegroundService::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_TASK_ID, task.id)
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            2,
            cancelIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val percent = task.progressPercent
        val progressText = "$percent% • ${task.formattedUploaded} / ${task.formattedSize} • ${task.formattedSpeed}"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Uploading ${task.filename}")
            .setContentText(progressText)
            .setSubText("Part ${task.currentPart} of ${task.totalParts}")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setProgress(100, percent, false)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cloud Uploads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active Cloudflare R2 chunked upload progress"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "cosmo_uploads_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_UPDATE_PROGRESS = "com.example.action.UPDATE_PROGRESS"
        const val ACTION_PAUSE = "com.example.action.PAUSE"
        const val ACTION_CANCEL = "com.example.action.CANCEL"
        const val ACTION_RETRY = "com.example.action.RETRY"
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_SERVICE"

        const val EXTRA_TASK_ID = "extra_task_id"
    }
}
