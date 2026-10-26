package com.renpyforge.app.build

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.io.File

/**
 * 步骤 5：aapt2 资源编译与链接（全内置版）。
 *
 * 输入：
 *  - assets/templates/AndroidManifest.template（RAPT app-AndroidManifest.xml）
 *  - assets/templates/app-strings.xml（渲染后生成 values/strings.xml）
 *  - assets/templates/game-res/（图标、styles、backup 等静态资源）
 *  - assets/tools/android.jar（API 36）
 * 输出：base.apk（含二进制资源 + manifest）
 */
object Aapt2Step {

    fun run(toolchain: ToolchainManager, config: BuildConfig, privateVersion: String, log: StringBuilder): File {
        val aapt2 = toolchain.resolveExecutable("aapt2")
            ?: throw StepException(StepId.AAPT2, "未找到内置 aapt2（nativeLibraryDir/libaapt2.so）")
        val ctx = toolchain.appContext()
        val work = File(toolchain.workDir(), "aapt2-work").apply {
            deleteRecursively(); mkdirs()
        }

        // 1) 准备 res：静态资源 + 渲染后的 strings.xml + presplash
        val resDir = File(work, "res")
        resDir.mkdirs()
        val staticRes = toolchain.releaseDir("templates/game-res", "game-res")
        staticRes.copyRecursively(resDir, overwrite = true)
        // 双保险：manifest 引用 @xml/file_paths，缺失会导致 link 失败
        val filePaths = File(resDir, "xml/file_paths.xml")
        if (!filePaths.isFile) {
            filePaths.parentFile?.mkdirs()
            filePaths.writeText(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<paths xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                    "    <external-path name=\"files\" path=\"/\" />\n" +
                    "</paths>\n"
            )
        }

        val stringsTemplate = ctx.assets.open("templates/app-strings.xml")
            .bufferedReader().use { it.readText() }
        val strings = TemplateRenderer.render(
            stringsTemplate,
            mapOf(
                "config.name" to config.appName,
                "config.icon_name" to config.appName,
                // 关键：游戏端以此与 files/private.version 比对决定是否重新解包 private.mp3。
                // 必须包含游戏文件哈希（由 BuildPipeline 传入），否则改了代码不会重新解包。
                "private_version" to privateVersion,
            ),
        )
        File(resDir, "values").mkdirs()
        File(resDir, "values/strings.xml").writeText(strings)

        // 自定义图标（覆盖模板 mipmap）
        // 注：启动图不在这里处理——它由 BuildPipeline 写入 assets/android-presplash.jpg
        config.iconFile?.let { applyCustomIcon(resDir, it, log) }

        // 2) 渲染 manifest（注入 package 属性 + 配置项）
        val manifestTemplate = ctx.assets.open("templates/AndroidManifest.template")
            .bufferedReader().use { it.readText() }
        val rendered = TemplateRenderer.render(
            manifestTemplate,
            mapOf(
                "config.package" to config.packageName,
                "config.orientation" to config.orientation,
                "config.name" to config.appName,
                "config.icon_name" to config.appName,
                "config.version" to config.versionName,
                "config.numeric_version" to config.versionCode.toString(),
                "config.store" to "none",
                "config.permissions" to "",
                "manifest_extra" to "",
                "private_version" to "",
            ),
        ).replace(
            "<manifest xmlns:android",
            "<manifest package=\"${config.packageName}\" xmlns:android",
        )
            // 显式要求解压 native libs（librenpython.so 以 DEFLATED 写入）
            .replace("<application", "<application\n      android:extractNativeLibs=\"true\"")
            // renpyandroid 模块的补充权限（VIBRATE）
            .replace(
                "<uses-permission android:name=\"android.permission.WAKE_LOCK\" />",
                "<uses-permission android:name=\"android.permission.WAKE_LOCK\" />\n" +
                    "    <uses-permission android:name=\"android.permission.VIBRATE\" />",
            )
        val manifest = File(work, "AndroidManifest.xml")
        manifest.writeText(rendered)

        // 3) compile + link
        val compiled = File(work, "res.zip")
        val linked = File(work, "base.apk")
        ProcessRunner.run(
            StepId.AAPT2, log,
            aapt2.absolutePath, "compile", "--dir", resDir.absolutePath, "-o", compiled.absolutePath,
        )
        ProcessRunner.run(
            StepId.AAPT2, log,
            aapt2.absolutePath, "link",
            "-o", linked.absolutePath,
            "-I", toolchain.androidJar().absolutePath,
            "--manifest", manifest.absolutePath,
            "--min-sdk-version", "21",
            "--target-sdk-version", "36",
            "--version-code", config.versionCode.toString(),
            "--version-name", config.versionName,
            "--auto-add-overlay",
            compiled.absolutePath,
        )
        return linked
    }

    /** 自定义图标：居中裁成方形 512px，覆盖 mipmap/icon.png，并移除自适应图标避免旧图生效 */
    private fun applyCustomIcon(resDir: File, src: File, log: StringBuilder) {
        try {
            if (!src.isFile) return
            val bmp = BitmapFactory.decodeFile(src.absolutePath) ?: run {
                log.appendLine("图标解码失败，沿用默认图标")
                return
            }
            val size = 512
            val side = minOf(bmp.width, bmp.height).toFloat().coerceAtLeast(1f)
            val scale = size / side
            val w = bmp.width * scale
            val h = bmp.height * scale
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.drawBitmap(
                bmp, null,
                RectF((size - w) / 2f, (size - h) / 2f, (size + w) / 2f, (size + h) / 2f),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
            File(resDir, "mipmap").mkdirs()
            File(resDir, "mipmap/icon.png").outputStream().use {
                out.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            // 自适应图标（v26）优先级高于普通 mipmap，删掉才能让自定义图标生效
            File(resDir, "mipmap-anydpi-v26/icon.xml").delete()
            log.appendLine("已应用自定义图标（512×512）")
        } catch (e: Exception) {
            log.appendLine("应用自定义图标失败：${e.message}")
        }
    }
}
