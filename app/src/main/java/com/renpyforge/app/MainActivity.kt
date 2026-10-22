package com.renpyforge.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.renpyforge.app.build.BuildPipeline
import com.renpyforge.app.ui.IdeScreen
import com.renpyforge.app.ui.IdeTheme
import com.renpyforge.app.ui.ProjectViewModel
import com.renpyforge.app.ui.WelcomeScreen
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: ProjectViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ProjectViewModel(this)

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
    }

    /** 欢迎页 ↔ IDE 的过场动画：打开项目上滑淡入，关闭项目下滑淡出 */
    @Composable
    private fun AppRoot(viewModel: ProjectViewModel) {
        val inIde = viewModel.currentProject != null
        AnimatedContent(
            targetState = inIde,
            transitionSpec = {
                if (targetState) {
                    // 进入 IDE：从底部滑入
                    (slideInUp() + fadeIn(tween(260))) togetherWith
                        (slideOutDown() + fadeOut(tween(200)))
                } else {
                    // 返回欢迎页：从顶部滑回
                    (slideInDown() + fadeIn(tween(260))) togetherWith
                        (slideOutUp() + fadeOut(tween(200)))
                }
            },
            label = "root",
        ) { ide ->
            if (ide) {
                IdeScreen(
                    viewModel = viewModel,
                    onInstall = { file: File -> BuildPipeline.install(this@MainActivity, file) },
                )
            } else {
                WelcomeScreen(
                    viewModel = viewModel,
                    onRequestStorage = { requestStorageAccess() },
                )
            }
        }
    }

    private fun slideInUp() = androidx.compose.animation.slideInVertically(
        animationSpec = tween(280), initialOffsetY = { it / 5 }
    )

    private fun slideOutDown() = androidx.compose.animation.slideOutVertically(
        animationSpec = tween(220), targetOffsetY = { it / 6 }
    )

    private fun slideInDown() = androidx.compose.animation.slideInVertically(
        animationSpec = tween(280), initialOffsetY = { -it / 5 }
    )

    private fun slideOutUp() = androidx.compose.animation.slideOutVertically(
        animationSpec = tween(220), targetOffsetY = { -it / 6 }
    )

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
