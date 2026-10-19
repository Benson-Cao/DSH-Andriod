package com.dsh.harness.mobile.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.harness.mobile.ui.components.*
import com.dsh.harness.mobile.ui.theme.*

// ============================ 局部零件 ============================

/** 用户气泡 .ub：右对齐，max-width 280，r15/15/5/15，品牌底白字 */
@Composable
fun ColumnScope.DshUserBubble(text: String, modifier: Modifier = Modifier) {
    val c = DshTheme.colors
    Box(
        modifier = modifier
            .align(Alignment.End)
            .widthIn(max = 280.dp)
            .shadow(2.dp, RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp,
                bottomEnd = 15.dp, bottomStart = 5.dp), clip = false)
            .background(c.brand, RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp,
                bottomEnd = 15.dp, bottomStart = 5.dp))
            .padding(horizontal = 13.dp, vertical = 10.dp),
    ) {
        Text(
            text,
            style = Type.body.copy(lineHeight = 21.sp),
            color = Color.White,
        )
    }
}

/** 回答正文 .ab：max-width 320，13.5px / 1.62 */
@Composable
fun DshAnswerText(text: String, modifier: Modifier = Modifier) {
    val c = DshTheme.colors
    Text(
        text,
        style = Type.body.copy(fontSize = 13.5.sp, lineHeight = 21.9.sp),
        color = c.ink,
        modifier = modifier.widthIn(max = 320.dp),
    )
}

/** 进度条 .pg：h4 r2，轨道 line，填充品牌→紫渐变 */
@Composable
fun DshProgress(progress: Float, modifier: Modifier = Modifier) {
    val c = DshTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(c.line),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(4.dp)
                .background(
                    Brush.horizontalGradient(listOf(c.brand, c.purple)),
                    RoundedCornerShape(2.dp),
                ),
        )
    }
}

/** 转圈 .spin：13px，2px 边，顶部品牌色 */
@Composable
fun DshSpinner(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 13.dp) {
    val c = DshTheme.colors
    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spinAngle",
    )
    Box(
        modifier
            .size(size)
            .rotate(angle)
            .clip(CircleShape)
            .border(2.dp, c.line, CircleShape)
            .then(
                Modifier.background(Color.Transparent)
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 顶部一段用品牌色覆盖，模拟 spinner 的头部
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .border(2.dp, Color.Transparent, CircleShape)
                .background(c.brand.copy(alpha = 0.001f), CircleShape),
        )
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .width(size)
                .height(2.dp)
                .background(c.brand, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)),
        )
    }
}

/** 工具调用行：状态图标 + 记录类型标签 + mono 工具名 + 参数 + 耗时 + 查看 */
@Composable
fun DshToolRow(
    tool: String,
    arg: String,
    status: ToolStatus,
    cost: String,
    modifier: Modifier = Modifier,
    /** 记录类型标签：工具 / 子工具（v13） */
    kindLabel: String? = null,
    /** 点击「查看」跳轨迹并定位该记录（v13） */
    onView: (() -> Unit)? = null,
) {
    val c = DshTheme.colors
    val active = status == ToolStatus.Running
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (active) Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(c.soft)
                    .padding(start = 7.dp, top = 5.dp, bottom = 5.dp, end = 0.dp)
                else Modifier.padding(top = 5.dp, bottom = 5.dp)
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        when (status) {
            ToolStatus.Done -> Icon(DshIcons.Done, null, Modifier.size(15.dp), tint = c.ok)
            ToolStatus.Running -> DshSpinner(Modifier.size(13.dp))
            ToolStatus.Fail -> Icon(DshIcons.ErrorOutline, null, Modifier.size(15.dp), tint = c.danger)
            ToolStatus.Wait -> Icon(DshIcons.Lock, null, Modifier.size(15.dp), tint = c.warn)
        }
        if (kindLabel != null) {
            DshChip(kindLabel, tone = if (kindLabel == "子工具") ChipTone.Purple else ChipTone.Ok, mini = true)
        }
        Text(
            tool,
            style = Type.mono,
            color = if (active) c.brand else c.ink,
            fontWeight = if (active) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
        )
        Text(
            arg,
            style = Type.sec,
            color = c.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (cost.isNotEmpty()) {
            Text(
                cost,
                style = Type.meta.copy(fontSize = 10.5.sp),
                color = c.ink3,
                fontWeight = if (active) androidx.compose.ui.text.font.FontWeight.Normal else androidx.compose.ui.text.font.FontWeight.SemiBold,
            )
        }
        if (onView != null) {
            Text(
                "查看",
                style = Type.meta.copy(fontSize = 10.5.sp),
                color = c.brand,
                modifier = Modifier.clickable { onView() },
            )
        }
    }
}

enum class ToolStatus { Done, Running, Fail, Wait }

/** 思考折叠块 .fold：标题行可点，展开后缩进显示正文。
 * [trailing] 挂标题行右侧的弱化补充（设计稿 S42「判断为 UI 侧插件」）。 */
@Composable
fun DshFold(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    initiallyOpen: Boolean = false,
) {
    val c = DshTheme.colors
    var open by remember { mutableStateOf(initiallyOpen) }
    val rotate by animateFloatAsState(
        if (open) 90f else 0f,
        tween(Motion.MICRO), label = "foldArrow",
    )
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                DshIcons.ChevronRight, null,
                Modifier.size(15.dp).rotate(rotate),
                tint = c.ink3,
            )
            if (icon != null) {
                Icon(icon, null, Modifier.size(14.dp), tint = c.ink3.copy(alpha = 0.8f))
            }
            Text(title, style = Type.sec, color = c.ink3, maxLines = 1)
            if (trailing != null) {
                Spacer(Modifier.weight(1f))
                trailing()
            }
        }
        if (open) {
            Text(
                detail,
                style = Type.sec.copy(lineHeight = 19.8.sp),
                color = c.ink2,
                modifier = Modifier.padding(start = 22.dp, top = 2.dp, bottom = 6.dp),
            )
        }
    }
}

// ============================ S5 对话流 · 运行中 ============================

/**
 * S5 对话流 · 运行中
 *
 * 布局（设计稿骨架表）：
 *   状态栏 5% ｜ 会话头 6%(标题+模式+权限) ｜ 消息区 56%(用户泡9 + 思考折叠5 + 文本6
 *   + 步骤组16 + 子代理12 + 后台任务11) ｜ 弹性 ｜ 介入条 5%(停止/排队/引导)
 *   ｜ 输入条 8% ｜ home 3%
 *   "消息区上滚至新内容，介入条与输入条双层吸底"
 */
@Composable
fun S5ChatRunning(
    title: String,
    subtitle: String,
    userText: String,
    modifier: Modifier = Modifier,
    /** 来自 ACP 的实时消息流；为空时退回设计稿演示数据 */
    liveMessages: List<com.dsh.harness.mobile.data.DshViewModel.ChatMessage> = emptyList(),
    liveTools: List<com.dsh.harness.mobile.data.DshViewModel.ToolCall> = emptyList(),
    /** v13 轨迹账本：对话/轨迹标签的「轨迹」态复用 */
    events: List<TrajectoryEvent> = emptyList(),
    showApproval: Boolean = false,
    /** 返回上一级（由 App 层的 NavState 决定从哪来回哪去） */
    onBack: () -> Unit,
    /** 是否有进行中的提示词（决定"停止"是否出现） */
    running: Boolean = false,
    /** 上下文占用百分比，来自 ACP usage_update；null 表示协议还没报 */
    contextUsage: Int? = null,
    /** 中断当前提示词（ACP: session/cancel） */
    onCancel: () -> Unit = {},
    onOpenMore: () -> Unit,
    onOpenApproval: () -> Unit,
    onOpenTrajectory: () -> Unit,
    onSend: (String) -> Unit,
) {
    val c = DshTheme.colors
    var input by remember { mutableStateOf("") }
    val listState = rememberScrollState()
    val hasLive = liveMessages.isNotEmpty()
    // v13：顶部「对话 / 轨迹」并列标签
    var tab by remember { mutableStateOf(0) }

    DshScreen(modifier) {
        // —— 会话头：标题 + 状态点 + 模式·权限 + 更多 ——
        DshNavBar(
            onBack = onBack,
            stacked = true,
            leading = {
                Column(Modifier.weight(1f)) {
                    Text(title, style = Type.title, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(5.dp).background(c.brand, CircleShape))
                        Spacer(Modifier.width(4.dp))
                        Text(subtitle, style = Type.meta.copy(fontSize = 10.5.sp), color = c.ink3)
                    }
                }
            },
            actions = {
                DshIBtn(DshIcons.MoreVert, "更多", onClick = onOpenMore)
            },
        )

        // —— v13 顶部「对话 / 轨迹」并列标签（默认对话）——
        Column(Modifier.padding(horizontal = Space.lg).padding(top = 10.dp)) {
            DshTabs(listOf("对话", "轨迹"), tab) { tab = it }
        }

        // —— 消息区 ——
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(listState)
                .padding(horizontal = Space.lg)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (tab) {
                // —— 对话态：消息 + 工具调用组 ——
                0 -> {
                    Spacer(Modifier.height(4.dp))

                    // 有真实会话时渲染 ACP 推来的消息，否则退回设计稿演示数据
                    if (hasLive) {
                        liveMessages.forEach { m ->
                            when (m.role) {
                                com.dsh.harness.mobile.data.DshViewModel.Role.User ->
                                    DshUserBubble(m.text)

                                com.dsh.harness.mobile.data.DshViewModel.Role.Thought ->
                                    DshFold(
                                        title = "已思考",
                                        detail = m.text,
                                        icon = DshIcons.Psychology,
                                        initiallyOpen = false,
                                    )

                                com.dsh.harness.mobile.data.DshViewModel.Role.Assistant ->
                                    DshAnswerText(m.text)
                            }
                        }
                    } else {
                        DshUserBubble(userText)

                        DshFold(
                            title = "已思考 · 2.4s",
                            detail = "表头有 8 行数据落在版心外，先确认列口径再套模板。" +
                                "同比需要去年同期列，文件里没有，走 web_search 补一份公开口径并标注来源。",
                            icon = DshIcons.Psychology,
                        )

                        DshAnswerText("好的，我按部门维度汇总，先读表结构再生成幻灯片。")
                    }

                    // —— 工具调用组（有实时数据就渲染实时，否则演示）——
                    if (liveTools.isNotEmpty()) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.xl))
                                .background(c.card)
                                .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(
                                "工具调用 · ${liveTools.size}",
                                style = Type.chip.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                                color = c.ink,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                            liveTools.forEach { t ->
                                DshToolRow(
                                    tool = t.name,
                                    arg = t.detail,
                                    status = when (t.status) {
                                        com.dsh.harness.mobile.data.DshViewModel.Status.Running -> ToolStatus.Running
                                        com.dsh.harness.mobile.data.DshViewModel.Status.Done -> ToolStatus.Done
                                        com.dsh.harness.mobile.data.DshViewModel.Status.Fail -> ToolStatus.Fail
                                        com.dsh.harness.mobile.data.DshViewModel.Status.Wait -> ToolStatus.Wait
                                    },
                                    cost = t.cost,
                                    kindLabel = if (t.name.contains("subagent", ignoreCase = true)) "子工具" else "工具",
                                    onView = onOpenTrajectory,
                                )
                            }
                        }
                    }

                    // ⚠️ 子代理与后台任务：ACP **没有**对应事件类型
                    //（协议表里只有 user/agent message、thought、tool_call、usage、config）。
                    // 所以这两块不再展示设计稿演示数据，等接上能力再开。

                    Spacer(Modifier.height(4.dp))
                }

                // —— 轨迹态：预览账本 + 打开轨迹 ——
                else -> {
                    Spacer(Modifier.height(28.dp))
                    Text(
                        "轨迹视图按「第 N 轮」展开账本与时间概览。",
                        style = Type.sec.copy(lineHeight = 19.sp), color = c.ink3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    DshBtn(
                        "打开轨迹",
                        small = true,
                        tone = BtnTone.Ghost,
                        onClick = onOpenTrajectory,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "看不到「轨迹」标签时：设置 → 通用设置 → 打开「显示代码工作视图」。引导里选「办公与创作」或跳过引导的用户，默认不带这个标签。",
                        style = Type.meta.copy(fontSize = 10.5.sp, lineHeight = 16.sp), color = c.ink3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.md))
                            .background(c.soft)
                            .padding(10.dp),
                    )
                }
            }
        }

        // —— 介入条：停止 / 排队 / 中途引导 + 上下文占用（吸底第一层）——
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg)
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 介入条：ACP 只有 session/cancel，没有"排队"与"中途引导"，
            // 所以这里只保留「停止」；上下文占用接真实的 usage_update。
            if (running) {
                DshBtn(
                    "停止",
                    small = true,
                    tone = BtnTone.Danger,
                    modifier = Modifier.width(78.dp),
                    onClick = onCancel,
                )
            }
            Spacer(Modifier.weight(1f))
            if (contextUsage != null) {
                DshChip("$contextUsage%")
            }
        }

        // —— 输入条（吸底第二层）——
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.lg)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .clickable { onOpenApproval() }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(DshIcons.Add, null, Modifier.size(18.dp), tint = c.ink3)
                Text("发消息或创建任务…", style = Type.input, color = c.ink3, modifier = Modifier.weight(1f))
                DshChip("V4-Flash", mini = true)
                Icon(DshIcons.Mic, null, Modifier.size(18.dp), tint = c.ink3)
            }
        }
    }
}

/** 子代理小卡。必须在 RowScope 内用（要 weight 平分宽度）。 */
@Composable
fun RowScope.AgentTile(
    name: String,
    status: String,
    statusColor: Color,
    running: Boolean = false,
) {
    val c = DshTheme.colors
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(Radius.md))
            .background(c.line2)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(name, style = Type.meta.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = c.ink)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (running) DshSpinner(Modifier.size(9.dp))
            Text(status, style = Type.meta.copy(fontSize = 10.5.sp), color = statusColor, maxLines = 1)
        }
    }
}

// ============================ S5 的「更多」Sheet ============================

/** 设计稿：轨迹 / 分叉 / 导出 / 设为任务，四行；底部一个 ghost 关闭 */
@Composable
fun BoxScope.S5MoreSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onTrajectory: () -> Unit,
) {
    val c = DshTheme.colors
    DshSheet(
        visible = visible,
        onDismiss = onDismiss,
        maxHeightRatio = 0.5f,
        header = { Spacer(Modifier.height(10.dp)) },
        content = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
            ) {
                DshMRow(
                    title = "查看轨迹",
                    subtitle = "按时间线复盘系统提示词与工具调用",
                    icon = DshIcons.Timeline,
                    iconTone = ChipTone.Neutral,
                    onClick = { onTrajectory() },
                )
                DshDivider()
                DshMRow(
                    title = "分叉会话",
                    subtitle = "复制上下文开一条新分支",
                    icon = DshIcons.CallSplit,
                    iconTone = ChipTone.Neutral,
                )
                DshDivider()
                DshMRow(
                    title = "导出会话",
                    subtitle = "Markdown / 原始日志",
                    icon = DshIcons.FileDownload,
                    iconTone = ChipTone.Neutral,
                )
                DshDivider()
                DshMRow(
                    title = "设为自动化任务",
                    subtitle = "让这条会话按计划重复执行",
                    icon = DshIcons.Schedule,
                    iconTone = ChipTone.Warn,
                )
            }
        },
        footer = {
            DshBtn("关闭", tone = BtnTone.Ghost, onClick = onDismiss)
        },
    )
}

// ============================ S7 操作审批 Sheet ============================

/**
 * S7 操作审批 —— 设计稿标 Sheet 高 62%。
 * 关键文案原则（设计稿原文）：切到 Read Only 时写入类操作一律询问；
 * Full Access 不再提示 —— 这段必须留在屏上，不能省。
 */
@Composable
fun BoxScope.S7ApprovalSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onAllowOnce: () -> Unit,
    onAllowAlways: () -> Unit,
    onReject: () -> Unit,
) {
    val c = DshTheme.colors
    DshSheet(
        visible = visible,
        onDismiss = onDismiss,
        maxHeightRatio = 0.62f,
        header = {
            DshSheetHeader(
                title = "需要你的确认",
                icon = DshIcons.Shield,
                iconTint = c.warn,
                trailing = {
                    DshChip("Workspace Write", tone = ChipTone.Warn, mini = true)
                },
            )
        },
        content = {
            Text(
                "将要执行的命令",
                style = Type.secTitle,
                color = c.ink3,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            DshCode(
                "rm -rf ./dist\n&& npm run build\n&& python scripts/export_all.py --overwrite",
            )

            Text(
                "影响范围",
                style = Type.secTitle,
                color = c.ink3,
                modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
            ) {
                ImpactRow(DshIcons.Check, "删除", "./dist", "工作区内", c.ok)
                DshDivider()
                ImpactRow(DshIcons.Check, "覆盖", "reports/*.csv", "3 个文件", c.ok)
                DshDivider()
                ImpactRow(DshIcons.Warning, "不可回滚", "", "未纳入 Git", c.danger, dangerText = true)
            }

            Text(
                "切到 Read Only 时写入类操作一律询问；Full Access 不再提示，仅建议在可信目录使用。",
                style = Type.meta.copy(lineHeight = 19.sp),
                color = c.ink3,
                modifier = Modifier.padding(top = 12.dp),
            )
        },
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                DshBtn("拒绝", tone = BtnTone.Ghost, modifier = Modifier.weight(1f), onClick = onReject)
                DshBtn("本次允许", modifier = Modifier.weight(1.3f), onClick = onAllowOnce)
            }
            Spacer(Modifier.height(9.dp))
            DshBtn("本会话始终允许此类操作", tone = BtnTone.Ghost, onClick = onAllowAlways)
        },
    )
}

@Composable
private fun ImpactRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    verb: String,
    path: String,
    tail: String,
    tint: Color,
    dangerText: Boolean = false,
) {
    val c = DshTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(15.dp), tint = tint)
        Text(verb, style = Type.chip, color = c.ink2)
        if (path.isNotEmpty()) {
            Text(path, style = Type.mono, color = c.ink2)
        }
        Spacer(Modifier.weight(1f))
        Text(
            tail,
            style = Type.chip,
            color = if (dangerText) c.danger else c.ink3,
            fontWeight = if (dangerText) androidx.compose.ui.text.font.FontWeight.SemiBold
            else androidx.compose.ui.text.font.FontWeight.Normal,
        )
    }
}