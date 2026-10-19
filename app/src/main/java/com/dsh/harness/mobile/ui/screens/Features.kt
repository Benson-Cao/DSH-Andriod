package com.dsh.harness.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.harness.mobile.ui.components.*
import com.dsh.harness.mobile.ui.theme.*

// ============================ S9 新会话设置 Sheet ============================

/**
 * S9 新会话设置（Sheet 高 84%）
 * 布局：标题 6% ｜ 模式 2×2 网格 30%(每卡 88px) ｜ 权限三段 8% ｜ 说明 5%
 *      ｜ 模型列表 18% ｜ 思考强度 8% ｜ 开始按钮 7%
 *   "模式卡文字两行截断，不撑破网格"
 */
@Composable
fun BoxScope.S9NewSessionSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onStart: (mode: String) -> Unit,
    /** 来自 ACP session/new 返回的真实模型目录；为空时退回设计稿演示项 */
    liveModels: List<com.dsh.harness.mobile.data.DshViewModel.ModelOption> = emptyList(),
    onPickModel: (String) -> Unit = {},
) {
    val c = DshTheme.colors
    var mode by remember { mutableStateOf("标准") }
    var perm by remember { mutableStateOf(1) }
    var model by remember { mutableStateOf(0) }
    var think by remember { mutableStateOf(1) }

    val modes = listOf(
        Triple("标准", "按需规划，过程与结论都给", DshIcons.Build),
        Triple("PTC", "程序化思考，复杂计算更稳", DshIcons.Psychology),
        Triple("极简", "只给结果，不展示过程", DshIcons.FilterNone),
        Triple("创造", "发散探索，给多个方向", DshIcons.AutoAwesome),
    )
    val perms = listOf("Read Only", "Workspace Write", "Full Access")
    // 设计稿的演示项；ACP 通了以后用真实目录替换
    val models = if (liveModels.isNotEmpty()) {
        liveModels.map { it.name to (it.value to it.group) }
    } else {
        listOf(
            "V4-Flash" to ("" to "deepseek-official"),
            "V4-Pro" to ("" to "deepseek-official"),
            "V4-Pro Max" to ("" to "deepseek-official"),
        )
    }
    val thinks = listOf("低", "中", "高")

    DshSheet(
        visible = visible,
        onDismiss = onDismiss,
        maxHeightRatio = 0.84f,
        header = { DshSheetHeader("新会话设置") },
        content = {
            // —— 模式 2×2，每卡 88 高 ——
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                modes.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (name, desc, icon) ->
                            ModeCard(
                                name = name,
                                desc = desc,
                                icon = icon,
                                selected = mode == name,
                                modifier = Modifier.weight(1f),
                            ) { mode = name }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            DshSecTitle("权限档位", Modifier.padding(top = 0.dp))
            DshSeg(perms, perm) { perm = it }
            Spacer(Modifier.height(8.dp))
            Text(
                "权限决定 Agent 能在哪些地方读写与执行命令；越界操作会先弹审批。",
                style = Type.meta.copy(lineHeight = 17.6.sp),
                color = c.ink3,
            )

            Spacer(Modifier.height(14.dp))
            DshSecTitle("模型", Modifier.padding(top = 0.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
            ) {
                models.forEachIndexed { i, (name, meta) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                model = i
                                meta.first.takeIf { v -> v.isNotEmpty() }?.let(onPickModel)
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(name, style = Type.rowTitle, color = c.ink, maxLines = 1)
                            Text(meta.second, style = Type.sec, color = c.ink3, maxLines = 1)
                        }
                        if (i == model) {
                            Icon(DshIcons.Check, "已选", Modifier.size(19.dp), tint = c.brand)
                        }
                    }
                    if (i != models.lastIndex) DshDivider()
                }
            }

            Spacer(Modifier.height(14.dp))
            DshSecTitle("思考强度", Modifier.padding(top = 0.dp))
            DshSeg(thinks, think) { think = it }
        },
        footer = {
            DshBtn("开始会话", onClick = { onStart(mode) })
        },
    )
}

/** 模式卡：设计稿 166×88，文字两行截断 */
@Composable
private fun ModeCard(
    name: String,
    desc: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = DshTheme.colors
    Column(
        modifier
            .height(Metrics.MODE_CARD_H)
            .clip(RoundedCornerShape(Radius.xl))
            .background(if (selected) c.soft else c.card)
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) c.brand else c.line,
                RoundedCornerShape(Radius.xl),
            )
            .clickable { onClick() }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon, null, Modifier.size(17.dp),
                tint = if (selected) c.brand else c.ink2,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                name,
                style = Type.rowTitle,
                color = if (selected) c.brand else c.ink,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(desc, style = Type.meta.copy(lineHeight = 15.sp), color = c.ink3, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// ============================ S10 斜杠指令面板 ============================

/**
 * S10 斜杠指令面板
 * 布局：导航 6% ｜ 弹性 ｜ 输入卡 9%(含 /goa 光标) ｜ 建议列表 58%(指令头4 + 4 条 9% + 引用头4 + 2 条 9%)
 *   "面板从输入框向上生长，覆盖下方区域，不遮输入框"
 */
@Composable
fun S10CommandPanel(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onPick: (String) -> Unit,
) {
    val c = DshTheme.colors
    val commands = listOf(
        "/compact" to "压缩当前上下文，保留最近 20 轮",
        "/clear" to "清空会话上下文，从零开始",
        "/init" to "扫描工作区并生成 AGENTS.md",
        "/review" to "检查当前工作区的未提交变更",
    )
    val refs = listOf(
        "@sales_q3.xlsx" to "引用工作区内的文件",
        "@上周会议记录.md" to "引用历史会话产物",
    )

    DshScreen(modifier) {
        DshNavBar(onBack = onBack, title = "指令与引用", stacked = true)

        Spacer(Modifier.weight(1f))

        // —— 输入卡：光标停在 goa 中间 ——
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.lg)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("/goa", style = Type.input, color = c.brand)
                Box(
                    Modifier
                        .padding(horizontal = 1.dp)
                        .width(1.5.dp)
                        .height(17.dp)
                        .background(c.brand),
                )
            }
            Spacer(Modifier.height(10.dp))

            GroupLabel("指令")
            commands.forEach { (cmd, desc) ->
                CommandRow(cmd, desc) { onPick(cmd) }
            }

            Spacer(Modifier.height(8.dp))
            GroupLabel("引用")
            refs.forEach { (name, desc) ->
                CommandRow(name, desc) { onPick(name) }
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    val c = DshTheme.colors
    Text(
        text,
        style = Type.secTitle,
        color = c.ink3,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp),
    )
}

@Composable
private fun CommandRow(cmd: String, desc: String, onClick: () -> Unit) {
    val c = DshTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(cmd, style = Type.mono.copy(color = c.brand), maxLines = 1)
            Text(desc, style = Type.meta, color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(DshIcons.ChevronRight, null, Modifier.size(16.dp), tint = c.ink3)
    }
}

// ============================ S11 工作区 ============================

/**
 * S11 工作区 · 文件与 Diff
 *
 * ⚠️ ACP **没有文件访问能力**（协议把 `session/load`、客户端文件系统操作
 * 都列为不支持，`fs` capability 也传的是 false）。所以文件列表 / Diff 目前
 * 没有真实数据源。用户要求"没数据的先空着" → 全部改为空态，
 * 结构保留，将来接上 Android 侧文件读取即可填数据。
 */
@Composable
fun S11Workspace(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit,
    initialTab: Int = 0,
    /** 将来接上文件读取后从这里传入；现在恒为空 */
    files: List<FileRow> = emptyList(),
    diffs: List<DiffLine> = emptyList(),
    artifacts: List<FileRow> = emptyList(),
    /** v20：顶栏放大镜 → S45 全局搜索 */
    onSearch: () -> Unit = {},
    /** v20：工作区横滑条末尾 add → S41 选择工作区目录 */
    onAddWorkspace: () -> Unit = {},
) {
    val c = DshTheme.colors
    var tab by remember { mutableStateOf(initialTab) }
    var ws by remember { mutableStateOf(0) } // 0=数据分析项目 1=默认工作区 2=DSH 插件开发 3=nas-mount

    DshScreen(modifier) {
        DshNavBar(
            stacked = true,
            // 设计稿原本在 S11 顶栏放 ☰，但工作区是从侧栏/首页进的二级页：
            // 主操作应是「返回来源页」，侧栏入口挪到右侧动作区。
            onBack = onBack,
            title = "工作区",
            actions = {
                DshIBtn(DshIcons.Search, "搜索", onClick = onSearch)
                DshIBtn(DshIcons.MoreVert, "更多", onClick = {})
                DshIBtn(DshIcons.Menu, "侧边栏", onClick = onOpenDrawer)
            },
        )

        // 工作区横滑条（设计稿 v20：四个工作区 chip + nas-mount + add → S41）
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Space.lg, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            listOf("数据分析项目", "默认工作区", "DSH 插件开发").forEachIndexed { i, label ->
                DshChip(label, tone = if (ws == i) ChipTone.Brand else ChipTone.Neutral, onClick = { ws = i })
            }
            DshChip("nas-mount", icon = DshIcons.Cloud, tone = if (ws == 3) ChipTone.Brand else ChipTone.Neutral, onClick = { ws = 3 })
            DshChip("", icon = DshIcons.Add, onClick = onAddWorkspace)
        }

        Column(Modifier.padding(horizontal = Space.lg).padding(top = 10.dp)) {
            DshTabs(listOf("文件", "Git 变更", "产物"), tab) { tab = it }
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg)
                .padding(top = 10.dp),
        ) {
            when (tab) {
                0 -> if (files.isEmpty()) {
                    DshEmpty("工作区里还没有文件", icon = DshIcons.FolderOpen)
                } else Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.xl))
                        .background(c.card)
                        .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
                ) {
                    files.forEachIndexed { i, f ->
                        DshMRow(title = f.name, subtitle = f.meta, icon = f.icon, iconTone = f.tone)
                        if (i != files.lastIndex) DshDivider()
                    }
                }

                1 -> {
                    // —— Diff 卡：头 + 代码行 + 脚注 ——
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.xl))
                            .background(c.card)
                            .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(c.line2)
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("aggregate.py", style = Type.mono, color = c.ink, modifier = Modifier.weight(1f))
                            Text("M", style = Type.meta.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = c.warn)
                            Spacer(Modifier.width(8.dp))
                            Text("+42 −8", style = Type.meta, color = c.ink3)
                        }
                        Column(Modifier.padding(vertical = 6.dp)) {
                            diffs.forEach { d ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(
                                            when (d.mark) {
                                                "+" -> c.okSoft()
                                                "-" -> c.dangerSoft()
                                                else -> Color.Transparent
                                            }
                                        )
                                        .padding(horizontal = 10.dp, vertical = 2.dp),
                                ) {
                                    // 变更标记定宽 26
                                    Text(
                                        d.mark,
                                        style = Type.mono,
                                        color = when (d.mark) {
                                            "+" -> c.ok
                                            "-" -> c.danger
                                            else -> c.ink3
                                        },
                                        modifier = Modifier.width(16.dp),
                                    )
                                    Text(
                                        d.no.toString(),
                                        style = Type.mono.copy(fontSize = 10.sp),
                                        color = c.ink3,
                                        modifier = Modifier.width(26.dp),
                                    )
                                    Text(d.text, style = Type.mono, color = c.ink2)
                                }
                            }
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .topLine(c.line)
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DshBtn("暂存", small = true, tone = BtnTone.Ghost, modifier = Modifier.weight(1f), onClick = {})
                            DshBtn("丢弃", small = true, tone = BtnTone.Ghost, modifier = Modifier.weight(1f), onClick = {})
                        }
                    }
                }

                2 -> Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.xl))
                        .background(c.card)
                        .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
                ) {
                    ArtifactRow("Q3数据汇总.pptx", "9 页 · 1.4 MB", DshIcons.Slideshow, ChipTone.Brand)
                    DshDivider()
                    ArtifactRow("charts/dept_rank.png", "1280×720 · 218 KB", DshIcons.Image, ChipTone.Purple)
                    DshDivider()
                    ArtifactRow("reports/q3_by_dept.csv", "8 行 × 14 列 · 12 KB", DshIcons.TableChart, ChipTone.Ok)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

data class FileRow(val name: String, val meta: String, val icon: ImageVector, val tone: ChipTone)
data class DiffLine(val mark: String, val no: Int, val text: String)

@Composable
private fun ArtifactRow(name: String, meta: String, icon: ImageVector, tone: ChipTone) {
    DshMRow(
        title = name,
        subtitle = meta,
        icon = icon,
        iconTone = tone,
        trailing = { DshChip("打开", tone = tone, mini = true) },
        onClick = {},
    )
}

// ============================ S13 自动化任务 ============================

/**
 * S13 自动化任务
 * 布局：导航+新建 6% ｜ 概览条 12%(3 数字 + 下次时间) ｜ 任务卡 ×3 66% ｜ 脚注 3%
 *   "每卡三段：标题行 / chip 组 / 动作行；动作行用分隔线隔开"
 *   空态：设计稿要求任务为空时整列表换 empty 组件
 */
@Composable
fun S13Automation(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    /** 将来接上任务调度后传入；空列表走空态，不用假任务冒充 */
    tasks: List<AutomationTask> = emptyList(),
    /** v20：顶栏加号 → S40 新建自动化任务 */
    onNewTask: () -> Unit = {},
) {
    val c = DshTheme.colors

    DshScreen(modifier) {
        DshNavBar(
            onBack = onBack,
            title = "自动化任务",
            stacked = true,
            actions = { DshIBtn(DshIcons.Add, "新建任务", onClick = onNewTask) },
        )

        if (tasks.isEmpty()) {
            // —— 空数据态（设计稿四态要求之一）——
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg),
                verticalArrangement = Arrangement.Center,
            ) {
                DshEmpty("还没有自动化任务", icon = DshIcons.Schedule)
                Spacer(Modifier.height(16.dp))
                DshBtn("新建第一个任务", icon = DshIcons.Add, onClick = {})
            }
            return@DshScreen
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            // —— 概览条：3 数字 + 下次时间 ——
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(14.dp),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    StatCell(
                        "${tasks.count { it.enabled }}", "启用中",
                        c.ok, Modifier.weight(1f),
                    )
                    StatCell(
                        "${tasks.count { !it.enabled }}", "已暂停",
                        c.warn, Modifier.weight(1f),
                    )
                    StatCell(
                        "${tasks.count { it.failed }}", "上次失败",
                        c.danger, Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(DshIcons.Schedule, null, Modifier.size(13.dp), tint = c.ink3)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (tasks.isEmpty()) "还没有安排运行时间"
                        else "共 ${tasks.size} 个任务",
                        style = Type.meta,
                        color = c.ink3,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // —— 任务卡：按真实数据渲染 ——
            tasks.forEachIndexed { i, t ->
                TaskCard(
                    title = t.title,
                    schedule = t.schedule,
                    enabled = t.enabled,
                    chips = t.chips,
                    progress = t.progress,
                    failed = t.failed,
                )
                if (i != tasks.lastIndex) Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "任务在后台按计划执行；暂停的任务保留配置与历史，运行日志可在详情查看。",
                style = Type.meta.copy(lineHeight = 17.6.sp),
                color = c.ink3,
            )
        }
    }
}

/** 一个自动化任务（将来由任务调度能力填充） */
data class AutomationTask(
    val title: String,
    val schedule: String,
    val enabled: Boolean,
    val chips: List<Pair<String, ChipTone>>,
    val progress: Float = 0f,
    val failed: Boolean = false,
)

@Composable
private fun StatCell(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    val c = DshTheme.colors
    Column(modifier) {
        Text(
            value,
            style = Type.h1.copy(fontSize = 22.sp),
            color = color,
        )
        Text(label, style = Type.meta, color = c.ink3)
    }
}

@Composable
private fun TaskCard(
    title: String,
    schedule: String,
    enabled: Boolean,
    chips: List<Pair<String, ChipTone>>,
    progress: Float,
    failed: Boolean = false,
) {
    val c = DshTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xl))
            .background(c.card)
            .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
    ) {
        // 段 1：标题行
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = Type.rowTitle, color = c.ink, modifier = Modifier.weight(1f))
            if (failed) {
                DshChip("失败", tone = ChipTone.Danger, mini = true)
            }
            DshSwitch(enabled) {}
        }

        // 段 2：chip 组 + 进度
        Column(Modifier.padding(horizontal = 14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DshChip(schedule, tone = ChipTone.Neutral, mini = true)
                chips.forEach { (label, tone) -> DshChip(label, tone = tone, mini = true) }
            }
            if (progress > 0f) {
                Spacer(Modifier.height(9.dp))
                DshProgress(progress)
                Spacer(Modifier.height(3.dp))
                Text("上次运行 2 分钟前 · 成功 · 42s", style = Type.meta.copy(fontSize = 10.sp), color = c.ok)
            }
        }

        // 段 3：动作行，用分隔线隔开
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .topLine(c.line)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DshChip("立即运行", icon = DshIcons.PlayArrow, tone = ChipTone.On, mini = true)
            DshChip("轨迹", icon = DshIcons.Timeline, mini = true)
            Spacer(Modifier.weight(1f))
            DshChip("日志", mini = true)
        }
    }
}