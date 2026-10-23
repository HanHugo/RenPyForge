@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.renpyforge.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renpyforge.app.BuildConfig

/**
 * 欢迎页（MD3）：LargeTopAppBar + 项目卡片列表 + ExtendedFAB 新建。
 * 权限横幅、列表项均带进出场动效。
 */
@Composable
fun WelcomeScreen(
    viewModel: ProjectViewModel,
    onRequestStorage: () -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = IdeColors.AppBg,
        topBar = {
            LargeTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Gamepad,
                            contentDescription = null,
                            tint = IdeColors.Accent,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("RenPyForge", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "手机端 Ren'Py IDE",
                                fontSize = 12.sp,
                                color = IdeColors.TextDim,
                            )
                        }
                    }
                },
                actions = {
                    Spacer(Modifier.width(4.dp))
                },
                colors = androidx.compose.material3.TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = IdeColors.AppBg,
                    scrolledContainerColor = IdeColors.PanelBg,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    viewModel.newProjectName = ""
                    viewModel.statusMessage = null
                    showCreate = true
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("新建项目") },
                containerColor = IdeColors.Accent,
                contentColor = Color.White,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 权限横幅（展开/收起动效）
            AnimatedVisibility(
                visible = !viewModel.storageGranted,
                enter = expandVertically(spring()) + fadeIn(),
                exit = shrinkVertically(spring()) + fadeOut(),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("需要「所有文件访问」权限", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "授权后项目保存在存储根目录 RenPyForge/ 下，文件管理器可直接访问。",
                            fontSize = 12.sp,
                            color = IdeColors.TextDim,
                        )
                        Button(onClick = onRequestStorage) { Text("去授权") }
                    }
                }
            }

            if (viewModel.storageGranted) {
                Text(
                    "工作目录 ${viewModel.storagePath}/RenPyForge",
                    fontSize = 11.sp,
                    color = IdeColors.TextDim,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }

            Text(
                "我的项目（${viewModel.projectList.size}）",
                fontSize = 12.sp,
                color = IdeColors.TextDim,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(viewModel.projectList, key = { _, n -> n }) { _, name ->
                    val active = name == viewModel.currentProject
                    // 按压缩放反馈
                    var pressed by remember { mutableStateOf(false) }
                    val scale by animateFloatAsState(
                        targetValue = if (pressed) 0.97f else 1f,
                        animationSpec = spring(dampingRatio = 0.6f),
                        label = "cardScale",
                    )
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (active) 4.dp else 1.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (active) IdeColors.SelectedBg else IdeColors.PanelBg,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .scale(scale),
                        onClick = { pressed = true; viewModel.openProject(name) },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .background(
                                        if (active) IdeColors.Accent.copy(alpha = 0.25f) else IdeColors.HoverBg,
                                        RoundedCornerShape(10.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = if (active) IdeColors.Accent else IdeColors.WarnOrange,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text("projects/$name", fontSize = 11.sp, color = IdeColors.TextDim)
                            }
                            IconButton(onClick = { pendingDelete = name }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除",
                                    tint = IdeColors.FailRed.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 新建项目对话框
    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            containerColor = IdeColors.PanelBg,
            title = { Text("新建项目") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = viewModel.newProjectName,
                        onValueChange = { viewModel.newProjectName = it },
                        label = { Text("项目名称") },
                        singleLine = true,
                    )
                    Text(
                        "模板包含完整的 gui / screens / script，创建后直接改编即可。",
                        fontSize = 12.sp, color = IdeColors.TextDim,
                    )
                    viewModel.statusMessage?.let {
                        Text(it, fontSize = 12.sp, color = IdeColors.Accent)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (viewModel.createProject()) showCreate = false
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("取消") } },
        )
    }

    // 删除确认对话框
    pendingDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = IdeColors.PanelBg,
            title = { Text("删除项目") },
            text = { Text("确定要删除「$name」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProject(name)
                    pendingDelete = null
                }) { Text("删除", color = IdeColors.FailRed) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}
