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
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)    // no timeout for WS reads
        .build()

    @Volatile
    private var ws: WebSocket? = null
    private var retryDelay   = 1000L       // ms, doubles on each failure
    @Volatile
    private var shouldRetry = true
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null

    private fun newScope() =
        CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect(serverUrl: String, token: String, deviceId: String) {
        disconnect() // 🔥 important

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
        val request = Request.Builder()
            .url("$url/phone?token=$token&deviceId=$deviceId")
            .build()

        ws = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, r: Response) {
                retryDelay = 1000L   // reset backoff on success
                onState(ConnState.CONNECTED)
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                val arr = bytes.toByteArray()
                BitmapFactory.decodeByteArray(arr, 0, arr.size)
                    ?.let { onFrame(it) }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                Log.d("WS_MSG", text)
                try { onMessage(JSONObject(text)) }
                catch (e: Exception) { }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, r: Response?) {
                onState(ConnState.RECONNECTING)

                if (!shouldRetry) return

                if (reconnectJob?.isActive == true) return

                reconnectJob = scope.launch {
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(30_000L)
                    if (!shouldRetry) return@launch
                    doConnect(url, token)
                }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                onState(ConnState.DISCONNECTED)
            }
        })
    }

    fun sendAction(action: JSONObject): Boolean {
        val wrapped = JSONObject()
        wrapped.put("type", "action")
        wrapped.put("action", action)   // ← nest the action object, don't flatten it

        Log.d("WS_DEBUG", "sending: $wrapped")
        Log.d("WS_DEBUG", "WS is null? ${ws == null}")
        Log.d("WS_DEBUG", "WS? ${ws}")
        return ws?.send(wrapped.toString()) ?: false
    }

    fun sendRaw(json: JSONObject): Boolean {
        if (ws == null) {
            Log.e("WS", "❌ Tried to send but WS is null")
            return false
        }
        return ws?.send(json.toString()) ?: false
    }
    fun disconnect() {
        shouldRetry = false
        scope.cancel()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        ws?.close(1000, "User disconnected")
        ws = null

        onState(ConnState.DISCONNECTED)
    }

}

