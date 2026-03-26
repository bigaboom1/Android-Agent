// app/src/main/java/com/example/agent/ui/theme/Theme.kt
package com.example.agent.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Colour palette ─────────────────────────────────────────────────────────
private val DarkColors = darkColorScheme(
    primary          = Color(0xFF82AAFF),
    onPrimary        = Color(0xFF003063),
    primaryContainer = Color(0xFF00458B),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary        = Color(0xFFB4C8E8),
    onSecondary      = Color(0xFF1E3147),
    background       = Color(0xFF0F1416),
    onBackground     = Color(0xFFDEE3E9),
    surface          = Color(0xFF0F1416),
    onSurface        = Color(0xFFDEE3E9),
    surfaceVariant   = Color(0xFF1E2832),
    error            = Color(0xFFFFB4AB),
    onError          = Color(0xFF690005),
)

private val LightColors = lightColorScheme(
    primary          = Color(0xFF0060AC),
    onPrimary        = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001C3A),
    secondary        = Color(0xFF536070),
    onSecondary      = Color(0xFFFFFFFF),
    background       = Color(0xFFF7F9FC),
    onBackground     = Color(0xFF181C1F),
    surface          = Color(0xFFF7F9FC),
    onSurface        = Color(0xFF181C1F),
    surfaceVariant   = Color(0xFFDDE3ED),
    error            = Color(0xFFBA1A1A),
    onError          = Color(0xFFFFFFFF),
)

@Composable
fun AgentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,   // Material You — Android 12+
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else           dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else      -> LightColors
    }

    // Make the status bar transparent and tint its icons to match theme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content     = content
    )
}
