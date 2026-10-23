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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.clip
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
import java.io.File

private val consoleTabs = listOf("构建", "终端", "问题", "资源")

/**
 * 独立控制台页（从 IDE 工具栏分离）：构建 / 终端 / 问题 / 资源四个页签。
 * 构建开始时自动切入本页第一条页签。
 */
@Composable
fun ConsoleScreen(
    viewModel: ProjectViewModel,
    initialTab: Int,
    onClose: () -> Unit,
    onInstall: (File) -> Unit,
) {
    var tab by remember { mutableIntStateOf(initialTab.coerceIn(0, consoleTabs.size - 1)) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshBuildHistory()
        viewModel.refreshResources()
    }

    Scaffold(
        containerColor = IdeColors.AppBg,
        topBar = {
            ConsoleTopBar(
                viewModel = viewModel,
                tab = tab,
                onBack = onClose,
                onSettings = { showSettings = true },
                onBuild = { viewModel.saveAllTabs(); viewModel.startBuild() },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 页签：MD3 分段按钮
            Surface(color = IdeColors.PanelBg) {
                SingleChoiceSegmentedButtonRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    consoleTabs.forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = tab == i,
                            onClick = { tab = i },
                            shape = SegmentedButtonDefaults.itemShape(i, consoleTabs.size),
                            label = {
                                Text(
                                    if (i == 2 && viewModel.problems.isNotEmpty()) "$label ${viewModel.problems.size}" else label,
                                    fontSize = 12.sp,
                                )
                            },
                        )
                    }
                }
            }
            HorizontalDivider(color = IdeColors.PanelBorder)

            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "consoleTab",
                modifier = Modifier.weight(1f),
            ) { t ->
                when (t) {
                    0 -> BuildPane(viewModel, onInstall, onOpenSettings = { showSettings = true })
                    1 -> TerminalPane(viewModel)
                    2 -> ProblemsPane(viewModel)
                    else -> ResourcePane(viewModel)
                }
            }
        }
    }

    if (showSettings) ProjectSettingsSheet(viewModel) { showSettings = false }
}

@Composable
private fun ConsoleTopBar(
    viewModel: ProjectViewModel,
    tab: Int,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onBuild: () -> Unit,
) {
    Surface(color = IdeColors.PanelBg, shadowElevation = 2.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(start = 4.dp, end = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(consoleTabs[tab], fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    viewModel.currentProject ?: "",
                    fontSize = 10.sp, color = IdeColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "项目设置", modifier = Modifier.size(20.dp))
            }
            if (tab == 0) {
                Button(
                    onClick = onBuild,
                    enabled = !viewModel.building,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (viewModel.building) IdeColors.OkGreen.copy(alpha = 0.4f) else IdeColors.OkGreen,
                        contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp),
                ) {
                    Icon(
                        if (viewModel.building) Icons.Filled.Build else Icons.Filled.PlayArrow,
                        contentDescription = null, modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (viewModel.building) "构建中" else if (viewModel.builtApk != null) "重新构建" else "开始构建", fontSize = 12.sp)
                }
            } else {
                IconButton(onClick = onBuild, enabled = !viewModel.building) {
                    Icon(
                        Icons.Filled.PlayArrow, contentDescription = "构建",
                        tint = if (viewModel.building) IdeColors.TextDim else IdeColors.OkGreen,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

// ═══════════════════════ 构建页 ═══════════════════════

@Composable
private fun BuildPane(viewModel: ProjectViewModel, onInstall: (File) -> Unit, onOpenSettings: () -> Unit) {
    val logState = rememberLazyListState()
    LaunchedEffect(viewModel.console.size) {
        if (viewModel.console.isNotEmpty()) logState.scrollToItem(viewModel.console.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { BuildConfigCard(viewModel, onOpenSettings) }
            item { BuildStepsCard(viewModel) }
            if (viewModel.builtApk != null) {
                item { ArtifactCard(viewModel.builtApk!!, onInstall) }
            }
            viewModel.error?.let { err ->
                item {
                    Text(
                        err,
                        color = IdeColors.FailRed, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(IdeColors.FailRed.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                    )
                }
            }
            if (viewModel.buildHistory.isNotEmpty()) {
                item { BuildHistoryCard(viewModel, onInstall) }
            }
        }

        // 日志
        Surface(color = IdeColors.ConsoleBg, shadowElevation = 6.dp, shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)) {
            Column(Modifier.fillMaxWidth().height(210.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                ) {
                    Text("构建日志", fontSize = 11.sp, color = IdeColors.TextDim, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    val clipboard = LocalClipboardManager.current
                    TextButton(
                        onClick = { clipboard.setText(AnnotatedString(viewModel.consoleText())) },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { Text("复制", fontSize = 11.sp) }
                    TextButton(
                        onClick = { viewModel.clearConsole() },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { Text("清空", fontSize = 11.sp) }
                }
                HorizontalDivider(color = IdeColors.PanelBorder)
                LazyColumn(state = logState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
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
    }
}

@Composable
private fun BuildConfigCard(viewModel: ProjectViewModel, onOpenSettings: () -> Unit) {
    val cfg = viewModel.projectConfig ?: return
    Surface(
        color = IdeColors.PanelBg,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, IdeColors.PanelBorder, RoundedCornerShape(14.dp)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            val iconFile = viewModel.projectImage("icon")
            val bmp = remember(iconFile?.absolutePath) {
                iconFile?.let {
                    runCatching {
                        android.graphics.BitmapFactory.decodeFile(
                            it.absolutePath,
                            android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 },
                        )
                    }.getOrNull()
                }
            }
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(IdeColors.HoverBg),
                contentAlignment = Alignment.Center,
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    Text("无图标", fontSize = 9.sp, color = IdeColors.TextDim)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(cfg.appName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(cfg.packageName, fontSize = 11.sp, color = IdeColors.TextDim, fontFamily = FontFamily.Monospace)
                Text(
                    "启动图：" + if (viewModel.projectImage("splash") != null) "已自定义" else "默认",
                    fontSize = 10.sp, color = IdeColors.TextDim,
                )
            }
            TextButton(onClick = onOpenSettings) { Text("修改", fontSize = 12.sp) }
        }
    }
}

@Composable
private fun BuildStepsCard(viewModel: ProjectViewModel) {
    Surface(
        color = IdeColors.PanelBg,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, IdeColors.PanelBorder, RoundedCornerShape(14.dp)),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                "构建流程",
                fontSize = 11.sp, color = IdeColors.TextDim, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
            viewModel.stepStates.forEachIndexed { idx, (id, state) ->
                val dotColor by animateColorAsState(
                    when (state) {
                        StepState.PENDING -> IdeColors.TextDim.copy(alpha = 0.35f)
                        StepState.RUNNING -> IdeColors.Accent
                        StepState.OK -> IdeColors.OkGreen
                        StepState.FAILED -> IdeColors.FailRed
                        StepState.SKIPPED -> IdeColors.WarnOrange
                    },
                    tween(220), label = "stepDot",
                )
                val statusText = when (state) {
                    StepState.PENDING -> "等待"
                    StepState.RUNNING -> "进行中"
                    StepState.OK -> "完成"
                    StepState.FAILED -> "失败"
                    StepState.SKIPPED -> "跳过"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
                ) {
                    Box(Modifier.size(8.dp).background(dotColor, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${idx + 1}. ${id.label}",
                        fontSize = 13.sp,
                        color = if (state == StepState.PENDING) IdeColors.TextDim else IdeColors.TextFg,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        statusText, fontSize = 11.sp,
                        color = if (state == StepState.PENDING) IdeColors.TextDim else dotColor,
                    )
                }
            }
            if (viewModel.building) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp).height(3.dp),
                    color = IdeColors.Accent,
                    trackColor = IdeColors.HoverBg,
                )
            }
        }
    }
}

@Composable
private fun ArtifactCard(apk: File, onInstall: (File) -> Unit) {
    Surface(
        color = IdeColors.AccentDim,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = IdeColors.OkGreen, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("构建成功", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "${apk.name}（${apk.length() / 1024 / 1024} MB）",
                    fontSize = 11.sp, color = IdeColors.TextDim,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Button(
                onClick = { onInstall(apk) },
                colors = ButtonDefaults.buttonColors(containerColor = IdeColors.OkGreen, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.height(34.dp),
            ) {
                Icon(Icons.Filled.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("安装", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun BuildHistoryCard(viewModel: ProjectViewModel, onInstall: (File) -> Unit) {
    Surface(
        color = IdeColors.PanelBg,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, IdeColors.PanelBorder, RoundedCornerShape(14.dp)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("历史产物", fontSize = 11.sp, color = IdeColors.TextDim, fontWeight = FontWeight.SemiBold)
            viewModel.buildHistory.take(5).forEach { artifact ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            artifact.file.name,
                            fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${artifact.sizeMb} MB · " + java.text.SimpleDateFormat(
                                "MM-dd HH:mm", java.util.Locale.getDefault()
                            ).format(artifact.modified),
                            fontSize = 9.sp, color = IdeColors.TextDim,
                        )
                    }
                    IconButton(onClick = { viewModel.deleteArtifact(artifact.file) }, modifier = Modifier.size(26.dp)) {
                        Icon(
                            Icons.Filled.Delete, contentDescription = "删除",
                            tint = IdeColors.FailRed.copy(alpha = 0.6f), modifier = Modifier.size(14.dp),
                        )
                    }
                    Button(
                        onClick = { onInstall(artifact.file) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IdeColors.AccentDim, contentColor = IdeColors.TextFg,
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.height(26.dp),
                    ) { Text("安装", fontSize = 10.sp) }
                }
            }
        }
    }
}

// ═══════════════════════ 终端页 ═══════════════════════

@Composable
private fun TerminalPane(viewModel: ProjectViewModel) {
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    LaunchedEffect(viewModel.terminalLines.size) {
        if (viewModel.terminalLines.isNotEmpty()) listState.scrollToItem(viewModel.terminalLines.size - 1)
    }
    Column(Modifier.fillMaxSize().background(IdeColors.ConsoleBg)) {
        LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(10.dp)) {
            items(viewModel.terminalLines.toList()) { line ->
                Text(
                    line,
                    color = when {
                        line.startsWith("renpyforge@") -> IdeColors.OkGreen
                        line.startsWith("[") -> IdeColors.TextDim
                        else -> IdeColors.ConsoleFg
                    },
                    fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 17.sp,
                )
            }
        }
        HorizontalDivider(color = IdeColors.PanelBorder)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        ) {
            items(listOf("pwd", "ls", "ls game", "ls game/script", "cat game/script.rpy")) { cmd ->
                AssistChip(
                    onClick = { viewModel.runTerminalCommand(cmd) },
                    label = { Text(cmd, fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                )
            }
        }
        Surface(color = IdeColors.PanelBg) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text("❯", color = IdeColors.OkGreen, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(6.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = viewModel.terminalInput,
                    onValueChange = { viewModel.terminalInput = it },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = IdeColors.TextFg),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(IdeColors.Accent),
                    singleLine = true,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
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
                TextButton(
                    onClick = { viewModel.clearTerminal() },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) { Text("清屏", fontSize = 12.sp) }
            }
        }
    }
}

// ═══════════════════════ 问题页 ═══════════════════════

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

// ═══════════════════════ 资源页 ═══════════════════════

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
                        ImageThumb(f) { clipboard.setText(AnnotatedString(rel)) }
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
                    Icon(Icons.Filled.Terminal, contentDescription = null, tint = IdeColors.Accent, modifier = Modifier.size(14.dp))
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
                Image(bitmap = b.asImageBitmap(), contentDescription = f.name, modifier = Modifier.size(72.dp))
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
