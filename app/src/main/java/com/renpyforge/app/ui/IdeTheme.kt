package com.renpyforge.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * 主题（浅色为主 / Material 3）：
 * · Android 12+ 使用动态取色（Material You），低版本回退到品牌蓝
 * · 编辑器 / 控制台配色随主题自动切换（浅色 / 深色双套语法色）
 */

@Immutable
data class SyntaxPalette(
    val keyword: Color,
    val directive: Color,
    val stringLit: Color,
    val comment: Color,
    val number: Color,
    val tag: Color,
)

@Immutable
data class IdePalette(
    val appBg: Color,
    val panelBg: Color,
    val panelBorder: Color,
    val editorBg: Color,
    val consoleBg: Color,
    val consoleFg: Color,
    val accent: Color,
    val accentDim: Color,
    val okGreen: Color,
    val warnOrange: Color,
    val failRed: Color,
    val textFg: Color,
    val textDim: Color,
    val selectedBg: Color,
    val hoverBg: Color,
    val tabActive: Color,
    val syntax: SyntaxPalette,
    val dark: Boolean,
)

val LocalPalette = staticCompositionLocalOf { lightPalette() }

// ───────────── 浅色（默认） ─────────────

fun lightPalette(accent: Color = Color(0xFF3F63D6)): IdePalette = IdePalette(
    appBg = Color(0xFFF5F6F8),
    panelBg = Color(0xFFFFFFFF),
    panelBorder = Color(0xFFE1E4E9),
    editorBg = Color(0xFFFCFCFD),
    consoleBg = Color(0xFFF0F2F5),
    consoleFg = Color(0xFF3C414B),
    accent = accent,
    accentDim = accent.copy(alpha = 0.14f),
    okGreen = Color(0xFF1E8E3E),
    warnOrange = Color(0xFFB26A00),
    failRed = Color(0xFFD93025),
    textFg = Color(0xFF1B1D22),
    textDim = Color(0xFF5C6270),
    selectedBg = accent.copy(alpha = 0.14f),
    hoverBg = Color(0xFFEDF0F4),
    tabActive = Color(0xFFFFFFFF),
    syntax = SyntaxPalette(
        keyword = Color(0xFF7A3E9D),
        directive = Color(0xFF0B57D0),
        stringLit = Color(0xFF137333),
        comment = Color(0xFF5F6368),
        number = Color(0xFFA8480A),
        tag = Color(0xFF953800),
    ),
    dark = false,
)

// ───────────── 深色（跟随系统时使用） ─────────────

fun darkPalette(accent: Color = Color(0xFFAFC6FF)): IdePalette = IdePalette(
    appBg = Color(0xFF14161A),
    panelBg = Color(0xFF1E2127),
    panelBorder = Color(0xFF2E3239),
    editorBg = Color(0xFF191B1F),
    consoleBg = Color(0xFF14161A),
    consoleFg = Color(0xFFB4BBC6),
    accent = accent,
    accentDim = Color(0xFF2D4A73),
    okGreen = Color(0xFF57965C),
    warnOrange = Color(0xFFE8A33D),
    failRed = Color(0xFFE55765),
    textFg = Color(0xFFDFE1E5),
    textDim = Color(0xFF8C929B),
    selectedBg = Color(0xFF2E4364),
    hoverBg = Color(0xFF2A2D33),
    tabActive = Color(0xFF2B2F36),
    syntax = SyntaxPalette(
        keyword = Color(0xFFCF8E6D),
        directive = Color(0xFFC77DBB),
        stringLit = Color(0xFF6AAB73),
        comment = Color(0xFF7A7E85),
        number = Color(0xFF2AACB8),
        tag = Color(0xFFE8A33D),
    ),
    dark = true,
)

// ───────────── 兼容层：既有代码继续用 IdeColors.X ─────────────

object IdeColors {
    val AppBg: Color @Composable get() = LocalPalette.current.appBg
    val PanelBg: Color @Composable get() = LocalPalette.current.panelBg
    val PanelBorder: Color @Composable get() = LocalPalette.current.panelBorder
    val EditorBg: Color @Composable get() = LocalPalette.current.editorBg
    val ConsoleBg: Color @Composable get() = LocalPalette.current.consoleBg
    val ConsoleFg: Color @Composable get() = LocalPalette.current.consoleFg
    val Accent: Color @Composable get() = LocalPalette.current.accent
    val AccentDim: Color @Composable get() = LocalPalette.current.accentDim
    val OkGreen: Color @Composable get() = LocalPalette.current.okGreen
    val WarnOrange: Color @Composable get() = LocalPalette.current.warnOrange
    val FailRed: Color @Composable get() = LocalPalette.current.failRed
    val TextFg: Color @Composable get() = LocalPalette.current.textFg
    val TextDim: Color @Composable get() = LocalPalette.current.textDim
    val SelectedBg: Color @Composable get() = LocalPalette.current.selectedBg
    val HoverBg: Color @Composable get() = LocalPalette.current.hoverBg
    val TabActive: Color @Composable get() = LocalPalette.current.tabActive
}

// ───────────── 主题入口 ─────────────

@Composable
fun IdeTheme(content: @Composable () -> Unit) {
    val useDark = isSystemInDarkTheme()
    val context = LocalContext.current

    // Android 12+ 动态取色作为主色来源（仅浅色模式；深色用固定品牌色保证可读性）
    val dynamicAccent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !useDark) {
        androidx.compose.material3.dynamicLightColorScheme(context).primary
    } else null

    val palette = if (useDark) darkPalette() else lightPalette(dynamicAccent ?: Color(0xFF3F63D6))

    val scheme = if (useDark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color(0xFF00296B),
            primaryContainer = palette.accentDim,
            onPrimaryContainer = Color(0xFFD6E4FF),
            secondary = palette.warnOrange,
            background = palette.appBg,
            onBackground = palette.textFg,
            surface = palette.panelBg,
            onSurface = palette.textFg,
            surfaceVariant = palette.hoverBg,
            onSurfaceVariant = palette.textDim,
            outline = palette.panelBorder,
            error = palette.failRed,
            errorContainer = Color(0xFF5C2A30),
            onErrorContainer = Color(0xFFFFC9CD),
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            primaryContainer = palette.accentDim,
            onPrimaryContainer = Color(0xFF0D2A6B),
            secondary = palette.warnOrange,
            background = palette.appBg,
            onBackground = palette.textFg,
            surface = palette.panelBg,
            onSurface = palette.textFg,
            surfaceVariant = palette.hoverBg,
            onSurfaceVariant = palette.textDim,
            outline = palette.panelBorder,
            error = palette.failRed,
            errorContainer = Color(0xFFFCE8E6),
            onErrorContainer = Color(0xFF8C1D18),
        )
    }

    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
