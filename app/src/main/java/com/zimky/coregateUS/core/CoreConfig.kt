package com.zimky.coregateUS.core

import android.util.Base64
import org.json.JSONObject
import java.io.File

/**
 * CoreConfig —— config.json / packages.txt 的读写。
 *
 * ⚠ 血泪教训：必须把 7 个字段全部写回。
 * service.sh 的 cfg_bool / cfg_int / cfg_val 在字段缺失时直接回退默认值，
 * 旧版 config.example.json 只有 7 个字段里的少数几个，
 * 一旦 APK 只写自己认识的字段，capture_boost 就会被静默关掉。
 * 所以 toJson() 永远输出全字段。
 *
 * 模块端约定：
 *   /data/adb/CoreGate/config.json    JSON 单行
 *   /data/adb/CoreGate/packages.txt   每行一个包名（游戏列表）
 */
object Paths {
    const val DIR = "/data/adb/CoreGate"
    const val FILE = "$DIR/config.json"
    const val PACKAGES = "$DIR/packages.txt"
    const val STATE = "$DIR/state"
    const val PID = "$DIR/daemon.pid"
    const val SNAPSHOT = "$DIR/freq_snapshot"

    const val MODULE_DIR = "/data/adb/modules/CoreGate"
    const val SERVICE = "$MODULE_DIR/service.sh"
    const val WEBROOT = "$MODULE_DIR/webroot"
}

/** 字段与 service.sh 一一对应，不要改名。 */
data class CoreConfig(
    /** 模块总开关 */
    var enabled: Boolean = true,
    /** 'auto' 或 "2,3,4" 这种手写名单 */
    var lockCores: String = "auto",
    /** 关掉系统 core_ctl */
    var disableCoreCtl: Boolean = false,
    /** 充电时不锁 */
    var noLockCharging: Boolean = false,
    /** 5~600 秒 */
    var pollInterval: Int = 30,
    /** 日常限频 */
    var limitFreq: Boolean = false,
    /**
     * 限频阈值（可自定义）。
     *
     * 前三项：0 表示「未自定义」，由 service.sh 用内置默认值兜底：
     *   cap_little_mhz=2266 / cap_big_mhz=1651 / cap_gpu_mhz=500
     * 写成 0 而不是留空，是为了让 cfg_int 拿到确定值 —— 缺字段时它会回退默认，
     * 但显式 0 也能被 service.sh 识别成「没设过」，两条路都通。
     *
     * cap_gpu_pwrlevel 例外：0 是合法的最高性能档，无法用 0 当哨兵，
     * 因此用 -1 表示「未设置」（缺键时 optInt/grabInt 也回退 -1），
     * 只要是 >=0 就说明用户存过一个真实档位。
     */
    var capLittleMhz: Int = 0,
    var capBigMhz: Int = 0,
    var capGpuMhz: Int = 0,
    var capGpuPwrLevel: Int = -1,
    /** 录屏 / 相机自动恢复 */
    var captureBoost: Boolean = false,
    /** 冻结：日志上报（六组，默认全关，由用户自己开） */
    var freezeGrpLog: Boolean = false,
    /** 冻结：媒体扫描 */
    var freezeGrpMedia: Boolean = false,
    /** 冻结：云同步 */
    var freezeGrpCloud: Boolean = false,
    /** 冻结：诊断上报 */
    var freezeGrpDiag: Boolean = false,
    /** 冻结：广告服务 */
    var freezeGrpAd: Boolean = false,
    /** 冻结：系统非必要组件（高风险组） */
    var freezeGrpSelf: Boolean = false,
    /** 真机上确实读到了文件的标记，用于诊断页。 */
    var loaded: Boolean = false,
    /**
     * 从磁盘读到的原始 JSON，用于「保留 App 不认识的键」。
     *
     * service.sh 会读 auto_lock_count（1~4，自动锁核个数），但 App 界面上没有这一项。
     * 若保存时只输出自己认识的 7 个键，auto_lock_count 会被抹掉，
     * service.sh 的 cfg_int 随即静默回退成默认值 1 —— 用户设过的数值就丢了。
     * 所以 toJson() 先把 rawExtra 原样铺回，再覆盖自己管理的字段。
     */
    var rawExtra: Map<String, Any?>? = null
) {
    /** 永远输出全部 7 个 App 管理字段，并保留 App 不认识的其它键。 */
    fun toJson(): JSONObject = JSONObject().apply {
        // 先铺回磁盘上原有的、App 不负责的键（如 auto_lock_count）
        rawExtra?.forEach { (k, v) ->
            if (k in MANAGED_KEYS) return@forEach
            if (v == null) put(k, JSONObject.NULL) else put(k, v)
        }
        // 再覆盖 App 管理的字段
        put("enabled", enabled)
        put("lock_cores", lockCores.ifBlank { "auto" })
        put("disable_core_ctl", disableCoreCtl)
        put("no_lock_charging", noLockCharging)
        put("poll_interval", clampInterval(pollInterval))
        put("limit_freq", limitFreq)
        // 限频阈值：0 交给 service.sh 兜底默认，非 0 即为用户自定义
        put("cap_little_mhz", capLittleMhz)
        put("cap_big_mhz", capBigMhz)
        put("cap_gpu_mhz", capGpuMhz)
        put("cap_gpu_pwrlevel", capGpuPwrLevel)
        put("capture_boost", captureBoost)
        // 冻结六组：键名必须与 service.sh 的 freeze_$1 拼出来的完全一致
        put("freeze_grp_log", freezeGrpLog)
        put("freeze_grp_media", freezeGrpMedia)
        put("freeze_grp_cloud", freezeGrpCloud)
        put("freeze_grp_diag", freezeGrpDiag)
        put("freeze_grp_ad", freezeGrpAd)
        put("freeze_grp_self", freezeGrpSelf)
    }

    fun toJsonString(): String = toJson().toString()

    companion object {
        fun defaults(): CoreConfig = CoreConfig()

        /**
         * 把 JSON 里所有键值抓成普通 Map 快照。
         *
         * JSONObject 的 opt* 只认识自己声明的类型，这里直接把原始对象存下来，
         * 保存时再原样 put 回去，避免丢掉 auto_lock_count 这类 App 不管的键。
         */
        private fun snapshotUnknownKeys(o: JSONObject): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            val it = o.keys()
            while (it.hasNext()) {
                val k = it.next()
                m[k] = o.opt(k)
            }
            return m
        }

        /** App 自己负责写回的键，其余键一律原样保留。 */
        private val MANAGED_KEYS = setOf(
            "enabled", "lock_cores", "disable_core_ctl", "no_lock_charging",
            "poll_interval", "limit_freq", "capture_boost",
            "cap_little_mhz", "cap_big_mhz", "cap_gpu_mhz", "cap_gpu_pwrlevel",
            "freeze_grp_log", "freeze_grp_media", "freeze_grp_cloud",
            "freeze_grp_diag", "freeze_grp_ad", "freeze_grp_self"
        )

        fun clampInterval(v: Int): Int = when {
            v < 5 -> 5
            v > 600 -> 600
            else -> v
        }

        fun parse(raw: String?): CoreConfig {
            val c = defaults()
            if (raw.isNullOrBlank()) return c
            val s = raw.trim()

            // 先严格解析
            runCatching {
                val o = JSONObject(s)
                c.enabled = o.optBoolean("enabled", true)
                c.lockCores = o.optString("lock_cores", "auto")
                c.disableCoreCtl = o.optBoolean("disable_core_ctl", false)
                c.noLockCharging = o.optBoolean("no_lock_charging", false)
                c.pollInterval = clampInterval(o.optInt("poll_interval", 30))
                c.limitFreq = o.optBoolean("limit_freq", false)
                // 限频阈值：0（前三项）/ -1（pwrlevel）视为「未自定义」，由 service.sh 兜底
                c.capLittleMhz = o.optInt("cap_little_mhz", 0)
                c.capBigMhz = o.optInt("cap_big_mhz", 0)
                c.capGpuMhz = o.optInt("cap_gpu_mhz", 0)
                c.capGpuPwrLevel = o.optInt("cap_gpu_pwrlevel", -1)
                c.captureBoost = o.optBoolean("capture_boost", false)
                // 冻结六组，默认全关；用户自己开过才为 true
                c.freezeGrpLog = o.optBoolean("freeze_grp_log", false)
                c.freezeGrpMedia = o.optBoolean("freeze_grp_media", false)
                c.freezeGrpCloud = o.optBoolean("freeze_grp_cloud", false)
                c.freezeGrpDiag = o.optBoolean("freeze_grp_diag", false)
                c.freezeGrpAd = o.optBoolean("freeze_grp_ad", false)
                c.freezeGrpSelf = o.optBoolean("freeze_grp_self", false)
                // 记住 App 不认识的键（auto_lock_count 等），保存时原样写回
                c.rawExtra = snapshotUnknownKeys(o)
                c.loaded = true
                if (c.lockCores.isBlank()) c.lockCores = "auto"
                return c
            }
            // 落到字段级兜底：模块被手改坏、或之前是别的格式

            c.enabled = grabBool(s, "enabled", true)
            c.lockCores = grabStr(s, "lock_cores", "auto")
            c.disableCoreCtl = grabBool(s, "disable_core_ctl", false)
            c.noLockCharging = grabBool(s, "no_lock_charging", false)
            c.pollInterval = clampInterval(grabInt(s, "poll_interval", 30))
            c.limitFreq = grabBool(s, "limit_freq", false)
            c.capLittleMhz = grabInt(s, "cap_little_mhz", 0)
            c.capBigMhz = grabInt(s, "cap_big_mhz", 0)
            c.capGpuMhz = grabInt(s, "cap_gpu_mhz", 0)
            c.capGpuPwrLevel = grabInt(s, "cap_gpu_pwrlevel", -1)
            c.captureBoost = grabBool(s, "capture_boost", false)
            c.freezeGrpLog = grabBool(s, "freeze_grp_log", false)
            c.freezeGrpMedia = grabBool(s, "freeze_grp_media", false)
            c.freezeGrpCloud = grabBool(s, "freeze_grp_cloud", false)
            c.freezeGrpDiag = grabBool(s, "freeze_grp_diag", false)
            c.freezeGrpAd = grabBool(s, "freeze_grp_ad", false)
            c.freezeGrpSelf = grabBool(s, "freeze_grp_self", false)
            c.loaded = true
            if (c.lockCores.isBlank()) c.lockCores = "auto"
            return c
        }

        private fun grabBool(s: String, k: String, def: Boolean): Boolean {
            val v = grabRaw(s, k) ?: return def
            if (v.startsWith("true")) return true
            if (v.startsWith("false")) return false
            return def
        }

        private fun grabInt(s: String, k: String, def: Int): Int {
            val v = grabRaw(s, k) ?: return def
            return v.trim().toIntOrNull() ?: def
        }

        private fun grabStr(s: String, k: String, def: String): String {
            val i = s.indexOf("\"$k\"")
            if (i < 0) return def
            val p = s.indexOf(':', i)
            if (p < 0) return def
            val q1 = s.indexOf('"', p + 1)
            if (q1 < 0) return def
            val q2 = s.indexOf('"', q1 + 1)
            if (q2 < 0) return def
            return s.substring(q1 + 1, q2)
        }

        /** 取 "key": 后面裸值的开头（true / false / 数字）。 */
        private fun grabRaw(s: String, k: String): String? {
            val i = s.indexOf("\"$k\"")
            if (i < 0) return null
            val p = s.indexOf(':', i)
            if (p < 0) return null
            val sb = StringBuilder()
            for (j in (p + 1) until s.length) {
                val ch = s[j]
                if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') continue
                if (ch == ',' || ch == '}' || ch == ']') break
                sb.append(ch)
            }
            return sb.toString().ifEmpty { null }
        }

        fun base64(s: String?): String {
            val t = s ?: ""
            return Base64.encodeToString(t.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }

        /**
         * 生成一条原子落盘的 shell 命令。
         *
         * 用 base64 传递内容，彻底绕开引号 / 换行 / 中文注释等转义问题
         * （WebUI 用 btoa(encodeURIComponent()) 干的是同一件事）。
         * packages 传 null 表示不动 packages.txt。
         */
        fun buildSaveCmd(cfg: CoreConfig, packages: String?): String {
            val sb = StringBuilder()
            sb.append("CFG='").append(base64(cfg.toJsonString())).append("'; ")
            sb.append("echo \"\$CFG\" | base64 -d > ").append(Paths.FILE).append(".tmp && ")
            sb.append("mv ").append(Paths.FILE).append(".tmp ").append(Paths.FILE).append(" && ")
            sb.append("chmod 644 ").append(Paths.FILE).append("; ")

            if (packages != null) {
                sb.append("PKG='").append(base64(packages)).append("'; ")
                sb.append("echo \"\$PKG\" | base64 -d > ").append(Paths.PACKAGES).append(".tmp && ")
                sb.append("mv ").append(Paths.PACKAGES).append(".tmp ").append(Paths.PACKAGES).append(" && ")
                sb.append("chmod 644 ").append(Paths.PACKAGES).append("; ")
            }

            sb.append("sync; echo OK")
            return sb.toString()
        }
        /**
         * 一次 root 调用取回 config.json + packages.txt。
         *
         * 输出形如（分隔符为纯 ASCII 下划线标记，绝不含 shell 元字符）：
         *   __CG_CFG__
         *   {...}
         *   __CG_PKG__
         *   com.xxx
         *   __CG_END__
         * 缺文件对应段为空，由调用方决定回退。
         */
        const val SEP_CFG = "__CG_CFG__"
        const val SEP_PKG = "__CG_PKG__"
        const val SEP_END = "__CG_END__"

        fun load(sh: RootShell, cb: (cfg: CoreConfig, packagesRaw: String, dbg: String) -> Unit) {
            // ⚠ 分隔符不能含 shell 元字符。旧写法 echo <<<CFG>>> 会被 sh 解析成
            // here-string 重定向（等价于 echo CFG），输出里根本不会出现 <<<CFG>>>，
            // 导致下面的 indexOf 全部为 -1、cfgRaw 恒为空、界面永远显示默认值。
            // 现在改用纯下划线标记，且用单引号包裹保证原样输出。
            val cmd = buildString {
                append("echo '").append(SEP_CFG).append("'; ")
                append("cat ").append(Paths.FILE).append(" 2>/dev/null; echo; ")
                append("echo '").append(SEP_PKG).append("'; ")
                append("cat ").append(Paths.PACKAGES).append(" 2>/dev/null; echo; ")
                append("echo '").append(SEP_END).append("'")
            }

            sh.exec(cmd) { code, out, _ ->
                var cfgRaw = ""
                var pkgRaw = ""
                var dbg = "exit=$code"

                if (out.isNotEmpty()) {
                    val a = out.indexOf(SEP_CFG)
                    val b = out.indexOf(SEP_PKG)
                    val c = out.indexOf(SEP_END)
                    if (a >= 0 && b > a) {
                        cfgRaw = out.substring(a + SEP_CFG.length, b).trim()
                    }
                    if (b >= 0) {
                        val end = if (c > b) c else out.length
                        pkgRaw = out.substring(b + SEP_PKG.length, end).trim()
                    }
                    if (cfgRaw.isEmpty()) {
                        dbg += " · config.json 缺失或为空"
                    } else if (!cfgRaw.trimStart().startsWith("{")) {
                        dbg += " · config.json 内容异常"
                    }
                } else {
                    dbg += " · 无输出（root 未授权？）"
                }

                val cfg = parse(cfgRaw)
                if (cfgRaw.isEmpty()) cfg.loaded = false
                cb(cfg, pkgRaw, dbg)
            }
        }

        /** 游戏包名列表 → 行数组（去空行、去注释）。 */
        fun splitPackages(raw: String?): MutableList<String> {
            val list = mutableListOf<String>()
            if (raw == null) return list
            for (ln in raw.split("\n")) {
                val s = ln.trim()
                if (s.isEmpty()) continue
                if (s.startsWith("#")) continue
                list.add(s)
            }
            return list
        }

        /** 行数组 → packages.txt 文本。 */
        fun joinPackages(list: Collection<String>?): String {
            val sb = StringBuilder()
            list?.forEach {
                val t = it.trim()
                if (t.isNotEmpty()) sb.append(t).append('\n')
            }
            return sb.toString()
        }
    }
}
/**
 * 已废弃：模块就位判定已并入 [SysProbe.probe]（见 `Snapshot.svcState`）。
 *
 * 保留这个函数只是为了让老调用点不会因为「找不到符号」而编译失败，
 * 但**新代码一律不要再调用它**，理由有两条：
 *   1) 它自己起一次 su，而 probe 已经在同一次 root 调用里顺带读到了结果，
 *      多开一个 root 请求既慢又容易触发安全机制的误判；
 *   2) 两条判定路径并存时，一旦其中一条拿到空输出，页面之间就会出现
 *      「核心在线但模块缺失」这种自相矛盾的显示。
 *
 * 正确用法（三态，谁都不许再把 null 当 false）：
 * ```
 * val svc = snapshot?.svcState ?: SysProbe.svcCache   // true / false / null
 * ```
 */
@Deprecated(
    message = "改用 SysProbe 的 Snapshot.svcState / SysProbe.svcCache",
    replaceWith = ReplaceWith("SysProbe.svcCache"),
    level = DeprecationLevel.WARNING
)
fun serviceExists(sh: RootShell? = null, cb: (Boolean?) -> Unit) {
    val cmd = "if [ -f ${Paths.SERVICE} ]; then echo CG_SVC_YES; " +
        "elif [ -d ${Paths.MODULE_DIR} ]; then echo CG_SVC_NODIR; " +
        "else echo CG_SVC_NO; fi"
    if (sh == null) {
        cb(null)
        return
    }
    sh.exec(cmd) { _, out, _ ->
        val o = out
        cb(
            when {
                o.contains("CG_SVC_YES") -> true
                o.contains("CG_SVC_NODIR") -> false
                o.contains("CG_SVC_NO") -> false
                else -> null   // su 没输出，判不了 —— 绝不误报「缺失」
            }
        )
    }
}