package com.renpyforge.app.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * Ren'Py 脚本轻量语法高亮。
 * 支持：注释(#)、字符串("…" / '…' / `…`)、关键字(label/screen/define…)、
 * 指令(jump/call/menu…)、数字、{tags}、[interpolation]。
 * 配色来自当前主题的 [SyntaxPalette]（浅色 / 深色自动切换）。
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

    fun highlight(text: String, syn: SyntaxPalette): AnnotatedString = buildAnnotatedString {
        val keywordStyle = SpanStyle(color = syn.keyword, fontWeight = FontWeight.Bold)
        val directiveStyle = SpanStyle(color = syn.directive)
        val commentStyle = SpanStyle(color = syn.comment, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        val stringStyle = SpanStyle(color = syn.stringLit)
        val numberStyle = SpanStyle(color = syn.number)
        val tagStyle = SpanStyle(color = syn.tag)

        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                c == '#' -> {
                    val end = text.indexOf('\n', i).let { if (it == -1) n else it }
                    withStyle(commentStyle) { append(text.substring(i, end)) }
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
                    withStyle(stringStyle) { append(text.substring(i, j)) }
                    i = j
                }
                c == '{' -> {
                    val end = text.indexOf('}', i)
                    if (end == -1 || end - i > 32 || text.substring(i + 1, end).contains('\n')) {
                        append(c); i++
                    } else {
                        withStyle(tagStyle) { append(text.substring(i, end + 1)) }
                        i = end + 1
                    }
                }
                c == '[' -> {
                    if (i + 1 < n && text[i + 1] == '[') { append("[["); i += 2; continue }
                    val end = text.indexOf(']', i)
                    if (end == -1 || end - i > 48 || text.substring(i + 1, end).contains('\n')) {
                        append(c); i++
                    } else {
                        withStyle(tagStyle) { append(text.substring(i, end + 1)) }
                        i = end + 1
                    }
                }
                c.isLetter() || c == '_' -> {
                    var j = i
                    while (j < n && (text[j].isLetterOrDigit() || text[j] == '_')) j++
                    val word = text.substring(i, j)
                    when {
                        word in keywords -> withStyle(keywordStyle) { append(word) }
                        word in directives -> withStyle(directiveStyle) { append(word) }
                        else -> append(word)
                    }
                    i = j
                }
                c.isDigit() -> {
                    var j = i
                    while (j < n && (text[j].isLetterOrDigit() || text[j] == '.')) j++
                    withStyle(numberStyle) { append(text.substring(i, j)) }
                    i = j
                }
                else -> { append(c); i++ }
            }
        }
    }

    fun isRpy(path: String): Boolean =
        path.endsWith(".rpy") || path.endsWith(".rpym")

    /** 补全候选：全部关键字 + 指令 */
    fun allWords(): List<String> = (keywords + directives).sorted()
}
