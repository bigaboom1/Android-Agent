// app/src/main/java/com/example/agent/model/ChatMessage.kt
package com.example.agent.model

import java.util.UUID

data class ChatMessage(
    val id:        String = UUID.randomUUID().toString(),
    val role:      Role,
    val text:      String,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class Role {
        USER,       // message typed/spoken by the user
        ASSISTANT,  // status/reasoning text from the AI
        ACTION,     // action being executed (click, type, etc.)
        DONE,       // task completion summary
        ERROR       // error from server or AI
    }
}