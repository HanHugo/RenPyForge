package com.renpyforge.app.build

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.renpyforge.app.project.AppStorage
import com.renpyforge.app.project.ProjectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 构建流水线调度（全内置版）。
 * 输入：App 内置工具链 + 用户项目 game/ 目录 → 直接产出可安装的游戏 APK。
 */
class BuildPipeline(
    private val context: Context,
    private val projectName: String,
    private val config: BuildConfig,
    private val onUpdate: (StepId, StepState, String) -> Unit,
) {
    private val toolchain = ToolchainManager(context)
    private val outputDir = AppStorage.outputRoot(context)

    private fun log(step: StepId, message: String) = onUpdate(step, StepState.RUNNING, message)

    /** 确保 rel 的所有父目录在 tar 中作为目录条目存在 */
    private fun ensureParentDirs(tar: TarWriter, addedDirs: MutableSet<String>, rel: String) {
        val parent = rel.substringBeforeLast('/', "")
        if (parent.isEmpty()) return
        var acc = ""
        for (p in parent.split('/')) {
            acc = if (acc.isEmpty()) p else "$acc/$p"
            if (addedDirs.add(acc)) tar.addDirectory(acc)
        }
    }

    /**
     * 脚本预编译：搭一个完整引擎暂存目录（main.py + renpy + common + stdlib + game），
     * 用内置 Py_BytesMain 包装器跑 `renpy compile`，把生成的 .rpyc 合并进打包目录。
     * 任一环节失败都只是回退到运行时自动编译，不影响构建。
     */
    private fun precompiledGameDir(
        commonDir: File,
        mainPy: File,
        renpyMainPy: File,
        runtimeDir: File,
        stdlibDir: File,
    ): File {
        val projectGameDir = ProjectManager(context).gameDir(projectName)
        val compileExe = toolchain.compileExe() ?: run {
            log(StepId.ASSEMBLE, "预编译器不可用，脚本将在游戏首次启动时自动编译")
            return projectGameDir
        }
        val staging = File(outputDir, "precompile")
        val merged = File(outputDir, "merged-game")
        staging.deleteRecursively()
        merged.deleteRecursively()
        staging.mkdirs()
        return try {
            mainPy.copyTo(File(staging, "main.py"), overwrite = true)
            renpyMainPy.copyTo(File(staging, "renpy-main.py"), overwrite = true)
            runtimeDir.copyRecursively(File(staging, "renpy"), overwrite = true)
            commonDir.copyRecursively(File(staging, "renpy/common"), overwrite = true)
            stdlibDir.copyRecursively(File(staging, "lib/python3.12"), overwrite = true)
            projectGameDir.copyRecursively(File(staging, "game"), overwrite = true)

            val pb = ProcessBuilder(
                listOf(compileExe.absolutePath, File(staging, "main.py").absolutePath, "compile")
            ).apply {
                directory(staging)
                redirectErrorStream(true)
                environment()["LD_LIBRARY_PATH"] = toolchain.workDir().absolutePath
                environment()["PYTHONHOME"] = staging.absolutePath
                environment()["PYTHONPATH"] = staging.absolutePath
                environment()["SDL_VIDEODRIVER"] = "dummy"
                environment()["SDL_AUDIODRIVER"] = "dummy"
                environment()["ANDROID_PUBLIC"] = staging.absolutePath
                environment()["HOME"] = staging.absolutePath
            }
            log(StepId.ASSEMBLE, "▶ 预编译脚本（.rpy → .rpyc）…")
            val proc = pb.start()
            val outText = proc.inputStream.bufferedReader().use { it.readText() }
            val finished = proc.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                log(StepId.ASSEMBLE, "预编译超时，回退到首次启动自动编译")
                return projectGameDir
            }
            if (proc.exitValue() != 0) {
                log(StepId.ASSEMBLE, "预编译失败（exit=${proc.exitValue()}），回退到首次启动自动编译：${outText.take(300)}")
                return projectGameDir
            }
            val rpyc = File(staging, "game").walkTopDown()
                .filter { it.isFile && (it.name.endsWith(".rpyc") || it.name.endsWith(".rpymc")) }
                .toList()
            if (rpyc.isEmpty()) {
                log(StepId.ASSEMBLE, "预编译未产出 .rpyc，游戏将在首次启动时自动编译")
                return projectGameDir
            }
            projectGameDir.copyRecursively(merged, overwrite = true)
            rpyc.forEach { f ->
                val rel = f.relativeTo(File(staging, "game"))
                f.copyTo(File(merged, rel.path), overwrite = true)
            }
            log(StepId.ASSEMBLE, "✔ 预编译完成：${rpyc.size} 个脚本缓存（首次启动更快）")
            merged
        } catch (e: Exception) {
            log(StepId.ASSEMBLE, "预编译跳过：${e.message}")
            projectGameDir
        } finally {
            staging.deleteRecursively()
        }
    }

    suspend fun run(): File = withContext(Dispatchers.IO) {
        fun enter(id: StepId) { onUpdate(id, StepState.RUNNING, "▶ 开始：${id.label}") }
        fun done(id: StepId) { onUpdate(id, StepState.OK, "✔ 完成：${id.label}") }

        // —— 步骤 1：环境自检 ——
        enter(StepId.ENV_CHECK)
        val missing = toolchain.checkEnvironment()
        if (missing.isNotEmpty()) {
            throw StepException(StepId.ENV_CHECK, "内置工具链异常：\n" + missing.joinToString("\n") { " - $it" })
        }
        // 释放 Ren'Py 运行时与 common（一次性，随后缓存）
        val runtimeDir = toolchain.releaseDir("renpy-runtime/renpy", "renpy-runtime/renpy")
        val mainPy = File(toolchain.workDir(), "main.py")
        if (!mainPy.isFile) {
            context.assets.open("renpy-runtime/main.py").use { input ->
                mainPy.outputStream().use { input.copyTo(it) }
            }
        }
        val renpyMainPy = File(toolchain.workDir(), "renpy-main.py")
        if (!renpyMainPy.isFile) {
            context.assets.open("renpy-runtime/renpy-main.py").use { input ->
                renpyMainPy.outputStream().use { input.copyTo(it) }
            }
        }
        val commonDir = toolchain.releaseDir("renpy-common/renpy/common", "renpy-common/renpy/common")
        // Python 标准库（distlib：*.pyc + sitecustomize + certifi），进 private/lib/python3.12
        val stdlibDir = toolchain.releaseDir("renpy-stdlib/python3.12", "renpy-stdlib/python3.12")
        if (!runtimeDir.isDirectory || runtimeDir.listFiles().isNullOrEmpty()) {
            throw StepException(StepId.ENV_CHECK, "Ren'Py 运行时未释放成功")
        }
        log(StepId.ENV_CHECK, "运行时：${runtimeDir.absolutePath}；common：${commonDir.absolutePath}")
        done(StepId.ENV_CHECK)

        // —— 步骤 2：aapt2 资源编译与链接 ——
        enter(StepId.AAPT2)
        val aapt2Log = StringBuilder()
        val baseApk = Aapt2Step.run(toolchain, config, aapt2Log)
        aapt2Log.lines().forEach { if (it.isNotBlank()) log(StepId.AAPT2, it) }
        done(StepId.AAPT2)

        // —— 步骤 3：APK 组装 ——
        enter(StepId.ASSEMBLE)
        val gameDir = precompiledGameDir(commonDir, mainPy, renpyMainPy, runtimeDir, stdlibDir)

        // 3a) 生成 private.mp3（**gzip 压缩的 ustar**，与 RAPT make_tar 一致；
        //     AssetExtract.java 用 GZIPInputStream 解包，且要求目录条目存在）
        val privateTar = File(outputDir, "private.mp3")
        java.util.zip.GZIPOutputStream(privateTar.outputStream().buffered()).use { gz ->
            TarWriter(gz).use { tar ->
                tar.addFile("main.py", mainPy)           // 日志壳
                tar.addFile("renpy-main.py", renpyMainPy) // 原版 renpy.py
                val addedDirs = HashSet<String>()
                // 3a-1) Python 标准库 → lib/python3.12/**（renpy-build distlib，必须存在）
                stdlibDir.walkTopDown().filter { it.isFile }.sorted().forEach { f ->
                    val rel = "lib/python3.12/" + f.relativeTo(stdlibDir).invariantSeparatorsPath
                    ensureParentDirs(tar, addedDirs, rel)
                    tar.addFile(rel, f)
                }
                // 3a-2) renpy 包 → renpy/**
                runtimeDir.walkTopDown().filter { it.isFile }.sorted().forEach { f ->
                    val rel = "renpy/" + f.relativeTo(runtimeDir).invariantSeparatorsPath
                    ensureParentDirs(tar, addedDirs, rel)
                    tar.addFile(rel, f)
                }
            }
        }
        log(StepId.ASSEMBLE, "private.mp3：${privateTar.length() / 1024} KB")

        val unsigned = File(outputDir, "unsigned.apk")
        val aligned = File(outputDir, "aligned.apk")
        ApkAssembler.assemble(
            ApkAssembler.Inputs(
                baseApk = baseApk,
                classesDex = toolchain.cachedDex(),
                engineSo = toolchain.engineSo(),
                gameDir = gameDir,
                commonDir = commonDir,
                privateTar = privateTar,
                presplash = toolchain.presplashFile(),
                downloading = toolchain.downloadingFile(),
            ),
            unsigned,
        )
        log(StepId.ASSEMBLE, "未签名 APK：${unsigned.length() / 1024 / 1024} MB")
        done(StepId.ASSEMBLE)

        // —— 步骤 4：zipalign -p 4 ——
        enter(StepId.ZIPALIGN)
        val zipalign = toolchain.resolveExecutable("zipalign")
            ?: throw StepException(StepId.ZIPALIGN, "未找到内置 zipalign")
        aligned.delete()
        ProcessRunner.run(
            StepId.ZIPALIGN, StringBuilder(),
            zipalign.absolutePath, "-p", "4",
            unsigned.absolutePath, aligned.absolutePath,
        )
        unsigned.delete()
        done(StepId.ZIPALIGN)

        // —— 步骤 5：apksig 签名 ——
        enter(StepId.SIGN)
        val keystore = Signing.ensureKeyStore(context)
        val safeName = config.appName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val signed = File(outputDir, "$safeName-${config.versionName}.apk")
        Signing.sign(aligned, signed, keystore)
        done(StepId.SIGN)

        // —— 步骤 6：输出 ——
        enter(StepId.INSTALL)
        log(StepId.INSTALL, "产物：${signed.absolutePath}（${signed.length() / 1024 / 1024} MB）")
        done(StepId.INSTALL)
        signed
    }

    companion object {
        /** 通过 FileProvider 调起系统安装器 */
        fun install(context: Context, apk: File) {
            try {
                val uri: Uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", apk
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    context, "无法启动安装器：${e.message}", android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
