package com.dsh.harness.mobile.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dsh.harness.mobile.ui.theme.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * 页面骨架三件套：状态栏占位 / 顶栏 / Bottom Sheet。
 *
 * 与设计稿的对应关系：
 *   .sb     状态栏 44px           → DshStatusBarSpacer（真机取真实 insets）
 *   .nav    顶栏 48px              → DshNavBar
 *   .sheet  顶部 r22 ≤88% 280ms    → DshSheet
 *   .mask   遮罩 220ms             → DshSheet 内部
 *
 * 设计稿把 iOS home 指示区（26px）画进了每屏；真机上那是系统手势区，
 * 所以不画，改用 navigationBarsPadding 把吸底元素顶上去。
 */

// ============================ 状态栏 ============================

/** 状态栏占位。设计稿画了 iOS 状态栏，真机上系统自己画，我们只别被刘海压住。 */
@Composable
fun DshStatusBarSpacer() {
    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
}

// ============================ 顶栏 ============================

/** 顶栏。stacked=true 时加卡片底 + 底部分隔线（S5/S8/S11 用）。
 *
 * ⚠️ v14 真机踩坑的防御设计：`onBack` 默认 null（不画返回键），
 * 于是 `onBack = {}` 这种**空 lambda** 能悄悄通过编译 —— 按钮画出来了，
 * 点下去什么都不发生，真机上表现为"返回键坏了"。
 * 现在加了 `noBackFor`：一级页（首页等）必须显式写出来，
 * 二级页必须传真实的 onBack。两类都要表态，不能沉默。
 *
 * 检查脚本：scripts/check-back-button.sh
 */
@Composable
fun DshNavBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    /** 仅用于一级页：显式声明"这一屏没有返回键"。传任何名字都会在预览里看到它 */
    noBackFor: String? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    stacked: Boolean = false,
    subtitle: String? = null,
) {
    val c = DshTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Metrics.NAV_H)
            .then(if (stacked) Modifier.background(c.card) else Modifier)
            .then(if (stacked) Modifier.bottomLine(c.line) else Modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) {
            DshIBtn(DshIcons.ArrowBack, "返回", onClick = onBack)
        }
        if (leading != null) leading()
        if (title != null) {
            // v22 修订版：`.nav .ttl` 改为 **15px / 居中**（旧版是 16px 左对齐）。
            // 用 weight(1f) 占满中间区 + textAlign.Center，而不是把整条 Row 居中 ——
            // 否则左右两侧的返回键 / 动作区会被挤到不对称的位置。
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = Type.title.copy(fontSize = 15.sp),
                    color = c.ink,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = Type.meta,
                        color = c.ink3,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        } else if (leading == null && onBack == null) {
            Spacer(Modifier.weight(1f))
        }
        if (actions != null) actions()
    }
}

/** 在 Modifier 底部画一条 1px 分隔线。颜色必须由调用方传入 —— drawBehind 不是 composable。 */
fun Modifier.bottomLine(color: Color): Modifier = drawBehind {
    drawLine(
        color = color,
        start = Offset(0f, size.height - 0.5f),
        end = Offset(size.width, size.height - 0.5f),
        strokeWidth = 1f,
    )
}

/** 在 Modifier 顶部画一条 1px 分隔线。 */
fun Modifier.topLine(color: Color): Modifier = drawBehind {
    drawLine(
        color = color,
        start = Offset(0f, 0.5f),
        end = Offset(size.width, 0.5f),
        strokeWidth = 1f,
    )
}

/**
 * 工作区胶囊。设计稿 .ws-pill：h30 r16，1px --line，前图标 14 ink3，
 * 主文 12.5/600 可省略号，尾图标 14。S3/S11/S18 顶栏都在用。
 */
@Composable
fun DshWsPill(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = DshIcons.FolderOpen,
    onClick: (() -> Unit)? = null,
) {
    val c = DshTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .height(30.dp)
            .background(c.card, shape)
            .let { m -> if (onClick != null) m.clickable { onClick() } else m }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = c.ink3)
        Text(label, style = Type.chip, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(DshIcons.ExpandMore, null, Modifier.size(14.dp), tint = c.ink3)
    }
}

// ============================ Bottom Sheet ============================

/**
 * 设计稿 .sheet：顶部 r22，最大 88%（S7 62% / S9 84%），280ms cubic-bezier(.32,.72,0,1)。
 * 结构：.grab 把手 36×4 → .sh-h 标题行 → .sh-b 内容内滚 → .sh-f 固定按钮区。
 *
 * 必须放在 BoxScope 里（用 align(BottomCenter) 贴底）。
 */
@Composable
fun BoxScope.DshSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeightRatio: Float = 0.88f,
    header: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val c = DshTheme.colors
    val shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet)

    // 遮罩
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(220)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.mask)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
        )
    }

    if (visible) {
        // v22 修订版 · 横屏/平板：Sheet 最大 480dp 居中（设计稿"Sheet max 480px centered"）。
        // 手机（Compact）必须走 fillMaxWidth() —— 与 v30.10 逐字节一致。
        val compact = LocalDshWidth.current == DshWidth.Compact
        // v30.15：Sheet **包裹内容高度**（短内容不再硬拉 88% 留下一大片空白），
        // 最高仍封顶 88% 屏高；内容超高时才由内容列内滚。heightIn(max) 让 Column
        // 走 wrap-content 向上取值，配合内容列 weight(fill=false) 即"短则包裹、长则封顶滚动"。
        val maxSheetH = (LocalConfiguration.current.screenHeightDp * maxHeightRatio).dp
        Column(
            modifier = modifier
                .align(Alignment.BottomCenter)
                .then(if (compact) Modifier.fillMaxWidth() else Modifier.width(480.dp))
                .heightIn(max = maxSheetH)
                .clip(shape)
                .background(c.sheet)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { /* 吃掉点击，避免穿透 */ },
        ) {
            // 把手
            Box(
                Modifier
                    .padding(top = 8.dp, bottom = 2.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(Metrics.SHEET_GRAB_W, Metrics.SHEET_GRAB_H)
                    .background(c.line, RoundedCornerShape(2.dp)),
            )

            header()

            // 内容区：设计稿"内容超出时 sh-b 内滚，按钮区固定"。
            // ⚠️ 这里绝不能挂 windowInsetsBottomHeight：v30.10 给内容列加了
            //    `.windowInsetsBottomHeight(navigationBars)`，真机上它挂在
            //    `weight(1f)+verticalScroll` 的内容列上会让**整块内容渲染不出来**
            //    （所有 Sheet 只剩 header/footer；footer 不滚动所以幸免）。
            //    git 实证：v26(a4a5a06) 内容列无它 → 正常；v30.10(2ad89dc) 加上 → 空。
            //    恢复 v26 结构；底部避导航栏由 footer（v30.21 起用 navigationBarsPadding，
            //    原 windowInsetsBottomHeight 会把 footer 列高钉死为 inset、钳制按钮高度）
            //    或短内容天然不触底来保证，无 footer 的 Sheet 内容都在顶部，不会压导航栏。
            // fill=false：内容列只取自身高度（不撑满），配合外层 heightIn 实现"短则包裹"。
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.sheet),
            ) {
                content()
                Spacer(Modifier.height(14.dp))
            }

            if (footer != null) {
                // v30.21 踩坑修正：这里原来用 `.windowInsetsBottomHeight(navigationBars)`，
                // 它把 footer 列高**钉死为导航栏 inset 高度**（手势导航机上仅 ~18dp），
                // footer 里的按钮 `.height(66.dp)` 全被约束钳制成 18dp——
                // 真机实测 footer 按钮永远只有 63px（v30.19 的 46dp、v30.20 的 66dp 全部无效）。
                // 正解 = navigationBarsPadding()（inset 作为 padding 追加，列包裹内容）。
                Column(
                    Modifier
                        .fillMaxWidth()
                        .topLine(c.line)
                        .navigationBarsPadding()
                        .padding(horizontal = Space.sheet, vertical = 12.dp),
                ) {
                    footer()
                }
            } else {
                // 无 footer 的 Sheet（权限档位/添加/附件）：包裹变短后末行会贴屏幕底，
                // 补一个底部避导航栏的 Spacer。用 navigationBarsPadding 挂 Spacer（安全，
                // DshScreen 同款）；切勿把 windowInsetsBottomHeight 挂到内容列（会引爆渲染）。
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

/** Sheet 标题行 .sh-h：内距 8/18/12，标题 16/650 flex1，右侧可放关闭或 badge */
@Composable
fun DshSheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = DshTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Space.sheet, end = 12.dp, top = 8.dp, bottom = 12.dp)
            .bottomLine(c.line),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp), tint = iconTint ?: c.ink2)
        }
        Text(title, style = Type.title, color = c.ink, maxLines = 1, modifier = Modifier.weight(1f))
        if (trailing != null) trailing()
    }
}

// ============================ 空态 ============================

/**
 * 设计稿要求"状态四态覆盖"：正常 / 空数据 / 加载 / 异常，四态都要有真实屏承载。
 * 这里承载"空数据"，用在 S4 侧栏「暂无会话」与 S13 无任务时。
 */
@Composable
fun DshEmpty(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = DshTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(22.dp), tint = c.ink3.copy(alpha = 0.6f))
            Spacer(Modifier.height(6.dp))
        }
        Text(text, style = Type.sec, color = c.ink3)
    }
}

// ============================ 屏幕外壳 ============================

/**
 * 一屏的标准外壳：状态栏占位 → 内容 → 底部手势区留白。所有屏统一套这个。
 *
 * v26 方案 B：内容区外面加了一层**最大宽约束 + 居中**的 Column。
 * 手机（Compact）DshDimens.contentMax = 1000dp，屏宽远小于它 → widthIn
 * 不触发，布局与 v25 逐像素一致；折叠屏展开/平板才收窄，避免一行文字
 * 横跨 800dp 那种"很空"的观感。
 *
 * 加这层不影响既有语义：内层仍是 ColumnScope，`weight(1f)` 在内部分配、
 * `fillMaxSize` 填满内层(=外层剩余高度)，与 v25 外层直挂等价。
 */
@Composable
fun DshScreen(
    modifier: Modifier = Modifier,
    bottomBar: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = DshTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg),
    ) {
        DshStatusBarSpacer()
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .widthIn(max = DshDimens.contentMax)
                .align(Alignment.CenterHorizontally),
        ) {
            content()
        }
        if (bottomBar) {
            Spacer(Modifier.navigationBarsPadding())
            Spacer(Modifier.height(10.dp))
        }
    }
}