package com.dsh.harness.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.harness.mobile.data.DshPrefs
import com.dsh.harness.mobile.ui.components.*
import com.dsh.harness.mobile.ui.theme.*

// ============================ S6 对话流 · 成果与迭代 ============================

/**
 * 产物条目（v30.8：S6 改为真实数据驱动，不再写死 "Q3数据汇总.pptx"）。
 *
 * @param name     文件名
 * @param size     字节数（<=0 表示未知）
 * @param mtime    最后修改时间戳（毫秒，<=0 表示未知）
 * @param ext      扩展名（大写，不含点）；用于产物卡上的类型徽标
 * @param absPath  绝对路径（可分享/可打开时非空；SAF 虚拟条目可能为空）
 */
data class S6Artifact(
    val name: String,
    val size: Long = 0L,
    val mtime: Long = 0L,
    val ext: String = "",
    val absPath: String? = null,
)

/**
 * S6 成果屏
 * 布局：工作过程（默认折叠）4% ｜ 回答文本 10% ｜ 产物卡 24%(缩略 92px + 三按钮)
 *      ｜ 变更摘要 8% ｜ 建议 chips 8% ｜ 输入条 8%
 *   "产物卡不折叠，保持在首屏可见范围内"
 *   v7 修复：摘要文件名不再折行、按钮文案收短不再与相邻按钮打架。
 *   v30.8：产物 / 变更 / 建议 chips 全部接真实数据与真实动作。
 */
@Composable
fun S6ChatResult(
    title: String,
    answer: String,
    modifier: Modifier = Modifier,
    /** 返回上一级（由 App 层的 NavState 决定从哪来回哪去） */
    onBack: () -> Unit,
    onOpenWorkspace: () -> Unit,
    onPreview: () -> Unit,
    onShare: () -> Unit,
    /** v13 轨迹账本：对话/轨迹标签的「轨迹」态复用 */
    events: List<TrajectoryEvent> = emptyList(),
    onOpenTrajectory: () -> Unit = {},
    /** v30.8：真实产物列表（来自工作区 / SAF 目录扫描）；空 = 还没有产物 */
    artifacts: List<S6Artifact> = emptyList(),
    /** v30.8：真实工作过程摘要（由工具调用归并而来）；null = 不渲染折叠块 */
    processSummary: String? = null,
    /** v30.8：真实变更摘要（写入工作区的文件数 / 名称）；null = 无变更 */
    changeSummary: Pair<Int, String>? = null,
    /** v30.8：继续迭代建议（点击即把该建议作为新提示词发出） */
    suggestions: List<String> = emptyList(),
    /** v30.8：发出新提示词（继续迭代 chip / 输入条发送） */
    onSend: (String) -> Unit = {},
) {
    val c = DshTheme.colors
    // v13：顶部「对话 / 轨迹」并列标签
    var tab by remember { mutableStateOf(0) }

    DshScreen(modifier) {
        DshNavBar(
            onBack = onBack,
            stacked = true,
            leading = {
                Text(title, style = Type.title, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            },
            actions = {
                DshChip("已完成 · 4m12s", icon = DshIcons.Check, tone = ChipTone.Ok, mini = true)
            },
        )

        // —— v13 顶部「对话 / 轨迹」并列标签（默认对话）——
        Column(Modifier.padding(horizontal = Space.lg).padding(top = 10.dp)) {
            DshTabs(listOf("对话", "轨迹"), tab) { tab = it }
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (tab) {
                // —— 对话态：成果与迭代 ——
                0 -> {
            Spacer(Modifier.height(4.dp))

            // 工作过程默认折叠 —— v7 起不再默认展开
            // v30.8：内容来自真实工具调用归并（processSummary 为 null = 本轮没有工具调用）
            if (processSummary != null) {
                DshFold(
                    title = "工作过程 · 点开看明细",
                    detail = processSummary,
                    icon = null,
                    initiallyOpen = false,
                )
            }

            DshAnswerText(answer)

            // —— 产物卡：缩略 74×92 + 标题 + 元信息 + 三按钮 ——
            // v30.8：真实产物列表（工作区 / SAF 扫描）。无产物时诚实空态，不写死示例文件。
            val firstArt = artifacts.firstOrNull()
            if (firstArt == null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.xl))
                        .background(c.card)
                        .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                        .padding(12.dp),
                ) {
                    Text(
                        "还没有产物文件",
                        style = Type.body.copy(fontSize = 14.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                        color = c.ink,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "本轮回答没有在工作区里写出文件。",
                        style = Type.meta.copy(lineHeight = 18.4.sp),
                        color = c.ink3,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DshBtn("打开工作区", icon = DshIcons.FolderOpen, small = true, tone = BtnTone.Ghost, modifier = Modifier.weight(1f), onClick = onOpenWorkspace)
                    }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.xl))
                        .background(c.card)
                        .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                        .padding(12.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // 缩略：设计稿 .thumb 74×92 渐变占位；类型徽标取真实扩展名
                        Column(
                            Modifier
                                .width(74.dp)
                                .height(92.dp)
                                .clip(RoundedCornerShape(Radius.md))
                                .background(Brush.linearGradient(listOf(c.brand, c.purple))),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(DshIcons.Slideshow, null, Modifier.size(24.dp), tint = Color.White)
                            if (firstArt.ext.isNotEmpty()) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    firstArt.ext.take(6),
                                    style = Type.meta.copy(
                                        fontSize = 9.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                    ),
                                    color = Color.White,
                                )
                            }
                        }

                        Column(Modifier.weight(1f)) {
                            // v7：文件名不再折行 —— maxLines=1 + ellipsis
                            Text(
                                firstArt.name,
                                style = Type.body.copy(fontSize = 14.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                                color = c.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                artifactMeta(firstArt),
                                style = Type.meta.copy(lineHeight = 18.4.sp),
                                color = c.ink3,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (firstArt.size > 0) DshChip(fmtArtSize(firstArt.size), mini = true)
                                if (artifacts.size > 1) DshChip("共 ${artifacts.size} 个", icon = DshIcons.Image, mini = true)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    // v7：三个按钮等分，文案收短，互不打架
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DshBtn("预览", icon = DshIcons.Visibility, small = true, tone = BtnTone.Ghost, modifier = Modifier.weight(1f), onClick = onPreview)
                        DshBtn("打开", icon = DshIcons.FolderOpen, small = true, tone = BtnTone.Ghost, modifier = Modifier.weight(1f), onClick = onOpenWorkspace)
                        DshBtn("分享", icon = DshIcons.Share, small = true, modifier = Modifier.weight(1f), onClick = onShare)
                    }
                }
            }

            // —— 变更摘要（v30.8：真实计数，无变更不渲染）——
            if (changeSummary != null) {
                val (n, names) = changeSummary
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.xl))
                        .background(c.card)
                        .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                        .clickable { onOpenWorkspace() }
                        .padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(DshIcons.Difference, null, Modifier.size(18.dp), tint = c.purple)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("工作区内修改 · $n 个文件", style = Type.rowTitle, color = c.ink, maxLines = 1)
                        Text(names, style = Type.sec, color = c.ink3, maxLines = 1)
                    }
                    Icon(DshIcons.ChevronRight, null, Modifier.size(17.dp), tint = c.ink3)
                }
            }

            // —— 继续迭代（v30.8：chip 点击 = 把建议作为新提示词发出）——
            if (suggestions.isNotEmpty()) {
                DshSecTitle("继续迭代", Modifier.padding(top = 0.dp))
                suggestions.chunked(2).forEachIndexed { i, pair ->
                    if (i > 0) Spacer(Modifier.height(7.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        pair.forEach { s ->
                            DshChip(s, icon = DshIcons.AddChart, onClick = { onSend(s) })
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
                }

                // —— 轨迹态：预览账本 + 打开轨迹 ——
                else -> {
                    Spacer(Modifier.height(28.dp))
                    val rounds = events.count { it.round > 0 }
                    val tools = events.count { it.kind == EventKind.Tool || it.kind == EventKind.SubTool }
                    Text(
                        "${rounds} 轮请求、${tools} 条工具记录。",
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
                }
            }
        }

        // —— 输入条（v30.8：真实可输入 + 发送接 onSend）——
        var followUp by remember { mutableStateOf("") }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(DshIcons.Add, null, Modifier.size(18.dp), tint = c.ink3)
                BasicTextField(
                    value = followUp,
                    onValueChange = { followUp = it },
                    modifier = Modifier.weight(1f),
                    textStyle = Type.input.copy(color = c.ink),
                    cursorBrush = SolidColor(c.brand),
                    singleLine = true,
                    decorationBox = { inner ->
                        if (followUp.isEmpty()) {
                            Text(
                                "继续提要求…",
                                style = Type.input,
                                color = c.ink3,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    },
                )
                DshChip("V4-Pro Max", tone = ChipTone.On, mini = true)
                DshIBtn(
                    DshIcons.ArrowUpward,
                    "发送",
                    bg = c.brand,
                    tint = Color.White,
                    enabled = followUp.isNotBlank(),
                    onClick = {
                        val t = followUp.trim()
                        if (t.isNotEmpty()) {
                            followUp = ""
                            onSend(t)
                        }
                    },
                )
            }
        }
    }
}

/** v30.8：产物大小 → 人类可读（B / KB / MB / GB）。 */
private fun fmtArtSize(bytes: Long): String {
    if (bytes <= 0) return "—"
    val units = listOf("B", "KB", "MB", "GB")
    var v = bytes.toDouble()
    var u = 0
    while (v >= 1024 && u < units.lastIndex) { v /= 1024; u++ }
    return if (u == 0) "${bytes}B" else "%.1f %s".format(v, units[u])
}

/** v30.8：产物卡副标题行 = 大小 · 写入时间（真实时间戳；无则省略）。 */
private fun artifactMeta(a: S6Artifact): String {
    val parts = mutableListOf<String>()
    if (a.size > 0) parts += fmtArtSize(a.size)
    if (a.mtime > 0) {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = a.mtime
        parts += "%02d:%02d 写入工作区".format(
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
        )
    }
    if (parts.isEmpty()) return "已写入工作区"
    return parts.joinToString(" · ")
}

/**
 * S8 轨迹
 *
 * ⚠️ ACP **不提供历史轨迹回放**（README 明确：resume 恢复日志但**不回放**旧更新，
 * 也不支持转录回放）。所以本屏只能显示**本次会话的实时事件流**。
 * 用户要求"没数据的先空着" → 无事件时显示空态，不再填演示数据。
 * 日志 Tab 是例外：它读应用自己的协议日志，是真实数据。
 */
@Composable
fun S8Trajectory(
    modifier: Modifier = Modifier,
    initialTab: Int = 1,
    onBack: () -> Unit,
    events: List<TrajectoryEvent> = emptyList(),
    protocolLog: List<String> = emptyList(),
    /** 导出 JSONL 轨迹（由 App 层实现，落盘或复制） */
    onExport: () -> Unit = {},
) {
    val c = DshTheme.colors
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(initialTab) }
    // 工具栏开关：轮次 / 调用 / 时长 三个持久化，搜索为会话内临时态
    var expandRounds by remember { mutableStateOf(DshPrefs.expandRounds(ctx)) }
    var showCalls by remember { mutableStateOf(DshPrefs.showCalls(ctx)) }
    var showDuration by remember { mutableStateOf(DshPrefs.showDuration(ctx)) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // S26 事件详情检查器：点任意记录打开
    var inspect by remember { mutableStateOf<TrajectoryEvent?>(null) }

    val filtered = if (query.isBlank()) events else events.filter {
        it.title.contains(query, true) || it.detail.contains(query, true) || it.kind.label().contains(query, true)
    }

    DshScreen(modifier) {
        DshNavBar(
            onBack = onBack,
            title = "轨迹",
            stacked = true,
            actions = {
                DshIBtn(DshIcons.Search, "搜索", onClick = { searching = !searching })
            },
        )

        // —— 顶部「对话 / 轨迹」并列标签（S8 默认停在轨迹）——
        Column(Modifier.padding(horizontal = Space.lg).padding(top = 10.dp)) {
            DshTabs(listOf("对话", "轨迹"), tab) { tab = it }
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg)
                .padding(top = 12.dp),
        ) {
            when (tab) {
                // 对话标签：本屏是轨迹，点此回对话视图读结果
                0 -> Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(40.dp))
                    Text("返回对话视图读结果。", style = Type.sec, color = c.ink3)
                    Spacer(Modifier.height(10.dp))
                    DshBtn("切回对话", small = true, tone = BtnTone.Ghost, onClick = onBack)
                }

                // 轨迹标签：工具栏 + 时间概览 + 指标 + 账本
                else -> {
                    // —— 工具栏：轮次 / 调用 / 搜索 / 时长 ——
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        DshChip(
                            "轮次", icon = DshIcons.UnfoldMore,
                            tone = if (expandRounds) ChipTone.On else ChipTone.Neutral,
                            onClick = {
                                expandRounds = !expandRounds
                                DshPrefs.setExpandRounds(ctx, expandRounds)
                            },
                        )
                        DshChip(
                            "调用", icon = DshIcons.Compress,
                            tone = if (showCalls) ChipTone.On else ChipTone.Neutral,
                            onClick = {
                                showCalls = !showCalls
                                DshPrefs.setShowCalls(ctx, showCalls)
                            },
                        )
                        DshChip(
                            "搜索", icon = DshIcons.Search,
                            tone = if (searching) ChipTone.On else ChipTone.Neutral,
                            onClick = { searching = !searching },
                        )
                        DshChip(
                            "时长", icon = DshIcons.Timer,
                            tone = if (showDuration) ChipTone.On else ChipTone.Neutral,
                            onClick = {
                                showDuration = !showDuration
                                DshPrefs.setShowDuration(ctx, showDuration)
                            },
                        )
                    }
                    if (searching) {
                        Spacer(Modifier.height(8.dp))
                        BasicTextField(
                            query,
                            onValueChange = { query = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.md))
                                .background(c.card)
                                .border(1.dp, c.line, RoundedCornerShape(Radius.md))
                                .padding(horizontal = 11.dp, vertical = 9.dp),
                            textStyle = Type.sec.copy(color = c.ink),
                            singleLine = true,
                            decorationBox = { inner ->
                                if (query.isEmpty()) {
                                    Text("过滤记录…", style = Type.sec, color = c.ink3)
                                }
                                inner()
                            },
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    // —— 时间概览：输入 / 模型 / 工具 三通道 ——
                    TrajectoryOverview(events = filtered, showDuration = showDuration)

                    // —— 指标条：Token / KV-Cache / 首 token / 吞吐（协议未提供，空着）——
                    TrajectoryMetrics()

                    // —— 账本：按「第 N 轮」分组 ——
                    if (filtered.isEmpty()) {
                        DshEmpty("本次会话还没有事件", icon = DshIcons.Timeline)
                    } else {
                        TrajectoryLedger(
                            events = filtered,
                            expandRounds = expandRounds,
                            showCalls = showCalls,
                            showDuration = showDuration,
                            onOpenEvent = { inspect = it },
                        )
                    }
                }
            }
        }

        // —— 动作条：恢复 / 分叉 / 回放 / 导出 ——
        // v30.8：ACP 协议明确**不支持** session/load、fork、转录回放，
        // 所以这三个按钮统一改为「禁用态 + 脚注说明原因」，
        // 不再出现「一排里导出能点、恢复/分叉/回放点了没反应」的半吊子状态。
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg)
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DshBtn("恢复", small = true, tone = BtnTone.Ghost, enabled = false, modifier = Modifier.weight(1f), onClick = {})
            DshBtn("分叉", small = true, tone = BtnTone.Ghost, enabled = false, modifier = Modifier.weight(1f), onClick = {})
            DshBtn("回放", small = true, tone = BtnTone.Ghost, enabled = false, modifier = Modifier.weight(1f), onClick = {})
            DshIBtn(DshIcons.FileDownload, "导出", onClick = onExport, modifier = Modifier.size(34.dp))
        }
        Text(
            "恢复 / 分叉 / 回放需要 dsh 支持会话快照与转录回放，当前 ACP 协议未提供该能力，暂不可用；导出 JSONL 可用。",
            style = Type.meta.copy(fontSize = 10.5.sp, lineHeight = 16.sp),
            color = c.ink3,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg)
                .padding(bottom = 8.dp),
        )
    }

    // —— S26 事件详情检查器 ——
    inspect?.let { ev ->
        S26EventInspector(event = ev, onDismiss = { inspect = null })
    }
}

/**
 * 时间线事件（由 DshViewModel 从 session/update 归并而来）。
 *
 * v13 对齐官方 0.2.0-rc.2 的记录类型：系统 / 用户 / 上下文 / 助手 / 工具 / 子工具 /
 * 已压缩 / 审批 / 请求#N。ACP 协议只给到 user/agent message、thought、tool_call、
 * usage（见 DshViewModel），所以 系统提示词 / 审批 / 已压缩 / 请求#N 这几类在真实运行里
 * 多数不会出现 —— 按用户要求「没数据的先空着」，不填演示数据。
 */
enum class EventKind { System, User, Context, Model, Tool, SubTool, Compress, Approval, Request }

/** 记录类型 → 标签文案（与设计稿 .chip.l* 一致） */
fun EventKind.label(): String = when (this) {
    EventKind.System -> "系统"
    EventKind.User -> "用户"
    EventKind.Context -> "上下文"
    EventKind.Model -> "助手"
    EventKind.Tool -> "工具"
    EventKind.SubTool -> "子工具"
    EventKind.Compress -> "已压缩"
    EventKind.Approval -> "审批"
    EventKind.Request -> "请求"
}

/** 记录类型 → chip 语义色（复用既有 DshChip 七档） */
fun EventKind.chipTone(): ChipTone = when (this) {
    EventKind.System -> ChipTone.Neutral
    EventKind.User -> ChipTone.Brand
    EventKind.Context -> ChipTone.On
    EventKind.Model -> ChipTone.On
    EventKind.Tool -> ChipTone.Ok
    EventKind.SubTool -> ChipTone.Purple
    EventKind.Compress -> ChipTone.Warn
    EventKind.Approval -> ChipTone.Danger
    EventKind.Request -> ChipTone.Neutral
}

data class TrajectoryEvent(
    val kind: EventKind,
    val title: String,
    val detail: String,
    /** 耗时，如 "1,240ms"；请求/工具记录带，其余为空 */
    val cost: String = "",
    /** 所属轮次（从 1 起）；由 ViewModel 按用户消息切分 */
    val round: Int = 0,
    /** 仅 Request 用：请求序号，渲染成「请求 #N」 */
    val reqIndex: Int = 0,
)

/** 把 "1,240ms" / "52.1s" 解析成毫秒；解析不出返回 0 */
private fun parseMs(s: String): Long {
    val digits = s.filter { it.isDigit() }
    if (digits.isEmpty()) return 0L
    val n = digits.toLongOrNull() ?: return 0L
    return if (s.contains("s") && !s.contains("ms")) n * 1000 else n
}

/** 时间概览：输入 / 模型 / 工具 三条通道的条形。
 *  工具通道有真实耗时（来自 ACP tool_call 的 cost），输入/模型协议未提供 → 画一条弱占位条，不造假数据。 */
@Composable
private fun TrajectoryOverview(events: List<TrajectoryEvent>, showDuration: Boolean) {
    val c = DshTheme.colors
    val toolEvents = events.filter { it.kind == EventKind.Tool || it.kind == EventKind.SubTool }
    val totalMs = toolEvents.sumOf { parseMs(it.cost) }.coerceAtLeast(1L)

    DshCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("时间概览", style = Type.chip.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = c.ink)
            Text(
                if (showDuration) "条形宽度 = 实际耗时 · 该选择会被记住" else "等宽只反映顺序",
                style = Type.meta.copy(fontSize = 9.5.sp), color = c.ink3,
            )
        }
        Spacer(Modifier.height(6.dp))
        // 输入 / 模型：协议未提供分段数据，画弱占位条，标注「—」
        OverviewChannel("输入", listOf(0.0f), c.line2, c.ink3, "—")
        OverviewChannel("模型", listOf(0.0f), c.line2, c.ink3, "—")
        // 工具：真实分段
        val segs = toolEvents.map { (parseMs(it.cost).toFloat() / totalMs).coerceAtLeast(0.04f) }
        OverviewChannel(
            "工具",
            segs,
            if (segs.isEmpty()) c.line2 else c.ok,
            c.ok,
            if (toolEvents.isEmpty()) "—" else null,
            subtone = true,
        )
        if (toolEvents.any { it.kind == EventKind.SubTool }) {
            Text("· 含子工具（紫色段）", style = Type.meta.copy(fontSize = 9.5.sp), color = c.ink3, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun OverviewChannel(
    label: String,
    weights: List<Float>,
    color: Color,
    fg: Color,
    hint: String?,
    subtone: Boolean = false,
) {
    val c = DshTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = Type.meta.copy(fontSize = 10.sp), color = c.ink3, modifier = Modifier.width(30.dp))
        Row(
            Modifier.weight(1f).height(9.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (weights.isEmpty() || (weights.size == 1 && weights[0] <= 0f)) {
                Box(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(2.dp)).background(c.line2))
            } else {
                weights.forEach { w ->
                    Box(Modifier.weight(w).height(9.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (subtone) c.purple else color))
                }
            }
        }
        if (hint != null) {
            Text(hint, style = Type.meta.copy(fontSize = 10.sp), color = fg, modifier = Modifier.width(18.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}

/** 指标条：Token 计量 / KV-Cache / 首 token / 吞吐。ACP 协议未提供这些，按用户要求空着（"—"）。 */
@Composable
private fun TrajectoryMetrics() {
    val c = DshTheme.colors
    DshCard {
        Row(Modifier.fillMaxWidth()) {
            MetricCell("—", "Token 计量", c.ink3, Modifier.weight(1f), true)
            MetricCell("—", "KV-Cache", c.ink3, Modifier.weight(1f), true)
            MetricCell("—", "首 token", c.ink3, Modifier.weight(1f), true)
            MetricCell("—", "吞吐", c.ink3, Modifier.weight(1f), false)
        }
    }
}

@Composable
private fun MetricCell(value: String, label: String, valueColor: Color, modifier: Modifier = Modifier, divider: Boolean) {
    val c = DshTheme.colors
    Column(
        modifier.then(if (divider) Modifier.endDivider(c.line2) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = Type.body.copy(fontSize = 13.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = valueColor)
        Spacer(Modifier.height(2.dp))
        Text(label, style = Type.meta.copy(fontSize = 9.5.sp), color = c.ink3)
    }
}

/**
 * 账本：按「第 N 轮」分组。
 * - round 由 ViewModel 按用户消息切分（每来一条用户消息开新一轮）。
 * - 已压缩 / 审批 等 round==0 的事件归入「轮次之间」。
 * - showCalls=false 时不渲染工具/子工具行；expandRounds=false 时只留轮次头。
 */
@Composable
private fun TrajectoryLedger(
    events: List<TrajectoryEvent>,
    expandRounds: Boolean,
    showCalls: Boolean,
    showDuration: Boolean,
    onOpenEvent: (TrajectoryEvent) -> Unit,
) {
    val c = DshTheme.colors
    val rounds = events.filter { it.round > 0 }.groupBy { it.round }.toSortedMap()
    val between = events.filter { it.round == 0 }

    rounds.forEach { (r, list) ->
        val dur = if (showDuration) list.sumOf { parseMs(it.cost) } else 0L
        Row(
            Modifier.fillMaxWidth().padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text("第 $r 轮", style = Type.body.copy(fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = c.ink)
            Spacer(Modifier.weight(1f).height(1.dp).background(c.line))
            Text(
                "${list.size} 条" + if (showDuration && dur > 0) " · ${(dur / 1000.0).let { "%.1f".format(it) }}s" else "",
                style = Type.meta.copy(fontSize = 10.sp), color = c.ink3,
            )
        }
        if (expandRounds) {
            list.forEach { e ->
                if (!showCalls && (e.kind == EventKind.Tool || e.kind == EventKind.SubTool)) return@forEach
                TrajectoryLedgerRow(e, showDuration, onOpenEvent)
            }
        }
    }

    if (between.isNotEmpty()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text("轮次之间", style = Type.body.copy(fontSize = 11.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = c.ink2)
            Spacer(Modifier.weight(1f).height(1.dp).background(c.line))
        }
        between.forEach { e -> TrajectoryLedgerRow(e, showDuration, onOpenEvent) }
    }
}

@Composable
private fun TrajectoryLedgerRow(
    e: TrajectoryEvent,
    showDuration: Boolean,
    onOpenEvent: (TrajectoryEvent) -> Unit,
) {
    val c = DshTheme.colors
    val chipText = if (e.kind == EventKind.Request) "请求 #${e.reqIndex}" else e.kind.label()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpenEvent(e) }
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        DshChip(chipText, tone = e.kind.chipTone(), modifier = Modifier.width(46.dp), mini = true)
        Text(
            e.title + if (e.detail.isNotEmpty()) " · ${e.detail}" else "",
            style = Type.sec, color = c.ink2,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (showDuration && e.cost.isNotEmpty()) {
            Text(e.cost, style = Type.meta.copy(fontSize = 10.sp), color = c.ink3, modifier = Modifier.width(56.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}

/**
 * S26 事件详情检查器（v13 新增）。
 * 点账本任意记录或「请求 #N」打开：标签页随记录类型变化。
 * 工具记录 = 概述 / 参数 / 结果 / Schema / 计时；模型请求 = 概述 / 选项 / 用量 / 计时。
 * ACP 未提供参数/结果/Schema/用量原文 → 这些页显示空态，不造假。
 */
@Composable
private fun S26EventInspector(event: TrajectoryEvent, onDismiss: () -> Unit) {
    val c = DshTheme.colors
    val ctx = LocalContext.current
    // 段：工具记录(0) / 模型请求(1)；模型请求类 = Request / Model
    var seg by remember { mutableStateOf(if (event.kind == EventKind.Request || event.kind == EventKind.Model) 1 else 0) }
    val toolTabs = listOf("概述", "参数", "结果", "Schema", "计时")
    val reqTabs = listOf("概述", "选项", "用量", "计时")
    val tabs = if (seg == 0) toolTabs else reqTabs
    var tab by remember { mutableStateOf(tabs.size - 1) } // 默认停在「计时」

    Box(Modifier.fillMaxSize()) {
        DshSheet(
            visible = true,
            onDismiss = onDismiss,
            maxHeightRatio = 0.86f,
            header = {
                Row(
                    Modifier.fillMaxWidth().padding(start = Space.sheet, end = 12.dp, top = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("事件详情", style = Type.title, color = c.ink, modifier = Modifier.weight(1f))
                    DshChip(
                        if (event.kind == EventKind.Request) "请求 #${event.reqIndex}" else event.kind.label(),
                        tone = event.kind.chipTone(), mini = true,
                    )
                    DshIBtn(DshIcons.Close, "关闭", onClick = onDismiss)
                }
            },
            content = {
                DshSeg(listOf("工具记录", "模型请求"), seg) { seg = it; tab = (if (it == 0) toolTabs else reqTabs).size - 1 }
                Spacer(Modifier.height(8.dp))
                DshTabs(tabs, tab) { tab = it }
                Spacer(Modifier.height(10.dp))

                when (tab) {
                    0 -> Text(
                        event.detail.ifEmpty { event.title },
                        style = Type.sec.copy(lineHeight = 19.sp), color = c.ink2,
                    )
                    1 -> if (seg == 0) {
                        DshEmpty("暂无参数（ACP 未回传工具入参原文）", icon = DshIcons.Code)
                    } else {
                        DshEmpty("暂无选项（模型/推理档位未回传）", icon = DshIcons.Tune)
                    }
                    2 -> if (seg == 0) {
                        DshEmpty("暂无结果原文", icon = DshIcons.Done)
                    } else {
                        // 模型请求「用量」页：ACP 未回传 Token/KV-Cache，复用计时行占位
                        TimingRows(event)
                    }
                    3 -> if (seg == 0) {
                        DshEmpty("暂无 Schema", icon = DshIcons.Code)
                    } else {
                        // 模型请求「用量」页
                        TimingRows(event)
                    }
                    else -> TimingRows(event)
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    "标签页随记录类型变化：用户/上下文/助手 → 概述·预览·原始内容；系统 → 系统提示词·工具；" +
                        "已压缩 → 概述·原始输出；模型请求 → 概述·选项·用量·计时。",
                    style = Type.meta.copy(fontSize = 10.5.sp, lineHeight = 16.sp), color = c.ink3,
                    modifier = Modifier.fillMaxWidth().topLine(c.line2).padding(top = 10.dp),
                )
            },
            footer = {
                Row(Modifier.fillMaxWidth()) {
                    DshBtn("关闭", small = true, tone = BtnTone.Ghost, modifier = Modifier.weight(1f), onClick = onDismiss)
                    Spacer(Modifier.width(9.dp))
                    DshBtn(
                        "复制事件 JSON", small = true, modifier = Modifier.weight(1f),
                        icon = DshIcons.ContentCopy,
                        onClick = {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            cm?.setPrimaryClip(android.content.ClipData.newPlainText("dsh-event",
                                """{"kind":"${event.kind}","title":"${event.title}","detail":"${event.detail}","cost":"${event.cost}","round":${event.round}}"""))
                            onDismiss()
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun TimingRows(event: TrajectoryEvent) {
    val c = DshTheme.colors
    DshCard {
        Column {
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("总时长", style = Type.rowTitle, color = c.ink)
                    Text("记录以毫秒显示并带千分位", style = Type.sec, color = c.ink3)
                }
                Text(event.cost.ifEmpty { "—" }, style = Type.chip.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = c.ink)
            }
            DshDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("退出码 / 状态", style = Type.rowTitle, color = c.ink)
                }
                Text("—", style = Type.chip, color = c.ink3)
            }
        }
    }
}

private fun Modifier.endDivider(color: Color): Modifier = drawBehind {
    drawLine(color = color, start = Offset(size.width - 0.5f, 0f), end = Offset(size.width - 0.5f, size.height), strokeWidth = 1f)
}