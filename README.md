<div align="center">

# RenPyForge

**手机端 Ren'Py 游戏制作与构建工具 / On-device Ren'Py game maker & APK builder for Android**

一个 Android 应用：在手机上创建 Ren'Py 项目、编写脚本，并**全链路在本地**完成 APK 的编译、对齐与签名——不需要电脑，不需要云构建。

An Android app that creates Ren'Py projects, edits scripts, and builds installable game APKs **entirely on your phone** — no PC, no cloud builds.

</div>

---

## 中文说明

### 这是什么

RenPyForge 把 Ren'Py 官方安卓打包链（RAPT）的全部工具链内置到了一个 App 里：

- **内置官方引擎**：Ren'Py 8.6.0 预编译 ARM64 引擎库（`librenpython.so`，来自 renpy.org 官方 RAPT 发行包）
- **内置构建工具**：静态链接的 ARM64 `aapt2` / `zipalign`、RAPT Java 模板的预编译 `classes.dex`、Android SDK 的 `android.jar`
- **内置 Python 运行时**：Ren'Py 的 Python 运行时与 `renpy/common` 预编译脚本，构建时以 RAPT 兼容的 `private.mp3`（gzip tar）形式打入游戏 APK
- **端内签名**：apksig（v1/v2/v3），密钥自动生成并保存在应用私有目录

因此装上 App 即可使用，无需 Termux、无需 NDK、无需电脑。

### 功能

| 模块 | 说明 |
|---|---|
| 项目管理 | 一键基于官方示例游戏模板新建项目，项目存放在存储根目录 `RenPyForge/projects/` |
| 脚本编辑 | `.rpy` 文件树 + 等宽编辑器，新建/编辑/删除/保存 |
| 终端 | 非交互 shell，内置工具已加入 PATH，可直接检查工程与调试 |
| 构建 | 环境自检 → aapt2 编译链接 → APK 组装 → zipalign → apksig 签名，全程可视化 |
| 安装 | 构建产物保存在 `RenPyForge/output/`，可直接调起系统安装器 |

### 系统要求

- Android 8.0（API 26）及以上的 **arm64-v8a** 设备
- 「所有文件访问」权限（用于在存储根目录保存项目）
- 「安装未知应用」权限（用于安装构建出的 APK）

### 使用流程

1. 项目 → 新建项目（模板为官方示例游戏 the_question，含完整 gui/screens）
2. 编辑 → 打开 `game/script.rpy` 改写你的剧情
3. 构建 → 填写包名/游戏名 → 开始构建
4. 构建完成后直接安装 APK

### 构建 RenPyForge 本身

```bash
git clone https://github.com/HanHugo/RenPyForge.git
cd RenPyForge
# 用 Android Studio 打开，或：
./gradlew assembleDebug
```

- compileSdk 34 / targetSdk 34 / minSdk 26 / Kotlin 2.x / Jetpack Compose (Material 3)
- 依赖：`com.android.tools.build:apksig`、BouncyCastle、AndroidX

### 目录结构

```
app/src/main/assets/
├── tools/                  # android.jar、classes.dex（RAPT Java 模板预编译）
├── templates/              # 游戏 APK 的 manifest / res 模板
├── renpy-runtime/          # main.py + renpy/**（Python 运行时）
├── renpy-common/           # renpy/common/**（预编译 .rpyc）
├── renpy-stdlib/           # Python 3.12 distlib（*.pyc）
└── newproject/             # 新建项目模板
app/src/main/jniLibs/arm64-v8a/
├── libaapt2.so             # 静态链接 aapt2（ARM64）
└── libzipalign.so          # 静态链接 zipalign（ARM64）
```

### Roadmap

- [x] 全内置工具链的端内构建流水线
- [x] 项目管理 / 脚本编辑 / 内置终端
- [ ] 语法高亮编辑器
- [ ] 图标 / 包名自定义增强
- [ ] 多架构（armeabi-v7a）支持
- [ ] 增量构建与构建缓存优化

---

## English

### What is this

RenPyForge bundles the complete Ren'Py official Android packaging toolchain (RAPT) into a single app:

- **Bundled official engine**: Ren'Py 8.6.0 prebuilt ARM64 engine (`librenpython.so`, from the official RAPT distribution on renpy.org)
- **Bundled build tools**: statically-linked ARM64 `aapt2` / `zipalign`, prebuilt `classes.dex` of the RAPT Java template, and `android.jar` from the Android SDK
- **Bundled Python runtime**: Ren'Py's Python runtime and precompiled `renpy/common`, packed into the game APK as a RAPT-compatible `private.mp3` (gzipped tar)
- **On-device signing**: apksig (v1/v2/v3); the signing key is generated automatically and kept in the app's private storage

Install the app and start building — no Termux, no NDK, no PC required.

### Features

| Module | Description |
|---|---|
| Projects | One-tap project creation from the official sample game template, stored under `RenPyForge/projects/` |
| Editor | `.rpy` file tree + monospace editor; create/edit/delete/save |
| Terminal | Non-interactive shell with bundled tools on PATH |
| Build | Env check → aapt2 compile/link → APK assembly → zipalign → apksig signing, all visualized |
| Install | Artifacts saved to `RenPyForge/output/`; install via the system installer |

### Requirements

- Android 8.0 (API 26)+, **arm64-v8a** device
- "All files access" permission (project storage)
- "Install unknown apps" permission

### Building RenPyForge

```bash
git clone https://github.com/HanHugo/RenPyForge.git
cd RenPyForge
# Open in Android Studio, or:
./gradlew assembleDebug
```

### License & Third-party Components

The RenPyForge source code is released under the [MIT License](LICENSE).

The app bundles binaries and data from third-party projects, which remain under their original licenses:

| Component | Source | License |
|---|---|---|
| Ren'Py engine (`librenpython.so`, Python runtime, common scripts, project template) | [renpy.org](https://renpy.org) 8.6.0 SDK & official RAPT distribution | MIT (Ren'Py) with bundled third-party components |
| RAPT Java template (`classes.dex` sources) | [github.com/renpy/rapt](https://github.com/renpy/rapt) | MIT (Ren'Py) |
| `aapt2` / `zipalign` (ARM64 static builds) | AOSP build-tools, static ARM64 builds by [lzhiyong/android-sdk-tools](https://github.com/lzhiyong/android-sdk-tools) | Apache-2.0 |
| `android.jar` | Android SDK Platform 36 | Android SDK License |
| apksig | [AOSP apksig](https://android.googlesource.com/platform/tools/apksig) | Apache-2.0 |
| BouncyCastle | [bouncycastle.org](https://www.bouncycastle.org) | MIT-like (BC license) |

Games built with RenPyForge are plain Ren'Py games and are subject to the Ren'Py engine license terms.

---

<div align="center">

用 Ren'Py 讲你的故事 —— 就在手机上。

Tell your story with Ren'Py — right from your phone.

</div>
