package com.zimky.coregate.core;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * RootShell —— su 执行封装。
 *
 * 设计要点（对齐 WebUI 的 ksu.exec 语义）：
 *  - 所有命令统一走 root shell（su），保证与模块 service.sh 权限一致；
 *  - 单线程串行队列（_shChain 同源思想），避免并发大量 root 请求被
 *    ColorOS / KernelSU 安全机制误判为入侵而弹「检测到有恶意应用尝试破坏系统」；
 *  - 回调在主线程，方便 UI 直接更新。
 *
 * 用法：
 *   RootShell.get().exec("cat /sys/...", result -> { ... });
 */
public final class RootShell {

    public interface Callback {
        /** 在 UI 线程回调。exitCode 为 -1 表示执行异常。 */
        void onResult(int exitCode, String stdout, String stderr);
    }

    private static final RootShell INSTANCE = new RootShell();

    public static RootShell get() {
        return INSTANCE;
    }

    /** 单线程串行执行器：核心防弹窗机制 */
    private final ExecutorService queue = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CoreGate-RootShell");
        t.setDaemon(true);
        return t;
    });

    private final Handler main = new Handler(Looper.getMainLooper());

    private RootShell() {
    }

    /**
     * 执行一条命令（自动包进 su -c '...'）。
     *
     * @param cmd 原始命令（可含空格、引号，内部会做安全转义）
     */
    public void exec(final String cmd, final Callback cb) {
        execRaw(buildSuCmd(cmd), cb);
    }

    /** 执行已经拼好的完整命令（不再加 su 前缀）。 */
    public void execRaw(final String fullCmd, final Callback cb) {
        queue.execute(() -> {
            int exit = -1;
            StringBuilder out = new StringBuilder();
            StringBuilder err = new StringBuilder();
            Process p = null;
            try {
                p = Runtime.getRuntime().exec(new String[]{"su", "-c", fullCmd});

                // 读 stdout / stderr（必须并发读，避免缓冲区堵塞死锁）
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
            main.post(() -> {
                if (cb != null) cb.onResult(fExit, fOut, fErr);
            });
        });
    }

    /** 把用户命令安全地塞进 su -c '...'。 */
    private static String buildSuCmd(String cmd) {
        // 单引号内单引号转义： ' -> '\''
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