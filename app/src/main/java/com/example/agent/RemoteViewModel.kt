// app/src/main/java/com/example/agent/RemoteViewModel.kt
package com.example.agent

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.agent.model.ChatMessage
import com.example.agent.model.ConnState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
class RemoteViewModel : ViewModel() {


    private val wsManager = WebSocketManager(
        onFrame = { _frameBitmap.value = it },
        onMessage = { handleJsonMessage(it) },
        onState = { _connectionState.value = it }
    )

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
    private var deviceId:     String     = ""

    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

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

        val (serverUrl, jwt, _, _) = saved

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

    // app/src/main/java/com/example/agent/RemoteViewModel.kt

    fun connect(wsUrl: String, jwtToken: String, deviceId: String) {
        Log.d("WS_CONNECT", "========== CONNECT CALLED ==========")
        Log.d("WS_CONNECT", "wsUrl: $wsUrl")
        Log.d("WS_CONNECT", "jwtToken: ${jwtToken.take(30)}...")
        Log.d("WS_CONNECT", "deviceId: $deviceId")

        // Убеждаемся что URL правильный
        var correctedUrl = wsUrl
        if (!correctedUrl.startsWith("ws://") && !correctedUrl.startsWith("wss://")) {
            correctedUrl = "ws://$correctedUrl"
            Log.d("WS_CONNECT", "Corrected URL: $correctedUrl")
        }

        // Убираем лишние слеши
        correctedUrl = correctedUrl.replace(Regex("(ws://|wss://)/+"), "$1")

        Log.d("WS_CONNECT", "Final URL: $correctedUrl")
        wsManager.connect(correctedUrl, jwtToken, deviceId)
    }

    fun disconnect() {
        wsManager.disconnect()
    }

    // ── Incoming message handler ───────────────────────────────────────────────
    private fun handleJsonMessage(json: JSONObject) {
        Log.d("HANDLEJSNMSG", "Received: ${json.toString()}")

        try {
            val type = json.getString("type")
            Log.d("HANDLEJSNMSG", "Type: $type")

            when (type) {
                "ai_msg" -> {
                    _isAiRunning.value = true
                    addMessage(ChatMessage(
                        role = ChatMessage.Role.ASSISTANT,
                        text = json.getString("text")
                    ))
                }

                "ai_action" -> {
                    _isAiRunning.value = true
                    val action = json.getString("action")
                    val params = json.optJSONObject("params") ?: JSONObject()
                    val text = json.optString("text", "▶ $action")
                    addMessage(ChatMessage(
                        role = ChatMessage.Role.ACTION,
                        text = text
                    ))
                }

                "execute_action" -> {
                    // Сервер отправляет execute_action, но мы показываем как action
                    _isAiRunning.value = true
                    val action = json.getString("action")
                    val params = json.optJSONObject("params") ?: JSONObject()
                    addMessage(ChatMessage(
                        role = ChatMessage.Role.ACTION,
                        text = "▶ $action ${params.toString()}"
                    ))
                }

                "ai_done" -> {
                    _isAiRunning.value = false
                    val summary = json.optString("summary", "Task complete")
                    val status = json.optString("status", "success")
                    if (status == "error" || summary.lowercase().contains("failed")) {
                        addMessage(ChatMessage(
                            role = ChatMessage.Role.ERROR,
                            text = "❌ $summary"
                        ))
                    } else {
                        addMessage(ChatMessage(
                            role = ChatMessage.Role.DONE,
                            text = "✅ $summary"
                        ))
                    }
                    Log.d("ai_done", "Task finished: $summary")
                }

                "agent_status" -> {
                    _agentOnline.value = json.optBoolean("online", false)
                    Log.d("AGENT_STATUS", "Agent online: ${_agentOnline.value}")
                }

                "status" -> {
                    val statusText = json.optString("text", "")
                    if (statusText.isNotEmpty()) {
                        addMessage(ChatMessage(
                            role = ChatMessage.Role.ASSISTANT,
                            text = statusText
                        ))
                    }
                }

                "task_start" -> {
                    _isAiRunning.value = true
                    addMessage(ChatMessage(
                        role = ChatMessage.Role.ASSISTANT,
                        text = "🤔 Thinking..."
                    ))
                }

                "error" -> {
                    _isAiRunning.value = false
                    val errorText = json.optString("text", "Unknown error")
                    addMessage(ChatMessage(
                        role = ChatMessage.Role.ERROR,
                        text = "❌ $errorText"
                    ))
                    Log.e("AI_ERROR", errorText)
                }

                "phone_connected", "phone_disconnected" -> {
                    // These are internal signals for the desktop agent only.
                    Log.d("PHONE_STATUS", "Phone $type")
                }

                "request_screenshot" -> {
                    // Это сообщение для агента, телефон игнорирует
                    Log.d("SCREENSHOT", "Server requested screenshot from agent")
                }

                else -> {
                    Log.d("HANDLEJSNMSG", "Unhandled message type: $type")
                }
            }
        } catch (e: Exception) {
            Log.e("HANDLEJSNMSG", "Error parsing message: ${e.message}", e)
        }
    }

    // ── Outgoing messages ──────────────────────────────────────────────────────

    fun sendGoal(context: Context, text: String) {
        val ai = CredentialStore.getAi(context)

        val json = JSONObject().apply {
            put("type", "user_msg")
            put("text", text)

            // Send AI settings if available
            ai?.let {
                put("ai_provider", it.provider)
                put("ai_key", it.apiKey)
                Log.d("AI_SETTINGS", "Using AI provider: ${it.provider}")
            }
        }

        Log.d("SEND_GOAL", "Sending: $json")
        wsManager.sendRaw(json)
        addMessage(ChatMessage(role = ChatMessage.Role.USER, text = text))
    }


    fun sendTap(relX: Float, relY: Float) {
        val action = JSONObject().apply {
            put("action", "click")
            put("relX", relX)
            put("relY", relY)
        }
        wsManager.sendAction(action)
    }

    fun sendDoubleTap(relX: Float, relY: Float) {
        val action = JSONObject().apply {
            put("action", "click")
            put("clicks", 2)
            put("relX", relX)
            put("relY", relY)
        }
        wsManager.sendAction(action)
    }

    fun sendRightClick(relX: Float, relY: Float) {
        val action = JSONObject().apply {
            put("action", "click")
            put("button", "right")
            put("relX", relX)
            put("relY", relY)
        }
        wsManager.sendAction(action)
    }

    fun sendScroll(relX: Float, relY: Float, direction: String) {
        val action = JSONObject().apply {
            put("action", "scroll")
            put("relX", relX)
            put("relY", relY)
            put("direction", direction)
        }

        wsManager.sendAction(action)
    }

    fun sendText(text: String) {
        val action = JSONObject().apply {
            put("action", "type")
            put("text", text)
        }

        wsManager.sendAction(action)
    }

    fun sendKey(key: String) {
        val action = JSONObject().apply {
            put("action", "key")
            put("keys", JSONArray().put(key))
        }

        wsManager.sendAction(action)
    }
    fun sendShortcut(keys: List<String>) {
        Log.d("WS_DEBUG", "Shortcut pressed: $keys")

        val arr = JSONArray()
        keys.forEach { arr.put(it.lowercase()) } // 🔥 important

        val action = JSONObject().apply {
            put("action", "key")
            put("keys", arr)
        }

        wsManager.sendAction(action)
    }

    fun sendDrag(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        screenWidth: Int,
        screenHeight: Int
    ) {
        val action = JSONObject().apply {
            put("action", "drag")
            put("startX", (startX * screenWidth).toInt())
            put("startY", (startY * screenHeight).toInt())
            put("endX", (endX * screenWidth).toInt())
            put("endY", (endY * screenHeight).toInt())
        }

        wsManager.sendAction(action)
    }
    fun stopAiTask() {
        val json = JSONObject().apply {
            put("type", "stop_ai")
        }

        wsManager.sendRaw(json)

        _isAiRunning.value = false
        addMessage(ChatMessage(role = ChatMessage.Role.ERROR, text = "Task stopped by user"))
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun addMessage(message: ChatMessage) {
        _chatMessages.value += message
    }

    override fun onCleared() {
        super.onCleared()
        wsManager.disconnect()
    }
}