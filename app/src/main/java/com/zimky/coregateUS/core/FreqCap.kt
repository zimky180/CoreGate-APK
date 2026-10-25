package com.zimky.coregateUS.core

import kotlin.math.abs

/**
 * FreqCap —— 限频实况判定。
 *
 * 对齐 WebUI 的 refreshFreqCap：拿实测值分别与目标值比对，
 * 给出「已锁定 / 未生效 / 超标」三种结论。
 *
 * v2.2.4 起目标值可自定义：由 [Targets] 承载，界面调用方从 CoreConfig 读，
 * 未自定义（0）时回退到下面的内置默认，保证与 service.sh 的默认值始终一致：
 *   CAP_LITTLE_MHZ = 2266
 *   CAP_BIG_MHZ    = 1651
 *   CAP_GPU_MHZ    = 500
 *   CAP_GPU_PWRLEVEL = 13
 */
object FreqCap {

    /** 内置默认阈值，与 service.sh 的 CAP_* 兜底值严格对应。 */
    const val LITTLE_MHZ = 2266
    const val BIG_MHZ = 1651
    const val GPU_MHZ = 500
    const val GPU_PWRLEVEL = 13

    /**
     * 实际生效的目标阈值。
     *
     * 约定：任一字段 <= 0 视为「未自定义」，自动回退到上面的内置默认。
     * pwrlevel 例外——0 是合法的最高性能档，因此用 [hasPwrLevel] 单独标记是否已设置，
     * 避免「用户设成 0」被误当成「没设置」。
     */
    data class Targets(
        val littleMhz: Int = 0,
        val bigMhz: Int = 0,
        val gpuMhz: Int = 0,
        val gpuPwrLevel: Int = 0,
        /** 是否显式设置过 pwrlevel（区分「设成 0」与「没设」）。 */
        val hasPwrLevel: Boolean = false
    ) {
        val effLittle: Int get() = if (littleMhz > 0) littleMhz else LITTLE_MHZ
        val effBig: Int get() = if (bigMhz > 0) bigMhz else BIG_MHZ
        val effGpu: Int get() = if (gpuMhz > 0) gpuMhz else GPU_MHZ
        val effPwr: Int get() = if (hasPwrLevel) gpuPwrLevel else GPU_PWRLEVEL

        companion object {
            /** 全部走默认。 */
            val DEFAULT = Targets()

            /** 由 CoreConfig 构造：前三项为 0、pwrlevel 为 -1 即视为未自定义。 */
            fun from(cfg: CoreConfig): Targets = Targets(
                littleMhz = cfg.capLittleMhz,
                bigMhz = cfg.capBigMhz,
                gpuMhz = cfg.capGpuMhz,
                gpuPwrLevel = if (cfg.capGpuPwrLevel >= 0) cfg.capGpuPwrLevel else 0,
                // -1 是「没设置过」的哨兵；>=0 说明用户存过真实档位，0 也要照采纳，
                // 与 service.sh 的 `[ "$_v" -ge 0 ]` 判定完全对齐。
                hasPwrLevel = cfg.capGpuPwrLevel >= 0
            )
        }
    }

    /** 单项判定结果。 */
    data class Item(
        /** "小核簇" */
        val name: String,
        /** "≤ 2266 MHz" */
        val target: String,
        /** "2266 MHz" */
        val actual: String,
        /** STATE_OK / STATE_OFF / STATE_OVER / STATE_UNKNOWN */
        val state: Int
    )

    /** 已按目标锁住 */
    const val STATE_OK = 0

    /** 限频开关关着 */
    const val STATE_OFF = 1

    /** 实测超过目标（没生效 / 被别的模块改了） */
    const val STATE_OVER = 2

    /** 读不到 */
    const val STATE_UNKNOWN = 3

    /** 容差：内核会把写入值对齐到最近档位，差个几 MHz 算已生效。 */
    private const val TOL_KHZ = 2000L

    fun judgeLittle(s: SysProbe.Snapshot, limitOn: Boolean, t: Targets = Targets.DEFAULT): Item {
        val mhz = t.effLittle
        val targetKhz = mhz * 1000L
        val target = "≤ $mhz MHz"
        val actual = SysProbe.fmtMhz(s.littleMaxCur)
        if (s.littleMaxCur <= 0) return Item("小核簇", target, "读不到", STATE_UNKNOWN)
        if (!limitOn) return Item("小核簇", target, actual, STATE_OFF)
        if (abs(s.littleMaxCur - targetKhz) <= TOL_KHZ) return Item("小核簇", target, actual, STATE_OK)
        if (s.littleMaxCur > targetKhz) return Item("小核簇", target, actual, STATE_OVER)
        // 比目标还低：说明被锁得更狠，也算生效
        return Item("小核簇", target, actual, STATE_OK)
    }

    fun judgeBig(s: SysProbe.Snapshot, limitOn: Boolean, t: Targets = Targets.DEFAULT): Item {
        val mhz = t.effBig
        val targetKhz = mhz * 1000L
        val target = "≤ $mhz MHz"
        val actual = SysProbe.fmtMhz(s.bigMaxCur)
        if (s.bigMaxCur <= 0) return Item("大核簇", target, "读不到", STATE_UNKNOWN)
        if (!limitOn) return Item("大核簇", target, actual, STATE_OFF)
        if (abs(s.bigMaxCur - targetKhz) <= TOL_KHZ) return Item("大核簇", target, actual, STATE_OK)
        if (s.bigMaxCur > targetKhz) return Item("大核簇", target, actual, STATE_OVER)
        return Item("大核簇", target, actual, STATE_OK)
    }

    fun judgeGpu(s: SysProbe.Snapshot, limitOn: Boolean, t: Targets = Targets.DEFAULT): Item {
        val mhz = t.effGpu
        val targetHz = mhz * 1000000L
        val target = "≤ $mhz MHz"
        val actual = SysProbe.fmtGpu(s.gpuMax)
        if (s.gpuMax <= 0) return Item("GPU", target, "读不到", STATE_UNKNOWN)
        if (!limitOn) return Item("GPU", target, actual, STATE_OFF)
        // GPU 写入会 +800000 对齐，实测会是 500000000
        val tol = 2000000L
        if (abs(s.gpuMax - targetHz) <= tol) return Item("GPU", target, actual, STATE_OK)
        if (s.gpuMax > targetHz) return Item("GPU", target, actual, STATE_OVER)
        return Item("GPU", target, actual, STATE_OK)
    }

    fun judgePwr(s: SysProbe.Snapshot, limitOn: Boolean, t: Targets = Targets.DEFAULT): Item {
        val pwr = t.effPwr
        val target = "= $pwr"
        val actual = if (s.gpuPwrLevel < 0) "读不到" else s.gpuPwrLevel.toString()
        if (s.gpuPwrLevel < 0) return Item("GPU 功耗档", target, actual, STATE_UNKNOWN)
        if (!limitOn) return Item("GPU 功耗档", target, actual, STATE_OFF)
        if (s.gpuPwrLevel == pwr) return Item("GPU 功耗档", target, actual, STATE_OK)
        // pwrlevel 数字越小频率越高 → 比目标更费电
        if (s.gpuPwrLevel < pwr) return Item("GPU 功耗档", target, actual, STATE_OVER)
        return Item("GPU 功耗档", target, actual, STATE_OK)
    }

    fun stateColor(state: Int): Int = when (state) {
        STATE_OK -> 0xFF34C759.toInt()   // ok 绿
        STATE_OVER -> 0xFFFF453A.toInt() // err 红
        STATE_OFF -> 0xFF5A6474.toInt()  // 灰
        else -> 0xFFFF9F0A.toInt()       // warn 橙
    }

    fun stateText(state: Int): String = when (state) {
        STATE_OK -> "已锁定"
        STATE_OVER -> "未生效"
        STATE_OFF -> "已关闭"
        else -> "未知"
    }
}