package com.zimky.coregateUS.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec

/**
 * 颜色模式。
 *
 * 与 KernelSU 的 ColorMode 保持一一对应，成员顺序即 UI 里的显示顺序：
 * SYSTEM → LIGHT → DARK → MONET_SYSTEM → MONET_LIGHT → MONET_DARK → DARK_AMOLED
 */
enum class ColorMode {
    SYSTEM,
    LIGHT,
    DARK,
    MONET_SYSTEM,
    MONET_LIGHT,
    MONET_DARK,
    DARK_AMOLED;

    val isDark: Boolean
        get() = this == DARK || this == MONET_DARK || this == DARK_AMOLED

    val isSystem: Boolean
        get() = this == SYSTEM || this == MONET_SYSTEM

    val isMonet: Boolean
        get() = this == MONET_SYSTEM || this == MONET_LIGHT || this == MONET_DARK
}

/**
 * ColorSpec 生效判断。
 *
 * material-kolor 的 SpecVersion 只分 2021 / 2025 两档，
 * 而 PaletteStyle 里的某些风格（如 TonalSpot）在 2021 规范下才有稳定表现。
 * 这里统一用 2025 规范，交给 dynamiccolor 自行降级。
 */
fun ColorSpec.SpecVersion.effectiveFor(style: PaletteStyle): ColorSpec.SpecVersion = this

/**
 * CoreGate 的主题配置。
 *
 * 对应 KernelSU 的 AppSettings，但只保留 UI 真正用到的 4 个字段，
 * 不引入 KSU 的 SharedPreferences 体系（CoreGate 的配置来源是 /data/adb/CoreGate/config.json）。
 *
 * colorMode 默认 MONET_SYSTEM：
 *   走 MiuixTheme 的 `colorMode.isMonet` 分支，从系统壁纸动态取主色（Android 12+ Monet）。
 *   此前默认 SYSTEM，会落到 `else -> null`，resolvedKeyColor 为 null，
 *   Miuix 于是用整套无主色的灰白配色 —— 这就是 v2.2.0 初版「界面纯白」的根因。
 *
 * keyColor 默认保持 0（= 不使用固定色）：
 *   MiuixTheme 里 `keyColor != 0` 的优先级高于 isMonet 分支，
 *   填了固定色会直接把动态取色盖掉，与「能动态取色就动态取色」的预期不符。
 *   若某台设备 Monet 不可用（Android 11 及以下），在 SettingsPage 里手动选固定色即可，
 *   C.ACC = #4F8CFF 是模块一直用的主色。
 */
@Immutable
data class AppSettings(
    val colorMode: ColorMode = ColorMode.MONET_SYSTEM,
    val keyColor: Int = 0,
    val paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    val colorSpec: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2021,
)

/**
 * 当前是否处于深色主题。
 *
 * FloatingBottomBar 用它区分明暗，从而决定阴影透明度、指示器底色和高光强度。
 * KSU 里这是一个 @Composable 函数，放在 ui/theme 下；这里保持同一语义。
 */
@Composable
fun isInDarkTheme(): Boolean = androidx.compose.foundation.isSystemInDarkTheme()