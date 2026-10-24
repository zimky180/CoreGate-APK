package com.zimky.coregateUS.core

/**
 * 冻结分组的界面侧数据模型与解析。
 *
 * 数据来源两处，职责严格分开：
 *   1) 分组定义（有哪些组、每组含哪些包）—— 硬编码在下面的 [GROUPS]，
 *      必须与 service.sh 里的 FREEZE_TABLE 逐字一致，否则界面勾了引擎不认。
 *   2) 运行时真实状态（到底冻上没冻）—— 全部来自 `service.sh --freeze-status`，
 *      界面绝不自作推断。这是「让用户一眼看出是否真冻结」的唯一可信来源。
 *
 * 为什么组定义要硬编码：APK 与模块是分别安装的两个东西，APK 无法在执行时
 * 解析 service.sh 的 shell 变量。所以这里必须有一份镜像表。改动包列表时
 * 两边都要改 —— 这是已知代价，比让 APK 去 grep 脚本稳得多。
 */
object FreezeData {

    /**
     * 一个冻结分组。
     *
     * @param key     组键，对应 config.json 里的 freeze_<key> 开关字段
     * @param name    显示名
     * @param pkgs    该组包含的包名（顺序与 service.sh 保持一致）
     * @param risky   是否属于高风险组（需要二次确认弹窗才能开启）
     * @param desc    组说明，显示在二级页卡片下方
     */
    data class Group(
        val key: String,
        val name: String,
        val pkgs: List<String>,
        val risky: Boolean = false,
        val desc: String = ""
    )

    /** 与 service.sh 的 FREEZE_TABLE 逐字对应。改一处必须同步改另一处。 */
    val GROUPS: List<Group> = listOf(
        Group(
            key = "log",
            name = "运行日志收集",
            pkgs = listOf(
                "com.oplus.logkit",
                "com.oplus.onetrace",
                "com.android.traceur"
            ),
            desc = "系统日志与链路追踪组件。冻结后不再持续写日志，日常使用无感。"
        ),
        Group(
            key = "media",
            name = "影音附加组件",
            pkgs = listOf(
                "com.coloros.karaoke",
                "com.oplus.audio.effectcenter",
                "com.oplus.mediaturbo",
                "com.oplus.smartmediacontroller"
            ),
            desc = "K歌、音效中心、媒体加速与智能媒体控制。冻结后耳机音效与媒体悬浮控制可能失效。"
        ),
        Group(
            key = "cloud",
            name = "云端备份同步",
            pkgs = listOf(
                "com.heytap.cloud",
                "com.android.sharedstoragebackup",
                "com.android.wallpaperbackup"
            ),
            desc = "云服务与本地备份。冻结后不会自动同步与备份，需要时请先解冻。"
        ),
        Group(
            key = "diag",
            name = "系统行为诊断",
            pkgs = listOf(
                "com.oplus.appsense",
                "com.oplus.motionsense",
                "com.oplus.audiomonitor"
            ),
            desc = "应用行为感知、运动感知与音频监听。冻结后智能场景识别可能不生效。"
        ),
        Group(
            key = "ad",
            name = "广告推送聚合",
            pkgs = listOf(
                "com.heytap.mcs",
                "com.heytap.openid",
                "com.heytap.tas"
            ),
            desc = "推送通道、设备标识与统计。冻结后系统级推送与广告追踪停止，部分应用推送可能受影响。"
        ),
        Group(
            key = "self",
            name = "作者自冻清单",
            pkgs = listOf(
                "com.heytap.colorfulengine", "com.heytap.openid", "com.heytap.pictorial",
                "com.heytap.speechassist", "com.heytap.tas", "com.microsoft.appmanager",
                "com.newcall", "com.oplus.acc.gac", "com.oplus.aicall",
                "com.oplus.aimemory", "com.oplus.aiwidgets", "com.oplus.aiwriter",
                "com.oplus.apprecover", "com.oplus.cardigitalkey", "com.oplus.cast",
                "com.oplus.cellularqoe", "com.oplus.customize.coreapp", "com.oplus.deepthinker",
                "com.oplus.encryption", "com.oplus.keyguard.clock.base",
                "com.oplus.keyguard.clock.gallery", "com.oplus.keyguard.clock.graffiti",
                "com.oplus.keyguard.clock.magazine", "com.oplus.keyguard.personality.clocks",
                "com.oplus.keyguard.style.widgets", "com.oplus.logkit", "com.oplus.matrix",
                "com.oplus.olc", "com.oplus.omoji", "com.oplus.onetrace",
                "com.oplus.owork", "com.oplus.remotecontrol", "com.oplus.travelengine",
                "com.coloros.karaoke", "com.coloros.ocrscanner", "com.coloros.remoteguardservice",
                "com.coloros.scenemode", "com.coloros.sceneservice", "com.coloros.securepay",
                "com.coloros.securityguard", "com.coloros.sharescreen", "com.coloros.systemclone",
                "com.coloros.translate.engine", "com.coloros.video", "com.fido.fido2client",
                "com.google.android.gms", "com.google.android.gsf",
                "com.google.android.marvin.talkback", "com.google.android.projection.gearhead",
                "com.android.devicelockcontroller", "com.coloros.codebook",
                "com.coloros.colordirectservice"
            ),
            risky = true,
            desc = "作者在 ColorOS 16.0.1.306 上实测确认可正常开机后原样收录的 52 个包。" +
                "其他系统版本不保证能正常进系统。"
        )
    )

    /**
     * `--freeze-status` 的解析结果。
     *
     * @param applied 本模块记录在案的冻结项（包名 → "suspend"/"disable"）
     * @param actual  系统当前真实的冻结/停用项（包名 → 方式），含非本模块所为
     * @param groups  各组开关当前值（组键 → 是否开启）
     */
    data class Snapshot(
        val applied: Map<String, String> = emptyMap(),
        val actual: Map<String, String> = emptyMap(),
        val groups: Map<String, Boolean> = emptyMap()
    ) {
        /** 本模块冻结中、且系统里确实已处于冻结态的数量（真正生效的证据） */
        val effectiveCount: Int
            get() = applied.keys.count { actual.containsKey(it) }

        /** 本模块记录了、但系统里没体现出来 —— 说明引擎没生效，必须在界面上暴露 */
        val staleList: List<String>
            get() = applied.keys.filterNot { actual.containsKey(it) }

        /**
         * 系统当前被停用的应用总数。
         *
         * 数据来自 `pm list packages -d`，**包含用户自己在系统设置里手动停用的应用**，
         * 不等于本模块冻结的数量 —— 只适合作背景参考，不要直接当「已冻结 N 个」展示。
         */
        val systemDisabledCount: Int by lazy { actual.size }

        /**
         * 落在本模块作用域内的冻结包：`actual` 里出现、且属于 [GROUPS] 任一组的包。
         *
         * 用包名清单匹配而不是靠 freeze.state 记录 —— 即使记录被删了
         * （freeze_restore_all 末尾会 rm 掉 freeze.state），只要组内包在系统里
         * 确实处于停用/挂起状态，这里依然数得出来。
         */
        val inScopeFrozen: Set<String> by lazy {
            val scope = HashSet<String>()
            for (g in GROUPS) scope.addAll(g.pkgs)
            actual.keys.filterTo(HashSet()) { it in scope }
        }

        /**
         * 界面上真正该给用户看的「已冻结」数字：本模块作用域内、确实处于冻结态的条目数。
         *
         * 它既不像 [effectiveCount] 那样依赖 freeze.state（记录丢了就归零，
         * 主页因此误报「没有冻结」），也不像 [systemDisabledCount] 那样
         * 把用户手动停用的无关包也算进来。
         */
        val scopedFrozenCount: Int by lazy { inScopeFrozen.size }

        fun isGroupOn(key: String): Boolean = groups[key] == true
    }

    /**
     * 解析 `service.sh --freeze-status` 的文本输出。
     *
     * 格式（三段，用 [段名] 分隔）：
     *   [applied]
     *   包名<TAB>方式
     *   [actual]
     *   包名<TAB>方式
     *   [groups]
     *   组键<TAB>on|off
     *
     * 容错：字段缺失、行格式不对、段缺失，一律跳过而不抛异常 ——
     * 状态面板宁可少显示一行，也不能因为一行异常导致整页崩掉。
     */
    fun parse(raw: String?): Snapshot {
        if (raw.isNullOrBlank()) return Snapshot()
        val applied = LinkedHashMap<String, String>()
        val actual = LinkedHashMap<String, String>()
        val groups = LinkedHashMap<String, Boolean>()
        var section = ""

        for (line in raw.lineSequence()) {
            val t = line.trim()
            if (t.isEmpty()) continue
            when (t) {
                "[applied]" -> { section = "applied"; continue }
                "[actual]" -> { section = "actual"; continue }
                "[groups]" -> { section = "groups"; continue }
            }
            val parts = t.split('\t')
            when (section) {
                "applied", "actual" -> {
                    if (parts.size >= 2 && parts[0].isNotEmpty()) {
                        val mode = parts[1].trim()
                        val target = if (section == "applied") applied else actual
                        target[parts[0].trim()] = if (mode.isEmpty()) "disable" else mode
                    }
                }
                "groups" -> {
                    if (parts.size >= 2) {
                        groups[parts[0].trim()] = parts[1].trim() == "on"
                    }
                }
            }
        }
        return Snapshot(applied, actual, groups)
    }

    /** 把某组内部的包按「系统里是否真的冻结了」分成两拨，供界面逐包打勾显示 */
    fun splitByActual(group: Group, actual: Map<String, String>): Pair<List<String>, List<String>> {
        val frozen = group.pkgs.filter { actual.containsKey(it) }
        val normal = group.pkgs.filterNot { actual.containsKey(it) }
        return frozen to normal
    }
}
