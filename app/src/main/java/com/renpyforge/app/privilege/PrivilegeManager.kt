package com.renpyforge.app.privilege

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import rikka.shizuku.Shizuku
import java.io.File

/** 终端 / 安装使用的执行通道 */
enum class ShellMode(val label: String) {
    NORMAL("本机"),
    ROOT("Root"),
    SHIZUKU("Shizuku"),
}

/**
 * 提权能力（可选功能）：
 * · Root：检测 su 并以其执行命令
 * · Shizuku：通过 Shizuku 的 binder（shell 身份）执行命令，无需 root
 *
 * 二者都不可用时，IDE 的普通功能完全不受影响。
 */
object PrivilegeManager {

    const val SHIZUKU_REQUEST_CODE = 4210
    private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"

    var rootAvailable by mutableStateOf(false)
        private set
    var shizukuInstalled by mutableStateOf(false)
        private set
    var shizukuRunning by mutableStateOf(false)
        private set
    var shizukuAuthorized by mutableStateOf(false)
        private set

    val canElevate: Boolean get() = rootAvailable || shizukuAuthorized

    fun refresh(context: Context) {
        rootAvailable = findSu() != null
        shizukuInstalled = runCatching {
            context.packageManager.getPackageInfo(SHIZUKU_PKG, 0)
        }.isSuccess
        shizukuRunning = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        shizukuAuthorized = runCatching {
            shizukuRunning && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    private fun findSu(): String? = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/vendor/bin/su",
        "/odm/bin/su",
        "/debug_ramdisk/su",
    ).firstOrNull { File(it).canExecute() }

    /** 申请 Shizuku 授权；返回 null 表示已弹出系统授权框 */
    fun requestShizuku(activity: Activity): String? {
        refresh(activity)
        if (!shizukuInstalled) return "未安装 Shizuku（包名 $SHIZUKU_PKG）"
        if (!shizukuRunning) return "Shizuku 服务未运行，请先打开 Shizuku 启动服务"
        return runCatching {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                shizukuAuthorized = true
                "已拥有 Shizuku 授权"
            } else {
                Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
                null
            }
        }.getOrElse { "请求失败：${it.message}" }
    }

    private fun createProcess(cmd: String, mode: ShellMode, cwd: File?): Process = when (mode) {
        ShellMode.ROOT -> ProcessBuilder("su", "-c", cmd)
            .apply { cwd?.let { directory(it) } }
            .start()

        ShellMode.SHIZUKU -> newShizukuProcess(
            arrayOf("sh", "-c", cmd),
            arrayOf(
                "PATH=/system/bin:/system/xbin:/vendor/bin",
                "HOME=/",
                "TMPDIR=/data/local/tmp",
            ),
            cwd?.absolutePath ?: "/",
        )

        ShellMode.NORMAL -> ProcessBuilder("sh", "-c", cmd)
            .apply { cwd?.let { directory(it) } }
            .redirectErrorStream(true)
            .start()
    }

    /** Shizuku 的 newProcess 在 API jar 中为 private，通过反射调用（shell 身份） */
    private fun newShizukuProcess(cmd: Array<String>, env: Array<String>, dir: String): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(null, cmd, env, dir) as Process
    }

    /** 同步执行并逐行回调；必须在 IO 线程调用。返回退出码 */
    fun runCommand(
        cmd: String,
        mode: ShellMode,
        cwd: File?,
        onLine: (String) -> Unit,
    ): Int {
        if (mode == ShellMode.ROOT && !rootAvailable) throw IllegalStateException("未检测到 Root（su）")
        if (mode == ShellMode.SHIZUKU && !shizukuAuthorized) throw IllegalStateException("未获得 Shizuku 授权")

        val process = createProcess(cmd, mode, cwd)
        if (mode != ShellMode.NORMAL) {
            // Shizuku / root 的 stderr 不一定合并，单独起线程读
            val err = process.errorStream
            val t = Thread {
                runCatching { err.bufferedReader().useLines { it.forEach(onLine) } }
            }.apply { isDaemon = true; start() }
            process.inputStream.bufferedReader().useLines { it.forEach(onLine) }
            val code = process.waitFor()
            t.join(500)
            return code
        }
        process.inputStream.bufferedReader().useLines { it.forEach(onLine) }
        return process.waitFor()
    }

    /** 静默安装 APK；成功返回 null，失败返回原因 */
    fun installApk(path: String): String? {
        if (!canElevate) return "未获取 Root 或 Shizuku 授权"
        val mode = if (rootAvailable) ShellMode.ROOT else ShellMode.SHIZUKU
        val out = StringBuilder()
        val code = runCatching {
            runCommand("pm install -r -d \"$path\"", mode, null) { out.appendLine(it) }
        }.getOrElse { return it.message ?: it.toString() }
        val text = out.toString()
        return if (code == 0 && text.contains("Success")) null
        else text.ifBlank { "安装失败（退出码 $code）" }
    }
}
