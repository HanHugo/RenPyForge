package com.renpyforge.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renpyforge.app.ui.IdeColors.TextDim
import kotlinx.coroutines.launch

private const val LINE_HEIGHT_SP = 20
private const val BASE_FONT_SP = 13

/**
 * 代码编辑器（干净版）：
 * · 编辑区就是编辑区——没有任何常驻按钮行，复制粘贴用系统文本选择菜单
 * · 「工具行」默认隐藏（撤销/重做/查找/插入代码），由外部开关控制
 * · 符号条固定在编辑器底部（紧贴输入法，代码符号一键输入）
 * · 双指缩放字号；行号 + 语法高亮
 */
@Composable
fun CodeEditor(
    path: String,
    content: String,
    onContentChange: (String) -> Unit,
    onCursorMoved: (line: Int, col: Int) -> Unit,
    showTools: Boolean,
    jumpLine: Int = 0,
    onJumpConsumed: () -> Unit = {},
    fontScale: Float = 1f,
    onFontScaleChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var value by remember(path) { mutableStateOf(TextFieldValue(content)) }
    var curLine by remember(path) { mutableIntStateOf(1) }
    var showFind by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findIdx by remember { mutableIntStateOf(0) }
    var showInsert by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var suggestPrefixLen by remember { mutableIntStateOf(0) }
    val latestScale by rememberUpdatedState(fontScale)

    // 补全候选池：内置关键字 + 当前文件里定义的符号
    fun symbolWords(text: String): List<String> {
        val out = mutableSetOf<String>()
        Regex("""(?m)^\s*(?:label|screen|define|default|transform|style|image)\s+([\w.]+)""")
            .findAll(text)
            .forEach { out.add(it.groupValues[1]) }
        return out.toList()
    }
    val builtinWords = remember { RpySyntax.allWords() }
    var userWords by remember(path) { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(value.text) {
        userWords = symbolWords(value.text).toSet()
    }
    fun updateSuggestions() {
        val pos = value.selection.start
        val t = value.text
        var s = pos
        while (s > 0 && (t[s - 1].isLetterOrDigit() || t[s - 1] == '_')) s--
        val word = t.substring(s, pos)
        if (word.length < 2 || t.getOrNull(pos)?.isLetterOrDigit() == true) {
            suggestions = emptyList()
            return
        }
        val low = word.lowercase()
        suggestions = (builtinWords.asSequence() + userWords.asSequence())
            .filter { it.lowercase().startsWith(low) && !it.equals(word, ignoreCase = true) }
            .distinct()
            .take(10)
            .toList()
        suggestPrefixLen = pos - s
    }

    LaunchedEffect(content) {
        if (value.text != content) value = TextFieldValue(content)
    }
    LaunchedEffect(value.selection.start, value.text) {
        val (l, c) = lineColOf(value.text, value.selection.start)
        curLine = l
        onCursorMoved(l, c)
    }

    val fontSp = (BASE_FONT_SP * fontScale).coerceIn(8f, 32f)
    val lineSp = (LINE_HEIGHT_SP * fontScale).coerceIn(13f, 46f)
    val density = LocalDensity.current
    val lineHeightDp = with(density) { lineSp.sp.toDp() }
    val lineCount = value.text.count { it == '\n' } + 1
    val linePx = with(density) { lineSp.sp.toPx() }

    val editorStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = fontSp.sp,
        lineHeight = lineSp.sp,
        color = IdeColors.TextFg,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    )

    // —— 编辑核心操作 ——
    val undoStack = remember(path) { mutableListOf<TextFieldValue>() }
    val redoStack = remember(path) { mutableListOf<TextFieldValue>() }
    fun replaceSelection(insert: String, cursorOffsetInInsert: Int = insert.length) {
        val s = value.selection.min
        val e = value.selection.max
        val newText = value.text.substring(0, s) + insert + value.text.substring(e)
        val pos = (s + cursorOffsetInInsert).coerceIn(0, newText.length)
        undoStack.add(value)
        if (undoStack.size > 300) undoStack.removeAt(0)
        redoStack.clear()
        value = TextFieldValue(newText, TextRange(pos))
        onContentChange(newText)
    }
    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.add(value)
        value = prev
        onContentChange(value.text)
    }
    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.add(value)
        value = next
        onContentChange(value.text)
    }
    fun completeWith(word: String) {
        val pos = value.selection.start
        val t = value.text
        val s = (pos - suggestPrefixLen).coerceAtLeast(0)
        val newText = t.substring(0, s) + word + t.substring(pos)
        undoStack.add(value)
        redoStack.clear()
        value = TextFieldValue(newText, TextRange(s + word.length))
        onContentChange(newText)
        suggestions = emptyList()
    }
    fun insertSymbol(sym: String) {
        val pair = mapOf("(" to ")", "[" to "]", "{" to "}", "\"" to "\"", "'" to "'")
        val close = pair[sym]
        if (close != null && value.selection.collapsed) {
            replaceSelection(sym + close, 1)
        } else {
            replaceSelection(sym)
        }
    }

    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    fun findMatches(): List<Int> {
        val q = findQuery.trim()
        if (q.isEmpty()) return emptyList()
        val lower = value.text.lowercase()
        val ql = q.lowercase()
        val out = mutableListOf<Int>()
        var i = lower.indexOf(ql)
        while (i != -1 && out.size < 2000) { out.add(i); i = lower.indexOf(ql, i + 1) }
        return out
    }
    fun gotoMatch(matches: List<Int>, forward: Boolean) {
        if (matches.isEmpty()) return
        findIdx = (if (forward) findIdx + 1 else findIdx - 1 + matches.size).mod(matches.size)
        val pos = matches[findIdx]
        value = value.copy(selection = TextRange(pos, pos + findQuery.trim().length))
        val line = value.text.substring(0, pos).count { it == '\n' }
        scope.launch { vScroll.animateScrollTo(((line - 2) * linePx).toInt().coerceIn(0, vScroll.maxValue)) }
    }

    // 问题列表跳转
    LaunchedEffect(jumpLine) {
        if (jumpLine > 0) {
            val text = value.text
            var pos = 0
            var n = 1
            while (n < jumpLine && pos < text.length) {
                if (text[pos] == '\n') n++
                pos++
            }
            if (pos <= text.length) {
                val lineEnd = text.indexOf('\n', pos).let { if (it == -1) text.length else it }
                value = value.copy(selection = TextRange(pos, lineEnd))
                scope.launch { vScroll.animateScrollTo(((jumpLine - 2) * linePx).toInt().coerceIn(0, vScroll.maxValue)) }
            }
            onJumpConsumed()
        }
    }

    Column(modifier.background(IdeColors.EditorBg)) {
        // ── 工具行（默认隐藏，⋮ → 编辑工具 开启） ──
        AnimatedVisibility(
            visible = showTools,
            enter = expandVertically(tween(160)) + fadeIn(),
            exit = shrinkVertically(tween(160)) + fadeOut(),
        ) {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .background(IdeColors.PanelBg)
                    .height(32.dp),
            ) {
                EditorAction("↶ 撤销", enabled = undoStack.isNotEmpty()) { undo() }
                EditorAction("↷ 重做", enabled = redoStack.isNotEmpty()) { redo() }
                EditorAction(if (showFind) "收起查找" else "查找") { showFind = !showFind }
                EditorAction("插入代码") { showInsert = !showInsert }
                EditorAction("A−") { onFontScaleChange(fontScale / 1.15f) }
                EditorAction("A+") { onFontScaleChange(fontScale * 1.15f) }
                Text(
                    "${(fontScale * 100).toInt()}%",
                    fontSize = 11.sp,
                    color = IdeColors.TextDim,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
        // 插入代码菜单（横滑选择）
        AnimatedVisibility(
            visible = showInsert,
            enter = expandVertically(tween(160)) + fadeIn(),
            exit = shrinkVertically(tween(160)) + fadeOut(),
        ) {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .background(IdeColors.ConsoleBg)
                    .height(34.dp)
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("插入：", fontSize = 11.sp, color = IdeColors.TextDim)
                val templates = listOf(
                    "label" to "label start:\n    \n    return",
                    "menu" to "menu:\n    \"选项一\":\n        jump next\n    \"选项二\":\n        jump other",
                    "对话" to "\"角色名\" \"对话内容\"",
                    "screen" to "screen my_screen():\n    vbox:\n        text \"内容\"\n        textbutton \"关闭\" action Return()",
                    "transform" to "transform slidein:\n    xoffset -400\n    easein 0.5 xoffset 0",
                )
                templates.forEach { (name, code) ->
                    SymbolKey(name) { replaceSelection(code) }
                }
            }
        }
        // 查找栏
        AnimatedVisibility(
            visible = showFind,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IdeColors.ConsoleBg)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text("⌕", color = IdeColors.TextDim, fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                BasicTextField(
                    value = findQuery,
                    onValueChange = { findQuery = it; findIdx = 0 },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = IdeColors.TextFg),
                    cursorBrush = SolidColor(IdeColors.Accent),
                    singleLine = true,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                )
                val matches = findMatches()
                Text(
                    if (findQuery.isBlank()) "" else "${if (matches.isEmpty()) 0 else findIdx + 1}/${matches.size}",
                    fontSize = 11.sp, color = IdeColors.TextDim,
                )
                Spacer(Modifier.width(4.dp))
                EditorAction("▲") { gotoMatch(matches, false) }
                EditorAction("▼") { gotoMatch(matches, true) }
            }
        }

        // ── 代码区（双指缩放，无任何按钮） ──
        Box(
            Modifier
                .weight(1f)
                .pointerInput(Unit) {
                    // 只在双指时接管手势，单指拖动继续用于滚动
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var scale = latestScale
                        var event: androidx.compose.ui.input.pointer.PointerEvent
                        do {
                            event = awaitPointerEvent()
                            if (event.changes.size >= 2) {
                                val zoom = event.calculateZoom()
                                if (zoom != 1f) {
                                    scale = (scale * zoom).coerceIn(0.6f, 2.4f)
                                    onFontScaleChange(scale)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(vScroll)
                    .horizontalScroll(hScroll)
            ) {
                Column(
                    Modifier
                        .background(IdeColors.EditorBg)
                        .padding(start = 6.dp, end = 8.dp)
                ) {
                    repeat(lineCount) { idx ->
                        Box(Modifier.height(lineHeightDp).width(38.dp)) {
                            Text(
                                "${idx + 1}",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp * fontScale,
                                    lineHeight = lineSp.sp,
                                    color = TextDim.copy(alpha = if (idx + 1 == curLine) 0.95f else 0.55f),
                                ),
                            )
                        }
                    }
                }
                val pal = LocalPalette.current
                val transform = if (RpySyntax.isRpy(path)) {
                    VisualTransformation { text ->
                        androidx.compose.ui.text.input.TransformedText(
                            RpySyntax.highlight(text.text, pal.syntax),
                            androidx.compose.ui.text.input.OffsetMapping.Identity,
                        )
                    }
                } else {
                    VisualTransformation.None
                }
                BasicTextField(
                    value = value,
                    onValueChange = {
                        if (it.text != value.text) {
                            undoStack.add(value)
                            if (undoStack.size > 300) undoStack.removeAt(0)
                            redoStack.clear()
                        }
                        value = it
                        onContentChange(it.text)
                        updateSuggestions()
                    },
                    textStyle = editorStyle,
                    cursorBrush = SolidColor(IdeColors.Accent),
                    visualTransformation = transform,
                    modifier = Modifier.padding(end = 40.dp)
                )
            }
        }

        // ── 补全建议条 ──
        AnimatedVisibility(
            visible = suggestions.isNotEmpty(),
            enter = expandVertically(tween(140)) + fadeIn(),
            exit = shrinkVertically(tween(140)) + fadeOut(),
        ) {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .background(IdeColors.AccentDim)
                    .height(34.dp)
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                suggestions.forEach { word ->
                    SymbolKey(word) { completeWith(word) }
                }
            }
        }

        // ── 符号条：固定在编辑器底部，紧贴输入法 ──
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .background(IdeColors.PanelBg)
                .height(36.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            SymbolKey("⇥") { insertSymbol("    ") }
            SymbolKey("⇤") {
                val s = value.selection.min
                val lineStart = value.text.lastIndexOf('\n', (s - 1).coerceAtLeast(0)) + 1
                if (value.text.substring(lineStart, s.coerceAtLeast(lineStart)).startsWith("    ") && s - lineStart >= 4) {
                    val newText = value.text.removeRange(lineStart, lineStart + 4)
                    undoStack.add(value)
                    redoStack.clear()
                    value = TextFieldValue(newText, TextRange((s - 4).coerceAtLeast(0)))
                    onContentChange(newText)
                }
            }
            listOf("\"", "'", ":", "=", "(", ")", "[", "]", "{", "}", "_", "-", "+", "*", "/", "<", ">", ",", ".", "!", "$", "#", "@", "%")
                .forEach { sym -> SymbolKey(sym) { insertSymbol(sym) } }
        }
    }
}

@Composable
private fun EditorAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 12.sp,
        color = if (enabled) IdeColors.TextFg else IdeColors.TextDim.copy(alpha = 0.4f),
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun SymbolKey(sym: String, onClick: () -> Unit) {
    Box(
        Modifier
            .border(1.dp, IdeColors.PanelBorder, RoundedCornerShape(6.dp))
            .background(IdeColors.HoverBg, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(
            sym,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = IdeColors.TextFg,
        )
    }
}

/** 计算指定内容在光标偏移处的行列号（供状态栏等使用） */
fun lineColOf(text: String, pos: Int): Pair<Int, Int> {
    val p = pos.coerceIn(0, text.length)
    val before = text.substring(0, p)
    val line = before.count { it == '\n' } + 1
    val col = p - (before.lastIndexOf('\n') + 1) + 1
    return line to col
}
