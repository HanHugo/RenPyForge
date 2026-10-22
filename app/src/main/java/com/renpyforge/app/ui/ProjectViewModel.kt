package com.renpyforge.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.renpyforge.app.build.BuildConfig
import com.renpyforge.app.build.BuildPipeline
import com.renpyforge.app.build.StepId
import com.renpyforge.app.build.StepState
import com.renpyforge.app.project.AppStorage
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

    // —— 终端 ——
    val terminalLines = mutableStateListOf<String>()
    var terminalRunning by mutableStateOf(false)
        private set
    var terminalInput by mutableStateOf("")

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

    fun createProject() {
        val name = newProjectName.trim()
        if (name.isEmpty()) return
        try {
            projects.createProject(name)
            openProject(name)
            newProjectName = ""
            statusMessage = "项目「$name」已创建于 $storagePath/projects/$name"
        } catch (e: Exception) {
            statusMessage = e.message
        }
        refresh()
    }

    fun deleteProject(name: String) {
        projects.deleteProject(name)
        if (currentProject == name) closeProject()
        refresh()
    }

    fun openProject(name: String) {
        currentProject = name
        gameFiles = projects.listGameFiles(name)
        packageName = "org.renpy.forge.${
            name.lowercase().replace(Regex("[^a-z0-9]"), "")
                .ifBlank { "game" }
        }"
        appName = name
        statusMessage = "已打开项目：$name"
        // 默认打开主脚本
        val def = gameFiles.firstOrNull { it == "script.rpy" }
            ?: gameFiles.firstOrNull { it.endsWith(".rpy") }
        if (def != null) openGameFile(def)
    }

    fun closeProject() {
        currentProject = null
        openTabs.clear()
        dirtyTabs.clear()
        tabContents.clear()
        activeTab = null
        activeContent = ""
        gameFiles = emptyList()
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
        val rel = if (name.endsWith(".rpy") || name.endsWith(".rpym") || name.contains('/')) name
        else "script/$name.rpy"
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

    // —— 终端 ——
    fun runTerminalCommand(raw: String) {
        val cmd = raw.trim()
        if (cmd.isEmpty() || terminalRunning) return
        terminalRunning = true
        terminalLines.add("renpyforge@device:${terminalCwd().path}$ $cmd")
        terminalInput = ""
        scope.launch(Dispatchers.IO) {
            try {
                val pb = ProcessBuilder("sh", "-c", cmd)
                pb.directory(terminalCwd())
                val env = pb.environment()
                // 把内置工具目录加入 PATH，用户可直接敲 aapt2/zipalign 等（lib*.so 形式的绝对路径也可用）
                val tools = context.applicationInfo.nativeLibraryDir
                env["PATH"] = "$tools:/system/bin:/system/xbin:/vendor/bin"
                env["HOME"] = AppStorage.forgeRoot(context).absolutePath
                env["TERM"] = "dumb"
                env["LANG"] = "en_US.UTF-8"
                env["TMPDIR"] = context.cacheDir.absolutePath
                pb.redirectErrorStream(true)
                val p = pb.start()
                p.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) terminalLines.add(line)
                }
                val code = p.waitFor()
                terminalLines.add(if (code == 0) "[退出码 0]" else "[退出码 $code]")
            } catch (e: Exception) {
                terminalLines.add("[错误] ${e.message}")
            } finally {
                withContext(Dispatchers.Main) { terminalRunning = false }
            }
        }
        if (terminalLines.size > 4000) terminalLines.removeRange(0, 1000)
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
            val pipeline = BuildPipeline(context, project, BuildConfig(
                packageName = packageName.ifBlank { "org.renpy.forge.game" },
                appName = appName.ifBlank { "RenPy Game" },
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
