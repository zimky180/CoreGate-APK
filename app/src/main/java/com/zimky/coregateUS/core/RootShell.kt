package com.zimky.coregateUS.core

import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.ArrayDeque
import java.util.concurrent.Executors

/**
 * 诊断用：一行执行记录
 */
data class ShellRecord(
    val cmd: String,
    val exit: Int,
    val costMs: Long,
    val out: String,
    val err: String
)

/**
 * RootShell —— su 执行封装（语义对齐旧版 KsuBridge / WebUI 的 ksu.exec）。
 *
 * 设计要点：
 *  - 所有命令统一走 root shell（su），保证与模块 service.sh 权限一致；
 *  - 单线程串行队列，避免并发大量 root 请求被 ColorOS / KernelSU 安全机制
 *    误判为入侵而弹「检测到有恶意应用尝试破坏系统」；
 *  - 回调在主线程，方便 UI 直接更新；
 *  - 保留最近 N 条执行记录，诊断页可直接查看。
 */
object RootShell {

    private const val HISTORY_MAX = 40

    private val queue = Executors.newSingleThreadExecutor { r ->
        Thread(r, "CoreGate-RootShell").apply { isDaemon = true }
    }

    private val main = Handler(Looper.getMainLooper())
    private val history = ArrayDeque<ShellRecord>()

    /** 执行一条命令（自动包进 su -c '...'）。 */
    fun exec(cmd: String, cb: ((exit: Int, out: String, err: String) -> Unit)? = null) {
        execRaw(buildSuCmd(cmd), cmd, cb)
    }

    /** 执行已经拼好的完整命令（不再加 su 前缀）。 */
    fun execRaw(fullCmd: String, cb: ((exit: Int, out: String, err: String) -> Unit)? = null) {
        execRaw(fullCmd, fullCmd, cb)
    }

    private fun execRaw(
        fullCmd: String,
        showCmd: String,
        cb: ((Int, String, String) -> Unit)?
    ) {
        queue.execute {
            var exit = -1
            val out = StringBuilder()
            val err = StringBuilder()
            var p: Process? = null
            val t0 = System.currentTimeMillis()
            try {
                // 用 sh -c 包一层，兼容部分机型 su 不解析引号的情况
                p = Runtime.getRuntime().exec(arrayOf("su", "-c", fullCmd))

                // stdout / stderr 必须并发读，避免缓冲区堵塞死锁
                val po = PumpOut(p.inputStream, out)
                val pe = PumpOut(p.errorStream, err)
                po.start()
                pe.start()

                exit = p.waitFor()
                po.join(2000)
                pe.join(2000)
            } catch (e: Exception) {
                err.append(e.message ?: "exec failed")
                exit = -1
            } finally {
                runCatching { p?.destroy() }
            }

            val fExit = exit
            val fOut = out.toString()
            val fErr = err.toString()
            val cost = System.currentTimeMillis() - t0

            remember(ShellRecord(showCmd, fExit, cost, fOut, fErr))

            main.post { cb?.invoke(fExit, fOut, fErr) }
        }
    }

    private fun remember(r: ShellRecord) {
        synchronized(history) {
            history.addLast(r)
            while (history.size > HISTORY_MAX) history.removeFirst()
        }
    }

    /** 返回执行记录快照（新 → 旧）。 */
    fun history(): List<ShellRecord> = synchronized(history) {
        history.toList().asReversed()
    }

    /** 把命令安全地塞进 su -c '...'。 */
    private fun buildSuCmd(cmd: String): String {
        val safe = cmd.replace("'", "'\\''")
        return "sh -c '$safe'"
    }

    private class PumpOut(
        private val input: InputStream,
        private val sb: StringBuilder
    ) : Thread() {
        init {
            isDaemon = true
        }

        override fun run() {
            var br: BufferedReader? = null
            try {
                br = BufferedReader(InputStreamReader(input, "UTF-8"))
                while (true) {
                    val line = br.readLine() ?: break
                    sb.append(line).append('\n')
                }
            } catch (_: Exception) {
            } finally {
                runCatching { br?.close() }
            }
        }
    }
}
