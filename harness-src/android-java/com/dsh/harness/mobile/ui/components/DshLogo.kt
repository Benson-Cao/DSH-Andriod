package com.dsh.harness.mobile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.dsh.harness.mobile.ui.theme.LocalDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser

/**
 * v22 修订版品牌标（2026-10-24 起）。
 *
 * ⚠️ **这不是 bolt 图标 + 渐变方块**——那是 v22 初版与更早版本的做法。
 * v22 修订版（`DeepSeekHarness手机端设计稿-v22.html`，Downloads 版，3430 行）把
 * `.brandmark .bm` 改成 **26px 透明容器 + 内嵌手绘 SVG**：
 *
 * ```html
 * <span class="bm"><svg viewBox="0 0 40 40" width="100%" height="100%">
 *   <path d="M4 20c0-7 6-12 14-12 …" fill="#79CFF6" stroke="#0E1B33" stroke-width="2.4" stroke-linejoin="round"/>
 *   <path d="M6 22c2 4 7 6 12 6 …" fill="#D6F3FF"/>
 *   <circle cx="15" cy="16.5" r="1.9" fill="#0E1B33"/>
 *   <path d="M8 23.5c2 2.2 5 3.2 8 3.2" stroke="#0E1B33" stroke-width="1.6" fill="none" stroke-linecap="round"/>
 * </svg></span>
 * ```
 *
 * 实现要点：
 * - **路径数据必须与设计稿逐字一致**，所以用 `PathParser.createPathFromPathData()`
 *   解析 SVG 原始 path 字符串，再 `asComposePath()` 转 Compose Path，
 *   避免手抄 SVG 指令时抄错（手抄 cubic 相对指令极容易错）。
 * - viewBox 是 40×40，容器是 26dp → 用 `scale(26/40)` 归一化，不写死坐标。
 * - **深色主题适配**：原描边 `#0E1B33`（深藏蓝）在深色底上会糊成一片。
 *   按设计稿意图「描边是轮廓不是装饰」，深色下把描边换成 `--ink` 档的浅色，
 *   保持形状可辨；填充色 `#79CFF6` / `#D6F3FF` 在深浅底上都成立，不动。
 */
@Composable
fun DshLogo(
    modifier: Modifier = Modifier,
    /** 画布边长；设计稿 .bm 为 26px */
    size: Dp = 26.dp,
) {
    val dark = LocalDarkTheme.current
    // 描边色：浅色底用设计稿的深藏蓝，深色底换浅色（否则轮廓消失）
    val stroke = if (dark) Color(0xFFD6F3FF) else Color(0xFF0E1B33)
    // 缓存解析结果：path 数据是常量，没理由每次重组都解析一遍
    val body = remember { PathParser.createPathFromPathData(BODY_D)?.asComposePath() }
    val belly = remember { PathParser.createPathFromPathData(BELLY_D)?.asComposePath() }
    val mouth = remember { PathParser.createPathFromPathData(MOUTH_D)?.asComposePath() }

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val k = this.size.minDimension / VB.toFloat()
            drawLogoPath(body, Color(0xFF79CFF6), stroke, k, strokeW = 2.4f)
            drawLogoPath(belly, Color(0xFFD6F3FF), null, k)
            // 眼睛：绝对坐标 circle(cx=15, cy=16.5, r=1.9)
            drawCircle(
                color = stroke,
                radius = 1.9f * k,
                center = androidx.compose.ui.geometry.Offset(15f * k, 16.5f * k),
            )
            drawLogoPath(mouth, Color.Transparent, stroke, k, strokeW = 1.6f, round = true)
        }
    }
}

/** 把一条已解析的 path 按归一化系数画出来；[fillColor] 为 null 表示只描边。 */
private fun DrawScope.drawLogoPath(
    path: Path?,
    fillColor: Color?,
    strokeColor: Color?,
    k: Float,
    strokeW: Float = 0f,
    round: Boolean = false,
) {
    if (path == null) return
    if (fillColor != null) {
        drawPath(
            path = path,
            color = fillColor,
            alpha = 1f,
            style = androidx.compose.ui.graphics.drawscope.Fill,
        )
    }
    if (strokeColor != null && strokeW > 0f) {
        drawPath(
            path = path,
            color = strokeColor,
            alpha = 1f,
            style = Stroke(
                width = strokeW * k,
                cap = if (round) androidx.compose.ui.graphics.StrokeCap.Round
                else androidx.compose.ui.graphics.StrokeCap.Butt,
                join = if (round) androidx.compose.ui.graphics.StrokeJoin.Round
                else androidx.compose.ui.graphics.StrokeJoin.Miter,
            ),
        )
    }
}

/** 设计稿 SVG 的 viewBox 边长 */
private const val VB = 40

// —— 以下三段 path 数据与设计稿逐字一致，勿"顺手简化" ——

/** 主体（含尾鳍）：fill #79CFF6 + stroke #0E1B33 2.4 round join */
private const val BODY_D =
    "M4 20c0-7 6-12 14-12 8 0 13 4 13 3 0 0 3-3 6-3 0 4-2 6-2 6 1 3 1 6-1 9-3 5-9 7-16 7C11 30 4 27 4 20Z"

/** 腹部高光：fill #D6F3FF，无描边 */
private const val BELLY_D =
    "M6 22c2 4 7 6 12 6 5 0 9-2 11-5-3 1-7 2-11 1-4-1-9-2-12-2Z"

/** 嘴线：stroke #0E1B33 1.6 round cap，无填充 */
private const val MOUTH_D = "M8 23.5c2 2.2 5 3.2 8 3.2"

/** 眼睛：circle(cx=15, cy=16.5, r=1.9)，fill 用描边色 */
