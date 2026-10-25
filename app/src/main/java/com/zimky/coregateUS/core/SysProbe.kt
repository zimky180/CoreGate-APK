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

    /**
     * 模块就位状态的进程内缓存（最近一次 probe 的结果）。
     *
     * 存在的理由：设置页「运行环境」那几行是同步渲染的，等不了 root 回调。
     * 之前这些行直接调 File(Paths.SERVICE).exists()，在 /data/adb 上恒为 false，
     * 于是模块明明正常也报「缺失」。现在只认 probe 拿回来的真实结果，
     * 没探测过就是 null，界面上显示「未知」而不是「缺失」。
     */
    @Volatile
    var svcCache: Boolean? = null
        private set

    /** service.sh 的字节数（诊断用），0 表示没读到。 */
    @Volatile
    var svcSizeCache: Int = 0
        private set

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
        /**
         * policy0 的真实合法档位表（kHz，升序）。
         *
         * 限频阈值可自定义后，滑杆必须吸附到内核真实认可的档位上 ——
         * 本机频率档位不等比（小核 23 档、大核 21 档、GPU 17 档），
         * 用百分比或 100MHz 步进算出来的值大概率不是合法档，
         * 写进去会被内核直接回弹到邻近档，界面显示与实际永远对不上。
         */
        var littleAvail: List<Long> = emptyList(),
        /** policy6 的真实合法档位表（kHz，升序）。 */
        var bigAvail: List<Long> = emptyList(),
        /** GPU 的真实合法档位表（Hz，升序）。 */
        var gpuAvail: List<Long> = emptyList(),
        var dbg: String = "",
        /** root 是否可用（探针命令里 id -u 是否为 0） */
        var rootOk: Boolean = false,
        /** root 校验到的身份（uid=0(root) 之类），便于诊断 */
        var rootId: String = "",
        /**
         * 模块是否就位：true 就位 / false 确实缺失 / null 无法判定。
         *
         * 由 probe 的同一次 root 调用顺带读出来（读 service.sh 的字节数），
         * 这样「核心状态」和「模块是否刷入」永远来自同一次执行，
         * 不会出现一个页面说在线、另一个页面说模块没刷入的自相矛盾。
         */
        var svcState: Boolean? = null
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
        // 两簇的完整合法档位表（限频滑杆吸附用，一次 root 顺带取回，不额外开 su）
        c.append("echo \"LAV:\"; cat /sys/devices/system/cpu/cpufreq/policy0/scaling_available_frequencies 2>/dev/null; ")
        c.append("echo \"BAV:\"; cat /sys/devices/system/cpu/cpufreq/policy6/scaling_available_frequencies 2>/dev/null; ")
        // GPU
        c.append("echo \"GCUR:\"; cat /sys/class/kgsl/kgsl-3d0/gpuclk 2>/dev/null; ")
        c.append("echo \"GMAX:\"; cat /sys/class/kgsl/kgsl-3d0/max_gpuclk 2>/dev/null; ")
        c.append("echo \"GPLV:\"; cat /sys/class/kgsl/kgsl-3d0/max_pwrlevel 2>/dev/null; ")
        c.append("echo \"GNUM:\"; cat /sys/class/kgsl/kgsl-3d0/num_pwrlevels 2>/dev/null; ")
        c.append("echo \"GDEVF:\"; cat /sys/class/kgsl/kgsl-3d0/devfreq/max_freq 2>/dev/null; ")
        // GPU 完整档位表（Hz）：
        //   主通道 gpu_available_frequencies（kgsl 标准节点，空格分隔，从高到低）；
        //   备用通道 devfreq/available_frequencies（部分机型只暴露这个）。
        // 两者都没有则留空，界面自动退化为「按 pwrlevel 档位」调节。
        c.append("echo \"GAV:\"; cat /sys/class/kgsl/kgsl-3d0/gpu_available_frequencies 2>/dev/null; ")
        c.append("echo \"GDAV:\"; cat /sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies 2>/dev/null; ")
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
        // ⚠️ 缺陷 4「守护进程永远显示未运行」的第二个根因（2026-10 实测确认）：
        // 只信 daemon.pid 是不够的 —— 该文件会被「一键清理残留」、系统清理、目录重建等
        // 各种操作删掉，而守护进程本身还活得好好的。此时 PID="" → 旧分支输出 "DEAD:"，
        // 后面连数字都没有，正则 (\d+) 也匹配不上，于是被判成「未运行」。
        // 修复思路：pid 文件只是「快速通道」，取不到就退化为 /proc 全量扫描——
        // 直接找 cmdline 里带 CoreGate/service.sh 的进程，这才是进程是否存活的唯一真相。
        c.append("echo -n \"DAEMON:\"; ")
        // Kotlin 字符串里裸 $ 会被当模板起始 → 编译期 Unresolved reference。
        // 用 D 变量持有真正的 $ 字符，拼进 shell 源码里。
        val D = "\u0024"
        c.append("PID=").append(D).append("(cat ").append(Paths.PID).append(" 2>/dev/null | tr -d ' \\n\\r\\t'); ")
        c.append("if [ -n \"").append(D).append("PID\" ] && kill -0 \"").append(D).append("PID\" 2>/dev/null; then ")
        c.append("echo -n \"ALIVE:").append(D).append("PID\"; else ")
        // pid 文件缺失/失效 → 找真正的守护进程。
        // 先 ps 筛（快，日志里的 27281 就是这么发现的），失败再遍历 /proc/cmdline 兜底。
        // ⚠️ 绝不能写成 SPID=$($(...) ...)：$(( 会被 shell 当成算术展开，语法直接炸。
        //    这里先落到 PS_OUT 变量，再单独 pipe 处理，彻底避开 $(( 歧义。
        c.append("SPID=\"\"; ")
        c.append("PS_OUT=\"").append(D).append("(ps -A -o PID,ARGS 2>/dev/null || ps -A 2>/dev/null)\"; ")
        c.append("SPID=\"").append(D).append("(echo \"").append(D).append("PS_OUT\" | grep 'CoreGate/service.sh' ")
            .append("| grep -v grep | head -n 1 | tr -s ' ' | cut -d' ' -f1)\"; ")
        c.append("if [ -z \"").append(D).append("SPID\" ]; then for d in /proc/[0-9]*; do ")
        c.append("cl=\"").append(D).append("(cat ").append(D).append("d/cmdline 2>/dev/null | tr '\\000' ' ')\"; ")
        c.append("case \"").append(D).append("cl\" in *CoreGate/service.sh*) SPID=")
            .append(D).append("{d#/proc/}; break;; esac; ")
        c.append("done; fi; ")
        c.append("if [ -n \"").append(D).append("SPID\" ] && kill -0 \"").append(D).append("SPID\" 2>/dev/null; then ")
        c.append("echo -n \"ALIVE:").append(D).append("SPID\"; else echo -n \"DEAD:0\"; fi; fi; ")
        // 上面用 echo -n 拼接成一行，最后补一个换行，避免和后面的 STATEF: 粘在一起
        c.append("echo \"\"; ")
        // 额外给一个冗余标记：把 pid 单独再输出一次，便于诊断区人类肉眼看
        c.append("echo \"DPID:\"; cat ").append(Paths.PID).append(" 2>/dev/null; ")
        c.append("echo \"STATEF:\"; cat ").append(Paths.STATE).append(" 2>/dev/null; ")
        // 模块就位检查（以前这里是 File(Paths.SERVICE).exists()，因为 /data/adb 是
        // 0700 root:root，普通 uid 恒判 false，直接导致「模块未刷入/损坏」的连锁误报）。
        // 现在改成同一次 root 调用里顺带判定，只输出三类标记，解析端一一对应。
        c.append("if [ -f ").append(Paths.SERVICE).append(" ]; then echo \"SVCYES:\"; ")
        c.append("elif [ -d ").append(Paths.MODULE_DIR).append(" ]; then echo \"SVCNODIR:\"; ")
        c.append("else echo \"SVCNONE:\"; fi; ")
        c.append("echo \"SVCSIZE:\"; cat ").append(Paths.SERVICE).append(" 2>/dev/null | wc -c; ")
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

            // ── 合法档位表（限频可自定义后，界面滑杆要吸附到这些真实档位）──
            s.littleAvail = parseFreqList(line(out, "LAV"))
            s.bigAvail = parseFreqList(line(out, "BAV"))
            // GPU 两条通道二选一：主通道为空才用 devfreq 备用通道
            var gpuList = parseFreqList(line(out, "GAV"))
            if (gpuList.isEmpty()) gpuList = parseFreqList(line(out, "GDAV"))
            // kgsl 的 gpu_available_frequencies 是从高到低输出的，统一转成升序，
            // 让上层「索引 0 = 最低档」这个约定对三类频率都成立。
            s.gpuAvail = gpuList.sorted()

            val stateb = line(out, "DAEMON")
            // DEAD:0 里的 0 是占位符（不是真 pid），要还原成「未知 = -1」
            val aliveFromMark = stateb != null && stateb.startsWith("ALIVE:")
            if (aliveFromMark) {
                s.daemonAlive = true
                s.daemonPid = num(stateb!!.substring(6)).toInt()
            } else if (stateb != null && stateb.startsWith("DEAD:")) {
                s.daemonAlive = false
                s.daemonPid = -1
            } else {
                // 兜底：标记后没取到内容（某些 shell 的 echo -n 行为不一致、或被吞字），
                // 退化为全文扫描。宁可多判一次，也不要再误报「未运行」。
                val m = Regex("(ALIVE|DEAD):(\\d+)").find(out)
                if (m != null) {
                    s.daemonAlive = m.groupValues[1] == "ALIVE"
                    val v = m.groupValues[2].toIntOrNull() ?: 0
                    s.daemonPid = if (s.daemonAlive) v else -1
                } else {
                    s.dbg += " · 未取到 DAEMON 标记"
                }
            }
            // pid 文件为空/失效时不再报错——守护进程可能通过 /proc 兜底找到了，
            // 只在「判定为未运行」且 pid 文件确实为空时给一句诊断。
            if (!s.daemonAlive) {
                val dpid = line(out, "DPID").orEmpty().trim()
                if (dpid.isEmpty()) s.dbg += " · pid 文件为空且未扫到进程"
            }

            val st = line(out, "STATEF")
            if (st != null) {
                s.stateRaw = st.trim()
                val seg = s.stateRaw.split(Regex("\\s+"))
                if (seg.isNotEmpty()) s.statePkg = seg[0]
            }

            // 模块就位：三个标记谁出现就认谁。
            // 都没出现（su 没输出/被截断）就保持 null = 未知，绝不写成「缺失」。
            val sizeRaw = line(out, "SVCSIZE").orEmpty().trim()
            val svcSize = sizeRaw.toIntOrNull() ?: 0
            s.svcState = when {
                out.contains("SVCYES:") && svcSize > 0 -> true
                out.contains("SVCYES:") -> false          // 文件在但 0 字节 = 真损坏
                out.contains("SVCNODIR:") -> false        // 目录在、service.sh 不在
                out.contains("SVCNONE:") -> false         // 连模块目录都没有
                else -> null
            }
            svcCache = s.svcState
            if (s.svcState == true) svcSizeCache = svcSize

            cb(s)
        }
    }

    /**
     * 取 "TAG:" 标记后面的值。
     *
     * ⚠️ 这里曾经只取「标记后到行尾」，是「核心全部离线」「诊断页报无 root」
     * 「守护进程显示未运行」这一整串误报的真正根因。
     *
     * 原因：探测命令绝大多数是 `echo "ON0:"; cat .../online` 这种写法，
     * 标记自己独占一行，**值在下一行**。旧实现 indexOf 之后 start 正好落在
     * 那个换行符上，end 又等于 start，于是永远返回空串：
     *   - ON1..7 取到 "" → online 判 false → 只剩 cpu0 在线，看着像全离线
     *   - ROOTU 取到 "" → rootOk 判 false → 诊断页说「无 root」
     * 只有 DAEMON 因为后来特意改成 echo -n 同行输出才侥幸正常。
     *
     * 现在两条路都支持：标记行后面**有内容就取内容，没内容就取下一行**。
     * 这样不管是同行式（`echo -n "TAG:$V"`）还是换行式（`echo "TAG:"; echo $V`）
     * 都能正确取值，也不用去改那三十多处 shell 拼接（改多了容易出新错）。
     */
    private fun line(out: String, tag: String): String? {
        val i = out.indexOf("$tag:")
        if (i < 0) return null
        val start = i + tag.length + 1
        var end = out.indexOf('\n', start)
        if (end < 0) end = out.length
        val same = out.substring(start, end).trim()
        if (same.isNotEmpty()) return same
        // 标记行是空的（值在下一行），继续往下取一行
        if (end >= out.length) return ""
        val nStart = end + 1
        var nEnd = out.indexOf('\n', nStart)
        if (nEnd < 0) nEnd = out.length
        return out.substring(nStart, nEnd).trim()
    }

    /**
     * 把 `scaling_available_frequencies` 这种「空格分隔的一串数字」解析成升序列表。
     *
     * 内核输出可能带前导/尾随空格，也可能一个值都没有（节点不存在 → line() 返回 null），
     * 因此这里对空串、非数字片段一律跳过，解析失败就返回空列表由上层降级处理。
     */
    private fun parseFreqList(raw: String?): List<Long> {
        if (raw.isNullOrBlank()) return emptyList()
        val out = mutableListOf<Long>()
        for (p in raw.trim().split(Regex("\\s+"))) {
            val v = p.toLongOrNull() ?: continue
            if (v > 0) out.add(v)
        }
        out.sort()
        return out
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