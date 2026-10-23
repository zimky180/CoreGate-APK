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
 */
@Immutable
data class AppSettings(
    val colorMode: ColorMode = ColorMode.SYSTEM,
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
