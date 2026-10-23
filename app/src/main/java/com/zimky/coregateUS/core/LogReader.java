package com.zimky.coregateUS.core;

/**
 * LogReader —— 读模块日志，多路径回退。
 *
 * 与 WebUI 的 refreshLog 顺序一致：
 *   /data/adb/modules/CoreGate/log/coregate.log
 *   /data/adb/modules_update/CoreGate/log/coregate.log
 *   /data/adb/CoreGate/log/coregate.log
 *   /data/adb/CoreGate/coregate.log   （旧路径）
 */
public final class LogReader {

    public static final String[] PATHS = new String[]{
            "/data/adb/modules/CoreGate/log/coregate.log",
            "/data/adb/modules_update/CoreGate/log/coregate.log",
            "/data/adb/CoreGate/log/coregate.log",
            "/data/adb/CoreGate/coregate.log"
    };

    public interface Callback {
        void onLog(String text, String from, boolean found);
    }

    /** tail 行数，够看即可，避免一次拉几 MB。 */
    public static void tail(final RootShell sh, final int lines, final Callback cb) {
        final StringBuilder c = new StringBuilder();
        int n = lines <= 0 ? 160 : lines;
        for (String p : PATHS) {
            c.append("if [ -f ").append(p).append(" ]; then ")
                    .append("echo \"###FROM:").append(p).append("\"; ")
                    .append("tail -n ").append(n).append(" ").append(p).append("; ")
                    .append("echo \"###ENDLOG\"; ")
                    .append("fi; ");
        }
        c.append("echo \"###ENDALL\"");

        sh.exec(c.toString(), new RootShell.Callback() {
            @Override
            public void onResult(int code, String out, String err) {
                if (out == null || !out.contains("###FROM:")) {
                    if (cb != null) cb.onLog("", "", false);
                    return;
                }
                int a = out.indexOf("###FROM:");
                int b = out.indexOf("###ENDLOG", a);
                if (b < 0) b = out.length();

                int nl = out.indexOf('\n', a);
                if (nl < 0) nl = a;
                String path = out.substring(a + "###FROM:".length(), nl).trim();

                String body = out.substring(Math.min(nl + 1, out.length()), Math.min(b, out.length()));
                if (cb != null) cb.onLog(body.trim(), path, true);
            }
        });
    }

    /** 清空所有日志文件。 */
    public static void clear(final RootShell sh, final RootShell.Callback cb) {
        StringBuilder c = new StringBuilder();
        for (String p : PATHS) {
            c.append(": > ").append(p).append(" 2>/dev/null; ");
        }
        c.append("echo ok");
        sh.exec(c.toString(), cb);
    }
}