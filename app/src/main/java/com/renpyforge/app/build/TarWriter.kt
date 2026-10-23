package com.renpyforge.app.build

import java.io.File
import java.io.OutputStream
import java.nio.charset.Charset

/**
 * 最小 ustar (tar) 写入器 —— 用于生成 assets/private.mp3。
 *
 * 与 RAPT 的 make_tar 一致：**gzip 压缩的 ustar**（调用方需包一层 GZIPOutputStream），
 * 且必须包含目录条目 —— AssetExtract.java 对文件条目不做 mkdirs，
 * 缺父目录条目会导致 FileOutputStream 直接失败。
 */
class TarWriter(private val out: OutputStream) : java.io.Closeable {

    fun addFile(entryName: String, file: File) {
        file.inputStream().use { addEntry(entryName, it, file.length(), typeFlag = '0') }
    }

    /** 目录条目：名字以 / 结尾，size 0，typeflag '5' */
    fun addDirectory(entryName: String) {
        val name = if (entryName.endsWith("/")) entryName else "$entryName/"
        addEntry(name, java.io.ByteArrayInputStream(ByteArray(0)), 0, typeFlag = '5')
    }

    private fun addEntry(name: String, input: java.io.InputStream, size: Long, typeFlag: Char) {
        val header = ByteArray(512)
        put(header, 0, 100, name.toByteArray(Charset.forName("UTF-8")))
        val mode = if (typeFlag == '5') "0000755\u0000" else "0000644\u0000"
        put(header, 100, 8, mode.toByteArray())                 // mode
        put(header, 108, 8, "0000000\u0000".toByteArray())      // uid
        put(header, 116, 8, "0000000\u0000".toByteArray())      // gid
        put(header, 124, 12, octal(size, 11).toByteArray())     // size（ustar 标准偏移 124）
        put(header, 136, 12, octal(System.currentTimeMillis() / 1000, 11).toByteArray()) // mtime
        put(header, 148, 8, "        ".toByteArray())           // checksum placeholder
        put(header, 156, 1, typeFlag.toString().toByteArray())  // typeflag
        put(header, 257, 6, "ustar\u0000".toByteArray())
        put(header, 263, 2, "00".toByteArray())

        var sum = 0L
        for (b in header) sum += b.toLong() and 0xFF
        // 标准格式：6 位八进制 + NUL + 空格，共 8 字节
        put(header, 148, 8, (octal(sum, 7) + " ").toByteArray())

        out.write(header)
        if (size > 0) {
            input.copyTo(out)
            val pad = ((512 - (size % 512)) % 512).toInt()
            if (pad > 0) out.write(ByteArray(pad))
        }
    }

    fun finish() {
        out.write(ByteArray(1024)) // 结束块
        out.flush()
    }

    override fun close() {
        finish()
        out.close()
    }

    private fun octal(v: Long, width: Int): String {
        // width 包含结尾 NUL；八进制数字占 width-1
        return "%0${width - 1}o".format(v) + "\u0000"
    }

    private fun put(buf: ByteArray, offset: Int, length: Int, data: ByteArray) {
        System.arraycopy(data, 0, buf, offset, data.size.coerceAtMost(length))
    }
}
