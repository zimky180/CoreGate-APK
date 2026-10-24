package com.zimky.coregateUS.core

import java.util.Locale

/**
 * SysProbe —— 系统状态探测。
 *
 * 全部走一次 root shell 批量读，拿回一份带标记的文本再在 Kotlin 侧解析，
 * 目的是把 root 调用次数压到最少（每次 root 都是一次 su 进程，狂刷会被
 * 系统安全机制误判成入侵，v2.0.6 踩过这个坑）。
 */
object SysProbe {

    /** 单个核心快照。 */
    data class Core(
        var id: Int = 0,
        var online: Boolean = false,
        /** kHz */
        var curFreq: Long = 0,
        /** kHz（cpuinfo_max_freq，厂商标称） */
        var maxFreq: Long = 0,
        /** kHz（scaling_available_frequencies 最大值，真实合法上限） */
        var availMax: Long = 0,
        /** "little" / "big" */
        var cluster: String = "little"
    )

    /** 一整次探测的结果。 */
    data class Snapshot(
        var cores: List<Core> = emptyList(),
        var onlineCount: Int = 0,
        /** Hz */
        var gpuCur: Long = 0,
        /** Hz */
        var gpuMax: Long = 0,
        var gpuPwrLevel: Int = -1,
        var gpuNumLevels: Int = -1,
        /** state 文件第一段（前台包名） */
        var statePkg: String = "",
        var stateRaw: String = "",
        var daemonAlive: Boolean = false,
        var daemonPid: Int = -1,
        /** policy0 scaling_max_freq */
        var littleMaxCur: Long = 0,
        /** policy6 scaling_max_freq */
        var bigMaxCur: Long = 0,
        var dbg: String = "",
        /** root 是否可用（探针命令里 id -u 是否为 0） */
        var rootOk: Boolean = false,
        /** root 校验到的身份（uid=0(root) 之类），便于诊断 */
        var rootId: String = ""
    )

    /** 一次 root 拿全量状态。 */
    fun probe(sh: RootShell, cb: (Snapshot) -> Unit) {
        val c = StringBuilder()
        // root 校验：id -u 是否为 0。放在最前面，任何一条后续失败都能先确认 root 本身是否可用。
        c.append("echo \"ROOTU:\"; id -u 2>/dev/null; ")
        c.append("echo \"ROOTID:\"; id 2>/dev/null; ")
        // 核心在线状态 0..7
        for (i in 0..7) {
            c.append("echo \"ON").append(i).append(":\"; cat /sys/devices/system/cpu/cpu")
                .append(i).append("/online 2>/dev/null; ")
        }
        // 每个核心当前频率与上限
        for (i in 0..7) {
            val base = "/sys/devices/system/cpu/cpu$i/cpufreq/"
            c.append("echo \"CU").append(i).append(":\"; cat ").append(base)
                .append("scaling_cur_freq 2>/dev/null; ")
            c.append("echo \"MX").append(i).append(":\"; cat ").append(base)
                .append("cpuinfo_max_freq 2>/dev/null; ")
            c.append("echo \"AV").append(i).append(":\"; cat ").append(base)
                .append("scaling_available_frequencies 2>/dev/null; ")
        }
        // 簇上限
        c.append("echo \"LMAX:\"; cat /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq 2>/dev/null; ")
        c.append("echo \"BMAX:\"; cat /sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq 2>/dev/null; ")
        // GPU
        c.append("echo \"GCUR:\"; cat /sys/class/kgsl/kgsl-3d0/gpuclk 2>/dev/null; ")
        c.append("echo \"GMAX:\"; cat /sys/class/kgsl/kgsl-3d0/max_gpuclk 2>/dev/null; ")
        c.append("echo \"GPLV:\"; cat /sys/class/kgsl/kgsl-3d0/max_pwrlevel 2>/dev/null; ")
        c.append("echo \"GNUM:\"; cat /sys/class/kgsl/kgsl-3d0/num_pwrlevels 2>/dev/null; ")
        c.append("echo \"GDEVF:\"; cat /sys/class/kgsl/kgsl-3d0/devfreq/max_freq 2>/dev/null; ")
        // daemon 与 state
        //
        // ⚠️ 这里踩过一个很隐蔽的坑（缺陷 2「守护进程永远显示未运行」的根因）：
        // 原来写的是 `echo "STATEB:";` 单独占一行，然后 if 里再 `echo "ALIVE:$PID"`，
        // 于是 ALIVE:/DEAD: 落在 STATEB: 的「下一行」。
        // 但 line() 的语义是「取标记后到行尾」，读到的是空串，
        // startsWith("ALIVE:") 永远不成立 → daemonAlive 一直是构造默认值 false。
        // 现在改成把结果「同行」输出在 DAEMON: 标记后面，line() 就能正确取到。
        //
        // ⚠️ 关键细节：标记本身必须用 `echo -n`（不换行），否则 DAEMON: 自己先换了一行，
        // ALIVE:/DEAD: 又跑到下一行，line() 依旧取到空串 —— 这个坑踩过第二次。
        // 但 Android 的 sh 是 mksh，`echo -n` 未必真能吞掉换行，所以在解析端
        // 还保留了一条「全文正则兜底」，两条路任何一条通都能拿到正确结果。
        c.append("echo -n \"DAEMON:\"; ")
        // 注意：shell 变量 $PID 必须转义成 ${'$'}PID，否则 Kotlin 会把它当成字符串模板，
        // 报 Unresolved reference 'PID'。
        c.append("PID=").append("${'$'}").append("(cat ").append(Paths.PID).append(" 2>/dev/null); ")
        c.append("if [ -n \"${'$'}PID\" ] && kill -0 \"${'$'}PID\" 2>/dev/null; then echo -n \"ALIVE:${'$'}PID\"; else echo -n \"DEAD:\"; echo -n \"${'$'}PID\"; fi; ")
        // 上面用 echo -n 拼接成一行，最后补一个换行，避免和后面的 STATEF: 粘在一起
        c.append("echo \"\"; ")
        // 额外给一个冗余标记：把 pid 单独再输出一次，便于诊断区人类肉眼看
        c.append("echo \"DPID:\"; cat ").append(Paths.PID).append(" 2>/dev/null; ")
        c.append("echo \"STATEF:\"; cat ").append(Paths.STATE).append(" 2>/dev/null; ")
        c.append("echo \"ENDPROBE:\"")

        sh.exec(c.toString()) { code, out, _ ->
            val s = Snapshot()
            s.dbg = "exit=$code"
            if (out.isEmpty()) {
                s.dbg += " · 无输出（root 未授权？）"
                cb(s)
                return@exec
            }

            // root 校验：id -u 返回 0 说明 su 真正提权成功（而不是授权被拒/命令没跑到）
            val u = line(out, "ROOTU")
            s.rootOk = u != null && u.trim() == "0"
            s.rootId = line(out, "ROOTID").orEmpty().trim()
            if (!s.rootOk) {
                s.dbg += " · id -u=" + (u?.trim().orEmpty().ifEmpty { "空" })
            }

            val cores = mutableListOf<Core>()
            for (i in 0..7) {
                val core = Core(id = i)
                val onRaw = line(out, "ON$i")
                // cpu0 没有 online 节点，视为在线
                core.online = if (onRaw.isNullOrEmpty()) i == 0 else onRaw.trim() == "1"
                core.curFreq = num(line(out, "CU$i"))
                core.maxFreq = num(line(out, "MX$i"))

                val avail = line(out, "AV$i")
                if (!avail.isNullOrEmpty()) {
                    var best = 0L
                    for (p in avail.trim().split(Regex("\\s+"))) {
                        val v = num(p)
                        if (v > best) best = v
                    }
                    core.availMax = best
                }

                core.cluster = if (i >= 6) "big" else "little"

                if (core.online) s.onlineCount++
                cores.add(core)
            }
            s.cores = cores

            s.littleMaxCur = num(line(out, "LMAX"))
            s.bigMaxCur = num(line(out, "BMAX"))

            s.gpuCur = num(line(out, "GCUR"))
            s.gpuMax = num(line(out, "GMAX"))
            val plv = line(out, "GPLV")
            if (!plv.isNullOrEmpty()) s.gpuPwrLevel = num(plv).toInt()
            val gnum = line(out, "GNUM")
            if (!gnum.isNullOrEmpty()) s.gpuNumLevels = num(gnum).toInt()

            val stateb = line(out, "DAEMON")
            if (stateb != null && stateb.startsWith("ALIVE:")) {
                s.daemonAlive = true
                s.daemonPid = num(stateb.substring(6)).toInt()
            } else if (stateb != null && stateb.startsWith("DEAD:")) {
                s.daemonAlive = false
                s.daemonPid = num(stateb.substring(5)).toInt()
            } else {
                // 兜底：标记后没取到内容（某些 shell 的 echo -n 行为不一致、或被吞字），
                // 退化为全文扫描。宁可多判一次，也不要再误报「未运行」。
                val m = Regex("(ALIVE|DEAD):(\\d+)").find(out)
                if (m != null) {
                    s.daemonAlive = m.groupValues[1] == "ALIVE"
                    s.daemonPid = m.groupValues[2].toIntOrNull() ?: -1
                } else {
                    s.dbg += " · 未取到 DAEMON 标记"
                }
            }
            // DPID 只用于诊断展示，正式判定以上面的结果为准
            val dpid = line(out, "DPID")
            if (dpid.isNullOrEmpty()) s.dbg += " · pid 文件为空"

            val st = line(out, "STATEF")
            if (st != null) {
                s.stateRaw = st.trim()
                val seg = s.stateRaw.split(Regex("\\s+"))
                if (seg.isNotEmpty()) s.statePkg = seg[0]
            }

            cb(s)
        }
    }

    /** 取 "TAG:" 标记后同一行剩余内容。 */
    private fun line(out: String, tag: String): String? {
        val i = out.indexOf("$tag:")
        if (i < 0) return null
        val start = i + tag.length + 1
        var end = out.indexOf('\n', start)
        if (end < 0) end = out.length
        return out.substring(start, end).trim()
    }

    private fun num(s: String?): Long {
        if (s == null) return 0
        var t = s.trim()
        if (t.isEmpty()) return 0
        // 可能带多个空格分隔的多个数字，取第一个
        val sp = t.indexOf(' ')
        if (sp > 0) t = t.substring(0, sp)
        return t.toLongOrNull() ?: 0L
    }

    /** kHz → "2.27 GHz" 风格。 */
    fun fmtMhz(khz: Long): String {
        if (khz <= 0) return "—"
        val mhz = khz / 1000.0
        return if (mhz >= 1000) String.format(Locale.US, "%.2f GHz", mhz / 1000.0)
        else String.format(Locale.US, "%.0f MHz", mhz)
    }

    /** Hz → MHz 字符串。 */
    fun fmtGpu(hz: Long): String {
        if (hz <= 0) return "—"
        return String.format(Locale.US, "%.0f MHz", hz / 1000000.0)
    }
}