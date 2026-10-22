package com.renpyforge.app.project

import android.content.Context
import java.io.File

/**
 * 项目管理：项目保存在存储根目录 RenPyForge/projects/<名称>/（见 [AppStorage]）。
 * "新建项目" 从 assets/newproject/ 复制 Ren'Py 模板（the_question 完整骨架）。
 */
class ProjectManager(private val context: Context) {

    private val root: File get() = AppStorage.projectsRoot(context)

    fun listProjects(): List<String> =
        root.listFiles()?.filter { it.isDirectory }?.map { it.name }?.sorted() ?: emptyList()

    fun projectDir(name: String): File = File(root, name)

    fun gameDir(name: String): File = File(projectDir(name), "game").apply { mkdirs() }

    fun exists(name: String): Boolean = projectDir(name).isDirectory

    fun deleteProject(name: String): Boolean {
        // 防御：只允许删除 projects 根目录下的直接子目录
        val dir = projectDir(name)
        if (!dir.absolutePath.startsWith(root.absolutePath)) return false
        return dir.deleteRecursively()
    }

    /** 从 assets/newproject/ 模板创建新项目；返回创建的 game/ 目录 */
    fun createProject(name: String): File {
        require(name.isNotBlank()) { "项目名不能为空" }
        require(!name.contains('/') && !name.contains('\\')) { "项目名不能包含路径分隔符" }
        require(!exists(name)) { "项目已存在：$name" }
        val dir = projectDir(name)
        val gameDir = gameDir(name)
        copyAssetDir("newproject/game", gameDir)
        return gameDir
    }

    private fun copyAssetDir(assetDir: String, target: File) {
        target.mkdirs()
        val entries = context.assets.list(assetDir) ?: return
        if (entries.isEmpty()) return
        for (entry in entries) {
            val childAsset = "$assetDir/$entry"
            val child = File(target, entry)
            val sub = context.assets.list(childAsset)?.size ?: 0
            if (sub > 0) {
                copyAssetDir(childAsset, child)
            } else {
                context.assets.open(childAsset).use { input ->
                    child.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }

    /** 相对 game/ 的文件列表（供编辑器） */
    fun listGameFiles(name: String): List<String> {
        val base = gameDir(name)
        return base.walkTopDown().filter { it.isFile }
            .map { it.relativeTo(base).invariantSeparatorsPath }
            .toList().sorted()
    }

    fun readFile(name: String, relPath: String): String {
        val f = safeFile(name, relPath) ?: return ""
        return if (f.isFile) f.readText() else ""
    }

    fun writeFile(name: String, relPath: String, content: String) {
        val f = safeFile(name, relPath) ?: throw IllegalArgumentException("非法路径：$relPath")
        f.parentFile?.mkdirs()
        f.writeText(content)
    }

    fun createFile(name: String, relPath: String): Boolean {
        val f = safeFile(name, relPath) ?: return false
        if (f.exists()) return false
        f.parentFile?.mkdirs()
        return f.createNewFile()
    }

    fun deleteFile(name: String, relPath: String): Boolean {
        val f = safeFile(name, relPath) ?: return false
        return f.delete()
    }

    /** 重命名/移动文件（newRel 可以带目录） */
    fun renameFile(name: String, oldRel: String, newRel: String): Boolean {
        val from = safeFile(name, oldRel) ?: return false
        val to = safeFile(name, newRel) ?: return false
        if (!from.isFile || from == to) return false
        to.parentFile?.mkdirs()
        return from.renameTo(to)
    }

    /** 防路径穿越：解析后必须仍在该项目的 game/ 内 */
    private fun safeFile(name: String, relPath: String): File? {
        val base = gameDir(name).canonicalFile
        val f = File(base, relPath).canonicalFile
        return if (f.absolutePath.startsWith(base.absolutePath)) f else null
    }
}
