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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.harness.mobile.ui.components.*
import com.dsh.harness.mobile.ui.theme.*

/**
 * S3 会话首页 · 对齐 Web 端
 *
 * 设计稿骨架表原文：
 *   状态栏 5% ｜ 顶栏 6%(仅菜单 + 品牌字标，右侧不放按钮) ｜ 上留白 7%
 *   ｜ Hero 10%(探索未至之境 23px + 预览版 chip) ｜ 弹性 18%
 *   ｜ 工作区 + 模式行 5% ｜ 输入卡 15%(提示 3 行 + 工具行)
 *   ｜ 弹性 30% ｜ 边界脚注 3% ｜ home 4%
 *   "上下两段 flex 取 1 : 1.6，把输入框压到屏幕中线略偏下；
 *    无底部导航，输入卡是唯一的吸底元素"
 *
 * v7 修复记录：输入卡工具行原本需要 365px 而可用宽只有 317px，
 * 发送按钮被裁到卡片外 → 这里工具行允许换行，且发送按钮固定在末尾不被挤掉。
 */
@Composable
fun S3Home(
    modifier: Modifier = Modifier,
    workspace: String = "默认工作区",
    workspaceIcon: androidx.compose.ui.graphics.vector.ImageVector = DshIcons.FolderOpen,
    mode: String = "标准模式",
    model: String = "V4-Flash",
    thinking: String = "High",
    /** 工作区未选时输入禁用 —— 设计稿"规则原样保留不放宽" */
    workspaceChosen: Boolean = true,
    /** 本地 dsh 引擎是否已连接（ACP 握手完成） */
    connectionReady: Boolean = false,
    onOpenDrawer: () -> Unit,
    onPickWorkspace: () -> Unit,
    onPickMode: () -> Unit,
    onSend: (String) -> Unit,
    /** 点顶栏状态 chip 时打开诊断（真实日志来自 ACP 客户端） */
    onShowDiagnostics: () -> Unit = {},
) {
    val c = DshTheme.colors
    var input by remember { mutableStateOf("") }

    DshScreen(modifier) {
        // —— 顶栏：仅 ☰ + 品牌字标，右侧不放按钮（设计稿明确）——
        DshNavBar(
            // 一级页：没有来源页可回，必须显式声明（见 DshNavBar 的防御说明）
            noBackFor = "S3 首页（一级页，返回键由侧栏与硬件键承担）",
            leading = {
                DshIBtn(DshIcons.Menu, "侧边栏", onClick = onOpenDrawer)
                DshBrandmark()
            },
            actions = {
                // 引擎状态：绿=已连接、琥珀=启动中、灰=未连接
                val tone = when {
                    connectionReady -> ChipTone.Ok
                    else -> ChipTone.Neutral
                }
                val label = when {
                    connectionReady -> "引擎就绪"
                    workspaceChosen -> "引擎启动中"
                    else -> "未连接"
                }
                // 失败时点开能看到具体原因（否则只有一个"未连接"，无从排查）
                DshChip(
                    label,
                    tone = tone,
                    mini = true,
                    onClick = { if (!connectionReady) onShowDiagnostics() },
                )
            },
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = Space.lg),
        ) {
            Spacer(Modifier.height(56.dp))   // 上留白 7%

            // —— Hero：23px 标题 + 预览版 chip ——
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "探索未至之境",
                    style = Type.hero,
                    color = c.ink,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(9.dp))
                DshChip("预览版", icon = DshIcons.Science, tone = ChipTone.Purple, mini = true)
            }

            Spacer(Modifier.weight(1f))     // 弹性 18%

            // —— 工作区 + 模式行 ——
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DshWsPill(
                    label = workspace,
                    icon = workspaceIcon,
                    onClick = onPickWorkspace,
                )
                Spacer(Modifier.width(8.dp))
                Row(
                    Modifier
                        .height(30.dp)
                        .background(c.line2, RoundedCornerShape(14.dp))
                        .clickable { onPickMode() }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(DshIcons.Build, null, Modifier.size(14.dp), tint = c.ink2)
                    Text(mode, style = Type.chip, color = c.ink2)
                }
            }

            Spacer(Modifier.height(12.dp))

            // —— 输入卡：唯一吸底元素，投影 0 6px 22px ——
            Column(
                Modifier
                    .fillMaxWidth()
                    .shadow(6.dp, RoundedCornerShape(Radius.xl), clip = false)
                    .background(c.card, RoundedCornerShape(Radius.xl))
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 11.dp),
            ) {
                if (workspaceChosen) {
                    BasicTextInputCompat(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp),
                    )
                } else {
                    // 未选工作区：输入禁用并给出去向提示
                    Text(
                        "先选择工作区，再描述你要构建的内容",
                        style = Type.input,
                        color = c.ink3,
                        modifier = Modifier.heightIn(min = 60.dp),
                    )
                }

                Spacer(Modifier.height(10.dp))

                // —— 工具行：可换行，发送按钮永远在最后且不被挤掉 ——
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DshIBtn(DshIcons.AddCircleOutline, "添加附件", onClick = {})
                    DshChip("工作区内修改")
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier
                            .height(30.dp)
                            .background(c.line2, RoundedCornerShape(14.dp))
                            .clickable { }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(model, style = Type.chip, color = c.ink2, maxLines = 1)
                        Text(thinking, style = Type.meta, color = c.ink3)
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

            Spacer(Modifier.weight(1.6f))   // 弹性 30% —— 与上方 1 : 1.6

            // —— 边界脚注：把"工作区内才干活"这条规则常驻在屏上 ——
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(DshIcons.Lock, null, Modifier.size(13.dp), tint = c.ink3)
                Spacer(Modifier.width(5.dp))
                Text(
                    "所选工作区内的读写与命令 · 越界需经你确认",
                    style = Type.meta,
                    color = c.ink3,
                    textAlign = TextAlign.Center,
                )
            }
        }
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
                    // 用 AnnotatedString 保持「/」和「@」的品牌色，同时让它自然换行
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = c.ink3)) { append("描述你想要构建的内容，") }
                            withStyle(SpanStyle(color = c.brand, fontWeight = FontWeight.SemiBold)) { append("/") }
                            withStyle(SpanStyle(color = c.ink3)) { append("调用指令，") }
                            withStyle(SpanStyle(color = c.brand, fontWeight = FontWeight.SemiBold)) { append("@") }
                            withStyle(SpanStyle(color = c.ink3)) { append("文件或对话") }
                            // 光标占位：设计稿 .cur
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
