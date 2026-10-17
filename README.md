# DSH-Phone for Android

DeepSeek Harness（`dsh`）的 Android 客户端。Compose 原生界面 + 本地 dsh 引擎，
通过 **ACP（Agent Client Protocol）** 直连 stdio，不走端口、不走 token。

包名 `com.dsh.harness` · versionCode `1004` · versionName `1.0.0`

---

## 这是什么

| 组成 | 说明 |
|---|---|
| `app/` | Android 应用。`com/dsh/harness/mobile/` 是 Compose 界面与数据层 |
| `app/src/main/java/com/dsh/harness/` | Harness 运行时（Java）：WebView 壳、bootstrap、SAF 桥、NAS 同步 |
| `termux-shared/` `terminal-emulator/` `terminal-view/` | 上游 termux 的终端组件（未改动，构建依赖它们） |
| `harness-src/` | **真相源**：Harness Java 源码 + 脚本 + 文档。改动先落这里，再同步到 `app/` |

代码基于 [termux-app](https://github.com/termux/termux-app) 改造，包名与入口 Activity 换成 DSH。

---

## 关键设计

### 数据层走 ACP，不走 HTTP
dsh 只暴露两条通路：`web`（端口 + token + WebView）与 `@deepseek-ai/dsh-acp`（JSON-RPC over stdio）。
本项目选 ACP —— 无端口、无 token、进程级可控，协议面正好覆盖界面需要的数据：

```
session/new                → 首页选工作区
session/prompt             → 发送消息
session/update             → 消息流 / thought / 工具生命周期 / 上下文用量
session/request_permission → 审批弹层
session/list               → 侧栏会话分组
session/cancel             → 停止按钮
```

启动命令：`node --expose-internals $HOME/node_modules/@deepseek-ai/dsh/lib/bin.js --profile acp`

### 内置插件目录是**真读**出来的
界面上的插件列表不是写死的，而是跑一次
`dsh --profile acp --dump-config` 解析真实插件树（当前 86 个条目，含启停状态与 config 键名）。

### API 密钥写入 dsh 自己的凭据存储
存在 `$DSH_HOME/.credentials.yaml` 的 `refs:` 分节、按环境变量名索引，
`deepseek-official` 这条 route 读 `DEEPSEEK_API_KEY`。
凭据文件有 watcher 热重载，**保存即生效，无需重启引擎**。

三条约束（来自 dsh 官方契约，界面必须遵守）：
1. **只写不回读** —— 保存后只显示掩码，重新编辑要重新输入
2. **空值等于没有** —— 移除密钥 = 删掉那一行，不是置空
3. 文件格式非法会在启动时失败 —— 校验必须在 App 侧做

### 运行时路径约定
```
$PREFIX = <filesDir>/usr        node 在 $PREFIX/bin/node
$HOME   = <filesDir>/home       dsh 入口 $HOME/node_modules/@deepseek-ai/dsh/lib/bin.js
```
`$PREFIX` 与 `$HOME` 是**兄弟目录**，不是父子。

---

## 构建

### 前置
- JDK 17
- Android SDK（`build-tools` / platform-tools）
- `local.properties` 里写 `sdk.dir=/你的/AndroidSDK`

### 依赖 dsh bundle（必需，不在仓库里）
`app/src/main/assets/dsh-bundle.tar.gz` 是 195MB 的二进制（node 运行时 + 200+ 个 `@deepseek-ai` 包），
**不纳入版本管理**。构建前放到该路径：

```bash
cp /path/to/dsh-bundle.tar.gz app/src/main/assets/
```

### 编译
```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/AndroidSDK
./gradlew :app:assembleDebug --offline
```

`--offline` 能成是因为依赖已缓存在 `~/.gradle/caches`；联网构建时若 `maven.google.com` 不通，
根 `build.gradle` 里已配置 `dl.google.com` 镜像。

产物：`app/build/outputs/apk/debug/termux-app_apt-android-7-debug_<abi>.apk`

> ⚠️ **不要跑 `gradlew clean`** —— 会触发 bootstrap 重新下载，可能卡住很久。

---

## 目录导航

```
app/src/main/java/com/dsh/harness/
├── mobile/
│   ├── data/          ACP 客户端、凭据、插件目录、MarketModel
│   └── ui/
│       ├── screens/   各屏实现（S1~S23）
│       ├── components/ 通用组件（严格对齐设计稿尺寸）
│       └── theme/     设计令牌（颜色 / 间距 / 动效）
├── DshBootstrap.java  解压 dsh bundle 到 $PREFIX
├── DshService.java    前台服务
└── ...                WebView 壳、SAF 桥、NAS 同步

harness-src/           ← 改这里，然后同步到 app/
├── android-java/      Harness Java 真相源
├── scripts/           同步与构建脚本
└── docs/              设计稿落地报告
```

### 改了 harness-src 之后
```bash
bash harness-src/scripts/sync-from-source.sh /path/to/此仓库
```
会同步 Java 源码与 assets，并自检一致性。

---

## 调试真机 DOM

```bash
adb forward tcp:9222 localabstract:webview_devtools_remote_$(adb shell pidof com.dsh.harness)
```
之后可用 CDP 直接在真机 WebView 里求值。

> `adb connect` 与 `adb install` 要写在**同一条**命令里，否则各自起 daemon 导致第二次报
> `no devices/emulators found`。

---

## 设计稿

界面严格对齐设计稿，每个组件的尺寸都能在稿子的 CSS 里找到对应数值。
落地报告见 `harness-src/docs/`。

## License

本项目基于 termux-app，其组件遵循上游 LICENSE。
Harness 集成部分见 `harness-src/LICENSE`。