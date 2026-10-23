package com.renpyforge.app.build

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.io.File

/**
 * 启动图预处理：PythonSDLActivity 只从 APK 的 assets/android-presplash.jpg 读取启动图，
 * 所以自定义启动图必须在组装阶段替换该文件（而不是写 res/drawable）。
 *
 * 处理：等比缩放到长边不超过 [MAX_SIDE]，透明区域合成白底，输出 JPEG。
 */
object PresplashPrep {

    private const val MAX_SIDE = 1920

    fun prepare(src: File, dst: File): Boolean {
        if (!src.isFile) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val bmp = BitmapFactory.decodeFile(src.absolutePath, opts) ?: return false

        val longSide = maxOf(bmp.width, bmp.height)
        val scale = if (longSide > MAX_SIDE) MAX_SIDE.toFloat() / longSide else 1f
        val w = (bmp.width * scale).toInt().coerceAtLeast(1)
        val h = (bmp.height * scale).toInt().coerceAtLeast(1)

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(
            bmp,
            Rect(0, 0, bmp.width, bmp.height),
            Rect(0, 0, w, h),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        dst.parentFile?.mkdirs()
        dst.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        return true
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width / sample, height / sample) > MAX_SIDE * 2) sample *= 2
        return sample
    }
}
