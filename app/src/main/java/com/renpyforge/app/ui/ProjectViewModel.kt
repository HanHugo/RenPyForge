package com.renpyforge.app.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.renpyforge.app.build.BuildConfig
import com.renpyforge.app.build.BuildPipeline
import com.renpyforge.app.build.StepId
import com.renpyforge.app.build.StepState
import com.renpyforge.app.privilege.PrivilegeManager
import com.renpyforge.app.privilege.ShellMode
import com.renpyforge.app.project.AppStorage
import com.renpyforge.app.project.ProjectConfig
import com.renpyforge.app.project.ProjectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** UI 状态容器：项目管理、编辑器、终端、构建控制台 */
class ProjectViewModel(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val projects = ProjectManager(context)

    // —— 存储 ——
    var storageGranted by mutableStateOf(AppStorage.hasAllFilesAccess(context))
        private set
    var storagePath by mutableStateOf(AppStorage.storageLocationLabel(context))
        private set

    fun onStorageMaybeChanged() {
        val was = storageGranted
        storageGranted = AppStorage.hasAllFilesAccess(context)
        storagePath = AppStorage.storageLocationLabel(context)
        if (was != storageGranted) refresh()
    }

    // —— 项目 ——
    var projectList by mutableStateOf<List<String>>(emptyList())
        private set
    var currentProject by mutableStateOf<String?>(null)
        private set
    var newProjectName by mutableStateOf("")
    var statusMessage by mutableStateOf<String?>(null)

    /** 当前项目的构建配置（包名 / 应用名 / 图标 / 启动图） */
    var projectConfig by mutableStateOf<ProjectConfig?>(null)
        private set

    // —— 编辑器（多标签） ——
    var gameFiles by mutableStateOf<List<String>>(emptyList())
        private set
    val openTabs = mutableStateListOf<String>()
    var activeTab by mutableStateOf<String?>(null)
        private set
    var activeContent by mutableStateOf("")
        private set
    val dirtyTabs = mutableStateListOf<String>()
    var cursorLine by mutableStateOf(1)
        private set
    var cursorCol by mutableStateOf(1)
        private set

    /** 编辑器字号缩放（双指或菜单调整） */
    var editorFontScale by mutableStateOf(1f)
        private set

    fun changeEditorFontScale(scale: Float) {
        editorFontScale = scale.coerceIn(0.6f, 2.4f)
    }

    private val tabContents = HashMap<String, String>()

    fun onCursorMoved(line: Int, col: Int) {
        cursorLine = line
        cursorCol = col
    }

    // —— 语法检查（防抖） ——
    data class Problem(val line: Int, val message: String)
    val problems = mutableStateListOf<Problem>()
    var jumpRequest by mutableStateOf<Pair<Int, Int>?>(null)   // (line, col) 让编辑器跳转
        private set

    private var checkJob: kotlinx.coroutines.Job? = null

    fun requestJump(line: Int, col: Int) {
        jumpRequest = line to col
    }

    fun consumeJump() {
        jumpRequest = null
    }

    private fun scheduleSyntaxCheck(path: String, text: String) {
        checkJob?.cancel()
        checkJob = scope.launch {
            kotlinx.coroutines.delay(500)
            val found = withContext(Dispatchers.Default) {
                com.renpyforge.app.project.RpyCheck.check(text)
            }
            // 仅当仍在查看同一文件时更新
            if (activeTab == path) {
                problems.clear()
                problems.addAll(found.map { Problem(it.line, it.message) })
            }
        }
    }

    fun refreshProblems() {
        val path = activeTab ?: return
        scheduleSyntaxCheck(path, activeContent)
    }

    // —— 资源浏览 ——
    var resourceImages by mutableStateOf<List<Pair<File, String>>>(emptyList())  // (file, 相对 game/ 的路径)
        private set
    var resourceAudio by mutableStateOf<List<Pair<File, String>>>(emptyList())
        private set

    fun refreshResources() {
        val project = currentProject ?: return
        scope.launch(Dispatchers.IO) {
            val base = projects.gameDir(project)
            val imgDir = java.io.File(base, "images")
            val audDir = java.io.File(base, "audio")
            val imgs = imgDir.walkTopDown().filter { it.isFile }
                .map { it to it.relativeTo(base).invariantSeparatorsPath }.toList()
            val auds = audDir.walkTopDown().filter { it.isFile }
                .map { it to it.relativeTo(base).invariantSeparatorsPath }.toList()
            withContext(Dispatchers.Main) {
                resourceImages = imgs
                resourceAudio = auds
            }
        }
    }

    // —— 构建历史 ——
    data class BuildArtifact(val file: File, val sizeMb: Int, val modified: Long)
    var buildHistory by mutableStateOf<List<BuildArtifact>>(emptyList())
        private set

    fun refreshBuildHistory() {
        val project = currentProject ?: return
        scope.launch(Dispatchers.IO) {
            val outDir = java.io.File(
                com.renpyforge.app.project.AppStorage.forgeRoot(context), "output"
            )
            val list = outDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".apk") }
                ?.sortedByDescending { it.lastModified() }
                ?.map { BuildArtifact(it, (it.length() / 1024 / 1024).toInt(), it.lastModified()) }
                ?: emptyList()
            withContext(Dispatchers.Main) { buildHistory = list }
        }
    }

    fun deleteArtifact(f: File) {
        scope.launch(Dispatchers.IO) {
            f.delete()
            refreshBuildHistory()
        }
    }

    // —— 脚本大纲 ——
    data class OutlineItem(val kind: String, val name: String, val line: Int)

    fun outline(): List<OutlineItem> {
        val regex = Regex("""^\s*(label|screen|define|default|transform|style|image)\s+([\w.]+)""")
        return activeContent.lineSequence().mapIndexed { idx, line ->
            val m = regex.find(line)
            if (m != null) OutlineItem(m.groupValues[1], m.groupValues[2], idx + 1) else null
        }.filterNotNull().toList()
    }

    // —— 终端 ——
    val terminalLines = mutableStateListOf<String>()
    var terminalRunning by mutableStateOf(false)
        private set
    var terminalInput by mutableStateOf("")
    var shellMode by mutableStateOf(ShellMode.NORMAL)

    // —— 构建 ——
    var packageName by mutableStateOf("org.renpy.forge.game")
    var appName by mutableStateOf("RenPy Game")
    val stepStates = mutableStateListOf<Pair<StepId, StepState>>().apply {
        StepId.entries.forEach { add(it to StepState.PENDING) }
    }
    val console = mutableStateListOf<String>()
    var building by mutableStateOf(false)
        private set
    var builtApk by mutableStateOf<File?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun refresh() {
        projectList = projects.listProjects()
    }

    /** @return true 表示创建成功（供对话框判断是否关闭） */
    fun createProject(): Boolean {
        val name = newProjectName.trim()
        if (name.isEmpty()) {
            statusMessage = "请输入项目名称"
            return false
        }
        return try {
            projects.createProject(name)
            openProject(name)
            newProjectName = ""
            statusMessage = "项目「$name」已创建于 $storagePath/projects/$name"
            refresh()
            true
        } catch (e: Exception) {
            statusMessage = e.message ?: "创建失败"
            refresh()
            false
        }
    }

    fun deleteProject(name: String) {
        projects.deleteProject(name)
        if (currentProject == name) closeProject()
        refresh()
    }

    fun openProject(name: String) {
        currentProject = name
        gameFiles = projects.listGameFiles(name)
        val cfg = projects.readConfig(name)
        projectConfig = cfg
        packageName = cfg.packageName
        appName = cfg.appName
        statusMessage = "已打开项目：$name"
        // 默认打开主脚本
        val def = gameFiles.firstOrNull { it == "script.rpy" }
            ?: gameFiles.firstOrNull { it.endsWith(".rpy") }
        if (def != null) openGameFile(def)
    }

    fun closeProject() {
        currentProject = null
        projectConfig = null
        openTabs.clear()
        dirtyTabs.clear()
        tabContents.clear()
        activeTab = null
        activeContent = ""
        gameFiles = emptyList()
    }

    // —— 项目配置（包名 / 应用名 / 图标 / 启动图） ——

    fun saveProjectConfig(pkg: String, name: String) {
        val project = currentProject ?: return
        val def = ProjectConfig.defaults(project)
        val cfg = (projectConfig ?: projects.readConfig(project)).copy(
            packageName = pkg.trim().ifBlank { def.packageName },
            appName = name.trim().ifBlank { project },
        )
        projects.writeConfig(project, cfg)
        projectConfig = cfg
        packageName = cfg.packageName
        appName = cfg.appName
        statusMessage = "项目信息已保存"
    }

    /** 导入自定义图标（kind = "icon"）或启动图（kind = "splash"） */
    fun importProjectImage(kind: String, uri: Uri) {
        val project = currentProject ?: return
        val fileName = if (kind == "icon") "icon.img" else "splash.img"
        scope.launch(Dispatchers.IO) {
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("无法读取所选图片")
                projects.saveAsset(project, fileName, input)
                val rel = "assets/$fileName"
                val cur = projectConfig ?: projects.readConfig(project)
                val cfg = if (kind == "icon") cur.copy(iconFile = rel) else cur.copy(splashFile = rel)
                projects.writeConfig(project, cfg)
                withContext(Dispatchers.Main) {
                    projectConfig = cfg
                    statusMessage = if (kind == "icon") "图标已更新" else "启动图已更新"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { statusMessage = "导入失败：${e.message}" }
            }
        }
    }

    fun removeProjectImage(kind: String) {
        val project = currentProject ?: return
        val fileName = if (kind == "icon") "icon.img" else "splash.img"
        projects.assetFile(project, fileName).delete()
        val cur = projectConfig ?: return
        val cfg = if (kind == "icon") cur.copy(iconFile = null) else cur.copy(splashFile = null)
        projects.writeConfig(project, cfg)
        projectConfig = cfg
    }

    /** 当前项目已选择的图片文件（不存在返回 null） */
    fun projectImage(kind: String): File? {
        val project = currentProject ?: return null
        val rel = if (kind == "icon") projectConfig?.iconFile else projectConfig?.splashFile
        return rel?.let { File(projects.projectDir(project), it) }?.takeIf { it.isFile }
    }

    /** 导入图片 / 音频到 game/images 或 game/audio（kind = "image" / "audio"） */
    fun importGameAsset(kind: String, uri: Uri) {
        val project = currentProject ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val dir = if (kind == "audio") "audio" else "images"
                val name = importFileName(uri, if (kind == "audio") "audio" else "image")
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("无法读取所选文件")
                val rel = "$dir/${uniqueGameName(project, dir, name)}"
                projects.writeBinary(project, rel, input)
                withContext(Dispatchers.Main) {
                    gameFiles = projects.listGameFiles(project)
                    refreshResources()
                    toast("已导入 $rel")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast("导入失败：${e.message}") }
            }
        }
    }

    /** 读取 content:// 的展示文件名，并按 MIME 补齐扩展名 */
    private fun importFileName(uri: Uri, fallbackBase: String): String {
        var name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
        if (name.isNullOrBlank()) name = "${fallbackBase}_${System.currentTimeMillis()}"
        name = name.replace(Regex("""[\\/:*?"<>|]"""), "_")
        if (!name.contains('.')) {
            val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
            val ext = mime?.let { android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            if (!ext.isNullOrBlank()) name = "$name.$ext"
        }
        return name
    }

    /** 同名文件自动追加 -1、-2… */
    private fun uniqueGameName(project: String, dir: String, name: String): String {
        val baseDir = File(projects.gameDir(project), dir)
        if (!File(baseDir, name).exists()) return name
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (true) {
            val candidate = "$stem-$i$ext"
            if (!File(baseDir, candidate).exists()) return candidate
            i++
        }
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    // —— 安装 ——

    /**
     * 安装 APK：有 Root / Shizuku 时静默安装，否则回落到系统安装器。
     */
    fun install(file: File) {
        if (!PrivilegeManager.canElevate) {
            com.renpyforge.app.build.BuildPipeline.install(context, file)
            toast("已打开系统安装器")
            return
        }
        scope.launch(Dispatchers.IO) {
            val err = PrivilegeManager.installApk(file.absolutePath)
            withContext(Dispatchers.Main) {
                if (err == null) {
                    toast("已静默安装 ${file.name}")
                } else {
                    toast("静默安装失败，改用系统安装器")
                    com.renpyforge.app.build.BuildPipeline.install(context, file)
                }
            }
        }
    }

    // —— 编辑器 ——
    fun openGameFile(relPath: String) {
        val project = currentProject ?: return
        if (relPath !in openTabs) {
            openTabs.add(relPath)
            tabContents[relPath] = projects.readFile(project, relPath)
        }
        activateTab(relPath)
    }

    fun activateTab(relPath: String) {
        activeTab = relPath
        activeContent = tabContents[relPath] ?: ""
    }

    fun closeTab(relPath: String) {
        val idx = openTabs.indexOf(relPath)
        if (dirtyTabs.contains(relPath)) saveTabNow(relPath)
        openTabs.remove(relPath)
        dirtyTabs.remove(relPath)
        tabContents.remove(relPath)
        when {
            openTabs.isEmpty() -> { activeTab = null; activeContent = "" }
            idx >= 0 -> activateTab(openTabs[(idx - 1).coerceAtLeast(0)])
            else -> {}
        }
    }

    fun onEditorChange(text: String) {
        val path = activeTab ?: return
        tabContents[path] = text
        activeContent = text
        if (!dirtyTabs.contains(path)) dirtyTabs.add(path)
        scheduleSyntaxCheck(path, text)
    }

    fun saveActiveTab() {
        val path = activeTab ?: return
        saveTabNow(path)
    }

    private fun saveTabNow(relPath: String) {
        val project = currentProject ?: return
        val content = tabContents[relPath] ?: return
        dirtyTabs.remove(relPath)
        scope.launch(Dispatchers.IO) {
            projects.writeFile(project, relPath, content)
            withContext(Dispatchers.Main) {
                gameFiles = projects.listGameFiles(project)
            }
        }
    }

    fun saveAllTabs() {
        dirtyTabs.toList().forEach { saveTabNow(it) }
    }

    fun newGameFile(name: String) {
        val project = currentProject ?: return
        val clean = name.trim().replace('\\', '/').trimStart('/')
        if (clean.isEmpty()) return
        val rel = when {
            clean.contains('/') -> clean                                  // 用户指定了路径，原样使用
            clean.endsWith(".rpy") || clean.endsWith(".rpym") -> "script/$clean"
            clean.contains('.') -> clean                                  // 其他扩展名（png/jpg/ogg…）按原名放 game/ 下
            else -> "script/$clean.rpy"
        }
        projects.createFile(project, rel)
        gameFiles = projects.listGameFiles(project)
        openGameFile(rel)
    }

    fun deleteGameFile(relPath: String) {
        val project = currentProject ?: return
        projects.deleteFile(project, relPath)
        closeTab(relPath)
        gameFiles = projects.listGameFiles(project)
    }

    /** 重命名/移动脚本（newName 可含相对路径）；同步更新打开的标签与缓存 */
    fun renameGameFile(oldRel: String, newName: String) {
        val project = currentProject ?: return
        val trimmed = newName.trim().replace('\\', '/').trim('/')
        if (trimmed.isEmpty()) return
        // 只有在没有任何扩展名时才自动补 .rpy，避免图片等被强加后缀
        val base = if (trimmed.contains('.')) trimmed else "$trimmed.rpy"
        val newRel = if (oldRel.contains('/')) {
            oldRel.substringBeforeLast('/') + "/" + base
        } else {
            base
        }
        val normalized = newRel.split('/').filter { it.isNotEmpty() }.joinToString("/")
        scope.launch(Dispatchers.IO) {
            val ok = projects.renameFile(project, oldRel, normalized)
            if (!ok) return@launch
            withContext(Dispatchers.Main) {
                val idx = openTabs.indexOf(oldRel)
                if (idx >= 0) openTabs[idx] = normalized
                tabContents.remove(oldRel)?.let { tabContents[normalized] = it }
                if (dirtyTabs.contains(oldRel)) {
                    dirtyTabs[dirtyTabs.indexOf(oldRel)] = normalized
                }
                if (activeTab == oldRel) {
                    activeTab = normalized
                    activeContent = tabContents[normalized] ?: ""
                }
                gameFiles = projects.listGameFiles(project)
                statusMessage = "已重命名为 $normalized"
            }
        }
    }

    // —— 全局搜索 ——
    data class SearchHit(val path: String, val line: Int, val text: String)
    val searchResults = mutableStateListOf<SearchHit>()
    var searching by mutableStateOf(false)
        private set

    fun globalSearch(query: String) {
        val q = query.trim()
        searchResults.clear()
        if (q.isEmpty() || currentProject == null) return
        searching = true
        scope.launch(Dispatchers.IO) {
            val hits = mutableListOf<SearchHit>()
            for (path in gameFiles) {
                val text = tabContents[path] ?: runCatching { projects.readFile(currentProject!!, path) }.getOrDefault("")
                if (text.isEmpty()) continue
                var lineNo = 0
                for (rawLine in text.lineSequence()) {
                    lineNo++
                    val i = rawLine.indexOf(q, ignoreCase = true)
                    if (i >= 0) {
                        hits.add(SearchHit(path, lineNo, rawLine.trim().take(120)))
                        if (hits.size >= 300) break
                    }
                }
                if (hits.size >= 300) break
            }
            withContext(Dispatchers.Main) {
                searchResults.clear()
                searchResults.addAll(hits)
                searching = false
            }
        }
    }

    // —— 终端 ——
    fun runTerminalCommand(raw: String) {
        val cmd = raw.trim()
        if (cmd.isEmpty() || terminalRunning) return
        val mode = shellMode
        if (mode == ShellMode.ROOT && !PrivilegeManager.rootAvailable) {
            terminalLines.add("[错误] 未检测到 Root（su）")
            return
        }
        if (mode == ShellMode.SHIZUKU && !PrivilegeManager.shizukuAuthorized) {
            terminalLines.add("[错误] 未获得 Shizuku 授权，请到「设置 → 高级权限」申请")
            return
        }
        val cwd = terminalCwd()
        terminalRunning = true
        terminalLines.add("${promptPrefix(mode)}${cwd.path} $ $cmd")
        terminalInput = ""
        scope.launch(Dispatchers.IO) {
            try {
                val code = if (mode == ShellMode.NORMAL) {
                    normalExec(cmd, cwd) { terminalLines.add(it) }
                } else {
                    PrivilegeManager.runCommand(cmd, mode, cwd) { terminalLines.add(it) }
                }
                terminalLines.add(if (code == 0) "[退出码 0]" else "[退出码 $code]")
            } catch (e: Exception) {
                terminalLines.add("[错误] ${e.message}")
            } finally {
                withContext(Dispatchers.Main) { terminalRunning = false }
            }
        }
        if (terminalLines.size > 4000) terminalLines.removeRange(0, 1000)
    }

    private fun promptPrefix(mode: ShellMode): String = when (mode) {
        ShellMode.NORMAL -> "renpyforge@device:"
        ShellMode.ROOT -> "root@device:"
        ShellMode.SHIZUKU -> "shell(shizuku)@device:"
    }

    private fun normalExec(cmd: String, cwd: File, onLine: (String) -> Unit): Int {
        val pb = ProcessBuilder("sh", "-c", cmd)
        pb.directory(cwd)
        val env = pb.environment()
        // 把内置工具目录加入 PATH，用户可直接敲 aapt2/zipalign 等
        val tools = context.applicationInfo.nativeLibraryDir
        env["PATH"] = "$tools:/system/bin:/system/xbin:/vendor/bin"
        env["HOME"] = AppStorage.forgeRoot(context).absolutePath
        env["TERM"] = "dumb"
        env["LANG"] = "en_US.UTF-8"
        env["TMPDIR"] = context.cacheDir.absolutePath
        pb.redirectErrorStream(true)
        val p = pb.start()
        p.inputStream.bufferedReader().useLines { lines -> lines.forEach(onLine) }
        return p.waitFor()
    }

    private fun terminalCwd(): File {
        val project = currentProject
        return if (project != null) {
            val d = File(AppStorage.forgeRoot(context), "projects/$project")
            if (d.isDirectory) d else AppStorage.forgeRoot(context)
        } else {
            AppStorage.forgeRoot(context)
        }
    }

    // —— 构建 ——
    fun startBuild() {
        val project = currentProject ?: return
        if (building) return
        building = true
        error = null
        builtApk = null
        StepId.entries.forEachIndexed { i, id -> stepStates[i] = id to StepState.PENDING }
        console.clear()

        scope.launch {
            val cfg = projectConfig ?: projects.readConfig(project)
            val def = ProjectConfig.defaults(project)
            val pipeline = BuildPipeline(context, project, BuildConfig(
                packageName = cfg.packageName.ifBlank { def.packageName },
                appName = cfg.appName.ifBlank { project },
                iconFile = cfg.iconFile?.let { File(projects.projectDir(project), it) }?.takeIf { it.isFile },
                splashFile = cfg.splashFile?.let { File(projects.projectDir(project), it) }?.takeIf { it.isFile },
            )) { step, state, message ->
                val idx = StepId.entries.indexOf(step)
                stepStates[idx] = step to state
                if (message.isNotBlank()) consoleLine(message)
            }
            try {
                val apk = pipeline.run()
                builtApk = apk
                refreshBuildHistory()
            } catch (e: Exception) {
                error = e.message ?: e.toString()
                consoleLine("✘ 构建失败：$error")
            } finally {
                building = false
            }
        }
    }

    private fun consoleLine(line: String) {
        console.add(line)
        if (console.size > 2000) console.removeRange(0, 500)
    }

    fun consoleText(): String = console.joinToString("\n")

    fun clearConsole() = console.clear()

    fun terminalText(): String = terminalLines.joinToString("\n")

    fun clearTerminal() = terminalLines.clear()
}
