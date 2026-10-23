package com.renpyforge.app.build

/**
 * 构建流水线步骤（全内置版）：
 * 1 环境自检 → 2 资源编译链接 → 3 APK 组装 → 4 zipalign → 5 签名 → 6 安装
 * （Java 模板使用内置 classes.dex，引擎使用内置 librenpython.so，无需 Cython/NDK）
 */
enum class StepId(val label: String) {
    ENV_CHECK("环境自检"),
    AAPT2("aapt2 资源编译与链接"),
    ASSEMBLE("APK 组装"),
    ZIPALIGN("zipalign 对齐"),
    SIGN("APK 签名"),
    INSTALL("输出与安装"),
}

enum class StepState { PENDING, RUNNING, OK, FAILED, SKIPPED }

/** 单个步骤的运行时状态与日志 */
data class StepStatus(
    val id: StepId,
    var state: StepState = StepState.PENDING,
    val log: StringBuilder = StringBuilder(),
)

/** 构建配置 */
data class BuildConfig(
    val packageName: String = "org.renpy.forge.game",
    val versionCode: Int = 1,
    val versionName: String = "1.0",
    val appName: String = "RenPy Game",
    val orientation: String = "landscape", // landscape / portrait / etc.
    /** 自定义应用图标（任意图片，构建时裁成方形） */
    val iconFile: java.io.File? = null,
    /** 自定义启动图（替换 renpy_presplash） */
    val splashFile: java.io.File? = null,
)

/** 步骤执行失败时抛出，携带可展示的错误信息与修复指引 */
class StepException(step: StepId, message: String) : Exception("[${step.label}] $message")
