package com.dsh.harness.mobile.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * DeepSeek Harness 设计系统 · 颜色令牌
 *
 * 严格对齐 v23 设计稿（DeepSeekHarness手机端设计稿-v23-官方配色.html）的 CSS 变量表：
 *   body[data-theme="light"] .screen { --bg:#FFFFFF; --card:#FFFFFF; --ink:#1F1F1F; ... }
 *   body[data-theme="dark"]  .screen { --bg:#151517; --card:#1C1C1F; --ink:#E9EBF0; ... }
 *   .board                   { --board-bg:#101013; --board-panel:#17171B; ... }
 *
 * v23 是「官方配色 + 章节重排」版本，相对旧版（v10/v22）的关键变化：
 *   · 品牌主色从 #5C78FF(暗) 提亮为 #6D86FF，亮色仍 #4D6BFE（官方蓝）
 *   · 状态四色整体换为官方语义色：ok #12B76A / warn #F79009 / danger #F04438 / purple #7A5AF8
 *   · 中性色阶重塑：亮色 bg 由 #F4F6FA → #FFFFFF，暗色 bg 由 #0E1117 → #151517，
 *     ink/line 全部跟随官方灰阶（ink2 #4B5563 / #B4BAC5，line #E8EAF0 / #262A31）
 *
 * 设计稿对颜色的约束（沿用）：
 *   "品牌主色取 DeepSeek 蓝，辅助紫用于子代理 / PTC 等「多智能体」语义；
 *    绿 / 琥珀 / 红专用于状态，不作为装饰色。"
 * 所以状态色**不允许**拿来做渐变或点缀，只用于状态语义。
 */

// ============ 品牌色（两套主题共用，不随主题变） ============
val BrandLight = Color(0xFF4D6BFE)      // 设计稿 官方蓝 · Brand 主色（亮）
val BrandDark = Color(0xFF6D86FF)       // 官方蓝（暗，提亮以保对比）
val Brand2Light = Color(0xFF3A57E8)     // --brand2，hover/按下态（亮）
val Brand2Dark = Color(0xFF5A74F5)      // --brand2（暗）
val PurpleLight = Color(0xFF7A5AF8)     // Agent 辅助紫（亮）
val PurpleDark = Color(0xFF9575FF)      // Agent 辅助紫（暗）
val Purple2Light = Color(0xFF7A5AF8)    // --purple 深浅同值（设计稿未区分）

// ============ 状态四色（两套主题共用） ============
val OkLight = Color(0xFF12B76A)         // Success 成功（官方绿）
val OkDark = Color(0xFF3DD68C)
val WarnLight = Color(0xFFF79009)       // Warn 待确认（官方琥珀）
val WarnDark = Color(0xFFFFB140)
val DangerLight = Color(0xFFF04438)     // Danger 失败（官方红）
val DangerDark = Color(0xFFFF6B5E)

// ============ 品牌中性色（固定不随主题，用于看板上层） ============
// v23 看板主题：--board-bg:#101013 / --board-panel:#17171B / --board-panel2:#1D1D22
val BoardBg = Color(0xFF101013)
val BoardPanel = Color(0xFF17171B)
val BoardPanel2 = Color(0xFF1D1D22)
val BoardLine = Color(0xFF2A2A31)
val BoardText = Color(0xFFE9EBF0)
val BoardDim = Color(0xFFB4BAC5)
val BoardDim2 = Color(0xFF858C99)

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
    /** 状态色的淡底，用于 chip.ok / chip.warn / chip.danger / chip.purple（v23 透明度） */
    fun okSoft(): Color = ok.copy(alpha = 0.12f)
    fun warnSoft(): Color = warn.copy(alpha = 0.13f)
    fun dangerSoft(): Color = danger.copy(alpha = 0.12f)
    fun purpleSoft(): Color = purple.copy(alpha = 0.15f)

    /** mono 字色：设计稿 --brand 在代码块里偏亮（色板标 #A9B7FF 同族） */
    val mono: Color get() = brand.copy(alpha = 0.95f)
}

val LightColors = DshColors(
    bg = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    card2 = Color(0xFFF7F8FA),
    ink = Color(0xFF1F1F1F),
    ink2 = Color(0xFF4B5563),
    ink3 = Color(0xFF8A93A6),
    line = Color(0xFFE8EAF0),
    line2 = Color(0xFFF0F1F5),
    brand = BrandLight,
    brand2 = Brand2Light,
    soft = Color(0xFFEEF2FF),
    code = Color(0xFFF7F8FA),
    ok = OkLight,
    warn = WarnLight,
    danger = DangerLight,
    purple = PurpleLight,
    sb = Color(0xFF1F1F1F),
    tabbg = Color(0xE6FFFFFF),      // rgba(255,255,255,.9)
    sheet = Color(0xFFFFFFFF),
    mask = Color(0x66141820),      // rgba(20,24,32,.4)
    bubble = Color(0xFFEEF2FF),
)

val DarkColors = DshColors(
    bg = Color(0xFF151517),
    card = Color(0xFF1C1C1F),
    card2 = Color(0xFF242427),
    ink = Color(0xFFE9EBF0),
    ink2 = Color(0xFFB4BAC5),
    ink3 = Color(0xFF858C99),
    line = Color(0xFF262A31),
    line2 = Color(0xFF1E2228),
    brand = BrandDark,
    brand2 = Brand2Dark,
    soft = Color(0xFF1E2440),
    code = Color(0xFF131316),
    ok = OkDark,
    warn = WarnDark,
    danger = DangerDark,
    purple = PurpleDark,
    sb = Color(0xFFE9EBF0),
    tabbg = Color(0xE6151517),      // rgba(21,21,23,.9)
    sheet = Color(0xFF1C1C1F),
    mask = Color(0x99000000),      // rgba(0,0,0,.6)
    bubble = Color(0xFF1E2440),
)
