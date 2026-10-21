package com.renpyforge.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import com.renpyforge.app.ui.BuildScreen
import com.renpyforge.app.ui.ProjectViewModel

private val ForgeDark = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF82B1FF),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF00296B),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF2B4C8C),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFDCE6FF),
    secondary = androidx.compose.ui.graphics.Color(0xFFFFB74D),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF3E2723),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF5D4037),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFFFE0B2),
    background = androidx.compose.ui.graphics.Color(0xFF121417),
    onBackground = androidx.compose.ui.graphics.Color(0xFFE4E2E6),
    surface = androidx.compose.ui.graphics.Color(0xFF1A1D21),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE4E2E6),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF23272E),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFB9C0CB),
    error = androidx.compose.ui.graphics.Color(0xFFFF5370),
)

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: ProjectViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ProjectViewModel(this)

        setContent {
            MaterialTheme(colorScheme = ForgeDark) {
                LaunchedEffect(Unit) { viewModel.refresh() }
                BuildScreen(
                    viewModel = viewModel,
                    onInstall = { file -> com.renpyforge.app.build.BuildPipeline.install(this, file) },
                    onRequestStorage = { requestStorageAccess() },
                )
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
