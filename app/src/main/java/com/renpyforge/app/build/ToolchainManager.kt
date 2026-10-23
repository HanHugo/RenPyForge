package com.renpyforge.app.build

import android.content.Context
import java.io.File

/**
 * 工具链管理：全部工具随 App 内置，开箱即用。
 *
 * - aapt2 / zipalign：静态链接 ARM64 二进制，以 lib*.so 形式放在 jniLibs，
 *   安装时解压到 nativeLibraryDir（可执行），通过绝对路径调用。
 * - classes.dex（RAPT Java 模板预编译）、android.jar、引擎库、Ren'Py 运行时：
 *   从 assets 释放到 filesDir。
 */
class ToolchainManager(private val context: Context) {

    private val workDir = File(context.filesDir, "tools").apply { mkdirs() }

    companion object {
        /** 释放资源的版本标记：升级内置资产时 +1，强制重新释放，避免旧缓存 */
        const val RELEASE_VER = 14
    }

    /** 释放 assets 下的大文件（android.jar / classes.dex / 引擎库 / main.py 等）到 filesDir；版本变化时强制重释放 */
    private fun releaseBundled() {
        val verFile = File(workDir, ".files-ver")
        if (verFile.isFile && verFile.readText().trim() == RELEASE_VER.toString()) return
        File(workDir, "android.jar").delete()
        File(workDir, "classes.dex").delete()
        File(workDir, "librenpython.so").delete()
        File(workDir, "main.py").delete()
        File(workDir, "renpy-main.py").delete()
        releaseOne("tools/android.jar", "android.jar")
        releaseOne("tools/classes.dex", "classes.dex")
        releaseOne("renpy-engine-librenpython.so", "librenpython.so")
        releaseOne("renpy-runtime/main.py", "main.py")
        releaseOne("renpy-runtime/renpy-main.py", "renpy-main.py")
        verFile.writeText(RELEASE_VER.toString())
        runtimeVersionCache = null
    }

    private var runtimeVersionCache: String? = null

    /**
     * 内置运行时的确定性版本号（写入游戏 APK 的 private_version 字符串）。
     * 关键：游戏端 PythonSDLActivity 以它判断是否解包 private.mp3，
     * 缺失该字符串会导致不解包 → 引擎找不到 private 文件直接崩溃。
     */
    fun runtimeVersion(): String {
        runtimeVersionCache?.let { return it }
        val md = java.security.MessageDigest.getInstance("MD5")
        fun feed(s: String) = md.update(s.toByteArray())
        feed("RELEASE_VER=$RELEASE_VER;")
        feed("main.py:" + File(workDir, "main.py").length() + ";")
        val runtime = releaseDir("renpy-runtime/renpy", "renpy-runtime/renpy")
        runtime.walkTopDown().filter { it.isFile }.sorted().forEach {
            feed(it.relativeTo(workDir).invariantSeparatorsPath + ":" + it.length() + ";")
        }
        // Python 标准库（distlib）也是运行时的一部分
        val stdlib = releaseDir("renpy-stdlib/python3.12", "renpy-stdlib/python3.12")
        stdlib.walkTopDown().filter { it.isFile }.sorted().forEach {
            feed(it.relativeTo(workDir).invariantSeparatorsPath + ":" + it.length() + ";")
        }
        return runtimeVersionCache ?: hashOf(md).also { runtimeVersionCache = it }
    }

    private fun hashOf(md: java.security.MessageDigest): String =
        md.digest().joinToString("") { "%02x".format(it) }

    /** 释放 presplash / downloading 到 filesDir（打入游戏 APK assets） */
    fun presplashFile(): File {
        releaseOne("templates/renpy-presplash.jpg", "android-presplash.jpg")
        return File(workDir, "android-presplash.jpg")
    }

    fun downloadingFile(): File {
        releaseOne("templates/renpy-downloading.jpg", "android-downloading.jpg")
        return File(workDir, "android-downloading.jpg")
    }

    /** nativeLibraryDir 中的可执行工具（lib*.so 形式） */
    fun resolveExecutable(name: String): File? {
        val f = File(context.applicationInfo.nativeLibraryDir, "lib$name.so")
        return if (f.isFile && f.canExecute()) f else null
    }

    /** 内置脚本预编译器（Py_BytesMain 包装器，链接 librenpython.so） */
    fun compileExe(): File? = resolveExecutable("renpycompile")

    private fun releaseOne(assetPath: String, targetName: String) {
        val target = File(workDir, targetName)
        if (target.isFile && target.length() > 0) return
        try {
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (_: Exception) {
            target.delete()
        }
    }

    /** 释放整个 assets 子目录（递归），返回本地目标目录；带版本标记，版本变化时重新释放 */
    fun releaseDir(assetDir: String, targetName: String): File {
        val target = File(workDir, targetName)
        val verFile = File(target, ".release-ver")
        val currentVer = RELEASE_VER.toString()
        if (target.isDirectory && verFile.isFile && verFile.readText().trim() == currentVer) {
            return target
        }
        target.deleteRecursively()
        target.mkdirs()
        releaseDirRec(assetDir, target)
        verFile.writeText(currentVer)
        return target
    }

    private fun releaseDirRec(assetDir: String, target: File) {
        target.mkdirs()
        val entries = try {
            context.assets.list(assetDir) ?: return
        } catch (_: Exception) {
            return
        }
        if (entries.isEmpty()) {
            // 空目录在 assets 中以占位文件表示（.keep）
            return
        }
        for (entry in entries) {
            val childAsset = "$assetDir/$entry"
            val child = File(target, entry)
            val sub = try { context.assets.list(childAsset)?.size ?: 0 } catch (_: Exception) { 0 }
            if (sub > 0 || entry == ".keep") {
                if (entry != ".keep") releaseDirRec(childAsset, child)
            } else {
                try {
                    context.assets.open(childAsset).use { input ->
                        child.outputStream().use { output -> input.copyTo(output) }
                    }
                } catch (_: Exception) {
                    // ignore
                }
            }
        }
    }

    /** 步骤 1：环境自检（全内置，只确认释放成功） */
    fun checkEnvironment(): List<String> {
        releaseBundled()
        val missing = mutableListOf<String>()
        if (resolveExecutable("aapt2") == null) missing += "aapt2（jniLibs 中未找到 libaapt2.so）"
        if (resolveExecutable("zipalign") == null) missing += "zipalign（jniLibs 中未找到 libzipalign.so）"
        if (!File(workDir, "android.jar").isFile) missing += "android.jar 未释放成功"
        if (!File(workDir, "classes.dex").isFile) missing += "classes.dex 未释放成功"
        if (!File(workDir, "librenpython.so").isFile) missing += "librenpython.so 未释放成功"
        return missing
    }

    fun workDir(): File = workDir
    fun cachedDex(): File = File(workDir, "classes.dex")
    fun androidJar(): File = File(workDir, "android.jar")
    fun engineSo(): File = File(workDir, "librenpython.so")
    fun appContext(): Context = context
}
