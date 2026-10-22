package com.renpyforge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 欢迎页 / 项目管理（无项目打开时的主界面） */
@Composable
fun WelcomeScreen(
    viewModel: ProjectViewModel,
    onRequestStorage: () -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(IdeColors.AppBg)
    ) {
        // 顶部品牌区
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(IdeColors.AccentDim.copy(alpha = 0.35f), IdeColors.AppBg)))
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Gamepad,
                    contentDescription = null,
                    tint = IdeColors.Accent,
                    modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("RenPyForge", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "手机端 Ren'Py IDE",
                        fontSize = 13.sp,
                        color = IdeColors.TextDim,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { showCreate = true },
                colors = ButtonDefaults.buttonColors(containerColor = IdeColors.Accent),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("新建项目（官方示例模板）")
            }
        }

        // 存储权限提示
        if (!viewModel.storageGranted) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF5C2A30)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("需要「所有文件访问」权限", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "授权后项目保存在存储根目录 RenPyForge/ 下，文件管理器可直接访问。",
                        fontSize = 12.sp, color = IdeColors.TextDim,
                    )
                    Button(onClick = onRequestStorage) { Text("去授权") }
                }
            }
        } else {
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

        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
            items(viewModel.projectList) { name ->
                val active = name == viewModel.currentProject
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (active) IdeColors.SelectedBg else IdeColors.PanelBg,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .border(
                            1.dp,
                            if (active) IdeColors.Accent else IdeColors.PanelBorder,
                            RoundedCornerShape(10.dp),
                        ),
                    onClick = { viewModel.openProject(name) },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            Icons.Filled.Folder,
                            contentDescription = null,
                            tint = IdeColors.WarnOrange,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(
                                "projects/$name", fontSize = 11.sp, color = IdeColors.TextDim,
                            )
                        }
                        IconButton(onClick = { viewModel.deleteProject(name) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "删除",
                                tint = IdeColors.FailRed.copy(alpha = 0.75f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            containerColor = IdeColors.PanelBg,
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
                    viewModel.createProject()
                    if (viewModel.currentProject != null) showCreate = false
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("取消") } },
        )
    }
}
