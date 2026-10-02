package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.files.FilesScreen
import com.example.ui.images.ImagesScreen
import com.example.ui.navigation.CosmoScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.uploads.UploadsScreen
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                CosmoApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun CosmoApp(viewModel: MainViewModel) {
    var currentScreen by remember { mutableStateOf(CosmoScreen.DASHBOARD) }
    val snackbarHostState = remember { SnackbarHostState() }
    val notification by viewModel.notification.collectAsState()

    // Notification permission launcher for Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Any File Picker (APK, XAPK, APKM, APKS, ZIP, etc.)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.uploadFromUri(uri)
            currentScreen = CosmoScreen.UPLOADS
        }
    }

    // Photo Picker (Google Play zero-permission photo picker)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.uploadFromUri(uri)
            currentScreen = CosmoScreen.UPLOADS
        }
    }

    // Listen for notification events and show snackbars
    LaunchedEffect(notification) {
        notification?.let { notif ->
            snackbarHostState.showSnackbar(notif.message)
            viewModel.clearNotification()
        }
    }

    // Back handling: Return to dashboard if on sub-screens
    if (currentScreen != CosmoScreen.DASHBOARD) {
        BackHandler {
            currentScreen = CosmoScreen.DASHBOARD
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("bottom_navigation_bar"),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                CosmoScreen.values().forEach { screen ->
                    val isSelected = currentScreen == screen
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentScreen = screen },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.title
                            )
                        },
                        label = { Text(screen.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = CyanAccent.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.testTag(screen.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                when (screen) {
                    CosmoScreen.DASHBOARD -> DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToUploads = { currentScreen = CosmoScreen.UPLOADS },
                        onNavigateToFiles = { currentScreen = CosmoScreen.FILES },
                        onPickAnyFile = { filePickerLauncher.launch(arrayOf("*/*")) },
                        onPickImage = {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                    CosmoScreen.FILES -> FilesScreen(
                        viewModel = viewModel,
                        onPickFile = { filePickerLauncher.launch(arrayOf("*/*")) }
                    )
                    CosmoScreen.UPLOADS -> UploadsScreen(
                        viewModel = viewModel,
                        onPickFile = { filePickerLauncher.launch(arrayOf("*/*")) }
                    )
                    CosmoScreen.IMAGES -> ImagesScreen(
                        viewModel = viewModel,
                        onPickImage = {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                    CosmoScreen.SETTINGS -> SettingsScreen(
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}
