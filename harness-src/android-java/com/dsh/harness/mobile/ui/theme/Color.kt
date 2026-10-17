package com.dsh.harness.mobile.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * DeepSeek Harness 设计系统 · 颜色令牌
 *
 * 严格对齐 v10 设计稿（prototype-dsh-mobile-ui-v10.html）的 CSS 变量表：
 *   body[data-theme="light"] .screen { --bg:#F4F6FA; --card:#FFFFFF; ... }
 *   body[data-theme="dark"]  .screen { --bg:#0E1117; --card:#171C25; ... }
 *
 * 设计稿原文对颜色的约束：
 *   "品牌主色取 DeepSeek 蓝，辅助紫用于子代理 / PTC 等「多智能体」语义；
 *    绿 / 琥珀 / 红专用于状态，不作为装饰色。"
 * 所以下面状态色**不允许**拿来做渐变或点缀，只用于状态语义。
 */

// ============ 品牌色（两套主题共用，不随主题变） ============
val BrandLight = Color(0xFF4D6BFE)      // 设计稿 §08 色板 · Brand 主色
val BrandDark = Color(0xFF5C78FF)
val Brand2Light = Color(0xFF3A55E0)     // --brand2，hover/按下态
val Brand2Dark = Color(0xFF4A64F0)
val PurpleLight = Color(0xFF7A5AF8)     // Agent 辅助紫
val PurpleDark = Color(0xFF9575FF)
val Purple2Light = Color(0xFF7A5AF8)    // --purple 深浅同值（设计稿未区分）

// ============ 状态四色（两套主题共用） ============
val OkLight = Color(0xFF0E9F6E)         // Success 成功
val OkDark = Color(0xFF25B37F)
val WarnLight = Color(0xFFC97A00)       // Warn 待确认
val WarnDark = Color(0xFFE0A33B)
val DangerLight = Color(0xFFE02E3A)     // Danger 失败
val DangerDark = Color(0xFFF0606B)

// ============ 品牌中性色（固定不随主题，用于看板上层） ============
val BoardBg = Color(0xFF0B0E14)
val BoardPanel = Color(0xFF12161F)
val BoardPanel2 = Color(0xFF171C27)
val BoardLine = Color(0xFF242B39)
val BoardText = Color(0xFFE7EBF3)
val BoardDim = Color(0xFF8A93A6)
val BoardDim2 = Color(0xFF5D6678)

/**
 * 主题无关的语义令牌集合。
 * Compose 没有 CSS 那样的变量级联，用一个 data class 承载，由 CompositionLocal 下发。
 */
data class DshColors(
    // 背景三层：页面底 → 卡片 → 卡片内次级块
    val bg: Color,
    val card: Color,
    val card2: Color,
    // 文字三层：主 → 次 → 弱（meta）
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    // 分隔线两层
    val line: Color,
    val line2: Color,
    // 品牌
    val brand: Color,
    val brand2: Color,
    // 品牌浅底（chip.on / 图标容器）
    val soft: Color,
    // 代码块底
    val code: Color,
    // 状态
    val ok: Color,
    val warn: Color,
    val danger: Color,
    val purple: Color,
    // 状态栏文字（浅色主题是深色，深色主题是浅色 —— 与 --sb 对应）
    val sb: Color,
    // 半透明底：底部栏、遮罩、用户气泡
    val tabbg: Color,
    val sheet: Color,
    val mask: Color,
    val bubble: Color,
) {
    /** 状态色的 12~13% 淡底，用于 chip.ok / chip.warn / chip.danger / chip.purple */
    fun okSoft(): Color = ok.copy(alpha = 0.12f)
    fun warnSoft(): Color = warn.copy(alpha = 0.13f)
    fun dangerSoft(): Color = danger.copy(alpha = 0.12f)
    fun purpleSoft(): Color = purple.copy(alpha = 0.13f)

    /** mono 字色：设计稿 --brand 在代码块里偏亮（色板标 #A9B7FF） */
    val mono: Color get() = brand.copy(alpha = 0.95f)
}

val LightColors = DshColors(
    bg = Color(0xFFF4F6FA),
    card = Color(0xFFFFFFFF),
    card2 = Color(0xFFFFFFFF),
    ink = Color(0xFF161922),
    ink2 = Color(0xFF5B6373),
    ink3 = Color(0xFF98A0B0),
    line = Color(0xFFEBEEF4),
    line2 = Color(0xFFF3F5F9),
    brand = BrandLight,
    brand2 = Brand2Light,
    soft = Color(0xFFEEF2FF),
    code = Color(0xFFF6F8FC),
    ok = OkLight,
    warn = WarnLight,
    danger = DangerLight,
    purple = PurpleLight,
    sb = Color(0xFF161922),
    tabbg = Color(0xE6FFFFFF),      // rgba(255,255,255,.9)
    sheet = Color(0xFFFFFFFF),
    mask = Color(0x66141820),      // rgba(20,24,32,.4)
    bubble = Color(0xFFEEF2FF),
)

val DarkColors = DshColors(
    bg = Color(0xFF0E1117),
    card = Color(0xFF171C25),
    card2 = Color(0xFF1D232E),
    ink = Color(0xFFECF0F7),
    ink2 = Color(0xFF9BA5B7),
    ink3 = Color(0xFF697386),
    line = Color(0xFF252C38),
    line2 = Color(0xFF1F2531),
    brand = BrandDark,
    brand2 = Brand2Dark,
    soft = Color(0xFF1A2135),
    code = Color(0xFF12161E),
    ok = OkDark,
    warn = WarnDark,
    danger = DangerDark,
    purple = PurpleDark,
    sb = Color(0xFFECF0F7),
    tabbg = Color(0xE613171F),      // rgba(19,23,31,.9)
    sheet = Color(0xFF1B212C),
    mask = Color(0x99000000),      // rgba(0,0,0,.6)
    bubble = Color(0xFF1E2534),
)