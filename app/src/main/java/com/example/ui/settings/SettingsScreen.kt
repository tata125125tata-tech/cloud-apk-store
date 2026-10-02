package com.example.ui.settings

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.WorkerStatusBadge
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.VioletAccent
import com.example.ui.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val currentSettings by viewModel.settings.collectAsState()
    val health by viewModel.workerHealth.collectAsState()

    var workerUrlInput by remember(currentSettings.workerUrl) { mutableStateOf(currentSettings.workerUrl) }
    var apiVersionInput by remember(currentSettings.apiVersion) { mutableStateOf(currentSettings.apiVersion) }
    var chunkSizeMb by remember(currentSettings.chunkSizeMb) { mutableIntStateOf(currentSettings.chunkSizeMb) }
    var maxRetries by remember(currentSettings.maxRetries) { mutableIntStateOf(currentSettings.maxRetries) }
    var authTokenInput by remember(currentSettings.authToken) { mutableStateOf(currentSettings.authToken) }
    var customDomainInput by remember(currentSettings.customDomainUrl) { mutableStateOf(currentSettings.customDomainUrl) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        item {
            Column {
                Text(
                    text = "Worker Configuration",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Configure Cloudflare Worker API & upload parameters",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Live Connection Test Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_connection_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Connection Status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        WorkerStatusBadge(health = health)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Current URL: ${currentSettings.cleanBaseUrl}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.testConnection() },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_test_connection_button")
                    ) {
                        Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Connection (Ping)")
                    }
                }
            }
        }

        // Worker Base URL Input
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Worker API URL",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Communicates exclusively with your Cloudflare Worker. Cloudflare API tokens and R2 keys are never inside the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = workerUrlInput,
                        onValueChange = { workerUrlInput = it },
                        label = { Text("Worker Base URL") },
                        placeholder = { Text("https://tight-surf-5eff.play125store.workers.dev") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_worker_url_input"),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = apiVersionInput,
                        onValueChange = { apiVersionInput = it },
                        label = { Text("API Version") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_api_version_input"),
                        singleLine = true
                    )
                }
            }
        }

        // Upload Parameters Card (Chunk Size & Retries)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Chunked Upload Parameters",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Default chunk size is 10 MB. Never loads the whole file into RAM.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Chunk Size (MB)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(5, 10, 20).forEach { size ->
                            FilterChip(
                                selected = chunkSizeMb == size,
                                onClick = { chunkSizeMb = size },
                                label = { Text("$size MB") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Max Retry Attempts per Chunk",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1, 3, 5).forEach { retry ->
                            FilterChip(
                                selected = maxRetries == retry,
                                onClick = { maxRetries = retry },
                                label = { Text("$retry retries") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Security & Custom Domain
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Security & Public Domain",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = authTokenInput,
                        onValueChange = { authTokenInput = it },
                        label = { Text("Worker Auth Token (Optional)") },
                        placeholder = { Text("Bearer token for Worker verification") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_auth_token_input"),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = customDomainInput,
                        onValueChange = { customDomainInput = it },
                        label = { Text("Custom Public CDN URL (Optional)") },
                        placeholder = { Text("https://pub-r2.yourdomain.com") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_custom_domain_input"),
                        singleLine = true
                    )
                }
            }
        }

        // Save & Reset Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.resetSettings()
                        workerUrlInput = "https://tight-surf-5eff.play125store.workers.dev"
                        apiVersionInput = "v1"
                        chunkSizeMb = 10
                        maxRetries = 3
                        authTokenInput = ""
                        customDomainInput = ""
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("settings_reset_button")
                ) {
                    Text("Reset Defaults")
                }

                Button(
                    onClick = {
                        viewModel.updateSettings(
                            workerUrl = workerUrlInput,
                            apiVersion = apiVersionInput,
                            chunkSizeMb = chunkSizeMb,
                            maxRetries = maxRetries,
                            authToken = authTokenInput,
                            customDomainUrl = customDomainInput
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("settings_save_button")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Settings")
                }
            }
        }

        // GitHub Actions & Android App Builder Architecture Guide
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "GitHub Actions APK Build System",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "1. Export Project: Download the full Android project source from Android App Builder.\n" +
                               "2. Push to GitHub: Push to main/master branch.\n" +
                               "3. Automatic Build: .github/workflows/android-build.yml triggers automatically, sets up JDK 17, and runs ./gradlew assembleDebug.\n" +
                               "4. Download APK: Go to GitHub Actions run -> download 'cosmo-cloud-manager-debug-apk'.\n" +
                               "5. Release Signing: Set KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, and KEY_PASSWORD secrets in GitHub Repository Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
