package com.zimky.coregateUS.core

/**
 * 墓碑实况解析。
 *
 * 数据来源是 `service.sh --tomb-status`，输出三段，顺序固定：
 *
 * ```
 * [on]
 * true
 * [engine]
 * cgroup_v2
 * [item]
 * com.foo.bar	12345	true
 * com.baz.qux	67890	false
 * ```
 *
 * 三个字段含义（与 service.sh 的 tomb_status_report 严格对齐）：
 *  - 第一段 on：墓碑总开关是否打开
 *  - 第二段 engine：实际生效的冻结通道（cgroup_v2 / cgroup_v1 / sigstop）
 *  - 第三段 item：每行「包名<TAB>PID<TAB>状态」，状态三态：
 *      true  = 回读 cgroup.freeze 为 1，**确实冻住了**
 *      false = 进程还在，但没冻住（写入失败或被系统放行）
 *      gone  = 那个 pid 已经退出，记录即将被清理
 *
 * 为什么状态要分三态而不是「冻了/没冻」两态：
 *   用户真正想知道的是「我以为冻住了，是真冻住了吗」。把 false 和 gone 混成
 *   「没冻住」会让人无法判断是自己设置有问题、还是进程本来就已经结束了。
 */
object TombData {

    /** 一个被墓碑管着的进程。 */
    data class Item(
        val pkg: String,
        val pid: Int,
        /** true=真冻着，false=没冻住，gone=进程已退出 */
        val state: String
    ) {
        val frozen: Boolean get() = state == "true"
        val gone: Boolean get() = state == "gone"
    }

    data class Snapshot(
        /** 总开关；null = 没读到（脚本没跑起来 / 未授权） */
        val on: Boolean? = null,
        /** 实际通道，空字符串表示没读到 */
        val engine: String = "",
        val items: List<Item> = emptyList()
    ) {
        /** 真冻住的进程数 */
        val frozenCount: Int get() = items.count { it.frozen }

        /** 冻住的包数（一个包可能有多个进程） */
        val frozenPackages: Int get() = items.filter { it.frozen }.map { it.pkg }.distinct().size

        /** 是否处于「开关打开且至少真冻住一个进程」——这才是真的在生效 */
        val effective: Boolean get() = on == true && frozenCount > 0

        /** 按包聚合，供界面按包展示 */
        fun grouped(): List<Pair<String, List<Item>>> =
            items.groupBy { it.pkg }.map { (k, v) -> k to v }
    }

    /**
     * 解析 `--tomb-status` 的原始输出。
     *
     * 容错策略：任何一段缺失都返回已解析到的部分，不抛异常、不伪造数据。
     * 界面上「读不到」必须显示成「还没读出来」，不能显示成「没在生效」——
     * 这两件事对用户的意义完全不同。
     */
    fun parse(raw: String): Snapshot {
        var on: Boolean? = null
        var engine = ""
        val items = mutableListOf<Item>()
        var section = ""

        for (line in raw.lineSequence()) {
            val s = line.trim()
            if (s.isEmpty()) continue
            when (s) {
                "[on]" -> { section = "on"; continue }
                "[engine]" -> { section = "engine"; continue }
                "[item]" -> { section = "item"; continue }
            }
            when (section) {
                "on" -> {
                    on = when (s) {
                        "true" -> true
                        "false" -> false
                        else -> null
                    }
                    section = ""
                }
                "engine" -> {
                    engine = s
                    section = ""
                }
                "item" -> {
                    val p = s.split('\t')
                    if (p.size >= 3) {
                        val pid = p[1].trim().toIntOrNull()
                        if (pid != null) {
                            items.add(Item(p[0].trim(), pid, p[2].trim()))
                        }
                    }
                }
            }
        }
        return Snapshot(on, engine, items)
    }
}
