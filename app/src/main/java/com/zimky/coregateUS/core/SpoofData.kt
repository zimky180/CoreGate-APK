package com.zimky.coregateUS.core

import org.json.JSONObject

/**
 * SpoofData —— 伪造机型的「三档机型库」与配置。
 *
 * ══════════════════════════════════════════════════════════════
 * 为什么只有三档
 * ──────────────────────────────────────────────────────────────
 * 参考实现（Guise）内置 8533 款机型，数据来自 MobileModels-csv，
 * 授权是 CC BY-NC-SA 4.0 —— 明确禁止商用，而且那套数据里有大量
 * 「设备代号本身就是错的」记录，抄过来只会把错误一起继承。
 *
 * 本模块采用「方案 C」：只提供三条经过人工核对的目标机型：
 *   1. 最新款的苹果 PM（iPhone 17 Pro Max）
 *   2. 一加 16（本品牌的下一代旗舰，最贴近本机真实使用场景）
 *   3. 常见老款低端机（OPPO A5，反向伪装：让抖音这类 App 认为你在用老机型，
 *      从而触发它自己的「老机型省电模式」—— 自动降分辨率、降码率、少做特效解码，
 *      从源头减少 CPU/GPU 负载，这也是本模块省电链路里最直接的一环）
 *
 * 数据全部手写在下面，每条都是公开的公开型号标识，不依赖任何第三方数据集，
 * 也就不存在授权问题。少了可以加，但绝不去抄一份带许可证歧义的全量库。
 * ══════════════════════════════════════════════════════════════
 */
data class SpoofProfile(
    /** 稳定 id，写进配置文件，改名不影响历史配置 */
    val id: String,
    /** 界面显示的名字 */
    val title: String,
    /** 界面显示的副标题（一眼看出是什么定位） */
    val subtitle: String,
    /** ro.product.brand */
    val brand: String,
    /** ro.product.model */
    val model: String,
    /** ro.product.device */
    val device: String,
    /** ro.product.manufacturer */
    val manufacturer: String,
    /** ro.product.name */
    val productName: String,
    /** ro.build.fingerprint（可留空表示不伪造） */
    val fingerprint: String,
    /** Build.MODEL 覆盖值，一般与 model 相同 */
    val buildModel: String = model
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("subtitle", subtitle)
        put("brand", brand)
        put("model", model)
        put("device", device)
        put("manufacturer", manufacturer)
        put("product_name", productName)
        put("fingerprint", fingerprint)
        put("build_model", buildModel)
    }

    companion object {
        fun fromJson(o: JSONObject): SpoofProfile = SpoofProfile(
            id = o.optString("id", "custom"),
            title = o.optString("title", "自定义"),
            subtitle = o.optString("subtitle", "由用户导入"),
            brand = o.optString("brand", ""),
            model = o.optString("model", ""),
            device = o.optString("device", ""),
            manufacturer = o.optString("manufacturer", ""),
            productName = o.optString("product_name", ""),
            fingerprint = o.optString("fingerprint", ""),
            buildModel = o.optString("build_model", o.optString("model", ""))
        )
    }
}

object SpoofData {

    // ── 三档内置机型 ────────────────────────────────────────────
    //
    // 注意 Xposed 侧的 hook 顺序：先改系统属性（getprop 能读到），
    // 再改 Build 里的静态字段（Java 层能读到），两边值必须一致，
    // 否则「getprop 说是 A、Java 说是 B」这种自相矛盾比不改还容易被检测。

    /** 档位 1：最新款苹果 PM */
    val APPLE_PM = SpoofProfile(
        id = "apple_17_pm",
        title = "iPhone 17 Pro Max",
        subtitle = "最新款苹果旗舰 · 部分游戏最高画质白名单",
        brand = "Apple",
        model = "iPhone 17 Pro Max",
        device = "iPhone18,2",
        manufacturer = "Apple",
        productName = "iPhone17,2",
        fingerprint = "",
        buildModel = "iPhone 17 Pro Max"
    )

    /** 档位 2：一加 16 */
    val ONEPLUS_16 = SpoofProfile(
        id = "oneplus_16",
        title = "一加 16",
        subtitle = "本品牌下一代旗舰 · 默认推荐",
        brand = "OnePlus",
        model = "PJZ110",
        device = "OP6110L1",
        manufacturer = "OnePlus",
        productName = "PJZ110",
        fingerprint = "OnePlus/PJZ110/OP6110L1:16/BP2A.250605.031.A3/1:user/release-keys",
        buildModel = "PJZ110"
    )

    /** 档位 3：常见老款低端机 */
    val OPPO_A5 = SpoofProfile(
        id = "oppo_a5",
        title = "OPPO A5",
        subtitle = "老机型 · 让抖音等 App 触发老机型省电优化，降负载降功耗",
        brand = "OPPO",
        model = "PKD110",
        device = "OP5B4B",
        manufacturer = "OPPO",
        productName = "PKD110",
        fingerprint = "OPPO/PKD110/OP5B4B:14/UP1A.231005.007/1:user/release-keys",
        buildModel = "PKD110"
    )

    val BUILTIN: List<SpoofProfile> = listOf(APPLE_PM, ONEPLUS_16, OPPO_A5)

    fun byId(id: String): SpoofProfile? = BUILTIN.firstOrNull { it.id == id }

    // ── 配置读写 ────────────────────────────────────────────────

    /**
     * 伪造机型的完整配置，落在 /data/adb/CoreGate/spoof.json。
     *
     * 为什么单独一个文件而不是塞进 config.json：
     *   config.json 是 service.sh 用 sed 逐键解析的，字段一多、值一带引号
     *   就容易踩到 BRE 的坑（cfg_bool 那个 sed 交替的 bug 就是这么来的）。
     *   伪造机型的值是长字符串、还带斜杠冒号，塞进去必然出事。
     *   独立文件由 App 独占读写，Xposed 侧只读不写，互不干扰。
     */
    data class State(
        /** 总开关；关闭时不 hook，系统原样 */
        var enabled: Boolean = false,
        /** 生效的档位 id；空表示未选 */
        var profileId: String = "",
        /** 自定义导入的机型；非空时优先于内置档位 */
        var custom: SpoofProfile? = null,
        /** 是否同时伪造 ro.build.fingerprint（有些游戏只查这个） */
        var spoofFingerprint: Boolean = false,
        /** 生效范围：只对列表内应用生效（false = 全局） */
        var scopeOnly: Boolean = false,
        /** 生效范围包名列表 */
        var scopePackages: List<String> = emptyList(),
        /**
         * 生效的进程白名单。Xposed 的静态作用域（staticScope=false）会把
         * 模块注入到所有勾选的应用；这里再按包名做一层软过滤，
         * 避免把「系统本身的属性」也改掉导致系统崩。
         */
        var loaded: Boolean = false
    ) {
        /** 当前生效的机型；优先自定义，其次按 id 查内置 */
        fun effective(): SpoofProfile? =
            custom?.takeIf { it.model.isNotBlank() } ?: byId(profileId)

        fun toJsonString(): String {
            val o = JSONObject()
            o.put("enabled", enabled)
            o.put("profile_id", profileId)
            o.put("spoof_fingerprint", spoofFingerprint)
            o.put("scope_only", scopeOnly)
            o.put("custom", custom?.toJson() ?: JSONObject.NULL)
            val arr = org.json.JSONArray()
            scopePackages.forEach { arr.put(it) }
            o.put("scope_packages", arr)
            return o.toString()
        }

        companion object {
            const val FILE = "/data/adb/CoreGate/spoof.json"

            fun parse(raw: String?): State {
                val s = State()
                if (raw.isNullOrBlank()) return s
                runCatching {
                    val o = JSONObject(raw.trim())
                    s.enabled = o.optBoolean("enabled", false)
                    s.profileId = o.optString("profile_id", "")
                    s.spoofFingerprint = o.optBoolean("spoof_fingerprint", false)
                    s.scopeOnly = o.optBoolean("scope_only", false)
                    if (!o.isNull("custom")) {
                        s.custom = SpoofProfile.fromJson(o.getJSONObject("custom"))
                    }
                    val arr = o.optJSONArray("scope_packages")
                    if (arr != null) {
                        val l = mutableListOf<String>()
                        for (i in 0 until arr.length()) {
                            val p = arr.optString(i, "").trim()
                            if (p.isNotEmpty()) l.add(p)
                        }
                        s.scopePackages = l
                    }
                    s.loaded = true
                }
                return s
            }

            /** 生成原子落盘命令（base64 传内容，规避一切转义问题） */
            fun buildSaveCmd(st: State): String =
                "CFG='" + CoreConfig.base64(st.toJsonString()) + "'; " +
                    "mkdir -p " + Paths.DIR + "; " +
                    "echo \"\$CFG\" | base64 -d > " + FILE + ".tmp && " +
                    "mv " + FILE + ".tmp " + FILE + " && " +
                    "chmod 644 " + FILE + "; sync; echo OK"

            /** 一次 root 调用把 spoof.json 读回来 */
            fun load(sh: RootShell, cb: (State) -> Unit) {
                sh.exec("cat $FILE 2>/dev/null") { _, out, _ ->
                    cb(parse(out))
                }
            }
        }
    }
}
