package com.renpyforge.app.build

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 步骤 6：APK 组装（RAPT 兼容布局，全内置）。
 *
 * 以 aapt2 link 产出的 base.apk 为底（二进制资源 + manifest），追加：
 *  - classes.dex（内置 RAPT Java 模板预编译产物）
 *  - lib/arm64-v8a/librenpython.so（内置官方引擎库）
 *  - assets 加 renpy/common 全部文件（逐级 x- 前缀；按扩展名决定 STORED）
 *  - assets/private.mp3（tar：main.py + renpy + lib/python3.12 + game/，STORED）
 *
 * 注意：游戏文件不再打入 assets —— Ren'Py 在 Android 上从 assets 只加载 .rpyc 且
 * 永不编译 .rpy；脚本必须解包到可写的 files/game/ 才能自动编译、修改才能生效
 * （由 private.mp3 解包完成，见 BuildPipeline）。
 */
object ApkAssembler {

    data class Inputs(
        val baseApk: File,
        val classesDex: File,
        val engineSo: File,
        val commonDir: File,
        val privateTar: File,
        val presplash: File? = null,    // → assets/android-presplash.jpg
        val downloading: File? = null,  // → assets/android-downloading.jpg
    )

    fun assemble(inputs: Inputs, output: File) {
        val existing = HashSet<String>()
        ZipOutputStream(output.outputStream().buffered()).use { zip ->

            // 1) 复制 base.apk 的全部条目（resources.arsc、AndroidManifest.xml、res/*）
            //    注意：aapt2 产物中 resources.arsc 等是 STORED —— 复制 STORED 条目时
            //    必须预计算 size/crc，否则 ZipOutputStream 抛
            //    "STORED entry missing size, compressed size, or crc-32"
            ZipInputStream(inputs.baseApk.inputStream().buffered()).use { zin ->
                var entry = zin.nextEntry
                while (entry != null) {
                    if (entry.name !in existing) {
                        existing += entry.name
                        val bytes = zin.readBytes()
                        val out = ZipEntry(entry.name).apply { method = entry.method }
                        if (entry.method == ZipEntry.STORED) {
                            out.size = bytes.size.toLong()
                            out.crc = CRC32().apply { update(bytes) }.value
                        }
                        zip.putNextEntry(out)
                        zip.write(bytes)
                        zip.closeEntry()
                    }
                    entry = zin.nextEntry
                }
            }

            // 2) classes.dex 与引擎库
            putFile(zip, existing, "classes.dex", inputs.classesDex)
            putFile(zip, existing, "lib/arm64-v8a/librenpython.so", inputs.engineSo)

            // 3) renpy/common/** → assets/x-renpy/x-common/**
            //    （common 全部为 .rpyc，assets 直读无需编译；游戏文件走 private.mp3）
            addTree(zip, existing, inputs.commonDir, "renpy/common")

            // 5) private.mp3（必须 STORED）
            putStored(zip, existing, "assets/private.mp3", inputs.privateTar)

            // 6) presplash（PythonSDLActivity 从 assets 读取）
            inputs.presplash?.takeIf { it.isFile }?.let {
                putStored(zip, existing, "assets/android-presplash.jpg", it)
            }
            inputs.downloading?.takeIf { it.isFile }?.let {
                putStored(zip, existing, "assets/android-downloading.jpg", it)
            }
        }
    }

    /** 递归把 `dir` 下的文件以 "assets/" + xPrefix(prefix/<rel>) 打入 APK */
    private fun addTree(zip: ZipOutputStream, existing: MutableSet<String>, dir: File, prefix: String) {
        if (!dir.isDirectory) throw StepException(StepId.ASSEMBLE, "目录不存在：${dir.absolutePath}")
        dir.walkTopDown().filter { it.isFile }.forEach { file ->
            val rel = file.relativeTo(dir).invariantSeparatorsPath
            val entryName = "assets/" + AssetRules.xPrefix("$prefix/$rel")
            if (entryName in existing) return@forEach
            existing += entryName
            val stored = AssetRules.isNoCompress(entryName)
            val entry = ZipEntry(entryName).apply {
                if (stored) {
                    method = ZipEntry.STORED
                    size = file.length()
                    crc = CRC32().apply { update(file.readBytes()) }.value
                } else {
                    method = ZipEntry.DEFLATED
                }
            }
            zip.putNextEntry(entry)
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    private fun putFile(zip: ZipOutputStream, existing: MutableSet<String>, name: String, file: File) {
        if (name in existing || !file.isFile) return
        existing += name
        val entry = ZipEntry(name).apply { method = ZipEntry.DEFLATED }
        zip.putNextEntry(entry)
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun putStored(zip: ZipOutputStream, existing: MutableSet<String>, name: String, file: File) {
        if (name in existing) return
        existing += name
        // 两遍处理：先计算 CRC，再写入内容（避免整文件驻留内存）
        val crc = CRC32().apply {
            file.inputStream().use { stream ->
                val buf = ByteArray(65536)
                var read: Int
                while (stream.read(buf).also { read = it } > 0) update(buf, 0, read)
            }
        }.value
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = file.length()
            this.crc = crc
        }
        zip.putNextEntry(entry)
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }
}
