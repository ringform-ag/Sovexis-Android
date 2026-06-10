package com.sovexis.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// ═══════════════ 基础主题色（默认海蓝） ═══════════════
val SovexisPrimary = Color(0xFF1565C0)
val SovexisPrimaryDark = Color(0xFF0D3B78)
val SovexisPrimaryLight = Color(0xFF42A5F5)
val SovexisSecondary = Color(0xFF00897B)
val SovexisSecondaryDark = Color(0xFF00695C)
val SovexisBackground = Color(0xFFF0F4F8)
val SovexisSurface = Color(0xFFFFFFFF)
val SovexisSurfaceVariant = Color(0xFFE4E9F0)
val SovexisTextPrimary = Color(0xFF1A1C1E)
val SovexisTextSecondary = Color(0xFF5F6368)
val SovexisTextHint = Color(0xFF9AA0A6)
val SovexisSuccess = Color(0xFF34A853)
val SovexisWarning = Color(0xFFFBBC04)
val SovexisError = Color(0xFFEA4335)

// ═══════════════ 抽屉专用色（旧硬编码兼容） ═══════════════
val SovexisDrawerBackground = Color(0xFF151E2B)
val SovexisDrawerSurface = Color(0xFF1E2A3A)
val SovexisDrawerText = Color(0xFFE8EDF2)
val SovexisDrawerTextSecondary = Color(0xFF9AA0A6)
val SovexisDrawerActive = Color(0xFF42A5F5)

// ═══════════════ 抽屉配色方案（7 套，索引对齐 ThemePresets） ═══════════════
data class DrawerPalette(
    val background: Color,
    val surface: Color,
    val text: Color,
    val textSecondary: Color,
    val active: Color
)

val DrawerPalettes = listOf(
    // 0 深空青
    DrawerPalette(Color(0xFF0A1A24), Color(0xFF142530), Color(0xFFE6EDF3), Color(0xFF8AA0B0), Color(0xFF26A69A)),
    // 1 紫罗灰
    DrawerPalette(Color(0xFF16111B), Color(0xFF221B2A), Color(0xFFE8DDF0), Color(0xFF9888B0), Color(0xFF8E6FC4)),
    // 2 海蓝（默认）
    DrawerPalette(Color(0xFF151E2B), Color(0xFF1E2A3A), Color(0xFFE3ECF6), Color(0xFF8CA0B8), Color(0xFF42A5F5)),
    // 3 暗夜金
    DrawerPalette(Color(0xFF1A1610), Color(0xFF282216), Color(0xFFF0E8D0), Color(0xFFA89870), Color(0xFFD4A017)),
    // 4 森林绿
    DrawerPalette(Color(0xFF0E1C12), Color(0xFF162A1A), Color(0xFFD0E8D0), Color(0xFF80A880), Color(0xFF4CAF50)),
    // 5 暖橙棕
    DrawerPalette(Color(0xFF1A1210), Color(0xFF2A1C18), Color(0xFFF0D8CC), Color(0xFFB09080), Color(0xFFE8833A)),
    // 6 极昼白
    DrawerPalette(Color(0xFFF5F7FA), Color(0xFFE4E9F0), Color(0xFF1A1C1E), Color(0xFF6B7280), Color(0xFF1565C0)),
)

// ═══════════════ 身份卡片配色 ═══════════════
val CardMasterGold = Color(0xFFD4AF37)
val CardMasterDark = Color(0xFF1A1A1A)
val CardMasterAccent = Color(0xFFFFD700)
val CardStewardGreen = Color(0xFF2E7D32)
val CardStewardLight = Color(0xFF4CAF50)
val CardDefaultBg = Color(0xFF37474F)
val CardDefaultAccent = Color(0xFF78909C)

// ═══════════════ 主题预设方案（7 套） ═══════════════
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
    // 0 深空青 — 深青色底面，暖橙点缀，沉稳高级
    ThemePreset("深空青", Color(0xFF00897B), Color(0xFF00594D), Color(0xFF4DB6AC),
        Color(0xFFFF8F00), Color(0xFF0A1618), Color(0xFF182A2D),
        Color(0xFFD0EDEE), Color(0xFF90B8BA)),
    // 1 紫罗灰 — 灰紫底面，冰蓝点缀，冷峻优雅
    ThemePreset("紫罗灰", Color(0xFF8E6FC4), Color(0xFF5E3F9A), Color(0xFFB39DDB),
        Color(0xFF4DD0E1), Color(0xFF0F0C16), Color(0xFF1B1724),
        Color(0xFFD8CFE8), Color(0xFF9A8EBA)),
    // 2 海蓝 — 钴蓝色底面，翡翠绿点缀，明亮专业（默认）
    ThemePreset("海蓝", Color(0xFF1565C0), Color(0xFF0D3B78), Color(0xFF42A5F5),
        Color(0xFF00897B), Color(0xFFF0F4F8), Color(0xFFFFFFFF),
        Color(0xFF1A1C1E), Color(0xFF5F6368)),
    // 3 暗夜金 — 深褐底面，琥珀金点缀，温暖尊贵
    ThemePreset("暗夜金", Color(0xFFD4A017), Color(0xFF8B6914), Color(0xFFF0C050),
        Color(0xFF00BFA5), Color(0xFF12100E), Color(0xFF1E1A14),
        Color(0xFFE8E0CC), Color(0xFFA89A70)),
    // 4 森林绿 — 深绿底面，暖黄点缀，自然舒适
    ThemePreset("森林绿", Color(0xFF43A047), Color(0xFF2E7D32), Color(0xFF81C784),
        Color(0xFFFFCA28), Color(0xFF0C1A0E), Color(0xFF192A1C),
        Color(0xFFD4EAD4), Color(0xFF90B890)),
    // 5 暖橙棕 — 暖橙底面，青蓝点缀，活力温暖
    ThemePreset("暖橙棕", Color(0xFFE8833A), Color(0xFFB45A24), Color(0xFFF0A870),
        Color(0xFF26A69A), Color(0xFF100B08), Color(0xFF1C1610),
        Color(0xFFF0DAC8), Color(0xFFC0A088)),
    // 6 极昼白 — 纯亮白底面，海蓝点缀，极简明快
    ThemePreset("极昼白", Color(0xFF1565C0), Color(0xFF0D3B78), Color(0xFF64B5F6),
        Color(0xFF00897B), Color(0xFFF8FAFC), Color(0xFFEEF1F5),
        Color(0xFF0F172A), Color(0xFF334155)),
)

val DefaultPreset = 2 // 海蓝

/** 全局响应式主题索引 — 任意位置修改后，SovexisTheme 自动重组 */
var themePresetIndex by mutableIntStateOf(DefaultPreset)
