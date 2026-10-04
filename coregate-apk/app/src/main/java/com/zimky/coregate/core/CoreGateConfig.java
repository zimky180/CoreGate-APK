package com.zimky.coregate.core;

import org.json.JSONObject;

/**
 * CoreGateConfig —— 模块配置读写（/data/adb/CoreGate/config.json）。
 *
 * 字段与模块 service.sh 的 cfg / cfg_bool 解析完全对齐：
 *   auto_lock_count   int    默认 1     锁核数量 1~4
 *   poll_interval     int    默认 30    检测间隔秒 5~600
 *   limit_freq        bool   默认 false 日常限频总开关
 *   capture_boost     bool   默认 false 录屏/相机自动恢复
 *   lock_on_screen_off bool  默认 true  息屏自动锁核
 *   keep_on_charge    bool   默认 true  充电时仍锁核
 *   lock_little_mhz   int    默认 2266  小核限频档
 *   lock_big_mhz      int    默认 1651  大核限频档
 *   lock_gpu_mhz      int    默认 500   GPU 限频档
 *   packages          string 目标游戏包名（换行分隔）
 *
 * 设计原则：读不到文件时全部回退默认值，不抛异常、不写坏文件。
 */
public final class CoreGateConfig {

    public static final String DIR = "/data/adb/CoreGate";
    public static final String FILE = DIR + "/config.json";

    // ---- 默认值（与 service.sh / WebUI 一致） ----
    public static final int DEF_LOCK_COUNT = 1;
    public static final int DEF_POLL = 30;
    public static final boolean DEF_LIMIT_FREQ = false;
    public static final boolean DEF_CAPTURE_BOOST = false;
    public static final boolean DEF_SCREEN_OFF = true;
    public static final boolean DEF_KEEP_CHARGE = true;
    public static final int DEF_LITTLE = 2266;
    public static final int DEF_BIG = 1651;
    public static final int DEF_GPU = 500;

    // ---- 字段 ----
    public int autoLockCount = DEF_LOCK_COUNT;
    public int pollInterval = DEF_POLL;
    public boolean limitFreq = DEF_LIMIT_FREQ;
    public boolean captureBoost = DEF_CAPTURE_BOOST;
    public boolean lockOnScreenOff = DEF_SCREEN_OFF;
    public boolean keepOnCharge = DEF_KEEP_CHARGE;
    public int littleMhz = DEF_LITTLE;
    public int bigMhz = DEF_BIG;
    public int gpuMhz = DEF_GPU;
    public String packages = "";

    // ---- 解析 ----

    /**
     * 从 JSON 文本解析，缺字段落默认值。
     */
    public static CoreGateConfig parse(String json) {
        CoreGateConfig c = new CoreGateConfig();
        if (json == null) return c;
        try {
            JSONObject o = new JSONObject(json);
            c.autoLockCount = clamp(o.optInt("auto_lock_count", DEF_LOCK_COUNT), 1, 4);
            c.pollInterval = clamp(o.optInt("poll_interval", DEF_POLL), 5, 600);
            c.limitFreq = o.optBoolean("limit_freq", DEF_LIMIT_FREQ);
            c.captureBoost = o.optBoolean("capture_boost", DEF_CAPTURE_BOOST);
            c.lockOnScreenOff = o.optBoolean("lock_on_screen_off", DEF_SCREEN_OFF);
            c.keepOnCharge = o.optBoolean("keep_on_charge", DEF_KEEP_CHARGE);
            c.littleMhz = o.optInt("lock_little_mhz", DEF_LITTLE);
            c.bigMhz = o.optInt("lock_big_mhz", DEF_BIG);
            c.gpuMhz = o.optInt("lock_gpu_mhz", DEF_GPU);
            c.packages = o.optString("packages", "");
        } catch (Exception ignored) {
            // 解析失败 -> 全默认
        }
        return c;
    }

    /**
     * 序列化为 JSON 文本（美观缩进）。
     */
    public String toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("auto_lock_count", autoLockCount);
            o.put("poll_interval", pollInterval);
            o.put("limit_freq", limitFreq);
            o.put("capture_boost", captureBoost);
            o.put("lock_on_screen_off", lockOnScreenOff);
            o.put("keep_on_charge", keepOnCharge);
            o.put("lock_little_mhz", littleMhz);
            o.put("lock_big_mhz", bigMhz);
            o.put("lock_gpu_mhz", gpuMhz);
            o.put("packages", packages == null ? "" : packages);
            return o.toString(2);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}