// app/src/main/java/com/example/agent/ui/LoginScreen.kt
package com.example.agent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.agent.CredentialStore
import com.example.agent.RemoteViewModel
import com.example.agent.model.ConnState

//const val server =  "http://10.50.56.187:3000"
const val server =  "http://192.168.31.86:3000"
//const val server =  "http://10.0.7.44:3000"

@Composable
fun LoginScreen(
    onConnected: () -> Unit,
    vm: RemoteViewModel = viewModel()
) {
    val context    = LocalContext.current
    val connState by vm.connectionState.collectAsState()

    //  0 = Manual login, 1 = Register
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
            Tab(
                selected = tab == 0, onClick = { tab = 0; errorMsg = null },
                text = { Text("Login") }
            )
            Tab(
                selected = tab == 1, onClick = { tab = 1; errorMsg = null },
                text = { Text("Register") }
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (tab) {

                // ── Tab 1: Manual Login ────────────────────────────────────────────
                0 -> ManualLoginTab(
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
                            onSuccess   = { result ->
                                vm.setDeviceId(result.deviceId)
                                CredentialStore.save(context, httpUrl, result.jwt, result.deviceId, result.deviceToken)
                                vm.connect(httpUrl, result.jwt, result.deviceId)
                            },
                            onError = { errorMsg = it }
                        )
                    }
                )

                // ── Tab 2: Register ────────────────────────────────────────────────
                1 -> RegisterTab(
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
                            onSuccess   = { result ->
                                vm.setDeviceId(result.deviceId)
                                CredentialStore.save(context, httpUrl, result.jwt, result.deviceId, result.deviceToken)
                                successMsg = "Registered! Connecting…"
                                vm.connect(httpUrl, result.jwt, result.deviceId)
                            },
                            onError = { errorMsg = it }
                        )
                    }
                )
            }
        }
    }
}



// ── Manual login tab ───────────────────────────────────────────────────────────
@Composable
fun ManualLoginTab(
    connState:  ConnState,
    errorMsg:   String?,
    successMsg: String?,
    onLogin:    (url: String, user: String, pwd: String) -> Unit
) {
    val focus = LocalFocusManager.current
    //val SERVER_URL = "http://10.50.56.128:3000"
    //val SERVER_URL = "http://10.50.56.65:3000"
    //val SERVER_URL = "http://192.168.31.86:3000"
    val SERVER_URL = server
    var url   by remember { mutableStateOf("ws://") }
    var user  by remember { mutableStateOf("") }
    var pwd   by remember { mutableStateOf("") }
    var show  by remember { mutableStateOf(false) }

    LoginForm(
        serverUrl    = url,  onUrlChange   = { url  = it },
        username     = user, onUserChange  = { user = it },
        password     = pwd,  onPwdChange   = { pwd  = it },
        showPassword = show, onToggleShow  = { show = !show },
        buttonText   = "Login",
        connState    = connState,
        errorMsg     = errorMsg,
        successMsg   = successMsg,
        onSubmit     = { focus.clearFocus(); onLogin(SERVER_URL, user, pwd) }
    )
}

// ── Register tab ───────────────────────────────────────────────────────────────
@Composable
fun RegisterTab(
    connState:  ConnState,
    errorMsg:   String?,
    successMsg: String?,
    onRegister: (url: String, user: String, pwd: String) -> Unit
) {
    val focus = LocalFocusManager.current
    //val SERVER_URL = "http://10.50.56.128:3000"
    //val SERVER_URL = "http://10.50.56.65:3000"
    //val SERVER_URL = "http://192.168.31.86:3000"
    val SERVER_URL = server
    var url   by remember { mutableStateOf("ws://") }
    var user  by remember { mutableStateOf("") }
    var pwd   by remember { mutableStateOf("") }
    var show  by remember { mutableStateOf(false) }

    LoginForm(
        serverUrl    = url,  onUrlChange   = { url  = it },
        username     = user, onUserChange  = { user = it },
        password     = pwd,  onPwdChange   = { pwd  = it },
        showPassword = show, onToggleShow  = { show = !show },
        buttonText   = "Register & Connect",
        connState    = connState,
        errorMsg     = errorMsg,
        successMsg   = successMsg,
        onSubmit     = { focus.clearFocus(); onRegister(SERVER_URL, user, pwd) }
    )
}

// ── Shared form ────────────────────────────────────────────────────────────────
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
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = username, onValueChange = onUserChange,
            label = { Text("Username") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(
                onNext = { focus.moveFocus(FocusDirection.Down) })
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
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
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
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = connState != ConnState.CONNECTING
                    && serverUrl.isNotBlank()
                    && username.isNotBlank()
                    && password.isNotBlank()
        ) {
            if (connState == ConnState.CONNECTING) {
                CircularProgressIndicator(
                    Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("Connecting…")
            } else {
                Text(buttonText, fontSize = 15.sp)
            }
        }
    }
}