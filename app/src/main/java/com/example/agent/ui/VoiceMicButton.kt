// app/src/main/java/com/example/agent/ui/VoiceMicButton.kt
package com.example.agent.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun VoiceMicButton(
    onResult:  (String) -> Unit,
    isRunning: Boolean = false,
    modifier:  Modifier = Modifier
) {
    val context     = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Permission launcher
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) isListening = true
    }

    // SpeechRecognizer — created once, destroyed on disposal
    val recognizer = remember {
        SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(bundle: Bundle) {
                    val results = bundle.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )
                    val transcript = results?.firstOrNull()?.trim() ?: ""
                    isListening = false
                    if (transcript.isNotBlank()) onResult(transcript)
                }

                override fun onError(error: Int) {
                    isListening = false
                    // Silently ignore — user can tap again
                }

                override fun onEndOfSpeech()          { isListening = false }
                override fun onReadyForSpeech(p: Bundle?) = Unit
                override fun onBeginningOfSpeech()    = Unit
                override fun onRmsChanged(v: Float)   = Unit
                override fun onBufferReceived(b: ByteArray?) = Unit
                override fun onPartialResults(p: Bundle?) = Unit
                override fun onEvent(t: Int, p: Bundle?) = Unit
            })
        }
    }

    DisposableEffect(Unit) {
        onDispose { recognizer.destroy() }
    }

    // Intent for speech recognition
    val intent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
    }

    // Animated scale pulse when listening
    val iconScale by animateFloatAsState(
        targetValue = if (isListening) 1.2f else 1f,
        animationSpec = tween(150),
        label = "mic_scale"
    )

    val iconColor by animateColorAsState(
        targetValue = when {
            isListening -> MaterialTheme.colorScheme.error
            isRunning   -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            else        -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "mic_color"
    )

    IconButton(
        onClick = {
            when {
                isRunning -> { /* AI is running — ignore mic tap */ }

                isListening -> {
                    // Stop listening
                    recognizer.stopListening()
                    isListening = false
                }

                !hasPermission -> {
                    // Request RECORD_AUDIO permission
                    permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }

                else -> {
                    // Start listening
                    isListening = true
                    recognizer.startListening(intent)
                }
            }
        },
        modifier = modifier,
        enabled  = !isRunning || isListening
    ) {
        Icon(
            imageVector        = if (isListening) Icons.Default.MicOff
                                 else             Icons.Default.Mic,
            contentDescription = if (isListening) "Stop recording"
                                 else             "Start voice input",
            tint               = iconColor,
            modifier           = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
        )
    }
}
