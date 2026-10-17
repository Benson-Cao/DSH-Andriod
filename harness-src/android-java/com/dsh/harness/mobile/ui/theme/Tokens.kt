package com.dsh.harness.mobile.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设计系统 · 尺寸与排版令牌
 *
 * 设计稿 §08 原文：
 *   间距阶梯  4 / 8 / 12 / 16 / 24 / 40
 *             "卡片内 16，卡片间 10–12，区块间 24，章节间 40。全稿未使用非阶梯值。"
 *   圆角      8 / 10 / 12 / 14 / 22
 *             "chip 14 · 列表卡 12–14 · Sheet 顶部 22 · 机身 34"
 *   动效      160 / 280 / 500 ms
 *             "微反馈 160，Sheet 与抽屉 280 cubic-bezier(.32,.72,0,1)，入场 500 stagger 60ms"
 *   触控目标  ≥ 32 × 32  "图标按钮 32，开关 42×25，列表行 ≥ 44，主按钮 46"
 *   画板      375 × 812  "状态栏 44，home 区 26，页面左右边距 16（内容宽 343）"
 */

object Space {
    /** 4 —— 芯片内图标与文字的间隙 */
    val xs: Dp = 4.dp
    /** 8 —— 行内元素间隙、chip 上下 */
    val sm: Dp = 8.dp
    /** 10 —— 卡片之间（设计稿允许 10–12 这一档的 10） */
    val smd: Dp = 10.dp
    /** 12 —— 卡片之间另一取值 / 模式网格 gutter */
    val md: Dp = 12.dp
    /** 14 —— 列表行内边距横向、Sheet 标题内边距 */
    val row: Dp = 14.dp
    /** 16 —— 页面左右边距、卡片内主内边距 */
    val lg: Dp = 16.dp
    /** 18 —— Sheet 内容区内边距 */
    val sheet: Dp = 18.dp
    /** 24 —— 区块之间 */
    val xl: Dp = 24.dp
    /** 40 —— 章节之间 */
    val xxl: Dp = 40.dp
}

object Radius {
    /** 8 —— 分段控件内项 */
    val sm: Dp = 8.dp
    /** 10 —— 折叠块、代码块、图标容器 */
    val md: Dp = 10.dp
    /** 12 —— 次按钮 */
    val lg: Dp = 12.dp
    /** 14 —— 列表卡 / chip / 设备卡 */
    val xl: Dp = 14.dp
    /** 22 —— Sheet 顶部圆角 */
    val sheet: Dp = 22.dp
    /** 34 —— 设计稿机身圆角（真机上不存在，仅演示画板用） */
    val device: Dp = 34.dp

    fun r(d: Dp) = RoundedCornerShape(d)
}

object Metrics {
    /** 状态栏高度。设计稿按 iOS 44；真机用 WindowInsets 真实值，见 StatusBar.kt */
    const val STATUS_BAR_H = 44
    /** 顶栏高度 —— 设计稿明确"顶部导航 48" */
    val NAV_H: Dp = 48.dp
    /** home 指示区：设计稿 26 + safe-area */
    val HOME_H: Dp = 26.dp
/** 图标按钮 32（视觉尺寸） */
val IBTN: Dp = 32.dp
    /**
     * 图标按钮触区外扩：设计稿 §11「图标按钮 32，**触区外扩 4**」。
     * 视觉仍是 32dp，但触摸区 = 32 + 4×2 = 40dp。
     * 真机上只给 32dp 会导致顶部返回键几乎点不中（v10 复刻踩过）。
     */
val IBTN_TOUCH_SLOP: Dp = 4.dp
    /** 开关 42×25 */
    val SW_W: Dp = 42.dp
    val SW_H: Dp = 25.dp
    /** 主按钮 46 */
    val BTN_H: Dp = 46.dp
    /** 次按钮 32 */
    val BTN_SM_H: Dp = 32.dp
    /** chip 高 26 */
    val CHIP_H: Dp = 26.dp
    /** 行内迷你 chip 高 17 / 20（设计稿"迷你态 h20 / h17"） */
    val CHIP_MINI_H: Dp = 17.dp
    val CHIP_SM_H: Dp = 20.dp
    /** 列表行：双行 62、单行 48 */
    val ROW_H1: Dp = 48.dp
    val ROW_H2: Dp = 62.dp
    /** 图标容器 36×36 r10 */
    val ICON_BOX: Dp = 36.dp
    /** 侧边栏宽 268 / 375 = 71%，窄屏 min(268, 78%) */
    val DRAWER_W: Dp = 268.dp
    /** Sheet 把手 36×4 */
    val SHEET_GRAB_W: Dp = 36.dp
    val SHEET_GRAB_H: Dp = 4.dp
    /** 页面左右边距（内容宽 = 屏宽 - 32） */
    val PAGE_H: Dp = 16.dp
    /** 基准画板宽 */
    val BOARD_W: Dp = 375.dp
    val BOARD_H: Dp = 812.dp
    /** 模式卡 166×88 */
    val MODE_CARD_W: Dp = 166.dp
    val MODE_CARD_H: Dp = 88.dp
}

/**
 * 动效档位。设计稿只有三档，禁止中间值——
 * 微反馈 160 / Sheet 与抽屉 280 cubic-bezier(.32,.72,0,1) / 入场 500，stagger 60。
 */
object Motion {
    /** 微反馈：开关、chip 选中、图标按下 */
    const val MICRO = 160
    /** Sheet 与抽屉：曲线 cubic-bezier(.32,.72,0,1) */
    const val SHEET = 280
    /** 入场：多元素 stagger */
    const val ENTER = 500
    /** stagger 间隔 */
    const val STAGGER = 60
    /** 路由切换：设计稿没单列，取微反馈与 Sheet 之间的 200ms（预览用） */
    const val ROUTE = 200

    /**
     * Compose 不支持任意 cubic-bezier 的通用入口，但 Easing 有工厂：
     * 标准三次贝塞尔 (0.32,0.72,0,1) 需自己解控制点。
     * 这里用 CubicBezierEasing 直接构造（Compose 1.7+ 提供 public 构造）。
     */
    val SheetEasing = androidx.compose.animation.core.CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    /** 路由进出场：入场用减速（快进慢停），出场用加速，两者合成"穿越感" */
    val RouteEnter = androidx.compose.animation.core.tween<Float>(
        durationMillis = ROUTE,
        easing = androidx.compose.animation.core.FastOutSlowInEasing,
    )
    val RouteExit = androidx.compose.animation.core.tween<Float>(
        durationMillis = ROUTE,
        easing = androidx.compose.animation.core.FastOutLinearInEasing,
    )

    /** 列表入场：设计稿「入场 500ms，stagger 60ms」 */
    fun <T> staggeredEnter(initialIndex: Int = 0): androidx.compose.animation.core.TweenSpec<Float> =
        androidx.compose.animation.core.tween(
            durationMillis = ENTER,
            delayMillis = (initialIndex * STAGGER).coerceAtMost(300),
            easing = androidx.compose.animation.core.FastOutSlowInEasing,
        )
}

/**
 * 字体阶梯。设计稿 §08 色板型排：
 *   22 / Bold   H1 品牌位 · 22/-0.4
 *   16 / Semi   页面标题 · 16
 *   14 / Regular 正文 · 行高 1.62
 *   12 / Sec    次要说明
 *   11 / Meta   时间 · 指标注脚
 *   11.5 / Mono 工具名 · 路径 · 命令
 */
object Type {
    private val align = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )

    /** 22 Bold 字距 -0.4 —— Hero「探索未至之境」实际用 23，设计稿 S3 标注为 23px */
    val h1 = TextStyle(
        fontSize = 22.sp, fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp, lineHeightStyle = align,
    )
    val hero = TextStyle(
        fontSize = 23.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp, lineHeightStyle = align,
    )
    /** 16 Semi —— 页面标题 / Sheet 标题 */
    val title = TextStyle(
        fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeightStyle = align,
    )
    /** 14 Regular 行高 1.62 —— 正文 */
    val body = TextStyle(
        fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 22.7.sp, lineHeightStyle = align,
    )
    /** 14 / 550 —— 列表行主标题 */
    val rowTitle = TextStyle(
        fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeightStyle = align,
    )
    /** 14 Semi —— 首页输入提示文字 */
    val input = TextStyle(
        fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp, lineHeightStyle = align,
    )
    /** 14.5 Bold —— 品牌字标 deepseek */
    val brandmark = TextStyle(
        fontSize = 14.5.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp, lineHeightStyle = align,
    )
    /** 12 —— 次要说明 / chip */
    val sec = TextStyle(
        fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeightStyle = align,
    )
    /** 11.5 / 700 字距 .6 —— 区块标题 sec-t */
    val secTitle = TextStyle(
        fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, lineHeightStyle = align,
    )
    /** 11 —— 时间 / 指标注脚 */
    val meta = TextStyle(
        fontSize = 11.sp, fontWeight = FontWeight.Normal, lineHeightStyle = align,
    )
    /** 11.5 / 600 —— 状态栏 */
    val statusBar = TextStyle(
        fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, lineHeightStyle = align,
    )
    /** 12.5 —— chip 文字 / 胶囊 / 分段项 */
    val chip = TextStyle(
        fontSize = 12.5.sp, fontWeight = FontWeight.Normal, lineHeightStyle = align,
    )
    /** 14 / 500 —— tab */
    val tab = TextStyle(
        fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeightStyle = align,
    )
    /** 14 / 650 —— tab 选中 */
    val tabOn = TextStyle(
        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeightStyle = align,
    )
    /** 15 / 600 —— 主按钮 */
    val btn = TextStyle(
        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeightStyle = align,
    )
    /** 12.5 / 600 —— 次按钮 */
    val btnSm = TextStyle(
        fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, lineHeightStyle = align,
    )
    /** 11.5 Mono —— 命令 / 路径 / 工具名，行高 1.7 */
    val mono = TextStyle(
        fontSize = 11.5.sp, fontFamily = FontFamily.Monospace,
        lineHeight = 19.5.sp, lineHeightStyle = align,
    )
}