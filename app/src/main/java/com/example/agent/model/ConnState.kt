// app/src/main/java/com/example/agent/model/ConnState.kt
package com.example.agent.model

enum class ConnState {
    /** No connection — initial state or after clean logout */
    DISCONNECTED,

    /** Actively trying to establish the WebSocket connection */
    CONNECTING,

    /** WebSocket is open and agent is online */
    CONNECTED,

    /** Connection was lost, waiting before retry (exponential backoff) */
    RECONNECTING;

    val isOnline: Boolean
        get() = this == CONNECTED

    val label: String
        get() = when (this) {
            DISCONNECTED  -> "Disconnected"
            CONNECTING    -> "Connecting..."
            CONNECTED     -> "Connected"
            RECONNECTING  -> "Reconnecting..."
        }
}
