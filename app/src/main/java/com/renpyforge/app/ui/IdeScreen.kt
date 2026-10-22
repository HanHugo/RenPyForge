@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.renpyforge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renpyforge.app.build.StepId
import com.renpyforge.app.build.StepState
import java.io.File

// ───────────────── 主界面 ─────────────────

/** IDE 主界面：顶栏 / 文件树抽屉 / 多标签编辑器 / 底部面板(终端+构建) / 状态栏 */
@Composable
fun IdeScreen(
    viewModel: ProjectViewModel,
    onInstall: (File) -> Unit,
) {
    var drawerOpen by remember { mutableStateOf(false) }
    var panelOpen by remember { mutableStateOf(false) }
    var panelTab by remember { mutableIntStateOf(0) }   // 0 终端 1 构建
    var showNewFile by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(IdeColors.AppBg)
    ) {
        IdeTopBar(
            viewModel = viewModel,
            drawerOpen = drawerOpen,
            onDrawerToggle = { drawerOpen = !drawerOpen },
            onPanelToggle = { panelOpen = !panelOpen },
            panelOpen = panelOpen,
            onSave = { viewModel.saveAllTabs() },
        )

        // 主体：左文件树(手机上以抽屉面板实现) + 编辑器
        Box(Modifier.weight(1f)) {
            EditorArea(viewModel, onNewFile = { showNewFile = true })
            if (drawerOpen) {
                FileTreePanel(
                    viewModel = viewModel,
                    onClose = { drawerOpen = false },
                    onNewFile = { showNewFile = true },
                )
            }
        }

        if (panelOpen) {
            BottomPanel(
                viewModel = viewModel,
                tab = panelTab,
                onTab = { panelTab = it },
                onClose = { panelOpen = false },
                onInstall = onInstall,
            )
        }

        StatusBar(viewModel)
    }

    if (showNewFile) {
        NewFileDialog(viewModel) { showNewFile = false }
    }
}

// ───────────────── 顶栏 ─────────────────

@Composable
private fun IdeTopBar(
    viewModel: ProjectViewModel,
    drawerOpen: Boolean,
    onDrawerToggle: () -> Unit,
    onPanelToggle: () -> Unit,
    panelOpen: Boolean,
    onSave: () -> Unit,
) {
    Surface(color = IdeColors.PanelBg, shadowElevation = 2.dp) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .padding(horizontal = 4.dp),
            ) {
            IconButton(onClick = onDrawerToggle) {
                Icon(
                    Icons.Filled.Menu, contentDescription = "文件树",
                    tint = if (drawerOpen) IdeColors.Accent else IdeColors.TextFg,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    viewModel.currentProject ?: "",
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "RenPyForge · ${viewModel.gameFiles.size} 个文件",
                    fontSize = 10.sp, color = IdeColors.TextDim,
                )
            }
            if (viewModel.dirtyTabs.isNotEmpty()) {
                IconButton(onClick = onSave) {
                    Icon(
                        Icons.Filled.Save, contentDescription = "全部保存",
                        tint = IdeColors.WarnOrange, modifier = Modifier.size(20.dp),
                    )
                }
            }
            IconButton(onClick = onPanelToggle) {
                Icon(
                    Icons.Filled.Terminal, contentDescription = "面板",
                    tint = if (panelOpen) IdeColors.Accent else IdeColors.TextFg,
                    modifier = Modifier.size(20.dp),
                )
            }
            // 运行构建按钮
            Button(
                onClick = {
                    viewModel.saveAllTabs()
                    viewModel.startBuild()
                    if (!panelOpen) { onPanelToggle() }
                },
                enabled = !viewModel.building,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (viewModel.building) IdeColors.OkGreen.copy(alpha = 0.4f) else IdeColors.OkGreen,
                    contentColor = Color.White,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier
                    .padding(end = 6.dp)
                    .height(32.dp),
            ) {
                Icon(
                    if (viewModel.building) Icons.Filled.Build else Icons.Filled.PlayArrow,
                    contentDescription = null, modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(if (viewModel.building) "构建中" else "构建", fontSize = 13.sp)
            }
        }
            if (viewModel.building) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = IdeColors.OkGreen,
                    trackColor = IdeColors.PanelBg,
                )
            }
        }
    }
}

// ───────────────── 文件树面板 ─────────────────

@Composable
private fun FileTreePanel(
    viewModel: ProjectViewModel,
    onClose: () -> Unit,
    onNewFile: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxHeight()
            .width(260.dp)
            .background(IdeColors.PanelBg)
            .border(1.dp, IdeColors.PanelBorder)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .padding(horizontal = 12.dp),
        ) {
            Text("game/", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = IdeColors.TextDim, modifier = Modifier.weight(1f))
            IconButton(onClick = onNewFile, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = "新建脚本", tint = IdeColors.TextFg, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onClose, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "关闭", tint = IdeColors.TextFg, modifier = Modifier.size(16.dp))
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(IdeColors.PanelBorder)
        )
        FileTreeList(viewModel)
    }
}

@Composable
private fun FileTreeList(viewModel: ProjectViewModel) {
    // 预计算条目（含文件夹头），避免 LazyColumn 惰性组合导致的状态错乱
    val entries = remember(viewModel.gameFiles) {
        buildList {
            var lastDir: String? = null
            for (f in viewModel.gameFiles) {
                val dir = f.substringBeforeLast('/', "")
                if (dir != lastDir) {
                    lastDir = dir
                    if (dir.isNotEmpty()) add(dir to null)
                }
                add(dir to f)
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
        items(entries, key = { it.second ?: "hdr:${it.first}" }) { (dir, file) ->
            if (file == null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, top = 6.dp, bottom = 2.dp),
                ) {
                    Icon(Icons.Filled.Folder, null, tint = IdeColors.WarnOrange, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(dir, fontSize = 12.sp, color = IdeColors.TextDim, fontFamily = FontFamily.Monospace)
                }
            } else {
                val depth = dir.count { it == '/' } + if (dir.isNotEmpty()) 1 else 0
                val active = file == viewModel.activeTab
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (active) IdeColors.SelectedBg else Color.Transparent)
                        .clickable { viewModel.openGameFile(file) }
                        .padding(start = (20 + depth * 14).dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
                ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(
                                if (viewModel.dirtyTabs.contains(file)) IdeColors.WarnOrange else IdeColors.Accent.copy(alpha = 0.5f),
                                CircleShape,
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        file.substringAfterLast('/'),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (active) IdeColors.TextFg else IdeColors.ConsoleFg,
                    )
                    Spacer(Modifier.weight(1f))
                    if (active) {
                        Icon(
                            Icons.Filled.Delete, contentDescription = "删除",
                            tint = IdeColors.FailRed.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { viewModel.deleteGameFile(file) },
                        )
                    }
                }
            }
        }
    }
}

// ───────────────── 编辑区 ─────────────────

@Composable
private fun EditorArea(viewModel: ProjectViewModel, onNewFile: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        // 标签行
        if (viewModel.openTabs.isNotEmpty()) {
            LazyRow(
                Modifier
                    .fillMaxWidth()
                    .background(IdeColors.PanelBg)
                    .height(36.dp),
            ) {
                items(viewModel.openTabs.toList(), key = { it }) { path ->
                    val active = path == viewModel.activeTab
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(if (active) IdeColors.TabActive else IdeColors.PanelBg)
                            .clickable { viewModel.activateTab(path) }
                            .padding(start = 12.dp, end = 4.dp)
                            .height(36.dp),
                    ) {
                        Text(
                            path.substringAfterLast('/'),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (active) IdeColors.TextFg else IdeColors.TextDim,
                        )
                        if (viewModel.dirtyTabs.contains(path)) {
                            Box(
                                Modifier
                                    .padding(start = 4.dp)
                                    .size(6.dp)
                                    .background(IdeColors.WarnOrange, CircleShape)
                            )
                        }
                        IconButton(onClick = { viewModel.closeTab(path) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "关闭", tint = IdeColors.TextDim, modifier = Modifier.size(13.dp))
                        }
                    }
                }
            }
        }

        // 编辑器
        val path = viewModel.activeTab
        if (path == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("编辑器", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = IdeColors.TextDim)
                    Spacer(Modifier.height(6.dp))
                    Text("从左侧文件树打开脚本，或新建一个", fontSize = 12.sp, color = IdeColors.TextDim.copy(alpha = 0.7f))
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onNewFile, colors = ButtonDefaults.buttonColors(containerColor = IdeColors.AccentDim)) {
                        Text("新建脚本", fontSize = 13.sp)
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                CodeEditor(
                    path = path,
                    content = viewModel.activeContent,
                    onContentChange = { viewModel.onEditorChange(it) },
                    onCursorMoved = { l, c -> viewModel.onCursorMoved(l, c) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

// ───────────────── 底部面板（终端 / 构建） ─────────────────

@Composable
private fun BottomPanel(
    viewModel: ProjectViewModel,
    tab: Int,
    onTab: (Int) -> Unit,
    onClose: () -> Unit,
    onInstall: (File) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(IdeColors.PanelBg)
            .border(1.dp, IdeColors.PanelBorder)
    ) {
        // 面板标签行
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(34.dp),
        ) {
            PanelTab("终端", tab == 0) { onTab(0) }
            PanelTab("构建", tab == 1) { onTab(1) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Clear, contentDescription = "收起面板", tint = IdeColors.TextDim, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(6.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(IdeColors.PanelBorder)
        )
        when (tab) {
            0 -> TerminalConsole(viewModel)
            1 -> BuildConsole(viewModel, onInstall)
        }
    }
}

@Composable
private fun PanelTab(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 12.sp,
        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        color = if (active) IdeColors.TextFg else IdeColors.TextDim,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun TerminalConsole(viewModel: ProjectViewModel) {
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    LaunchedEffect(viewModel.terminalLines.size) {
        if (viewModel.terminalLines.isNotEmpty()) listState.scrollToItem(viewModel.terminalLines.size - 1)
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(8.dp)) {
            items(viewModel.terminalLines.toList()) { line ->
                Text(
                    line,
                    color = if (line.startsWith("renpyforge@")) IdeColors.OkGreen
                    else if (line.startsWith("[")) IdeColors.TextDim
                    else IdeColors.ConsoleFg,
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(IdeColors.ConsoleBg)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text("❯", color = IdeColors.OkGreen, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(6.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = viewModel.terminalInput,
                onValueChange = { viewModel.terminalInput = it },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = IdeColors.TextFg),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(IdeColors.Accent),
                singleLine = true,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
            )
            TextButton(
                onClick = { viewModel.runTerminalCommand(viewModel.terminalInput) },
                enabled = !viewModel.terminalRunning,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) { Text(if (viewModel.terminalRunning) "…" else "执行", fontSize = 12.sp) }
            TextButton(
                onClick = { clipboard.setText(AnnotatedString(viewModel.terminalText())) },
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) { Text("复制", fontSize = 12.sp) }
        }
    }
}

@Composable
private fun BuildConsole(viewModel: ProjectViewModel, onInstall: (File) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(viewModel.console.size) {
        if (viewModel.console.isNotEmpty()) listState.scrollToItem(viewModel.console.size - 1)
    }
    Column(Modifier.fillMaxSize()) {
        // 配置行
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(IdeColors.ConsoleBg)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text("id:", fontSize = 11.sp, color = IdeColors.TextDim)
            androidx.compose.foundation.text.BasicTextField(
                value = viewModel.packageName,
                onValueChange = { viewModel.packageName = it },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = IdeColors.TextFg),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(IdeColors.Accent),
                singleLine = true,
                modifier = Modifier.weight(1.4f).padding(vertical = 4.dp),
            )
            Text("名:", fontSize = 11.sp, color = IdeColors.TextDim)
            androidx.compose.foundation.text.BasicTextField(
                value = viewModel.appName,
                onValueChange = { viewModel.appName = it },
                textStyle = TextStyle(fontSize = 11.sp, color = IdeColors.TextFg),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(IdeColors.Accent),
                singleLine = true,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
            )
        }
        // 构建产物/错误
        viewModel.builtApk?.let { apk ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IdeColors.AccentDim.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${apk.name}（${apk.length() / 1024 / 1024} MB）", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(apk.parent ?: "", fontSize = 10.sp, color = IdeColors.TextDim)
                }
                Button(
                    onClick = { onInstall(apk) },
                    colors = ButtonDefaults.buttonColors(containerColor = IdeColors.OkGreen),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier.height(30.dp),
                ) { Text("安装", fontSize = 12.sp) }
            }
        }
        viewModel.error?.let {
            Text(
                it, color = IdeColors.FailRed, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth().background(IdeColors.FailRed.copy(alpha = 0.12f)).padding(8.dp),
            )
        }
        // 步骤条
        if (viewModel.stepStates.any { it.second != StepState.PENDING }) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(IdeColors.ConsoleBg)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                viewModel.stepStates.forEach { (id, state) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(
                                    when (state) {
                                        StepState.PENDING -> IdeColors.TextDim.copy(alpha = 0.4f)
                                        StepState.RUNNING -> IdeColors.Accent
                                        StepState.OK -> IdeColors.OkGreen
                                        StepState.FAILED -> IdeColors.FailRed
                                        StepState.SKIPPED -> IdeColors.WarnOrange
                                    },
                                    CircleShape,
                                )
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            id.label, fontSize = 10.sp,
                            color = if (state == StepState.PENDING) IdeColors.TextDim else IdeColors.TextFg,
                        )
                    }
                }
            }
        }
        // 控制台输出
        LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(8.dp)) {
            items(viewModel.console.toList()) { line ->
                Text(
                    line,
                    color = when {
                        line.startsWith("✘") -> IdeColors.FailRed
                        line.startsWith("✔") -> IdeColors.OkGreen
                        line.startsWith("▶") -> IdeColors.Accent
                        else -> IdeColors.ConsoleFg
                    },
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp,
                )
            }
        }
    }
}

// ───────────────── 状态栏 ─────────────────

@Composable
private fun StatusBar(viewModel: ProjectViewModel) {
    Surface(color = IdeColors.PanelBg) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .padding(horizontal = 10.dp),
        ) {
            Text(
                viewModel.activeTab ?: "无打开文件",
                fontSize = 10.sp, color = IdeColors.TextDim, fontFamily = FontFamily.Monospace,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Ln ${viewModel.cursorLine}, Col ${viewModel.cursorCol}",
                fontSize = 10.sp, color = IdeColors.TextDim, fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .size(6.dp)
                    .background(
                        if (viewModel.building) IdeColors.Accent
                        else if (viewModel.dirtyTabs.isNotEmpty()) IdeColors.WarnOrange
                        else IdeColors.OkGreen,
                        CircleShape,
                    )
            )
            Text(
                when {
                    viewModel.building -> "构建中"
                    viewModel.dirtyTabs.isNotEmpty() -> "未保存 ×${viewModel.dirtyTabs.size}"
                    else -> "就绪"
                },
                fontSize = 10.sp, color = IdeColors.TextDim, modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

// ───────────────── 新建文件对话框 ─────────────────

@Composable
private fun NewFileDialog(viewModel: ProjectViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = IdeColors.PanelBg,
        title = { Text("新建脚本") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("脚本名") },
                    singleLine = true,
                )
                Text(
                    "将创建于 game/script/<名称>.rpy，如需其他路径请填写相对路径。",
                    fontSize = 12.sp, color = IdeColors.TextDim,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    viewModel.newGameFile(name.trim())
                    onDismiss()
                }
            }) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
