package com.renpyforge.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.renpyforge.app.privilege.PrivilegeManager
import com.renpyforge.app.ui.ConsoleScreen
import com.renpyforge.app.ui.IdeColors
import com.renpyforge.app.ui.IdeScreen
import com.renpyforge.app.ui.IdeTheme
import com.renpyforge.app.ui.ProjectViewModel
import com.renpyforge.app.ui.SettingsScreen
import com.renpyforge.app.ui.WelcomeScreen

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: ProjectViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ProjectViewModel(this)
        PrivilegeManager.refresh(this)

        setContent {
            IdeTheme {
                LaunchedEffect(Unit) { viewModel.refresh() }
                AppRoot(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onStorageMaybeChanged()
        PrivilegeManager.refresh(this)
    }

    /**
     * 根导航：MD3 底栏两个入口（项目 / 设置）。
     * 构建台与终端是独立全屏页面，按返回逐步退回。
     */
    @Composable
    private fun AppRoot(viewModel: ProjectViewModel) {
        var section by rememberSaveable { mutableIntStateOf(0) } // 0 项目；1 设置
        var consoleTab by rememberSaveable { mutableStateOf<Int?>(null) } // null 非控制台页
        var lastBackAt by remember { mutableLongStateOf(0L) }
        val context = LocalContext.current

        // 返回键：控制台 → 设置页 → IDE → 首页两次确认退出
        BackHandler {
            when {
                consoleTab != null -> consoleTab = null
                section != 0 -> section = 0
                viewModel.currentProject != null -> viewModel.closeProject()
                else -> {
                    val now = System.currentTimeMillis()
                    if (now - lastBackAt < 2000) {
                        (context as? android.app.Activity)?.finish()
                    } else {
                        lastBackAt = now
                        Toast.makeText(context, "再按一次返回退出", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        Scaffold(
            containerColor = IdeColors.AppBg,
            bottomBar = {
                // 只有首页（项目列表）与设置页显示底栏；IDE 内不显示
                if (consoleTab == null && viewModel.currentProject == null) {
                    NavigationBar(containerColor = IdeColors.PanelBg) {
                        NavigationBarItem(
                            selected = section == 0,
                            onClick = { section = 0 },
                            icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                            label = { Text("项目") },
                        )
                        NavigationBarItem(
                            selected = section == 1,
                            onClick = { section = 1 },
                            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                            label = { Text("设置") },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                AnimatedContent(
                    targetState = consoleTab,
                    transitionSpec = {
                        if (targetState != null) {
                            (slideInHorizontally(tween(260)) { it } + fadeIn(tween(180))) togetherWith
                                fadeOut(tween(150))
                        } else {
                            (slideInHorizontally(tween(260)) { -it / 3 } + fadeIn(tween(180))) togetherWith
                                slideOutHorizontally(tween(200)) { it }
                        }
                    },
                    label = "console",
                ) { tab ->
                    if (tab != null) {
                        ConsoleScreen(
                            viewModel = viewModel,
                            initialTab = tab,
                            onClose = { consoleTab = null },
                        )
                    } else if (viewModel.currentProject != null) {
                        IdeScreen(
                            viewModel = viewModel,
                            onOpenConsole = { consoleTab = it },
                        )
                    } else if (section == 1) {
                        SettingsScreen(
                            viewModel = viewModel,
                            onRequestStorage = { requestStorageAccess() },
                        )
                    } else {
                        WelcomeScreen(
                            viewModel = viewModel,
                            onRequestStorage = { requestStorageAccess() },
                        )
                    }
                }
            }
        }
    }

    private fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            @Suppress("DEPRECATION")
            requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }
    }
}
