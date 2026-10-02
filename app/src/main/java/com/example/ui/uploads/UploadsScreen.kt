package com.example.ui.uploads

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.UploadTaskEntity
import com.example.data.model.UploadStatus
import com.example.ui.components.EmptyStateView
import com.example.ui.components.PublicUrlDialog
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import com.example.ui.viewmodel.MainViewModel

@Composable
fun UploadsScreen(
    viewModel: MainViewModel,
    onPickFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uploads by viewModel.allUploads.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var selectedCompletedTaskForUrl by remember { mutableStateOf<UploadTaskEntity?>(null) }

    val tabs = listOf("All", "Active", "Completed", "Failed")

    val filteredUploads = remember(uploads, selectedTabIndex) {
        when (selectedTabIndex) {
            1 -> uploads.filter { it.status == UploadStatus.UPLOADING.name || it.status == UploadStatus.QUEUED.name || it.status == UploadStatus.PAUSED.name }
            2 -> uploads.filter { it.status == UploadStatus.COMPLETED.name }
            3 -> uploads.filter { it.status == UploadStatus.FAILED.name || it.status == UploadStatus.CANCELLED.name }
            else -> uploads
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Upload Manager",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Multipart chunked upload with auto-resume",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (uploads.any { it.status == UploadStatus.COMPLETED.name }) {
                        IconButton(
                            onClick = { viewModel.clearCompletedUploads() },
                            modifier = Modifier.testTag("uploads_clear_completed_button")
                        ) {
                            Icon(Icons.Default.ClearAll, contentDescription = "Clear completed")
                        }
                    }
                }
            }

            // Tabs
            item {
                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    edgePadding = 0.dp,
                    divider = {},
                    containerColor = Color.Transparent
                ) {
                    tabs.forEachIndexed { index, title ->
                        val count = when (index) {
                            1 -> uploads.count { it.status in listOf(UploadStatus.UPLOADING.name, UploadStatus.QUEUED.name, UploadStatus.PAUSED.name) }
                            2 -> uploads.count { it.status == UploadStatus.COMPLETED.name }
                            3 -> uploads.count { it.status in listOf(UploadStatus.FAILED.name, UploadStatus.CANCELLED.name) }
                            else -> uploads.size
                        }
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text("$title ($count)") }
                        )
                    }
                }
            }

            if (filteredUploads.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = Icons.Default.CloudUpload,
                        title = "No ${tabs[selectedTabIndex].lowercase()} uploads",
                        description = "Start uploading multi-gigabyte files or images with auto-resume and background service.",
                        actionButtonText = "Upload File",
                        onActionClick = onPickFile
                    )
                }
            } else {
                items(filteredUploads, key = { it.id }) { task ->
                    UploadCard(
                        task = task,
                        onPause = { viewModel.pauseUpload(task.id) },
                        onResume = { viewModel.resumeUpload(task.id) },
                        onCancel = { viewModel.cancelUpload(task.id) },
                        onRetry = { viewModel.retryUpload(task.id) },
                        onDelete = { viewModel.deleteUploadRecord(task.id) },
                        onViewUrl = { selectedCompletedTaskForUrl = task }
                    )
                }
            }
        }

        // FAB to add upload
        FloatingActionButton(
            onClick = onPickFile,
            containerColor = CyanAccent,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("uploads_fab_add")
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = "Add upload")
        }
    }

    selectedCompletedTaskForUrl?.let { task ->
        task.publicUrl?.let { url ->
            PublicUrlDialog(
                filename = task.filename,
                sizeFormatted = task.formattedSize,
                url = url,
                onDismiss = { selectedCompletedTaskForUrl = null }
            )
        }
    }
}

@Composable
fun UploadCard(
    task: UploadTaskEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onViewUrl: () -> Unit
) {
    val context = LocalContext.current
    val isUploading = task.status == UploadStatus.UPLOADING.name
    val isPaused = task.status == UploadStatus.PAUSED.name
    val isCompleted = task.status == UploadStatus.COMPLETED.name
    val isFailed = task.status == UploadStatus.FAILED.name || task.status == UploadStatus.CANCELLED.name

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("upload_card_${task.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Title & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = when {
                    isCompleted -> Icons.Default.CheckCircle
                    isFailed -> Icons.Default.Error
                    else -> Icons.Default.CloudUpload
                }
                val iconTint = when {
                    isCompleted -> EmeraldSuccess
                    isFailed -> RoseError
                    else -> CyanAccent
                }

                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.filename,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${task.formattedUploaded} / ${task.formattedSize}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete upload record",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { task.progressPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = when {
                    isCompleted -> EmeraldSuccess
                    isFailed -> RoseError
                    isPaused -> AmberWarning
                    else -> CyanAccent
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Telemetry: Speed, ETA, Part info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${task.progressPercent}% • Part ${task.currentPart} of ${task.totalParts}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isUploading) {
                    Text(
                        text = "${task.formattedSpeed} • ETA ${task.formattedEta}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent
                    )
                } else {
                    Text(
                        text = task.status,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isCompleted -> EmeraldSuccess
                            isFailed -> RoseError
                            isPaused -> AmberWarning
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            // Error message if any
            if (!task.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = RoseError.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = task.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = RoseError,
                        modifier = Modifier.padding(8.dp),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isUploading) {
                    OutlinedButton(
                        onClick = onPause,
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("upload_pause_button"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pause", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("upload_cancel_button"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cancel", fontSize = 12.sp)
                    }
                } else if (isPaused) {
                    Button(
                        onClick = onResume,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("upload_resume_button"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Resume", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Text("Cancel", fontSize = 12.sp)
                    }
                } else if (isFailed) {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("upload_retry_button"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry", fontSize = 12.sp)
                    }
                } else if (isCompleted && task.publicUrl != null) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("R2 URL", task.publicUrl))
                            Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    OutlinedButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, task.filename)
                                putExtra(Intent.EXTRA_TEXT, task.publicUrl)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Public URL"))
                        },
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = onViewUrl,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Text("Details", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
