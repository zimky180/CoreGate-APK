# CoreGate APK 工程（GitHub Actions 云编译版）

本目录是 **CoreGate** 的 Android APK 外壳工程，用于把 CoreGate 的 WebUI 打包成原生 App。
所有源码公开透明，可直接阅读、可自行编译。

## 这是什么

CoreGate 本体是一个 KernelSU 模块（仓库 `zimky180/CoreGate`），其配置界面是一份
HTML/JS 的 WebUI（`webroot/index.html`）。本 APK 工程做的事情只有一件：

> 用一个原生 `WebView` 壳加载同一份 WebUI，并通过 `@JavascriptInterface` 桥接
> `ksu.exec(cmd, options, callbackName)`，让 WebUI **零改动**就能在 App 里运行。

也就是说：**APK 只是"搬运工"，业务逻辑、界面、配置格式全部沿用模块的 WebUI**，
不存在两套实现、不存在配置漂移。

## 界面架构（对齐 KernelSU / LSPosed）

- 顶部：标题 + 副标题
- 中部：**分页内容区**，4 页由底部导航栏切换
  1. **锁核** — 运行状态 · 核心可视化 · 锁核建议 · 主开关 · 封锁核心
  2. **策略** — 游戏包名列表 · 录屏/相机自动恢复 · 策略（充电/间隔）
  3. **限频** — 日常限频（含实况）
  4. **自定义** — 诊断 · 测试 · 清理
- 底部：**悬浮液态玻璃导航栏**（`position:fixed`，圆角胶囊 + 跟手模糊 +
  滑动指示胶囊），视觉与 KernelSU / LSPosed 的悬浮导航栏一致

玻璃效果由 CSS `backdrop-filter: blur() saturate()` 实现，元素包含
卡片、导航栏、胶囊指示器、Toast。

## 桥接链路（三层，签名严格对齐）

```
WebUI:  ksu.exec(cmd, '{}', cb)
   ↓
KsuBridge.java   @JavascriptInterface exec(String cmd, String options, String callbackName)
   ↓
RootShell.java   get().exec(cmd, cb)  →  Runtime.exec({"su", "-c", "sh -c '...'"})
   ↓
回主线程 deliver()  →  webView.evaluateJavascript("window['cb'](errno, stdout, stderr)")
```

- `KsuBridge.NAME = "ksu"`（`addJavascriptInterface(bridge, "ksu")`）
- 字符串转义由 `jsStr()` 处理（`\` `'` `\n` `\r` `\u2028` `\u2029`）
- 命令经单线程队列串行执行，避免并发 root 请求触发系统安全弹窗

## 编译方式

### 云端（推荐）
push 到 `main` 分支即自动触发（`.github/workflows/build.yml`），
也可在 Actions 页面手动 `workflow_dispatch`。
产物：Actions 运行结束后在 Artifacts 下载 `CoreGate-APK-debug-v2.1.0`。

工作流使用：JDK 17 + Android SDK (platform 34 / build-tools 34.0.0) + Gradle 8.2 + AGP 8.2.2。

### 本地（可选）
```
cd coregate-apk
gradle assembleDebug
```

## 工程结构

```
coregate-apk/
├── .github/workflows/build.yml     # 云编译工作流
├── build.gradle                    # 顶层：声明 AGP 8.2.2
├── settings.gradle                 # 仓库源 + include ':app'
├── gradle.properties               # useAndroidX=true
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle                # applicationId com.zimky.coregate, minSdk 23
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/index.html       # ★ 完整 WebUI（含分页 + 悬浮导航栏）
        ├── java/com/zimky/coregate/
        │   ├── MainActivity.java   # WebView 壳
        │   └── core/
        │       ├── KsuBridge.java  # JS 桥（ksu.exec）
        │       └── RootShell.java  # su 执行引擎
        └── res/
            ├── drawable/
            └── values/
```

## 配置说明

App 读写的配置文件与模块完全一致：

```
/data/adb/CoreGate/config.json    # 主配置
/data/adb/CoreGate/packages.txt   # 游戏包名
```

字段名、默认值、取值范围均与模块 `service.sh` 解析逻辑一一对应，
不存在 APK 专用字段。

## 说明

- 不写任何系统分区，所有运行时改动可逆
- release 未签名，请优先使用 debug 版
- 源码以本仓库为准，构建产物仅为方便安装
