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

    // —— 编辑器 ——
    var gameFiles by mutableStateOf<List<String>>(emptyList())
        private set
    var openFile by mutableStateOf<String?>(null)
        private set
    var editorContent by mutableStateOf("")
    var editorDirty by mutableStateOf(false)
        private set

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
    }

    fun closeProject() {
        currentProject = null
        openFile = null
        gameFiles = emptyList()
    }

    // —— 编辑器 ——
    fun openGameFile(relPath: String) {
        openFile = relPath
        editorContent = projects.readFile(currentProject!!, relPath)
        editorDirty = false
    }

    fun onEditorChange(text: String) {
        editorContent = text
        editorDirty = true
    }

    fun saveEditor() {
        val project = currentProject ?: return
        val path = openFile ?: return
        scope.launch(Dispatchers.IO) {
            projects.writeFile(project, path, editorContent)
            withContext(Dispatchers.Main) {
                editorDirty = false
                gameFiles = projects.listGameFiles(project)
            }
        }
    }

    fun newGameFile(name: String) {
        val project = currentProject ?: return
        val rel = if (name.endsWith(".rpy") || name.endsWith(".rpym")) name else "$name.rpy"
        projects.createFile(project, rel)
        gameFiles = projects.listGameFiles(project)
        openGameFile(rel)
    }

    fun deleteGameFile(relPath: String) {
        val project = currentProject ?: return
        projects.deleteFile(project, relPath)
        if (openFile == relPath) { openFile = null; editorContent = "" }
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
