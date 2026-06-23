package com.sovexis.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// ═══════════════ 基础主题色（深空青，新默认） ═══════════════
val SovexisPrimary = Color(0xFF00897B)
val SovexisPrimaryDark = Color(0xFF00594D)
val SovexisPrimaryLight = Color(0xFF4DB6AC)
val SovexisSecondary = Color(0xFFFF8F00)
val SovexisBackground = Color(0xFF0A1618)
val SovexisSurface = Color(0xFF182A2D)
val SovexisSurfaceVariant = Color(0xFF1C2E30)
val SovexisTextPrimary = Color(0xFFD0EDEE)
val SovexisTextSecondary = Color(0xFF90B8BA)
val SovexisTextHint = Color(0xFF5C7A7C)
val SovexisSuccess = Color(0xFF34A853)
val SovexisWarning = Color(0xFFFBBC04)
val SovexisError = Color(0xFFEA4335)

// ═══════════════ 抽屉专用色（旧硬编码兼容） ═══════════════
val SovexisDrawerBackground = Color(0xFF0A1A24)
val SovexisDrawerSurface = Color(0xFF142530)
val SovexisDrawerText = Color(0xFFE6EDF3)
val SovexisDrawerTextSecondary = Color(0xFF8AA0B0)
val SovexisDrawerActive = Color(0xFF26A69A)

// ═══════════════ 抽屉配色方案（6 套，已删除海蓝） ═══════════════
data class DrawerPalette(
    val background: Color,
    val surface: Color,
    val text: Color,
    val textSecondary: Color,
    val active: Color
)

val DrawerPalettes = listOf(
    // 0 深空青（深色抽屉）
    DrawerPalette(Color(0xFF0A1A24), Color(0xFF142530), Color(0xFFE6EDF3), Color(0xFF8AA0B0), Color(0xFF26A69A)),
    // 1 紫罗灰（深色抽屉）
    DrawerPalette(Color(0xFF16111B), Color(0xFF221B2A), Color(0xFFE8DDF0), Color(0xFF9888B0), Color(0xFF8E6FC4)),
    // 2 晨光金（浅色抽屉 — 新配色，替换原海蓝）
    DrawerPalette(Color(0xFFF5F3EF), Color(0xFFE8E4DC), Color(0xFF2A2218), Color(0xFF6B5D48), Color(0xFFD4A017)),
    // 3 暗夜金（深色抽屉）
    DrawerPalette(Color(0xFF1A1610), Color(0xFF282216), Color(0xFFF0E8D0), Color(0xFFA89870), Color(0xFFD4A017)),
    // 4 森林绿（浅色抽屉）
    DrawerPalette(Color(0xFFF0F6F0), Color(0xFFDCE8DC), Color(0xFF1A2A1C), Color(0xFF5A7A5C), Color(0xFF4CAF50)),
    // 5 暖橙棕（浅色抽屉）
    DrawerPalette(Color(0xFFF8F2EE), Color(0xFFE8DCD4), Color(0xFF2A1A10), Color(0xFF7A6050), Color(0xFFE8833A)),
    // 6 极昼白（浅色抽屉）
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

// ═══════════════ 主题预设方案（6 套，已删除海蓝） ═══════════════
data class ThemePreset(
    val name: String,
    val primary: Color,
    val primaryDark: Color,
    val primaryLight: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val onBackground: Color,
    val onSurface: Color,
    /** true = 深色界面背景 */
    val darkBackground: Boolean
)

val ThemePresets = listOf(
    // 0 深空青 — 深青色底面，暖橙点缀，沉稳高级 · 深色背景
    ThemePreset("深空青", Color(0xFF00897B), Color(0xFF00594D), Color(0xFF4DB6AC),
        Color(0xFFFF8F00), Color(0xFF0A1618), Color(0xFF182A2D),
        Color(0xFFD0EDEE), Color(0xFF90B8BA), darkBackground = true),
    // 1 紫罗灰 — 灰紫底面，冰蓝点缀，冷峻优雅 · 深色背景
    ThemePreset("紫罗灰", Color(0xFF8E6FC4), Color(0xFF5E3F9A), Color(0xFFB39DDB),
        Color(0xFF4DD0E1), Color(0xFF0F0C16), Color(0xFF1B1724),
        Color(0xFFD8CFE8), Color(0xFF9A8EBA), darkBackground = true),
    // 2 晨光金 — 暖金底色，翡翠绿点缀，温润典雅 · 浅色背景（新配色，替代原海蓝）
    ThemePreset("晨光金", Color(0xFFD4A017), Color(0xFF8B6914), Color(0xFFF0C050),
        Color(0xFF00897B), Color(0xFFF6F4EF), Color(0xFFEBE7DE),
        Color(0xFF2A2218), Color(0xFF5F5240), darkBackground = false),
    // 3 暗夜金 — 深褐底面，琥珀金点缀，温暖尊贵 · 深色背景
    ThemePreset("暗夜金", Color(0xFFD4A017), Color(0xFF8B6914), Color(0xFFF0C050),
        Color(0xFF00BFA5), Color(0xFF12100E), Color(0xFF1E1A14),
        Color(0xFFE8E0CC), Color(0xFFA89A70), darkBackground = true),
    // 4 森林绿 — 深绿底面，暖黄点缀，自然舒适 · 浅色背景
    ThemePreset("森林绿", Color(0xFF43A047), Color(0xFF2E7D32), Color(0xFF81C784),
        Color(0xFFFFCA28), Color(0xFFF0F6F0), Color(0xFFDCE8DC),
        Color(0xFF1A2A1C), Color(0xFF5A7A5C), darkBackground = false),
    // 5 暖橙棕 — 暖橙底面，青蓝点缀，活力温暖 · 浅色背景
    ThemePreset("暖橙棕", Color(0xFFE8833A), Color(0xFFB45A24), Color(0xFFF0A870),
        Color(0xFF26A69A), Color(0xFFF8F2EE), Color(0xFFE8DCD4),
        Color(0xFF2A1A10), Color(0xFF7A6050), darkBackground = false),
    // 6 极昼白 — 纯亮白底面，海蓝点缀，极简明快 · 浅色背景
    ThemePreset("极昼白", Color(0xFF1565C0), Color(0xFF0D3B78), Color(0xFF64B5F6),
        Color(0xFF00897B), Color(0xFFF8FAFC), Color(0xFFEEF1F5),
        Color(0xFF0F172A), Color(0xFF334155), darkBackground = false),
)

val DefaultPreset = 0 // 深空青

/** 全局响应式主题索引 — 任意位置修改后，SovexisTheme 自动重组 */
var themePresetIndex by mutableIntStateOf(DefaultPreset)
