package com.zimky.coregateUS.core;

/**
 * FreqCap —— 限频实况判定。
 *
 * 对齐 WebUI 的 refreshFreqCap：拿实测值分别与目标值比对，
 * 给出「已锁定 / 未生效 / 超标」三种结论。
 *
 * 目标值与 service.sh 的硬编码一致：
 *   CAP_LITTLE_MHZ = 2266
 *   CAP_BIG_MHZ    = 1651
 *   CAP_GPU_MHZ    = 500
 *   CAP_GPU_PWRLEVEL = 13
 */
public final class FreqCap {

    public static final int LITTLE_MHZ = 2266;
    public static final int BIG_MHZ = 1651;
    public static final int GPU_MHZ = 500;
    public static final int GPU_PWRLEVEL = 13;

    /** 单项判定结果。 */
    public static final class Item {
        public String name;       // "小核簇"
        public String target;     // "≤ 2266 MHz"
        public String actual;     // "2266 MHz"
        public int state;         // STATE_OK / STATE_OFF / STATE_OVER / STATE_UNKNOWN

        public Item(String n, String t, String a, int s) {
            name = n;
            target = t;
            actual = a;
            state = s;
        }
    }

    public static final int STATE_OK = 0;       // 已按目标锁住
    public static final int STATE_OFF = 1;      // 限频开关关着
    public static final int STATE_OVER = 2;     // 实测超过目标（没生效 / 被别的模块改了）
    public static final int STATE_UNKNOWN = 3;  // 读不到

    /** 容差：内核会把写入值对齐到最近档位，差个几 MHz 算已生效。 */
    private static final long TOL_KHZ = 2000;   // ±2 MHz

    public static Item judgeLittle(SysProbe.Snapshot s, boolean limitOn) {
        long targetKhz = LITTLE_MHZ * 1000L;
        String actual = SysProbe.fmtMhz(s.littleMaxCur);
        if (s.littleMaxCur <= 0) {
            return new Item("小核簇", "≤ " + LITTLE_MHZ + " MHz", "读不到", STATE_UNKNOWN);
        }
        if (!limitOn) {
            return new Item("小核簇", "≤ " + LITTLE_MHZ + " MHz", actual, STATE_OFF);
        }
        if (Math.abs(s.littleMaxCur - targetKhz) <= TOL_KHZ) {
            return new Item("小核簇", "≤ " + LITTLE_MHZ + " MHz", actual, STATE_OK);
        }
        if (s.littleMaxCur > targetKhz) {
            return new Item("小核簇", "≤ " + LITTLE_MHZ + " MHz", actual, STATE_OVER);
        }
        // 比目标还低：说明被锁得更狠，也算生效
        return new Item("小核簇", "≤ " + LITTLE_MHZ + " MHz", actual, STATE_OK);
    }

    public static Item judgeBig(SysProbe.Snapshot s, boolean limitOn) {
        long targetKhz = BIG_MHZ * 1000L;
        String actual = SysProbe.fmtMhz(s.bigMaxCur);
        if (s.bigMaxCur <= 0) {
            return new Item("大核簇", "≤ " + BIG_MHZ + " MHz", "读不到", STATE_UNKNOWN);
        }
        if (!limitOn) {
            return new Item("大核簇", "≤ " + BIG_MHZ + " MHz", actual, STATE_OFF);
        }
        if (Math.abs(s.bigMaxCur - targetKhz) <= TOL_KHZ) {
            return new Item("大核簇", "≤ " + BIG_MHZ + " MHz", actual, STATE_OK);
        }
        if (s.bigMaxCur > targetKhz) {
            return new Item("大核簇", "≤ " + BIG_MHZ + " MHz", actual, STATE_OVER);
        }
        return new Item("大核簇", "≤ " + BIG_MHZ + " MHz", actual, STATE_OK);
    }

    public static Item judgeGpu(SysProbe.Snapshot s, boolean limitOn) {
        long targetHz = GPU_MHZ * 1000000L;
        String actual = SysProbe.fmtGpu(s.gpuMax);
        if (s.gpuMax <= 0) {
            return new Item("GPU", "≤ " + GPU_MHZ + " MHz", "读不到", STATE_UNKNOWN);
        }
        if (!limitOn) {
            return new Item("GPU", "≤ " + GPU_MHZ + " MHz", actual, STATE_OFF);
        }
        // GPU 写入会 +800000 对齐，实测会是 500000000
        long tol = 2000000L;
        if (Math.abs(s.gpuMax - targetHz) <= tol) {
            return new Item("GPU", "≤ " + GPU_MHZ + " MHz", actual, STATE_OK);
        }
        if (s.gpuMax > targetHz) {
            return new Item("GPU", "≤ " + GPU_MHZ + " MHz", actual, STATE_OVER);
        }
        return new Item("GPU", "≤ " + GPU_MHZ + " MHz", actual, STATE_OK);
    }

    public static Item judgePwr(SysProbe.Snapshot s, boolean limitOn) {
        String actual = s.gpuPwrLevel < 0 ? "读不到" : String.valueOf(s.gpuPwrLevel);
        if (s.gpuPwrLevel < 0) {
            return new Item("GPU 功耗档", "= " + GPU_PWRLEVEL, actual, STATE_UNKNOWN);
        }
        if (!limitOn) {
            return new Item("GPU 功耗档", "= " + GPU_PWRLEVEL, actual, STATE_OFF);
        }
        if (s.gpuPwrLevel == GPU_PWRLEVEL) {
            return new Item("GPU 功耗档", "= " + GPU_PWRLEVEL, actual, STATE_OK);
        }
        if (s.gpuPwrLevel < GPU_PWRLEVEL) {
            // pwrlevel 数字越小频率越高 → 比目标更费电
            return new Item("GPU 功耗档", "= " + GPU_PWRLEVEL, actual, STATE_OVER);
        }
        return new Item("GPU 功耗档", "= " + GPU_PWRLEVEL, actual, STATE_OK);
    }

    public static int stateColor(int state) {
        switch (state) {
            case STATE_OK:   return 0xFF34C759; // ok 绿
            case STATE_OVER: return 0xFFFF453A; // err 红
            case STATE_OFF:  return 0xFF5A6474; // 灰
            default:         return 0xFFFF9F0A; // warn 橙
        }
    }

    public static String stateText(int state) {
        switch (state) {
            case STATE_OK:   return "已锁定";
            case STATE_OVER: return "未生效";
            case STATE_OFF:  return "已关闭";
            default:         return "未知";
        }
    }
}