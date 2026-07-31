# Clash 配置工坊（Android）

一个本地优先的 Mihomo / Clash YAML 配置编辑器。首版包含：

- 通过 Android 系统文件选择器导入、覆盖保存和另存 `.yaml` / `.yml`
- “保存并分享到 Clash”会在写入成功后直接唤起 ClashMi、Clash Meta for Android 或 Clash for Android，无需再次选择文件
- 接收 ClashMi、Clash for Android 等应用分享出来的配置文件
- 配置概览、完整 YAML 编辑与保存前校验
- 未识别的新配置键原样保留，不用固定数据类限制 Mihomo 字段
- Android 包名路由向导：创建 `url-test` 最低延迟组或 `fallback` 稳定优先组，并把规则安全地插到 `MATCH` 前
- 支持内联节点，以及 `proxy-providers` 的 `use` + `filter` 节点筛选
- 无联网、无存储权限、无统计 SDK

> Android 沙盒禁止普通应用直接读取其他 App 的私有数据目录。请在 ClashMi/CFA 中“导出/分享配置”，或在系统文件选择器中授权可见的配置文件。对私有目录的无交互读取只能依赖 root/Shizuku 等高权限方案，本项目默认不申请。

分享时应用只会在自己的缓存目录生成一份副本，并通过 Android `content://` 授权交给目标客户端，不会上传配置。如果同时安装了多个兼容客户端，系统会让你选择一次目标应用；以后保存并分享时不再出现文件选择器。

## 构建

用 Android Studio 打开仓库，选择 JDK 17，等待 Gradle 同步后运行 `app`。命令行构建：

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

要求 Android SDK 35；最低支持 Android 8.0（API 26）。

## 包名 + 自动优选线路的等价 YAML

向导生成的核心配置类似：

```yaml
find-process-mode: always

proxy-groups:
  - name: Telegram 自动
    type: url-test
    proxies:
      - 香港 01
      - 香港 02
      - 日本 01
    url: https://www.gstatic.com/generate_204
    interval: 300
    tolerance: 80
    lazy: false
    timeout: 5000
    expected-status: 204

rules:
  - PROCESS-NAME,org.telegram.messenger,Telegram 自动
  - DOMAIN-SUFFIX,telegram.org,Telegram 自动 # 仅作补充，会影响所有 App
  - MATCH,节点选择
```

Android 上 `PROCESS-NAME` 可以匹配包名。只要客户端把进程信息交给 Mihomo，这一条就覆盖该 App 访问的所有域名；不需要也不可能静态穷举“某个软件未来可能访问的全部域名”。如果包名匹配在特定客户端无效，请确认它使用 Mihomo/Clash.Meta 内核并启用了进程识别；CFA 的旧 Clash Premium 内核可能不支持所有新字段。

`url-test` 以健康检查延迟为依据选最低延迟线路，`tolerance` 用来避免因很小的延迟差频繁切换；如果更看重连接连续性，用 `fallback`，它会按线路顺序选择第一个可用节点，当前节点超时才切换。

如果线路来自 `proxy-providers`，请同时在对应 provider 中启用 `health-check.enable: true`；规则组的 `url` 健康检查只直接检查其 `proxies` 条目。

官方参考：[路由规则](https://wiki.metacubex.one/config/rules/)、[代理组](https://wiki.metacubex.one/config/proxy-groups/)、[url-test](https://wiki.metacubex.one/config/proxy-groups/url-test/)。

## 数据与兼容策略

SnakeYAML 以有序 Map/List 解析配置，所以未知键和值会保留；结构化修改后 YAML 的缩进和引号风格可能统一，原注释目前不会保留。若注释对你很重要，请只使用原始编辑器，或先保留一份备份。
