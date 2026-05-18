package com.sovexis.mobile.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = SovexisPrimary,
    onPrimary = Color.White,
    primaryContainer = SovexisPrimaryLight,
    onPrimaryContainer = SovexisPrimaryDark,
    secondary = SovexisSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFA7F3D0),
    onSecondaryContainer = SovexisSecondaryDark,
    background = SovexisBackground,
    onBackground = SovexisTextPrimary,
    surface = SovexisSurface,
    onSurface = SovexisTextPrimary,
    surfaceVariant = SovexisSurfaceVariant,
    onSurfaceVariant = SovexisTextSecondary,
    error = SovexisError,
    onError = Color.White,
    success = SovexisSuccess,
    warning = SovexisWarning
)

private val DarkColorScheme = darkColorScheme(
    primary = SovexisPrimaryLight,
    onPrimary = SovexisPrimaryDark,
    primaryContainer = SovexisPrimaryDark,
    onPrimaryContainer = SovexisPrimaryLight,
    secondary = SovexisSecondary,
    onSecondary = SovexisSecondaryDark,
    secondaryContainer = SovexisSecondaryDark,
    onSecondaryContainer = SovexisSecondary,
    background = Color(0xFF121212),
    onBackground = Color(0xFFE8EDF2),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE8EDF2),
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFF9AA0A6),
    error = Color(0xFFCF6679),
    onError = Color(0xFF1A1C1E),
    success = SovexisSuccess,
    warning = SovexisWarning
)

@Composable
fun SovexisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,  // Sovexis 浣跨敤鍥哄畾鍝佺墝鑹诧紝涓嶅惎鐢ㄥ姩鎬佸彇鑹?    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            window?.let {
                WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
