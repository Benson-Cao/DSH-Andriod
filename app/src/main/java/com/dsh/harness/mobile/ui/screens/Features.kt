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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.dsh.harness.SafConfig
import com.dsh.harness.SafTree
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
    /** v30：真实工作区 cwd（来自 DshViewModel.workspace）；null = 还没选 */
    workspace: String? = null,
    initialTab: Int = 0,
    /** v20：顶栏放大镜 → S45 全局搜索 */
    onSearch: () -> Unit = {},
    /** v20：工作区横滑条末尾 add → S41 选择工作区目录 */
    onAddWorkspace: () -> Unit = {},
    /** v30.8：Git 页签 → S47 提交流程（S47 此前是孤儿路由，无任何入口） */
    onOpenGitCommit: () -> Unit = {},
    /** v30.9：顶栏「更多」→ 归档会话 S48（此前是空回调） */
    onOpenArchived: () -> Unit = {},
) {
    val c = DshTheme.colors
    val ctx = LocalContext.current.applicationContext
    var tab by remember { mutableStateOf(initialTab) }

    // v30：真实读取 SAF 授权态，并拉取已授权目录下的真实文件列表（IO 放到后台）
    val safOn = remember { SafConfig.isConfigured(ctx) }
    var safEntries by remember { mutableStateOf<List<SafTree.Entry>>(emptyList()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(safOn) {
        safEntries = if (safOn) {
            withContext(Dispatchers.IO) { SafTree.list(ctx, "") }
        } else emptyList()
    }

    DshScreen(modifier) {
        DshNavBar(
            stacked = true,
            // 设计稿原本在 S11 顶栏放 ☰，但工作区是从侧栏/首页进的二级页：
            // 主操作应是「返回来源页」，侧栏入口挪到右侧动作区。
            onBack = onBack,
            title = "工作区",
            actions = {
                DshIBtn(DshIcons.Search, "搜索", onClick = onSearch)
                DshIBtn(DshIcons.MoreVert, "更多", onClick = onOpenArchived)
                DshIBtn(DshIcons.Menu, "侧边栏", onClick = onOpenDrawer)
            },
        )

        // 工作区横滑条：真实只有「当前工作区」一个（来自 VM 的 cwd）；
        // 未选择时给一个入口 chip 引导去 S41 选目录。外部存储若已授权也单列。
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Space.lg, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (workspace != null) {
                DshChip(workspace.substringAfterLast('/'), tone = ChipTone.Brand)
            } else {
                DshChip("选择工作区", tone = ChipTone.Neutral, onClick = onAddWorkspace)
            }
            if (safOn) {
                DshChip(
                    "外部存储 · " + SafConfig.displayName(ctx),
                    icon = DshIcons.FolderOpen,
                    tone = ChipTone.Ok,
                )
            }
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
                // —— 文件：真实优先（SAF 已授权则列真实目录），否则诚实空态 ——
                0 -> when {
                    safOn && safEntries.isNotEmpty() -> Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.xl))
                            .background(c.card)
                            .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
                    ) {
                        safEntries.forEachIndexed { i, e ->
                            DshMRow(
                                title = e.name,
                                subtitle = if (e.isDir) "文件夹" else fmtSize(e.size),
                                icon = if (e.isDir) DshIcons.FolderOpen else DshIcons.Article,
                                iconTone = if (e.isDir) ChipTone.Brand else ChipTone.Neutral,
                            )
                            if (i != safEntries.lastIndex) DshDivider()
                        }
                    }
                    workspace != null -> Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.xl))
                            .background(c.card)
                            .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
                    ) {
                        DshMRow(
                            title = workspace.substringAfterLast('/'),
                            subtitle = workspace,
                            icon = DshIcons.FolderOpen,
                            iconTone = ChipTone.Brand,
                        )
                    }
                    else -> DshEmpty("工作区里还没有文件", icon = DshIcons.FolderOpen)
                }

                // —— Git 变更：本 harness 无 Git 后端，诚实空态 ——
                // v30.8：给出唯一入口 → S47 提交流程（该页自带"示例数据"标注）
                1 -> Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    DshEmpty("暂无 Git 变更记录", icon = DshIcons.Sync)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "本机运行环境未内置 Git，无法读取真实变更。",
                        style = Type.meta.copy(fontSize = 10.5.sp, lineHeight = 16.sp),
                        color = c.ink3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    DshBtn(
                        "查看提交流程",
                        small = true,
                        tone = BtnTone.Ghost,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onOpenGitCommit,
                    )
                }

                // —— 产物：无真实产物数据源，诚实空态 ——
                2 -> DshEmpty("还没有生成产物", icon = DshIcons.Slideshow)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 字节数 → 人类可读（用于 SAF 文件列表） */
private fun fmtSize(bytes: Long): String {
    if (bytes <= 0) return "—"
    val units = listOf("B", "KB", "MB", "GB")
    var v = bytes.toDouble()
    var u = 0
    while (v >= 1024 && u < units.lastIndex) { v /= 1024; u++ }
    return "%.1f %s".format(v, units[u])
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
                DshBtn("新建第一个任务", icon = DshIcons.Add, onClick = onNewTask)
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