package com.example.agent

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    private var ws:          WebSocket? = null
    private var retryDelay   = 1000L       // ms, doubles on each failure
    private var shouldRetry  = true
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect(url: String, token: String, deviceId: String){
        shouldRetry = true
        retryDelay  = 1000L
        this.deviceId = deviceId   // 👈 ADD THIS
        doConnect(url, token)
    }

    private fun doConnect(url: String, token: String) {
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
                try { onMessage(JSONObject(text)) }
                catch (e: Exception) { /* malformed JSON — ignore */ }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, r: Response?) {
                onState(ConnState.RECONNECTING)
                if (shouldRetry) {
                    scope.launch {
                        delay(retryDelay)
                        retryDelay = (retryDelay * 2).coerceAtMost(30_000L)
                        doConnect(url, token)
                    }
                }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                onState(ConnState.DISCONNECTED)
            }
        })
    }

    fun send(text: String): Boolean = ws?.send(text) ?: false

    fun disconnect() {
        shouldRetry = false
        ws?.close(1000, "User logout")
        scope.cancel()
    }
}

enum class ConnState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }