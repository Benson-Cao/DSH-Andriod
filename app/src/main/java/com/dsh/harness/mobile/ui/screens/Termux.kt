package com.dsh.harness.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.harness.mobile.ui.components.*
import com.dsh.harness.mobile.ui.theme.*

/**
 * S27 Termux 终端 · 本机启动 dsh（v14 新增，v20 重做为窗口化终端）
 *
 * v20 重做要点（设计稿原文）：
 *   - 终端区改成**带标题栏的独立窗口**（深色岛 + 描边 + 阴影），
 *     在浅色与深色主题下都有清晰边界；
 *   - 状态栏区域颜色固定深色，不再串色；
 *   - 快捷指令、服务状态、动作按钮按顺序排布，取消撑高的弹性空白；
 *   - 服务卡去掉「进程守护」行（留 工作区 / 会话存储 / 日志·导出）；
 *   - 标题栏右侧 swap_horiz 打开「终端会话」列表（tsx Sheet），add 去 S49；
 *   - 动作行：打开 UI(flex 1.5) / 停止(flex 1) / +(46dp → S49)。
 *
 * ⚠️ 与设计稿的一处有意偏差：设计稿 S27 取消了顶栏（只留深色状态栏条）。
 * 但 S27 是二级页（侧栏 / 设置 / svc Sheet / S49 均可进），
 * 按本工程「二级页必须有可见返回键」的硬规矩（v14 真机踩坑 + check-back-button.sh），
 * 这里保留一条**深色 slim 顶栏**（返回 + Termux + 新建），
 * 既满足"状态栏区域固定深色不串色"，又不丢返回 affordance。
 *
 * 命令与约束取自官方运行方式：
 *   - npx @deepseek-ai/dsh web 拉起 Web UI（默认本机访问，--public-url 才外放）
 *   - 端口占用用 --port 改端口
 *   - --profile headless 一次性批处理，跑完即退不留常驻进程
 *   - 长任务在 Android 上需 termux-wake-lock + 通知常驻才不被系统回收
 *
 * 职责划清：Termux 只起服务和看日志，任务进展与轨迹回到 Harness 界面。
 * 版本号 / 端口号 / 路径为示意占位。
 */

// —— 终端固定配色（脱离 App 主题，浅深两档都清晰）——
private val TermBg = Color(0xFF0B0F14)
private val TermTitleBar = Color(0xFF151C26)
private val TermWinBorder = Color(0xFF2C3747)
private val TermFg = Color(0xFFC9D6E4)
private val TermTitle = Color(0xFFE7EDF5)
private val TermIcon = Color(0xFF9FB4C9)
private val TermPrompt = Color(0xFF7A8AA0)
private val TermOk = Color(0xFF7FE58A)
private val TermKey = Color(0xFF4D6BFE)
private val TermWarn = Color(0xFFC97A00)
private val TermErr = Color(0xFFE02E3A)
private val TermPath = Color(0xFFA9B7FF)

@Composable
fun S27Termux(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenUi: () -> Unit,
    onStop: () -> Unit,
    onExportLog: () -> Unit,
    onCopyCommand: (String) -> Unit = {},
    /** 标题栏 add / 动作行 + / tsx Sheet「新建会话」→ S49 */
    onNewSession: () -> Unit = {},
) {
    val c = DshTheme.colors
    // 当前终端会话：0 = bash·0（dsh web），1 = bash·1（tail 日志）。
    // 设计稿 S49 脚注：「会话创建后可在终端窗口的会话列表里来回切换」。
    var session by remember { mutableStateOf(0) }
    var tsxOpen by remember { mutableStateOf(false) }

    // tsx Sheet 要盖住整屏（含状态栏）→ S27 自己套 Box：DshScreen 与 Sheet 同级
    Box(Modifier.fillMaxSize()) {
        DshScreen(modifier) {
            // —— 深色 slim 顶栏（状态栏区域固定深色，不串色；保留返回 affordance）——
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(TermBg)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DshIBtn(DshIcons.ArrowBack, "返回", tint = TermIcon, onClick = onBack)
                Text("Termux", style = Type.title, color = TermTitle, modifier = Modifier.weight(1f))
                DshIBtn(DshIcons.Add, "新建会话", tint = TermIcon, onClick = onNewSession)
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // —— 终端窗口：标题栏 + 深色岛（v20 重做核心）——
                Column(
                    Modifier
                        .fillMaxWidth()
                        .shadow(10.dp, RoundedCornerShape(14.dp), clip = false)
                        .clip(RoundedCornerShape(14.dp))
                        .background(TermBg)
                        .border(1.dp, TermWinBorder, RoundedCornerShape(14.dp)),
                ) {
                    // 标题栏：状态点 + bash·N + dsh web chip + swap_horiz + add
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(TermTitleBar)
                            .padding(horizontal = 11.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(TermOk, RoundedCornerShape(50)),
                        )
                        Text(
                            "bash · $session",
                            style = Type.sec.copy(fontWeight = FontWeight.SemiBold),
                            color = TermTitle,
                            maxLines = 1,
                        )
                        Box(
                            Modifier
                                .height(19.dp)
                                .background(Color(0xFF1E2A38), RoundedCornerShape(Radius.xl))
                                .padding(horizontal = 7.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (session == 0) "dsh web" else "tail",
                                style = Type.meta.copy(fontSize = 9.5.sp),
                                color = TermOk,
                                maxLines = 1,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        DshIBtn(
                            DshIcons.SwapHoriz, "切换终端会话",
                            tint = TermIcon, size = 26.dp,
                            onClick = { tsxOpen = true },
                        )
                        DshIBtn(
                            DshIcons.Add, "新建会话",
                            tint = TermIcon, size = 26.dp,
                            onClick = onNewSession,
                        )
                    }

                    // 终端输出（深色等宽）
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
                        val lines = if (session == 0) TERMINAL_LINES else TERMINAL_TAIL_LINES
                        lines.forEach { line ->
                            Text(
                                buildAnnotatedString {
                                    line.forEach { (t, tone) ->
                                        val col = when (tone) {
                                            TermTone.FG -> TermFg
                                            TermTone.PROMPT -> TermPrompt
                                            TermTone.OK -> TermOk
                                            TermTone.KEY -> TermKey
                                            TermTone.WARN -> TermWarn
                                            TermTone.ERR -> TermErr
                                            TermTone.PATH -> TermPath
                                        }
                                        withStyle(SpanStyle(color = col)) { append(t) }
                                    }
                                },
                                style = Type.mono.copy(fontSize = 12.sp, lineHeight = 18.sp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                // —— 快捷指令横滑 ——
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    TERMINAL_QUICK.forEach { cmd ->
                        DshChip(cmd, mini = true, onClick = { onCopyCommand(cmd) })
                    }
                }

                // —— 本机服务状态卡（v20：去掉「进程守护」行）——
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.xl))
                        .background(c.card)
                        .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                        .padding(13.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(DshIcons.AdsClick, null, Modifier.size(17.dp), tint = c.brand)
                        Text(
                            "本机服务",
                            style = Type.secTitle,
                            color = c.ink,
                            modifier = Modifier.weight(1f),
                        )
                        DshChip(":3081 运行中", tone = ChipTone.Ok, mini = true)
                    }
                    TermRow("工作区", "~/storage/shared/Documents/dsh-ws", mono = true)
                    TermRow("会话存储", "~/.dsh/sessions · SQLite", mono = true)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "日志",
                            style = Type.sec.copy(color = c.ink3),
                            modifier = Modifier.width(66.dp),
                        )
                        Text(
                            "~/.dsh/logs/web.log",
                            style = Type.mono.copy(fontSize = 11.5.sp),
                            color = c.ink2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "导出",
                            style = Type.sec,
                            color = c.brand,
                            modifier = Modifier.clickable { onExportLog() },
                        )
                    }
                }

                // —— 动作行：打开 UI(1.5) / 停止(1) / +(46 → S49)——
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DshBtn(
                        "打开 UI",
                        icon = DshIcons.OpenInNew,
                        modifier = Modifier.weight(1.5f),
                        onClick = onOpenUi,
                    )
                    DshBtn(
                        "停止",
                        tone = BtnTone.Ghost,
                        modifier = Modifier.weight(1f),
                        onClick = onStop,
                    )
                    TermAddBtn(onClick = onNewSession)
                }

                Text(
                    "Termux 只管起服务与看日志；任务进展与轨迹回到 Harness 界面看。长任务需通知常驻 + 唤醒锁。",
                    style = Type.meta.copy(lineHeight = 16.8.sp),
                    color = c.ink3,
                )
            }
        }

        // —— 终端会话列表（tsx Sheet）：bash·0 / bash·1 / 新建会话 ——
        S27SessionSheet(
            visible = tsxOpen,
            current = session,
            onDismiss = { tsxOpen = false },
            onSelect = { session = it; tsxOpen = false },
            onNewSession = { tsxOpen = false; onNewSession() },
        )
    }
}

/**
 * S27 动作行的「+」方块按钮：设计稿宽 46 的 ghost 样式（card 底 + 1px line + r12），
 * 内放 18dp add 图标。DshBtn 只支持「文字 + 图标」且文字在前，空文字会偏 6dp，
 * 所以这里按 .btn.ghost 的样式单独画。
 */
@Composable
private fun TermAddBtn(onClick: () -> Unit) {
    val c = DshTheme.colors
    Box(
        Modifier
            .width(46.dp)
            .height(Metrics.BTN_H)
            .clip(RoundedCornerShape(Radius.lg))
            .background(c.card)
            .border(1.dp, c.line, RoundedCornerShape(Radius.lg))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(DshIcons.Add, "新建会话", Modifier.size(18.dp), tint = c.ink2)
    }
}

/** 终端状态行（标签 66px + 内容；v20 设计稿标签列宽 66） */
@Composable
private fun TermRow(label: String, value: String, mono: Boolean) {
    val c = DshTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Type.sec.copy(color = c.ink3), modifier = Modifier.width(66.dp))
        Text(
            value,
            style = if (mono) Type.mono.copy(fontSize = 11.5.sp) else Type.sec,
            color = c.ink2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * S27 的「终端会话」Sheet（tsx，v20 新增）。
 * 设计稿：bash·0（当前）/ bash·1（tail -f web.log）/ 新建会话 → S49。
 */
@Composable
private fun BoxScope.S27SessionSheet(
    visible: Boolean,
    current: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    onNewSession: () -> Unit,
) {
    DshSheet(
        visible = visible,
        onDismiss = onDismiss,
        header = {
            DshSheetHeader(
                title = "终端会话",
                trailing = {
                    DshChip("+ 新建", mini = true, onClick = onNewSession)
                    Spacer(Modifier.width(2.dp))
                    DshIBtn(DshIcons.Close, "关闭", onClick = onDismiss)
                },
            )
        },
        content = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(DshTheme.colors.card)
                    .border(1.dp, DshTheme.colors.line, RoundedCornerShape(Radius.xl)),
            ) {
                DshMRow(
                    title = "bash · 0",
                    subtitle = "dsh web 运行中 · :3081",
                    icon = DshIcons.ChevronRight,
                    iconTone = ChipTone.Ok,
                    trailing = {
                        if (current == 0) DshChip("当前", tone = ChipTone.Brand, mini = true)
                    },
                    onClick = { onSelect(0) },
                )
                DshDivider()
                DshMRow(
                    title = "bash · 1",
                    subtitle = "tail -f web.log",
                    icon = DshIcons.ChevronRight,
                    iconTone = ChipTone.Neutral,
                    trailing = {
                        if (current == 1) DshChip("当前", tone = ChipTone.Brand, mini = true)
                    },
                    onClick = { onSelect(1) },
                )
                DshDivider()
                DshMRow(
                    title = "新建会话",
                    subtitle = "选模板与启动参数",
                    icon = DshIcons.Add,
                    chevron = true,
                    onClick = onNewSession,
                )
            }
        },
        footer = {
            DshBtn("关闭", tone = BtnTone.Ghost, onClick = onDismiss)
        },
    )
}

// ============================ S49 新建终端会话 ============================

/** S49 会话模板（设计稿：dsh web 默认 / headless / tail 日志 / 资源监控） */
private data class S49Template(
    val name: String,
    val desc: String,
    val icon: ImageVector,
    val iconTone: ChipTone,
    val isDefault: Boolean = false,
)

/**
 * S49 Termux · 新建终端会话（v20 新增）
 *
 * 设计稿：原先「新建会话」只弹提示，这轮补上落地页：
 * 会话模板 → Profile 段控 → 工作目录(→S41) → 唤醒/自动开 UI 开关 →
 * 参数 chips(--port 3081 / --no-open / --public-url) → 取消 / 创建并进入(→S27)。
 */
@Composable
fun S49NewTermuxSession(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onPickWorkspace: () -> Unit = {},
    onCreate: () -> Unit = {},
) {
    val c = DshTheme.colors
    var tpl by remember { mutableStateOf(0) }
    var profile by remember { mutableStateOf(0) }
    var wakeLock by remember { mutableStateOf(true) }
    var autoOpen by remember { mutableStateOf(true) }
    val args = remember { mutableStateOf(setOf("--port 3081")) }

    val templates = listOf(
        S49Template(
            "dsh web", "拉起 Web UI 并保持前台服务",
            DshIcons.Code, ChipTone.Brand, isDefault = true,
        ),
        S49Template(
            "headless 一次性任务", "跑完即退，不留常驻进程",
            DshIcons.FlashOn, ChipTone.Neutral,
        ),
        S49Template(
            "tail 日志", "tail -f ~/.dsh/logs/web.log",
            DshIcons.Article, ChipTone.Neutral,
        ),
        S49Template(
            "资源监控", "htop · 看 CPU 与内存占用",
            DshIcons.TrendingUp, ChipTone.Neutral,
        ),
    )
    val argChips = listOf("--port 3081", "--no-open", "--public-url")

    DshScreen(modifier) {
        DshNavBar(onBack = onBack, title = "新建终端会话", stacked = true)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            Spacer(Modifier.height(12.dp))

            DshSecTitle("会话模板", Modifier.padding(top = 0.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
            ) {
                templates.forEachIndexed { i, t ->
                    if (i > 0) DshDivider()
                    DshMRow(
                        title = t.name,
                        subtitle = t.desc,
                        icon = t.icon,
                        iconTone = t.iconTone,
                        trailing = {
                            if (t.isDefault) {
                                DshChip("默认", tone = ChipTone.Brand, mini = true)
                                Spacer(Modifier.width(6.dp))
                            }
                            if (tpl == i) {
                                Icon(
                                    DshIcons.CheckCircle, "已选",
                                    Modifier.size(19.dp),
                                    tint = c.brand,
                                )
                            }
                        },
                        onClick = { tpl = i },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            DshFieldGroup(label = "Profile") {
                DshSeg(listOf("web", "demo", "headless"), profile) { profile = it }
            }

            DshFieldGroup(label = "工作目录") {
                DshFinRow(
                    onClick = onPickWorkspace,
                    leading = {
                        Icon(DshIcons.FolderOpen, null, Modifier.size(15.dp), tint = c.ink3)
                    },
                    trailing = {
                        Icon(DshIcons.ChevronRight, null, Modifier.size(16.dp), tint = c.ink3)
                    },
                ) {
                    Text(
                        "~/storage/shared/Documents/dsh-ws",
                        style = Type.mono.copy(fontSize = 11.5.sp),
                        color = c.ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
            ) {
                DshMRow(
                    title = "保持唤醒",
                    subtitle = "唤醒锁 + 通知常驻，防后台回收",
                    trailing = { DshSwitch(wakeLock) { wakeLock = it } },
                )
                DshDivider()
                DshMRow(
                    title = "启动后自动打开 UI",
                    subtitle = "服务就绪即跳 Harness 首页",
                    trailing = { DshSwitch(autoOpen) { autoOpen = it } },
                )
            }

            Spacer(Modifier.height(12.dp))
            // 参数 chips：可追加 / 取消，选中态走品牌色
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                argChips.forEach { a ->
                    val on = a in args.value
                    DshChip(
                        a,
                        tone = if (on) ChipTone.Brand else ChipTone.Neutral,
                        onClick = {
                            args.value = if (on) args.value - a else args.value + a
                        },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "端口被占用会直接报错，届时改用 --port 重试；会话创建后可在终端窗口的会话列表里来回切换。",
                style = Type.meta.copy(lineHeight = 16.8.sp),
                color = c.ink3,
            )

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                DshBtn(
                    "取消",
                    tone = BtnTone.Ghost,
                    modifier = Modifier.weight(1f),
                    onClick = onBack,
                )
                DshBtn(
                    "创建并进入",
                    icon = DshIcons.Add,
                    modifier = Modifier.weight(1.4f),
                    onClick = onCreate,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 终端色调：普通 / 提示符$ / 成功 / [dsh]键 / 警告 / 错误 / 路径高亮 */
private enum class TermTone { FG, PROMPT, OK, KEY, WARN, ERR, PATH }

/** 一行 = 若干带色调的文本段 */
private typealias Seg = Pair<String, TermTone>

/** S27 终端输出 · bash·0（dsh web，对应 v20 设计稿 8 行） */
private val TERMINAL_LINES: List<List<Seg>> = listOf(
    listOf(Seg("$ ", TermTone.PROMPT), Seg("node -v", TermTone.FG)),
    listOf(Seg("v22.11.0", TermTone.OK)),
    listOf(Seg("$ ", TermTone.PROMPT), Seg("npx @deepseek-ai/dsh web", TermTone.FG)),
    listOf(Seg("[dsh] ", TermTone.KEY), Seg("profile \"web\" 首次启动，按内置模板初始化", TermTone.FG)),
    listOf(Seg("[dsh] ", TermTone.KEY), Seg("载入 bundle：llm · session · agent · api-gateway · tools · ui", TermTone.FG)),
    listOf(
        Seg("[dsh] ", TermTone.KEY), Seg("Web UI 已启动", TermTone.FG),
        Seg("  http://localhost:3081", TermTone.PATH),
    ),
    listOf(
        Seg("提示", TermTone.WARN), Seg("  默认仅本机可访问；外部访问需 ", TermTone.FG),
        Seg("--public-url", TermTone.PATH),
    ),
    listOf(Seg("$ ", TermTone.PROMPT), Seg("▎", TermTone.PATH)),
)

/** S27 终端输出 · bash·1（tail -f web.log，tsx 切换后显示） */
private val TERMINAL_TAIL_LINES: List<List<Seg>> = listOf(
    listOf(Seg("$ ", TermTone.PROMPT), Seg("tail -f ~/.dsh/logs/web.log", TermTone.FG)),
    listOf(Seg("[dsh] ", TermTone.KEY), Seg("GET /api/models 200 · 12ms", TermTone.FG)),
    listOf(Seg("[dsh] ", TermTone.KEY), Seg("session new · 12f4c0 · model=deepseek-v4-flash", TermTone.FG)),
    listOf(Seg("[dsh] ", TermTone.KEY), Seg("prompt turn 1 · 1,284 tok in / 96 out", TermTone.FG)),
    listOf(Seg("[dsh] ", TermTone.KEY), Seg("tool bash · ", TermTone.FG), Seg("exit 0", TermTone.OK)),
    listOf(
        Seg("[dsh] ", TermTone.KEY), Seg("web ui ready", TermTone.OK),
        Seg("  http://localhost:3081", TermTone.PATH),
    ),
    listOf(Seg("$ ", TermTone.PROMPT), Seg("▎", TermTone.PATH)),
)

/** 快捷指令（横滑） */
private val TERMINAL_QUICK = listOf(
    "--port 3082", "headless", "tail 日志", "wake-lock", "setup-storage",
)
