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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renpyforge.app.BuildConfig
import com.renpyforge.app.privilege.PrivilegeManager
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

        // —— 高级权限 ——
        item {
            SettingsCard("高级权限（可选）") {
                Text(
                    "用于终端提权与构建产物静默安装；不授权也不影响 IDE 与构建。",
                    fontSize = 11.sp, color = IdeColors.TextDim,
                )
                Spacer(Modifier.height(8.dp))
                PrivilegeRow(
                    name = "Root",
                    available = PrivilegeManager.rootAvailable,
                    detail = if (PrivilegeManager.rootAvailable) {
                        "已检测到 su，可静默安装、以 root 执行终端命令"
                    } else {
                        "未检测到 su（设备未 Root 或未授予 root 权限）"
                    },
                )
                Spacer(Modifier.height(6.dp))
                PrivilegeRow(
                    name = "Shizuku",
                    available = PrivilegeManager.shizukuAuthorized,
                    detail = when {
                        !PrivilegeManager.shizukuInstalled -> "未安装 Shizuku（moe.shizuku.privileged.api）"
                        !PrivilegeManager.shizukuRunning -> "Shizuku 已安装但服务未运行，请先打开 Shizuku 启动服务"
                        PrivilegeManager.shizukuAuthorized -> "已授权，可静默安装、以 shell 身份执行命令"
                        else -> "Shizuku 运行中，尚未授权"
                    },
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (PrivilegeManager.shizukuInstalled && !PrivilegeManager.shizukuAuthorized) {
                        Button(onClick = {
                            val activity = context as? android.app.Activity
                            if (activity == null) {
                                viewModel.toast("无法申请权限")
                            } else {
                                val err = PrivilegeManager.requestShizuku(activity)
                                viewModel.toast(err ?: "已弹出 Shizuku 授权请求")
                                PrivilegeManager.refresh(context)
                            }
                        }) { Text("申请 Shizuku 授权") }
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = {
                        PrivilegeManager.refresh(context)
                        viewModel.toast("权限状态已刷新")
                    }) { Text("刷新状态", fontSize = 12.sp) }
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
private fun PrivilegeRow(name: String, available: Boolean, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (available) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (available) IdeColors.OkGreen else IdeColors.WarnOrange,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                "$name · " + if (available) "可用" else "不可用",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(detail, fontSize = 11.sp, color = IdeColors.TextDim)
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
