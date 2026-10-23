package com.zimky.coregateUS.core;

import java.util.ArrayList;
import java.util.List;

/**
 * SysProbe —— 系统状态探测。
 *
 * 全部走一次 root shell 批量读，拿回一份带标记的文本再在 Java 侧解析，
 * 目的是把 root 调用次数压到最少（每次 root 都是一次 su 进程，狂刷会被
 * 系统安全机制误判成入侵，v2.0.6 踩过这个坑）。
 */
public final class SysProbe {

    /** 单个核心快照。 */
    public static final class Core {
        public int id;
        public boolean online;
        public long curFreq;      // kHz
        public long maxFreq;      // kHz（cpuinfo_max_freq，厂商标称）
        public long availMax;     // kHz（scaling_available_frequencies 最大值，真实合法上限）
        public String cluster;    // "little" / "big" / "?"
    }

    /** 一整次探测的结果。 */
    public static final class Snapshot {
        public final List<Core> cores = new ArrayList<Core>();
        public int onlineCount;
        public long gpuCur;       // Hz
        public long gpuMax;       // Hz
        public int gpuPwrLevel = -1;
        public int gpuNumLevels = -1;
        public String statePkg = "";   // state 文件第一段（前台包名）
        public String stateRaw = "";
        public boolean daemonAlive;
        public int daemonPid = -1;
        public long littleMaxCur; // policy0 scaling_max_freq
        public long bigMaxCur;    // policy6 scaling_max_freq
        public String dbg = "";
    }

    public interface Callback {
        void onSnapshot(Snapshot s);
    }

    /** 一次 root 拿全量状态。 */
    public static void probe(final RootShell sh, final Callback cb) {
        final StringBuilder c = new StringBuilder();
        // 核心在线状态 0..7
        for (int i = 0; i < 8; i++) {
            c.append("echo \"ON").append(i).append(":\"; cat /sys/devices/system/cpu/cpu")
                    .append(i).append("/online 2>/dev/null; ");
        }
        // 每个核心当前频率与上限
        for (int i = 0; i < 8; i++) {
            String base = "/sys/devices/system/cpu/cpu" + i + "/cpufreq/";
            c.append("echo \"CU").append(i).append(":\"; cat ").append(base)
                    .append("scaling_cur_freq 2>/dev/null; ");
            c.append("echo \"MX").append(i).append(":\"; cat ").append(base)
                    .append("cpuinfo_max_freq 2>/dev/null; ");
            c.append("echo \"AV").append(i).append(":\"; cat ").append(base)
                    .append("scaling_available_frequencies 2>/dev/null; ");
        }
        // 簇上限
        c.append("echo \"LMAX:\"; cat /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq 2>/dev/null; ");
        c.append("echo \"BMAX:\"; cat /sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq 2>/dev/null; ");
        // GPU
        c.append("echo \"GCUR:\"; cat /sys/class/kgsl/kgsl-3d0/gpuclk 2>/dev/null; ");
        c.append("echo \"GMAX:\"; cat /sys/class/kgsl/kgsl-3d0/max_gpuclk 2>/dev/null; ");
        c.append("echo \"GPLV:\"; cat /sys/class/kgsl/kgsl-3d0/max_pwrlevel 2>/dev/null; ");
        c.append("echo \"GNUM:\"; cat /sys/class/kgsl/kgsl-3d0/num_pwrlevels 2>/dev/null; ");
        c.append("echo \"GDEVF:\"; cat /sys/class/kgsl/kgsl-3d0/devfreq/max_freq 2>/dev/null; ");
        // daemon 与 state
        c.append("echo \"STATEB:\"; ");
        c.append("PID=$(cat ").append(Config.PID).append(" 2>/dev/null); ");
        c.append("if [ -n \"$PID\" ] && kill -0 \"$PID\" 2>/dev/null; then echo \"ALIVE:$PID\"; else echo \"DEAD:$PID\"; fi; ");
        c.append("echo \"STATEF:\"; cat ").append(Config.STATE).append(" 2>/dev/null; ");
        c.append("echo \"ENDPROBE:\"");

        sh.exec(c.toString(), new RootShell.Callback() {
            @Override
            public void onResult(int code, String out, String err) {
                Snapshot s = new Snapshot();
                s.dbg = "exit=" + code;
                if (out == null || out.isEmpty()) {
                    s.dbg += " · 无输出（root 未授权？）";
                    if (cb != null) cb.onSnapshot(s);
                    return;
                }

                for (int i = 0; i < 8; i++) {
                    Core core = new Core();
                    core.id = i;
                    String onRaw = line(out, "ON" + i);
                    // cpu0 没有 online 节点，视为在线
                    if (onRaw == null || onRaw.isEmpty()) {
                        core.online = (i == 0);
                    } else {
                        core.online = "1".equals(onRaw.trim());
                    }
                    core.curFreq = num(line(out, "CU" + i));
                    core.maxFreq = num(line(out, "MX" + i));

                    String avail = line(out, "AV" + i);
                    if (avail != null && !avail.isEmpty()) {
                        long best = 0;
                        String[] parts = avail.trim().split("\\s+");
                        for (String p : parts) {
                            long v = num(p);
                            if (v > best) best = v;
                        }
                        core.availMax = best;
                    }

                    if (i <= 3) {
                        core.cluster = "little";
                    } else if (i >= 6) {
                        core.cluster = "big";
                    } else {
                        core.cluster = "little";
                    }

                    if (core.online) s.onlineCount++;
                    s.cores.add(core);
                }

                s.littleMaxCur = num(line(out, "LMAX"));
                s.bigMaxCur = num(line(out, "BMAX"));

                s.gpuCur = num(line(out, "GCUR"));
                s.gpuMax = num(line(out, "GMAX"));
                String plv = line(out, "GPLV");
                if (plv != null && !plv.isEmpty()) s.gpuPwrLevel = (int) num(plv);
                String gnum = line(out, "GNUM");
                if (gnum != null && !gnum.isEmpty()) s.gpuNumLevels = (int) num(gnum);

                String stateb = line(out, "STATEB");
                if (stateb != null && stateb.startsWith("ALIVE:")) {
                    s.daemonAlive = true;
                    s.daemonPid = (int) num(stateb.substring(6));
                } else if (stateb != null && stateb.startsWith("DEAD:")) {
                    s.daemonAlive = false;
                    s.daemonPid = (int) num(stateb.substring(5));
                }

                String st = line(out, "STATEF");
                if (st != null) {
                    s.stateRaw = st.trim();
                    String[] seg = s.stateRaw.split("\\s+");
                    if (seg.length > 0) s.statePkg = seg[0];
                }

                if (cb != null) cb.onSnapshot(s);
            }
        });
    }

    /** 取 "TAG:" 标记后同一行剩余内容。 */
    private static String line(String out, String tag) {
        int i = out.indexOf(tag + ":");
        if (i < 0) return null;
        int start = i + tag.length() + 1;
        int end = out.indexOf('\n', start);
        if (end < 0) end = out.length();
        String v = out.substring(start, end).trim();
        return v;
    }

    private static long num(String s) {
        if (s == null) return 0;
        s = s.trim();
        if (s.isEmpty()) return 0;
        try {
            // 可能带多个空格分隔的多个数字，取第一个
            int sp = s.indexOf(' ');
            if (sp > 0) s = s.substring(0, sp);
            return Long.parseLong(s);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** kHz → "2.27 GHz" 风格。 */
    public static String fmtMhz(long khz) {
        if (khz <= 0) return "—";
        double mhz = khz / 1000.0;
        if (mhz >= 1000) return String.format(java.util.Locale.US, "%.2f GHz", mhz / 1000.0);
        return String.format(java.util.Locale.US, "%.0f MHz", mhz);
    }

    /** Hz → MHz 字符串。 */
    public static String fmtGpu(long hz) {
        if (hz <= 0) return "—";
        return String.format(java.util.Locale.US, "%.0f MHz", hz / 1000000.0);
    }
}