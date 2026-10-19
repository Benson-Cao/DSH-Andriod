package com.dsh.harness.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 令牌下发通道。组件一律从 LocalDshColors / LocalDshDimens 取值，
 * 不要在组件里硬编码颜色 —— 这是"全稿统一改主题"能成立的前提。
 */
val LocalDshColors = compositionLocalOf { LightColors }

/** 主题开关。S14 设置里有"语言与外观"一行，会改这个值。 */
val LocalDarkTheme = compositionLocalOf { false }

private fun schemeOf(c: DshColors) = if (c.bg == LightColors.bg) {
    lightColorScheme(
        primary = c.brand, onPrimary = Color.White,
        background = c.bg, onBackground = c.ink,
        surface = c.card, onSurface = c.ink,
        surfaceVariant = c.line2, onSurfaceVariant = c.ink2,
        outline = c.line, outlineVariant = c.line2,
        error = c.danger, onError = Color.White,
    )
} else {
    darkColorScheme(
        primary = c.brand, onPrimary = Color.White,
        background = c.bg, onBackground = c.ink,
        surface = c.card, onSurface = c.ink,
        surfaceVariant = c.line2, onSurfaceVariant = c.ink2,
        outline = c.line, outlineVariant = c.line2,
        error = c.danger, onError = Color.White,
    )
}

/**
 * App 根主题。
 * @param dark null = 跟随系统；非 null = 强制（设置页切换后由上层持有）。
 */
@Composable
fun DshTheme(
    dark: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val isDark = dark ?: isSystemInDarkTheme()
    val colors = if (isDark) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val win = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            // 状态栏/导航栏图标明暗必须跟主题反着来，否则浅底白字会看不清
            WindowCompat.getInsetsController(win, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    // v26 方案 B：按屏幕宽 dp 算断点并下发。大屏收窄内容列、列表多列；
    // Compact 档（手机）令牌值与 v25 完全一致 → 普通手机零感知。
    val widthClass = widthClassOf(LocalConfiguration.current.screenWidthDp)

    CompositionLocalProvider(
        LocalDshColors provides colors,
        LocalDarkTheme provides isDark,
        LocalDshWidth provides widthClass,
    ) {
        MaterialTheme(
            colorScheme = schemeOf(colors),
            typography = Typography(
                bodyLarge = Type.body,
                titleLarge = Type.title,
                labelLarge = Type.chip,
                labelSmall = Type.meta,
            ),
            content = content,
        )
    }
}

/** 便捷取色：val c = DshTheme.colors */
object DshTheme {
    val colors: DshColors
        @Composable get() = LocalDshColors.current

    /** v26 方案 B：当前断点档位。val w = DshTheme.width */
    val width: DshWidth
        @Composable get() = LocalDshWidth.current
}