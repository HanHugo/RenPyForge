package com.renpyforge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renpyforge.app.ui.IdeColors.PanelBorder
import com.renpyforge.app.ui.IdeColors.Syntax
import com.renpyforge.app.ui.IdeColors.TextDim

private const val LINE_HEIGHT_SP = 20

/** 代码编辑器：行号列 + .rpy 语法高亮 + 光标行列回报 */
@Composable
fun CodeEditor(
    path: String,
    content: String,
    onContentChange: (String) -> Unit,
    onCursorMoved: (line: Int, col: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var value by remember(path) {
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(content))
    }
    var curLine by remember(path) { mutableStateOf(1) }
    LaunchedEffect(content) {
        if (value.text != content) {
            value = androidx.compose.ui.text.input.TextFieldValue(content)
        }
    }
    // 光标位置回报
    LaunchedEffect(value.selection.start, value.text) {
        val pos = value.selection.start.coerceIn(0, value.text.length)
        val before = value.text.substring(0, pos)
        val line = before.count { it == '\n' } + 1
        val col = pos - (before.lastIndexOf('\n') + 1) + 1
        curLine = line
        onCursorMoved(line, col)
    }

    val lineHeight = with(LocalDensity.current) { LINE_HEIGHT_SP.sp.toDp() }
    val lineCount = value.text.count { it == '\n' } + 1
    val editorStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = LINE_HEIGHT_SP.sp,
        color = IdeColors.TextFg,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    )

    val scroll = rememberScrollState()
    val hScroll = rememberScrollState()

    Row(
        modifier
            .background(IdeColors.EditorBg)
            .verticalScroll(scroll)
            .horizontalScroll(hScroll)
    ) {
        // 行号列
        Column(
            Modifier
                .background(IdeColors.EditorBg)
                .padding(start = 6.dp, end = 8.dp)
        ) {
            repeat(lineCount) { idx ->
                Box(
                    Modifier
                        .height(lineHeight)
                        .width(38.dp)
                ) {
                    androidx.compose.material3.Text(
                        "${idx + 1}",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = LINE_HEIGHT_SP.sp,
                            color = TextDim.copy(alpha = if (idx + 1 == curLine) 0.95f else 0.55f),
                        ),
                    )
                }
            }
        }
        // 竖直分隔线由外层绘制

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

/** 计算指定内容在光标偏移处的行列号（供状态栏等使用） */
fun lineColOf(text: String, pos: Int): Pair<Int, Int> {
    val p = pos.coerceIn(0, text.length)
    val before = text.substring(0, p)
    val line = before.count { it == '\n' } + 1
    val col = p - (before.lastIndexOf('\n') + 1) + 1
    return line to col
}
