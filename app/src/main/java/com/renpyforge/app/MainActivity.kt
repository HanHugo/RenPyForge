package com.renpyforge.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
                if (viewModel.currentProject == null) {
                    WelcomeScreen(
                        viewModel = viewModel,
                        onRequestStorage = { requestStorageAccess() },
                    )
                } else {
                    IdeScreen(
                        viewModel = viewModel,
                        onInstall = { file: File -> BuildPipeline.install(this, file) },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onStorageMaybeChanged()
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
