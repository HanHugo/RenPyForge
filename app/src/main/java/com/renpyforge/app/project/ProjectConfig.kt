package com.renpyforge.app.project

import org.json.JSONObject

/** 项目级构建配置：包名、应用名、自定义图标与启动图（相对项目目录的路径） */
data class ProjectConfig(
    val packageName: String,
    val appName: String,
    val iconFile: String? = null,
    val splashFile: String? = null,
) {
    fun toJson(): String = JSONObject().apply {
        put("packageName", packageName)
        put("appName", appName)
        if (iconFile != null) put("iconFile", iconFile) else remove("iconFile")
        if (splashFile != null) put("splashFile", splashFile) else remove("splashFile")
    }.toString(2)

    companion object {
        fun defaults(projectName: String): ProjectConfig = ProjectConfig(
            packageName = "org.renpy.forge." +
                projectName.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "game" },
            appName = projectName,
        )

        fun fromJson(text: String, projectName: String): ProjectConfig {
            val def = defaults(projectName)
            return try {
                val o = JSONObject(text)
                ProjectConfig(
                    packageName = o.optString("packageName").ifBlank { def.packageName },
                    appName = o.optString("appName").ifBlank { def.appName },
                    iconFile = o.optString("iconFile").ifBlank { null },
                    splashFile = o.optString("splashFile").ifBlank { null },
                )
            } catch (_: Exception) {
                def
            }
        }
    }
}
