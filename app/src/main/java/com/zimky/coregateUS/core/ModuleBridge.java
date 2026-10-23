package com.zimky.coregateUS.core;

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
public final class ModuleBridge {

    public interface Callback {
        void onDone(boolean ok, String msg);
    }

    /** 结果明细，供 UI 显示。 */
    public static final class Result {
        public boolean ok;
        public String list = "";
        public String applied = "";
        public int online = -1;
        public String msg = "";
    }

    public static boolean serviceExists() {
        // 走进程内 File 判断即可，不需要 root
        return new java.io.File(Config.SERVICE).exists();
    }

    /**
     * 立即封锁一次。
     *
     * @param count 封锁核心数；0 或负数表示按 config.json 的 lock_cores 走
     */
    public static void lockOnce(final RootShell sh, final int count, final Callback cb) {
        String cmd = buildCmd("--lock-once", count);
        sh.exec(cmd, new RootShell.Callback() {
            @Override
            public void onResult(int code, String out, String err) {
                Result r = parse(out);
                if (r == null) {
                    r = new Result();
                    r.ok = false;
                    r.msg = firstLine(err, out, code);
                }
                if (cb != null) cb.onDone(r.ok, r.msg);
            }
        });
    }

    /** 立即恢复全部核心。 */
    public static void unlockOnce(final RootShell sh, final Callback cb) {
        String cmd = buildCmd("--unlock-once", 0);
        sh.exec(cmd, new RootShell.Callback() {
            @Override
            public void onResult(int code, String out, String err) {
                Result r = parse(out);
                if (r == null) {
                    r = new Result();
                    r.ok = false;
                    r.msg = firstLine(err, out, code);
                }
                if (cb != null) cb.onDone(r.ok, r.msg);
            }
        });
    }

    private static String buildCmd(String flag, int count) {
        StringBuilder sb = new StringBuilder();
        sb.append("[ -f ").append(Config.SERVICE).append(" ] || { echo 'NO_SERVICE'; exit 1; }; ");
        sb.append("sh ").append(Config.SERVICE).append(' ').append(flag);
        if ("--lock-once".equals(flag) && count > 0) {
            sb.append(' ').append(count);
        }
        return sb.toString();
    }

    /** 解析 service.sh 的输出，认不出来返回 null。 */
    public static Result parse(String out) {
        if (out == null) return null;
        Result r = new Result();

        if (out.contains("LOCK_ONCE_DONE")) {
            r.ok = true;
            r.list = between(out, "名单=[", "]");
            r.applied = between(out, "结果=[", "]");
            int on = indexInt(out, "在线=");
            r.online = on;
            StringBuilder m = new StringBuilder();
            m.append("已封锁 ").append(r.applied.isEmpty() ? "—" : r.applied);
            if (!r.list.isEmpty()) m.append("（名单 ").append(r.list).append("）");
            if (on >= 0) m.append(" · 在线核心 ").append(on);
            r.msg = m.toString();
            return r;
        }

        if (out.contains("UNLOCK_ONCE_DONE")) {
            r.ok = true;
            int on = indexInt(out, "在线=");
            r.online = on;
            r.msg = on >= 0 ? ("已恢复全部核心 · 在线 " + on) : "已恢复全部核心";
            return r;
        }

        if (out.contains("NO_SERVICE")) {
            r.ok = false;
            r.msg = "未找到 service.sh，模块可能没装或路径不对";
            return r;
        }

        if (out.contains("LOCK_ONCE: 模块已禁用")) {
            r.ok = false;
            r.msg = "模块已禁用，已跳过封锁。请先打开上面那个总开关";
            return r;
        }

        if (out.contains("LOCK_ONCE: 无可封锁核心")) {
            r.ok = false;
            r.msg = "没有可封锁的核心（可能全部已离线）";
            return r;
        }

        return null;
    }

    private static String between(String s, String start, String end) {
        int a = s.indexOf(start);
        if (a < 0) return "";
        int b = s.indexOf(end, a + start.length());
        if (b < 0) return "";
        return s.substring(a + start.length(), b).trim();
    }

    private static int indexInt(String s, String tag) {
        int a = s.indexOf(tag);
        if (a < 0) return -1;
        int i = a + tag.length();
        StringBuilder sb = new StringBuilder();
        while (i < s.length() && Character.isDigit(s.charAt(i))) {
            sb.append(s.charAt(i));
            i++;
        }
        if (sb.length() == 0) return -1;
        try {
            return Integer.parseInt(sb.toString());
        } catch (Throwable t) {
            return -1;
        }
    }

    private static String firstLine(String err, String out, int code) {
        String src = (err != null && !err.trim().isEmpty()) ? err : out;
        if (src == null || src.trim().isEmpty()) {
            return "命令无输出（exit=" + code + "），请检查 root 授权";
        }
        String[] lines = src.trim().split("\n");
        for (String ln : lines) {
            String t = ln.trim();
            if (!t.isEmpty()) return t;
        }
        return "exit=" + code;
    }
}