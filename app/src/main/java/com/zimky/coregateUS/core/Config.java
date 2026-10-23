package com.zimky.coregateUS.core;

import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * Config —— config.json / packages.txt 的读写。
 *
 * ⚠ 血泪教训：必须把 7 个字段全部写回。
 * service.sh 的 cfg_bool / cfg_int / cfg_val 在字段缺失时直接回退默认值，
 * 旧版 config.example.json 只有 7 个字段里的少数几个，
 * 一旦 APK 只写自己认识的字段，capture_boost 就会被静默关掉。
 * 所以 toJson() 永远输出全字段。
 *
 * 模块端约定：
 *   /data/adb/CoreGate/config.json    JSON 单行
 *   /data/adb/CoreGate/packages.txt   每行一个包名（游戏列表）
 */
public final class Config {

    public static final String DIR        = "/data/adb/CoreGate";
    public static final String FILE       = DIR + "/config.json";
    public static final String PACKAGES   = DIR + "/packages.txt";
    public static final String STATE      = DIR + "/state";
    public static final String PID        = DIR + "/daemon.pid";
    public static final String SNAPSHOT   = DIR + "/freq_snapshot";

    public static final String MODULE_DIR = "/data/adb/modules/CoreGate";
    public static final String SERVICE    = MODULE_DIR + "/service.sh";
    public static final String WEBROOT    = MODULE_DIR + "/webroot";

    /** 字段与 service.sh 一一对应，不要改名。 */
    public boolean enabled;         // 模块总开关
    public String  lockCores;       // 'auto' 或 "2,3,4" 这种手写名单
    public boolean disableCoreCtl;  // 关掉系统 core_ctl
    public boolean noLockCharging;  // 充电时不锁
    public int     pollInterval;    // 5~600 秒
    public boolean limitFreq;       // 日常限频
    public boolean captureBoost;    // 录屏/相机自动恢复

    /** 真机上确实读到了文件的标记，用于诊断页。 */
    public boolean loaded;

    public interface LoadCallback {
        void onConfig(Config cfg, String packagesRaw, String dbg);
    }

    public static Config defaults() {
        Config c = new Config();
        c.enabled        = true;
        c.lockCores      = "auto";
        c.disableCoreCtl = false;
        c.noLockCharging = false;
        c.pollInterval   = 30;
        c.limitFreq      = false;
        c.captureBoost   = false;
        c.loaded         = false;
        return c;
    }

    // ---------------------------------------------------------------
    // 解析
    // ---------------------------------------------------------------

    public static Config parse(String raw) {
        Config c = defaults();
        if (raw == null) return c;
        raw = raw.trim();
        if (raw.isEmpty()) return c;

        try {
            JSONObject o = new JSONObject(raw);
            c.enabled        = o.optBoolean("enabled", true);
            c.lockCores      = o.optString("lock_cores", "auto");
            c.disableCoreCtl = o.optBoolean("disable_core_ctl", false);
            c.noLockCharging = o.optBoolean("no_lock_charging", false);
            c.pollInterval   = o.optInt("poll_interval", 30);
            c.limitFreq      = o.optBoolean("limit_freq", false);
            c.captureBoost   = o.optBoolean("capture_boost", false);
            c.loaded = true;
            c.pollInterval = clampInterval(c.pollInterval);
            if (c.lockCores == null || c.lockCores.isEmpty()) c.lockCores = "auto";
            return c;
        } catch (Throwable ignored) {
            // 落到字段级兜底：模块被手改坏、或之前是别的格式
        }

        c.enabled        = grabBool(raw, "enabled", true);
        c.lockCores      = grabStr(raw, "lock_cores", "auto");
        c.disableCoreCtl = grabBool(raw, "disable_core_ctl", false);
        c.noLockCharging = grabBool(raw, "no_lock_charging", false);
        c.pollInterval   = clampInterval(grabInt(raw, "poll_interval", 30));
        c.limitFreq      = grabBool(raw, "limit_freq", false);
        c.captureBoost   = grabBool(raw, "capture_boost", false);
        c.loaded = true;
        if (c.lockCores == null || c.lockCores.isEmpty()) c.lockCores = "auto";
        return c;
    }

    public static int clampInterval(int v) {
        if (v < 5) return 5;
        if (v > 600) return 600;
        return v;
    }

    private static boolean grabBool(String s, String k, boolean def) {
        String v = grabRaw(s, k);
        if (v == null) return def;
        if (v.startsWith("true")) return true;
        if (v.startsWith("false")) return false;
        return def;
    }

    private static int grabInt(String s, String k, int def) {
        String v = grabRaw(s, k);
        if (v == null) return def;
        try {
            return Integer.parseInt(v.trim());
        } catch (Throwable t) {
            return def;
        }
    }

    private static String grabStr(String s, String k, String def) {
        int i = s.indexOf("\"" + k + "\"");
        if (i < 0) return def;
        int p = s.indexOf(':', i);
        if (p < 0) return def;
        int q1 = s.indexOf('"', p + 1);
        if (q1 < 0) return def;
        int q2 = s.indexOf('"', q1 + 1);
        if (q2 < 0) return def;
        return s.substring(q1 + 1, q2);
    }

    /** 取 "key": 后面裸值的开头（true / false / 数字）。 */
    private static String grabRaw(String s, String k) {
        int i = s.indexOf("\"" + k + "\"");
        if (i < 0) return null;
        int p = s.indexOf(':', i);
        if (p < 0) return null;
        StringBuilder sb = new StringBuilder();
        for (int j = p + 1; j < s.length(); j++) {
            char ch = s.charAt(j);
            if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') continue;
            if (ch == ',' || ch == '}' || ch == ']') break;
            sb.append(ch);
        }
        String v = sb.toString();
        return v.isEmpty() ? null : v;
    }

    // ---------------------------------------------------------------
    // 序列化
    // ---------------------------------------------------------------

    /** 永远输出全部 7 个字段，绝不省略。 */
    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("enabled", enabled);
            o.put("lock_cores", lockCores == null || lockCores.isEmpty() ? "auto" : lockCores);
            o.put("disable_core_ctl", disableCoreCtl);
            o.put("no_lock_charging", noLockCharging);
            o.put("poll_interval", clampInterval(pollInterval));
            o.put("limit_freq", limitFreq);
            o.put("capture_boost", captureBoost);
        } catch (Throwable ignored) {
        }
        return o;
    }

    public String toJsonString() {
        return toJson().toString();
    }

    public Config copy() {
        Config c = new Config();
        c.enabled        = enabled;
        c.lockCores      = lockCores;
        c.disableCoreCtl = disableCoreCtl;
        c.noLockCharging = noLockCharging;
        c.pollInterval   = pollInterval;
        c.limitFreq      = limitFreq;
        c.captureBoost   = captureBoost;
        c.loaded         = loaded;
        return c;
    }

    // ---------------------------------------------------------------
    // 保存
    // ---------------------------------------------------------------

    /**
     * 生成一条 atomically 落盘的 shell 命令。
     *
     * 用 base64 传递内容，彻底绕开引号 / 换行 / 中文注释等转义问题
     * （WebUI 用 btoa(encodeURIComponent()) 干的是同一件事）。
     * packages 传 null 表示不动 packages.txt。
     */
    public static String buildSaveCmd(Config cfg, String packages) {
        StringBuilder sb = new StringBuilder();
        sb.append("CFG='").append(base64(cfg.toJsonString())).append("'; ");
        sb.append("echo \"$CFG\" | base64 -d > ").append(FILE).append(".tmp && ");
        sb.append("mv ").append(FILE).append(".tmp ").append(FILE).append(" && ");
        sb.append("chmod 644 ").append(FILE).append("; ");

        if (packages != null) {
            sb.append("PKG='").append(base64(packages)).append("'; ");
            sb.append("echo \"$PKG\" | base64 -d > ").append(PACKAGES).append(".tmp && ");
            sb.append("mv ").append(PACKAGES).append(".tmp ").append(PACKAGES).append(" && ");
            sb.append("chmod 644 ").append(PACKAGES).append("; ");
        }

        sb.append("sync; echo OK");
        return sb.toString();
    }

    public static String base64(String s) {
        if (s == null) s = "";
        return Base64.encodeToString(s.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    // ---------------------------------------------------------------
    // 读取
    // ---------------------------------------------------------------

    /**
     * 一次 root 调用取回 config.json + packages.txt。
     *
     * 输出形如：
     *   <<<CFG>>>
     *   {...}
     *   <<<PKG>>>
     *   com.xxx
     *   ...
     * 缺文件对应段为空，由调用方决定回退。
     */
    public static void load(final RootShell sh, final LoadCallback cb) {
        StringBuilder cmd = new StringBuilder();
        cmd.append("echo <<<CFG>>>; ");
        cmd.append("cat ").append(FILE).append(" 2>/dev/null; echo; ");
        cmd.append("echo <<<PKG>>>; ");
        cmd.append("cat ").append(PACKAGES).append(" 2>/dev/null; echo; ");
        cmd.append("echo <<<END>>>");

        sh.exec(cmd.toString(), new RootShell.Callback() {
            @Override
            public void onResult(int code, String out, String err) {
                String cfgRaw = "";
                String pkgRaw = "";
                String dbg = "exit=" + code;

                if (out != null) {
                    int a = out.indexOf("<<<CFG>>>");
                    int b = out.indexOf("<<<PKG>>>");
                    int c = out.indexOf("<<<END>>>");
                    if (a >= 0 && b > a) {
                        cfgRaw = out.substring(a + "<<<CFG>>>".length(), b).trim();
                    }
                    if (b >= 0) {
                        int end = c > b ? c : out.length();
                        pkgRaw = out.substring(b + "<<<PKG>>>".length(), end).trim();
                    }
                    if (cfgRaw.isEmpty()) {
                        dbg += " · config.json 缺失或为空";
                    }
                } else {
                    dbg += " · 无输出（root 未授权？）";
                }

                Config cfg = parse(cfgRaw);
                if (cfgRaw.isEmpty()) cfg.loaded = false;
                if (cb != null) cb.onConfig(cfg, pkgRaw, dbg);
            }
        });
    }

    // ---------------------------------------------------------------
    // 小工具
    // ---------------------------------------------------------------

    /** 游戏包名列表 → 行数组（去空行、去注释）。 */
    public static java.util.List<String> splitPackages(String raw) {
        java.util.ArrayList<String> list = new java.util.ArrayList<String>();
        if (raw == null) return list;
        String[] lines = raw.split("\n");
        for (String ln : lines) {
            if (ln == null) continue;
            String s = ln.trim();
            if (s.isEmpty()) continue;
            if (s.startsWith("#")) continue;
            list.add(s);
        }
        return list;
    }

    /** 行数组 → packages.txt 文本。 */
    public static String joinPackages(java.util.List<String> list) {
        StringBuilder sb = new StringBuilder();
        if (list != null) {
            for (String s : list) {
                if (s == null) continue;
                String t = s.trim();
                if (t.isEmpty()) continue;
                sb.append(t).append('\n');
            }
        }
        return sb.toString();
    }
}