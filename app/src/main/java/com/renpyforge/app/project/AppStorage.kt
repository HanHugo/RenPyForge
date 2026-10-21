package com.renpyforge.app.project

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File

/**
 * 存储布局：项目直接放在设备存储根目录 /storage/emulated/0/RenPyForge/projects/<项目名>/，
 * 用户可以用任何文件管理器自由操作（"大权利"）。
 * Android 11+ 需要 MANAGE_EXTERNAL_STORAGE（所有文件访问）；
 * 未授权时回退到 App 私有目录并在 UI 上提示。
 */
object AppStorage {

    const val FORGE_DIR = "RenPyForge"

    fun hasAllFilesAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            @Suppress("DEPRECATION")
            android.content.pm.PackageManager.PERMISSION_GRANTED ==
                context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

    /** 存储根目录（受权限影响） */
    fun forgeRoot(context: Context): File =
        if (hasAllFilesAccess(context)) {
            File(Environment.getExternalStorageDirectory(), FORGE_DIR)
        } else {
            File(context.filesDir, FORGE_DIR)
        }

    fun projectsRoot(context: Context): File =
        File(forgeRoot(context), "projects").apply { mkdirs() }

    fun outputRoot(context: Context): File =
        File(forgeRoot(context), "output").apply { mkdirs() }

    fun storageLocationLabel(context: Context): String = forgeRoot(context).absolutePath
}
