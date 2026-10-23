package com.renpyforge.app.build

/**
 * RAPT 兼容的资产打包规则。
 *
 * 1) 所有进入 APK assets 的文件/目录都要加 "x-" 前缀（每一级），Ren'Py 运行时按此约定读取。
 *    （RAPT 的 build.py make_assets 对 aapt2 做同样处理）
 * 2) noCompress 扩展名的条目必须 STORED（不压缩），保证运行时 seek 与一致行为。
 */
object AssetRules {

    // 与 RAPT app-build.gradle 中 androidResources.noCompress 一致
    val NO_COMPRESS_EXTS = setOf(
        "ogv", "avi", "mpg", "webm", "mkv", "mp4", "mp3", "mp2", "ogg", "opus",
        "flac", "png", "jpg", "jpeg", "webp", "avif", "rpyc", "rpymc", "rpyb",
        "wav", "zip", "mp3",
    )

    fun isNoCompress(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in NO_COMPRESS_EXTS
    }

    /** "game/script.rpy" → "x-game/x-script.rpy"（每一级加 x- 前缀） */
    fun xPrefix(path: String): String =
        path.split('/').joinToString("/") { if (it.startsWith("x-")) it else "x-$it" }
}
