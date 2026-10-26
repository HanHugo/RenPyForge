package com.renpyforge.app.project

/**
 * Ren'Py 脚本轻量语法检查（无需引擎，纯文本规则）。
 *
 * 只做「高置信度」检查，宁可漏报也不误报：
 * · 缩进必须是 4 空格的倍数（Tab、续行、多行字符串、python 块内不检查）
 * · 字符串引号未闭合（用状态机扫描，不再简单计数，避免 `"It's"` 这类误报）
 * · 常见语句缺少结尾冒号
 */
object RpyCheck {

    data class Problem(val line: Int, val col: Int, val message: String)

    private val BLOCK_STATEMENTS = listOf("label", "menu", "screen", "style", "transform", "python")
    private val PYTHON_BLOCK = Regex("""^\s*(?:init\s+)?python(?:\s+early)?\s*:""")

    // show/scene/hide 后跟带引号的文件名（Ren'Py 语法错误：这三个语句只接受图片名）
    private val IMG_STMT_QUOTED = Regex("""^\s*(?:show|scene|hide)\s+["']""")

    // 单独一行的图片/音频文件名（不是合法语句）
    private val BARE_FILENAME = Regex(
        """^\s*[\w\-\u4e00-\u9fff.]+\.(?:png|jpe?g|webp|gif|bmp|ogg|opus|mp3|wav)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    fun check(content: String): List<Problem> {
        val problems = mutableListOf<Problem>()
        val lines = content.split('\n')

        var tripleQuote: Char? = null     // 跨行三引号字符串
        var bracketDepth = 0              // 跨行括号（续行）
        var pythonIndent = -1             // >=0 表示处于 python: 块内

        lines.forEachIndexed { idx, raw ->
            val no = idx + 1
            val line = raw.trimEnd()
            val stripped = line.trimStart()

            // 1) 多行字符串内部：整行跳过，只寻找三引号闭合
            val openTriple = tripleQuote
            if (openTriple != null) {
                val closer = "" + openTriple + openTriple + openTriple
                if (line.contains(closer)) tripleQuote = null
                return@forEachIndexed
            }

            if (line.isBlank()) return@forEachIndexed
            if (stripped.startsWith("#")) return@forEachIndexed

            val indent = line.length - stripped.length

            // python 块结束判定
            if (pythonIndent >= 0 && indent <= pythonIndent) pythonIndent = -1
            val inPython = pythonIndent >= 0

            // 2) 语句块跟踪
            if (!inPython && PYTHON_BLOCK.matches(stripped)) {
                pythonIndent = indent
            }

            // 3) 字符串 / 括号扫描
            val scan = scanLine(stripped)
            val continuation = bracketDepth > 0

            if (scan.unclosedTriple != null) {
                tripleQuote = scan.unclosedTriple
            } else if (scan.unclosedQuote != null && !line.endsWith("\\")) {
                problems.add(
                    Problem(no, indent + scan.unclosedCol, "字符串引号未闭合（缺少结尾的 ${scan.unclosedQuote}）")
                )
            }
            bracketDepth = (bracketDepth + scan.bracketDelta).coerceAtLeast(0)

            if (inPython || continuation) return@forEachIndexed

            // 4) show/scene/hide 带引号的文件名（常见新手错误，运行时必然报错）
            if (IMG_STMT_QUOTED.matches(stripped)) {
                problems.add(
                    Problem(no, indent + 1, "show/scene/hide 后不能跟带引号的文件名：去掉引号和扩展名，如 scene 87870（图片需在 game/images/ 下）")
                )
                return@forEachIndexed
            }

            // 5) 单独一行的文件名不是语句
            if (BARE_FILENAME.matches(stripped)) {
                problems.add(
                    Problem(no, indent + 1, "单独的文件名不是语句：先定义 image 图片名 = \"文件名\"，再用 show 图片名；或删除该行")
                )
                return@forEachIndexed
            }

            // 6) 缩进必须是 4 空格的倍数（续行/多行字符串已在上面排除）
            if (indent % 4 != 0) {
                problems.add(Problem(no, 1, "缩进应为 4 空格的倍数（当前 $indent 个空格）"))
            }
            if ('\t' in line.take(indent)) {
                problems.add(Problem(no, 1, "使用了 Tab 缩进，Ren'Py 要求 4 空格"))
            }

            // 7) 常见语句缺少结尾冒号
            val lower = stripped.lowercase()
            if (BLOCK_STATEMENTS.any { lower == it || lower.startsWith("$it ") } && !stripped.endsWith(":")) {
                problems.add(Problem(no, stripped.length + 1, "语句末尾可能缺少冒号「:」"))
            }
        }
        return problems
    }

    private data class Scan(
        val unclosedTriple: Char?,
        val unclosedQuote: Char?,
        val unclosedCol: Int,
        val bracketDelta: Int,
    )

    /**
     * 逐字符扫描一行：处理转义、注释、单/双引号、三引号。
     * 引号状态是「同一个字符串内只认开引号类型」，因此 "don't" 不会误判。
     */
    private fun scanLine(s: String): Scan {
        var quote: Char? = null
        var depth = 0
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (quote != null) {
                when {
                    c == '\\' -> { i += 2; continue }
                    c == quote -> quote = null
                }
                i++
                continue
            }
            when (c) {
                '#' -> break
                '"', '\'' -> {
                    // 三引号
                    if (i + 2 < s.length && s[i + 1] == c && s[i + 2] == c) {
                        val closer = "$c$c$c"
                        val close = s.indexOf(closer, i + 3)
                        if (close < 0) return Scan(c, null, i + 1, depth)
                        i = close + 3
                        continue
                    }
                    quote = c
                }
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
            }
            i++
        }
        return Scan(null, quote, s.length, depth)
    }
}
