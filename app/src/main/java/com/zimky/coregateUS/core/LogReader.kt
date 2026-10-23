package com.zimky.coregateUS.core

/**
 * LogReader —— 读模块日志，多路径回退。
 *
 * 与 WebUI 的 refreshLog 顺序一致：
 *   /data/adb/modules/CoreGate/log/coregate.log
 *   /data/adb/modules_update/CoreGate/log/coregate.log
 *   /data/adb/CoreGate/log/coregate.log
 *   /data/adb/CoreGate/coregate.log   （旧路径）
 */
object LogReader {

    val PATHS = arrayOf(
        "/data/adb/modules/CoreGate/log/coregate.log",
        "/data/adb/modules_update/CoreGate/log/coregate.log",
        "/data/adb/CoreGate/log/coregate.log",
        "/data/adb/CoreGate/coregate.log"
    )

    private const val TAG_FROM = "###FROM:"
    private const val TAG_ENDLOG = "###ENDLOG"

    /**
     * tail 行数，够看即可，避免一次拉几 MB。
     *
     * @param cb (文本, 来源路径, 是否找到)
     */
    fun tail(sh: RootShell, lines: Int = 160, cb: ((text: String, from: String, found: Boolean) -> Unit)?) {
        val c = StringBuilder()
        val n = if (lines <= 0) 160 else lines
        for (p in PATHS) {
            c.append("if [ -f ").append(p).append(" ]; then ")
                .append("echo \"").append(TAG_FROM).append(p).append("\"; ")
                .append("tail -n ").append(n).append(' ').append(p).append("; ")
                .append("echo \"").append(TAG_ENDLOG).append("\"; ")
                .append("fi; ")
        }
        c.append("echo \"###ENDALL\"")

        sh.exec(c.toString()) { _, out, _ ->
            if (!out.contains(TAG_FROM)) {
                cb?.invoke("", "", false)
                return@exec
            }
            val a = out.indexOf(TAG_FROM)
            var b = out.indexOf(TAG_ENDLOG, a)
            if (b < 0) b = out.length

            var nl = out.indexOf('\n', a)
            if (nl < 0) nl = a
            val path = out.substring(a + TAG_FROM.length, nl).trim()

            val start = minOf(nl + 1, out.length)
            val end = minOf(b, out.length)
            val body = if (end > start) out.substring(start, end) else ""
            cb?.invoke(body.trim(), path, true)
        }
    }

    /** 清空所有日志文件。 */
    fun clear(sh: RootShell, cb: ((Int, String, String) -> Unit)? = null) {
        val c = StringBuilder()
        for (p in PATHS) {
            c.append(": > ").append(p).append(" 2>/dev/null; ")
        }
        c.append("echo ok")
        sh.exec(c.toString(), cb)
    }
}
