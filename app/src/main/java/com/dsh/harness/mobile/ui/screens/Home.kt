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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.harness.mobile.ui.components.*
import com.dsh.harness.mobile.ui.theme.*

/**
 * S3 会话首页 · 对齐 Web 端（v15 重排）+ S28 未选工作区态（v16 新增）
 *
 * v15 把首页输入框改回 Web 同款排布：
 *   - 工作区与模式从「居中胶囊」改为**输入框上方左对齐的文字下拉**
 *     （默认工作区 ▾ / 标准模式 ▾），不再用胶囊底；
 *   - 框内占位文案照抄 Web（"描述你想要构建的内容，/调用指令，@文件或对话"）；
 *   - 底行「+ 工作区内修改 …… DeepSeek-V4-Flash High ▾ + 圆形发送键」；
 *   - 模型全名与思考强度同行，点开进 S29 模型选择器。
 *
 * v16 新增 S28：未选工作区时（workspaceChosen=false）输入框置灰、
 * 显示"选择一个工作区开始"，只有选定目录后才能发任务（规则原样保留不放宽）。
 *
 * v20 新增：右上角**带描边的菜单按钮** → 唤起「本机服务与快捷入口」Sheet
 * （见 [S3ServiceSheet]）。设计稿原文：右上是服务与快捷入口，管本机进程与开关；
 * 左上 ☰ 是主导航侧边栏，管会话与工作区，两者职责不同。
 *
 * 骨架比例（设计稿 §03 规格）：
 *   状态栏 5% ｜ 顶栏 6% ｜ 上留白 7% ｜ Hero 10% ｜ 弹性 18%
 *   ｜ 工作区+模式行 5% ｜ 输入卡 15% ｜ 弹性 30% ｜ 边界脚注 3% ｜ home 4%
 */
@Composable
fun S3Home(
    modifier: Modifier = Modifier,
    workspace: String = "默认工作区",
    mode: String = "标准模式",
    /** 输入框右下角模型名 + 思考强度（来自 VM，与 S29 共享） */
    modelName: String = "DeepSeek-V4-Flash",
    thinking: String = "High",
    /** 工作区未选时输入禁用 —— 设计稿"规则原样保留不放宽" */
    workspaceChosen: Boolean = true,
    /** 本地 dsh 引擎是否已连接（ACP 握手完成） */
    connectionReady: Boolean = false,
    onOpenDrawer: () -> Unit,
    onPickWorkspace: () -> Unit,
    onPickMode: () -> Unit,
    /** 点输入框右下角模型名 → 唤起 S29 模型选择器 */
    onPickModel: () -> Unit = {},
    onSend: (String) -> Unit,
    /** 点顶栏状态 chip 时打开诊断（真实日志来自 ACP 客户端） */
    onShowDiagnostics: () -> Unit = {},
    /** v20：右上角描边菜单按钮 → 「本机服务与快捷入口」Sheet */
    onOpenServices: () -> Unit = {},
) {
    val c = DshTheme.colors
    var input by remember { mutableStateOf("") }

    DshScreen(modifier) {
        // —— 顶栏：☰ + 品牌字标；右侧 = 引擎状态 chip + v20 描边服务菜单按钮 ——
        DshNavBar(
            noBackFor = "S3 首页（一级页，返回键由侧栏与硬件键承担）",
            leading = {
                DshIBtn(DshIcons.Menu, "侧边栏", onClick = onOpenDrawer)
                DshBrandmark()
            },
            actions = {
                val tone = when {
                    connectionReady -> ChipTone.Ok
                    else -> ChipTone.Neutral
                }
                val label = when {
                    connectionReady -> "引擎就绪"
                    workspaceChosen -> "引擎启动中"
                    else -> "未连接"
                }
                DshChip(
                    label,
                    tone = tone,
                    mini = true,
                    onClick = { if (!connectionReady) onShowDiagnostics() },
                )
                // v20：右上角带描边菜单按钮（设计稿 border:1px solid var(--line)）
                SvcMenuBtn(onClick = onOpenServices)
            },
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = Space.lg),
        ) {
            Spacer(Modifier.height(56.dp))

            // —— Hero：23px 标题 + 预览版 chip ——
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("探索未至之境", style = Type.hero, color = c.ink, textAlign = TextAlign.Center)
                Spacer(Modifier.height(9.dp))
                DshChip("预览版", icon = DshIcons.Science, tone = ChipTone.Purple, mini = true)
            }

            Spacer(Modifier.weight(1f))

            // —— 工作区 + 模式：v15 改回 Web 同款左对齐文字下拉 ——
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (workspaceChosen) {
                    DshTextDropdown(
                        text = workspace,
                        icon = DshIcons.FolderOpen,
                        onClick = onPickWorkspace,
                    )
                } else {
                    // S28：未选时品牌色「选择工作区 ▾」
                    DshTextDropdown(
                        text = "选择工作区",
                        icon = DshIcons.FolderOpen,
                        brand = true,
                        onClick = onPickWorkspace,
                    )
                }
                DshTextDropdown(
                    text = mode,
                    onClick = onPickMode,
                    enabled = workspaceChosen,
                )
            }

            Spacer(Modifier.height(7.dp))

            // —— 输入卡：唯一吸底元素 ——
            Column(
                Modifier
                    .fillMaxWidth()
                    .shadow(6.dp, RoundedCornerShape(Radius.xl), clip = false)
                    .background(c.card, RoundedCornerShape(Radius.xl))
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .alpha(if (workspaceChosen) 1f else 0.72f)
                    .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 11.dp),
            ) {
                if (workspaceChosen) {
                    BasicTextInputCompat(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                    )
                } else {
                    // S28：未选工作区 → 置灰提示 + 约束条
                    Text(
                        "选择一个工作区开始",
                        style = Type.input,
                        color = c.ink3,
                        modifier = Modifier.heightIn(min = 60.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            DshIcons.ErrorOutline, null,
                            Modifier.size(15.dp).padding(top = 2.dp),
                            tint = c.warn,
                        )
                        Text(
                            "Harness 必须以本地文件夹作为工作区才能读写文件与执行命令",
                            style = Type.sec.copy(fontWeight = FontWeight.Medium, lineHeight = 18.sp),
                            color = c.ink3,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // —— 工具行 ——
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DshIBtn(
                        DshIcons.AddCircleOutline, "添加附件",
                        enabled = workspaceChosen,
                        onClick = {},
                    )
                    DshTextDropdown(
                        text = "工作区内修改",
                        onClick = onPickMode,
                        enabled = workspaceChosen,
                    )
                    Spacer(Modifier.weight(1f))
                    // 模型名 + 思考强度同行（点开进 S29）
                    Row(
                        Modifier
                            .then(if (workspaceChosen) Modifier.clickable { onPickModel() } else Modifier)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(modelName, style = Type.chip, color = c.ink2, maxLines = 1)
                        Text(thinking, style = Type.meta, color = c.ink3)
                        Icon(
                            DshIcons.ExpandMore, null, Modifier.size(14.dp),
                            tint = if (workspaceChosen) c.ink3 else c.ink3.copy(alpha = 0.45f),
                        )
                    }
                    val canSend = workspaceChosen && input.isNotBlank() && connectionReady
                    DshIBtn(
                        icon = DshIcons.ArrowForward,
                        contentDescription = "发送",
                        bg = if (canSend) c.brand else c.line,
                        tint = if (canSend) Color.White else c.ink3,
                        enabled = canSend,
                        onClick = { if (canSend) onSend(input) },
                    )
                }
            }

            // S28：未选时输入框下方补一个主按钮，直达选择工作区 Sheet
            if (!workspaceChosen) {
                Spacer(Modifier.height(14.dp))
                DshBtn(
                    text = "选择一个工作区",
                    icon = DshIcons.FolderOpen,
                    onClick = onPickWorkspace,
                )
            }

            Spacer(Modifier.weight(1.6f))

            // —— 边界脚注 ——
            Row(
                Modifier.fillMaxWidth().padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (workspaceChosen) DshIcons.Lock else DshIcons.Info,
                    null, Modifier.size(13.dp), tint = c.ink3,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    if (workspaceChosen)
                        "所选工作区内的读写与命令 · 越界需经你确认"
                    else
                        "选定后，会话、读写与命令都限定在该目录内",
                    style = Type.meta,
                    color = c.ink3,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * v15 文字下拉：输入框上方的左对齐文字 + 尾箭头，无胶囊底。
 * 用于工作区 / 模式 / 工作区内修改。品牌色用于未选工作区态的「选择工作区」。
 */
@Composable
private fun DshTextDropdown(
    text: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    brand: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val c = DshTheme.colors
    val fg = when {
        !enabled -> c.ink3.copy(alpha = 0.45f)
        brand -> c.brand
        else -> c.ink2
    }
    Row(
        modifier = modifier
            .height(30.dp)
            .then(if (onClick != null && enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(14.dp), tint = fg)
        }
        Text(text, style = Type.chip, color = fg, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Icon(DshIcons.ExpandMore, null, Modifier.size(14.dp), tint = fg)
    }
}

/**
 * 轻量文本输入：只为这一行 placeholder，不引入完整 TextField 的视觉与内边距。
 *
 * ⚠️ v10 复刻真机踩坑：placeholder 原来用 Row 硬排 5 个 Text，
 * 窄屏（如 360dp 小屏机）里「描述你想要构建的内容，/调用指令，@文件或对话」+光标
 * 会被挤成 3 行、还右对齐溢出。改成**单个可换行 Text + AnnotatedString**，
 * 宽度自动跟随容器，高度由设计稿的 min-height 60dp 兜住。
 */
@Composable
private fun BasicTextInputCompat(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DshTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = Type.input.copy(color = c.ink),
        cursorBrush = SolidColor(c.brand),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = c.ink3)) { append("描述你想要构建的内容，") }
                            withStyle(SpanStyle(color = c.brand, fontWeight = FontWeight.SemiBold)) { append("/") }
                            withStyle(SpanStyle(color = c.ink3)) { append("调用指令，") }
                            withStyle(SpanStyle(color = c.brand, fontWeight = FontWeight.SemiBold)) { append("@") }
                            withStyle(SpanStyle(color = c.ink3)) { append("文件或对话") }
                            withStyle(SpanStyle(color = c.brand)) { append("▎") }
                        },
                        style = Type.input,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                inner()
            }
        },
    )
}

/**
 * S3 的「选择工作区」Sheet。
 * 设计稿结构：默认工作区卡 → NAS 来源标题+卡 → 边界警告 → 添加本地文件夹 → 说明。
 */
@Composable
fun BoxScope.S3WorkspaceSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onAddLocal: () -> Unit,
) {
    val c = DshTheme.colors
    DshSheet(
        visible = visible,
        onDismiss = onDismiss,
        header = {
            DshSheetHeader(
                title = "选择工作区",
                trailing = {
                    DshIBtn(DshIcons.Close, "关闭", onClick = onDismiss)
                },
            )
        },
        content = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
            ) {
                DshMRow(
                    title = "默认工作区",
                    subtitle = "~/dsh/default · 4 个会话",
                    icon = DshIcons.HomeWork,
                    trailing = {
                        Icon(DshIcons.Check, "已选", Modifier.size(19.dp), tint = c.brand)
                    },
                )
                DshDivider()
                DshMRow(
                    title = "数据分析项目",
                    subtitle = "~/Projects/data · 12 个会话",
                    icon = DshIcons.Folder,
                    iconTone = ChipTone.Neutral,
                    chevron = true,
                )
                DshDivider()
                DshMRow(
                    title = "DSH 插件开发",
                    subtitle = "~/dev/dsh-plugins · 暂无会话",
                    icon = DshIcons.Power,
                    iconTone = ChipTone.Purple,
                    chevron = true,
                )
            }

            DshSecTitle("NAS 来源", note = "群晖 DS920+ · WebDAV")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
            ) {
                DshMRow(
                    title = "vault/dsh-sync",
                    subtitle = "↔ ~/dsh/nas-mount · 已挂载",
                    icon = DshIcons.Cloud,
                    iconTone = ChipTone.Purple,
                    trailing = {
                        DshChip("选为工作区", tone = ChipTone.Brand, mini = true)
                    },
                )
                DshDivider()
                DshMRow(
                    title = "backup/charts",
                    subtitle = "仅上传映射 · 不能作写入目标",
                    icon = DshIcons.CloudDone,
                    iconTone = ChipTone.Neutral,
                    chevron = true,
                )
            }

            // —— 边界警告：琥珀色 info ——
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    DshIcons.ErrorOutline, null,
                    Modifier.size(13.dp).padding(top = 1.dp),
                    tint = c.warn,
                )
                Text(
                    "NAS 目录作工作区后边界不变：仍只在该挂载目录内读写；挂载掉线自动转只读，写入类操作禁用（见 S18）",
                    style = Type.meta.copy(lineHeight = 17.6.sp),
                    color = c.ink3,
                )
            }

            Spacer(Modifier.height(12.dp))
            DshBtn(
                text = "添加本地文件夹",
                icon = DshIcons.CreateNewFolder,
                tone = BtnTone.Ghost,
                onClick = onAddLocal,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "网页版必须选定工作区才能干活：选中后输入框才可用，所有读写与命令只在目录内执行。",
                style = Type.meta.copy(lineHeight = 18.4.sp),
                color = c.ink3,
            )
        },
    )
}

/**
 * v20 右上角描边菜单按钮：视觉 32 + 1px line 描边，触区按 Metrics.IBTN_TOUCH_SLOP 外扩。
 * 设计稿与普通 ibtn 的唯一区别就是这道描边（`border:1px solid var(--line)`）。
 */
@Composable
private fun SvcMenuBtn(onClick: () -> Unit) {
    val c = DshTheme.colors
    Box(
        Modifier
            .sizeIn(
                minWidth = Metrics.IBTN + Metrics.IBTN_TOUCH_SLOP * 2,
                minHeight = Metrics.IBTN + Metrics.IBTN_TOUCH_SLOP * 2,
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(Metrics.IBTN)
                .border(1.dp, c.line, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                DshIcons.Menu, "本机服务与快捷入口",
                Modifier.size(Metrics.IBTN * 0.5625f),
                tint = c.ink2,
            )
        }
    }
}

// ============================ v20 本机服务与快捷入口 Sheet ============================

/**
 * S3 的「本机服务与快捷入口」Sheet（v20 新增）。
 *
 * 设计稿结构：标题行(ads_click + 标题 + dsh 运行中 chip + 关闭)
 *   → 本机服务·Termux(:3081) / 新建终端会话 / NAS 连接(未连接)
 *     / 导出数据与脱敏日志 / 用量与账单 / 显示代码工作视图(switch)
 *   → 职责说明脚注 → footer：关闭 + 全部设置。
 *
 * 与左上 ☰ 的分工（设计稿原文）：右上是服务与快捷入口，管本机进程与开关；
 * 左上侧边栏管会话与工作区。
 */
@Composable
fun BoxScope.S3ServiceSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    serviceRunning: Boolean = true,
    servicePort: String = ":3081",
    nasConnected: Boolean = false,
    codeViewOn: Boolean = true,
    onCodeViewChange: (Boolean) -> Unit = {},
    onTermux: () -> Unit = {},
    onNewSession: () -> Unit = {},
    onNas: () -> Unit = {},
    onExport: () -> Unit = {},
    onUsage: () -> Unit = {},
    onAllSettings: () -> Unit = {},
) {
    val c = DshTheme.colors
    DshSheet(
        visible = visible,
        onDismiss = onDismiss,
        header = {
            DshSheetHeader(
                title = "本机服务与快捷入口",
                icon = DshIcons.AdsClick,
                iconTint = c.brand,
                trailing = {
                    if (serviceRunning) {
                        DshChip("dsh 运行中", tone = ChipTone.Ok, mini = true)
                        Spacer(Modifier.width(4.dp))
                    }
                    DshIBtn(DshIcons.Close, "关闭", onClick = onDismiss)
                },
            )
        },
        content = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
            ) {
                DshMRow(
                    title = "本机服务 · Termux",
                    subtitle = "dsh web 运行状态、日志与终端会话",
                    icon = DshIcons.Memory,
                    iconTone = ChipTone.Purple,
                    trailing = {
                        if (serviceRunning) {
                            DshChip(servicePort, tone = ChipTone.Ok, mini = true)
                        } else {
                            DshChip("未运行", tone = ChipTone.Neutral, mini = true)
                        }
                    },
                    onClick = onTermux,
                )
                DshDivider()
                DshMRow(
                    title = "新建终端会话",
                    subtitle = "选模板与启动参数",
                    icon = DshIcons.Add,
                    chevron = true,
                    onClick = onNewSession,
                )
                DshDivider()
                DshMRow(
                    title = "NAS 连接",
                    subtitle = "未连接 · 可作工作区来源或备份目标",
                    icon = DshIcons.Dns,
                    iconTone = ChipTone.Neutral,
                    trailing = {
                        DshChip(
                            if (nasConnected) "已连接" else "未连接",
                            tone = if (nasConnected) ChipTone.Ok else ChipTone.Warn,
                            mini = true,
                        )
                    },
                    onClick = onNas,
                )
                DshDivider()
                DshMRow(
                    title = "导出数据与脱敏日志",
                    icon = DshIcons.FileDownload,
                    iconTone = ChipTone.Neutral,
                    chevron = true,
                    onClick = onExport,
                )
                DshDivider()
                DshMRow(
                    title = "用量与账单",
                    icon = DshIcons.QueryBuilder,
                    iconTone = ChipTone.Neutral,
                    chevron = true,
                    onClick = onUsage,
                )
                DshDivider()
                DshMRow(
                    title = "显示代码工作视图",
                    subtitle = "开启后会话页才有「轨迹」标签",
                    trailing = { DshSwitch(codeViewOn) { onCodeViewChange(it) } },
                )
            }

            Text(
                "右上是「服务与快捷入口」，左上是主导航侧边栏，两者职责不同：" +
                    "前者管本机进程与开关，后者管会话与工作区。",
                style = Type.meta.copy(lineHeight = 17.6.sp),
                color = c.ink3,
                modifier = Modifier.padding(top = 11.dp),
            )
        },
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                DshBtn(
                    "关闭",
                    tone = BtnTone.Ghost,
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                )
                DshBtn(
                    "全部设置",
                    icon = DshIcons.Settings,
                    modifier = Modifier.weight(1.2f),
                    onClick = onAllSettings,
                )
            }
        },
    )
}
// ============================ 引擎诊断 Sheet ============================

/**
 * 引擎诊断 —— 真机上"未连接"只有一个标签，无从排查；
 * 这里把 ACP 客户端的真实协议日志摊开，并给出重连入口。
 */
@Composable
fun BoxScope.S3EngineDiagnostics(
    visible: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    log: List<String> = emptyList(),
    error: String? = null,
) {
    val c = DshTheme.colors
    DshSheet(
        visible = visible,
        onDismiss = onDismiss,
        maxHeightRatio = 0.7f,
        header = {
            DshSheetHeader(
                title = "引擎诊断",
                icon = DshIcons.ErrorOutline,
                iconTint = if (error != null) c.danger else c.brand,
                trailing = {
                    DshChip(
                        if (error != null) "异常" else "正常",
                        tone = if (error != null) ChipTone.Danger else ChipTone.Ok,
                        mini = true,
                    )
                },
            )
        },
        content = {
            if (error != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.xl))
                        .background(c.dangerSoft())
                        .padding(12.dp),
                ) {
                    Text("失败原因", style = Type.secTitle, color = c.danger)
                    Spacer(Modifier.height(4.dp))
                    Text(error, style = Type.meta.copy(lineHeight = 18.sp), color = c.ink2)
                }
                Spacer(Modifier.height(12.dp))
            }

            Text("协议日志", style = Type.secTitle, color = c.ink3)
            Spacer(Modifier.height(6.dp))
            if (log.isEmpty()) {
                Text("暂无日志", style = Type.sec, color = c.ink3)
            } else {
                DshCode(log.takeLast(40).joinToString("\n"), fontSize = 10.5.sp)
            }
        },
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                DshBtn("关闭", tone = BtnTone.Ghost, modifier = Modifier.weight(1f), onClick = onDismiss)
                DshBtn("重试连接", icon = DshIcons.Sync, modifier = Modifier.weight(1.3f), onClick = onRetry)
            }
        },
    )
}

// ============================ S29 模型选择器 ============================

/**
 * S29 模型选择器（v16 新增）
 *
 * 入口：S3 输入框右下角「DeepSeek-V4-Flash High ▾」。
 * 设计稿结构：标题行(选择模型 + auto + 余额入口 + 关闭)
 *   → DeepSeek 账号组(每行 Off/High/Max 分段，与模型名同行)
 *   → 自定义提供方组(声明档位的给 "High ▾" 胶囊，未声明的给琥珀"未声明档位")
 *   → auto 卡(开关) → 底部规则说明 → 完成按钮。
 *
 * 当前项标「当前」；思考强度与其同行，避免二级弹层。
 * 模型名录按设计稿（含自定义提供方）为示意占位，与 S3 默认显示保持一致。
 */
private data class S29Model(
    val name: String,
    val desc: String,
    /** 默认思考强度；null = 未声明推理等级（不提供档位） */
    val thinking: String?,
)

@Composable
fun BoxScope.S29ModelSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    selModelName: String,
    selThinking: String,
    autoOn: Boolean,
    onSelect: (name: String, thinking: String) -> Unit,
    onAuto: (Boolean) -> Unit,
    onAccount: () -> Unit,
    onDone: () -> Unit,
) {
    val c = DshTheme.colors
    val levels = listOf("Off", "High", "Max")

    val deepseek = listOf(
        S29Model("DeepSeek-V4-Flash", "快 · 适合日常任务与长上下文缓存", "High"),
        S29Model("DeepSeek-V4-Pro", "强推理 · 复杂改造与长任务", "Max"),
    )
    val custom = listOf(
        S29Model("qwen3.7-max", "bailian · openai-completions", "High"),
        S29Model("minimax-m3", "bailian · 目录内", "High"),
        S29Model("Tencent TokenPlan · auto", "tencent · 套餐内自动路由", null),
    )

    DshSheet(
        visible = visible,
        onDismiss = onDismiss,
        header = {
            DshSheetHeader(
                title = "选择模型",
                trailing = {
                    DshChip(
                        if (autoOn) "auto · 开" else "auto",
                        tone = if (autoOn) ChipTone.Brand else ChipTone.Neutral,
                        mini = true,
                        onClick = { onAuto(!autoOn) },
                    )
                    Spacer(Modifier.width(6.dp))
                    DshIBtn(DshIcons.AccountBalanceWallet, "账号与余额", onClick = onAccount)
                    Spacer(Modifier.width(2.dp))
                    DshIBtn(DshIcons.Close, "关闭", onClick = onDismiss)
                },
            )
        },
        content = {
            DshSecTitle("DeepSeek 账号", note = "用余额计费")
            S29GroupCard {
                deepseek.forEachIndexed { i, m ->
                    if (i > 0) DshDivider()
                    S29ModelRow(
                        model = m,
                        current = m.name == selModelName,
                        rowThinking = if (m.name == selModelName) selThinking else (m.thinking ?: "High"),
                        onSelect = onSelect,
                    )
                }
            }

            DshSecTitle("自定义提供方")
            S29GroupCard {
                custom.forEachIndexed { i, m ->
                    if (i > 0) DshDivider()
                    S29ModelRow(
                        model = m,
                        current = m.name == selModelName,
                        rowThinking = if (m.name == selModelName) selThinking else (m.thinking ?: "High"),
                        onSelect = onSelect,
                    )
                }
            }

            // —— auto 卡 ——
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.soft)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(DshIcons.AutoAwesome, null, Modifier.size(16.dp), tint = c.brand)
                Column(Modifier.weight(1f)) {
                    Text("auto", style = Type.rowTitle, color = c.ink)
                    Text(
                        "由 Harness 按任务挑模型，跨提供方可用",
                        style = Type.sec.copy(lineHeight = 15.sp),
                        color = c.ink2,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                DshSwitch(autoOn) { onAuto(it) }
            }

            Text(
                "手动录入的模型在声明推理等级前不会出现在档位菜单里，是否思考由服务端默认行为决定；可在 设置 → 模型 → 编辑提供方 → 自定义设置 → 模型选项 → 推理等级 配置。切换模型只影响后续轮次，进行中的任务不会被搬走。",
                style = Type.meta.copy(lineHeight = 17.6.sp),
                color = c.ink3,
                modifier = Modifier.padding(top = 11.dp),
            )
        },
        footer = {
            DshBtn("完成", tone = BtnTone.Ghost, onClick = onDone)
        },
    )
}

/** S29 分组卡容器（圆角描边，行内可滚动） */
@Composable
private fun S29GroupCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xl))
            .background(DshTheme.colors.card)
            .border(1.dp, DshTheme.colors.line, RoundedCornerShape(Radius.xl)),
        content = content,
    )
}

/** S29 单行：模型名 +「当前」标 + 思考强度（段控件或档位胶囊 / 未声明档位） */
@Composable
private fun S29ModelRow(
    model: S29Model,
    current: Boolean,
    rowThinking: String,
    onSelect: (name: String, thinking: String) -> Unit,
) {
    val c = DshTheme.colors
    val levels = listOf("Off", "High", "Max")
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onSelect(model.name, model.thinking ?: "High") }
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(model.name, style = Type.rowTitle, color = c.ink, maxLines = 1, fontWeight = FontWeight.SemiBold)
            if (current) {
                Spacer(Modifier.width(5.dp))
                DshChip("当前", tone = ChipTone.Brand, mini = true)
            }
            Spacer(Modifier.weight(1f))
            if (model.thinking != null) {
                // 已声明推理等级：Off/High/Max 段控件与模型名同行
                DshSeg(
                    options = levels,
                    selectedIndex = levels.indexOf(rowThinking).coerceAtLeast(0),
                    modifier = Modifier.width(120.dp),
                    onSelect = { onSelect(model.name, levels[it]) },
                )
            } else {
                // 未声明推理等级：琥珀标签，不提供档位
                DshChip("未声明档位", tone = ChipTone.Warn, mini = true)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(model.desc, style = Type.sec, color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
