plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.renpyforge.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.renpyforge.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 35
        versionName = "1.5.0"
    }

    signingConfigs {
        create("release") {
            // 公开的 CI 签名密钥：保证任何人构建出的 APK 签名一致、可互相覆盖安装。
            // 正式对外发布请替换为你自己的密钥（见 README）。
            storeFile = file("../keystore/release.keystore")
            storePassword = "renpyforge"
            keyAlias = "renpyforge"
            keyPassword = "renpyforge"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    androidResources {
        // 关键：默认忽略规则含 <dir>_*（下划线开头的目录），会把标准库的
        // zipfile/_path 丢掉。改为不含该规则的pattern。
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:!CVS:!thumbs.db:!picasa.ini:!*~"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/versions/**",
                "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA",
            )
        }
        // 必须为 true：工具链可执行文件（aapt2/zipalign 等）以 lib*.so 形式放入 jniLibs，
        // 依赖安装时被解压到 nativeLibraryDir（该目录允许 execve，绕过 W^X 限制）。
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // 端内 APK 签名（纯 Java 库，支持 v1/v2/v3 签名方案）
    implementation("com.android.tools.build:apksig:8.5.2")
    // 自签名证书生成（X509v3CertificateBuilder）+ PKCS12 keystore 写入
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")

    // Shizuku（可选提权：shell 身份执行命令 / 静默安装）
    // 使用本地 jar，避免构建时联网拉取依赖
    implementation(files("libs/shizuku-api.jar", "libs/shizuku-provider.jar"))
}
