# CoreGate APK 工程（GitHub Actions 云编译版）

本目录是 CoreGate Android APK 的标准 Gradle 工程，用于 GitHub Actions 云端编译。

## 为什么不用 AIDE
AIDE Pro 的资源编译工具链（aapt2 + AGP）在生成 R.java 阶段存在缺陷，导致 `gen/` 始终为空、
构建卡死。云端用官方 Android SDK + Gradle + AGP 8.2 编译，稳定可靠。

## 编译方式
push 到 main 分支即自动触发（仅当 `coregate-apk/**` 有变更）。
也可在 Actions 页面手动 `workflow_dispatch` 触发。

产物：Actions 运行结束后在 Artifacts 下载 `CoreGate-APK`。

## 本地编译（可选）
```
cd coregate-apk
./gradlew assembleDebug
```

## 工程结构
- `app/src/main/java/com/zimky/coregate/` — 9 个 Java 源文件
  - `MainActivity.java` — 主界面（FrameLayout + 悬浮 GlassBottomBar）
  - `core/` — CoreGateConfig / CoreGateService / RootShell
  - `ui/` — LockStatus / Strategy / FreqCap / Custom 四个 Fragment
  - `widget/GlassBottomBar.java` — 自绘玻璃底部导航栏
- `app/src/main/res/` — 布局 / drawable / values
- `gradle.properties` — `android.useAndroidX=true`（关键）
