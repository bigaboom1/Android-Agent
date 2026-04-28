// app/src/main/java/com/example/agent/WebSocketManager.kt

package com.example.agent

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.agent.model.ConnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebSocketManager(
    private val onFrame:   (Bitmap) -> Unit,
    private val onMessage: (JSONObject) -> Unit,
    private val onState:   (ConnState) -> Unit,
    private var deviceId: String = ""
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)  // Увеличил timeout
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)  // Добавил автоматический retry
        .build()

    @Volatile
    private var ws: WebSocket? = null
    private var retryDelay   = 1000L
    @Volatile
    private var shouldRetry = true
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null

    fun connect(serverUrl: String, token: String, deviceId: String) {
        Log.d("WS_MANAGER", "========== CONNECT START ==========")
        Log.d("WS_MANAGER", "Server URL: $serverUrl")
        Log.d("WS_MANAGER", "Token: ${token.take(20)}...")
        Log.d("WS_MANAGER", "Device ID: $deviceId")

        disconnect()

        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        this.deviceId = deviceId
        shouldRetry = true
        retryDelay = 1000L

        doConnect(serverUrl, token)
    }

    private fun doConnect(url: String, token: String) {
        ws?.cancel()
        ws = null

        onState(ConnState.CONNECTING)

        // Формируем URL с параметрами
        val fullUrl = if (url.contains("?")) {
            "$url&token=$token&deviceId=$deviceId"
        } else {
            "$url/phone?token=$token&deviceId=$deviceId"
        }

        Log.d("WS_MANAGER", "Full WebSocket URL: $fullUrl")

        val request = Request.Builder()
            .url(fullUrl)
            .addHeader("Authorization", "Bearer $token")  // Добавил header
            .build()

        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, r: Response) {
                Log.d("WS_MANAGER", "✅ WebSocket OPENED successfully!")
                Log.d("WS_MANAGER", "Response: ${r.code} ${r.message}")
                retryDelay = 1000L
                onState(ConnState.CONNECTED)
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                Log.d("WS_MANAGER", "Received binary: ${bytes.size} bytes")
                val arr = bytes.toByteArray()
                BitmapFactory.decodeByteArray(arr, 0, arr.size)
                    ?.let { onFrame(it) }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                Log.d("WS_MANAGER", "Received text: $text")
                try {
                    onMessage(JSONObject(text))
                } catch (e: Exception) {
                    Log.e("WS_MANAGER", "Parse error: ${e.message}", e)
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, r: Response?) {
                Log.e("WS_MANAGER", "❌ WebSocket FAILURE", t)
                Log.e("WS_MANAGER", "Error: ${t.message}")
                Log.e("WS_MANAGER", "Response: $r")
                Log.e("WS_MANAGER", "Response code: ${r?.code}")
                Log.e("WS_MANAGER", "Response message: ${r?.message}")

                onState(ConnState.RECONNECTING)

                if (!shouldRetry) return
                if (reconnectJob?.isActive == true) return

                reconnectJob = scope.launch {
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(30_000L)
                    if (!shouldRetry) return@launch
                    Log.d("WS_MANAGER", "🔄 Reconnecting... (delay=${retryDelay}ms)")
                    doConnect(url, token)
                }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d("WS_MANAGER", "WebSocket CLOSED: code=$code, reason=$reason")
                onState(ConnState.DISCONNECTED)
            }
        })
    }

    fun sendAction(action: JSONObject): Boolean {
        val wrapped = JSONObject()
        wrapped.put("type", "action")
        wrapped.put("action", action)

        Log.d("WS_DEBUG", "Sending action: $wrapped")
        Log.d("WS_DEBUG", "WS is null? ${ws == null}")

        return ws?.send(wrapped.toString()) ?: false
    }

    fun sendRaw(json: JSONObject): Boolean {
        if (ws == null) {
            Log.e("WS", "❌ Cannot send - WebSocket is null")
            return false
        }
        Log.d("WS", "Sending raw: $json")
        return ws?.send(json.toString()) ?: false
    }

    fun disconnect() {
        Log.d("WS_MANAGER", "Disconnecting...")
        shouldRetry = false
        scope.cancel()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        ws?.close(1000, "User disconnected")
        ws = null

        onState(ConnState.DISCONNECTED)
    }
}