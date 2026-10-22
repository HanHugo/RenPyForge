package com.renpyforge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
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

/** 代码编辑器：行号 + 语法高亮 + 剪贴板工具条 + 快捷符号栏 + 双指缩放 */
@Composable
fun CodeEditor(
    path: String,
    content: String,
    onContentChange: (String) -> Unit,
    onCursorMoved: (line: Int, col: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var value by remember(path) { mutableStateOf(TextFieldValue(content)) }
    var curLine by remember(path) { mutableIntStateOf(1) }
    var fontScale by remember { mutableFloatStateOf(1f) }
    var showFind by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findIdx by remember { mutableIntStateOf(0) }
    val clipboard = LocalClipboardManager.current

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

    // —— 编辑操作 ——
    fun commitSelection(range: TextRange) { value = value.copy(selection = range) }

    fun selText(): String =
        if (value.selection.collapsed) "" else value.text.substring(value.selection.min, value.selection.max)

    fun replaceSelection(insert: String, cursorOffsetInInsert: Int = insert.length) {
        val s = value.selection.min
        val e = value.selection.max
        val newText = value.text.substring(0, s) + insert + value.text.substring(e)
        val pos = (s + cursorOffsetInInsert).coerceAtMost(newText.length)
        value = TextFieldValue(newText, TextRange(pos))
        onContentChange(newText)
    }

    fun insertSymbol(sym: String) {
        // 自动补对：光标落在括号/引号对中间
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

    // —— 查找 ——
    val linePx = with(density) { lineSp.sp.toPx() }
    val findScope = androidx.compose.runtime.rememberCoroutineScope()
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
        val target = ((line - 2) * linePx).toInt().coerceIn(0, vScroll.maxValue)
        findScope.launch { vScroll.animateScrollTo(target) }
    }

    Column(modifier.background(IdeColors.EditorBg)) {
        // ── 工具条：剪贴板操作 + 缩放 ──
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(IdeColors.PanelBg)
                .height(32.dp),
        ) {
            EditorAction("全选") { commitSelection(TextRange(0, value.text.length)) }
            EditorAction("复制") {
                val t = selText()
                if (t.isNotEmpty()) clipboard.setText(AnnotatedString(t))
            }
            EditorAction("剪切") {
                val t = selText()
                if (t.isNotEmpty()) {
                    clipboard.setText(AnnotatedString(t))
                    replaceSelection("")
                }
            }
            EditorAction("粘贴") {
                clipboard.getText()?.text?.let { replaceSelection(it) }
            }
            Box(
                Modifier
                    .padding(horizontal = 6.dp)
                    .height(16.dp)
                    .width(1.dp)
                    .background(IdeColors.PanelBorder)
            )
            EditorAction("A−") { fontScale = (fontScale - 0.15f).coerceAtLeast(0.5f) }
            EditorAction("A+") { fontScale = (fontScale + 0.15f).coerceAtMost(2.5f) }
            Box(
                Modifier
                    .padding(horizontal = 6.dp)
                    .height(16.dp)
                    .width(1.dp)
                    .background(IdeColors.PanelBorder)
            )
            EditorAction(if (showFind) "收起查找" else "查找") { showFind = !showFind }
        }
        // ── 查找栏 ──
        androidx.compose.animation.AnimatedVisibility(
            visible = showFind,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
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
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(IdeColors.PanelBorder)
        )

        // ── 快捷符号栏 ──
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .background(IdeColors.PanelBg)
                .height(34.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SymbolKey("⇥") { insertSymbol("    ") }          // Tab 缩进
            SymbolKey("⇤") {                                  // 反缩进：删行首空格
                val s = value.selection.min
                val lineStart = value.text.lastIndexOf('\n', (s - 1).coerceAtLeast(0)) + 1
                if (value.text.substring(lineStart, s.coerceAtLeast(lineStart)).startsWith("    ") && s - lineStart >= 4) {
                    val newText = value.text.removeRange(lineStart, lineStart + 4)
                    value = TextFieldValue(newText, TextRange((s - 4).coerceAtLeast(0)))
                    onContentChange(newText)
                }
            }
            listOf("\"", "'", ":", "=", "(", ")", "[", "]", "{", "}", "_", "-", "+", "*", "/", "<", ">", ",", ".", "!", "$", "#", "@", "%")
                .forEach { sym -> SymbolKey(sym) { insertSymbol(sym) } }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(IdeColors.PanelBorder)
        )

        // ── 代码区（双指缩放） ──
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        fontScale = (fontScale * zoom).coerceIn(0.5f, 2.5f)
                    }
                }
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(vScroll)
                    .horizontalScroll(hScroll)
            ) {
                // 行号列
                Column(
                    Modifier
                        .background(IdeColors.EditorBg)
                        .padding(start = 6.dp, end = 8.dp)
                ) {
                    repeat(lineCount) { idx ->
                        Box(Modifier.height(lineHeightDp).width(38.dp)) {
                            androidx.compose.material3.Text(
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
                val transform = if (RpySyntax.isRpy(path)) {
                    VisualTransformation { text ->
                        androidx.compose.ui.text.input.TransformedText(
                            RpySyntax.highlight(text.text),
                            androidx.compose.ui.text.input.OffsetMapping.Identity,
                        )
                    }
                } else {
                    VisualTransformation.None
                }
                BasicTextField(
                    value = value,
                    onValueChange = { value = it; onContentChange(it.text) },
                    textStyle = editorStyle,
                    cursorBrush = SolidColor(IdeColors.Accent),
                    visualTransformation = transform,
                    modifier = Modifier.padding(end = 40.dp)
                )
            }
        }
    }
}

@Composable
private fun EditorAction(label: String, onClick: () -> Unit) {
    androidx.compose.material3.Text(
        label,
        fontSize = 12.sp,
        color = IdeColors.TextFg,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun SymbolKey(sym: String, onClick: () -> Unit) {
    Box(
        Modifier
            .border(1.dp, IdeColors.PanelBorder, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .background(IdeColors.HoverBg, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        androidx.compose.material3.Text(
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
