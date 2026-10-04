package com.zimky.coregate.core;

import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

/**
 * WebUI ↔ root shell 的 JS 桥。
 *
 * 目标：让原模块 WebUI（webroot/index.html）**一行不改**即可在 APK 内运行。
 * 原 WebUI 调用的是 KernelSU 注入的全局对象：
 *
 *   ksu.exec(cmd, '{}', callbackName);
 *   window[callbackName] = function(errno, stdout, stderr){ ... }
 *
 * 本桥用 @JavascriptInterface 暴露一个同名同签名的 ksu 对象，
 * 把 ksu.exec 的调用转发给 RootShell（已内置单线程串行队列，防 ColorOS 误判），
 * 执行完成后回到主线程，用 WebView.evaluateJavascript 调用页面的回调函数。
 *
 * 注意：KernelSU 的 ksu.exec 语义是「把 cmd 当作 shell 脚本交给 root 执行」，
 * 故这里直接走 RootShell.exec（内部 su -c），不再额外包一层 sh -c，
 * 与原 WebUI 的 sh() 包装（其自身已拼 "sh -c '...'"）正好衔接。
 */
public final class KsuBridge {

    /** 注入到 WebView 的全局对象名，必须与 WebUI 里用的一致。 */
    public static final String NAME = "ksu";

    private final WebView webView;
    private final Handler main = new Handler(Looper.getMainLooper());

    public KsuBridge(WebView webView) {
        this.webView = webView;
    }

    /**
     * 对应 ksu.exec(cmd, options, callback)。
     * options 原样为 '{}'，此处不解析（WebUI 未使用其内容）。
     */
    @JavascriptInterface
    public void exec(final String cmd, final String options, final String callbackName) {
        RootShell.get().exec(cmd, new RootShell.Callback() {
            @Override
            public void onResult(int exitCode, String stdout, String stderr) {
                deliver(callbackName, exitCode, stdout, stderr);
            }
        });
    }

    /** 兼容少数脚本可能调用的同步取版本接口（原 WebUI 未用，留着无害）。 */
    @JavascriptInterface
    public String version() {
        return "CoreGate-APK/1.0";
    }

    /**
     * 在主线程回调页面里的 window[callbackName](errno, stdout, stderr)。
     * 用 JSON 字符串拼参，确保多行/引号内容安全传递。
     */
    private void deliver(final String callbackName, final int exitCode, final String stdout, final String stderr) {
        if (callbackName == null || callbackName.isEmpty()) return;
        final String js = "window['" + callbackName.replace("'", "\\'") + "'](" +
                exitCode + "," +
                jsStr(stdout) + "," +
                jsStr(stderr) + ")";
        main.post(new Runnable() {
            @Override
            public void run() {
                if (webView != null) {
                    try {
                        webView.evaluateJavascript(js, null);
                    } catch (Throwable ignore) {
                    }
                }
            }
        });
    }

    /** 安全地把任意字符串包装成 JS 字符串字面量（含换行、引号、unicode 行分隔符）。 */
    private static String jsStr(String s) {
        if (s == null) return "''";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        sb.append('\'');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\'': sb.append("\\'"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\u2028': sb.append("\\u2028"); break;
                case '\u2029': sb.append("\\u2029"); break;
                default: sb.append(c);
            }
        }
        sb.append('\'');
        return sb.toString();
    }
}
