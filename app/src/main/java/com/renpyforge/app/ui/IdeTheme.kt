package com.renpyforge.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * IDE 深色主题（Darcula 风格）。
 * 背景分层：appBg(最外) > panelBg(面板/侧栏) > editorBg(编辑器) > consoleBg(控制台)
 */
object IdeColors {
    val AppBg = Color(0xFF1B1D22)
    val PanelBg = Color(0xFF22252B)
    val PanelBorder = Color(0xFF2E3239)
    val EditorBg = Color(0xFF191B1F)
    val ConsoleBg = Color(0xFF14161A)
    val ConsoleFg = Color(0xFFB4BBC6)
    val Accent = Color(0xFF548AF7)
    val AccentDim = Color(0xFF2D4A73)
    val OkGreen = Color(0xFF57965C)
    val WarnOrange = Color(0xFFE8A33D)
    val FailRed = Color(0xFFE55765)
    val TextFg = Color(0xFFDFE1E5)
    val TextDim = Color(0xFF8C929B)
    val SelectedBg = Color(0xFF2E4364)
    val HoverBg = Color(0xFF2A2D33)
    val TabActive = Color(0xFF2B2F36)

    // .rpy 语法高亮
    object Syntax {
        val Keyword = Color(0xFFCF8E6D)     // label / screen / define / init ...
        val Directive = Color(0xFFC77DBB)   // menu / jump / call / return ...
        val StringLit = Color(0xFF6AAB73)
        val Comment = Color(0xFF7A7E85)
        val NumberLit = Color(0xFF2AACB8)
        val LabelDef = Color(0xFF57AAF7)    // 标签名/变量定义名
        val Tag = Color(0xFFE8A33D)         // {tags} 与 [interpolations]
        val Plain = Color(0xFFBCBEC4)
    }
}

private val IdeScheme = darkColorScheme(
    primary = IdeColors.Accent,
    onPrimary = Color.White,
    primaryContainer = IdeColors.AccentDim,
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = IdeColors.WarnOrange,
    onSecondary = Color(0xFF2B1A05),
    secondaryContainer = Color(0xFF5A4423),
    onSecondaryContainer = Color(0xFFFFE3B8),
    background = IdeColors.AppBg,
    onBackground = IdeColors.TextFg,
    surface = IdeColors.PanelBg,
    onSurface = IdeColors.TextFg,
    surfaceVariant = IdeColors.HoverBg,
    onSurfaceVariant = IdeColors.TextDim,
    outline = IdeColors.PanelBorder,
    error = IdeColors.FailRed,
    errorContainer = Color(0xFF5C2A30),
    onErrorContainer = Color(0xFFFFC9CD),
)

@Composable
fun IdeTheme(content: @Composable () -> Unit) {
    // 本应用为纯深色 IDE 风格，不跟随系统
    MaterialTheme(colorScheme = IdeScheme, content = content)
}
