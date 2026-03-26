// app/src/main/java/com/example/agent/RemoteViewModel.kt
package com.example.agent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.agent.model.ChatMessage
import com.example.agent.model.ConnState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
class RemoteViewModel : ViewModel() {

    // ── Exposed state ──────────────────────────────────────────────────────────

    private val _frameBitmap      = MutableStateFlow<Bitmap?>(null)
    val frameBitmap: StateFlow<Bitmap?> = _frameBitmap.asStateFlow()

    private val _chatMessages     = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _connectionState  = MutableStateFlow(ConnState.DISCONNECTED)
    val connectionState: StateFlow<ConnState> = _connectionState.asStateFlow()

    private val _agentOnline      = MutableStateFlow(false)
    val agentOnline: StateFlow<Boolean> = _agentOnline.asStateFlow()

    private val _isAiRunning      = MutableStateFlow(false)
    val isAiRunning: StateFlow<Boolean> = _isAiRunning.asStateFlow()

    // ── Internal state ─────────────────────────────────────────────────────────

    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    private var ws:           WebSocket? = null
    private var serverUrl:    String     = ""
    private var token:        String     = ""
    private var deviceId:     String     = ""
    private var retryDelay:   Long       = 1_000L
    private var shouldRetry:  Boolean    = false
    private var reconnectJob: Job?       = null

    // ── Login ──────────────────────────────────────────────────────────────────

    fun login(
        httpBaseUrl: String,
        username:    String,
        password:    String,
        onSuccess:   (AuthResult) -> Unit,
        onError:     (String) -> Unit
    ) {
        _connectionState.value = ConnState.CONNECTING
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url  = httpBaseUrl.trimEnd('/') + "/api/login"
                val body = JSONObject()
                    .put("username", username)
                    .put("password", password)
                    .toString()
                    .toRequestBody("application/json".toMediaType())
                val response = client.newCall(
                    Request.Builder().url(url).post(body).build()
                ).execute()
                val respBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json        = JSONObject(respBody)
                    val jwt         = json.getString("token")
                    val deviceId    = json.getString("deviceId")
                    val deviceToken = json.optString("deviceToken", "")
                    withContext(Dispatchers.Main) {
                        onSuccess(AuthResult(jwt = jwt, deviceId = deviceId, deviceToken = deviceToken))
                    }
                } else {
                    val msg = runCatching { JSONObject(respBody).optString("error","Login failed") }
                        .getOrDefault("Login failed (${response.code})")
                    _connectionState.value = ConnState.DISCONNECTED
                    withContext(Dispatchers.Main) { onError(msg) }
                }
            } catch (e: Exception) {
                _connectionState.value = ConnState.DISCONNECTED
                withContext(Dispatchers.Main) { onError("Cannot reach server: ${e.message}") }
            }
        }
    }

    // ── Register ───────────────────────────────────────────────────────────────

    fun register(
        httpBaseUrl: String,
        username:    String,
        password:    String,
        onSuccess:   (AuthResult) -> Unit,
        onError:     (String) -> Unit
    ) {
        _connectionState.value = ConnState.CONNECTING
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url  = httpBaseUrl.trimEnd('/') + "/api/register"
                val body = JSONObject()
                    .put("username", username)
                    .put("password", password)
                    .toString()
                    .toRequestBody("application/json".toMediaType())
                val response = client.newCall(
                    Request.Builder().url(url).post(body).build()
                ).execute()
                val respBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    // Auto-login after successful registration
                    withContext(Dispatchers.Main) {
                        login(httpBaseUrl, username, password, onSuccess, onError)
                    }
                } else {
                    val msg = runCatching { JSONObject(respBody).optString("error","Registration failed") }
                        .getOrDefault("Registration failed (${response.code})")
                    _connectionState.value = ConnState.DISCONNECTED
                    withContext(Dispatchers.Main) { onError(msg) }
                }
            } catch (e: Exception) {
                _connectionState.value = ConnState.DISCONNECTED
                withContext(Dispatchers.Main) { onError("Cannot reach server: ${e.message}") }
            }
        }
    }

    fun setDeviceId(id: String) { deviceId = id }

    // ── QR pairing ─────────────────────────────────────────────────────────────
    //
    // Called when the phone has scanned a QR code.
    //
    // New flow:
    //   1. Phone must already be authenticated (JWT stored in CredentialStore).
    //   2. QR payload contains { url, pair_token } — no device token, no secret.
    //   3. Phone sends POST /api/pair  { pairToken }  with its own JWT.
    //   4. Server returns { agentDeviceToken } which the phone uses to open WebSocket.
    //
    // If the phone is NOT yet logged in, onError is called with a clear message
    // so the UI can redirect to the Login tab.
    //
    fun pairViaQr(
        pairToken: String,
        context: Context,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ){
        // Must be authenticated first
        val saved = CredentialStore.load(context)
        if (saved == null) {
            onError("Please log in first")
            return
        }

        val (serverUrl, jwt, _) = saved

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = "$serverUrl/api/pair"
                val body = JSONObject()
                    .put("pairToken", pairToken)
                    .toString()
                    .toRequestBody("application/json".toMediaType())
                val response = client.newCall(
                    Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer $jwt")
                        .post(body)
                        .build()
                ).execute()
                val respBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json             = JSONObject(respBody)
                    val agentDeviceToken = json.getString("agentDeviceToken")
                    Log.d("QR_PAIR", "Paired! agentDeviceToken=$agentDeviceToken")
                    withContext(Dispatchers.Main) { onSuccess(agentDeviceToken) }
                } else {
                    val msg = runCatching { JSONObject(respBody).optString("error","Pairing failed") }
                        .getOrDefault("Pairing failed (${response.code})")
                    withContext(Dispatchers.Main) { onError(msg) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError("Cannot reach server: ${e.message}") }
            }
        }
    }

    // ── WebSocket connection ───────────────────────────────────────────────────

    fun connect(wsUrl: String, jwtToken: String) {
        serverUrl   = wsUrl.trimEnd('/')
        token       = jwtToken
        shouldRetry = true
        retryDelay  = 1_000L

        if (deviceId.isBlank()) {
            Log.e("WS_DEBUG", "❌ deviceId is EMPTY — aborting connect")
            return
        }
        doConnect()
    }

    private fun doConnect() {
        _connectionState.value = ConnState.CONNECTING
        val url = "$serverUrl/phone?token=$token&deviceId=$deviceId"
        Log.d("WS_DEBUG", "🌐 Connecting to: $url")

        ws = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WS_DEBUG", "✅ WebSocket OPENED")
                retryDelay             = 1_000L
                _connectionState.value = ConnState.CONNECTED
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val arr = bytes.toByteArray()
                BitmapFactory.decodeByteArray(arr, 0, arr.size)?.let { _frameBitmap.value = it }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleJsonMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WS_DEBUG", "❌ WebSocket FAILURE: ${t.message}")
                _connectionState.value = ConnState.RECONNECTING
                _agentOnline.value     = false
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WS_DEBUG", "🔌 WebSocket CLOSED: $reason")
                _connectionState.value = ConnState.DISCONNECTED
                _agentOnline.value     = false
                if (shouldRetry) scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!shouldRetry) return
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            delay(retryDelay)
            retryDelay = (retryDelay * 2).coerceAtMost(30_000L)
            if (shouldRetry) doConnect()
        }
    }

    fun disconnect() {
        shouldRetry            = false
        reconnectJob?.cancel()
        ws?.close(1000, "User disconnected")
        ws                     = null
        _connectionState.value = ConnState.DISCONNECTED
        _agentOnline.value     = false
        _isAiRunning.value     = false
    }

    // ── Incoming message handler ───────────────────────────────────────────────

    private fun handleJsonMessage(text: String) {
        try {
            val json = JSONObject(text)
            when (json.getString("type")) {
                "ai_msg"       -> {
                    _isAiRunning.value = true
                    addMessage(ChatMessage(role = ChatMessage.Role.ASSISTANT,
                        text = json.getString("text")))
                }
                "ai_action"    -> {
                    addMessage(ChatMessage(
                        role = ChatMessage.Role.ACTION,
                        text = "${json.optString("action","")} ${json.optJSONObject("params") ?: ""}"
                    ))
                }
                "ai_done"      -> {
                    _isAiRunning.value = false
                    addMessage(ChatMessage(role = ChatMessage.Role.DONE,
                        text = "✓ ${json.optString("summary","Task complete")}"))
                }
                "agent_status" -> _agentOnline.value = json.optBoolean("online", false)
                "error"        -> {
                    _isAiRunning.value = false
                    addMessage(ChatMessage(role = ChatMessage.Role.ERROR,
                        text = json.optString("text","Unknown error")))
                }
            }
        } catch (_: Exception) { /* ignore malformed messages */ }
    }

    // ── Outgoing messages ──────────────────────────────────────────────────────

    fun sendGoal(context: Context, text: String) {
        val ai = CredentialStore.getAi(context)

        val json = JSONObject().apply {
            put("type", "user_msg")
            put("text", text)

            ai?.let {
                put("ai_provider", it.provider)
                put("ai_key", it.apiKey)
            }
        }

        ws?.send(json.toString())
        addMessage(ChatMessage(role = ChatMessage.Role.USER, text = text))
    }


    fun sendTap(relX: Float, relY: Float) =
        ws?.send("""{"type":"manual","action":"click","relX":$relX,"relY":$relY}""")

    fun sendDoubleTap(relX: Float, relY: Float) =
        ws?.send("""{"type":"manual","action":"click","relX":$relX,"relY":$relY,"clicks":2}""")

    fun sendRightClick(relX: Float, relY: Float) =
        ws?.send("""{"type":"manual","action":"click","relX":$relX,"relY":$relY,"button":"right"}""")

    fun sendScroll(relX: Float, relY: Float, direction: String) =
        ws?.send("""{"type":"manual","action":"scroll","relX":$relX,"relY":$relY,"direction":"$direction"}""")

    fun sendShortcut(keys: List<String>) {
        val keysJson = keys.joinToString(",") { "\"$it\"" }
        ws?.send("""{"type":"manual","action":"key","keys":[$keysJson]}""")
    }

    fun stopAiTask() {
        ws?.send("""{"type":"stop_ai"}""")
        _isAiRunning.value = false
        addMessage(ChatMessage(role = ChatMessage.Role.ERROR, text = "Task stopped by user"))
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun addMessage(message: ChatMessage) {
        _chatMessages.value += message
    }

    override fun onCleared() {
        super.onCleared()
        shouldRetry = false
        reconnectJob?.cancel()
        ws?.close(1000, "ViewModel cleared")
        client.dispatcher.executorService.shutdown()
    }
}