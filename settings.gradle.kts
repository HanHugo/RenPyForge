pluginManagement {
    repositories {
        google()
        // 腾讯云 Maven 镜像（central 全量；storage-download.googleapis.com 大文件限速严重）
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
    }
}
rootProject.name = "RenPyForge"
include(":app")
