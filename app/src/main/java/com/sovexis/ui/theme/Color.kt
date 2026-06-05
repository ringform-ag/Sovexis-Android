package com.sovexis.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// ═══════════════ 基础主题色 ═══════════════
val SovexisPrimary = Color(0xFF1A73E8)
val SovexisPrimaryDark = Color(0xFF0D47A1)
val SovexisPrimaryLight = Color(0xFF63A4FF)
val SovexisSecondary = Color(0xFF00BFA5)
val SovexisSecondaryDark = Color(0xFF008C7A)
val SovexisBackground = Color(0xFFF5F7FA)
val SovexisSurface = Color(0xFFFFFFFF)
val SovexisSurfaceVariant = Color(0xFFE8EDF2)
val SovexisTextPrimary = Color(0xFF1A1C1E)
val SovexisTextSecondary = Color(0xFF5F6368)
val SovexisTextHint = Color(0xFF9AA0A6)
val SovexisSuccess = Color(0xFF34A853)
val SovexisWarning = Color(0xFFFBBC04)
val SovexisError = Color(0xFFEA4335)

// ═══════════════ 抽屉专用色（旧硬编码 — 保留兼容） ═══════════════
val SovexisDrawerBackground = Color(0xFF1A2332)
val SovexisDrawerSurface = Color(0xFF243447)
val SovexisDrawerText = Color(0xFFE8EDF2)
val SovexisDrawerTextSecondary = Color(0xFF9AA0A6)
val SovexisDrawerActive = Color(0xFF1A73E8)

// ═══════════════ 抽屉配色方案（随主题预设联动） ═══════════════
data class DrawerPalette(
    val background: Color,
    val surface: Color,
    val text: Color,
    val textSecondary: Color,
    val active: Color
)

val DrawerPalettes = listOf(
    // 0 深空蓝
    DrawerPalette(Color(0xFF0A1A24), Color(0xFF142530), Color(0xFFE6EDF3), Color(0xFF8AA0B0), Color(0xFF006D77)),
    // 1 极光紫
    DrawerPalette(Color(0xFF120A1E), Color(0xFF1E1530), Color(0xFFE8DDFF), Color(0xFF9080B0), Color(0xFF7C4DFF)),
    // 2 钴蓝
    DrawerPalette(Color(0xFF1A2332), Color(0xFF243447), Color(0xFFE8EDF2), Color(0xFF9AA0A6), Color(0xFF1A73E8)),
    // 3 暗夜橙
    DrawerPalette(Color(0xFF1A1410), Color(0xFF2A2018), Color(0xFFE8EDF2), Color(0xFF9AA0A6), Color(0xFFFF6D00)),
    // 4 森林
    DrawerPalette(Color(0xFF0D1B0E), Color(0xFF152A16), Color(0xFFD0E8D0), Color(0xFF80A880), Color(0xFF2E7D32)),
    // 5 墨红
    DrawerPalette(Color(0xFF1A0808), Color(0xFF2A1010), Color(0xFFF5D5D5), Color(0xFFB08080), Color(0xFFB71C1C)),
    // 6 深空青
    DrawerPalette(Color(0xFF091011), Color(0xFF111C1F), Color(0xFFCEF0F0), Color(0xFF70A0A0), Color(0xFF006D77)),
)

// ═══════════════ 身份卡片配色 ═══════════════
val CardMasterGold = Color(0xFFD4AF37)        // 主账号 — 黑金底色
val CardMasterDark = Color(0xFF1A1A1A)        // 主账号卡面深色
val CardMasterAccent = Color(0xFFFFD700)      // 主账号金色点缀
val CardStewardGreen = Color(0xFF2E7D32)      // 管家 — 深绿底色
val CardStewardLight = Color(0xFF4CAF50)      // 管家亮色
val CardDefaultBg = Color(0xFF37474F)         // 其他账号 — 蓝灰
val CardDefaultAccent = Color(0xFF78909C)     // 其他账号点缀

// ═══════════════ 主题预设方案 ═══════════════
data class ThemePreset(
    val name: String,
    val primary: Color,
    val primaryDark: Color,
    val primaryLight: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val onBackground: Color,
    val onSurface: Color
)

val ThemePresets = listOf(
    ThemePreset("深空蓝", Color(0xFF006D77), Color(0xFF004D53), Color(0xFF49A8B3),
        Color(0xFFFF9F1C), Color(0xFF0B1319), Color(0xFF1A282F),
        Color(0xFFE6EDF3), Color(0xFFC5CDD5)),
    ThemePreset("极光紫", Color(0xFF7C4DFF), Color(0xFF5C2DC8), Color(0xFFB388FF),
        Color(0xFF00E5FF), Color(0xFF120A1E), Color(0xFF1E1530),
        Color(0xFFE8DDFF), Color(0xFFB0A0D0)),
    ThemePreset("钴蓝", Color(0xFF1A73E8), Color(0xFF0D47A1), Color(0xFF63A4FF),
        Color(0xFF00BFA5), Color(0xFFF5F7FA), Color(0xFFFFFFFF),
        Color(0xFF1A1C1E), Color(0xFF5F6368)),
    ThemePreset("暗夜橙", Color(0xFFFF6D00), Color(0xFFC43E00), Color(0xFFFF9E40),
        Color(0xFF00C853), Color(0xFF121212), Color(0xFF1E1E1E),
        Color(0xFFE8EDF2), Color(0xFF9AA0A6)),
    ThemePreset("森林", Color(0xFF2E7D32), Color(0xFF1B5E20), Color(0xFF66BB6A),
        Color(0xFFFFC107), Color(0xFF0D1B0E), Color(0xFF152A16),
        Color(0xFFD0E8D0), Color(0xFF90B890)),
    ThemePreset("墨红", Color(0xFFB71C1C), Color(0xFF7F0000), Color(0xFFE57373),
        Color(0xFFFFD600), Color(0xFF100505), Color(0xFF1C0D0D),
        Color(0xFFF5D5D5), Color(0xFFC09090)),
    ThemePreset("深空青", Color(0xFF006D77), Color(0xFF00363D), Color(0xFF4DB6AC),
        Color(0xFFFF8F00), Color(0xFF091011), Color(0xFF111C1F),
        Color(0xFFCEF0F0), Color(0xFF80B0B0))
)

val DefaultPreset = 2 // 钴蓝

/** 全局响应式主题索引 — 任意位置修改后，SovexisTheme 自动重组 */
var themePresetIndex by mutableIntStateOf(DefaultPreset)
