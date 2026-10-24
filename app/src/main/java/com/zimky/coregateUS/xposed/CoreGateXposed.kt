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
 * 【3】选内置档位时必须让 custom 为 null。
 *      effective() 的语义是 custom 优先于 profileId，若 App 切档时没清
 *      custom，这里就会继续用旧的 custom，表现成「切了没反应」。
 *      App 侧 persist() 已保证这一点，这里只做防御性处理。
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

        /** App 侧的落盘路径，两侧必须一致（SpoofData.State.Companion.FILE）。 */
        private const val CFG = "/data/adb/CoreGate/spoof.json"

        /** 配置缓存有效期：避免每次属性读取都碰文件 IO。 */
        private const val CACHE_TTL_MS = 1500L
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

        val raw = try {
            val f = File(CFG)
            if (!f.exists() || !f.canRead()) {
                clearSpoof()
                return
            }
            f.readText()
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "读配置失败: " + t.message)
            clearSpoof()
            return
        }

        if (!bool(raw, "enabled")) {
            clearSpoof()
            return
        }

        // 自定义优先，其次内置档位
        val custom = obj(raw, "custom")
        val base = custom ?: builtin(str(raw, "profile_id"))
        if (base == null || base["model"].isNullOrBlank()) {
            clearSpoof()
            return
        }

        // 作用域：scope_only=true 时只对 scope_packages 里的包生效
        if (bool(raw, "scope_only")) {
            val list = arr(raw, "scope_packages")
            if (pkg.isNullOrBlank() || list.none { it == pkg }) {
                clearSpoof()
                return
            }
        }

        val m = HashMap<String, String>()
        m["ro.product.model"] = base["model"]!!
        base["brand"]?.takeIf { it.isNotBlank() }?.let { m["ro.product.brand"] = it }
        base["device"]?.takeIf { it.isNotBlank() }?.let { m["ro.product.device"] = it }
        base["manufacturer"]?.takeIf { it.isNotBlank() }?.let { m["ro.product.manufacturer"] = it }
        base["productName"]?.takeIf { it.isNotBlank() }?.let { m["ro.product.name"] = it }
        if (bool(raw, "spoof_fingerprint")) {
            base["fingerprint"]?.takeIf { it.isNotBlank() }?.let {
                m["ro.build.fingerprint"] = it
                m["ro.product.build.fingerprint"] = it
            }
        }
        spoof = m
        active = true
        log(Log.INFO, TAG, "生效: model=" + m["ro.product.model"] + " 共 " + m.size + " 项")
    }

    private fun clearSpoof() {
        spoof = null
        active = false
    }

    /** 内置三档；id 必须与 App 侧 SpoofData.BUILTIN 完全一致。 */
    private fun builtin(id: String?): Map<String, String>? = when (id) {
        "apple_17_pm" -> mapOf(
            "model" to "iPhone 17 Pro Max",
            "brand" to "Apple",
            "device" to "iPhone18,2",
            "manufacturer" to "Apple",
            "productName" to "iPhone17,2",
            "fingerprint" to ""
        )
        "oneplus_16" -> mapOf(
            "model" to "PJZ110",
            "brand" to "OnePlus",
            "device" to "OP6110L1",
            "manufacturer" to "OnePlus",
            "productName" to "PJZ110",
            "fingerprint" to "OnePlus/PJZ110/OP6110L1:16/BP2A.250605.031.A3/1:user/release-keys"
        )
        "oppo_a5" -> mapOf(
            "model" to "PKD110",
            "brand" to "OPPO",
            "device" to "OP5B4B",
            "manufacturer" to "OPPO",
            "productName" to "PKD110",
            "fingerprint" to "OPPO/PKD110/OP5B4B:14/UP1A.231005.007/1:user/release-keys"
        )
        else -> null
    }

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
        hookIntLong(cls, "getInt")
        hookIntLong(cls, "getLong")
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

    private fun hookIntLong(cls: Class<*>, name: String) {
        try {
            val m = cls.getDeclaredMethod(name, String::class.java, Int::class.javaPrimitiveType)
            m.isAccessible = true
            hook(m).intercept { chain ->
                val hit = spoofValue(chain.getArg(0) as? String)
                val n = hit?.toIntOrNull()
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
        setStatic("MODEL", s["ro.product.model"])
        setStatic("BRAND", s["ro.product.brand"])
        setStatic("DEVICE", s["ro.product.device"])
        setStatic("MANUFACTURER", s["ro.product.manufacturer"])
        setStatic("PRODUCT", s["ro.product.name"])
        setStatic("FINGERPRINT", s["ro.build.fingerprint"])
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
        listOf("id", "title", "subtitle", "brand", "model", "device", "manufacturer", "productName", "fingerprint", "buildModel")
            .forEach { f -> scalars(body, f)?.let { m[f] = it } }
        return if (m["model"].isNullOrBlank()) null else m
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