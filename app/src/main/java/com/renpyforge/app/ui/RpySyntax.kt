package com.renpyforge.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.renpyforge.app.ui.IdeColors.Syntax

/**
 * Ren'Py 脚本轻量语法高亮。
 * 支持：注释(#)、字符串("…" / '…' / `…`)、关键字(label/screen/define…)、
 * 指令(jump/call/menu…)、数字、{tags}、[interpolation]。
 */
object RpySyntax {

    private val keywords = setOf(
        "label", "screen", "define", "default", "init", "python", "style",
        "transform", "image", "layeredimage", "translate", "old", "new",
        "strings", "class", "from", "import", "as", "for", "in",
        "def", "global", "expression",
    )

    private val directives = setOf(
        "scene", "show", "hide", "play", "stop", "queue", "voice", "pause",
        "jump", "call", "menu", "if", "elif", "else", "while", "return",
        "window", "nvl", "clear", "extend", "showif", "use", "add",
        "text", "textbutton", "button", "hbox", "vbox", "frame", "grid",
        "null", "bar", "vbar", "imagemap", "hotspot", "key", "timer",
        "drag", "draggroup", "mousearea", "on", "contains", "has", "at", "with",
    )

    private fun keywordStyle() = SpanStyle(color = Syntax.Keyword, fontWeight = FontWeight.Bold)
    private fun directiveStyle() = SpanStyle(color = Syntax.Directive)
    private fun commentStyle() = SpanStyle(color = Syntax.Comment, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
    private fun stringStyle() = SpanStyle(color = Syntax.StringLit)
    private fun numberStyle() = SpanStyle(color = Syntax.NumberLit)
    private fun tagStyle() = SpanStyle(color = Syntax.Tag)

    fun highlight(text: String): AnnotatedString = buildAnnotatedString {
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                c == '#' -> {
                    val end = text.indexOf('\n', i).let { if (it == -1) n else it }
                    withStyle(commentStyle()) { append(text.substring(i, end)) }
                    i = end
                }
                c == '"' || c == '\'' || c == '`' -> {
                    var j = i + 1
                    while (j < n) {
                        if (text[j] == '\\' && c != '`') { j += 2; continue }
                        if (text[j] == c) { j++; break }
                        if (text[j] == '\n') break
                        j++
                    }
                    withStyle(stringStyle()) { append(text.substring(i, j)) }
                    i = j
                }
                c == '{' -> {
                    val end = text.indexOf('}', i)
                    if (end == -1 || end - i > 32 || text.substring(i + 1, end).contains('\n')) {
                        append(c); i++
                    } else {
                        withStyle(tagStyle()) { append(text.substring(i, end + 1)) }
                        i = end + 1
                    }
                }
                c == '[' -> {
                    if (i + 1 < n && text[i + 1] == '[') { append("[["); i += 2; continue }
                    val end = text.indexOf(']', i)
                    if (end == -1 || end - i > 48 || text.substring(i + 1, end).contains('\n')) {
                        append(c); i++
                    } else {
                        withStyle(tagStyle()) { append(text.substring(i, end + 1)) }
                        i = end + 1
                    }
                }
                c.isLetter() || c == '_' -> {
                    var j = i
                    while (j < n && (text[j].isLetterOrDigit() || text[j] == '_')) j++
                    val word = text.substring(i, j)
                    when {
                        word in keywords -> withStyle(keywordStyle()) { append(word) }
                        word in directives -> withStyle(directiveStyle()) { append(word) }
                        else -> append(word)
                    }
                    i = j
                }
                c.isDigit() -> {
                    var j = i
                    while (j < n && (text[j].isLetterOrDigit() || text[j] == '.')) j++
                    withStyle(numberStyle()) { append(text.substring(i, j)) }
                    i = j
                }
                else -> { append(c); i++ }
            }
        }
    }

    /** 纯文本（非 .rpy 文件）不做高亮，仅给统一前景色 */
    fun plain(text: String): AnnotatedString = AnnotatedString(text)

    fun isRpy(path: String): Boolean =
        path.endsWith(".rpy") || path.endsWith(".rpym")

    @Suppress("unused")
    private fun unusedColor(): Color = Syntax.Plain
}
