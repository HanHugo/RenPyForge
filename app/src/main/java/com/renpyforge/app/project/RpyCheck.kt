package com.renpyforge.app.project

/**
 * Ren'Py 脚本轻量语法检查（无需引擎，纯文本规则）。
 * 覆盖新手最常犯的错误：缩进、Tab 混用、未闭合字符串、行尾冒号缺失（常见语句）。
 */
object RpyCheck {

    data class Problem(val line: Int, val col: Int, val message: String)

    fun check(content: String): List<Problem> {
        val problems = mutableListOf<Problem>()
        val lines = content.split('\n')
        var inMenuBlock = false

        lines.forEachIndexed { idx, raw ->
            val no = idx + 1
            val line = raw.trimEnd()
            if (line.isBlank()) return@forEachIndexed
            val stripped = line.trimStart()
            if (stripped.startsWith("#")) return@forEachIndexed  // 注释行跳过

            val indent = line.length - line.trimStart().length
            val indentText = line.take(indent)

            // 1. 缩进必须是 4 的倍数
            if (indent % 4 != 0) {
                problems.add(Problem(no, 1, "缩进应为 4 空格的倍数（当前 $indent 个空格）"))
            }
            // 2. Tab 混用
            if ('\t' in indentText) {
                problems.add(Problem(no, 1, "使用了 Tab 缩进，Ren'Py 要求 4 空格"))
            }

            // 3. 未闭合字符串（排除注释部分后引号数为奇数）
            val codePart = stripComment(stripped)
            val dq = countUnescaped(codePart, '"')
            val sq = countUnescaped(codePart, '\'')
            if (dq % 2 != 0 || sq % 2 != 0) {
                problems.add(Problem(no, codePart.length + 1, "字符串引号未闭合"))
            }

            // 5. 常见语句缺冒号
            val lower = stripped.lowercase()
            val needsColon = listOf("label", "menu:", "screen", "style", "transform", "python")
            if (needsColon.any { lower == it || lower.startsWith("$it ") } && !stripped.endsWith(":")) {
                problems.add(Problem(no, stripped.length + 1, "语句末尾可能缺少冒号「:」"))
            }

            // menu 块跟踪（简化）
            if (lower == "menu:" || lower.startsWith("menu:")) inMenuBlock = true
            if (inMenuBlock && indent == 0 && !lower.startsWith("menu")) inMenuBlock = false
        }
        return problems
    }

    /** 去掉行内注释（# 不在字符串内时） */
    private fun stripComment(s: String): String {
        var inDq = false
        var inSq = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' && (inDq || inSq) -> i++
                c == '"' && !inSq -> inDq = !inDq
                c == '\'' && !inDq -> inSq = !inSq
                c == '#' && !inDq && !inSq -> return s.substring(0, i)
            }
            i++
        }
        return s
    }

    private fun countUnescaped(s: String, q: Char): Int {
        var count = 0
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\') { i += 2; continue }
            if (s[i] == q) count++
            i++
        }
        return count
    }
}
