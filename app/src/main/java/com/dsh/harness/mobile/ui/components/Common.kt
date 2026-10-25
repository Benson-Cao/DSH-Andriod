package com.dsh.harness.mobile.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.harness.mobile.ui.theme.*

/**
 * 通用组件库 —— 严格 1:1 对齐 v10 设计稿的 `.screen *` 组件样式表。
 *
 * 复刻原则：**组件的每个尺寸都能在设计稿 CSS 里找到对应数值**，
 * 凡是这里出现的字面量都能对上稿子的一条声明（如 .chip{height:26px;border-radius:14px}）。
 * 这样"改设计稿 → 改这里"是机械动作，不会引入设计外的中间值。
 */

// ============================ Chip ============================

/** chip 语义变体。设计稿有 .chip / .on / .brand / .ok / .warn / .danger / .purple 七种。 */
enum class ChipTone { Neutral, On, Brand, Ok, Warn, Danger, Purple }

@Composable
fun DshChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: ChipTone = ChipTone.Neutral,
    onClick: (() -> Unit)? = null,
    /** 行内迷你态：设计稿 .chip 迷你 h20 / h17 */
    mini: Boolean = false,
    enabled: Boolean = true,
) {
    val c = DshTheme.colors
    val bg: Color = when (tone) {
        ChipTone.Neutral -> c.line2
        ChipTone.On -> c.soft
        ChipTone.Brand -> c.brand
        ChipTone.Ok -> c.okSoft()
        ChipTone.Warn -> c.warnSoft()
        ChipTone.Danger -> c.dangerSoft()
        ChipTone.Purple -> c.purpleSoft()
    }
    val fg: Color = when (tone) {
        ChipTone.Neutral -> c.ink2
        ChipTone.On, ChipTone.Brand -> c.brand
        ChipTone.Brand -> Color.White
        ChipTone.Ok -> c.ok
        ChipTone.Warn -> c.warn
        ChipTone.Danger -> c.danger
        ChipTone.Purple -> c.purple
    }
    // 品牌实心 chip 文字必须是白的
    val finalFg = if (tone == ChipTone.Brand) Color.White else fg

    val h = when {
        mini -> Metrics.CHIP_MINI_H
        else -> Metrics.CHIP_H
    }
    val style = if (mini) Type.meta else Type.chip

    Row(
        modifier = modifier
            .height(if (mini) 20.dp else h)
            .background(bg, RoundedCornerShape(Radius.xl))
            .alpha(if (enabled) 1f else 0.45f)
            .then(
                if (onClick != null)
                    Modifier.clickable(enabled = enabled) { onClick() }
                else Modifier
            )
            .padding(horizontal = if (mini) 5.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (mini) 3.dp else 4.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(if (mini) 11.dp else 13.dp), tint = finalFg)
        }
        Text(text, style = style, color = finalFg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ============================ Card ============================

/** 统一卡片容器：设计稿 .card 的 r16 + 1px 描边 + 内距 11/12。 */
@Composable
fun DshCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 11.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = DshTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xl))
            .background(c.card)
            .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
            .padding(padding),
        content = content,
    )
}

// ============================ Button ============================

enum class BtnTone { Primary, Ghost, Danger }

/**
 * 主按钮 46 / 次按钮 32，圆角 12 / 9。
 * 设计稿明确："一律 white-space:nowrap，杜绝文字折行与相邻按钮顶压"
 * → 这里默认 maxLines=1 且不换行。
 *
 * ⚠️ 设计稿 `.btn{width:100%}`：按钮**默认满宽**。
 * 所以 modifier 的默认值是 fillMaxWidth()；行内并排时调用方传 weight(1f) 即可覆盖。
 * （否则在 Column 里会塌成 wrap 宽度，背景与边框看起来"消失"了。）
 */
@Composable
fun DshBtn(
    text: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    icon: ImageVector? = null,
    tone: BtnTone = BtnTone.Primary,
    small: Boolean = false,
    enabled: Boolean = true,
    /** 设计稿里有 h40 一档（如 S43「添加档位映射」）；默认 null 走 46 / 32 两档 */
    height: Dp? = null,
    /** 覆盖文字/图标色（S48「删除所选」= ghost 底 + danger 字） */
    contentColor: Color? = null,
    onClick: () -> Unit,
) {
    val c = DshTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val bg = when {
        !enabled -> c.line
        tone == BtnTone.Primary -> if (pressed) c.brand2 else c.brand
        else -> if (pressed) c.line2 else c.card
    }
    val fg = contentColor ?: when {
        !enabled -> c.ink3
        tone == BtnTone.Primary -> Color.White
        tone == BtnTone.Danger -> c.danger
        else -> c.ink
    }
    val stroke = when {
        !enabled -> null
        tone == BtnTone.Primary -> null
        else -> BorderStroke(1.dp, c.line)
    }

    Row(
        modifier = modifier
            .height(height ?: if (small) Metrics.BTN_SM_H else Metrics.BTN_H)
            .then(
                if (stroke != null)
                    Modifier.border(stroke, RoundedCornerShape(Radius.lg))
                else Modifier
            )
            .background(bg, RoundedCornerShape(Radius.lg))
            .clickable(enabled = enabled, interactionSource = interaction, indication = null) { onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = if (small) Type.btnSm else Type.btn,
            color = fg,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
        if (icon != null) {
            Spacer(Modifier.width(6.dp))
            Icon(icon, null, Modifier.size(if (small) 15.dp else 18.dp), tint = fg)
        }
    }
}

// ============================ 图标按钮 ============================

/**
 * 设计稿 .ibtn：**视觉** 32×32，圆角 9，hover 底色 line2。
 *
 * ⚠️ v10 复刻真机踩坑（返回键"点不动"）：
 * 设计稿 §11 写的是"图标按钮 32，**触区外扩 4**"——我只实现了 32 的视觉尺寸，
 * 把"触区外扩"这半句漏了。真机上 32dp 见方的小方块在顶部边缘几乎点不中，
 * 表现为"所有二级页的返回键都没反应"。
 * 正解：**视觉 32、触摸 40**（=32+4×2），用 Box 外层放大尺寸承载点击，
 * 内层 Box 才是真正画图标的 32dp。
 */
@Composable
fun DshIBtn(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    bg: Color? = null,
    size: Dp = Metrics.IBTN,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = DshTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // v22 修订版：`.ibtn` r9 → r8
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            // 触摸区外扩 4：撑满父级给的约束，不改变视觉
            .sizeIn(minWidth = size + Metrics.IBTN_TOUCH_SLOP * 2, minHeight = size + Metrics.IBTN_TOUCH_SLOP * 2)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        // 视觉层：严格等于设计稿的 32
        Box(
            Modifier
                .size(size)
                .background(bg ?: if (pressed) c.line2 else Color.Transparent, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon, contentDescription,
                Modifier.size(size * 0.5625f),
                tint = tint ?: c.ink2,
            )
        }
    }
}

// ============================ 列表行 MRow ============================

/**
 * 设计稿 .mrow：内边距 13/14，图标容器 36×36 r10，主副标题两行 + 右侧。
 * 双行高 62 / 单行 48（设计稿 §11）。
 */
@Composable
fun DshMRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTone: ChipTone? = null,
    trailing: (@Composable () -> Unit)? = null,
    chevron: Boolean = false,
    /** v22 修订版：权限档位 Sheet 的当前档位需要软底高亮（设计稿 `.mrow{background:var(--soft)}`）。 */
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val c = DshTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.background(c.soft, RoundedCornerShape(Radius.md)) else Modifier)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            val ibg = when (iconTone) {
                null, ChipTone.On -> c.soft
                ChipTone.Ok -> c.okSoft()
                ChipTone.Warn -> c.warnSoft()
                ChipTone.Danger -> c.dangerSoft()
                ChipTone.Purple -> c.purpleSoft()
                else -> c.soft
            }
            val ifg = when (iconTone) {
                null, ChipTone.On -> c.brand
                ChipTone.Neutral -> c.ink2
                ChipTone.Ok -> c.ok
                ChipTone.Warn -> c.warn
                ChipTone.Danger -> c.danger
                ChipTone.Purple -> c.purple
                else -> c.brand
            }
            Box(
                Modifier
                    .size(Metrics.ICON_BOX)
                    .background(ibg, RoundedCornerShape(Radius.md)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(19.dp), tint = ifg)
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.rowTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = Type.sec, color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        if (chevron) {
            Spacer(Modifier.width(6.dp))
            Icon(DshIcons.ChevronRight, null, Modifier.size(18.dp), tint = c.ink3)
        }
    }
}

// ============================ 开关 ============================

/** 设计稿 .sw：42×25，圆角 13，滑块 20，开关态底色 --ok，过渡 .2s */
@Composable
fun DshSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Boolean) -> Unit,
) {
    val c = DshTheme.colors
    val bg by animateColorAsState(
        if (checked) c.ok else c.line,
        tween(Motion.MICRO), label = "swBg",
    )
    val knob by animateDpAsState(
        if (checked) 17.dp else 2.5.dp,
        tween(Motion.MICRO), label = "swKnob",
    )
    Box(
        modifier = modifier
            .size(Metrics.SW_W, Metrics.SW_H)
            .background(bg, RoundedCornerShape(13.dp))
            .clickable { onChange(!checked) },
    ) {
        Box(
            Modifier
                .padding(start = 2.5.dp, top = 2.5.dp)
                .offset(x = knob)
                .size(20.dp)
                .background(Color.White, RoundedCornerShape(50)),
        )
    }
}

// ============================ 分段控件 ============================

/** 设计稿 .seg：底 line2，内项选中时 card + 阴影，r8。
 *  tall=true 用更大内距（模型 Sheet 的 Off/High/Max  reasoning 档位，触控目标更大）。 */
@Composable
fun DshSeg(
    options: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    tall: Boolean = false,
    onSelect: (Int) -> Unit,
) {
    val c = DshTheme.colors
    Row(
        modifier = modifier
            .background(c.line2, RoundedCornerShape(Radius.md))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .background(
                        if (on) c.card else Color.Transparent,
                        RoundedCornerShape(Radius.sm),
                    )
                    .clickable { onSelect(i) }
                    .padding(
                        vertical = if (tall) 10.dp else 7.dp,
                        horizontal = if (tall) 10.dp else 4.dp,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = Type.chip,
                    color = if (on) c.ink else c.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.SemiBold
                    else androidx.compose.ui.text.font.FontWeight.Normal,
                )
            }
        }
    }
}

// ============================ Tab 下划线 ============================

/** 设计稿 .tabs：gap 18，选中 14/650 + 底部 2.5px 品牌色圆角线 */
/**
 * 设计稿 .tabs：gap 18，选中 14/650 + 底部 2.5px 品牌色圆角线。
 *
 * ⚠️ 两个必须避开的 Compose 陷阱（v10 复刻真机踩到）：
 *  1. 下划线**不能**用「wrap-content 父 Box 里的 fillMaxWidth 子节点」——
 *     fillMaxWidth 子节点会反过来把父撑成满宽，Row 里第一个 tab 吃掉全部空间、
 *     后面的 tab 被挤出屏幕。正确做法是 drawBehind 画在 Text 自身上，
 *     宽度天然等于文字宽度。
 *  2. 设计稿 .tabs 只有 border-bottom，用 .border() 会画出四边全框。
 */
@Composable
fun DshTabs(
    tabs: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val c = DshTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .bottomLine(c.line)
            .padding(start = 2.dp, end = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        tabs.forEachIndexed { i, label ->
            val on = i == selectedIndex
            Text(
                label,
                style = if (on) Type.tabOn else Type.tab,
                color = if (on) c.ink else c.ink3,
                maxLines = 1,
                modifier = Modifier
                    .clickable { onSelect(i) }
                    .drawBehind {
                        if (on) {
                            // bottom:-1px —— 与设计稿 .tab.on::after 一致，略微下沉
                            val stroke = 2.5.dp.toPx()
                            val y = size.height - stroke / 2f
                            drawLine(
                                color = c.brand,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = stroke,
                                cap = StrokeCap.Round,
                            )
                        }
                    },
            )
        }
    }
}

// ============================ 分隔线 ============================

/** 列表卡内的行分隔线。设计稿 .mrow:last-child{border-bottom:none}，末行不要线。 */
@Composable
fun DshDivider(modifier: Modifier = Modifier) {
    val c = DshTheme.colors
    Box(modifier.fillMaxWidth().height(1.dp).background(c.line2))
}

// ============================ 区块标题 ============================

/** 设计稿 .sec-t：11.5/700，字距 .6，ink3，右侧可挂一个弱化的补充说明 */
@Composable
fun DshSecTitle(
    text: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    icon: ImageVector? = null,
) {
    val c = DshTheme.colors
    Row(
        modifier = modifier.padding(top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(13.dp), tint = c.ink3)
        Text(text, style = Type.secTitle, color = c.ink3)
        if (note != null) {
            Spacer(Modifier.weight(1f))
            Text(note, style = Type.meta, color = c.ink3, maxLines = 1)
        }
    }
}

// ============================ 代码块 ============================

/** 设计稿 .code：底 --code，1px --line，r10，内距 10/12，mono 11.5/1.7 */
@Composable
fun DshCode(
    code: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 11.5.sp,
) {
    val c = DshTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(c.code, RoundedCornerShape(Radius.md))
            .border(1.dp, c.line, RoundedCornerShape(Radius.md))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(code, style = Type.mono.copy(fontSize = fontSize), color = c.ink2)
    }
}

// ============================ 品牌标 ============================

/**
 * 设计稿 .brandmark：**26px 手绘 SVG logo** + "Deepseek" + "Harness" 描边小标。
 * 这是 App 的品牌锚点，S3/S4/S18 顶栏都在用。
 *
 * ⚠️ v22 修订版（2026-10-24）变更：
 * - logo 本体从「渐变圆角方块 + material-icons bolt」换成 [DshLogo] 的手绘 SVG
 *   （一只带尾鳍和眼睛的蓝色鲸形，详见 [DshLogo] 文档里的设计稿原文）；
 * - 文字大小写 `deepseek` / `HARNESS` → `Deepseek` / `Harness`；
 * - 容器 22dp → 26dp，且**不再是渐变底**（透明容器，svg 占满）。
 */
@Composable
fun DshBrandmark(
    modifier: Modifier = Modifier,
    name: String = "Deepseek",
    tag: String = "Harness",
) {
    val c = DshTheme.colors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        // v22 修订版：26dp 透明容器 + 手绘 SVG（不再是渐变方块 + bolt 图标）
        DshLogo(modifier = Modifier.size(26.dp))
        Text(name, style = Type.brandmark, color = c.ink)
        if (tag.isNotEmpty()) {
            Box(
                Modifier
                    .border(BorderStroke(1.dp, c.brand.copy(alpha = 0.85f)), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    tag,
                    style = Type.meta.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        letterSpacing = 1.sp,
                    ),
                    color = c.brand.copy(alpha = 0.85f),
                )
            }
        }
    }
}

// ============================ 选择卡 pick ============================

/**
 * 设计稿 .pick：引导页的选项卡（图标 + 标题 + 说明 + 右侧勾选）。
 * S1 是多选、S2 是单选，靠 [selected] 的多值集合区分。
 */
@Composable
fun DshPick(
    title: String,
    desc: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    /** S2「关键细节」标题里嵌了一个"推荐"品牌 chip */
    titleTrailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val c = DshTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (selected) c.soft else c.card, RoundedCornerShape(Radius.xl))
            .border(
                BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) c.brand else c.line),
                RoundedCornerShape(Radius.xl),
            )
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .background(if (selected) c.brand else c.line2, RoundedCornerShape(Radius.md)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(20.dp), tint = if (selected) Color.White else c.ink2)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = Type.rowTitle, color = c.ink)
                if (titleTrailing != null) {
                    Spacer(Modifier.width(4.dp))
                    titleTrailing()
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(desc, style = Type.sec, color = c.ink3, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(22.dp)
                .background(if (selected) c.brand else Color.Transparent, RoundedCornerShape(50))
                .then(
                    if (selected) Modifier
                    else Modifier.border(1.5.dp, c.line, RoundedCornerShape(50)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(DshIcons.Check, null, Modifier.size(14.dp), tint = Color.White)
            }
        }
    }
}

// ============================ 表单字段（v20 S30–S49 共用）============================

/**
 * 设计稿 .flb：字段组标签。11/600，ink3，字距 .3，右下间距 5。
 * 右侧可挂补充（如「不可修改」chip）。
 */
@Composable
fun DshFieldLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = DshTheme.colors
    Row(
        modifier.padding(bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text,
            style = Type.meta.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
            ),
            color = c.ink3,
        )
        if (trailing != null) trailing()
    }
}

/**
 * 设计稿 .fgrp：字段组（标签 + 控件），组间距 12。
 * ⚠️ 尾 lambda 落在最后一个参数上：调用方 `DshFieldGroup(label = "x") { ... }`。
 */
@Composable
fun DshFieldGroup(
    modifier: Modifier = Modifier,
    label: String? = null,
    labelTrailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.padding(bottom = 12.dp)) {
        if (label != null) DshFieldLabel(label, trailing = labelTrailing)
        content()
    }
}

/**
 * 设计稿 .fin：只读展示字段行。h40，1px --line，--card 底，r10，内距 11，gap 7。
 * 整行可点（用于「跳去选择」类字段，如 S49 工作目录 → S41）。
 *
 * [autoHeight] = true 时改为 minHeight 40 + 顶部对齐（设计稿 S39 的 API 地址
 * 用了 height:auto 让长 URL 换行显示）。
 *
 * ⚠️ content 必须是最后一个参数（尾 lambda 绑定），trailing 放它前面。
 */
@Composable
fun DshFinRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    autoHeight: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val c = DshTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (autoHeight) Modifier.heightIn(min = 40.dp)
                else Modifier.height(40.dp)
            )
            .clip(RoundedCornerShape(Radius.md))
            .background(c.card)
            .border(1.dp, c.line, RoundedCornerShape(Radius.md))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 11.dp, vertical = if (autoHeight) 9.dp else 0.dp),
        verticalAlignment = if (autoHeight) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (leading != null) leading()
        content()
        if (trailing != null) trailing()
    }
}

/**
 * 设计稿 .pg：进度条（S35 按模型占比、S46 打包进度）。
 * h6，底 line2，填充色由调用方给（品牌 / 紫）。
 */
@Composable
fun DshProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = DshTheme.colors.brand,
) {
    val c = DshTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(c.line2),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
    }
}

/**
 * 设计稿 .rt：行右侧数值/动作小字（S35 金额、S41 会话数、S48「恢复 ›」）。
 */
@Composable
fun DshRowTail(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    val c = DshTheme.colors
    Text(
        text,
        style = Type.meta.copy(fontWeight = FontWeight.SemiBold),
        color = color ?: c.ink3,
        modifier = modifier,
        maxLines = 1,
    )
}