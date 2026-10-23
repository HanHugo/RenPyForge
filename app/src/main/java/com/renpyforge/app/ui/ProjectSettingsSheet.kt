@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.renpyforge.app.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/** 包名校验：至少两段，每段字母开头，仅字母数字下划线 */
private val PACKAGE_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

/**
 * 项目设置：应用名 / 包名 / 图标 / 启动图。
 * 改动只影响打包产物，不写入 game/ 目录。
 */
@Composable
fun ProjectSettingsSheet(
    viewModel: ProjectViewModel,
    onDismiss: () -> Unit,
) {
    val config = viewModel.projectConfig ?: return
    var appName by remember(config.appName) { mutableStateOf(config.appName) }
    var packageName by remember(config.packageName) { mutableStateOf(config.packageName) }
    var showError by remember { mutableStateOf(false) }

    val iconPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.importProjectImage("icon", it) } }
    val splashPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.importProjectImage("splash", it) } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = IdeColors.PanelBg,
        contentColor = IdeColors.TextFg,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("项目设置", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "这里的信息用于打包游戏 APK，不会修改 game/ 里的脚本。",
                fontSize = 12.sp, color = IdeColors.TextDim,
            )

            OutlinedTextField(
                value = appName,
                onValueChange = { appName = it },
                label = { Text("应用名称（桌面显示）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = packageName,
                onValueChange = { packageName = it; showError = false },
                label = { Text("包名") },
                singleLine = true,
                isError = showError,
                supportingText = {
                    Text(
                        if (showError) "格式不对：至少两段，如 com.example.mygame"
                        else "唯一标识，形如 com.example.mygame",
                        fontSize = 11.sp,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            ImageField(
                title = "应用图标",
                hint = "正方形效果最佳，构建时自动裁成 512×512",
                file = viewModel.projectImage("icon"),
                wide = false,
                onPick = { iconPicker.launch("image/*") },
                onClear = { viewModel.removeProjectImage("icon") },
            )

            ImageField(
                title = "启动图",
                hint = "游戏启动时全屏显示，建议用游戏主视觉",
                file = viewModel.projectImage("splash"),
                wide = true,
                onPick = { splashPicker.launch("image/*") },
                onClear = { viewModel.removeProjectImage("splash") },
            )

            Button(
                onClick = {
                    if (!PACKAGE_REGEX.matches(packageName.trim())) {
                        showError = true
                    } else {
                        viewModel.saveProjectConfig(packageName, appName)
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(46.dp),
            ) { Text("保存") }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ImageField(
    title: String,
    hint: String,
    file: File?,
    wide: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    var bitmap by remember(file?.absolutePath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(file?.absolutePath) {
        bitmap = if (file != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeFile(file.absolutePath, opts)
                } catch (_: Exception) {
                    null
                }
            }
        } else null
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(if (wide) 96.dp else 64.dp)
                .clip(RoundedCornerShape(if (wide) 10.dp else 32.dp))
                .background(IdeColors.HoverBg)
                .border(1.dp, IdeColors.PanelBorder, RoundedCornerShape(if (wide) 10.dp else 32.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = title,
                    modifier = Modifier.size(if (wide) 96.dp else 64.dp),
                )
            } else {
                Icon(
                    Icons.Filled.AddPhotoAlternate,
                    contentDescription = null,
                    tint = IdeColors.TextDim.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(hint, fontSize = 11.sp, color = IdeColors.TextDim)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) {
                    Text(if (file == null) "选择图片" else "更换", fontSize = 12.sp)
                }
                if (file != null) {
                    IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "移除", tint = IdeColors.FailRed, modifier = Modifier.size(15.dp))
                    }
                    Text(
                        file.name,
                        fontSize = 10.sp,
                        color = IdeColors.TextDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
