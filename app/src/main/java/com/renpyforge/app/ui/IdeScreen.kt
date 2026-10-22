@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.renpyforge.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import kotlinx.coroutines.launch
import java.io.File

// ═══════════════════════ IDE 主界面 ═══════════════════════

/**
 * IDE 主界面（MD3 + 动效）：
 * · MD3 ModalNavigationDrawer 承载文件树
 * · Scaffold + TopAppBar 顶栏
 * · AnimatedContent 驱动标签页切换
 * · AnimatedVisibility 滑入/滑出底部面板
 */
@Composable
fun IdeScreen(
    viewModel: ProjectViewModel,
    onInstall: (File) -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var panelOpen by remember { mutableStateOf(false) }
    var panelTab by remember { mutableIntStateOf(0) }
    var showNewFile by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<String?>(null) }

    // 打开面板时刷新构建历史与资源列表
    LaunchedEffect(panelOpen) {
        if (panelOpen) {
            viewModel.refreshBuildHistory()
            viewModel.refreshResources()
        }
    }
    // 打开文件时立刻做一次语法检查
    LaunchedEffect(viewModel.activeTab) {
        if (viewModel.activeTab != null) viewModel.refreshProblems()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = IdeColors.PanelBg,
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            ) {
                FileTreePanel(
                    viewModel = viewModel,
                    onClose = { scope.launch { drawerState.close() } },
                    onNewFile = { showNewFile = true },
                    onCloseProject = { viewModel.closeProject() },
                    onRename = { renameTarget = it },
                )
            }
        },
    ) {
        Scaffold(
            containerColor = IdeColors.AppBg,
            topBar = {
                IdeTopBar(
                    viewModel = viewModel,
                    onMenu = { scope.launch { drawerState.open() } },
                    onPanelToggle = {
                        panelOpen = !panelOpen
                        if (panelOpen) panelTab = 0
                    },
                    panelOpen = panelOpen,
                    onSave = { viewModel.saveAllTabs() },
                    onManual = { showManual = true },
                    onSearch = { showSearch = true },
                )
            },
            bottomBar = {
                StatusBar(
                    viewModel = viewModel,
                    panelOpen = panelOpen,
                    onOpenProblems = {
                        panelTab = 2
                        panelOpen = true
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                EditorTabs(viewModel)

                Box(Modifier.weight(1f)) {
                    // 标签页切换动效：淡入 + 轻微位移
                    AnimatedContent(
                        targetState = viewModel.activeTab,
                        transitionSpec = {
                            (fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 12 }) togetherWith
                                fadeOut(tween(120))
                        },
                        label = "editor",
                    ) { path ->
                        if (path == null) {
                            EditorEmptyState(onNewFile = { showNewFile = true })
                        } else {
                            CodeEditor(
                                path = path,
                                content = viewModel.activeContent,
                                onContentChange = { viewModel.onEditorChange(it) },
                                onCursorMoved = { l, c -> viewModel.onCursorMoved(l, c) },
                                jumpLine = viewModel.jumpRequest?.first ?: 0,
                                onJumpConsumed = { viewModel.consumeJump() },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }

                // 底部面板：从底部滑入/滑出
                AnimatedVisibility(
                    visible = panelOpen,
                    enter = slideInVertically(tween(240)) { it } + fadeIn(tween(240)),
                    exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(160)),
                ) {
                    BottomPanel(
                        viewModel = viewModel,
                        tab = panelTab,
                        onTab = { panelTab = it },
                        onClose = { panelOpen = false },
                        onInstall = onInstall,
                    )
                }
            }
        }
    }

    if (showNewFile) NewFileDialog(viewModel) { showNewFile = false }
    if (showManual) ManualSheet { showManual = false }
    if (showSearch) GlobalSearchSheet(viewModel) { showSearch = false }
    renameTarget?.let { target ->
        RenameDialog(target) { newName ->
            if (newName != null) viewModel.renameGameFile(target, newName)
            renameTarget = null
        }
    }
}

// ═══════════════════════ 顶栏 ═══════════════════════

@Composable
private fun IdeTopBar(
    viewModel: ProjectViewModel,
    onMenu: () -> Unit,
    onPanelToggle: () -> Unit,
    panelOpen: Boolean,
    onSave: () -> Unit,
    onManual: () -> Unit,
    onSearch: () -> Unit,
) {
    Column {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = onMenu) {
                    Icon(Icons.Filled.Menu, contentDescription = "文件树", modifier = Modifier.size(22.dp))
                }
            },
            title = {
                Column {
                    Text(
                        viewModel.currentProject ?: "",
                        fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${viewModel.gameFiles.size} 个文件",
                        fontSize = 10.sp, color = IdeColors.TextDim,
                    )
                }
            },
            actions = {
                // 未保存时出现「全部保存」按钮（缩放+淡入动效）
                AnimatedVisibility(
                    visible = viewModel.dirtyTabs.isNotEmpty(),
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                ) {
                    IconButton(onClick = onSave) {
                        Icon(
                            Icons.Filled.Save, contentDescription = "全部保存",
                            tint = IdeColors.WarnOrange, modifier = Modifier.size(20.dp),
                        )
                    }
                }
                IconButton(onClick = onManual) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "代码手册", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onSearch) {
                    Icon(Icons.Filled.Search, contentDescription = "全局搜索", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onPanelToggle) {
                    Icon(
                        Icons.Filled.Terminal, contentDescription = "终端面板",
                        tint = if (panelOpen) IdeColors.Accent else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
                BuildButton(viewModel, onPanelToggle, panelOpen)
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = IdeColors.PanelBg,
                navigationIconContentColor = IdeColors.TextFg,
                titleContentColor = IdeColors.TextFg,
                actionIconContentColor = IdeColors.TextFg,
            ),
        )
        // 构建中的细进度条
        AnimatedVisibility(
            visible = viewModel.building,
            enter = expandVertically(tween(180)),
            exit = shrinkVertically(tween(180)),
        ) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = IdeColors.OkGreen,
                trackColor = IdeColors.PanelBg,
            )
        }
    }
}

private fun scaleIn() = androidx.compose.animation.scaleIn(spring(dampingRatio = 0.6f))
private fun scaleOut() = androidx.compose.animation.scaleOut(spring(dampingRatio = 0.6f))

@Composable
private fun BuildButton(viewModel: ProjectViewModel, onPanelToggle: () -> Unit, panelOpen: Boolean) {
    Button(
        onClick = {
            viewModel.saveAllTabs()
            viewModel.startBuild()
            if (!panelOpen) onPanelToggle()
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

// ═══════════════════════ 编辑器标签行 ═══════════════════════

@Composable
private fun EditorTabs(viewModel: ProjectViewModel) {
    AnimatedVisibility(
        visible = viewModel.openTabs.isNotEmpty(),
        enter = expandVertically(tween(180)) + fadeIn(),
        exit = shrinkVertically(tween(180)) + fadeOut(),
    ) {
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
                        .background(
                            animateColorAsState(
                                if (active) IdeColors.TabActive else IdeColors.PanelBg,
                                tween(160), label = "tabBg",
                            ).value
                        )
                        .clickable { viewModel.activateTab(path) }
                        .padding(start = 12.dp, end = 4.dp)
                        .height(36.dp)
                        .animateItem(),
                ) {
                    Text(
                        path.substringAfterLast('/'),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (active) IdeColors.TextFg else IdeColors.TextDim,
                    )
                    // 未保存小圆点（缩放动效）
                    androidx.compose.animation.AnimatedVisibility(
                        visible = viewModel.dirtyTabs.contains(path),
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut(),
                    ) {
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
}

@Composable
private fun EditorEmptyState(onNewFile: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = IdeColors.TextDim.copy(alpha = 0.5f),
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text("从左侧文件树打开脚本", fontSize = 14.sp, color = IdeColors.TextDim)
            Spacer(Modifier.height(4.dp))
            Text("或新建一个开始编写剧情", fontSize = 12.sp, color = IdeColors.TextDim.copy(alpha = 0.7f))
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onNewFile,
                colors = ButtonDefaults.buttonColors(containerColor = IdeColors.AccentDim, contentColor = IdeColors.TextFg),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("新建脚本", fontSize = 13.sp)
            }
        }
    }
}

// ═══════════════════════ 文件树抽屉 ═══════════════════════

@Composable
private fun FileTreePanel(
    viewModel: ProjectViewModel,
    onClose: () -> Unit,
    onNewFile: () -> Unit,
    onCloseProject: () -> Unit,
    onRename: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 16.dp),
        ) {
            Icon(Icons.Filled.Folder, null, tint = IdeColors.WarnOrange, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("game/", fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onNewFile, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "新建脚本", modifier = Modifier.size(18.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(IdeColors.PanelBorder))
        Text(
            "长按文件可重命名",
            fontSize = 10.sp,
            color = IdeColors.TextDim.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        FileTreeList(viewModel, onRename)
        Box(Modifier.fillMaxWidth().height(1.dp).background(IdeColors.PanelBorder))
        // 抽屉底部动作
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCloseProject() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(Icons.Filled.Home, contentDescription = null, tint = IdeColors.TextDim, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("关闭项目，返回欢迎页", fontSize = 13.sp, color = IdeColors.TextDim)
        }
        // 关闭抽屉
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClose() }
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = IdeColors.TextDim, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("收起文件树", fontSize = 13.sp, color = IdeColors.TextDim)
        }
    }
}

// —— 树数据结构 ——
private sealed class TreeNode2 { abstract val key: String }
private data class TreeDir(val path: String, val name: String, val depth: Int) : TreeNode2() { override val key = "d:$path" }
private data class TreeFile(val path: String, val name: String, val depth: Int) : TreeNode2() { override val key = "f:$path" }

private class FsNode(val name: String, val path: String) {
    val dirs = linkedMapOf<String, FsNode>()
    val files = mutableListOf<String>()
}

private fun buildFileTree(files: List<String>): FsNode {
    val root = FsNode("", "")
    for (f in files) {
        val parts = f.split('/')
        var cur = root
        for (i in 0 until parts.size - 1) {
            val p = parts.subList(0, i + 1).joinToString("/")
            cur = cur.dirs.getOrPut(parts[i]) { FsNode(parts[i], p) }
        }
        cur.files.add(f)
    }
    return root
}

private fun flattenTree(root: FsNode, expanded: Set<String>): List<TreeNode2> {
    val out = mutableListOf<TreeNode2>()
    fun walk(node: FsNode, depth: Int) {
        for ((_, dir) in node.dirs) {
            out.add(TreeDir(dir.path, dir.name, depth))
            if (dir.path in expanded) walk(dir, depth + 1)
        }
        for (f in node.files) out.add(TreeFile(f, f.substringAfterLast('/'), depth))
    }
    walk(root, 0)
    return out
}

@Composable
private fun FileTreeList(viewModel: ProjectViewModel, onRename: (String) -> Unit) {
    val tree = remember(viewModel.gameFiles) { buildFileTree(viewModel.gameFiles) }
    var expanded by remember(viewModel.gameFiles) {
        mutableStateOf(tree.dirs.values.map { it.path }.toSet())
    }
    val flat = remember(tree, expanded) { flattenTree(tree, expanded) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
        items(flat.size, key = { flat[it].key }) { idx ->
            when (val node = flat[idx]) {
                is TreeDir -> {
                    // 箭头旋转动效
                    val rotation by animateFloatAsState(
                        targetValue = if (node.path in expanded) 90f else 0f,
                        animationSpec = tween(180), label = "chevron",
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expanded = if (node.path in expanded) expanded - node.path else expanded + node.path
                            }
                            .padding(start = (10 + node.depth * 16).dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                    ) {
                        Text(
                            "▸",
                            fontSize = 12.sp,
                            color = IdeColors.TextDim,
                            modifier = Modifier
                                .width(14.dp)
                                .rotate(rotation),
                        )
                        Icon(Icons.Filled.Folder, null, tint = IdeColors.WarnOrange, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(node.name, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                is TreeFile -> FileRow(node, viewModel, onRename)
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FileRow(node: TreeFile, viewModel: ProjectViewModel, onRename: (String) -> Unit) {
    val active = node.path == viewModel.activeTab
    val bg by animateColorAsState(
        if (active) IdeColors.SelectedBg else Color.Transparent,
        tween(160), label = "fileBg",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .combinedClickable(
                onClick = { viewModel.openGameFile(node.path) },
                onLongClick = { onRename(node.path) },
            )
            .padding(start = (40 + node.depth * 16).dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(
                    if (viewModel.dirtyTabs.contains(node.path)) IdeColors.WarnOrange else IdeColors.Accent.copy(alpha = 0.5f),
                    CircleShape,
                )
        )
        Spacer(Modifier.width(8.dp))
        Text(
            node.name,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = if (active) IdeColors.TextFg else IdeColors.ConsoleFg,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        if (active) {
            Icon(
                Icons.Filled.Delete, contentDescription = "删除",
                tint = IdeColors.FailRed.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(14.dp)
                    .clickable { viewModel.deleteGameFile(node.path) },
            )
        }
    }
}

// ═══════════════════════ 底部面板（终端 / 构建） ═══════════════════════

@Composable
private fun BottomPanel(
    viewModel: ProjectViewModel,
    tab: Int,
    onTab: (Int) -> Unit,
    onClose: () -> Unit,
    onInstall: (File) -> Unit,
) {
    Surface(
        color = IdeColors.PanelBg,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(Modifier.fillMaxWidth().height(310.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(40.dp).padding(start = 10.dp, end = 4.dp),
            ) {
                val tabs = listOf("终端", "构建", "问题", "资源")
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f).height(30.dp)) {
                    tabs.forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = tab == i,
                            onClick = { onTab(i) },
                            shape = SegmentedButtonDefaults.itemShape(i, tabs.size),
                            label = {
                                if (i == 2 && viewModel.problems.isNotEmpty()) {
                                    Text("$label ${viewModel.problems.size}", fontSize = 12.sp)
                                } else {
                                    Text(label, fontSize = 12.sp)
                                }
                            },
                        )
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "收起", tint = IdeColors.TextDim, modifier = Modifier.size(16.dp))
                }
            }
            // 面板内容切换动效
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (fadeIn(tween(160)) + slideInHorizontally(tween(200)) { dir * it / 10 }) togetherWith
                        fadeOut(tween(120))
                },
                label = "panelTab",
            ) { t ->
                when (t) {
                    0 -> TerminalConsole(viewModel)
                    1 -> BuildConsole(viewModel, onInstall)
                    2 -> ProblemsPane(viewModel)
                    else -> ResourcePane(viewModel)
                }
            }
        }
    }
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
                    color = when {
                        line.startsWith("renpyforge@") -> IdeColors.OkGreen
                        line.startsWith("[") -> IdeColors.TextDim
                        else -> IdeColors.ConsoleFg
                    },
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
        // 构建产物
        AnimatedVisibility(
            visible = viewModel.builtApk != null,
            enter = expandVertically(spring()) + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
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
                    ) {
                        Icon(Icons.Filled.InstallMobile, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("安装", fontSize = 12.sp)
                    }
                }
            }
        }
        // 错误
        viewModel.error?.let {
            Text(
                it, color = IdeColors.FailRed, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth().background(IdeColors.FailRed.copy(alpha = 0.12f)).padding(8.dp),
            )
        }
        // 步骤指示灯（颜色渐变动效）
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
                    val dotColor by animateColorAsState(
                        when (state) {
                            StepState.PENDING -> IdeColors.TextDim.copy(alpha = 0.4f)
                            StepState.RUNNING -> IdeColors.Accent
                            StepState.OK -> IdeColors.OkGreen
                            StepState.FAILED -> IdeColors.FailRed
                            StepState.SKIPPED -> IdeColors.WarnOrange
                        },
                        tween(250), label = "dot",
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(dotColor, CircleShape))
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
        // 历史产物
        AnimatedVisibility(
            visible = viewModel.buildHistory.isNotEmpty(),
            enter = expandVertically(spring()) + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(IdeColors.ConsoleBg)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("历史产物（RenPyForge/output/）", fontSize = 10.sp, color = IdeColors.TextDim)
                viewModel.buildHistory.take(3).forEach { artifact ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(artifact.file.name, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${artifact.sizeMb} MB · " + java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(artifact.modified),
                                fontSize = 9.sp, color = IdeColors.TextDim,
                            )
                        }
                        IconButton(onClick = { viewModel.deleteArtifact(artifact.file) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除", tint = IdeColors.FailRed.copy(alpha = 0.6f), modifier = Modifier.size(13.dp))
                        }
                        Button(
                            onClick = { onInstall(artifact.file) },
                            colors = ButtonDefaults.buttonColors(containerColor = IdeColors.AccentDim),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.height(24.dp),
                        ) { Text("安装", fontSize = 10.sp) }
                    }
                }
            }
        }
    }
}

// ═══════════════════════ 问题面板 ═══════════════════════

@Composable
private fun ProblemsPane(viewModel: ProjectViewModel) {
    if (viewModel.problems.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(IdeColors.OkGreen, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text("没有发现语法问题", fontSize = 13.sp, color = IdeColors.TextDim)
            }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
        items(viewModel.problems.size, key = { "${viewModel.problems[it].line}-${it}" }) { idx ->
            val p = viewModel.problems[idx]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.requestJump(p.line, 1) }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            ) {
                Text(
                    "Ln ${p.line}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = IdeColors.FailRed,
                    modifier = Modifier.width(56.dp),
                )
                Text(p.message, fontSize = 12.sp, color = IdeColors.TextFg)
            }
        }
    }
}

// ═══════════════════════ 资源浏览器 ═══════════════════════

@Composable
private fun ResourcePane(viewModel: ProjectViewModel) {
    val clipboard = LocalClipboardManager.current
    if (viewModel.resourceImages.isEmpty() && viewModel.resourceAudio.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "暂无资源\n把图片放入 game/images/，音频放入 game/audio/",
                fontSize = 12.sp, color = IdeColors.TextDim,
                lineHeight = 18.sp,
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
        if (viewModel.resourceImages.isNotEmpty()) {
            item {
                Text("图片（点击复制路径）", fontSize = 10.sp, color = IdeColors.TextDim)
                Spacer(Modifier.height(6.dp))
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(viewModel.resourceImages, key = { it.first.absolutePath }) { (f, rel) ->
                        ImageThumb(f) {
                            clipboard.setText(AnnotatedString(rel))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        if (viewModel.resourceAudio.isNotEmpty()) {
            item {
                Text("音频（点击复制路径）", fontSize = 10.sp, color = IdeColors.TextDim)
                Spacer(Modifier.height(4.dp))
            }
            items(viewModel.resourceAudio, key = { "a" + it.first.absolutePath }) { (f, rel) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { clipboard.setText(AnnotatedString(rel)) }
                        .padding(vertical = 4.dp),
                ) {
                    Icon(Icons.Filled.Build, contentDescription = null, tint = IdeColors.Accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(rel, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun ImageThumb(f: File, onClick: () -> Unit) {
    var bmp by remember(f.absolutePath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(f.absolutePath) {
        bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                android.graphics.BitmapFactory.decodeFile(f.absolutePath, opts)
            } catch (_: Exception) { null }
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
        Box(
            Modifier
                .size(72.dp)
                .background(IdeColors.EditorBg, RoundedCornerShape(8.dp))
                .border(1.dp, IdeColors.PanelBorder, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            val b = bmp
            if (b != null) {
                androidx.compose.foundation.Image(
                    bitmap = b.asImageBitmap(),
                    contentDescription = f.name,
                    modifier = Modifier.size(72.dp),
                )
            } else {
                Text("…", color = IdeColors.TextDim, fontSize = 16.sp)
            }
        }
        Text(
            f.name,
            fontSize = 9.sp,
            color = IdeColors.TextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(76.dp),
        )
    }
}

// ═══════════════════════ 状态栏 ═══════════════════════

@Composable
private fun StatusBar(
    viewModel: ProjectViewModel,
    panelOpen: Boolean,
    onOpenProblems: () -> Unit,
) {
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
            // 问题数量指示（有问题时才显示，点击打开问题面板）
            if (viewModel.problems.isNotEmpty() && !panelOpen) {
                Spacer(Modifier.width(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onOpenProblems),
                ) {
                    Box(Modifier.size(6.dp).background(IdeColors.FailRed, CircleShape))
                    Text(
                        " ${viewModel.problems.size} 个问题",
                        fontSize = 10.sp, color = IdeColors.FailRed,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            // 状态点与文字随状态渐变
            val dotColor by animateColorAsState(
                when {
                    viewModel.building -> IdeColors.Accent
                    viewModel.dirtyTabs.isNotEmpty() -> IdeColors.WarnOrange
                    else -> IdeColors.OkGreen
                },
                tween(250), label = "statusDot",
            )
            Box(Modifier.size(6.dp).background(dotColor, CircleShape))
            AnimatedContent(
                targetState = when {
                    viewModel.building -> "构建中"
                    viewModel.dirtyTabs.isNotEmpty() -> "未保存 ×${viewModel.dirtyTabs.size}"
                    else -> "就绪"
                },
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                label = "statusText",
            ) { label ->
                Text(label, fontSize = 10.sp, color = IdeColors.TextDim, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

// ═══════════════════════ 对话框与手册 ═══════════════════════

@Composable
private fun NewFileDialog(viewModel: ProjectViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
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
                    "无扩展名 → 创建 game/script/<名称>.rpy；\n带扩展名（如 bg.png）→ 按原名创建到 game/ 下；\n也可填写相对路径精确指定。",
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

private data class ManualSection(val title: String, val code: String, val desc: String)

private val manualSections = listOf(
    ManualSection(
        "剧本基础", """label start:
    scene bg room
    show eileen happy
    "你好，世界！"
    return""",
        "label 是流程入口；scene 换背景；show 显示立绘；直接写字符串就是对话，前面加角色名变量表示谁在说话。",
    ),
    ManualSection(
        "选择分支", """menu:
    "去图书馆":
        jump library
    "回家睡觉":
        jump home""",
        "menu: 创建选项；每个选项是一段缩进的 \"文本\": 加后续动作。",
    ),
    ManualSection(
        "跳转与标签", """label library:
    "这里很安静。"
    jump start""",
        "jump 跳到另一个 label；call 会记住返回位置，配合 return 回来。",
    ),
    ManualSection(
        "变量", """default points = 0
define e = Character("艾琳")

label start:
    $ points += 1
    if points >= 3:
        e "好感度很高嘛。"
""",
        "default 定义存档变量；define 定义常量（如角色）；$ 行执行任意 Python；if/elif/else 条件分支。",
    ),
    ManualSection(
        "图像与音频", """image bg room = "images/room.jpg"
play music "audio/bgm.ogg"
play sound "audio/click.wav"
stop music fadeout 1.0""",
        "image 注册图像；play/stop 控制音乐音效，支持 fadeout。",
    ),
    ManualSection(
        "界面 screen", """screen stats():
    frame:
        vbox:
            text "好感度: [points]"
            textbutton "关闭" action Return()

label start:
    show screen stats""",
        "screen 定义 UI；[变量] 插值显示；textbutton + action 响应点击；show screen 显示，hide screen 隐藏。",
    ),
    ManualSection(
        "转场与停留", """with dissolve
pause 2.0
window hide""",
        "with 指定转场效果（dissolve/fade 等）；pause 暂停秒数。",
    ),
    ManualSection(
        "实用技巧", """# 这是注释
"[player_name] 早上好"      # 文本插值
nvl clear                   # NVL 模式清屏
transform shake:
    linear 0.2 xoffset 10""",
        "# 注释；transform 定义动画；官方文档 renpy.org/doc 见「更多」。",
    ),
    ManualSection(
        "ATL 动画", """show eileen happy:
    xalign 0.5
    linear 1.0 zoom 1.5

transform slidein:
    xoffset -400
    easein 0.5 xoffset 0""",
        "show 时接 ATL 块做位移动画；transform 定义可复用动画；easein/linear 是插值方式。",
    ),
    ManualSection(
        "label 参数", """label start(route="A"):
    "路线 [route]"
    call chapter(3)

label chapter(n):
    "第 [n] 章"
    return""",
        "label 可带默认参数，call/jump 时传参；[n] 插值使用。",
    ),
    ManualSection(
        "Python 块", """init python:
    def double(x):
        return x * 2

label start:
    $ answer = double(21)
    "答案是 [answer]"
""",
        "init python 在启动时执行（定义函数/常量）；$ 单行；python: 块多行。",
    ),
    ManualSection(
        "条件显示 showif", """screen hp():
    showif hp_value > 50:
        text "状态良好" color "#6AAB73"
    else:
        text "状态不佳" color "#E55765\"""",
        "screen 内 showif 根据变量切换显示，带平滑过渡。",
    ),
    ManualSection(
        "角色 Character", """define n = Character("旁白", kind=nvl)
define e = Character("艾琳",
    color="#c8ffc8",
    who_suffix="：")

label start:
    n "NVL 模式旁白。"
    e "带颜色的名字。"
""",
        "Character 支持名字颜色、前后缀、NVL 等大量参数，是文本表现的核心。",
    ),
)

private val devManualSections = listOf(
    ManualSection(
        "完整工作流", """1. 欢迎页 → 新建项目
2. 编辑 script.rpy（或新建脚本）
3. 顶栏 ▶ 构建
4. 构建完成 → 安装""",
        "构建会自动保存所有未保存的文件。产物 APK 位于存储根目录 RenPyForge/output/。",
    ),
    ManualSection(
        "资源文件", """game/
├── script.rpy
├── images/       ← 图片自动注册
│   └── bg room.png   → scene bg room
└── audio/        ← 音频
    └── bgm.ogg       → play music "audio/bgm.ogg\"""",
        "images/ 下的图片文件名（去扩展名）自动成为图像名；空格即空格：\"bg room.png\" 对应 scene bg room。",
    ),
    ManualSection(
        "文件管理与传输", """手机存储根目录：
RenPyForge/projects/<项目名>/game/""",
        "项目是普通文件夹：用文件管理器、USB 连电脑均可拷入图片/音频/字体。改完回 App 里直接构建。",
    ),
    ManualSection(
        "构建配置", """包名：org.renpy.forge.<项目名>
  （可在构建面板修改）
游戏名：显示在桌面图标下
签名：内置密钥，同一包名
      覆盖安装即可更新""",
        "改包名会生成一个「新应用」而非覆盖旧版；想更新已安装的游戏请保持包名不变。",
    ),
    ManualSection(
        "脚本拆分", """game/script.rpy      ← 主剧情
game/chapter1.rpy    ← 第一章
game/characters.rpy  ← 角色定义""",
        "多个 .rpy 文件像一个文件一样工作；label 可跨文件跳转。文件名任意，建议英文。",
    ),
    ManualSection(
        "常见问题", """✘ 构建失败 → 看构建面板控制台的 ✘ 行
✘ 脚本报错 → 检查缩进必须是 4 空格
✘ 图片不显示 → 检查文件名与 scene/show 一致
✘ 中文乱码 → 使用支持中文的字体文件""",
        "游戏运行报错会显示 traceback，对照行号回编辑器修复即可。",
    ),
)

@Composable
private fun ManualSheet(onDismiss: () -> Unit) {
    var manualTab by remember { mutableIntStateOf(0) }
    val sections = if (manualTab == 0) manualSections else devManualSections
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = IdeColors.PanelBg,
        contentColor = IdeColors.TextFg,
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Ren'Py 手册", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "最常用语句与开发流程速查 · 完整文档见 renpy.org/doc",
                fontSize = 11.sp, color = IdeColors.TextDim,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )
            SingleChoiceSegmentedButtonRow(Modifier.width(220.dp).height(32.dp).padding(bottom = 10.dp)) {
                SegmentedButton(
                    selected = manualTab == 0,
                    onClick = { manualTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    label = { Text("代码手册", fontSize = 12.sp) },
                )
                SegmentedButton(
                    selected = manualTab == 1,
                    onClick = { manualTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    label = { Text("开发手册", fontSize = 12.sp) },
                )
            }
        }
        AnimatedContent(
            targetState = manualTab,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (fadeIn(tween(160)) + slideInHorizontally(tween(200)) { dir * it / 10 }) togetherWith
                    fadeOut(tween(120))
            },
            label = "manualTab",
        ) { t ->
            val list = if (t == 0) manualSections else devManualSections
            LazyColumn(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list.size, key = { list[it].title }) { idx ->
                    val s = list[idx]
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(IdeColors.EditorBg, RoundedCornerShape(10.dp))
                            .border(1.dp, IdeColors.PanelBorder, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(s.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = IdeColors.Accent)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            s.code,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp,
                            color = IdeColors.ConsoleFg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(IdeColors.ConsoleBg, RoundedCornerShape(6.dp))
                                .padding(8.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(s.desc, fontSize = 12.sp, color = IdeColors.TextDim)
                    }
                }
            }
        }
    }
}

// ═══════════════════════ 全局搜索 ═══════════════════════

@Composable
private fun GlobalSearchSheet(
    viewModel: ProjectViewModel,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = IdeColors.PanelBg,
        contentColor = IdeColors.TextFg,
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("全局搜索", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "在项目所有脚本中查找文本，点击结果直接跳转",
                fontSize = 11.sp, color = IdeColors.TextDim,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.globalSearch(it)
                },
                label = { Text("搜索内容") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    query.isBlank() -> "输入关键词开始搜索"
                    viewModel.searching -> "搜索中…"
                    viewModel.searchResults.isEmpty() -> "没有匹配结果"
                    else -> "共 ${viewModel.searchResults.size} 条结果"
                },
                fontSize = 11.sp, color = IdeColors.TextDim,
            )
        }
        LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            items(viewModel.searchResults.size, key = { idx ->
                "${viewModel.searchResults[idx].path}:${viewModel.searchResults[idx].line}:$idx"
            }) { idx ->
                val hit = viewModel.searchResults[idx]
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.openGameFile(hit.path)
                            viewModel.requestJump(hit.line, 1)
                            onDismiss()
                        }
                        .padding(vertical = 8.dp)
                ) {
                    Row {
                        Text(
                            hit.path,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = IdeColors.Accent,
                        )
                        Text(
                            "  Ln ${hit.line}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = IdeColors.TextDim,
                        )
                    }
                    Text(
                        hit.text,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = IdeColors.ConsoleFg,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HorizontalDivider(color = IdeColors.PanelBorder.copy(alpha = 0.5f))
            }
        }
    }
}

// ═══════════════════════ 重命名对话框 ═══════════════════════

@Composable
private fun RenameDialog(path: String, onConfirm: (String?) -> Unit) {
    var name by remember(path) { mutableStateOf(path.substringAfterLast('/').removeSuffix(".rpy")) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { onConfirm(null) },
        containerColor = IdeColors.PanelBg,
        title = { Text("重命名文件") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    path,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = IdeColors.TextDim,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("新名称（自动保留 .rpy）") },
                    singleLine = true,
                )
                Text(
                    "也可以填写相对路径来移动文件，如 gui/custom.rpy",
                    fontSize = 11.sp, color = IdeColors.TextDim,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = { onConfirm(null) }) { Text("取消") } },
    )
}
