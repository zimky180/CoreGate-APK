package com.zimky.coregateUS.core;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * RootShell —— su 执行封装（原生版，语义对齐旧版 KsuBridge / WebUI 的 ksu.exec）。
 *
 * 设计要点：
 *  - 所有命令统一走 root shell（su），保证与模块 service.sh 权限一致；
 *  - 单线程串行队列，避免并发大量 root 请求被 ColorOS / KernelSU 安全机制
 *    误判为入侵而弹「检测到有恶意应用尝试破坏系统」；
 *  - 回调在主线程，方便 UI 直接更新；
 *  - 保留最近 N 条执行记录，诊断页可直接查看。
 */
public final class RootShell {

    public interface Callback {
        /** 在 UI 线程回调。exitCode 为 -1 表示执行异常。 */
        void onResult(int exitCode, String stdout, String stderr);
    }

    /** 诊断用：一行执行记录 */
    public static final class Record {
        public final String cmd;
        public final int exit;
        public final long costMs;
        public final String out;
        public final String err;

        Record(String cmd, int exit, long costMs, String out, String err) {
            this.cmd = cmd;
            this.exit = exit;
            this.costMs = costMs;
            this.out = out;
            this.err = err;
        }
    }

    private static final int HISTORY_MAX = 40;

    private static final RootShell INSTANCE = new RootShell();

    public static RootShell get() {
        return INSTANCE;
    }

    private final ExecutorService queue = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CoreGate-RootShell");
        t.setDaemon(true);
        return t;
    });

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayDeque<Record> history = new ArrayDeque<>();

    private RootShell() {
    }

    /** 执行一条命令（自动包进 su -c '...'）。 */
    public void exec(String cmd, Callback cb) {
        execRaw(buildSuCmd(cmd), cmd, cb);
    }

    /** 执行已经拼好的完整命令（不再加 su 前缀）。 */
    public void execRaw(String fullCmd, Callback cb) {
        execRaw(fullCmd, fullCmd, cb);
    }

    private void execRaw(final String fullCmd, final String showCmd, final Callback cb) {
        queue.execute(() -> {
            int exit = -1;
            StringBuilder out = new StringBuilder();
            StringBuilder err = new StringBuilder();
            Process p = null;
            long t0 = System.currentTimeMillis();
            try {
                // 用 sh -c 包一层，兼容部分机型 su 不解析引号的情况
                p = Runtime.getRuntime().exec(new String[]{"su", "-c", fullCmd});

                // stdout / stderr 必须并发读，避免缓冲区堵塞死锁
                PumpOut po = new PumpOut(p.getInputStream(), out);
                PumpOut pe = new PumpOut(p.getErrorStream(), err);
                po.start();
                pe.start();

                exit = p.waitFor();
                po.join(2000);
                pe.join(2000);
            } catch (Exception e) {
                err.append(e.getMessage() == null ? "exec failed" : e.getMessage());
                exit = -1;
            } finally {
                if (p != null) {
                    try { p.destroy(); } catch (Exception ignored) { }
                }
            }

            final int fExit = exit;
            final String fOut = out.toString();
            final String fErr = err.toString();
            final long cost = System.currentTimeMillis() - t0;

            remember(new Record(showCmd, fExit, cost, fOut, fErr));

            main.post(() -> {
                if (cb != null) cb.onResult(fExit, fOut, fErr);
            });
        });
    }

    private void remember(Record r) {
        synchronized (history) {
            history.addLast(r);
            while (history.size() > HISTORY_MAX) history.removeFirst();
        }
    }

    /** 返回执行记录快照（新 → 旧）。 */
    public java.util.List<Record> history() {
        synchronized (history) {
            java.util.List<Record> list = new java.util.ArrayList<>(history);
            java.util.Collections.reverse(list);
            return list;
        }
    }

    /** 把命令安全地塞进 su -c '...'。 */
    private static String buildSuCmd(String cmd) {
        String safe = cmd.replace("'", "'\\''");
        return "sh -c '" + safe + "'";
    }

    // ---------------------------------------------------------------

    private static final class PumpOut extends Thread {
        private final InputStream in;
        private final StringBuilder sb;

        PumpOut(InputStream in, StringBuilder sb) {
            this.in = in;
            this.sb = sb;
            setDaemon(true);
        }

        @Override
        public void run() {
            BufferedReader br = null;
            try {
                br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            } catch (IOException ignored) {
            } finally {
                if (br != null) {
                    try { br.close(); } catch (IOException ignored) { }
                }
            }
        }
    }
}
