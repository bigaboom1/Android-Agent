// app/src/main/java/com/example/agent/ui/RemoteScreen.kt
package com.example.agent.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.agent.RemoteViewModel
import com.example.agent.model.ConnState

@Composable
fun RemoteScreen(
    onOpenChat:     () -> Unit,
    onOpenSettings: () -> Unit,
    vm: RemoteViewModel = viewModel()
) {
    val frame       by vm.frameBitmap.collectAsState()
    val connState   by vm.connectionState.collectAsState()
    val agentOnline by vm.agentOnline.collectAsState()
    val isAiRunning by vm.isAiRunning.collectAsState()

    // Local zoom/pan state — does not affect server
    var scale  by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Track where the image is actually drawn (letterbox rect)
    var _lastImageRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Live stream canvas ─────────────────────────────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale  = (scale * zoom).coerceIn(1f, 4f)
                        offset += pan
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            // Convert tap to image-relative coords accounting for letterbox
                            val (relX, relY) = tapToRelative(tapOffset, size, _lastImageRect)
                            if (relX != null && relY != null) vm.sendTap(relX, relY)
                        },
                        onDoubleTap = { tapOffset ->
                            val (relX, relY) = tapToRelative(tapOffset, size, _lastImageRect)
                            if (relX != null && relY != null) vm.sendDoubleTap(relX, relY)
                        },
                        onLongPress = { tapOffset ->
                            val (relX, relY) = tapToRelative(tapOffset, size, _lastImageRect)
                            if (relX != null && relY != null) vm.sendRightClick(relX, relY)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures { _, dragAmount ->
                        val direction = if (dragAmount.y > 0) "down" else "up"
                        vm.sendScroll(0.5f, 0.5f, direction)
                    }
                }
        ) {
            // Draw the latest frame letterboxed (preserving aspect ratio)
            frame?.let { bmp ->
                val bmpW = bmp.width.toFloat()
                val bmpH = bmp.height.toFloat()
                val canvasW = size.width.toFloat()
                val canvasH = size.height.toFloat()

                // Calculate letterbox rect
                val bmpAspect    = bmpW / bmpH
                val canvasAspect = canvasW / canvasH

                val drawW: Float
                val drawH: Float
                if (bmpAspect > canvasAspect) {
                    // Image wider than canvas — fit width, bars on top/bottom
                    drawW = canvasW
                    drawH = canvasW / bmpAspect
                } else {
                    // Image taller than canvas — fit height, bars on left/right
                    drawH = canvasH
                    drawW = canvasH * bmpAspect
                }

                val left = (canvasW - drawW) / 2f
                val top  = (canvasH - drawH) / 2f

                // Store rect so tap handler can use it
                _lastImageRect = androidx.compose.ui.geometry.Rect(left, top, left + drawW, top + drawH)

                withTransform({
                    scale(scale, scale, pivot = Offset(canvasW / 2f, canvasH / 2f))
                    translate(offset.x, offset.y)
                }) {
                    drawImage(
                        image    = bmp.asImageBitmap(),
                        srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                        srcSize   = androidx.compose.ui.unit.IntSize(bmp.width, bmp.height),
                        dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
                        dstSize   = androidx.compose.ui.unit.IntSize(drawW.toInt(), drawH.toInt())
                    )
                }
            } ?: run {
                drawRect(color = Color(0xFF0F1416))
            }
        }

        // ── Connection status badge (top-left) ─────────────────────────────
        ConnStatusBadge(
            connState   = connState,
            agentOnline = agentOnline,
            modifier    = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp)
        )

        // ── AI running indicator (top-center) ─────────────────────────────
        if (isAiRunning) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 12.dp),
                shape  = RoundedCornerShape(20.dp),
                color  = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color       = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text     = "AI working...",
                        fontSize = 13.sp,
                        color    = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // ── Top-right action buttons ───────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallFloatingActionButton(
                onClick            = onOpenChat,
                containerColor     = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                contentColor       = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Open AI chat")
            }
            SmallFloatingActionButton(
                onClick            = onOpenSettings,
                containerColor     = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                contentColor       = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }

        // ── Bottom shortcut bar ────────────────────────────────────────────
        ShortcutBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
            onShortcut = { keys -> vm.sendShortcut(keys) }
        )
    }
}

// ── Connection status badge ────────────────────────────────────────────────
@Composable
fun ConnStatusBadge(
    connState:   ConnState,
    agentOnline: Boolean,
    modifier:    Modifier = Modifier
) {
    val (dotColor, label) = when (connState) {
        ConnState.CONNECTED if agentOnline ->
            Color(0xFF4CAF50) to "Agent online"
        ConnState.CONNECTED ->
            Color(0xFFFFA726) to "Waiting for agent"
        ConnState.CONNECTING, ConnState.RECONNECTING ->
            Color(0xFFFFA726) to connState.label
        else -> Color(0xFFF44336) to "Disconnected"
    }

    Surface(
        modifier  = modifier,
        shape     = RoundedCornerShape(20.dp),
        color     = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape)
            )
            Text(text = label, fontSize = 12.sp)
        }
    }
}

// ── Tap coordinate helper ──────────────────────────────────────────────────
/**
 * Converts a tap position on the Canvas to relative PC coordinates (0..1).
 * Returns null if the tap was outside the image (in the letterbox bars).
 */
private fun tapToRelative(
    tap:       Offset,
    canvasSize: androidx.compose.ui.unit.IntSize,
    imageRect: androidx.compose.ui.geometry.Rect
): Pair<Float?, Float?> {
    if (imageRect == androidx.compose.ui.geometry.Rect.Zero) {
        // Fallback before first frame — use full canvas
        return Pair(
            (tap.x / canvasSize.width).coerceIn(0f, 1f),
            (tap.y / canvasSize.height).coerceIn(0f, 1f)
        )
    }

    // Check if tap is inside the image rect
    if (!imageRect.contains(tap)) return Pair(null, null)

    val relX = ((tap.x - imageRect.left) / imageRect.width).coerceIn(0f, 1f)
    val relY = ((tap.y - imageRect.top)  / imageRect.height).coerceIn(0f, 1f)
    return Pair(relX, relY)
}
@Composable
fun ShortcutBar(
    modifier:    Modifier = Modifier,
    onShortcut:  (List<String>) -> Unit
) {
    val shortcuts = listOf(
        "Win"      to listOf("win"),
        "Alt+Tab"  to listOf("alt", "tab"),
        "Ctrl+C"   to listOf("ctrl", "c"),
        "Ctrl+V"   to listOf("ctrl", "v"),
        "Ctrl+Z"   to listOf("ctrl", "z"),
        "Esc"      to listOf("esc"),
    )

    Surface(
        modifier       = modifier.padding(horizontal = 12.dp),
        shape          = RoundedCornerShape(16.dp),
        color          = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            shortcuts.forEach { (label, keys) ->
                TextButton(
                    onClick      = { onShortcut(keys) },
                    modifier     = Modifier.defaultMinSize(minWidth = 1.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = label, fontSize = 12.sp)
                }
            }
        }
    }
}