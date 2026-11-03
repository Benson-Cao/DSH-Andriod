package com.dsh.harness.mobile.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 断点 · 响应式尺寸（v26 方案 B）
 *
 * ## 为什么自实现而不是 material3-window-size-class
 * 该 artifact 不在本机 Gradle 离线缓存里，而本工程的硬约束是
 * 「依赖已缓存、离线可构建、绝不 gradlew clean」。为了三档断点去新增一个
 * 依赖，等于拿"能离线出包"这条命去换官方 API —— 不划算。
 * 断点规则只有三行，自己算更可控，也省掉 material3 版本对齐问题。
 *
 * ## 阈值（dp，与 Material3 WindowSizeClass 对齐）
 *   Compact  < 600    手机竖屏（实测主流机型 360~430dp）
 *   Medium   600~840 小平板、折叠屏展开态
 *   Expanded > 840    大平板、横屏
 *
 * ## 与 v25 的关系
 * Compact 档的所有令牌值都让布局与 v25 **完全一致**（contentMax 取 1000dp，
 * 手机屏宽远小于它 → widthIn 不触发），所以普通手机用户零感知，
 * 只有大屏/折叠屏会变。
 */
enum class DshWidth { Compact, Medium, Expanded }

/** 屏幕宽 dp → 断点档位。抽成纯函数便于单测与预览。 */
fun widthClassOf(screenWidthDp: Int): DshWidth = when {
    screenWidthDp < 600 -> DshWidth.Compact
    screenWidthDp < 840 -> DshWidth.Medium
    else -> DshWidth.Expanded
}

/** 由 DshTheme 在根部下发；未包裹时退化为 Compact（= 现有行为）。 */
val LocalDshWidth = compositionLocalOf { DshWidth.Compact }

/**
 * 响应式尺寸令牌。大屏收窄内容列、列表变多列；手机行为不变。
 * 用法：`GridCells.Fixed(DshDimens.listColumns)`、`widthIn(max = DshDimens.contentMax)`。
 */
object DshDimens {
    /** 当前档位 */
    val width: DshWidth @Composable get() = LocalDshWidth.current

    /**
     * 内容区最大宽度 —— 收窄居中是大屏观感的关键：
     * 不加约束时内容会在折叠屏展开态被拉到 800+dp 宽，一行文字横跨整屏很难读。
     */
    val contentMax: Dp @Composable get() = when (LocalDshWidth.current) {
        // 手机屏宽（约 360~430）永远小于 1000 → widthIn 不触发，行为与 v25 一致
        DshWidth.Compact -> 1000.dp
        DshWidth.Medium -> 640.dp
        DshWidth.Expanded -> 720.dp
    }

    /** 列表/网格列数。S21 市场（2953 条）是最大受益场景。 */
    val listColumns: Int @Composable get() = when (LocalDshWidth.current) {
        DshWidth.Compact -> 1
        DshWidth.Medium -> 2
        DshWidth.Expanded -> 3
    }

    /** 页面左右边距：随屏宽加大留白。 */
    val pageH: Dp @Composable get() = when (LocalDshWidth.current) {
        DshWidth.Compact -> Space.lg      // 16
        DshWidth.Medium -> Space.xl       // 24
        DshWidth.Expanded -> 32.dp
    }
}

// 注：本文件曾提供 `designZoom()`（按设计稿预览器的「整画布缩放系数」放大固定高度令牌）。
// 已删除 —— 那是设计稿*预览器*把 375px 画板铺满屏的预览行为，不是 App 该遵循的布局契约。
// 真机 App 统一按「1 设计 px ≈ 1 dp」落地：设计稿 `.btn{height:46px}` = App 的
// `Metrics.BTN_H = 46.dp`，与 App 内其它所有按钮一致（411~430dp 机器上其它按钮用户已确认
// "没问题"）。footer 按钮已回退到默认 46dp，见 Home.kt 各 Sheet footer 注释。
// 若日后确实需要整 App 按比例缩放（非仅 footer），应在此统一提供并应用到全部尺寸令牌，
// 而不是只放大某一种控件——否则会出现这次的"footer 比其它按钮高"不一致。