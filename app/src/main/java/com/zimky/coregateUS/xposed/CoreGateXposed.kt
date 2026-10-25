package com.zimky.coregateUS.xposed

import android.os.Build
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.io.File

/**
 * CoreGate 的 Xposed 入口：只做一件事——在目标应用进程内伪造机型读数。
 *
 * ═══════════════════════════════════════════════════════════════════
 * 设计原则（跟锁核/限频完全一致的哲学：不写系统分区，关掉即还原）
 * ═══════════════════════════════════════════════════════════════════
 * 系统属性一个字节都不写。我们只拦截「属性读取方法」的返回值，
 * 让目标进程看到 iPhone / 一加 16 / OPPO A5，而：
 *   · getprop（shell）读的仍是真机值
 *   · 其他没勾选作用域的应用读的仍是真机值
 *   · 停用模块 / 关闭开关 / 卸载 APK 后，一切自动恢复，无残留
 * 因此不存在「属性写坏了 zygote 起不来」的风险——这条路径根本不存在。
 *
 * ═══════════════════════════════════════════════════════════════════
 * 三条硬性约束
 * ═══════════════════════════════════════════════════════════════════
 * 【1】属性读数 + Build 字段，两条都要 hook。
 *      SystemProperties.get 是绝大多数检测的入口（Build.MODEL 在类初始化时
 *      就是从它取值来的），只 hook 它会留下「静态字段仍是旧值」的漏洞；
 *      只改字段则漏掉所有走 SystemProperties 的调用。两者都做才干净。
 *
 * 【2】配置只读 /data/adb/CoreGate/spoof.json，绝不回写。
 *      该文件由 App 独占写（base64 原子写），Xposed 侧只读解析。
 *      半个字节都不写，所以不可能出现「模块把用户配置写坏」这种事。
 *
 * 【3】伪装只有 OPPO A5 一档，配置结构固定。
 *      App 侧无条件写 {"enabled", "profile"{6 键}, "scope_packages"}，
 *      硬件画像（SoC/主板/指纹/ABI/API level）由本侧 OPPO_A5_FULL 补齐。
 *      用户在本模块能做的唯一操作就是打开开关，
 *      真正生效范围由 LSPosed 里给本模块勾选的作用域决定，这里不再做机型选择。
 *
 * ═══════════════════════════════════════════════════════════════════
 * 为什么 hook 多条路径
 * ═══════════════════════════════════════════════════════════════════
 * Android 里「读机型」至少有四条独立路径，漏一条就可能被绕过：
 *   1. android.os.SystemProperties.get / getInt / getLong / getBoolean ← 底层
 *   2. android.os.Build.MODEL / DEVICE / BRAND / MANUFACTURER / PRODUCT / FINGERPRINT
 *   3. ro.product.* 系列属性          ← 被 1 覆盖
 *   4. 少数应用直接读 /system/build.prop ← 只读文件，不在本模块范围
 * 前两条全覆盖；第四条极其罕见且需要挂载读写，做了就违背零残留原则，不做。
 *
 * ═══════════════════════════════════════════════════════════════════
 * 关于 API（全部对齐 libxposed api 102.0.0 源码，不是凭记忆写的）
 * ═══════════════════════════════════════════════════════════════════
 * · 生命周期回调来自 XposedModuleInterface 的 default 方法，Kotlin 侧直接 override。
 * · 参数是 getter：param.processName / param.packageName（Kotlin 属性语法映射 getXxx）。
 * · hook 是 HookBuilder 链式：hook(executable).intercept { chain -> ... }，
 *   Hooker 是 SAM 接口，Kotlin 下直接传 lambda，不必写匿名对象。
 * · Chain 提供 getArg(int) / getArgs() / getThisObject() / proceed() / proceedWith()。
 * · log(priority, tag, msg) 是 XposedInterface 的方法，输出到 logcat。
 */
class CoreGateXposed : XposedModule() {

    companion object {
        private const val TAG = "CoreGate-Spoof"

        /**
         * 配置候选路径，按顺序尝试，第一个读得通的就用。
         *
         * ══════════════════════════════════════════════════════════════
         * 为什么不能只写 /data/adb/CoreGate/spoof.json（v2.2.3 修的坑）
         * ──────────────────────────────────────────────────────────────
         * 早期只认 /data/adb 这一个路径，结果伪造在真机上「永远不生效」：
         *   · /data/adb 的权限是 drwx------ root root（700），连 system(1000)
         *     都进不去，更别说被注入的抖音进程（untrusted_app 域）。
         *   · 于是 File(CFG).exists() 恒为 false → clearSpoof() → active=false
         *     → 日志里刷「不在生效范围，跳过」，hook 一次都没装上。
         *   · 用 su 2000 实测 cat /data/adb/CoreGate/spoof.json 就是
         *     "Permission denied"，证据确凿。
         *
         * /data/local/tmp 是 drwxrwx--x，别的应用能进能读，su 2000 实测
         * 可完整读出配置，所以把它作为首选；/data/adb 放第二位，给
         * system_server 这类本身就是 root 的进程兜底。
         */
        private val CFG_CANDIDATES = arrayOf(
            "/data/local/tmp/CoreGate/spoof.json",
            "/data/adb/CoreGate/spoof.json"
        )

        /** 配置缓存有效期：避免每次属性读取都碰文件 IO。 */
        private const val CACHE_TTL_MS = 1500L

        /**
         * 参与伪造的属性短名全表（"ro." + 短名 = 系统属性名）。
         *
         * 覆盖范围是按「能被拿来判定机型的读数」列的，不是随便凑数量：
         *   · product.*  —— 机型/厂商/主板/市场名/ABI/首次 API，最常被读
         *   · build.*    —— 编译指纹/版本/日期，风控必查，且与 product.* 有交叉校验关系
         *   · soc.*      —— Android 12+ 的 SoC 标准属性，新一代 App 越来越爱读
         *   · board.platform / hardware —— 主板平台与硬件平台，老牌检测手段
         *
         * 只读不写：这些名字仅用于「在 hook 里返回伪造值」，
         * 系统属性本身一个字节都不改，进程退出即恢复。
         */
        private val PROP_KEYS = listOf(
            "product.model",
            "product.brand",
            "product.device",
            "product.manufacturer",
            "product.name",
            "product.board",
            "product.marketname",
            "product.cpu.abi",
            "product.cpu.abilist",
            "product.cpu.abilist32",
            "product.first_api_level",
            "build.product",
            "build.id",
            "build.display.id",
            "build.type",
            "build.tags",
            "build.version.release",
            "build.version.sdk",
            "build.date.utc",
            "build.fingerprint",
            "board.platform",
            "soc.manufacturer",
            "soc.model",
            "hardware"
        )
    }

    // ── 运行时状态（每进程一份，volatile 保证 hook 线程可见）────────────

    /** 本进程是否被允许伪造。false 时所有 hook 原样透传。 */
    @Volatile
    private var active = false

    /** 当前生效的伪造值：属性名 → 伪造值。active=false 时为 null。 */
    @Volatile
    private var spoof: Map<String, String>? = null

    /** 配置缓存时间戳。 */
    @Volatile
    private var cacheAt = 0L

    // ── 生命周期 ──────────────────────────────────────────────────

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        log(Log.INFO, TAG, "onModuleLoaded: proc=" + param.processName + " sysServer=" + param.isSystemServer)
    }

    /**
     * system_server 里刻意什么都不做。
     * 改 system_server 的属性读数等于改全局，污染面太大，风险不可控。
     * 伪造机型只需要在应用进程内生效，这里彻底不介入。
     */
    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        log(Log.INFO, TAG, "onSystemServerStarting: 按设计不介入")
    }

    /**
     * 主入口：每个被注入的应用进程回调一次。
     * 在这里判断「本进程要不要伪造」，需要才装 hook。
     */
    override fun onPackageReady(param: PackageReadyParam) {
        // 同一进程里可能加载多个包（sharedUserId / createPackageContext），
        // 只在主包上动手，避免重复装 hook。
        if (!param.isFirstPackage) return

        val pkg = param.packageName
        refreshCfg(pkg, true)
        if (!active) {
            log(Log.INFO, TAG, "包 " + pkg + " 不在生效范围，跳过")
            return
        }
        log(Log.INFO, TAG, "包 " + pkg + " 命中生效范围，开始 hook")
        hookSystemProperties()
        hookBuildFields()
    }

    // ── 配置读取 ──────────────────────────────────────────────────

    /**
     * 读 spoof.json 并决定本进程要不要动手。
     *
     * 用手写解析而不是 JSON 库，原因有二：
     *   1. 不想在目标应用进程里引入额外类加载器依赖，跟宿主打架风险高；
     *   2. org.json 内部会调 SystemProperties，等于自己 hook 自己，有重入风险。
     * 本文件结构由我们自己的 App 侧 toJsonString() 生成、键名固定，解析够用。
     */
    @Synchronized
    private fun refreshCfg(pkg: String?, force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - cacheAt < CACHE_TTL_MS) return
        cacheAt = now

        // 按候选顺序找第一个「存在且可读」的配置。
        // 读不到不是异常，而是常态（比如 LSPosed 作用域没勾抖音、或者进程域受限），
        // 所以这里只在全部落空时打一条 WARN，把试过的路径一起打出来，方便定位。
        var raw: String? = null
        var usedPath: String? = null
        for (p in CFG_CANDIDATES) {
            try {
                val f = File(p)
                if (f.exists() && f.canRead()) {
                    val text = f.readText()
                    if (text.isNotBlank()) {
                        raw = text
                        usedPath = p
                        break
                    }
                }
            } catch (t: Throwable) {
                log(Log.WARN, TAG, "读 " + p + " 失败: " + t.message)
            }
        }
        if (raw == null) {
            log(Log.WARN, TAG, "找不到可读的伪造配置（试过: " + CFG_CANDIDATES.joinToString() + "）")
            clearSpoof()
            return
        }
        // 显式承接成非空局部常量：raw 是 var String?，Kotlin 的智能转换
        // 对「循环内赋值的局部 var」并不总是放行，这里判空后立刻固化成
        // String，后面所有取值函数都接收非空 String，编译期零悬念。
        val cfg: String = raw ?: ""

        if (!bool(cfg, "enabled")) {
            log(Log.INFO, TAG, "配置 enabled=false，跳过（" + usedPath + "）")
            clearSpoof()
            return
        }

        // 档案：App 侧 SpoofData.toJsonString() 固定写一个 "profile" 对象（6 个标识字段）。
        // 字段不全，硬件画像由本侧的 OPPO_A5_FULL 补齐，详见该常量注释。
        // 用局部非空变量承接，避免依赖编译器的智能转换（复合条件里的判空容易失效）
        val prof = obj(cfg, "profile")
        if (prof == null) {
            log(Log.WARN, TAG, "配置缺少 profile 对象，跳过（" + usedPath + "）")
            clearSpoof()
            return
        }
        val base: Map<String, String> = prof
        val modelOf = base["product.model"] ?: base["model"]
        if (modelOf.isNullOrBlank()) {
            log(Log.WARN, TAG, "profile 里没有机型字段，跳过（" + usedPath + "）")
            clearSpoof()
            return
        }

        // 作用域：scope_packages 非空时按包名过滤，为空则不做包名过滤
        // （实际生效范围由 LSPosed 作用域兜底，用户只勾抖音即可）
        val scopeList = arr(cfg, "scope_packages")
        if (scopeList.isNotEmpty()) {
            if (pkg.isNullOrBlank() || scopeList.none { it == pkg }) {
                log(Log.INFO, TAG, "包 " + pkg + " 不在配置的作用域内（" + scopeList.joinToString() + "）")
                clearSpoof()
                return
            }
        }

        // 伪造表：norm 里出现什么键，就映射到同名系统属性。
        // 键名即属性名去掉 "ro." 前缀后的短名（见 OPPO_A5_FULL 的注释）。
        // norm = OPPO_A5_FULL 全量画像 ∪ App 下发的 6 个标识字段；
        // App 侧用 model/brand/device/manufacturer/product_name 这套短键，
        // 本侧用 product.model 这套长键，下面做一次归一化让两者同构。
        val norm = HashMap<String, String>(OPPO_A5_FULL)
        norm.putAll(base)
        norm["model"]?.let { if (!norm.containsKey("product.model")) norm["product.model"] = it }
        norm["brand"]?.let { if (!norm.containsKey("product.brand")) norm["product.brand"] = it }
        norm["device"]?.let { if (!norm.containsKey("product.device")) norm["product.device"] = it }
        norm["manufacturer"]?.let { if (!norm.containsKey("product.manufacturer")) norm["product.manufacturer"] = it }
        norm["product_name"]?.let { if (!norm.containsKey("product.name")) norm["product.name"] = it }
        norm["productName"]?.let { if (!norm.containsKey("product.name")) norm["product.name"] = it }

        val m = HashMap<String, String>()
        PROP_KEYS.forEach { short ->
            norm[short]?.takeIf { it.isNotBlank() }?.let { m["ro." + short] = it }
        }
        // 机型必须落地，否则这套伪造等于没开
        norm["product.model"]?.takeIf { it.isNotBlank() }?.let { m["ro.product.model"] = it }
        if (m["ro.product.model"].isNullOrBlank()) {
            log(Log.WARN, TAG, "配置里没有可用机型，跳过伪造")
            clearSpoof()
            return
        }
        spoof = m
        active = true
        log(Log.INFO, TAG, "生效: model=" + m["ro.product.model"] + " 共 " + m.size + " 项")
    }

    private fun clearSpoof() {
        spoof = null
        active = false
    }

    /**
     * OPPO A5（PKD110）完整画像：只有这一档，不再做机型库。
     *
     * ══════════════════════════════════════════════════════════════
     * 为什么 App 只传 6 个字段，这里却要留一整张表
     * ──────────────────────────────────────────────────────────────
     * 「伪造机型」最容易被识破的方式不是 model 不对，而是**【内部自相矛盾】**：
     *   model 说自己是 OPPO A5（联发科 MT6835 的千元机），
     *   但 ro.soc.model 还是 SM8845、ro.board.platform 还是 canoe——
     *   任何一家风控只要交叉比对两项就露馅，比干脆不伪造还糟。
     *
     * 所以真实可用的伪造必须是一整套自洽的画像：SoC、主板代号、ABI、
     * 编译指纹、API level、厂商定制版本全部对得上。
     * 这份表就放在 Xposed 侧（注入进程内），App 只需传 6 个标识字段，
     * 剩下的硬件画像由这里补齐并覆盖。
     *
     * 键名规则：短名，前面加 "ro." 就是系统属性名。
     *   例 "product.model" -> ro.product.model
     *   例 "build.fingerprint" -> ro.build.fingerprint
     * ══════════════════════════════════════════════════════════════
     */
    private val OPPO_A5_FULL: Map<String, String> = mapOf(
        "product.model" to "PKD110",
        "product.brand" to "OPPO",
        "product.device" to "OP5B4B",
        "product.manufacturer" to "OPPO",
        "product.name" to "PKD110",
        "product.board" to "mt6835",
        "product.marketname" to "OPPO A5",
        "product.cpu.abi" to "arm64-v8a",
        "product.cpu.abilist" to "arm64-v8a,armeabi-v7a,armeabi",
        "product.cpu.abilist32" to "armeabi-v7a,armeabi",
        "product.first_api_level" to "33",
        "build.product" to "PKD110",
        "build.id" to "UP1A.231005.007",
        "build.display.id" to "PKD110_14.0.1.300(CN01)",
        "build.type" to "user",
        "build.tags" to "release-keys",
        "build.version.release" to "14",
        "build.version.sdk" to "34",
        "build.date.utc" to "1730000000",
        "build.fingerprint" to "OPPO/PKD110/OP5B4B:14/UP1A.231005.007/1:user/release-keys",
        "board.platform" to "mt6835",
        "soc.manufacturer" to "MediaTek",
        "soc.model" to "MT6835",
        "hardware" to "mt6835"
    )

    // ── Hook 一：SystemProperties 读取入口 ─────────────────────────

    private fun hookSystemProperties() {
        val cls = try {
            Class.forName("android.os.SystemProperties")
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "找不到 SystemProperties: " + t.message)
            return
        }

        // get(String key) —— Android 11+ 的单参重载
        hookGetter(cls, "get", 1, String::class.java)
        // get(String key, String def)
        hookGetter(cls, "get", 2, String::class.java, String::class.java)
        // 数字/布尔读取：机型字段用不到，但游戏偶尔拿 getInt 探 SDK，
        // 这里同样透传伪造值，伪造值非数字时回退原样，不抛异常。
        hookInt(cls, "getInt", Int::class.javaPrimitiveType!!)
        hookLong(cls, "getLong", java.lang.Long.TYPE)
        hookBool(cls, "getBoolean")
    }

    /** 命中伪造表返回伪造值，否则返回 null 表示「交给原方法」。 */
    private fun spoofValue(key: String?): String? {
        if (key == null) return null
        return spoof?.get(key)
    }

    /**
     * 装 String 读取的 hook。
     * 若本机不存在该重载，getDeclaredMethod 抛异常后被捕获跳过，不影响其他版本。
     */
    private fun hookGetter(cls: Class<*>, name: String, argc: Int, vararg paramTypes: Class<*>) {
        try {
            val m = cls.getDeclaredMethod(name, *paramTypes)
            m.isAccessible = true
            hook(m).intercept { chain ->
                val key = chain.getArg(0) as? String
                val hit = spoofValue(key)
                if (hit != null) {
                    hit
                } else if (argc > 1) {
                    chain.getArg(1)
                } else {
                    chain.proceed()
                }
            }
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "hook " + name + "/" + argc + "参 失败: " + t.message)
        }
    }

    /**
     * getInt(String, int) 的 hook。
     * 返回类型必须严格是 Integer：框架按方法声明返回 int，塞 Long 会抛
     * ClassCastException 到调用方，比不 hook 更糟。伪造值不是数字时原样透传。
     */
    private fun hookInt(cls: Class<*>, name: String, defType: Class<*>) {
        try {
            val m = cls.getDeclaredMethod(name, String::class.java, defType)
            m.isAccessible = true
            hook(m).intercept { chain ->
                val n = spoofValue(chain.getArg(0) as? String)?.toIntOrNull()
                if (n != null) n else chain.proceed()
            }
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "hook " + name + " 失败: " + t.message)
        }
    }

    /**
     * getLong(String, long) 的 hook。
     * 单独一个函数的原因：第二个参数是 long 而不是 int，
     * 且返回值必须装箱成 java.lang.Long，不能复用 getInt 的实现。
     */
    private fun hookLong(cls: Class<*>, name: String, defType: Class<*>) {
        try {
            val m = cls.getDeclaredMethod(name, String::class.java, defType)
            m.isAccessible = true
            hook(m).intercept { chain ->
                val n = spoofValue(chain.getArg(0) as? String)?.toLongOrNull()
                if (n != null) n else chain.proceed()
            }
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "hook " + name + " 失败: " + t.message)
        }
    }

    private fun hookBool(cls: Class<*>, name: String) {
        try {
            val m = cls.getDeclaredMethod(name, String::class.java, Boolean::class.javaPrimitiveType)
            m.isAccessible = true
            hook(m).intercept { chain ->
                val hit = spoofValue(chain.getArg(0) as? String)
                if (hit != null) hit == "true" else chain.proceed()
            }
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "hook " + name + " 失败: " + t.message)
        }
    }

    // ── Hook 二：Build 静态字段 ───────────────────────────────────

    /**
     * Build 的字段在类初始化时就已成常量，libxposed 不直接支持字段 hook，
     * 所以走「反射改写 static final」。改的是本进程内 Build 类的一份副本，
     * 不影响系统和其他进程，进程退出即消失，零残留。
     *
     * SystemProperties 那条 hook 是主力，这里是补丁——JIT 常量折叠可能让
     * 部分代码在编译期就把字段值内联了，那种情况无法覆盖，属已知边界。
     */
    private fun hookBuildFields() {
        val s = spoof ?: return
        // 与 SystemProperties 那条 hook 一一对应。少改一个字段，
        // 就会出现「SystemProperties.get 返回 A、Build.XXX 返回 B」的自相矛盾，
        // 比完全不改更容易被判定为作弊，所以这里必须同表同源。
        setStatic("MODEL", s["ro.product.model"])
        setStatic("BRAND", s["ro.product.brand"])
        setStatic("DEVICE", s["ro.product.device"])
        setStatic("MANUFACTURER", s["ro.product.manufacturer"])
        setStatic("PRODUCT", s["ro.product.name"])
        setStatic("BOARD", s["ro.product.board"])
        // Build.HARDWARE 读的是 ro.hardware
        setStatic("HARDWARE", s["ro.hardware"])
        setStatic("FINGERPRINT", s["ro.build.fingerprint"])
        // Build.ID / DISPLAY / TYPE / TAGS：编译标识，风控交叉校验必查
        setStatic("ID", s["ro.build.id"])
        setStatic("DISPLAY", s["ro.build.display.id"])
        setStatic("TYPE", s["ro.build.type"])
        setStatic("TAGS", s["ro.build.tags"])
        // Build.PRODUCT 已在上面随 ro.product.name 一并写入，此处不再重复
        // Android 12+ 新增的 SoC 字段，读的是 ro.soc.*
        // 用「伪造后的 SDK」判断字段是否存在，而不是读运行时 Build.VERSION.SDK_INT：
        // 一旦将来补上 SDK_INT 的改写，运行时读取会受自己干扰。
        val fkSdk = s["ro.build.version.sdk"]?.toIntOrNull() ?: Build.VERSION.SDK_INT
        if (fkSdk >= 31) {
            setStatic("SOC_MANUFACTURER", s["ro.soc.manufacturer"])
            setStatic("SOC_MODEL", s["ro.soc.model"])
        }
        // CPU_ABI 是 String 而非数组，能直接改；SUPPORTED_ABIS 是 String[]
        // 数组类型无法用 f.set 换成 String，故只改单值 ABI 字段。
        setStatic("CPU_ABI", s["ro.product.cpu.abi"])
    }

    private fun setStatic(field: String, value: String?) {
        if (value.isNullOrBlank()) return
        try {
            val f = Build::class.java.getDeclaredField(field)
            f.isAccessible = true
            try {
                val mods = java.lang.reflect.Field::class.java.getDeclaredField("modifiers")
                mods.isAccessible = true
                val cur = f.modifiers
                if (cur and java.lang.reflect.Modifier.FINAL != 0) {
                    mods.setInt(f, cur and java.lang.reflect.Modifier.FINAL.inv())
                }
            } catch (_: Throwable) {
                // 改不了修饰符也不致命，属性 hook 仍能兜住
            }
            f.set(null, value)
            log(Log.INFO, TAG, "Build." + field + " = " + value)
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "改 Build." + field + " 失败: " + t.message)
        }
    }

    // ── 极简 JSON 取值（见 refreshCfg 注释，刻意不用库）────────────────

    private fun bool(raw: String, key: String): Boolean = scalars(raw, key) == "true"

    private fun str(raw: String, key: String): String = scalars(raw, key) ?: ""

    private fun scalars(raw: String, key: String): String? {
        val i = raw.indexOf("\"" + key + "\"")
        if (i < 0) return null
        val colon = raw.indexOf(':', i + key.length + 2)
        if (colon < 0) return null
        var j = colon + 1
        while (j < raw.length && raw[j].isWhitespace()) j++
        if (j >= raw.length) return null
        if (raw[j] == '"') {
            val sb = StringBuilder()
            var k = j + 1
            while (k < raw.length) {
                val c = raw[k]
                if (c == '\\' && k + 1 < raw.length) {
                    sb.append(raw[k + 1]); k += 2; continue
                }
                if (c == '"') break
                sb.append(c); k++
            }
            return sb.toString()
        }
        val end = raw.indexOfAny(charArrayOf(',', '}', '\n'), j)
        val stop = if (end < 0) raw.length else end
        return raw.substring(j, stop).trim()
    }

    private fun obj(raw: String, key: String): Map<String, String>? {
        val i = raw.indexOf("\"" + key + "\"")
        if (i < 0) return null
        val open = raw.indexOf('{', i)
        if (open < 0) return null
        var depth = 0
        var k = open
        while (k < raw.length) {
            val c = raw[k]
            if (c == '{') depth++
            if (c == '}') {
                depth--
                if (depth == 0) break
            }
            k++
        }
        if (k >= raw.length) return null
        val body = raw.substring(open + 1, k)
        val m = HashMap<String, String>()
        // 白名单必须与 SpoofProfile 的字段保持一致：
        // 少列一个键，解析出来的表就少一项，伪造就缺一块。
        listOf(
            "brand", "model", "device", "manufacturer",
            "product_name", "productName", "build_model", "buildModel"
        ).forEach { f -> scalars(body, f)?.let { m[f] = it } }
        // 兼容旧配置里可能存在的 productName 驼峰键
        if (!m.containsKey("product_name") && m.containsKey("productName")) {
            m["product_name"] = m["productName"]!!
        }
        return if ((m["model"] ?: m["productName"]).isNullOrBlank()) null else m
    }

    private fun arr(raw: String, key: String): List<String> {
        val i = raw.indexOf("\"" + key + "\"")
        if (i < 0) return emptyList()
        val open = raw.indexOf('[', i)
        if (open < 0) return emptyList()
        val close = raw.indexOf(']', open)
        if (close < 0) return emptyList()
        return raw.substring(open + 1, close)
            .split(',')
            .map { it.trim().trim('"') }
            .filter { it.isNotEmpty() }
    }
}