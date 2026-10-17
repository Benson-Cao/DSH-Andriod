package com.dsh.harness.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.harness.mobile.ui.components.*
import com.dsh.harness.mobile.ui.theme.*

// ============================ S6 对话流 · 成果与迭代 ============================

/**
 * S6 成果屏
 * 布局：工作过程（默认折叠）4% ｜ 回答文本 10% ｜ 产物卡 24%(缩略 92px + 三按钮)
 *      ｜ 变更摘要 8% ｜ 建议 chips 8% ｜ 输入条 8%
 *   "产物卡不折叠，保持在首屏可见范围内"
 *   v7 修复：摘要文件名不再折行、按钮文案收短不再与相邻按钮打架。
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
) {
    val c = DshTheme.colors

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

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // 工作过程默认折叠 —— v7 起不再默认展开
            DshFold(
                title = "工作过程 · 9 步 · 点开看明细",
                detail = "read_file · sales_q3.xlsx\nbash · python aggregate.py\n" +
                    "web_search · 2025 年同期口径\nwrite_file · charts/*.png ×9\n+4 步 · 展开见 S8 轨迹",
                icon = null,
                initiallyOpen = false,
            )

            DshAnswerText(answer)

            // —— 产物卡：缩略 74×92 + 标题 + 元信息 + 三按钮 ——
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 缩略：设计稿 .thumb 74×92 渐变占位
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
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "PPTX",
                            style = Type.meta.copy(
                                fontSize = 9.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                            ),
                            color = Color.White,
                        )
                    }

                    Column(Modifier.weight(1f)) {
                        // v7：文件名不再折行 —— maxLines=1 + ellipsis
                        Text(
                            "Q3数据汇总.pptx",
                            style = Type.body.copy(fontSize = 14.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                            color = c.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "9 页 · 1.4 MB · 16:9\n19:52 写入工作区",
                            style = Type.meta.copy(lineHeight = 18.4.sp),
                            color = c.ink3,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DshChip("1280×720", mini = true)
                            DshChip("9 图", icon = DshIcons.Image, mini = true)
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

            // —— 变更摘要 ——
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
                    Text("工作区内修改 · 6 个文件", style = Type.rowTitle, color = c.ink, maxLines = 1)
                    Text("aggregate.py 等 6 个文件已写入", style = Type.sec, color = c.ink3, maxLines = 1)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("+214", style = Type.sec.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = c.ok)
                    Text("−38", style = Type.sec.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = c.danger)
                }
                Icon(DshIcons.ChevronRight, null, Modifier.size(17.dp), tint = c.ink3)
            }

            DshSecTitle("继续迭代", Modifier.padding(top = 0.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                DshChip("把环比也做成柱状", icon = DshIcons.AddChart, onClick = {})
                DshChip("换深色模板", onClick = {})
            }
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                DshChip("导出 PDF", onClick = {})
                DshChip("压缩到 6 页", onClick = {})
            }

            Spacer(Modifier.height(4.dp))
        }

        // —— 输入条 ——
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
                Text("继续提要求…", style = Type.input, color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                DshChip("V4-Pro Max", tone = ChipTone.On, mini = true)
                DshIBtn(DshIcons.ArrowUpward, "发送", bg = c.brand, tint = Color.White, onClick = {})
            }
        }
    }
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
    initialTab: Int = 0,
    onBack: () -> Unit,
    events: List<TrajectoryEvent> = emptyList(),
    protocolLog: List<String> = emptyList(),
) {
    val c = DshTheme.colors
    var tab by remember { mutableStateOf(initialTab) }

    DshScreen(modifier) {
        DshNavBar(
            onBack = onBack,
            title = "轨迹",
            stacked = true,
            actions = { DshIBtn(DshIcons.FilterList, "筛选", onClick = {}) },
        )

        Column(Modifier.padding(horizontal = Space.lg).padding(top = 10.dp)) {
            DshTabs(listOf("进展", "步骤", "日志"), tab) { tab = it }
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
                0 -> if (events.isEmpty()) {
                    DshEmpty("本次会话还没有事件", icon = DshIcons.Timeline)
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.xl))
                            .background(c.card)
                            .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                            .padding(vertical = 11.dp),
                    ) {
                        StatCell("${events.size}", "事件", c.ink, Modifier.weight(1f), true)
                        StatCell(
                            "${events.count { it.tag == "T" }}", "工具调用",
                            c.ok, Modifier.weight(1f), true,
                        )
                        StatCell(
                            "${events.count { it.tag == "U" }}", "用户输入",
                            c.brand, Modifier.weight(1f), true,
                        )
                        StatCell(
                            "${events.count { it.tag == "A" }}", "子代理",
                            c.purple, Modifier.weight(1f), false,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    events.forEachIndexed { i, e ->
                        TrajectoryRow(e, isLast = i == events.lastIndex)
                    }
                }

                1 -> {
                    val toolEvents = events.filter { it.tag == "T" }
                    if (toolEvents.isEmpty()) {
                        DshEmpty("没有工具调用步骤", icon = DshIcons.Done)
                    } else {
                        DshSecTitle("工具调用", Modifier.padding(top = 0.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.xl))
                                .background(c.card)
                                .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
                        ) {
                            toolEvents.forEachIndexed { i, e ->
                                StageRow(e.title, e.detail, DshIcons.Done, ChipTone.Ok)
                                if (i != toolEvents.lastIndex) DshDivider()
                            }
                        }
                    }
                }

                2 -> if (protocolLog.isEmpty()) {
                    DshEmpty("暂无日志", icon = DshIcons.Article)
                } else {
                    DshCode(protocolLog.takeLast(80).joinToString("\n"), fontSize = 10.5.sp)
                }
            }
        }

        // 动作条：恢复/分叉/回放对实时事件流没有意义，只留导出
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg)
                .padding(bottom = 4.dp),
        ) {
            DshBtn(
                "导出日志",
                small = true,
                tone = BtnTone.Ghost,
                modifier = Modifier.weight(1f),
                onClick = {},
            )
        }
    }
}

/** 时间线事件（由 DshViewModel 从 session/update 归并而来） */
data class TrajectoryEvent(
    val tag: String,      // S 系统 / U 用户 / M 模型 / T 工具 / A 子代理 / ! 权限
    val title: String,
    val detail: String,
)

@Composable
private fun StatCell(
    value: String,
    label: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    divider: Boolean = false,
) {
    val c = DshTheme.colors
    Column(
        modifier.then(if (divider) Modifier.endDivider(c.line2) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            style = Type.body.copy(fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            color = valueColor,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(label, style = Type.meta.copy(fontSize = 10.sp), color = c.ink3)
    }
}

@Composable
private fun TrajectoryRow(node: TrajectoryEvent, isLast: Boolean) {
    val c = DshTheme.colors
    // 徽章配色由 tag 决定，与设计稿 .tl-i 的语义色一致
    val (bg, fg) = when (node.tag) {
        "U" -> c.brand to Color.White
        "M" -> c.soft to c.brand
        "T" -> c.okSoft() to c.ok
        "A" -> c.purpleSoft() to c.purple
        "!" -> c.warnSoft() to c.warn
        else -> c.line2 to c.ink2
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // 左轨：22px 方块 + 连线
        Column(Modifier.width(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(bg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    node.tag,
                    style = Type.meta.copy(
                        fontSize = if (node.tag.length > 1) 10.sp else 12.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    ),
                    color = fg,
                )
            }
            if (!isLast) {
                Box(
                    Modifier
                        .width(1.5.dp)
                        .height(if (node.detail.length > 30) 40.dp else 28.dp)
                        .background(c.line),
                )
            }
        }
        Column(Modifier.weight(1f).padding(bottom = 14.dp)) {
            Text(
                node.title,
                style = Type.chip.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                color = c.ink,
                maxLines = 2,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                node.detail,
                style = Type.meta.copy(lineHeight = 16.sp),
                color = c.ink3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StageRow(title: String, subtitle: String, icon: ImageVector, tone: ChipTone) {
    val c = DshTheme.colors
    val ibg = if (tone == ChipTone.Ok) c.okSoft() else c.soft
    val ifg = if (tone == ChipTone.Ok) c.ok else c.brand
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(Metrics.ICON_BOX)
                .clip(RoundedCornerShape(Radius.md))
                .background(ibg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(19.dp), tint = ifg)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.rowTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = Type.sec, color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(DshIcons.ChevronRight, null, Modifier.size(18.dp), tint = c.ink3)
    }
}

private fun Modifier.endDivider(color: Color): Modifier = drawBehind {
    drawLine(
        color = color,
        start = Offset(size.width - 0.5f, 0f),
        end = Offset(size.width - 0.5f, size.height),
        strokeWidth = 1f,
    )
}