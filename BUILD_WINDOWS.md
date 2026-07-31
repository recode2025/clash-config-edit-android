# 在另一台 Windows 电脑打包 APK

## 准备环境

1. 安装 64 位 OpenJDK 17。
2. 安装 Android Studio，并在 SDK Manager 中安装：
   - Android SDK Platform 35
   - Android SDK Build-Tools
   - Android SDK Platform-Tools
   - Android SDK Command-line Tools
3. 解压本项目 ZIP。路径尽量简短，例如 `D:\ClashConfigEditor`。

## 配置 OpenJDK 17

在 PowerShell 中将路径换成实际的 JDK 17 安装目录：

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

`java -version` 必须显示版本 17。

## 配置 Android SDK

在解压后的项目根目录创建 `local.properties`，内容类似：

```properties
sdk.dir=C\:\\Users\\你的用户名\\AppData\\Local\\Android\\Sdk
```

也可以用 Android Studio 打开项目，它通常会自动创建该文件。

## 生成 APK

在项目根目录打开 PowerShell：

```powershell
.\gradlew.bat clean assembleDebug
```

首次构建需要联网下载 Gradle 插件和依赖。生成的 APK 位于：

```text
app\build\outputs\apk\debug\app-debug.apk
```

如需生成发布包，可运行：

```powershell
.\gradlew.bat assembleRelease
```

发布版本还需要在 Android Studio 中配置自己的签名证书；不要把证书或密码提交、分享进源码 ZIP。

## 常见问题

- `JAVA_HOME is not set`：重新设置 `JAVA_HOME`，并确认它指向 JDK 17 根目录而不是 `bin` 目录。
- `SDK location not found`：检查 `local.properties` 中的 `sdk.dir`。
- 找不到 `platforms;android-35`：在 Android Studio SDK Manager 安装 Android SDK Platform 35。
- 下载依赖失败：检查网络或 Gradle 代理设置，然后重新执行构建命令。
