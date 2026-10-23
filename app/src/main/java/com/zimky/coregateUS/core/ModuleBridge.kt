package com.zimky.coregateUS.core

/**
 * ModuleBridge —— 通过 service.sh 的 CLI 入口控制模块。
 *
 * 为什么不再自己拼 sysfs 命令：service.sh 里的 lock_once / unlock_once
 * 已经处理了「模块已禁用跳过」「lock_cores=auto 走 detect_high」
 * 「手写名单归一化」「N 钳到 1~4」「cpu0 永不锁」这些规则，原生端照抄一遍
 * 只会抄漏。直接调它的入口，保证 APK 和 WebUI 行为逐字节一致。
 *
 * CLI 契约（service.sh 原文）：
 *   sh service.sh --lock-once [N]   → 输出 LOCK_ONCE_DONE: 名单=[...] 结果=[...] 在线=N
 *                                     或 LOCK_ONCE: 模块已禁用，跳过封锁
 *                                     或 LOCK_ONCE: 无可封锁核心
 *   sh service.sh --unlock-once     → 输出 UNLOCK_ONCE_DONE: 在线=N
 */
object ModuleBridge {

    /** 结果明细，供 UI 显示。 */
    data class Result(
        var ok: Boolean = false,
        var list: String = "",
        var applied: String = "",
        var online: Int = -1,
        var msg: String = ""
    )

    /**
     * 立即封锁一次。
     *
     * @param count 封锁核心数；0 或负数表示按 config.json 的 lock_cores 走
     */
    fun lockOnce(sh: RootShell, count: Int, cb: ((ok: Boolean, msg: String) -> Unit)?) {
        val cmd = buildCmd("--lock-once", count)
        sh.exec(cmd) { code, out, err ->
            val r = parse(out) ?: Result(ok = false, msg = firstLine(err, out, code))
            cb?.invoke(r.ok, r.msg)
        }
    }

    /** 立即恢复全部核心。 */
    fun unlockOnce(sh: RootShell, cb: ((ok: Boolean, msg: String) -> Unit)?) {
        val cmd = buildCmd("--unlock-once", 0)
        sh.exec(cmd) { code, out, err ->
            val r = parse(out) ?: Result(ok = false, msg = firstLine(err, out, code))
            cb?.invoke(r.ok, r.msg)
        }
    }

    private fun buildCmd(flag: String, count: Int): String {
        val sb = StringBuilder()
        sb.append("[ -f ").append(Paths.SERVICE).append(" ] || { echo 'NO_SERVICE'; exit 1; }; ")
        sb.append("sh ").append(Paths.SERVICE).append(' ').append(flag)
        if (flag == "--lock-once" && count > 0) {
            sb.append(' ').append(count)
        }
        return sb.toString()
    }

    /** 解析 service.sh 的输出，认不出来返回 null。 */
    fun parse(out: String?): Result? {
        if (out == null) return null
        val r = Result()

        if (out.contains("LOCK_ONCE_DONE")) {
            r.ok = true
            r.list = between(out, "名单=[", "]")
            r.applied = between(out, "结果=[", "]")
            val on = indexInt(out, "在线=")
            r.online = on
            val m = StringBuilder()
            m.append("已封锁 ").append(if (r.applied.isEmpty()) "—" else r.applied)
            if (r.list.isNotEmpty()) m.append("（名单 ").append(r.list).append("）")
            if (on >= 0) m.append(" · 在线核心 ").append(on)
            r.msg = m.toString()
            return r
        }

        if (out.contains("UNLOCK_ONCE_DONE")) {
            r.ok = true
            val on = indexInt(out, "在线=")
            r.online = on
            r.msg = if (on >= 0) "已恢复全部核心 · 在线 $on" else "已恢复全部核心"
            return r
        }

        if (out.contains("NO_SERVICE")) {
            r.ok = false
            r.msg = "未找到 service.sh，模块可能没装或路径不对"
            return r
        }

        if (out.contains("LOCK_ONCE: 模块已禁用")) {
            r.ok = false
            r.msg = "模块已禁用，已跳过封锁。请先打开上面那个总开关"
            return r
        }

        if (out.contains("LOCK_ONCE: 无可封锁核心")) {
            r.ok = false
            r.msg = "没有可封锁的核心（可能全部已离线）"
            return r
        }

        return null
    }

    private fun between(s: String, start: String, end: String): String {
        val a = s.indexOf(start)
        if (a < 0) return ""
        val b = s.indexOf(end, a + start.length)
        if (b < 0) return ""
        return s.substring(a + start.length, b).trim()
    }

    private fun indexInt(s: String, tag: String): Int {
        val a = s.indexOf(tag)
        if (a < 0) return -1
        var i = a + tag.length
        val sb = StringBuilder()
        while (i < s.length && s[i].isDigit()) {
            sb.append(s[i])
            i++
        }
        if (sb.isEmpty()) return -1
        return sb.toString().toIntOrNull() ?: -1
    }

    private fun firstLine(err: String?, out: String?, code: Int): String {
        val src = if (!err.isNullOrBlank()) err else out
        if (src.isNullOrBlank()) {
            return "命令无输出（exit=$code），请检查 root 授权"
        }
        for (ln in src.trim().split("\n")) {
            val t = ln.trim()
            if (t.isNotEmpty()) return t
        }
        return "exit=$code"
    }
}
