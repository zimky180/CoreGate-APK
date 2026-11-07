package com.zimky.coregateUS.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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

/** 界面主题独立保存在 App 私有偏好中，默认动态取色与绿色强调色。 */
@Immutable
data class AppSettings(
    val colorMode: ColorMode = ColorMode.MONET_SYSTEM,
    val keyColor: Int = 0xff43a047.toInt(),
    val gravityGlow: Boolean = true,
    val waterRipples: Boolean = true,
    val skipStartupIntro: Boolean = false,
    val paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    val colorSpec: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2021,
) {
    fun save(context: android.content.Context) {
        context.getSharedPreferences("appearance", 0).edit()
            .putString("mode", colorMode.name).putInt("keyColor", keyColor)
            .putBoolean("gravityGlow", gravityGlow)
            .putBoolean("waterRipples", waterRipples)
            .putBoolean("skipStartupIntro", skipStartupIntro).apply()
    }
    companion object {
        fun load(context: android.content.Context): AppSettings {
            val prefs = context.getSharedPreferences("appearance", 0)
            val mode = runCatching { ColorMode.valueOf(prefs.getString("mode", "MONET_SYSTEM")!!) }
                .getOrDefault(ColorMode.MONET_SYSTEM)
            return AppSettings(colorMode = mode, keyColor = prefs.getInt("keyColor", 0xff43a047.toInt()),
                gravityGlow = prefs.getBoolean("gravityGlow", true),
                waterRipples = prefs.getBoolean("waterRipples", true),
                skipStartupIntro = prefs.getBoolean("skipStartupIntro", false))
        }
    }
}

val LocalAppSettings = androidx.compose.runtime.staticCompositionLocalOf { AppSettings() }
val LocalSetAppSettings = androidx.compose.runtime.staticCompositionLocalOf<(AppSettings) -> Unit> { {} }

/**
 * 当前是否处于深色主题。
 *
 * FloatingBottomBar 用它区分明暗，从而决定阴影透明度、指示器底色和高光强度。
 * KSU 里这是一个 @Composable 函数，放在 ui/theme 下；这里保持同一语义。
 */
@Composable
fun isInDarkTheme(): Boolean {
    val mode = LocalAppSettings.current.colorMode
    return mode.isDark || (mode.isSystem && androidx.compose.foundation.isSystemInDarkTheme())
}