@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.renpyforge.app.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renpyforge.app.BuildConfig
import com.renpyforge.app.project.AppStorage

/**
 * 设置页（融合原「关于」页）：
 * 存储权限 / 构建输出 / 关于与链接。
 */
@Composable
fun SettingsScreen(
    viewModel: ProjectViewModel,
    onRequestStorage: () -> Unit,
) {
    val context = LocalContext.current

    fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) { /* 无浏览器时静默 */ }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(IdeColors.AppBg),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("设置", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text("存储、构建输出与应用信息", fontSize = 12.sp, color = IdeColors.TextDim)
        }

        // —— 存储 ——
        item {
            SettingsCard("存储") {
                Text(
                    viewModel.storagePath,
                    fontSize = 12.sp, color = IdeColors.TextDim, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (viewModel.storageGranted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (viewModel.storageGranted) IdeColors.OkGreen else IdeColors.WarnOrange,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (viewModel.storageGranted) "已获得「所有文件访问」权限" else "未授权，项目暂存于应用私有目录",
                        fontSize = 12.sp,
                    )
                }
                if (!viewModel.storageGranted) {
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onRequestStorage) { Text("去授权") }
                }
            }
        }

        // —— 构建输出 ——
        item {
            SettingsCard("构建输出") {
                Text(
                    "产物目录：${AppStorage.outputRoot(context).absolutePath}",
                    fontSize = 12.sp, color = IdeColors.TextDim, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "历史产物 ${viewModel.buildHistory.size} 个（在构建页可安装或删除）",
                    fontSize = 12.sp, color = IdeColors.TextDim,
                )
            }
        }

        // —— 关于 ——
        item {
            SettingsCard("关于") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Gamepad, contentDescription = null,
                        tint = IdeColors.Accent, modifier = Modifier.size(38.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("RenPyForge", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "v${BuildConfig.VERSION_NAME} · 手机端 Ren'Py IDE",
                            fontSize = 11.sp, color = IdeColors.TextDim,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "在手机上创建 Ren'Py 项目、编写脚本，并全链路完成 APK 构建与签名——无需电脑。",
                    fontSize = 12.sp, color = IdeColors.TextDim,
                )
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = IdeColors.PanelBorder)

                LinkRow(
                    icon = { Icon(Icons.Filled.Code, null, tint = IdeColors.Accent, modifier = Modifier.size(20.dp)) },
                    title = "GitHub 项目主页",
                    subtitle = "github.com/HanHugo/RenPyForge",
                ) { open("https://github.com/HanHugo/RenPyForge") }

                LinkRow(
                    icon = { Icon(Icons.Filled.Groups, null, tint = IdeColors.OkGreen, modifier = Modifier.size(20.dp)) },
                    title = "QQ 交流群",
                    subtitle = "群号 860798476 · 点击加入",
                ) {
                    open(
                        "https://qun.qq.com/universal-share/share?ac=1&authKey=O7nI2jJY5%2FZG8ixRzNlF0mPn7i%2FzuzZmPlR1G2tzmgoRmh2lWxbtf8caoQr9XMWh&busi_data=eyJncm91cENvZGUiOiI4NjA3OTg0NzYiLCJ0b2tlbiI6Ii9GWkJOcEw4Q1JnckJPVVhRcjhTS004YVpnNlZxWUw5R2s3UVF1ZkpJaGUyMXBzUDRXSEtvNTAwMEgzL3pDZDQiLCJ1aW4iOiIyMDIxODYyODgzIn0%3D&data=5tKuIrbXEuPo9E1xw9pXQ-tLsE96_6A4UkVlUaP3zebovAlwiRgZtBA6YPT4lA_Jzsq6PIhderE-i7T6fvupdQ&svctype=4&tempid=h5_group_info"
                    )
                }

                LinkRow(
                    icon = { Icon(Icons.Filled.Public, null, tint = IdeColors.WarnOrange, modifier = Modifier.size(20.dp)) },
                    title = "Ren'Py 官网与文档",
                    subtitle = "renpy.org",
                ) { open("https://www.renpy.org") }

                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = IdeColors.PanelBorder)
                Spacer(Modifier.height(8.dp))
                Text(
                    "RenPyForge 以 MIT 许可开源。内置 Ren'Py 引擎版权归 Ren'Py 项目所有，" +
                        "构建工具来自 AOSP/RAPT，均遵循其原始许可协议。",
                    fontSize = 11.sp, color = IdeColors.TextDim.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Surface(
        color = IdeColors.PanelBg,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(IdeColors.PanelBg, RoundedCornerShape(16.dp)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = IdeColors.TextDim)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun LinkRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(IdeColors.HoverBg, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 11.sp, color = IdeColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
