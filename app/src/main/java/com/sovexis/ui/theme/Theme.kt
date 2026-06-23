package com.sovexis.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** 全局主题索引 — Composable 级状态，所有子组件可读取 */
val LocalThemePreset = compositionLocalOf { ThemePresets[DefaultPreset] }

@Composable
fun SovexisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val presetIndex = themePresetIndex
    val preset = ThemePresets.getOrElse(presetIndex) { ThemePresets[DefaultPreset] }

    val context = LocalContext.current

    // 根据预设的 darkBackground 决定是否强制深色 → darkTheme 参数覆盖
    val effectiveDark = preset.darkBackground

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (effectiveDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        effectiveDark -> darkColorScheme(
            primary = preset.primaryLight,
            onPrimary = preset.primaryDark,
            primaryContainer = preset.primaryDark,
            onPrimaryContainer = preset.primaryLight,
            secondary = preset.secondary,
            onSecondary = Color(0xFF00362D),
            secondaryContainer = Color(0xFF005040),
            onSecondaryContainer = preset.secondary,
            background = preset.background,
            onBackground = preset.onBackground,
            surface = preset.surface,
            onSurface = preset.onSurface,
            surfaceVariant = Color(0xFF2C2C2C),
            onSurfaceVariant = Color(0xFF9AA0A6),
            error = Color(0xFFCF6679),
            onError = Color(0xFF1A1C1E)
        )
        else -> lightColorScheme(
            primary = preset.primary,
            onPrimary = Color.White,
            primaryContainer = preset.primaryLight,
            onPrimaryContainer = preset.primaryDark,
            secondary = preset.secondary,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFA7F3D0),
            onSecondaryContainer = Color(0xFF003D33),
            background = preset.background,
            onBackground = preset.onBackground,
            surface = preset.surface,
            onSurface = preset.onSurface,
            surfaceVariant = Color(0xFFE8EDF2),
            onSurfaceVariant = Color(0xFF5F6368),
            error = Color(0xFFEA4335),
            onError = Color.White
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.let {
                WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = !effectiveDark
            }
        }
    }

    // 直接更新 MaterialTheme，不使用 Crossfade ——
    // Crossfade 会在主题切换时销毁并重建整个 composable 子树，
    // 导致 NavHost 被重置到 startDestination，出现"切换主题→跳转会話主页"的 bug。
    CompositionLocalProvider(LocalThemePreset provides preset) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
