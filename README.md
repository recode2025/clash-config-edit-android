# Clash 配置工坊（Android）

> 本地优先、**零联网、零权限**的 Mihomo / Clash YAML 配置编辑器。

在手机上对 ClashMi、Clash Meta for Android、Clash for Android 等客户端的配置做结构化编辑与校验，并为单个 App 一键生成「包名路由 + 自动优选线路」规则组。

## ✨ 功能

- **导入 / 保存**：通过系统文件选择器导入、覆盖保存、另存 `.yaml` / `.yml`
- **保存并分享到 Clash**：写入成功后直接唤起 ClashMi / CFA / CMFA，无需再次选文件
- **接收分享**：接收 ClashMi、CFA 等应用分享出来的配置
- **概览 + 完整编辑**：配置概览、完整 YAML 编辑、保存前自动校验
- **保留未知字段**：未识别的新配置键原样保留，不因固定数据类而丢失
- **规则向导**：为指定包名创建 `url-test`（最低延迟）或 `fallback`（稳定优先）组，规则安全插到 `MATCH` 前
- **节点筛选**：支持内联节点，以及 `proxy-providers` 的 `use` + `filter`
- **零联网 · 零存储权限 · 无统计 SDK**

## 🔒 隐私

分享时应用只会在**自己的缓存目录**生成一份副本，再通过 Android `content://` 授权交给目标客户端，**不会上传配置**。

> Android 沙盒禁止普通应用直接读取其他 App 的私有数据目录。请在 ClashMi / CFA 中「导出 / 分享配置」，或在系统文件选择器中授权可见的配置文件。无交互读取私有目录只能依赖 root / Shizuku，本项目默认不申请。

## 🛠️ 构建环境

| 依赖 | 版本 |
| --- | --- |
| OpenJDK | **17** |
| Android SDK Platform | **35** |
| Gradle | 8.9（仓库已含 wrapper） |
| AGP / Kotlin | 8.7.3 / 2.0.21 |

最低运行：Android 8.0（API 26）。首次构建需联网下载 Gradle 插件与依赖。

通过 Android Studio SDK Manager 安装：Android SDK Platform 35、Build-Tools、Platform-Tools、Command-line Tools。

## 📦 构建步骤

1. **配置 JDK 17**（PowerShell，替换为你的实际路径）
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17"
   $env:Path = "$env:JAVA_HOME\bin;$env:Path"
   java -version   # 必须显示版本 17
   ```

2. **指定 Android SDK**：在项目根目录创建 `local.properties`
   ```properties
   sdk.dir=C\:\\Users\\你的用户名\\AppData\\Local\\Android\\Sdk
   ```
   也可设置 `ANDROID_HOME` 环境变量，或直接用 Android Studio 打开（会自动生成）。

3. **生成 APK**
   ```powershell
   .\gradlew.bat assembleDebug      # 调试包
   .\gradlew.bat assembleRelease    # 发布包（需另行配置签名）
   ```
   产物路径：`app\build\outputs\apk\debug\app-debug.apk`

> 建议路径尽量简短（如 `D:\ClashConfigEditor`）。发布包需在 Android Studio 中配置自己的签名证书；**不要**把证书或密码提交进源码。

## 🧰 常见构建问题

| 现象 | 解决 |
| --- | --- |
| `JAVA_HOME is not set` | 重新设置 `JAVA_HOME`，指向 JDK 17 **根目录**（不是 `bin`） |
| `SDK location not found` | 检查 `local.properties` 的 `sdk.dir` 或 `ANDROID_HOME` |
| 找不到 `platforms;android-35` | 在 SDK Manager 安装 Android SDK Platform 35 |
| 插件 / 依赖下载失败 | 检查网络或 Gradle 代理后重试；首次启动需联网拉取 AGP 插件 |

## ⚙️ 路由原理：包名 + 自动优选

规则向导生成的核心配置等价于：

```yaml
find-process-mode: always

proxy-groups:
  - name: Telegram 自动
    type: url-test
    proxies: [香港 01, 香港 02, 日本 01]
    url: https://www.gstatic.com/generate_204
    interval: 300
    tolerance: 80
    lazy: false
    timeout: 5000
    expected-status: 204

rules:
  - PROCESS-NAME,org.telegram.messenger,Telegram 自动
  - DOMAIN-SUFFIX,telegram.org,Telegram 自动   # 补充规则，全局生效
  - MATCH,节点选择
```

- Android 上 `PROCESS-NAME` 可匹配**包名**，覆盖该 App 访问的全部域名，无需静态穷举其未来可能访问的域名。若包名匹配无效，请确认客户端使用 Mihomo / Clash.Meta 内核并启用了进程识别（CFA 旧 Clash Premium 内核可能不支持部分新字段）。
- **`url-test`**：以健康检查延迟选最低线路，`tolerance` 避免因微小延迟差频繁切换。
- **`fallback`**：按顺序使用第一个可用节点，当前节点超时才切换，更看重连接连续性。
- 若线路来自 `proxy-providers`，请在对应 provider 启用 `health-check.enable: true`；规则组的 `url` 只直接检查其 `proxies` 条目。

官方参考：[路由规则](https://wiki.metacubex.one/config/rules/) · [代理组](https://wiki.metacubex.one/config/proxy-groups/) · [url-test](https://wiki.metacubex.one/config/proxy-groups/url-test/)

## 📐 数据与兼容

SnakeYAML 以有序 Map / List 解析配置，未知键和值会保留；结构化修改后 YAML 的缩进 / 引号风格可能被统一，**原注释不会保留**。若注释对你很重要，请只使用原始编辑器，或先保留一份备份。
