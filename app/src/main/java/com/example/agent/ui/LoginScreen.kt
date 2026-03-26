// app/src/main/java/com/example/agent/ui/LoginScreen.kt
package com.example.agent.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.agent.CredentialStore
import com.example.agent.RemoteViewModel
import com.example.agent.model.ConnState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.json.JSONObject
import java.util.concurrent.Executors

@Composable
fun LoginScreen(
    onConnected: () -> Unit,
    vm: RemoteViewModel = viewModel()
) {
    val context      = LocalContext.current
    val connState   by vm.connectionState.collectAsState()

    // Tab: 0=QR scan, 1=Manual login, 2=Register
    var tab        by remember { mutableIntStateOf(0) }
    var errorMsg   by remember { mutableStateOf<String?>(null) }
    var successMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(connState) {
        if (connState == ConnState.CONNECTED) onConnected()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        // Title
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("PC Agent", fontSize = 28.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Text("Remote desktop control", fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp))
        }

        // Tabs
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0; errorMsg = null },
                icon = { Icon(Icons.Default.QrCodeScanner, null, Modifier.size(18.dp)) },
                text = { Text("QR Scan") })
            Tab(selected = tab == 1, onClick = { tab = 1; errorMsg = null },
                text = { Text("Login") })
            Tab(selected = tab == 2, onClick = { tab = 2; errorMsg = null },
                text = { Text("Register") })
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (tab) {
                0 -> QrScanTab(
                    onScanned = { serverUrl, qrToken ->
                        errorMsg = null
                        vm.connectViaQr(
                            serverUrl = serverUrl,
                            qrToken   = qrToken,
                            context   = context,
                            onSuccess = { combined ->
                                val parts = combined.split("|")
                                val jwt = parts[0]
                                val deviceId = parts[1]

                                vm.setDeviceId(deviceId)
                                CredentialStore.save(context, serverUrl, jwt, deviceId)

                                vm.connect(serverUrl, jwt)   // ✅ REQUIRED
                            },
                            onError = { errorMsg = it }
                        )
                    }
                )

                1 -> ManualLoginTab(
                    connState  = connState,
                    errorMsg   = errorMsg,
                    successMsg = successMsg,
                    onLogin    = { url, user, pwd ->
                        errorMsg = null
                        val httpUrl = url.replace("ws://","http://")
                            .replace("wss://","https://")
                            .removeSuffix("/agent").removeSuffix("/phone")
                        vm.login(
                            httpBaseUrl = httpUrl,
                            username    = user,
                            password    = pwd,
                            onSuccess = { result ->
                                val jwt = result.jwt
                                val deviceId = result.deviceId

                                vm.setDeviceId(deviceId)

                                CredentialStore.save(context, url, jwt, deviceId)

                                vm.connect(url, jwt)
                            },
                            onError = { errorMsg = it }
                        )
                    }
                )

                2 -> RegisterTab(
                    connState  = connState,
                    errorMsg   = errorMsg,
                    successMsg = successMsg,
                    onRegister = { url, user, pwd ->
                        errorMsg = null
                        val httpUrl = url.replace("ws://","http://")
                            .replace("wss://","https://")
                            .removeSuffix("/agent").removeSuffix("/phone")
                        vm.register(
                            httpBaseUrl = httpUrl,
                            username    = user,
                            password    = pwd,
                            onSuccess = { result ->
                                val jwt = result.jwt
                                val deviceId = result.deviceId

                                vm.setDeviceId(deviceId)

                                CredentialStore.save(context, url, jwt, deviceId)

                                successMsg = "Registered! Connecting..."
                                vm.connect(url, jwt)
                            },
                            onError = { errorMsg = it }
                        )
                    }
                )
            }
        }
    }
}

// ── QR Scan tab ────────────────────────────────────────────────────────────
@Composable
fun QrScanTab(onScanned: (serverUrl: String, qrToken: String) -> Unit) {
    val context       = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCam        by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) }
    var scanned       by remember { mutableStateOf(false) }
    var statusText    by remember { mutableStateOf("Point camera at QR code") }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasCam = it }

    LaunchedEffect(Unit) {
        if (!hasCam) permLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCam) {
            // Camera preview
            val executor = remember { Executors.newSingleThreadExecutor() }
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraFuture.addListener({
                        val provider = cameraFuture.get()
                        val preview  = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        val scanner = BarcodeScanning.getClient()
                        analysis.setAnalyzer(executor) { imageProxy ->
                            if (scanned) { imageProxy.close(); return@setAnalyzer }
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val image = InputImage.fromMediaImage(
                                    mediaImage, imageProxy.imageInfo.rotationDegrees)
                                scanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        for (bc in barcodes) {
                                            if (bc.format == Barcode.FORMAT_QR_CODE) {
                                                val raw = bc.rawValue ?: continue
                                                try {
                                                    val json = JSONObject(raw)
                                                    val url  = json.getString("url")
                                                    val tok  = json.getString("token")
                                                    scanned = true
                                                    onScanned(url, tok)
                                                } catch (e: Exception) {
                                                    // Not our QR format — ignore
                                                }
                                            }
                                        }
                                    }
                                    .addOnCompleteListener { imageProxy.close() }
                            } else {
                                imageProxy.close()
                            }
                        }

                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                        } catch (e: Exception) { /* ignore */ }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (scanned) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("Connecting...", color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp)
                } else {
                    Surface(shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)) {
                        Text(statusText, modifier = Modifier.padding(12.dp, 8.dp),
                            fontSize = 13.sp)
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Camera permission required", fontSize = 15.sp)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Permission")
                }
            }
        }
    }
}

// ── Manual login tab ───────────────────────────────────────────────────────
@Composable
fun ManualLoginTab(
    connState:  ConnState,
    errorMsg:   String?,
    successMsg: String?,
    onLogin:    (url: String, user: String, pwd: String) -> Unit
) {
    val focus = LocalFocusManager.current
    var url   by remember { mutableStateOf("ws://") }
    var user  by remember { mutableStateOf("") }
    var pwd   by remember { mutableStateOf("") }
    var show  by remember { mutableStateOf(false) }

    LoginForm(
        serverUrl    = url, onUrlChange   = { url  = it },
        username     = user, onUserChange = { user = it },
        password     = pwd, onPwdChange  = { pwd  = it },
        showPassword = show, onToggleShow = { show = !show },
        buttonText   = "Login",
        connState    = connState,
        errorMsg     = errorMsg,
        successMsg   = successMsg,
        onSubmit     = { focus.clearFocus(); onLogin(url, user, pwd) }
    )
}

// ── Register tab ───────────────────────────────────────────────────────────
@Composable
fun RegisterTab(
    connState:  ConnState,
    errorMsg:   String?,
    successMsg: String?,
    onRegister: (url: String, user: String, pwd: String) -> Unit
) {
    val focus = LocalFocusManager.current
    var url   by remember { mutableStateOf("ws://") }
    var user  by remember { mutableStateOf("") }
    var pwd   by remember { mutableStateOf("") }
    var show  by remember { mutableStateOf(false) }

    LoginForm(
        serverUrl    = url, onUrlChange   = { url  = it },
        username     = user, onUserChange = { user = it },
        password     = pwd, onPwdChange  = { pwd  = it },
        showPassword = show, onToggleShow = { show = !show },
        buttonText   = "Register & Connect",
        connState    = connState,
        errorMsg     = errorMsg,
        successMsg   = successMsg,
        onSubmit     = { focus.clearFocus(); onRegister(url, user, pwd) }
    )
}

// ── Shared form ────────────────────────────────────────────────────────────
@Composable
fun LoginForm(
    serverUrl:    String, onUrlChange:   (String) -> Unit,
    username:     String, onUserChange:  (String) -> Unit,
    password:     String, onPwdChange:   (String) -> Unit,
    showPassword: Boolean, onToggleShow: () -> Unit,
    buttonText:   String,
    connState:    ConnState,
    errorMsg:     String?,
    successMsg:   String?,
    onSubmit:     () -> Unit
) {
    val focus = LocalFocusManager.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        OutlinedTextField(
            value = serverUrl, onValueChange = onUrlChange,
            label = { Text("Server URL") },
            placeholder = { Text("ws://192.168.1.x:3000") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) })
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = username, onValueChange = onUserChange,
            label = { Text("Username") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) })
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = password, onValueChange = onPwdChange,
            label = { Text("Password") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (showPassword) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onToggleShow) {
                    Icon(if (showPassword) Icons.Default.VisibilityOff
                    else Icons.Default.Visibility, null)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() })
        )
        errorMsg?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp))
        }
        successMsg?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick  = onSubmit,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled  = connState != ConnState.CONNECTING &&
                    serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
        ) {
            if (connState == ConnState.CONNECTING) {
                CircularProgressIndicator(Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Connecting...")
            } else {
                Text(buttonText, fontSize = 15.sp)
            }
        }
    }
}