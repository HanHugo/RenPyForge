@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.renpyforge.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

// ═══════════════════════ IDE 主界面（响应式 IDE 布局） ═══════════════════════

/**
 * 真·IDE 布局：
 * · 宽屏（≥640dp）：文件树常驻左栏，与编辑器左右分栏（桌面 IDE 形态）
 * · 窄屏：文件树走 MD3 抽屉（边缘手势可拉出）
 * · 紧凑单行工具条（无大标题栏），标签行紧贴编辑器
 * · 构建台 / 终端是独立全屏页面（由根导航承载），不占用编辑区
 */
@Composable
fun IdeScreen(
    viewModel: ProjectViewModel,
    onBuild: () -> Unit,
    onOpenConsole: (Int) -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showNewFile by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    var showOutline by remember { mutableStateOf(false) }
    var showProjectSettings by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel.activeTab) {
        if (viewModel.activeTab != null) viewModel.refreshProblems()
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(IdeColors.AppBg)) {
        val wide = maxWidth >= 640.dp

        val treeContent: @Composable () -> Unit = {
            FileTreePanel(
                viewModel = viewModel,
                onClose = if (wide) ({ }) else ({ scope.launch { drawerState.close() } }),
                showClose = !wide,
                onNewFile = { showNewFile = true },
                onCloseProject = { viewModel.closeProject() },
                onRename = { renameTarget = it },
            )
        }

        val ideBody: @Composable () -> Unit = {
            Column(Modifier.fillMaxSize()) {
                // —— 紧凑工具行 ——
                IdeToolbarRow(
                    viewModel = viewModel,
                    showMenu = !wide,
                    onMenu = { scope.launch { drawerState.open() } },
                    onSave = { viewModel.saveAllTabs() },
                    onManual = { showManual = true },
                    onSearch = { showSearch = true },
                    onProjectSettings = { showProjectSettings = true },
                    showTools = showTools,
                    onToggleTools = { showTools = !showTools },
                    onOutline = { showOutline = true },
                    onBuild = onBuild,
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

                // —— 编辑器标签行 ——
                EditorTabs(viewModel, onNewFile = { showNewFile = true })

                // —— 编辑区 ——
                Box(Modifier.weight(1f)) {
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
                                showTools = showTools,
                                jumpLine = viewModel.jumpRequest?.first ?: 0,
                                onJumpConsumed = { viewModel.consumeJump() },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }

                StatusBar(
                    viewModel = viewModel,
                    onOpenTerminal = { onOpenConsole(1) },
                    onOpenProblems = { onOpenConsole(2) },
                )
            }
        }

        if (wide) {
            // ── 桌面 IDE 形态：文件树常驻左栏 ──
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(250.dp).fillMaxHeight()) { treeContent() }
                Box(Modifier.weight(1f).fillMaxHeight()) { ideBody() }
            }
        } else {
            // ── 手机形态：抽屉文件树 ──
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = IdeColors.PanelBg,
                        windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                    ) { treeContent() }
                },
            ) { ideBody() }
        }
    }

    if (showNewFile) NewFileDialog(viewModel) { showNewFile = false }
    if (showManual) ManualSheet { showManual = false }
    if (showSearch) GlobalSearchSheet(viewModel) { showSearch = false }
    if (showOutline) OutlineSheet(viewModel) { showOutline = false }
    if (showProjectSettings) ProjectSettingsSheet(viewModel) { showProjectSettings = false }
    renameTarget?.let { target ->
        RenameDialog(target) { newName ->
            if (newName != null) viewModel.renameGameFile(target, newName)
            renameTarget = null
        }
    }
}

// ═══════════════════════ 紧凑工具行 ═══════════════════════

/**
 * 顶栏极简：☰ / 当前文件 / ⋮ 菜单（保存·搜索·手册·项目设置）/ ▶ 构建
 */
@Composable
private fun IdeToolbarRow(
    viewModel: ProjectViewModel,
    showMenu: Boolean,
    onMenu: () -> Unit,
    onSave: () -> Unit,
    onManual: () -> Unit,
    onSearch: () -> Unit,
    onProjectSettings: () -> Unit,
    showTools: Boolean,
    onToggleTools: () -> Unit,
    onOutline: () -> Unit,
    onBuild: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(color = IdeColors.PanelBg, shadowElevation = 2.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(start = 2.dp, end = 6.dp),
        ) {
            if (showMenu) {
                IconButton(onClick = onMenu) {
                    Icon(Icons.Filled.Menu, contentDescription = "文件树", modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    viewModel.activeTab?.substringAfterLast('/') ?: "无打开文件",
                    fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    viewModel.currentProject ?: "",
                    fontSize = 10.sp, color = IdeColors.TextDim,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多", modifier = Modifier.size(22.dp))
            }
            androidx.compose.material3.DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = IdeColors.PanelBg,
            ) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("脚本大纲") },
                    leadingIcon = { Icon(Icons.Filled.List, null, modifier = Modifier.size(18.dp)) },
                    onClick = { menuOpen = false; onOutline() },
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("编辑工具（撤销/查找/插入）") },
                    leadingIcon = {
                        Icon(
                            if (showTools) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            null, tint = if (showTools) IdeColors.Accent else IdeColors.TextDim,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = { menuOpen = false; onToggleTools() },
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("全部保存" + if (viewModel.dirtyTabs.isNotEmpty()) "（${viewModel.dirtyTabs.size} 个未保存）" else "") },
                    leadingIcon = { Icon(Icons.Filled.Save, null, tint = IdeColors.WarnOrange, modifier = Modifier.size(18.dp)) },
                    onClick = { menuOpen = false; onSave() },
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("全局搜索") },
                    leadingIcon = { Icon(Icons.Filled.Search, null, modifier = Modifier.size(18.dp)) },
                    onClick = { menuOpen = false; onSearch() },
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("代码手册") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null, modifier = Modifier.size(18.dp)) },
                    onClick = { menuOpen = false; onManual() },
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("项目设置（包名/名称/图标/启动图）") },
                    leadingIcon = { Icon(Icons.Filled.Settings, null, modifier = Modifier.size(18.dp)) },
                    onClick = { menuOpen = false; onProjectSettings() },
                )
            }
            Button(
                onClick = onBuild,
                enabled = !viewModel.building,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (viewModel.building) IdeColors.OkGreen.copy(alpha = 0.4f) else IdeColors.OkGreen,
                    contentColor = Color.White,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(32.dp),
            ) {
                Icon(
                    if (viewModel.building) Icons.Filled.Build else Icons.Filled.PlayArrow,
                    contentDescription = null, modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(if (viewModel.building) "构建中" else "构建", fontSize = 13.sp)
            }
        }
    }
}

private fun scaleIn() = androidx.compose.animation.scaleIn(spring(dampingRatio = 0.6f))
private fun scaleOut() = androidx.compose.animation.scaleOut(spring(dampingRatio = 0.6f))

// ═══════════════════════ 编辑器标签行（MD3 ScrollableTabRow） ═══════════════════════

@Composable
private fun EditorTabs(viewModel: ProjectViewModel, onNewFile: () -> Unit) {
    AnimatedVisibility(
        visible = viewModel.openTabs.isNotEmpty(),
        enter = expandVertically(tween(180)) + fadeIn(),
        exit = shrinkVertically(tween(180)) + fadeOut(),
    ) {
        ScrollableTabRow(
            selectedTabIndex = viewModel.openTabs.indexOf(viewModel.activeTab).coerceAtLeast(0),
            edgePadding = 4.dp,
            containerColor = IdeColors.PanelBg,
            contentColor = IdeColors.TextFg,
            divider = {},
            modifier = Modifier.height(38.dp),
        ) {
            viewModel.openTabs.toList().forEach { path ->
                val active = path == viewModel.activeTab
                Tab(
                    selected = active,
                    onClick = { viewModel.activateTab(path) },
                    modifier = Modifier.height(38.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                    ) {
                        Text(
                            path.substringAfterLast('/'),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (active) IdeColors.Accent else IdeColors.TextDim,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        AnimatedVisibility(
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
            // 末尾「＋」新建标签
            Tab(selected = false, onClick = onNewFile, modifier = Modifier.height(38.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "新建脚本", tint = IdeColors.TextDim, modifier = Modifier.size(16.dp))
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
            Text("从文件树打开脚本", fontSize = 14.sp, color = IdeColors.TextDim)
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

// ═══════════════════════ 文件树面板 ═══════════════════════

@Composable
private fun FileTreePanel(
    viewModel: ProjectViewModel,
    onClose: () -> Unit,
    showClose: Boolean,
    onNewFile: () -> Unit,
    onCloseProject: () -> Unit,
    onRename: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .padding(horizontal = 16.dp),
        ) {
            Icon(Icons.Filled.Folder, null, tint = IdeColors.WarnOrange, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("game/", fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onNewFile, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "新建脚本", modifier = Modifier.size(18.dp))
            }
            if (showClose) {
                IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", modifier = Modifier.size(18.dp))
                }
            }
        }
        HorizontalDivider(color = IdeColors.PanelBorder)
        Text(
            "长按文件可重命名",
            fontSize = 10.sp,
            color = IdeColors.TextDim.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        FileTreeList(viewModel, onRename)
        HorizontalDivider(color = IdeColors.PanelBorder)
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
        if (showClose) {
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
                            modifier = Modifier.width(14.dp).rotate(rotation),
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

// ═══════════════════════ 状态栏 ═══════════════════════

@Composable
private fun StatusBar(
    viewModel: ProjectViewModel,
    onOpenTerminal: () -> Unit,
    onOpenProblems: () -> Unit,
) {
    Surface(color = IdeColors.PanelBg) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .padding(start = 0.dp, end = 10.dp),
        ) {
            // 打开独立终端页（不在工具栏，属于控制台页）
            IconButton(onClick = onOpenTerminal, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Filled.Terminal,
                    contentDescription = "终端",
                    tint = IdeColors.TextDim,
                    modifier = Modifier.size(16.dp),
                )
            }
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
            if (viewModel.problems.isNotEmpty()) {
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
        title = { Text("新建文件") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("文件名") },
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
        "存档与读档", """screen save_ui():
    textbutton "存档" action FileSave(1)
    textbutton "读档" action FileLoad(1)
    textbutton "删档" action FileDelete(1)

label start:
    "自动存档已内置，手动存档用 FileSave。"
""",
        "FileSave/FileLoad/FileDelete 是标准存档动作；Ren'Py 自动管理存档槽与截图。",
    ),
    ManualSection(
        "persistent 跨周目", """default persistent.unlocked_end = False

label good_end:
    $ persistent.unlocked_end = True
    "真结局已解锁！"

label title:
    if persistent.unlocked_end:
        textbutton " Extra 剧情" action Start("extra")""",
        "persistent 变量跨存档、跨周目存在，适合做 CG 回廊、结局收集、已读跳过。",
    ),
    ManualSection(
        "界面布局容器", """screen shop():
    fixed:
        add "bg shop"
    hbox:
        spacing 10
        textbutton "买" action Return("buy")
    grid 2 2:
        textbutton "1"
        textbutton "2"
        textbutton "3"
        textbutton "4\"""",
        "fixed 层叠、hbox/vbox 排列、grid 网格、viewport 滚动容器，组合出任意界面。",
    ),
    ManualSection(
        "样式 style", """style my_text is text:
    size 30
    color "#ffffff"
    outlines [(2, "#000000", 0, 0)]

screen title():
    text "标题" style "my_text\"""",
        "style 继承现有样式并覆盖属性；outlines 做描边；也可在 text 后直接内联 size/color。",
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
        "Python 块", """init python:
    def double(x):
        return x * 2

label start:
    $ answer = double(21)
    "答案是 [answer]"
""",
        "init python 在启动时执行（定义函数/常量）；$ 单行；python: 块多行。",
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
        "调试工具", """define config.developer = True

# 电脑模拟器/PC 上可用：
#   Shift+R  重载脚本（热重载）
#   Shift+O  打开控制台
#   Shift+D  开发者菜单""",
        "developer 模式解锁 Ren'Py 全套调试工具；出错时的 traceback 会精确到行号。",
    ),
    ManualSection(
        "热重载（进阶技巧）", """# 监视文件 → 主线程回调 → 快速重启
init python:
    import threading, time, os
    HOTLOAD = "/sdcard/RenPyForge/hotload"   # 改动后的文件放这里（保持相对路径）

    def _rf_target(rel):
        # 关键：APK 里的脚本以 x- 前缀虚拟名加载（每级一段），
        # 同步文件必须写成同名 x- 文件才能覆盖资产版本，
        # 否则会加载两份脚本，触发 default/label 重复定义报错。
        parts = rel.split("/")
        return os.path.join(renpy.config.gamedir, *("x-" + p for p in parts))

    _state = {"dirty": False}

    def _rf_sync():
        for root, dirs, files in os.walk(HOTLOAD):
            rel_root = os.path.relpath(root, HOTLOAD)
            for f in files:
                rel = f if rel_root == "." else rel_root + "/" + f
                src = os.path.join(root, f)
                dst = _rf_target(rel)
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                with open(src, "rb") as a, open(dst, "wb") as b:
                    b.write(a.read())
                if dst.endswith(".rpy"):        # 删旧缓存，强制重编译
                    try: os.unlink(dst + "c")
                    except OSError: pass

    def _rf_watch():
        while True:
            if _hotload_changed():              # 自行实现 mtime 轮询
                _state["dirty"] = True
            time.sleep(2)

    def _rf_on_interact():
        if _state["dirty"]:
            _state["dirty"] = False
            _rf_sync()
            renpy.utter_restart()               # 进程内快速重启（回到开场）

    config.interact_callbacks.append(_rf_on_interact)
    threading.Thread(target=_rf_watch, daemon=True).start()""",
        "关键点：① 重启必须在主线程（interact 回调），监视线程只置标志；" +
            "② 同步文件必须按 x- 前缀命名（见 _rf_target），否则同一脚本被加载两份，" +
            "报 “is being given a default a second time” 或 label 重复定义；" +
            "③ utter_restart 秒级生效但回到开场，要保留进度可改用 renpy.reload_script()；" +
            "④ 同步目录用普通共享目录（如 /sdcard/RenPyForge/hotload），游戏读它需要" +
            "系统设置里授予该游戏「所有文件访问」权限。",
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

// ═══════════════════════ 脚本大纲 ═══════════════════════

@Composable
private fun OutlineSheet(
    viewModel: ProjectViewModel,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = IdeColors.PanelBg,
        contentColor = IdeColors.TextFg,
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("脚本大纲", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                viewModel.activeTab ?: "无打开文件",
                fontSize = 11.sp, color = IdeColors.TextDim, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )
        }
        val items = viewModel.outline()
        if (items.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(bottom = 48.dp), contentAlignment = Alignment.Center) {
                Text(
                    "当前文件没有 label / screen / define 等定义",
                    fontSize = 12.sp, color = IdeColors.TextDim,
                )
            }
            return@ModalBottomSheet
        }
        LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            items(items.size, key = { "${items[it].kind}-${items[it].name}-${items[it].line}" }) { idx ->
                val item = items[idx]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.requestJump(item.line, 1)
                            onDismiss()
                        }
                        .padding(vertical = 8.dp),
                ) {
                    Text(
                        when (item.kind) {
                            "label" -> "▶"
                            "screen" -> "▦"
                            "define" -> "≡"
                            "default" -> "◇"
                            "transform" -> "✦"
                            "style" -> "S"
                            else -> "🖼"
                        },
                        fontSize = 13.sp, color = IdeColors.Accent,
                        modifier = Modifier.width(26.dp),
                    )
                    Text(item.name, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("Ln ${item.line}", fontSize = 11.sp, color = IdeColors.TextDim, fontFamily = FontFamily.Monospace)
                }
                HorizontalDivider(color = IdeColors.PanelBorder.copy(alpha = 0.4f))
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
