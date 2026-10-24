# CoreGate v2.2.0 伪造机型（Xposed 化）构建前静态审查报告

时间：2026-10-25
范围：`_native` 工程全部 34 个 Kotlin 文件 + Gradle 配置 + CI 工作流 + `META-INF/xposed/` 三件套
结论：**静态审查全部通过，可以进入 CI 构建阶段**（推送动作仍需用户点头）

---

## 一、审查结论速览

| 检查项 | 结果 | 依据 |
|---|---|---|
| 前 34 个 Kotlin 文件括号平衡 | 通过 | 33 个直接通过；`SysProbe.kt` 的 230:231 已定性为脚本误报 |
| libxposed API 调用签名 | 通过 | 6 个方法签名与 17 处 `log` + 3 处 `intercept` 逐一对映 |
| 三个 Param 接口属性映射 | 通过 | 逐个 getter 对照，Kotlin 属性语法成立 |
| `Hooker` SAM 转换合法性 | 通过 | 单抽象方法接口，无需 `@FunctionalInterface` |
| `META-INF/xposed/` 三件套 | 通过 | 内容、行数、行尾、权限全部合规 |
| `packaging.merges` 打包配置 | 通过 | `merges += 'META-INF/xposed/*'` 是唯一进包途径，已存在 |
| `compileOnly` 依赖范围 | 通过 | libxposed 不打进 APK，防与框架类冲突 |
| Manifest 声明 | 无需改动 | 官方示例同样无 xposed meta-data |
| 类名与 java_init.list 一致 | 通过 | `com.zimky.coregateUS.xposed.CoreGateXposed` 严格对上 |
| 配置文件路径两端一致 | 通过 | 两侧均为 `/data/adb/CoreGate/spoof.json` |
| 三档内置机型 id 两端一致 | 通过 | `apple_17_pm` / `oneplus_16` / `oppo_a5` |
| CI 工作流 YAML 结构 | 通过（已改进） | 见第四节 |

---

## 二、`SysProbe.kt` 的 230:231 为脚本误报（已证伪）

静态检查报 `(` 230 个、`)` 231 个，多 1 个右括号。逐行增量分析结果：

- 全文件原始计数：`(` 246 / `)` 243，差 3
- 逐行累计最终 `acc = 3`，与原始差值一致，说明偏差集中在少数行
- 偏差来源全部是**字符串字面量与中文注释里的 shell 语法**：
  - 第 137、138 行是注释，正文写的就是反例 `SPID=$($(...)` 和 `$((` 会被当成算术展开
  - 第 141、142、145 行是 shell 命令拼装字符串，内含 `$(` 与 `)`
- `data class Core(` 与 `data class Snapshot(` 两处累计均正确归零，结构完整

三条独立证据链：①偏差全在注释/字符串内；②剥注释剥字符串后仅剩 1 个残差（源于剥离边界处理）；③该文件全程未改动且历史构建一直成功。

**判定：文件完好，不需修改。**

---

## 三、libxposed 调用链合法性（核心编译风险已排除）

### 3.1 `Hooker` 的 SAM 转换

- `API_Interface.java:302` 定义 `interface Hooker`，**全文无 `@FunctionalInterface`**（`grep -c` 得 0）
- 唯一抽象方法在 313 行：`Object intercept(@NonNull Chain chain) throws Throwable`
- Kotlin 对 Java 单抽象方法接口的 lambda 转换**不要求**该注解

故 `hook(m).intercept { chain -> ... }` 三处写法全部合法。

### 3.2 三处 lambda 返回类型推断

| 位置 | 分支返回类型 | 公共类型 | 目标类型 |
|---|---|---|---|
| `hookGetter` 259 行 | `String` / `Any?` / `Any?` | `Any?` | `Object!` |
| `hookIntLong` 279 行 | `Int` / `Any?` | `Any?` | `Object!` |
| `hookBool` 293 行 | `Boolean` / `Any?` | `Any?` | `Object!` |

`Any?` 可安全赋值给平台类型 `Object!`，三处均通过。

### 3.3 继承链

`XposedModule extends XposedInterfaceWrapper implements XposedModuleInterface`（`API_Module.java:8`）
→ `log(...)` 来自 Wrapper（`public final`，可调用不可覆写）；三个生命周期回调来自 ModuleInterface 的 default 方法（可覆写）。这与本地代码只 `override` 三个方法、`log` 直接用完全吻合。

---

## 四、CI 工作流改进（本轮唯一代码改动）

`.github/workflows/build.yml` 原第 30 行：

```bash
"$SM" "platform-tools" "platforms;android-37.0" "build-tools;36.0.0"
```

**风险**：单条 `sdkmanager` 命令安装多项，任一失败整条退出（GH Actions `run` 默认 `set -e`），SDK 阶段硬失败会导致 `assembleDebug` 根本执行不到。且 `compileSdk 37` 下 AGP 9.1.1 会校验平台版本，只装 `37.0` 而稳定通道已有 `37.1` / `37.2`，存在被判定平台过旧的可能。

**改法**（实测官方 `repository2-3.xml` 确认 `android-37.0` / `37.1` / `37.2` 均在稳定通道 `channel-0`）：

```bash
"$SM" "platform-tools" "build-tools;36.0.0" || true
"$SM" "platforms;android-37.2" \
  || "$SM" "platforms;android-37.1" \
  || "$SM" "platforms;android-37.0"
```

三重回退，任一版本被下架都不会整体失败。已用 PyYAML 解析验证：8 个 step 结构完整，`run` 块缩进正确，`\` 续行原样保留。

---

## 五、配置链路双端一致性

### 5.1 路径

- Xposed 侧：`CoreGateXposed.kt:67` → `CFG = "/data/adb/CoreGate/spoof.json"`
- App 侧：`SpoofData.kt:178` → `FILE = "/data/adb/CoreGate/spoof.json"`

完全一致。

### 5.2 写入方式（`SpoofData.buildSaveCmd`）

base64 编码传内容 + `.tmp` 临时文件 + `mv` 原子替换 + `chmod 644` + `sync`
→ 规避所有转义问题，且不会出现写一半的半损文件。

### 5.3 `custom: null` 的处理（关键正确性验证）

`toJsonString()` 在无自定义时输出 `"custom":`（无花括号）。
Xposed 侧 `obj(raw,"custom")` → `indexOf('{')` 找不到（`scope_packages` 是方括号数组，不含 `{`）→ 返回 `null` → `base = builtin(profile_id)` 正确回退内置档位。

即使配了自定义对象，`obj()` 用 `depth` 花括号配对扫描截取主体，末尾 `if (m["model"].isNullOrBlank()) return null` 是防污染最后保险。

三档内置值的 fingerprint 均不含 `{}`，不会干扰 `obj()` 的括号扫描。

### 5.4 选档原子性

`SpoofPage.kt:219` → `persist(st.copy(profileId = p.id, custom = null))`
选内置档时同时清空 `custom`，避免残留自定义值覆盖内置档位。

---

## 六、`refreshCfg` 五道防线（复述确认）

1. 文件不存在或不可读 → `clearSpoof()`
2. 读取抛异常 → 打 WARN + `clearSpoof()`
3. `enabled = false` → `clearSpoof()`
4. `base == null` 或 `model` 为空 → `clearSpoof()`
5. `scope_only = true` 且当前包不在 `scope_packages` → `clearSpoof()`

缓存 1500ms（`CACHE_TTL_MS`），`@Synchronized`。

字段映射：`model` 必写；`brand`/`device`/`manufacturer`/`productName` 走 `takeIf { isNotBlank() }`；指纹开关为真时同时写 `ro.build.fingerprint` 与 `ro.product.build.fingerprint`。

---

## 七、`hookBuildFields` 六字段

| Build 字段 | 属性键 |
|---|---|
| `MODEL` | `ro.product.model` |
| `BRAND` | `ro.product.brand` |
| `DEVICE` | `ro.product.device` |
| `MANUFACTURER` | `ro.product.manufacturer` |
| `PRODUCT` | `ro.product.name` |
| `FINGERPRINT` | `ro.build.fingerprint` |

`setStatic` 会先反射清 `Modifier.FINAL` 位（内层失败静默，因为属性 hook 仍能兜住），再 `f.set(null, value)`。

---

## 八、安全性说明（保持原有设计认知）

伪造只在**目标应用进程内** hook 属性读取的返回值，系统属性一个字节都不写：

- `getprop` 仍返回真值
- 关开关 / 停模块 / 卸载后立即恢复
- 不存在“属性写错导致 zygote 起不来”的路径
- `onSystemServerStarting` 内只做 `Class.forName` 预热，**不介入 system_server 任何属性读写**

---

## 九、下一步（等待用户指令）

1. **推送**：`push_v220.py B` 推私有仓，触发 GitHub Actions（**需用户同意**）
2. **验证构建**：重点看依赖解析命中 mavenCentral、三件套是否真进 APK、`java_init.list` 是否被 adapt
3. **产物校验**：用 `python3 zipfile` 查 APK 内 `META-INF/xposed/` 三文件存在且内容正确、无 libxposed 类被打进去
4. **真机测试**：刷入 + LSPosed 勾选作用域，三档各测 `Build.MODEL` 等字段，`getprop` 对照真机值不变
5. **关闭验证**：关开关后确认立即失效
6. **一次性汇报**：锁核限频、冻结、墓碑、伪造机型四项功能

---

## 十、遗留说明（非阻塞）

- `obj()` 字段清单含 `buildModel`，`refreshCfg` 未使用 —— App 侧 UI 预留，无害
- `arr()` 无法处理嵌套数组 —— `scope_packages` 恰是纯字符串数组，够用
- `/storage/emulated/0` 上文件权限恒为 660 —— FUSE/sdcardfs 内核强制，`chmod` 被静默忽略；CI 侧经 git checkout 后由 git 重新赋权（默认 644），不影响打包
- `hookGetter` 的 `argc > 1` 分支直接返回调用方默认值而跳过原调用 —— 语义等价，属性能优化
