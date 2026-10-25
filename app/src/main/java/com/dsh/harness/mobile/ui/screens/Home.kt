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
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.dsh.harness.mobile.ui.components.*
import com.dsh.harness.mobile.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

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
    /** v22 修订版：工具行「+」按钮 —— 打开「添加」Sheet（新建文件/文件夹 + 上传） */
    onOpenAdd: () -> Unit = {},
    /** v22 修订版：工具行「盾牌」按钮 —— 打开「权限档位」Sheet */
    onOpenPerm: () -> Unit = {},
    /** v22 修订版：工具行「附件」按钮 —— 打开「附件」Sheet（上传附件/图片） */
    onOpenAttach: () -> Unit = {},
    /** 点输入框右下角模型名 → 唤起 S29 模型选择器 */
    onPickModel: () -> Unit = {},
    onSend: (String) -> Unit,
    /** 发送键在不可用被点时回调（原因说明，由调用方弹提示） */
    onSendBlocked: (String) -> Unit = {},
    /**
     * v23：输入框内容提升到 DshApp —— 路由切去 S10 指令面板再回来时，
     * 原来的屏内 state 会随 AnimatedContent 退出组合而丢失，选中的指令
     * 就回填不进输入框（用户实测"选了指令没反应"）。
     */
    input: String = "",
    onInputChange: (String) -> Unit = {},
    /** 输入 "/" → 打开 S10 斜杠指令面板 */
    onSlashTrigger: () -> Unit = {},
    /** 输入 "@" → 打开文件/对话选择 Sheet */
    onMentionTrigger: () -> Unit = {},
    /** 点顶栏状态 chip 时打开诊断（真实日志来自 ACP 客户端） */
    onShowDiagnostics: () -> Unit = {},
    /** v20：右上角描边菜单按钮 → 「本机服务与快捷入口」Sheet */
    onOpenServices: () -> Unit = {},
    /**
     * v30.7（设计稿 v22 **S18**）：NAS 断连 → 已转只读。
     *
     * 由 [com.dsh.harness.DshSync.readOnlyNow] 判定——那是**挂载状态**触发的
     * （ping 失败 + 开了「离线转只读」），不是 Agent 自己判断的。降级时：
     * 顶栏下方出横幅、输入卡禁用、写入类动作禁用，但读取与切回本地工作区仍可用。
     */
    nasReadOnly: Boolean = false,
    /** 降级横幅上的「最后同步」时间（真实时间戳，无数据则 null）。 */
    nasLastSync: String? = null,
    /** 点横幅「重连」 */
    onNasReconnect: () -> Unit = {},
    /** 点「切回本地工作区」 */
    onSwitchLocalWorkspace: () -> Unit = {},
    /**
     * v30.8（设计稿 v22 S18）：「断连期间仍可」三行能力卡。
     * 第二行的副标题要写真实的挂载目录文件数（nasMountedFileCount），
     * 不写死设计稿里的 "Q3数据汇总.pptx 等 6 个"。
     */
    nasMountedFileCount: Int = 0,
    /** 「读取与预览挂载目录文件」→ 打开工作区（只读浏览） */
    onOpenMountedFiles: () -> Unit = {},
    /** 「搜索资料与核对信息」→ 全局搜索 */
    onOpenSearch: () -> Unit = {},
    /** 「产物改写到本地暂存目录」→ 打开工作区（本地暂存） */
    onOpenLocalStaging: () -> Unit = {},
) {
    val c = DshTheme.colors

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

            // —— v30.7 / 设计稿 S18：NAS 断连只读横幅 ——
            if (nasReadOnly) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.lg))
                        .background(c.warn.copy(alpha = 0.12f))
                        .border(1.dp, c.warn.copy(alpha = 0.3f), RoundedCornerShape(Radius.lg))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(DshIcons.ErrorOutline, null, Modifier.size(17.dp), tint = c.warn)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "NAS 连接中断 · 已转只读",
                            style = Type.secTitle, color = c.warn,
                        )
                        Text(
                            "写入与命令执行已禁用 · " + (nasLastSync ?: "尚未同步"),
                            style = Type.meta, color = c.ink2,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    DshBtn("重连", small = true, modifier = Modifier.widthIn(min = 64.dp),
                        onClick = onNasReconnect)
                }
                Spacer(Modifier.height(10.dp))
            }

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
                    // v22 删除 S28：未选工作区不再是独立页/高亮态。这里只把下拉文案
                    // 换成"选择工作区"作为入口提示，样式与已选时一致（ink2，不搞品牌色强调）。
                    DshTextDropdown(
                        text = "选择工作区",
                        icon = DshIcons.FolderOpen,
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
                    .alpha(if (workspaceChosen && !nasReadOnly) 1f else 0.72f)
                    .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 11.dp),
            ) {
                // v30.7：只读降级优先于「未选工作区」——两者都置灰，但原因文案不同
                if (nasReadOnly) {
                    Text(
                        "只读工作区 · 无法写文件与执行命令",
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
                            DshIcons.Lock, null,
                            Modifier.size(15.dp).padding(top = 2.dp),
                            tint = c.warn,
                        )
                        Text(
                            "当前工作区来自 NAS 挂载目录，连接中断已自动转只读；恢复后可继续，或切回本地工作区",
                            style = Type.sec.copy(fontWeight = FontWeight.Medium, lineHeight = 18.sp),
                            color = c.ink3,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    // v22 删除 S28：未选工作区不再是独立页。首页保持统一极简布局，
                    // 输入框常驻可编辑（与设计稿一致），"未选工作区"只在发送时给出原因。
                    BasicTextInputCompat(
                        value = input,
                        onValueChange = { v ->
                            val grew = v.length > input.length
                            onInputChange(v)
                            // v23：设计稿 S3 输入框 "/调用指令，@文件或对话" 的联动落地：
                            // 新内容恰为 "/" → 拉起 S10；新键入 "@" → 拉起选择 Sheet。
                            // 只认"增长"方向，避免退格删除时反复弹面板。
                            if (v == "/") onSlashTrigger()
                            if (grew && v.lastOrNull() == '@') onMentionTrigger()
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                    )
                }

                Spacer(Modifier.height(10.dp))

                // —— 工具行（v22 修订版：与 HTML 工具行逐像素对齐）——
                // HTML：+（添加）/ 盾牌（权限档位）/ 附件（attach_file）/ 弹性 / 模型下拉 / 发送
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DshIBtn(
                        DshIcons.Add, "添加",
                        enabled = workspaceChosen && !nasReadOnly,
                        onClick = onOpenAdd,
                    )
                    DshIBtn(
                        DshIcons.Shield, "权限档位",
                        enabled = workspaceChosen && !nasReadOnly,
                        onClick = onOpenPerm,
                    )
                    DshIBtn(
                        DshIcons.AttachFile, "附件",
                        enabled = workspaceChosen && !nasReadOnly,
                        onClick = onOpenAttach,
                    )
                    // 模型名 + 思考强度同行（点开进 S29）
                    // v24：本区域参与 weight —— 此前模型名 Text 不换行不省略，
                    // 窄屏上把发送键整个挤出容器（用户实测"首页还是没有发送按钮"）。
                    // 现在超长模型名在内部 ellipsis，发送键常驻右侧。
                    Row(
                        Modifier
                            .weight(1f)
                            .then(if (workspaceChosen) Modifier.clickable { onPickModel() } else Modifier)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            modelName,
                            style = Type.chip,
                            color = c.ink2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(thinking, style = Type.meta, color = c.ink3)
                        Icon(
                            DshIcons.ExpandMore, null, Modifier.size(14.dp),
                            tint = if (workspaceChosen) c.ink3 else c.ink3.copy(alpha = 0.45f),
                        )
                    }
                    val canSend = workspaceChosen && !nasReadOnly && input.isNotBlank() && connectionReady
                    // v23：不禁用点击。原来 enabled=false 时整个按钮无响应，
                    // 用户实测"发送按钮点不到"且得不到任何原因。保持灰底视觉，
                    // 但点击时给出具体原因（空输入 / 引擎未就绪 / 未选工作区 / 只读）。
                    DshIBtn(
                        icon = DshIcons.ArrowForward,
                        contentDescription = "发送",
                        bg = if (canSend) c.brand else c.line,
                        tint = if (canSend) Color.White else c.ink3,
                        onClick = {
                            if (canSend) {
                                onSend(input)
                            } else {
                                onSendBlocked(
                                    when {
                                        nasReadOnly -> "NAS 已断开，工作区处于只读，先重连或切回本地工作区"
                                        !workspaceChosen -> "先选择一个工作区"
                                        input.isBlank() -> "先输入内容"
                                        else -> "引擎启动中，稍候再发"
                                    },
                                )
                            }
                        },
                    )
                }
            }

            // v30.8：设计稿 v22 S18「断连期间仍可」三行能力卡。
            // 只读降级时告诉用户"还剩什么能用"，避免整屏灰掉像坏了。
            if (nasReadOnly) {
                DshSecTitle("断连期间仍可", Modifier.padding(top = 0.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.xl))
                        .background(c.card)
                        .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
                ) {
                    DshMRow(
                        title = "搜索资料与核对信息",
                        subtitle = "不依赖工作区写入",
                        icon = DshIcons.Search,
                        iconTone = ChipTone.Ok,
                        chevron = true,
                        onClick = onOpenSearch,
                    )
                    DshDivider()
                    DshMRow(
                        title = "读取与预览挂载目录文件",
                        subtitle = if (nasMountedFileCount > 0)
                            "挂载目录中 $nasMountedFileCount 个文件"
                        else
                            "挂载目录当前读不到文件",
                        icon = DshIcons.Visibility,
                        iconTone = ChipTone.Ok,
                        chevron = true,
                        onClick = onOpenMountedFiles,
                    )
                    DshDivider()
                    DshMRow(
                        title = "产物改写到本地暂存目录",
                        subtitle = "恢复后由同步任务回传",
                        icon = DshIcons.FolderOpen,
                        iconTone = ChipTone.Warn,
                        chevron = true,
                        onClick = onOpenLocalStaging,
                    )
                }
            }

            // v30.7：只读降级时给「切回本地工作区」出口（设计稿 S18 底部幽灵按钮）
            if (nasReadOnly) {
                Spacer(Modifier.height(14.dp))
                DshBtn(
                    text = "切回本地工作区继续",
                    icon = DshIcons.HomeWork,
                    tone = BtnTone.Ghost,
                    onClick = onSwitchLocalWorkspace,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "降级由挂载状态触发，不由 Agent 判断；恢复连接后需你确认才把暂存产物回传 NAS。",
                    style = Type.meta.copy(fontSize = 11.sp, lineHeight = 18.sp),
                    color = c.ink3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // v22 删除 S28：输入框下方不再补「选择一个工作区」大按钮
            // （选定工作区走输入框上方的「选择工作区 ▾」下拉，与设计稿一致）。
            Spacer(Modifier.weight(1.6f))

            // v22：底部脚注统一为设计稿的锁形文案（S28 的"选定后…"变体一并去掉）
            Row(
                Modifier.fillMaxWidth().padding(bottom = 2.dp),
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
    currentWorkspace: String? = null,
    nasConfigured: Boolean = false,
    onNas: () -> Unit = {},
    /** v30.7：NAS 是否已挂为工作区来源（对应 S17 的「挂载为工作区来源」开关） */
    nasMounted: Boolean = false,
    /** v30.7：NAS 断连 → 只读（设计稿 S18 的降级态） */
    nasReadOnly: Boolean = false,
    /** NAS 设备/协议说明（副标题，如「群晖 DS920+ · WebDAV」） */
    nasLabel: String = "NAS",
    /** 远端同步目录（显示用，如「vault/dsh-sync」） */
    nasRemotePath: String = "dsh-phone/storages",
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
            // —— 当前工作区（真实，来自 ViewModel；不再编造假项目行）——
            DshSecTitle("当前工作区")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
            ) {
                if (currentWorkspace.isNullOrBlank()) {
                    Text(
                        "尚未选择工作区 · 去左上角侧栏开一个",
                        style = Type.sec,
                        color = c.ink3,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                    )
                } else {
                    DshMRow(
                        title = currentWorkspace.substringAfterLast('/'),
                        subtitle = "当前已选工作区",
                        icon = DshIcons.HomeWork,
                        trailing = {
                            Icon(DshIcons.Check, "已选", Modifier.size(19.dp), tint = c.brand)
                        },
                    )
                }
            }

            // —— NAS 来源（v30.7 按 v22 S3/S18：可写挂载可作工作区来源，只读仅可读）——
            if (nasConfigured) {
                Spacer(Modifier.height(16.dp))
                DshSecTitle("NAS 来源", note = nasLabel)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.xl))
                        .background(c.card)
                        .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
                ) {
                    val canWrite = nasMounted && !nasReadOnly
                    DshMRow(
                        title = nasRemotePath,
                        subtitle = if (!nasMounted) "未挂载 · 在 NAS 连接页开「挂载为工作区来源」"
                        else if (nasReadOnly) "已挂载 · 连接中断，仅可读"
                        else "已挂载 · 可作工作区来源",
                        icon = if (nasReadOnly) DshIcons.CloudOff else DshIcons.Cloud,
                        iconTone = if (canWrite) ChipTone.Brand else ChipTone.Neutral,
                        trailing = {
                            DshChip(
                                when {
                                    nasReadOnly -> "只读"
                                    nasMounted -> "可写"
                                    else -> "未挂载"
                                },
                                tone = if (canWrite) ChipTone.Ok else ChipTone.Warn, mini = true,
                            )
                        },
                        onClick = onNas,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        DshIcons.ErrorOutline, null,
                        Modifier.size(13.dp).padding(top = 1.dp), tint = c.warn,
                    )
                    Text(
                        "NAS 目录作工作区后边界不变：仍只在该挂载目录内读写；挂载掉线自动转只读，写入类操作禁用",
                        style = Type.meta.copy(lineHeight = 16.sp),
                        color = c.ink3,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // —— 添加本地文件夹（走 S41 真实目录选择）——
            Spacer(Modifier.height(14.dp))
            DshBtn(
                text = "添加本地文件夹",
                icon = DshIcons.CreateNewFolder,
                tone = BtnTone.Ghost,
                onClick = onAddLocal,
            )

            // —— 边界说明（诚实，不编假）——
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    DshIcons.Info, null,
                    Modifier.size(13.dp).padding(top = 1.dp),
                    tint = c.ink3,
                )
                Text(
                    "NAS 仅作会话/工作区数据的备份目标，不作工作区来源；挂载掉线时自动跳过同步，不影响本地使用。",
                    style = Type.meta.copy(lineHeight = 17.6.sp),
                    color = c.ink3,
                )
            }
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
        // footer 按钮高度 = 66.dp（v30.20 用户显式指定，高于 App 默认 46dp；这是有意的
        // 视觉强调，不照搬设计稿 46px，也不做整画布缩放）。四个 Sheet footer 统一此值。
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                DshBtn(
                    "关闭",
                    tone = BtnTone.Ghost,
                    modifier = Modifier.weight(1f),
                    height = 66.dp,
                    onClick = onDismiss,
                )
                DshBtn(
                    "全部设置",
                    icon = DshIcons.Settings,
                    modifier = Modifier.weight(1.2f),
                    height = 66.dp,
                    onClick = onAllSettings,
                )
            }
        },
    )
}
// ============================ S3 提及（@）选择 Sheet ============================

/**
 * S3 输入框敲 `@` 弹出的「提及文件或会话」Sheet（v23 新增）。
 *
 * 修复点：此前 placeholder 里品牌色高亮的 `/` `@` 只是静态文字，
 * 点了没有任何反应（用户实测反馈「/和@无法使用」）。现在：
 *   - 输入 `/` → DshApp 导航到 S10 指令面板，选中指令回填输入框；
 *   - 输入 `@` → 弹出本 Sheet，选中会话把 `@<标题> ` 追加进输入框。
 *
 * 会话组用真实 `session/list` 数据（来自 ACP）；文件组本 App 没有文件索引能力
 * → 诚实空态，不拿设计稿示意名充数。
 */
@Composable
fun BoxScope.S3MentionSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    sessions: List<com.dsh.harness.mobile.data.DshViewModel.Session> = emptyList(),
    onPickSession: (com.dsh.harness.mobile.data.DshViewModel.Session) -> Unit = {},
    onPickFile: (String) -> Unit = {},
) {
    val c = DshTheme.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pickedName by remember { mutableStateOf<String?>(null) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val name = queryDisplayName(ctx, uri) ?: ("file_" + System.currentTimeMillis())
                    // v30.6：复制进 dsh 工作区（HOME=/filesDir/home）下的 attachments/，
                    // 引擎（cwd=home）能真实读到；不再放 getExternalFilesDir（dsh 读不到、路径也只是文本）。
                    val dir = java.io.File(
                        com.dsh.harness.DshBootstrap.home(ctx.applicationContext),
                        "attachments",
                    )
                    dir.mkdirs()
                    val fname = System.currentTimeMillis().toString() + "_" + name
                    val dest = java.io.File(dir, fname)
                    ctx.contentResolver.openInputStream(uri)?.use { ins ->
                        FileOutputStream(dest).use { outs -> ins.copyTo(outs) }
                    }
                    pickedName = name
                    // 回填相对路径：cwd=home 时引擎可直接读取
                    onPickFile("attachments/" + fname)
                } catch (e: Throwable) {
                    pickedName = null
                }
            }
        }
    }
    DshSheet(
        visible = visible,
        onDismiss = onDismiss,
        maxHeightRatio = 0.75f,
        header = {
            DshSheetHeader(
                title = "提及文件或会话",
                icon = DshIcons.AlternateEmail,
                iconTint = c.brand,
                trailing = {
                    DshIBtn(DshIcons.Close, "关闭", onClick = onDismiss)
                },
            )
        },
        content = {
            DshSecTitle("会话", note = "${sessions.size}")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
            ) {
                if (sessions.isEmpty()) {
                    Text(
                        "暂无会话 · 先去左上角侧栏开一个",
                        style = Type.sec,
                        color = c.ink3,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                    )
                } else {
                    sessions.forEachIndexed { i, s ->
                        if (i > 0) DshDivider()
                        MentionSessionRow(
                            title = s.title,
                            meta = s.cwd.substringAfterLast('/') + " · " + s.updatedAt.takeLast(5),
                            onClick = { onPickSession(s) },
                        )
                    }
                }
            }

            DshSecTitle("文件")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(14.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Icon(DshIcons.InsertDriveFile, null, Modifier.size(17.dp), tint = c.brand)
                    Column(Modifier.weight(1f)) {
                        Text("从本机选择文件", style = Type.rowTitle, color = c.ink)
                        Text("文件会复制到工作区 attachments/ 目录，引擎直接读取真实文件", style = Type.sec, color = c.ink3)
                    }
                    DshBtn(
                        "选择", icon = DshIcons.FolderOpen, small = true, tone = BtnTone.Ghost,
                        onClick = { pickFile.launch(arrayOf("*/*")) },
                    )
                }
                if (pickedName != null) {
                    Spacer(Modifier.height(10.dp))
                    DshDivider()
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Icon(DshIcons.Check, null, Modifier.size(15.dp), tint = c.ok)
                        Text(
                            "已选择：$pickedName",
                            style = Type.sec, color = c.ink2,
                            modifier = Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Text(
                "输入 @ 弹出本面板；选中会话会把「@会话标题」写回输入框，发送时随消息一起提交。",
                style = Type.meta.copy(lineHeight = 17.6.sp),
                color = c.ink3,
                modifier = Modifier.padding(top = 11.dp),
            )
        },
        footer = {
            DshBtn("关闭", tone = BtnTone.Ghost, height = 66.dp, onClick = onDismiss)
        },
    )
}

/** S3 提及 Sheet 的单行会话（标题 + 工作区目录名 · 时间） */
@Composable
private fun MentionSessionRow(
    title: String,
    meta: String,
    onClick: () -> Unit,
) {
    val c = DshTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            DshIcons.Chat,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = c.brand,
        )
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = Type.rowTitle,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                meta,
                style = Type.sec,
                color = c.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            DshIcons.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = c.ink3,
        )
    }
}

/** 从 SAF 返回的 Uri 取文件显示名（用于 @ 引用时的展示）。 */
private fun queryDisplayName(ctx: android.content.Context, uri: Uri): String? {
    return try {
        val cur = ctx.contentResolver.query(uri, null, null, null, null)
        cur?.use {
            if (it.moveToFirst()) {
                val i = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) it.getString(i) else null
            } else null
        }
    } catch (_: Throwable) {
        null
    }
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
                DshBtn("关闭", tone = BtnTone.Ghost, modifier = Modifier.weight(1f), height = 66.dp, onClick = onDismiss)
                DshBtn("重试连接", icon = DshIcons.Sync, modifier = Modifier.weight(1.3f), height = 66.dp, onClick = onRetry)
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
            // v27：模型本身是真实的（来自 session/new 的 configOptions），
            // 但计费方式不是"账号余额" —— 上游只认 API Key，改掉假 note。
            DshSecTitle("DeepSeek 官方", note = "API Key 计费")
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
            DshBtn("完成", tone = BtnTone.Ghost, height = 66.dp, onClick = onDone)
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
                // v30.16：宽度 160→140dp，给「当前」chip 让位，避免行溢出把段控件
                // 压扁、文字省略成「…」（实测仅带 chip 的当前模型中招）。
                DshSeg(
                    options = levels,
                    selectedIndex = levels.indexOf(rowThinking).coerceAtLeast(0),
                    modifier = Modifier.width(140.dp),
                    tall = true,
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

// ============================ v22 修订版 · S3 工具行三个 Sheet ============================

/** 权限档位（v22 修订版「权限档位」Sheet）。默认「工作区内修改」，
 *  与 S9 模式卡、ACP 越界审批的语义一致：仅可查看 / 工作区内修改 / 完全权限。 */
enum class PermTier(val label: String) {
    ViewOnly("仅可查看"),
    Workspace("工作区内修改"),
    Full("完全权限"),
}

/**
 * S3 工具行「+」→「添加」Sheet（v22 修订版）。
 *
 * 设计稿结构：标题行(添加 + 关闭) → 新建（新建文件 / 新建文件夹）→ 上传（上传附件 / 上传图片）。
 * 与之前「+ = 提及选择器」不同，v22 把"添加"拆成独立 Sheet，并把上传收进来。
 * 四类动作都接真 SAF 选择器：新建文件/文件夹走系统创建，上传走 OpenDocument /
 * GetContent 并复制到 dsh 工作区 attachments/，引擎（cwd=home）能真实读到。
 */
@Composable
fun BoxScope.S3AddSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onToast: (String) -> Unit = {},
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val newFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) onToast("已新建文件：" + (queryDisplayName(ctx, uri) ?: "未命名"))
    }
    val newFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                onToast(if (createFolderInTree(ctx, uri)) "已在所选位置创建文件夹：新建文件夹" else "创建文件夹失败")
            }
        }
    }
    val pickAttachment = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val name = copyToAttachments(ctx, uri)
                onToast(if (name != null) "已添加附件：$name" else "添加附件失败")
            }
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val name = copyToAttachments(ctx, uri)
                onToast(if (name != null) "已添加图片：$name" else "添加图片失败")
            }
        }
    }

    val c = DshTheme.colors
    DshSheet(
        visible = visible,
        onDismiss = onDismiss,
        header = {
            DshSheetHeader(
                title = "添加",
                trailing = { DshIBtn(DshIcons.Close, "关闭", onClick = onDismiss) },
            )
        },
        content = {
            DshSecTitle("新建")
            DshCard(padding = PaddingValues(0.dp)) {
                DshMRow(
                    title = "新建文件",
                    subtitle = "在工作区创建空白文件",
                    icon = DshIcons.NoteAdd,
                    onClick = { newFile.launch("") },
                )
                DshDivider()
                DshMRow(
                    title = "新建文件夹",
                    subtitle = "创建目录组织文件",
                    icon = DshIcons.CreateNewFolder,
                    iconTone = ChipTone.Purple,
                    onClick = { newFolder.launch(null) },
                )
            }
            Spacer(Modifier.height(14.dp))
            DshSecTitle("上传")
            DshCard(padding = PaddingValues(0.dp)) {
                DshMRow(
                    title = "上传附件",
                    subtitle = "支持 PDF / Word / Excel / 代码等",
                    icon = DshIcons.Description,
                    chevron = true,
                    onClick = { pickAttachment.launch(arrayOf("*/*")) },
                )
                DshDivider()
                DshMRow(
                    title = "上传图片",
                    subtitle = "支持 PNG / JPG / 截图，多模态输入",
                    icon = DshIcons.Image,
                    iconTone = ChipTone.Ok,
                    chevron = true,
                    onClick = { pickImage.launch("image/*") },
                )
            }
        },
    )
}

/**
 * 把 SAF 选中的文件复制到 dsh 工作区 attachments/ 目录，返回显示名；失败返回 null。
 * 与 S3MentionSheet 同款真链路：引擎 cwd=home，复制后可直接读取真实文件。
 */
private fun copyToAttachments(ctx: android.content.Context, uri: Uri): String? = try {
    val name = queryDisplayName(ctx, uri) ?: ("file_" + System.currentTimeMillis())
    val dir = java.io.File(com.dsh.harness.DshBootstrap.home(ctx.applicationContext), "attachments")
    dir.mkdirs()
    val fname = System.currentTimeMillis().toString() + "_" + name
    val dest = java.io.File(dir, fname)
    ctx.contentResolver.openInputStream(uri)?.use { ins ->
        FileOutputStream(dest).use { outs -> ins.copyTo(outs) }
    }
    name
} catch (_: Throwable) {
    null
}

/** 在用户用 OpenDocumentTree 选中的目录里创建「新建文件夹」子目录（框架 DocumentsContract，无需额外依赖）。 */
private fun createFolderInTree(ctx: android.content.Context, treeUri: Uri): Boolean = try {
    val cr = ctx.contentResolver
    val docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
    val parentUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
    android.provider.DocumentsContract.createDocument(
        cr, parentUri,
        android.provider.DocumentsContract.Document.MIME_TYPE_DIR, "新建文件夹",
    ) != null
} catch (_: Throwable) {
    false
}

/**
 * S3 工具行「盾牌」→「权限档位」Sheet（v22 修订版）。
 *
 * 设计稿结构：标题行(shield + 权限档位 + 关闭)
 *   → 仅可查看 / 工作区内修改(选中) / 完全权限
 *   → 脚注：权限档位决定 Agent 对工作区的操作边界，越界操作始终需要你确认。
 *
 * 权限档位在 ACP 里不是会话级预设（协议无 session/set_mode），而是越界时
 * per-request 的 session/request_permission 审批；这里选中的是当前档位，仅作
 * 本地状态与 UI 呈现，切换给出 toast，不编造"已生效"的服务端语义。
 */
@Composable
fun BoxScope.S3PermSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    initialTier: PermTier = PermTier.Workspace,
    onToast: (String) -> Unit = {},
) {
    var sel by remember { mutableStateOf(initialTier) }
    val c = DshTheme.colors

    val tiers = listOf(
        Triple(PermTier.ViewOnly, "只读模式，不修改任何文件", DshIcons.Visibility to ChipTone.Ok),
        Triple(PermTier.Workspace, "可在选定工作区内读写文件", DshIcons.Edit to ChipTone.Brand),
        Triple(PermTier.Full, "可执行命令，越界操作需确认", DshIcons.Security to ChipTone.Warn),
    )

    DshSheet(
        visible = visible,
        onDismiss = onDismiss,
        header = {
            DshSheetHeader(
                title = "权限档位",
                icon = DshIcons.Shield,
                iconTint = c.brand,
                trailing = { DshIBtn(DshIcons.Close, "关闭", onClick = onDismiss) },
            )
        },
        content = {
            DshCard(padding = PaddingValues(0.dp)) {
                tiers.forEachIndexed { i, (tier, sub, iconAndTone) ->
                    if (i > 0) DshDivider()
                    val (icon, tone) = iconAndTone
                    val on = sel == tier
                    DshMRow(
                        title = tier.label,
                        subtitle = sub,
                        icon = icon,
                        iconTone = tone,
                        selected = on,
                        trailing = if (on) ({ Icon(DshIcons.CheckCircle, "当前", Modifier.size(19.dp), tint = c.brand) }) else null,
                        onClick = {
                            sel = tier
                            onToast("已切换：${tier.label}")
                        },
                    )
                }
            }
            Text(
                "权限档位决定 Agent 对工作区的操作边界，越界操作始终需要你确认。",
                style = Type.meta.copy(lineHeight = 17.6.sp),
                color = c.ink3,
                modifier = Modifier.padding(top = 10.dp),
            )
        },
    )
}

/**
 * S3 工具行「附件」→「附件」Sheet（v22 修订版）。
 *
 * 设计稿结构：标题行(attach_file + 附件 + 关闭)
 *   → 上传附件（从文件管理器选择）/ 上传图片（从相册选择或拍照）。
 *
 * 接真 SAF 选择器：选中的文件复制到 dsh 工作区 attachments/，并把相对路径
 * 经 [onPicked] 回填输入框（与 @ 提及同一真链路），发送时随消息提交。
 */
@Composable
fun BoxScope.S3AttachSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onToast: (String) -> Unit = {},
    onPicked: (String) -> Unit = {},
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val pickAttachment = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val name = copyToAttachments(ctx, uri)
                if (name != null) {
                    onPicked("attachments/" + System.currentTimeMillis().toString() + "_" + name)
                    onToast("已添加附件：$name")
                } else onToast("添加附件失败")
            }
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val name = copyToAttachments(ctx, uri)
                if (name != null) {
                    onPicked("attachments/" + System.currentTimeMillis().toString() + "_" + name)
                    onToast("已添加图片：$name")
                } else onToast("添加图片失败")
            }
        }
    }

    val c = DshTheme.colors
    DshSheet(
        visible = visible,
        onDismiss = onDismiss,
        header = {
            DshSheetHeader(
                title = "附件",
                icon = DshIcons.AttachFile,
                iconTint = c.brand,
                trailing = { DshIBtn(DshIcons.Close, "关闭", onClick = onDismiss) },
            )
        },
        content = {
            DshCard(padding = PaddingValues(0.dp)) {
                DshMRow(
                    title = "上传附件",
                    subtitle = "从文件管理器选择",
                    icon = DshIcons.Description,
                    chevron = true,
                    onClick = { pickAttachment.launch(arrayOf("*/*")) },
                )
                DshDivider()
                DshMRow(
                    title = "上传图片",
                    subtitle = "从相册选择或拍照",
                    icon = DshIcons.Image,
                    iconTone = ChipTone.Ok,
                    chevron = true,
                    onClick = { pickImage.launch("image/*") },
                )
            }
        },
    )
}
