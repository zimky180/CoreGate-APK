package com.zimky.coregateUS.core

import org.json.JSONObject

/**
 * SpoofData —— 伪造机型配置。
 *
 * ══════════════════════════════════════════════════════════════
 * 设计口径
 * ──────────────────────────────────────────────────────────────
 * 本功能不针对游戏做机型伪装，只服务一个目标：让抖音把本机
 * 认成一台老机型，从而触发它自己的「低端机省电策略」——
 * 自动降分辨率、降码率、少做特效解码，从源头减少 CPU/GPU 负载。
 *
 * 所以不需要多档位选择，只固定一个目标机型：OPPO A5。
 * 用户能做的只有一件事：打开开关，然后去 LSPosed 勾选抖音。
 *
 * 数据全部手写，是公开的型号标识，不依赖任何第三方数据集。
 * ══════════════════════════════════════════════════════════════
 */
data class SpoofProfile(
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
    /** Build.MODEL 覆盖值，一般与 model 相同 */
    val buildModel: String = model
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("brand", brand)
        put("model", model)
        put("device", device)
        put("manufacturer", manufacturer)
        put("product_name", productName)
        put("build_model", buildModel)
    }

    companion object {
        fun fromJson(o: JSONObject): SpoofProfile = SpoofProfile(
            brand = o.optString("brand", ""),
            model = o.optString("model", ""),
            device = o.optString("device", ""),
            manufacturer = o.optString("manufacturer", ""),
            productName = o.optString("product_name", ""),
            buildModel = o.optString("build_model", o.optString("model", ""))
        )
    }
}

object SpoofData {

    /**
     * 唯一的伪造目标：OPPO A5。
     *
     * 注意 Xposed 侧的 hook 顺序：先改系统属性（getprop 能读到），
     * 再改 Build 里的静态字段（Java 层能读到），两边值必须一致，
     * 否则「getprop 说是 A、Java 说是 B」这种自相矛盾比不改还容易被检测。
     */
    val OPPO_A5 = SpoofProfile(
        brand = "OPPO",
        model = "PKD110",
        device = "OP5B4B",
        manufacturer = "OPPO",
        productName = "PKD110",
        buildModel = "PKD110"
    )

    // ── 配置读写 ────────────────────────────────────────────────

    /**
     * 伪造配置，落在 /data/adb/CoreGate/spoof.json。
     *
     * 为什么单独一个文件而不是塞进 config.json：
     *   config.json 是 service.sh 用 sed 逐键解析的，字段一多、值一带引号
     *   就容易踩到 BRE 的坑（cfg_bool 那个 sed 交替的 bug 就是这么来的）。
     *   独立文件由 App 独占读写，Xposed 侧只读不写，互不干扰。
     */
    data class State(
        /** 总开关；关闭时不 hook，系统原样 */
        var enabled: Boolean = false,
        /** 生效范围包名列表，默认为空（不额外过滤） */
        var scopePackages: List<String> = emptyList()
    ) {
        fun toJsonString(): String {
            val o = JSONObject()
            o.put("enabled", enabled)
            o.put("profile", OPPO_A5.toJson())
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
                    val arr = o.optJSONArray("scope_packages")
                    if (arr != null) {
                        val l = mutableListOf<String>()
                        for (i in 0 until arr.length()) {
                            val p = arr.optString(i, "").trim()
                            if (p.isNotEmpty()) l.add(p)
                        }
                        s.scopePackages = l
                    }
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