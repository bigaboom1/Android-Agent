// app/src/main/java/com/example/agent/ui/ChatScreen.kt
package com.example.agent.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.agent.RemoteViewModel
import com.example.agent.model.ChatMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    vm: RemoteViewModel = viewModel()
) {
    val messages   by vm.chatMessages.collectAsState()
    val isRunning  by vm.isAiRunning.collectAsState()
    val listState   = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    var inputText  by remember { mutableStateOf("") }

    // Auto-scroll to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Chat") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back")
                    }
                },
                actions = {
                    if (isRunning) {
                        TextButton(
                            onClick = { vm.stopAiTask() },
                            colors  = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop",
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Stop")
                        }
                    }
                }
            )
        },
        bottomBar = {
            ChatInputBar(
                text        = inputText,
                isRunning   = isRunning,
                onTextChange = { inputText = it },
                onSend = {
                    val goal = inputText.trim()
                    if (goal.isNotBlank()) {
                        vm.sendGoal(goal)
                        inputText = ""
                        focusManager.clearFocus()
                    }
                },
                onVoiceResult = { transcript ->
                    if (transcript.isNotBlank()) {
                        vm.sendGoal(transcript)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state            = listState,
            modifier         = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding   = PaddingValues(vertical = 12.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                ChatBubble(message = message)
            }

            // Typing indicator while AI is working
            if (isRunning) {
                item {
                    TypingIndicator()
                }
            }
        }
    }
}

// ── Chat bubble ────────────────────────────────────────────────────────────
@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == ChatMessage.Role.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .animateContentSize(),
            shape = RoundedCornerShape(
                topStart    = if (isUser) 16.dp else 4.dp,
                topEnd      = if (isUser) 4.dp  else 16.dp,
                bottomStart = 16.dp,
                bottomEnd   = 16.dp
            ),
            color = when (message.role) {
                ChatMessage.Role.USER      -> MaterialTheme.colorScheme.primary
                ChatMessage.Role.ASSISTANT -> MaterialTheme.colorScheme.surfaceVariant
                ChatMessage.Role.ACTION    -> MaterialTheme.colorScheme.secondaryContainer
                ChatMessage.Role.DONE      -> MaterialTheme.colorScheme.tertiaryContainer
                ChatMessage.Role.ERROR     -> MaterialTheme.colorScheme.errorContainer
            },
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                // Role label for non-user messages
                if (!isUser) {
                    Text(
                        text = when (message.role) {
                            ChatMessage.Role.ASSISTANT -> "AI"
                            ChatMessage.Role.ACTION    -> "Action"
                            ChatMessage.Role.DONE      -> "Done"
                            ChatMessage.Role.ERROR     -> "Error"
                            else                       -> ""
                        },
                        fontSize = 10.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                Text(
                    text     = message.text,
                    fontSize = 14.sp,
                    color    = when (message.role) {
                        ChatMessage.Role.USER   -> MaterialTheme.colorScheme.onPrimary
                        ChatMessage.Role.ERROR  -> MaterialTheme.colorScheme.onErrorContainer
                        ChatMessage.Role.DONE   -> MaterialTheme.colorScheme.onTertiaryContainer
                        ChatMessage.Role.ACTION -> MaterialTheme.colorScheme.onSecondaryContainer
                        else                    -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontFamily = if (message.role == ChatMessage.Role.ACTION)
                        FontFamily.Monospace else FontFamily.Default
                )
            }
        }
    }
}

// ── Typing indicator ───────────────────────────────────────────────────────
@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp,
                bottomStart = 16.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(50)
                            )
                    )
                }
            }
        }
    }
}

// ── Chat input bar ─────────────────────────────────────────────────────────
@Composable
fun ChatInputBar(
    text:          String,
    isRunning:     Boolean,
    onTextChange:  (String) -> Unit,
    onSend:        () -> Unit,
    onVoiceResult: (String) -> Unit
) {
    Surface(
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Voice input button
            VoiceMicButton(
                onResult  = onVoiceResult,
                isRunning = isRunning
            )

            // Text input field
            OutlinedTextField(
                value         = text,
                onValueChange = onTextChange,
                modifier      = Modifier.weight(1f),
                placeholder   = { Text("Type a goal...", fontSize = 14.sp) },
                singleLine    = true,
                enabled       = !isRunning,
                shape         = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )

            // Send button
            IconButton(
                onClick  = onSend,
                enabled  = text.isNotBlank() && !isRunning
            ) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send goal",
                    tint = if (text.isNotBlank() && !isRunning)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }
    }
}