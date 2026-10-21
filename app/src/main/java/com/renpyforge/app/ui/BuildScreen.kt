@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.renpyforge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renpyforge.app.build.StepId
import com.renpyforge.app.build.StepState
import java.io.File

private val OkGreen = Color(0xFF4CAF50)
private val RunBlue = Color(0xFF42A5F5)
private val WarnOrange = Color(0xFFFFA726)
private val FailRed = Color(0xFFEF5350)
private val ConsoleBg = Color(0xFF101418)
private val ConsoleFg = Color(0xFFB0BEC5)

private fun stateColor(s: StepState): Color = when (s) {
    StepState.PENDING -> Color.Gray
    StepState.RUNNING -> RunBlue
    StepState.OK -> OkGreen
    StepState.FAILED -> FailRed
    StepState.SKIPPED -> WarnOrange
}

private fun stateLabel(s: StepState): String = when (s) {
    StepState.PENDING -> "等待"
    StepState.RUNNING -> "进行中"
    StepState.OK -> "完成"
    StepState.FAILED -> "失败"
    StepState.SKIPPED -> "跳过"
}

private fun stateIcon(s: StepState): String = when (s) {
    StepState.PENDING -> "○"
    StepState.RUNNING -> "◐"
    StepState.OK -> "●"
    StepState.FAILED -> "✕"
    StepState.SKIPPED -> "—"
}

@Composable
fun BuildScreen(
    viewModel: ProjectViewModel,
    onInstall: (File) -> Unit,
    onRequestStorage: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("RenPyForge", fontWeight = FontWeight.Bold)
                        Text(
                            viewModel.currentProject ?: "未打开项目",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                val items = listOf(
                    Triple("项目", Icons.Filled.Home, 0),
                    Triple("编辑", Icons.Filled.Edit, 1),
                    Triple("终端", Icons.Filled.List, 2),
                    Triple("构建", Icons.Filled.Build, 3),
                )
                items.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (tab) {
                0 -> ProjectsPane(viewModel, onRequestStorage)
                1 -> EditorPane(viewModel)
                2 -> TerminalPane(viewModel)
                3 -> BuildPane(viewModel, onInstall)
            }
        }
    }
}

// ───────────────── 项目 ─────────────────

@Composable
private fun ProjectsPane(viewModel: ProjectViewModel, onRequestStorage: () -> Unit) {
    var showCreate by remember { mutableStateOf(false) }

    if (!viewModel.storageGranted) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("需要「所有文件访问」权限", fontWeight = FontWeight.Bold)
                Text(
                    "授权后项目保存在存储根目录 RenPyForge/ 下，文件管理器可直接访问。未授权时暂存于应用私有目录。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onRequestStorage) { Text("去授权") }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (viewModel.storageGranted) {
            Text(
                "工作目录：${viewModel.storagePath}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        Button(
            onClick = { showCreate = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) { Text("＋ 新建项目（官方示例游戏模板）") }

        Text(
            "全部项目（${viewModel.projectList.size}）",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )

        LazyColumn {
            items(viewModel.projectList) { name ->
                val active = name == viewModel.currentProject
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (active) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    onClick = { viewModel.openProject(name) },
                ) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            Icons.Filled.Home,
                            contentDescription = null,
                            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (active) "$name（当前）" else name,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                "projects/$name",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { viewModel.deleteProject(name) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "删除",
                                tint = FailRed,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        var newName by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建项目") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("项目名称") },
                        singleLine = true,
                    )
                    Text(
                        "模板包含完整的 gui / screens / script，创建后改编即可。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    viewModel.statusMessage?.let {
                        Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.createProject()
                    if (viewModel.currentProject != null) showCreate = false
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("取消") } },
        )
    }
}

// ───────────────── 编辑 ─────────────────

@Composable
private fun EditorPane(viewModel: ProjectViewModel) {
    if (viewModel.currentProject == null) {
        EmptyHint("先在「项目」页新建或打开一个项目")
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 文件下拉选择
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = viewModel.openFile ?: "选择文件…",
                onValueChange = { },
                readOnly = true,
                label = { Text("文件") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                viewModel.gameFiles.forEach { f ->
                    DropdownMenuItem(
                        text = { Text(f, fontSize = 13.sp, fontFamily = FontFamily.Monospace) },
                        onClick = { viewModel.openGameFile(f); expanded = false },
                    )
                }
            }
        }

        // 新建脚本
        var newFile by remember { mutableStateOf("") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newFile,
                onValueChange = { newFile = it },
                label = { Text("新脚本名") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            androidx.compose.material3.Button(onClick = {
                if (newFile.isNotBlank()) viewModel.newGameFile(newFile.trim())
                newFile = ""
            }) { Text("创建") }
        }

        // 保存行
        if (viewModel.openFile != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (viewModel.editorDirty) {
                    Text("有未保存修改", color = WarnOrange, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = {
                        viewModel.openFile?.let { viewModel.deleteGameFile(it) }
                    }) { Text("删除文件", color = FailRed) }
                    androidx.compose.material3.Button(onClick = { viewModel.saveEditor() }) {
                        Text("保存")
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                    Text("已保存", color = OkGreen, style = MaterialTheme.typography.bodySmall)
                }
            }

            OutlinedTextField(
                value = viewModel.editorContent,
                onValueChange = { viewModel.onEditorChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = ConsoleFg,
                ),
            )
        }
    }
}

// ───────────────── 终端 ─────────────────

@Composable
private fun TerminalPane(viewModel: ProjectViewModel) {
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    LaunchedEffect(viewModel.terminalLines.size) {
        if (viewModel.terminalLines.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.terminalLines.size - 1)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "非交互 shell · cwd: ${viewModel.currentProject ?: "RenPyForge/"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { clipboard.setText(AnnotatedString(viewModel.terminalText())) }) { Text("复制") }
            TextButton(onClick = { viewModel.clearTerminal() }) { Text("清空") }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = ConsoleBg),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            LazyColumn(Modifier.padding(10.dp)) {
                items(viewModel.terminalLines.toList()) { line ->
                    Text(
                        line,
                        color = if (line.startsWith("renpyforge@")) OkGreen else ConsoleFg,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 15.sp,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("ls", "cat game/script.rpy", "du -sh *", "uname -a").forEach { cmd ->
                androidx.compose.material3.OutlinedButton(
                    onClick = { viewModel.runTerminalCommand(cmd) },
                    modifier = Modifier.weight(1f),
                ) { Text(cmd, fontSize = 9.sp, maxLines = 1) }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = viewModel.terminalInput,
                onValueChange = { viewModel.terminalInput = it },
                placeholder = { Text("输入 shell 命令…") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            )
            androidx.compose.material3.Button(
                onClick = { viewModel.runTerminalCommand(viewModel.terminalInput) },
                enabled = !viewModel.terminalRunning,
            ) { Text(if (viewModel.terminalRunning) "…" else "执行") }
        }
    }
}

// ───────────────── 构建 ─────────────────

@Composable
private fun BuildPane(viewModel: ProjectViewModel, onInstall: (File) -> Unit) {
    if (viewModel.currentProject == null) {
        EmptyHint("先在「项目」页新建或打开一个项目")
        return
    }
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    LaunchedEffect(viewModel.console.size) {
        if (viewModel.console.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.console.size - 1)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 配置
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = viewModel.packageName,
                onValueChange = { viewModel.packageName = it },
                label = { Text("包名") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            )
            OutlinedTextField(
                value = viewModel.appName,
                onValueChange = { viewModel.appName = it },
                label = { Text("游戏名") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }

        Button(
            onClick = { viewModel.startBuild() },
            enabled = !viewModel.building,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (viewModel.building) "构建中…" else "构建游戏 APK") }

        if (viewModel.building) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // 产物
        viewModel.builtApk?.let { apk ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("${apk.name}（${apk.length() / 1024 / 1024} MB）", fontWeight = FontWeight.Bold)
                        Text(
                            apk.parent ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = { onInstall(apk) },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = OkGreen),
                    ) { Text("安装") }
                }
            }
        }

        // 错误
        viewModel.error?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }

        // 步骤
        viewModel.stepStates.forEach { (id, state) ->
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text(stateIcon(state), color = stateColor(state), fontSize = 14.sp)
                Spacer(Modifier.width(10.dp))
                Text(id.label, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    stateLabel(state),
                    color = stateColor(state),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        // 控制台
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("控制台", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { clipboard.setText(AnnotatedString(viewModel.consoleText())) }) { Text("复制") }
            TextButton(onClick = { viewModel.clearConsole() }) { Text("清空") }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = ConsoleBg),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            LazyColumn(state = listState, modifier = Modifier.padding(10.dp)) {
                items(viewModel.console.toList()) { line ->
                    Text(
                        line,
                        color = when {
                            line.startsWith("✘") -> FailRed
                            line.startsWith("✔") -> OkGreen
                            line.startsWith("▶") -> RunBlue
                            else -> ConsoleFg
                        },
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
