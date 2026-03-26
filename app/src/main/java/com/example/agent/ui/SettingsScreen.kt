// app/src/main/java/com/example/agent/ui/SettingsScreen.kt
package com.example.agent.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.agent.CredentialStore
import com.example.agent.QrPairScanner
import com.example.agent.RemoteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack:   () -> Unit,
    onLogout: () -> Unit,
    vm: RemoteViewModel = viewModel()
) {
    val context     = LocalContext.current
    val connState  by vm.connectionState.collectAsState()
    val agentOnline by vm.agentOnline.collectAsState()

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {

            // ── Connection status section ──────────────────────────────────
            SettingsSection(title = "Connection") {
                StatusRow(
                    label = "Server",
                    value = connState.label,
                    icon  = Icons.Default.Wifi
                )
                StatusRow(
                    label = "Agent",
                    value = if (agentOnline) "Online" else "Offline",
                    icon  = Icons.Default.Computer
                )

                // Saved server URL
                val savedUrl = remember {
                    CredentialStore.load(context)?.first ?: "Not saved"
                }
                StatusRow(
                    label = "Server URL",
                    value = savedUrl,
                    icon  = Icons.Default.Link
                )
            }

            SettingsSection(title = "Devices") {

                Button(
                    onClick = { showScanner = true },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                ) {
                    Text("Pair new device")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── Stream quality section ─────────────────────────────────────
            SettingsSection(title = "Stream quality") {
                Text(
                    text     = "Quality is configured on the desktop agent side in agent/.env (QUALITY, FPS, SCALE settings).",
                    fontSize = 13.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── About section ──────────────────────────────────────────────
            SettingsSection(title = "About") {
                StatusRow(
                    label = "Version",
                    value = "1.0.0",
                    icon  = Icons.Default.Info
                )
                StatusRow(
                    label = "AI backend",
                    value = "Gemini / Claude / Ollama",
                    icon  = Icons.Default.Psychology
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── Account section ────────────────────────────────────────────
            SettingsSection(title = "Account") {
                ListItem(
                    headlineContent = {
                        Text(
                            text  = "Disconnect & logout",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    supportingContent = {
                        Text("Clears saved credentials and returns to login")
                    },
                    leadingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Button(
                    onClick = { showLogoutDialog = true },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor   = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth()
                ) {
                    Text("Logout")
                }
            }
        }
    }

    // ── Logout confirmation dialog ─────────────────────────────────────────
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon    = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
            title   = { Text("Logout?") },
            text    = { Text("This will disconnect the app and clear your saved server credentials.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        vm.disconnect()
                        onLogout()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Logout") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    if (showScanner) {
        QrPairScanner(
            onClose = { showScanner = false },
            onScanned = { pairToken ->
                vm.pairViaQr(
                    pairToken = pairToken,
                    context = context,
                    onSuccess = { agentDeviceToken ->
                        showScanner = false

                        val saved = CredentialStore.load(context)
                        if (saved != null) {
                            val (serverUrl, jwt, _) = saved

                            vm.setDeviceId(agentDeviceToken)
                            vm.connect(serverUrl, jwt)
                        }
                    },
                    onError = {
                        showScanner = false
                        // optionally show error
                    }
                )
            }
        )
    }
}

// ── Helper composables ─────────────────────────────────────────────────────

@Composable
fun SettingsSection(
    title:   String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text     = title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color    = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
        )
        content()
    }
}

@Composable
fun StatusRow(
    label: String,
    value: String,
    icon:  androidx.compose.ui.graphics.vector.ImageVector
) {
    ListItem(
        headlineContent   = { Text(label) },
        supportingContent = { Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingContent    = {
            Icon(icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
fun AiSettings() {
    val context = LocalContext.current

    var provider by remember { mutableStateOf("openai") }
    var apiKey by remember { mutableStateOf("") }

    Column {
        Text("AI Provider")

        OutlinedTextField(
            value = provider,
            onValueChange = { provider = it },
            label = { Text("Provider (openai)") }
        )

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key") }
        )

        Button(onClick = {
            CredentialStore.saveAi(context, provider, apiKey)
        }) {
            Text("Save AI Settings")
        }
    }
}