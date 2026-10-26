# MonaFlow 莫奈艺术风个人效率中心 (Android 原生工程)

本工程基于 **Kotlin + Jetpack Compose + Material 3 + Room** 构建，完美实现了莫奈色彩体系与您所指定的**高级感悬浮胶囊底栏**。

## 🚀 两种方式获得可直接安装的 APK

### 方式一：电脑 Android Studio 1分钟本地出包（推荐）
1. 解压本压缩包并在电脑上打开 **Android Studio** (推荐 Hedgehog 2023.1.1 或更新版本)。
2. 点击菜单 **File -> Open...** 选择本解压目录，等待 Gradle 自动同步。
3. 点击顶部菜单 **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**。
4. 构建完成后右下角点击 **locate**，即可得到 `app-debug.apk`，直接传输安装至小米手机。

### 方式二：GitHub Actions 零电脑云端自动构建
1. 将本工程上传至您的任意 GitHub 私有或公开仓库。
2. 项目已内置 `.github/workflows/build_apk.yml`。
3. 推送代码后，GitHub 就会自动运行 Android SDK 构建流程。
4. 在仓库页面的 **Actions -> Build Android Debug APK -> Artifacts** 直接下载编译好的 `MonaFlow-Debug-APK.zip`，解压即是安装包！
