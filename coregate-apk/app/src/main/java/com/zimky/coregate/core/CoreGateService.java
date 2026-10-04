package com.zimky.coregate.core;

/**
 * CoreGateService —— 业务逻辑集中层。
 *
 * 所有读写内核节点、读写 config.json、调用模块脚本的操作都在这里，
 * Fragment 只负责调用 + 渲染，保持 UI 层干净。
 *
 * 所有命令最终都走 RootShell（su 串行队列）。
 *
 * 命令来源与模块 service.sh / WebUI index.html 保持一致，确保
 * 「APK 改的 = WebUI 改的 = 模块认的」三方语义统一。
 */
public final class CoreGateService {

    private CoreGateService() {
    }

    // =========================================================
    //  一、基础路径常量（与 service.sh 对齐）
    // =========================================================
    public static final String CG_DIR = "/data/adb/CoreGate";
    public static final String CONFIG = CG_DIR + "/config.json";
    public static final String STATE = CG_DIR + "/state";
    public static final String DAEMON_PID = CG_DIR + "/daemon.pid";
    public static final String SNAPSHOT = CG_DIR + "/freq_snapshot";
    public static final String PKG_FILE = CG_DIR + "/packages.txt";
    /** 模块脚本一次性入口（--lock-once / --unlock-once）；APK 与模块通信的权威通道。 */
    public static final String MODULE_SH = "/data/adb/modules/CoreGate/service.sh";

    public static final String P0 = "/sys/devices/system/cpu/cpufreq/policy0";
    public static final String P6 = "/sys/devices/system/cpu/cpufreq/policy6";
    public static final String GPU_CLK = "/sys/class/kgsl/kgsl-3d0/max_gpuclk";
    public static final String GPU_PWR = "/sys/class/kgsl/kgsl-3d0/max_pwrlevel";

    // =========================================================
    //  二、状态查询
    // =========================================================

    /** 读取模块配置文件全文（原始 JSON 文本）。 */
    public static void loadConfigText(RootShell.Callback cb) {
        RootShell.get().exec("cat " + CONFIG + " 2>/dev/null", cb);
    }

    /** 保存配置：写入临时文件再原子替换（避免写坏）。 */
    public static void saveConfig(CoreGateConfig cfg, RootShell.Callback cb) {
        String json = cfg.toJson();
        // 用 base64 传递，避免引号/换行在 shell 里被吞
        String b64 = android.util.Base64.encodeToString(
                json.getBytes(java.nio.charset.Charset.forName("UTF-8")),
                android.util.Base64.NO_WRAP);
        String cmd = "mkdir -p " + CG_DIR + "; "
                + "echo '" + b64 + "' | base64 -d > " + CONFIG + ".tmp; "
                + "mv " + CONFIG + ".tmp " + CONFIG + "; "
                + "chmod 600 " + CONFIG;
        RootShell.get().exec(cmd, cb);
    }

    /** 读取守护进程状态：pid + 存活 + state 内容。 */
    public static void queryDaemon(RootShell.Callback cb) {
        String cmd =
                "PID=$(cat " + DAEMON_PID + " 2>/dev/null); "
                        + "echo \"PID=$PID\"; "
                        + "if [ -n \"$PID\" ] && [ -d /proc/$PID ]; then "
                        + "  echo \"ALIVE=1\"; "
                        + "  echo \"WCHAN=$(cat /proc/$PID/wchan 2>/dev/null)\"; "
                        + "else echo \"ALIVE=0\"; fi; "
                        + "echo \"STATE=$(cat " + STATE + " 2>/dev/null)\"; "
                        + "echo \"SNAP=$([ -f " + SNAPSHOT + " ] && echo 1 || echo 0)\"";
        RootShell.get().exec(cmd, cb);
    }

    /** 读取 CPU 核心状态（每核 online + 当前频率）+ 两个簇的 max 频率。 */
    public static void queryCpu(RootShell.Callback cb) {
        StringBuilder cmd = new StringBuilder();
        // 先各取一次簇频率（per-policy 才是真实存在的节点，per-core 通常没有）
        cmd.append("P0_CUR=$(cat ").append(P0).append("/scaling_cur_freq 2>/dev/null);");
        cmd.append("P6_CUR=$(cat ").append(P6).append("/scaling_cur_freq 2>/dev/null);");
        // 每核 online；频率统一取所属簇的频率（同簇同频，符合真机实际）
        for (int i = 0; i < 8; i++) {
            cmd.append("echo \"C").append(i).append("_ON=$(cat /sys/devices/system/cpu/cpu")
                    .append(i).append("/online 2>/dev/null || echo 1)\";");
            cmd.append("echo \"C").append(i).append("_FREQ=$")
                    .append(i < 6 ? "P0_CUR" : "P6_CUR").append("\";");
        }
        cmd.append("echo \"P0_MAX=$(cat ").append(P0).append("/scaling_max_freq 2>/dev/null)\";");
        cmd.append("echo \"P0_MIN=$(cat ").append(P0).append("/scaling_min_freq 2>/dev/null)\";");
        cmd.append("echo \"P0_HW=$(cat ").append(P0).append("/scaling_available_frequencies 2>/dev/null | tr ' ' '\\n' | sort -n | tail -1)\";");
        cmd.append("echo \"P6_MAX=$(cat ").append(P6).append("/scaling_max_freq 2>/dev/null)\";");
        cmd.append("echo \"P6_MIN=$(cat ").append(P6).append("/scaling_min_freq 2>/dev/null)\";");
        cmd.append("echo \"P6_HW=$(cat ").append(P6).append("/scaling_available_frequencies 2>/dev/null | tr ' ' '\\n' | sort -n | tail -1)\";");
        RootShell.get().exec(cmd.toString(), cb);
    }

    /** 读取 GPU 限频实况。 */
    public static void queryGpu(RootShell.Callback cb) {
        String cmd =
                "echo \"GPU_CLK=$(cat " + GPU_CLK + " 2>/dev/null)\"; "
                        + "echo \"GPU_PWR=$(cat " + GPU_PWR + " 2>/dev/null)\"; "
                        + "echo \"GPU_MIN_PWR=$(cat /sys/class/kgsl/kgsl-3d0/min_pwrlevel 2>/dev/null)\"; "
                        + "echo \"GPU_NUM_PWR=$(cat /sys/class/kgsl/kgsl-3d0/num_pwrlevels 2>/dev/null)\"";
        RootShell.get().exec(cmd, cb);
    }

    /** 读取当前前台包名（ColorOS 多路回退，与 service.sh / WebUI 一致）。 */
    public static void queryFocusPkg(RootShell.Callback cb) {
        String cmd =
                "P=$(dumpsys activity activities 2>/dev/null | grep -m1 -E 'mResumedActivity|topResumedActivity' | grep -oE '[a-zA-Z0-9_.]+/[a-zA-Z0-9_.]+' | head -1 | cut -d/ -f1); "
                        + "[ -z \"$P\" ] && P=$(dumpsys window 2>/dev/null | grep -m1 'mCurrentFocus' | grep -oE '[a-zA-Z0-9_.]+/' | head -1 | cut -d/ -f1); "
                        + "[ -z \"$P\" ] && P=$(dumpsys activity 2>/dev/null | grep -m1 'mFocusedApp' | sed 's/.* //' | cut -d/ -f1); "
                        + "echo \"PKG=$P\"";
        RootShell.get().exec(cmd, cb);
    }

    // =========================================================
    //  三、操作类
    // =========================================================

    /**
     * 手动触发一次锁核。
     *
     * 优先调用模块 service.sh 的一次性入口（--lock-once N），与 WebUI 完全同源；
     * 若模块未安装 / 脚本不存在（su 返回非 0），则**退回真正的 sysfs 兜底**：
     * 自己按「最高频簇」挑 N 个核（跳过 cpu0），逐核写 online=0，
     * 语义与 service.sh 的 do_lock 保持一致（cpu0 永不下线、逐核渐进）。
     *
     * ⚠️ 旧实现兜底只是一句 echo，脚本失败时锁核会【静默失效】却回报成功；
     *    现改为真实 sysfs 写入，并在结尾输出 RESULT 行供调用方判断。
     */
    public static void applyLock(int count, RootShell.Callback cb) {
        int n = count;
        if (n < 1) n = 1;
        if (n > 4) n = 4;

        String cmd =
                // 1) 先试模块一次性入口（最权威，与 WebUI 同源）
                "OUT=$(sh " + MODULE_SH + " --lock-once " + n + " 2>&1); "
                        + "if echo \"$OUT\" | grep -q 'LOCK_ONCE_DONE'; then "
                        + "  echo \"$OUT\"; echo 'RESULT=module'; "
                        + "else "
                        // 2) 模块不可用 → 真·sysfs 兜底
                        + "  echo '(模块入口不可用，改用 sysfs 直接封锁)'; "
                        + "  MAXF=0; "
                        + "  for c in 0 1 2 3 4 5 6 7; do "
                        + "    f=$(cat /sys/devices/system/cpu/cpu$c/cpufreq/cpuinfo_max_freq 2>/dev/null); "
                        + "    [ -n \"$f\" ] && [ \"$f\" -gt \"$MAXF\" ] && MAXF=$f; "
                        + "  done; "
                        + "  CANDS=''; "
                        + "  for c in 7 6 5 4 3 2 1; do "
                        + "    f=$(cat /sys/devices/system/cpu/cpu$c/cpufreq/cpuinfo_max_freq 2>/dev/null); "
                        + "    [ -n \"$f\" ] && [ \"$f\" -eq \"$MAXF\" ] && CANDS=\"$CANDS $c\"; "
                        + "  done; "
                        + "  PICK=$(echo $CANDS | tr ' ' '\\n' | sort -nr | head -n " + n + "); "
                        + "  OK=0; FAIL=0; "
                        + "  for c in $PICK; do "
                        + "    [ \"$c\" = \"0\" ] && continue; "
                        + "    if echo 0 > /sys/devices/system/cpu/cpu$c/online 2>/dev/null; then "
                        + "      OK=$((OK+1)); sleep 1; "
                        + "    else FAIL=$((FAIL+1)); fi; "
                        + "  done; "
                        + "  OC=0; for c in 0 1 2 3 4 5 6 7; do "
                        + "    o=$(cat /sys/devices/system/cpu/cpu$c/online 2>/dev/null); "
                        + "    [ \"$o\" = \"1\" ] && OC=$((OC+1)); "
                        + "  done; "
                        + "  echo \"LOCK_DONE: 封锁=[$PICK] 成功=$OK 失败=$FAIL 在线=$OC\"; "
                        + "  echo 'RESULT=sysfs'; "
                        + "fi";
        RootShell.get().exec(cmd, cb);
    }

    /** 写入目标包名列表（packages.txt）。 */
    public static void savePackages(String pkgs, RootShell.Callback cb) {
        String b64 = android.util.Base64.encodeToString(
                (pkgs == null ? "" : pkgs).getBytes(java.nio.charset.Charset.forName("UTF-8")),
                android.util.Base64.NO_WRAP);
        String cmd = "mkdir -p " + CG_DIR + "; "
                + "echo '" + b64 + "' | base64 -d > " + PKG_FILE + "; "
                + "chmod 600 " + PKG_FILE;
        RootShell.get().exec(cmd, cb);
    }

    /** 读取目标包名列表。 */
    public static void loadPackages(RootShell.Callback cb) {
        RootShell.get().exec("cat " + PKG_FILE + " 2>/dev/null", cb);
    }

    // =========================================================
    //  四、诊断 / 测试 / 清理
    // =========================================================

    /** 环境诊断：root / 模块目录 / 关键节点 / 内核版本。 */
    public static void diagnose(RootShell.Callback cb) {
        String cmd =
                "echo \"== ROOT ==\"; id; "
                        + "echo \"== MODULE ==\"; ls -la /data/adb/modules/CoreGate/ 2>/dev/null | head -20; "
                        + "echo \"== DATA ==\"; ls -la " + CG_DIR + "/ 2>/dev/null; "
                        + "echo \"== NODES ==\"; "
                        + "echo \"P0=$(cat " + P0 + "/scaling_max_freq 2>/dev/null)\"; "
                        + "echo \"P6=$(cat " + P6 + "/scaling_max_freq 2>/dev/null)\"; "
                        + "echo \"GPU=$(cat " + GPU_CLK + " 2>/dev/null)\"; "
                        + "echo \"== KERNEL ==\"; uname -a";
        RootShell.get().exec(cmd, cb);
    }

    /** 读取最近日志（模块没有独立日志时回退 logcat 抓 CoreGate 相关）。 */
    public static void getLog(RootShell.Callback cb) {
        String cmd = "cat " + CG_DIR + "/coregate.log 2>/dev/null; "
                + "if [ ! -s " + CG_DIR + "/coregate.log ]; then "
                + "  echo '(模块未生成 coregate.log，以下为实时进程状态)'; "
                + "  PID=$(cat " + DAEMON_PID + " 2>/dev/null); "
                + "  [ -n \"$PID\" ] && cat /proc/$PID/status 2>/dev/null | head -12; "
                + "fi";
        RootShell.get().exec(cmd, cb);
    }

    /** 清理临时 / 残留文件（不动用户配置）。 */
    public static void cleanTemp(RootShell.Callback cb) {
        String cmd = "rm -f " + CONFIG + ".tmp 2>/dev/null; "
                + "rm -f " + CG_DIR + "/*.tmp 2>/dev/null; "
                + "echo CLEAN_DONE";
        RootShell.get().exec(cmd, cb);
    }

    /**
     * 一键恢复原始状态（撤限频 + 解锁核 + 删快照），不删模块本体。
     *
     * CPU：优先读 freq_snapshot 回写（与 service.sh 的快照机制对齐）；
     *      无快照时回退硬件最大合法档（scaling_available_frequencies 最大值）。
     * GPU：先 chmod 644 解锁 → 写回 → chmod 444 锁上（与晨夕行为对齐、保持可逆）。
     *      max_gpuclk 优先快照/硬件最大档；max_pwrlevel 恢复为 0（最高性能档）。
     *
     * ⚠️ 快照格式对齐（必须与 service.sh 完全一致）：
     *      service.sh 的 freq_snapshot 是「每簇一行 key=value」：
     *          /sys/devices/system/cpu/cpufreq/policy0=<freq>
     *          /sys/devices/system/cpu/cpufreq/policy6=<freq>
     *      且【只记录 P0/P6 两簇，不记录 GPU】。
     *      旧实现误用 sed -n '1p'/'2p'/'3p' 按行号取值，会把整行
     *      “目录=频率”当成频率写回（必然失败），且第 3 行取 GPU 恒为空。
     *      现改为按「簇目录」精确 grep 取值，与 shell 端 freq_snapshot_get 同源。
     */
    public static void restoreOriginal(RootShell.Callback cb) {
        String cmd =
                "echo 'RESTORE: 撤限频'; "
                        // 0) 优先调用模块一次性恢复入口（解锁全部核心 + 撤销限频，与 WebUI 同源）
                        + "UOUT=$(sh " + MODULE_SH + " --unlock-once 2>&1); "
                        + "if echo \"$UOUT\" | grep -q 'UNLOCK_ONCE_DONE'; then "
                        + "  echo \"$UOUT\"; echo 'RESULT=module'; "
                        + "else echo '(模块恢复入口不可用，改用 sysfs 直接恢复)'; echo 'RESULT=sysfs'; fi; "
                        // --- CPU: 快照优先，无快照用硬件最大合法档 ---
                        + "P0_HW=$(cat " + P0 + "/scaling_available_frequencies 2>/dev/null | tr ' ' '\\n' | sort -n | tail -1); "
                        + "P6_HW=$(cat " + P6 + "/scaling_available_frequencies 2>/dev/null | tr ' ' '\\n' | sort -n | tail -1); "
                        + "P0_SNAP=$(grep \"^" + P0 + "=\" " + SNAPSHOT + " 2>/dev/null | tail -n 1 | sed 's/^[^=]*=//'); "
                        + "P6_SNAP=$(grep \"^" + P6 + "=\" " + SNAPSHOT + " 2>/dev/null | tail -n 1 | sed 's/^[^=]*=//'); "
                        + "[ -n \"$P0_SNAP\" ] && P0_HW=$P0_SNAP; "
                        + "[ -n \"$P6_SNAP\" ] && P6_HW=$P6_SNAP; "
                        + "[ -n \"$P0_HW\" ] && echo $P0_HW > " + P0 + "/scaling_max_freq 2>/dev/null; "
                        + "[ -n \"$P6_HW\" ] && echo $P6_HW > " + P6 + "/scaling_max_freq 2>/dev/null; "
                        // --- GPU: 解锁 -> 写回 -> 锁上 ---
                        // 快照不含 GPU（与 service.sh 一致）；GPU 恢复恒用硬件最大档。
                        + "chmod 644 " + GPU_CLK + " 2>/dev/null; "
                        + "chmod 644 " + GPU_PWR + " 2>/dev/null; "
                        + "GPU_HW=1225000000; "
                        + "echo $GPU_HW > " + GPU_CLK + " 2>/dev/null; "
                        + "echo 0 > " + GPU_PWR + " 2>/dev/null; "
                        + "sync; "
                        + "chmod 444 " + GPU_CLK + " 2>/dev/null; "
                        + "chmod 444 " + GPU_PWR + " 2>/dev/null; "
                        + "rm -f " + SNAPSHOT + " 2>/dev/null; "
                        + "echo RESTORE_DONE";
        RootShell.get().exec(cmd, cb);
    }

    // =========================================================
    //  五、工具
    // =========================================================

    /** 从 "KEY=VALUE" 多行输出里取某个键的值。 */
    public static String pick(String dump, String key) {
        if (dump == null) return "";
        String[] lines = dump.split("\n");
        for (String ln : lines) {
            int eq = ln.indexOf('=');
            if (eq > 0 && ln.substring(0, eq).trim().equals(key)) {
                return ln.substring(eq + 1).trim();
            }
        }
        return "";
    }

    /** MHz -> 频率数字串（用于显示 kHz -> MHz）。 */
    public static String khzToMhz(String khz) {
        try {
            long v = Long.parseLong(khz.trim());
            return String.valueOf(v / 1000);
        } catch (Exception e) {
            return khz;
        }
    }
}