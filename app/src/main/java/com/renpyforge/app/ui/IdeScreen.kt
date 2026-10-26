@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.renpyforge.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

private enum class SidebarTab(val label: String) { FILES("文件"), ASSETS("资源") }

/** 可在编辑器里打开的文本类文件扩展名 */
private val EDITABLE_EXT = setOf(
    "rpy", "rpym", "py", "txt", "md", "json", "xml", "css", "js", "html", "ini", "cfg",
)

private fun isEditableFile(path: String): Boolean {
    val name = path.substringAfterLast('/')
    if (!name.contains('.')) return true
    return name.substringAfterLast('.').lowercase() in EDITABLE_EXT
}

// ═══════════════════════ IDE 主界面 ═══════════════════════

/**
 * IDE 布局：
 * ┌ 顶栏（文件 / ⋮ 菜单 / 构建台入口）
 * ├ 可拖拽侧栏（文件树 · 资源）+ 可缩放宽度
 * │   编辑器（标签 + 代码 + 双指缩放）
 * │   问题面板（可折叠，来自状态栏）
 * └ 状态栏
 * 进入 IDE 不显示主导航底栏。
 */
@Composable
fun IdeScreen(
    viewModel: ProjectViewModel,
    onOpenConsole: (Int) -> Unit,
) {
    var showNewFile by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    var showOutline by remember { mutableStateOf(false) }
    var showProjectSettings by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var showProblems by remember { mutableStateOf(false) }
    var sidebarOpen by rememberSaveable { mutableStateOf(true) }
    var sidebarWidth by rememberSaveable { mutableStateOf(132f) }
    var sidebarTab by rememberSaveable { mutableStateOf(SidebarTab.FILES) }

    val density = LocalDensity.current
    val clipboard = LocalClipboardManager.current

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.importGameAsset("image", it) }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.importGameAsset("audio", it) }
    }

    LaunchedEffect(viewModel.activeTab) {
        if (viewModel.activeTab != null) viewModel.refreshProblems()
    }

    Scaffold(
        containerColor = IdeColors.AppBg,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            IdeTopBar(
                viewModel = viewModel,
                onMenu = { sidebarOpen = !sidebarOpen },
                onSave = { viewModel.saveAllTabs() },
                onManual = { showManual = true },
                onSearch = { showSearch = true },
                onProjectSettings = { showProjectSettings = true },
                showTools = showTools,
                onToggleTools = { showTools = !showTools },
                onOutline = { showOutline = true },
                onImportImage = { imagePicker.launch("image/*") },
                onImportAudio = { audioPicker.launch("audio/*") },
                onBuild = { viewModel.saveAllTabs(); onOpenConsole(0) },
            )

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

            Row(Modifier.fillMaxWidth().weight(1f)) {
                AnimatedVisibility(
                    visible = sidebarOpen,
                    enter = expandHorizontally(tween(180)) + fadeIn(tween(160)),
                    exit = shrinkHorizontally(tween(160)) + fadeOut(tween(120)),
                ) {
                    Row(Modifier.fillMaxHeight()) {
                        SidebarPanel(
                            viewModel = viewModel,
                            tab = sidebarTab,
                            onTabChange = { sidebarTab = it },
                            width = sidebarWidth,
                            onClose = { sidebarOpen = false },
                            onNewFile = { showNewFile = true },
                            onRename = { renameTarget = it },
                            onCloseProject = { viewModel.closeProject() },
                            onPickImage = { imagePicker.launch("image/*") },
                            onPickAudio = { audioPicker.launch("audio/*") },
                            onCopyPath = { rel ->
                                clipboard.setText(AnnotatedString(rel))
                                viewModel.toast("已复制 $rel")
                            },
                        )
                        ResizeHandle(
                            onDrag = { deltaPx ->
                                val deltaDp = deltaPx / density.density
                                sidebarWidth = (sidebarWidth + deltaDp).coerceIn(96f, 260f)
                            },
                        )
                    }
                }

                Column(Modifier.fillMaxHeight().weight(1f)) {
                    EditorTabs(viewModel, onNewFile = { showNewFile = true })
                    Box(Modifier.weight(1f)) {
                        AnimatedContent(
                            targetState = viewModel.activeTab,
                            transitionSpec = {
                                (fadeIn(tween(160)) + slideInHorizontally(tween(200)) { it / 14 }) togetherWith
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
                                    fontScale = viewModel.editorFontScale,
                                    onFontScaleChange = { viewModel.changeEditorFontScale(it) },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = showProblems,
                        enter = expandVertically(tween(180)) + fadeIn(),
                        exit = shrinkVertically(tween(160)) + fadeOut(),
                    ) {
                        ProblemsPanel(
                            viewModel = viewModel,
                            onClose = { showProblems = false },
                        )
                    }
                    StatusBar(
                        viewModel = viewModel,
                        problemsOpen = showProblems,
                        onToggleProblems = { showProblems = !showProblems },
                        onOpenTerminal = { onOpenConsole(1) },
                        onOpenBuild = { viewModel.saveAllTabs(); onOpenConsole(0) },
                    )
                }
            }
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

// ═══════════════════════ 顶栏 ═══════════════════════

@Composable
private fun IdeTopBar(
    viewModel: ProjectViewModel,
    onMenu: () -> Unit,
    onSave: () -> Unit,
    onManual: () -> Unit,
    onSearch: () -> Unit,
    onProjectSettings: () -> Unit,
    showTools: Boolean,
    onToggleTools: () -> Unit,
    onOutline: () -> Unit,
    onImportImage: () -> Unit,
    onImportAudio: () -> Unit,
    onBuild: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(color = IdeColors.PanelBg, shadowElevation = 2.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(46.dp).padding(start = 2.dp, end = 6.dp),
        ) {
            IconButton(onClick = onMenu, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Menu, contentDescription = "侧栏", modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    viewModel.activeTab?.substringAfterLast('/') ?: "未打开文件",
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    (viewModel.currentProject ?: "") + " · game/",
                    fontSize = 10.sp, color = IdeColors.TextDim,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onBuild, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Build, contentDescription = "构建台", tint = IdeColors.OkGreen, modifier = Modifier.size(20.dp))
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多", modifier = Modifier.size(20.dp))
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = IdeColors.PanelBg,
                ) {
                    MenuRow("脚本大纲") { menuOpen = false; onOutline() }
                    MenuRow("编辑工具（撤销/查找/缩放/插入）", checked = showTools) {
                        menuOpen = false; onToggleTools()
                    }
                    MenuRow("全部保存" + if (viewModel.dirtyTabs.isNotEmpty()) "（${viewModel.dirtyTabs.size} 未保存）" else "") {
                        menuOpen = false; onSave()
                    }
                    MenuRow("全局搜索") { menuOpen = false; onSearch() }
                    MenuRow("代码手册") { menuOpen = false; onManual() }
                    HorizontalDivider(color = IdeColors.PanelBorder)
                    MenuRow("导入图片到 images/") { menuOpen = false; onImportImage() }
                    MenuRow("导入音频到 audio/") { menuOpen = false; onImportAudio() }
                    HorizontalDivider(color = IdeColors.PanelBorder)
                    MenuRow("项目设置（包名/名称/图标/启动图）") { menuOpen = false; onProjectSettings() }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(
    text: String,
    checked: Boolean? = null,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text, fontSize = 13.sp) },
        leadingIcon = if (checked != null) {
            {
                Icon(
                    if (checked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (checked) IdeColors.Accent else IdeColors.TextDim,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else null,
        onClick = onClick,
    )
}

// ═══════════════════════ 侧栏（文件 / 资源） ═══════════════════════

@Composable
private fun SidebarPanel(
    viewModel: ProjectViewModel,
    tab: SidebarTab,
    onTabChange: (SidebarTab) -> Unit,
    width: Float,
    onClose: () -> Unit,
    onNewFile: () -> Unit,
    onRename: (String) -> Unit,
    onCloseProject: () -> Unit,
    onPickImage: () -> Unit,
    onPickAudio: () -> Unit,
    onCopyPath: (String) -> Unit,
) {
    Surface(color = IdeColors.PanelBg, modifier = Modifier.width(width.dp).fillMaxHeight()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 6.dp),
            ) {
                SidebarTab.entries.forEach { t ->
                    val active = tab == t
                    Text(
                        t.label,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) IdeColors.Accent else IdeColors.TextDim,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (active) IdeColors.AccentDim else Color.Transparent)
                            .clickable { onTabChange(t) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "收起侧栏", tint = IdeColors.TextDim, modifier = Modifier.size(15.dp))
                }
            }
            HorizontalDivider(color = IdeColors.PanelBorder)

            when (tab) {
                SidebarTab.FILES -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 2.dp, top = 4.dp),
                    ) {
                        Text(
                            "game/",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = IdeColors.WarnOrange,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onNewFile, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Filled.Add, contentDescription = "新建文件", modifier = Modifier.size(15.dp))
                        }
                    }
                    FileTreeList(viewModel, onRename)
                    HorizontalDivider(color = IdeColors.PanelBorder)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onCloseProject)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Filled.Home, contentDescription = null, tint = IdeColors.TextDim, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("关闭项目", fontSize = 11.sp, color = IdeColors.TextDim)
                    }
                }

                SidebarTab.ASSETS -> {
                    ResourceList(viewModel, onPickImage, onPickAudio, onCopyPath)
                }
            }
        }
    }
}

@Composable
private fun ResizeHandle(onDrag: (Float) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(10.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(2.dp)
                .fillMaxHeight(0.3f)
                .background(IdeColors.PanelBorder)
        )
    }
}

@Composable
private fun ResourceList(
    viewModel: ProjectViewModel,
    onPickImage: () -> Unit,
    onPickAudio: () -> Unit,
    onCopyPath: (String) -> Unit,
) {
    val images = viewModel.resourceImages
    val audio = viewModel.resourceAudio

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                AssetAction("＋图片", Icons.Filled.Image, onPickImage)
                AssetAction("＋音频", Icons.Filled.AudioFile, onPickAudio)
            }
            HorizontalDivider(color = IdeColors.PanelBorder)
        }
        item { SectionLabel("图片 ${images.size}") }
        if (images.isEmpty()) {
            item { HintText("放入 game/images/ 后自动出现") }
        } else {
            items(images.size, key = { "img" + images[it].first.absolutePath }) { idx ->
                val (file, rel) = images[idx]
                AssetRow(file, rel, isImage = true, onClick = { onCopyPath(rel) })
            }
        }
        item { SectionLabel("音频 ${audio.size}") }
        if (audio.isEmpty()) {
            item { HintText("放入 game/audio/ 后自动出现") }
        } else {
            items(audio.size, key = { "aud" + audio[it].first.absolutePath }) { idx ->
                val (_, rel) = audio[idx]
                AssetRow(null, rel, isImage = false, onClick = { onCopyPath(rel) })
            }
        }
    }
}

@Composable
private fun AssetAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = IdeColors.Accent, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(3.dp))
        Text(label, fontSize = 10.sp, color = IdeColors.Accent)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 10.sp,
        color = IdeColors.TextDim,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 3.dp),
    )
}

@Composable
private fun HintText(text: String) {
    Text(
        text,
        fontSize = 10.sp,
        color = IdeColors.TextDim.copy(alpha = 0.7f),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun AssetRow(file: File?, rel: String, isImage: Boolean, onClick: () -> Unit) {
    var bitmap by remember(file?.absolutePath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(file?.absolutePath) {
        bitmap = if (file != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
                }.getOrNull()
            }
        } else null
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(IdeColors.HoverBg),
            contentAlignment = Alignment.Center,
        ) {
            if (isImage && bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(5.dp)),
                )
            } else {
                Icon(
                    if (isImage) Icons.Filled.Image else Icons.Filled.AudioFile,
                    contentDescription = null,
                    tint = if (isImage) IdeColors.WarnOrange else IdeColors.Accent,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            rel.substringAfterLast('/'),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ═══════════════════════ 编辑器标签行 ═══════════════════════

private fun scaleIn() = androidx.compose.animation.scaleIn(spring(dampingRatio = 0.6f))
private fun scaleOut() = androidx.compose.animation.scaleOut(spring(dampingRatio = 0.6f))

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
            modifier = Modifier.height(36.dp),
        ) {
            viewModel.openTabs.toList().forEach { path ->
                val active = path == viewModel.activeTab
                Tab(
                    selected = active,
                    onClick = { viewModel.activateTab(path) },
                    modifier = Modifier.height(36.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 10.dp, end = 2.dp),
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
                                    .size(5.dp)
                                    .background(IdeColors.WarnOrange, CircleShape)
                            )
                        }
                        IconButton(onClick = { viewModel.closeTab(path) }, modifier = Modifier.size(22.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "关闭", tint = IdeColors.TextDim, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
            Tab(selected = false, onClick = onNewFile, modifier = Modifier.height(36.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "新建脚本", tint = IdeColors.TextDim, modifier = Modifier.size(15.dp))
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
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text("从左侧文件树打开脚本", fontSize = 13.sp, color = IdeColors.TextDim)
            Spacer(Modifier.height(4.dp))
            Text("左右拖动侧栏边缘可调宽度", fontSize = 11.sp, color = IdeColors.TextDim.copy(alpha = 0.7f))
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onNewFile,
                colors = ButtonDefaults.buttonColors(containerColor = IdeColors.AccentDim, contentColor = IdeColors.TextFg),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("新建脚本", fontSize = 12.sp)
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
            cur = cur.dirs.getOrPut(parts[i]) { FsNode(parts[i], parts.subList(0, i + 1).joinToString("/")) }
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
    val files = remember(viewModel.gameFiles) { viewModel.gameFiles.filter(::isEditableFile) }
    val tree = remember(files) { buildFileTree(files) }
    var expanded by remember(files) {
        mutableStateOf(tree.dirs.values.map { it.path }.toSet())
    }
    val flat = remember(tree, expanded) { flattenTree(tree, expanded) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
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
                            .padding(start = (4 + node.depth * 10).dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                    ) {
                        Text("▸", fontSize = 10.sp, color = IdeColors.TextDim, modifier = Modifier.width(11.dp).rotate(rotation))
                        Icon(Icons.Filled.Folder, null, tint = IdeColors.WarnOrange, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(node.name, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                is TreeFile -> FileRow(node, viewModel, onRename)
            }
        }
    }
}

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
            .padding(start = (15 + node.depth * 10).dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Box(
            Modifier
                .size(5.dp)
                .background(
                    if (viewModel.dirtyTabs.contains(node.path)) IdeColors.WarnOrange else IdeColors.Accent.copy(alpha = 0.5f),
                    CircleShape,
                )
        )
        Spacer(Modifier.width(5.dp))
        Text(
            node.name,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = if (active) IdeColors.TextFg else IdeColors.ConsoleFg,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

// ═══════════════════════ 问题面板 ═══════════════════════

@Composable
private fun ProblemsPanel(viewModel: ProjectViewModel, onClose: () -> Unit) {
    Surface(color = IdeColors.ConsoleBg, shadowElevation = 6.dp) {
        Column(Modifier.fillMaxWidth().heightIn(max = 190.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(32.dp).padding(start = 10.dp, end = 2.dp),
            ) {
                Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = IdeColors.FailRed, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    "问题 ${viewModel.problems.size}",
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = if (viewModel.problems.isEmpty()) IdeColors.TextDim else IdeColors.FailRed,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    viewModel.activeTab ?: "",
                    fontSize = 10.sp, color = IdeColors.TextDim,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    fontFamily = FontFamily.Monospace,
                )
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", tint = IdeColors.TextDim, modifier = Modifier.size(14.dp))
                }
            }
            HorizontalDivider(color = IdeColors.PanelBorder)
            if (viewModel.problems.isEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Box(Modifier.size(6.dp).background(IdeColors.OkGreen, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text("未发现语法问题", fontSize = 11.sp, color = IdeColors.TextDim)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 154.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                    items(viewModel.problems.size, key = { "${viewModel.problems[it].line}-$it" }) { idx ->
                        val p = viewModel.problems[idx]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.requestJump(p.line, 1) }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                "Ln ${p.line}",
                                fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                color = IdeColors.FailRed,
                                modifier = Modifier.width(48.dp),
                            )
                            Text(
                                p.message,
                                fontSize = 11.sp, color = IdeColors.TextFg,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════ 状态栏 ═══════════════════════

@Composable
private fun StatusBar(
    viewModel: ProjectViewModel,
    problemsOpen: Boolean,
    onToggleProblems: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenBuild: () -> Unit,
) {
    Surface(color = IdeColors.PanelBg) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(28.dp).padding(end = 8.dp),
        ) {
            IconButton(onClick = onOpenTerminal, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Terminal, contentDescription = "终端", tint = IdeColors.TextDim, modifier = Modifier.size(15.dp))
            }
            IconButton(onClick = onOpenBuild, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Build, contentDescription = "构建台", tint = IdeColors.TextDim, modifier = Modifier.size(14.dp))
            }
            Text(
                viewModel.activeTab ?: "无打开文件",
                fontSize = 10.sp, color = IdeColors.TextDim, fontFamily = FontFamily.Monospace,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Text(
                "Ln ${viewModel.cursorLine}, Col ${viewModel.cursorCol}",
                fontSize = 10.sp, color = IdeColors.TextDim, fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (problemsOpen) IdeColors.FailRed.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable(onClick = onToggleProblems)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(if (viewModel.problems.isEmpty()) IdeColors.OkGreen else IdeColors.FailRed, CircleShape)
                )
                Text(
                    " ${viewModel.problems.size}",
                    fontSize = 10.sp,
                    color = if (viewModel.problems.isEmpty()) IdeColors.TextDim else IdeColors.FailRed,
                )
            }
            Spacer(Modifier.width(8.dp))
            val dotColor by animateColorAsState(
                when {
                    viewModel.building -> IdeColors.Accent
                    viewModel.dirtyTabs.isNotEmpty() -> IdeColors.WarnOrange
                    else -> IdeColors.OkGreen
                },
                tween(250), label = "statusDot",
            )
            Box(Modifier.size(6.dp).background(dotColor, CircleShape))
            Text(
                when {
                    viewModel.building -> "构建中"
                    viewModel.dirtyTabs.isNotEmpty() -> "未保存"
                    else -> "就绪"
                },
                fontSize = 10.sp, color = IdeColors.TextDim, modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

// ═══════════════════════ 对话框与手册 ═══════════════════════

@Composable
private fun NewFileDialog(viewModel: ProjectViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
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
                    "无扩展名 → game/script/<名称>.rpy\n带扩展名（如 bg.png）→ 按原名创建到 game/\n也可填写相对路径精确指定",
                    fontSize = 11.sp, color = IdeColors.TextDim,
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
        "常见错误对照（必读）", """✘ scene "87870.png"
✔ scene 87870

✘ show "立绘.jpg"
✔ show 立绘

✘ hide "立绘.jpg"
✔ hide 立绘

✘ game_menu.png          ← 单独一行文件名不是语句
✔ image game_menu = "game_menu.png"
✔ show game_menu""",
        "show/scene/hide 后面跟的是「图片名」（不带引号、不带扩展名）。图片放在 game/images/ 里时，文件名去掉扩展名就是图片名。带引号的写法会直接报 expected 'image_name_component' 错误。",
    ),
    ManualSection(
        "剧本基础", """label start:
    scene bg room
    show eileen happy
    "你好，世界！"
    return""",
        "label 是流程入口；scene 换背景；show 显示立绘；直接写字符串就是对话，前面加角色名变量表示谁在说话。",
    ),
    ManualSection(
        "图像语句详解", """# 方式一：图片放 game/images/，文件名自动注册
#   images/87870.png      → scene 87870
#   images/bg room.png    → scene bg room（空格分属性）
#   images/eileen happy.png → show eileen happy

# 方式二：显式定义（图片不在 images/ 或想用别名）
image bg room = "images/room.jpg"
image eileen = "chara/eileen.png"

# 位置与属性
show eileen at left
show eileen happy        # 属性叠加
hide eileen
scene bg room with dissolve""",
        "scene 会清空立绘层并显示背景；show 叠加显示；hide 隐藏。at 指定位置（left/right/center）。文件名中的空格会成为图片属性，方便用 show eileen happy 这类组合。",
    ),
    ManualSection(
        "音频语句", """play music "audio/bgm.ogg" fadein 1.0
play sound "audio/click.wav"
play ambient "audio/rain.ogg" fadein 2.0
queue music "audio/next.ogg"    # 排队下一首
stop music fadeout 1.0

# 音量调节（0.0 ~ 1.0）
play music "audio/bgm.ogg" volume 0.6""",
        "music 循环播放、sound 一次性音效、ambient 环境音、queue 排队不中断。音频文件放 game/audio/，路径带扩展名和引号（这里引号是正确的！与 show/scene 不同）。",
    ),
    ManualSection(
        "选择分支", """menu:
    "去图书馆":
        jump library
    "回家睡觉":
        jump home

# 带条件/次数限制的选项
menu:
    "牵手" if points >= 3:
        jump love_end
    "回家":
        jump home""",
        "menu: 创建选项；选项后可加 if 条件控制显示；jump 跳走，也可以直接在选项下写对话后 fallthrough。",
    ),
    ManualSection(
        "跳转与标签", """label library:
    "这里很安静。"
    jump start

# call 会记住来源，return 回来
label chapter1:
    call event_a
    "继续主线。"

label event_a:
    "事件内容……"
    return""",
        "jump 是「跳过去不回头」；call 是「调用子程序」，子程序 return 后回到 call 的下一句。同一项目内 label 名不能重复。",
    ),
    ManualSection(
        "变量", """default points = 0
define e = Character("艾琳")

label start:
    $ points += 1
    if points >= 3:
        e "好感度很高嘛。"
    elif points == 2:
        e "感觉还不错。"
    else:
        e "初次见面。\"""",
        "default 定义存档变量（会存进档）；define 定义常量（角色、图像等，不存档）；$ 行执行 Python；if/elif/else 条件分支。",
    ),
    ManualSection(
        "角色进阶", """define e = Character(
    "艾琳",
    color="#88ccff",          # 名字颜色
    what_prefix="「",          # 台词前缀
    what_suffix="」",          # 台词后缀
    who_suffix="：",
)

define narrator = Character(None)   # 无名旁白
define centered = Character(None, kind=centered)""",
        "Character 支持名字颜色、前后缀、样式等参数。kind 可继承已有角色配置。None 表示旁白（不显示说话人）。",
    ),
    ManualSection(
        "转场大全", """with dissolve              # 淡入淡出（最常用）
with fade                  # 先黑屏再淡入
with move                  # 移动
with pixellate             # 像素化
with blinds                # 百叶窗
with flash                 # 闪白（雷声/枪声）
with vpunch                # 垂直震动（撞击）

# 自定义时长
with Dissolve(1.5)
$ renpy.transition(Fade(0.5, 0.5, 0.5))""",
        "with 应用在画面变化（scene/show/hide）之后。常用的有 dissolve / fade / move / pixellate / flash / hpunch / vpunch。",
    ),
    ManualSection(
        "界面 screen", """screen stats():
    frame:
        vbox:
            text "好感度: [points]"
            textbutton "关闭" action Return()

label start:
    show screen stats
    "..."
    hide screen stats

# 模态（阻断点击穿透）与交互式
call screen stats""",
        "screen 定义 UI；[变量] 插值显示；textbutton + action 响应点击。show/hide screen 非阻断；call screen 阻断并等待 Return 值。",
    ),
    ManualSection(
        "call screen 交互", """screen choice_ui():
    vbox:
        textbutton "吃蛋糕" action Return("cake")
        textbutton "吃苹果" action Return("apple")

label start:
    $ result = renpy.call_screen("choice_ui")
    if result == "cake":
        "你选择了蛋糕。\"""",
        "call screen 会暂停剧情等待玩家点击，按钮的 Return(值) 会作为返回值，可用来做复杂选择界面、小游戏入口。",
    ),
    ManualSection(
        "常用 action 速查", """textbutton "回标题" action ShowMenu("main_menu")
textbutton "跳转" action Jump("chapter2")
textbutton "显示界面" action Show("stats")
textbutton "隐藏界面" action Hide("stats")
textbutton "存档" action FileSave(1)
textbutton "读档" action FileLoad(1)
textbutton "设置变量" action SetVariable("points", 10)
textbutton "加一点" action ToggleVariable("flag")
textbutton "退出游戏" action Quit(confirm=True)""",
        "action 是按钮被点击时执行的动作；常用有 Jump / Show / Hide / ShowMenu / FileSave / FileLoad / SetVariable / ToggleVariable / Quit。",
    ),
    ManualSection(
        "文本插值与格式", """label start:
    $ name = "艾琳"
    $ score = 97
    "你好，[name]！得分 [score] 分。"
    "得分是 [score!q]"      # !q 转义
    "进度 [[50%]"           # [[ 显示字面 [
    "换行\\n第二行"""",
        "[变量] 会替换为值；[[ 输出字面的 [；\\n 换行。对话里也能直接用插值。",
    ),
    ManualSection(
        "玩家输入", """define p = Character("[player_name]")

label start:
    $ player_name = renpy.input("你叫什么名字？", length=20)
    if player_name == "":
        $ player_name = "路人"
    p "我叫 [player_name]。\"""",
        "renpy.input 弹出输入框返回字符串；记得给默认值。配合 Character(\"[player_name]\") 让名字实时生效。",
    ),
    ManualSection(
        "循环与随机", """label start:
    $ days = ["周一", "周二", "周三"]
    $ i = 0
    while i < len(days):
        "今天是 [days[i]]"
        $ i += 1

    $ weather = renpy.random.choice(["晴", "雨", "雪"])
    "今天天气：[weather]\"""",
        "while 循环；renpy.random.choice 随机选取、randint(a,b) 随机整数。注意循环里要有跳出条件，否则死循环。",
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
    easein 0.5 xoffset 0

# 循环动画
transform float:
    yoffset 0
    block:
        linear 1.0 yoffset -10
        linear 1.0 yoffset 0
        repeat""",
        "show 时接 ATL 块做位移动画；transform 定义可复用动画；easein/linear 是插值方式；block + repeat 做循环。",
    ),
    ManualSection(
        "语音与旁白", """voice "audio/vo_001.ogg"
e "这句会配上语音。"

# config 控制语音在点击后停止
define config.voice_sustain = False""",
        "voice 播放一次性配音；与 play sound 不同，voice 专用于角色语音，切换台词自动停止。",
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
3. 顶栏 🛠 打开构建台
4. 在构建台点「开始构建」
5. 构建完成 → 安装""",
        "构建会先保存所有未保存的文件。产物 APK 位于存储根目录 RenPyForge/output/。",
    ),
    ManualSection(
        "资源导入", """game/
├── script.rpy
├── images/       ← 图片（左侧「资源」页可导入）
│   └── bg room.png   → scene bg room
└── audio/        ← 音频（左侧「资源」页可导入）
    └── bgm.ogg       → play music "audio/bgm.ogg\"""",
        "images/ 下的图片文件名（去扩展名）自动成为图像名；空格即空格：\"bg room.png\" 对应 scene bg room。点击资源项可复制相对路径。",
    ),
    ManualSection(
        "文件管理与传输", """手机存储根目录：
RenPyForge/projects/<项目名>/game/""",
        "项目是普通文件夹：用文件管理器、USB 连电脑均可拷入图片/音频/字体。改完回 App 里直接构建。",
    ),
    ManualSection(
        "构建配置", """包名：org.renpy.forge.<项目名>
  （IDE 菜单 → 项目设置可改）
游戏名：显示在桌面图标下
图标 / 启动图：项目设置里选择图片
签名：内置密钥，同一包名
      覆盖安装即可更新""",
        "改包名会生成一个「新应用」而非覆盖旧版；想更新已安装的游戏请保持包名不变。启动图会替换 assets/android-presplash.jpg。",
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
        "高级权限（Root / Shizuku）", """设置 → 高级权限：
· Root：检测到 su 后，终端可切到 Root 模式
· Shizuku：安装并启动 Shizuku 后申请授权
终端页顶部可切换 本机 / Root / Shizuku
构建产物支持静默安装（pm install）""",
        "提权是可选的：不授权也能正常构建。Shizuku 以 shell 身份执行，权限比 Root 低但无需 root；静默安装优先使用 Root。",
    ),
    ManualSection(
        "常见问题", """✘ expected 'image_name_component'
  → show/scene/hide 带了引号，去掉引号和扩展名
✘ expected statement（单独文件名）
  → 删掉该行，或用 image 名 = "文件" 定义
✘ 构建失败 → 看构建台日志的 ✘ 行
✘ 脚本报错 → 检查缩进必须是 4 空格
✘ 图片不显示 → 检查文件名与 scene/show 一致
✘ 中文乱码 → 使用支持中文的字体文件""",
        "写错语法时编辑器底部「问题」面板会当场标红并给出修法，不用等游戏运行报错。游戏运行报错会显示 traceback，对照行号回编辑器修复。",
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
                                .horizontalScroll(rememberScrollState())
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
    AlertDialog(
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
