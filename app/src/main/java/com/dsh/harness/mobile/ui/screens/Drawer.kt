package com.dsh.harness.mobile.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.harness.mobile.ui.components.*
import com.dsh.harness.mobile.ui.theme.*

/**
 * S4 侧边栏（Web 同款行式导航）
 *
 * 设计稿原文：
 *   侧栏宽 268/375 = 71%；品牌栏 6% ｜ 一级入口 4 行 17%(行高 34，行式非按钮)
 *   ｜ 会话分组区 46%(3 组 + 空态「暂无会话」) ｜ 栏底 13%(账号 + 设置) ｜ 版本脚注
 *   右侧 29% 露出主屏首页并覆遮罩
 *   "一级入口与栏底固定，只有会话列表区滚动；宽屏 ≥768 转为常驻、不遮主屏"
 *
 * v6 决策：取消底部 Tab，侧栏是唯一一级导航枢纽。
 * 这也是为什么 App 里没有一个底部导航栏 —— 不是漏做，是设计稿明确去掉���。
 */
@Composable
fun BoxScope.S4Drawer(
    visible: Boolean,
    onClose: () -> Unit,
    onNewChat: () -> Unit,
    onPlugins: () -> Unit,
    onAutomation: () -> Unit,
    onWorkspace: () -> Unit,
    onSettings: () -> Unit,
    onAccount: () -> Unit,
    /**
     * v22 修订版：**「本机服务 · Termux」入口从侧栏移除**。
     * 设计稿把这一行删了（S27 不再是侧栏一级入口）。Termux 终端本身仍可用，
     * 入口收回到 S14 设置页；这里不再重复占一个侧栏位。
     */
    /** v30：SAF 存储目录桥入口 */
    onStorage: () -> Unit = {},
    /** v22 修订版：「工作区」分组头添加面板的「从 NAS 挂载」→ S51 */
    onNas: () -> Unit = {},
    /** v22 修订版：新增「导出数据」→ S46 导出页 */
    onExport: () -> Unit = {},
    /** v20：会话分组「已归档 · 全部」→ S48 归档管理 */
    onArchived: () -> Unit = {},
    /** v30.23：长按会话 → 删除（真实文件删除 + 刷新列表） */
    onDeleteSession: (String) -> Unit = {},
    /** v30.23：长按会话 → 归档（移动到 .dsh/sessions-archive/ + 刷新列表） */
    onArchiveSession: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    selectedNav: Int = 0,
    /** 来自 ACP session/list 的真实会话；为空时退回设计稿演示数据 */
    liveSessions: List<com.dsh.harness.mobile.data.DshViewModel.Session> = emptyList(),
    /** v29：真实密钥状态，替代原写死的 ¥4.08 假余额 */
    keyConfigured: Boolean = false,
) {
    val c = DshTheme.colors

    if (!visible) return

    // 遮罩：右侧露出的主屏区域盖住
    Box(
        Modifier
            .fillMaxSize()
            .background(c.mask)
            .clickable(onClick = onClose),
    )

    Column(
        modifier = modifier
            .align(Alignment.CenterStart)
            .fillMaxHeight()
            .width(Metrics.DRAWER_W)
            .clip(RoundedCornerShape(topEnd = Radius.sheet, bottomEnd = Radius.sheet))
            .background(c.card)
            .rightBorder(c.line),
    ) {
        // —— 品牌栏（设计稿 h78，padding 30/14/0）——
        Row(
            Modifier
                .fillMaxWidth()
                .height(78.dp)
                .padding(start = 14.dp, end = 14.dp, top = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DshBrandmark(Modifier.weight(1f))
            DshIBtn(DshIcons.Close, "关闭侧边栏", onClick = onClose)
        }

        // —— 一级入口（设计稿 S4 顺序）：新会话 / 插件 / 自动化任务 / 导出数据 / 存储目录 ——
        Column(Modifier.padding(vertical = 2.dp)) {
            NavRow("新会话", DshIcons.Add, selected = selectedNav == 0, onClick = onNewChat)
            NavRow("插件", DshIcons.Extension, selected = selectedNav == 1, badge = "12", onClick = onPlugins)
            NavRow("自动化任务", DshIcons.Schedule, selected = selectedNav == 2, badge = "3", onClick = onAutomation)
            NavRow("导出数据", DshIcons.FileDownload, selected = false, onClick = onExport)
            NavRow("存储目录", DshIcons.FolderShared, selected = false, onClick = onStorage)
        }

        // 会话分组区工具状态（设计稿 S4「工作区」分组头的 搜索/排序/添加）
        var wsPanel by remember { mutableStateOf<String?>(null) }   // null/q(搜索)/s(排序)/a(添加)
        var wsQuery by remember { mutableStateOf("") }
        var wsSort by remember { mutableStateOf(0) }                 // 0 最近活动 / 1 创建时间 / 2 名称

        // —— 会话分组区：只有这里滚动（设计稿明确）——
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 6.dp),
        ) {
            // 工作区分组头 + 搜索/排序/添加 工具（设计稿 .grp 工作区）
            WorkspaceGroupHeader(
                activeTool = wsPanel,
                onTool = { t -> wsPanel = if (wsPanel == t) null else t },
            )
            when (wsPanel) {
                "q" -> {
                    // 搜索面板：实时过滤会话标题
                    Row(
                        Modifier
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.card2)
                            .border(1.dp, c.line, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Icon(DshIcons.Search, null, Modifier.size(14.dp), tint = c.ink2)
                        Box(Modifier.weight(1f)) {
                            if (wsQuery.isEmpty()) {
                                Text("搜索当前工作区会话…", style = Type.sec, color = c.ink2)
                            }
                            BasicTextField(
                                value = wsQuery,
                                onValueChange = { wsQuery = it },
                                singleLine = true,
                                textStyle = Type.sec.copy(color = c.ink),
                                cursorBrush = SolidColor(c.brand),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Text(
                        "输入关键词实时过滤当前工作区的会话标题",
                        style = Type.meta.copy(fontSize = 10.sp),
                        color = c.ink3,
                        modifier = Modifier.padding(start = 14.dp, top = 6.dp),
                    )
                }
                "s" -> {
                    val sorts = listOf("最近活动", "创建时间", "名称 A-Z")
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        sorts.forEachIndexed { i, label ->
                            DshChip(
                                label,
                                tone = if (wsSort == i) ChipTone.Brand else ChipTone.Neutral,
                                onClick = { wsSort = i },
                            )
                        }
                    }
                    Text(
                        "当前：${sorts[wsSort]}",
                        style = Type.meta.copy(fontSize = 10.sp),
                        color = c.ink3,
                        modifier = Modifier.padding(start = 14.dp, top = 4.dp),
                    )
                }
                "a" -> {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) {
                        WsAddRow(DshIcons.CreateNewFolder, "新建工作区", onClick = onWorkspace)
                        WsAddRow(DshIcons.FolderOpen, "添加本地文件夹", onClick = onStorage)
                        WsAddRow(DshIcons.Cloud, "从 NAS 挂载", onClick = onNas)
                    }
                }
            }

            // v30.23：手势提示改为长按（左滑在窄列表行上易误触、且从未实现）
            Row(
                Modifier.padding(start = 10.dp, top = 4.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(DshIcons.SwipeLeft, null, Modifier.size(11.dp), tint = c.ink3)
                Text("长按会话可删除 / 归档", style = Type.meta.copy(fontSize = 9.5.sp), color = c.ink3)
            }

            if (liveSessions.isNotEmpty()) {
                // 真实数据：按 cwd（工作区）分组 + 搜索过滤 + 排序，与设计稿「按工作区分组」一致
                val q = wsQuery.trim()
                val grouped = liveSessions
                    .filter { q.isEmpty() || it.title.contains(q, ignoreCase = true) }
                    .groupBy { it.cwd }
                    .toList()
                    .sortedBy { it.first }
                grouped.forEachIndexed { idx, (cwd, list) ->
                    val sorted = when (wsSort) {
                        1 -> list.sortedBy { it.updatedAt }
                        2 -> list.sortedBy { it.title }
                        else -> list.sortedByDescending { it.updatedAt }
                    }
                    // 第一个工作区的会话直接跟在「工作区」头下（同设计稿）；其余工作区各自加分组头
                    if (idx > 0) {
                        GroupHeader(
                            cwd.substringAfterLast('/').ifEmpty { "工作区" },
                            more = list.size.toString(),
                        )
                    }
                    sorted.forEach { s ->
                        SessionRow(
                            title = s.title,
                            time = s.updatedAt.takeLast(5),
                            onDelete = { onDeleteSession(s.id) },
                            onArchive = { onArchiveSession(s.id) },
                        )
                    }
                }
                if (grouped.isEmpty()) {
                    Text(
                        "没有匹配的会话",
                        style = Type.meta.copy(fontSize = 11.5.sp),
                        color = c.ink3,
                        modifier = Modifier.padding(start = 24.dp, top = 7.dp, bottom = 7.dp),
                    )
                }
            } else {
                // v30.23：删掉设计稿演示假数据（「生成 Q3 数据汇总 PPT」等），
                //         改诚实空态——用户明确要求清退「未命名会话」式假列表。
                Text(
                    "暂无会话",
                    style = Type.meta.copy(fontSize = 11.5.sp),
                    color = c.ink3,
                    modifier = Modifier.padding(start = 24.dp, top = 7.dp, bottom = 7.dp),
                )

                GroupHeader("已归档", more = "全部", onMoreClick = onArchived)
                Text(
                    "暂无归档",
                    style = Type.meta.copy(fontSize = 11.5.sp),
                    color = c.ink3,
                    modifier = Modifier.padding(start = 24.dp, top = 7.dp, bottom = 7.dp),
                )
            }
        }

        // —— 栏底：账号 + 设置，固定不滚 ——
        Column(
            Modifier
                .fillMaxWidth()
                .topLine(c.line)
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .clickable { onAccount() }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(22.dp)
                        .background(Brush.linearGradient(listOf(c.brand, c.purple)), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "D",
                        style = Type.meta.copy(
                            fontSize = 11.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        ),
                        color = Color.White,
                    )
                }
                Text(
                    "DeepSeek 账号",
                    style = Type.rowTitle,
                    color = c.ink,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (keyConfigured) "已配置 API Key" else "未配置",
                    style = Type.meta.copy(fontSize = 10.5.sp),
                    color = if (keyConfigured) c.ink2 else c.ink3,
                )
            }

            // v22 修订版：栏底只剩「设置」（导出数据/存储目录已移到顶部一级入口，
            // 与设计稿 S4 栏底「账号 + 设置」两行一致；Termux 行早已移除）
            NavRow(
                title = "设置",
                icon = DshIcons.Settings,
                selected = false,
                badge = "v0.2.1-alpha.4",
                onClick = onSettings,
            )
        }
    }
}

/** 侧栏行 .navrow：内距 8/14，margin 1/8，r9，13.5px；选中 soft 底 + brand 字 */
@Composable
private fun NavRow(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    badge: String? = null,
) {
    val c = DshTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) c.soft else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            icon, null,
            Modifier.size(16.dp),
            tint = if (selected) c.brand else c.ink2.copy(alpha = 0.85f),
        )
        Text(
            title,
            style = Type.chip.copy(fontSize = 13.5.sp),
            color = if (selected) c.brand else c.ink2,
            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold
            else androidx.compose.ui.text.font.FontWeight.Normal,
            maxLines = 1,
        )
        if (badge != null) {
            Spacer(Modifier.weight(1f))
            Text(badge, style = Type.meta.copy(fontSize = 10.5.sp), color = c.ink3, maxLines = 1)
        }
    }
}

/** 分组标题 .grp：10.5/600 ink3，字距 .5，内距 14/18/5；more 可点（S48 已归档入口） */
@Composable
private fun GroupHeader(title: String, more: String? = null, onMoreClick: (() -> Unit)? = null) {
    val c = DshTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            title,
            style = Type.meta.copy(
                letterSpacing = 0.5.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            ),
            color = c.ink3,
            maxLines = 1,
        )
        if (more != null) {
            Spacer(Modifier.weight(1f))
            Text(
                more,
                style = Type.meta.copy(fontSize = 10.sp),
                color = if (onMoreClick != null) c.brand else c.ink3.copy(alpha = 0.75f),
                modifier = if (onMoreClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onMoreClick() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                } else {
                    Modifier
                },
            )
        }
    }
}

/** 会话行 .srow：内距 7/14/7/24，r8；选中时左侧有 2.5px 品牌色竖条 */
@Composable
private fun SessionRow(
    title: String,
    time: String,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    /** v30.23：长按弹出「归档 / 删除」菜单（替代设计稿的左滑手势——列表行窄，滑动手势易误触） */
    onDelete: (() -> Unit)? = null,
    onArchive: (() -> Unit)? = null,
) {
    val c = DshTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    val hasMenu = onDelete != null || onArchive != null
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) c.soft else Color.Transparent)
                .clickable { onClick?.invoke() }
                .pointerInput(hasMenu) {
                    if (hasMenu) detectTapGestures(onLongPress = { menuOpen = true })
                }
                .padding(start = 14.dp, end = 7.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (selected) {
                Box(
                    Modifier
                        .width(2.5.dp)
                        .height(13.dp)
                        .background(c.brand, RoundedCornerShape(2.dp)),
                )
            }
            Text(
                title,
                style = Type.chip.copy(fontSize = 12.5.sp),
                color = if (selected) c.brand else c.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(time, style = Type.meta.copy(fontSize = 10.sp), color = c.ink3)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (onArchive != null) {
                DropdownMenuItem(
                    text = { Text("归档", style = Type.chip.copy(fontSize = 13.sp), color = c.ink) },
                    onClick = { menuOpen = false; onArchive() },
                )
            }
            if (onDelete != null) {
                DropdownMenuItem(
                    text = { Text("删除", style = Type.chip.copy(fontSize = 13.sp), color = c.danger) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

/** 工作区分组头 + 搜索/排序/添加 工具（设计稿 S4「.grp 工作区」带 ws-tool 三图标）。
 *  点工具切换对应面板（同设计稿 toggle-panel），activeTool 为当前展开的面板 id。 */
@Composable
private fun WorkspaceGroupHeader(activeTool: String?, onTool: (String) -> Unit) {
    val c = DshTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "工作区",
            style = Type.meta.copy(
                letterSpacing = 0.5.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            ),
            color = c.ink3,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        WsToolIcon(DshIcons.Search, active = activeTool == "q") { onTool("q") }
        WsToolIcon(DshIcons.FilterList, active = activeTool == "s") { onTool("s") }
        WsToolIcon(DshIcons.Add, active = activeTool == "a") { onTool("a") }
    }
}

/** 工作区分组头右侧小工具图标（设计稿 .ws-tool：26dp 触区，active 时 soft 底 + brand 字） */
@Composable
private fun WsToolIcon(icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val c = DshTheme.colors
    Box(
        Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) c.soft else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(15.dp), tint = if (active) c.brand else c.ink3)
    }
}

/** 「添加」面板的行（设计稿 .ws-addrow：图标 + 文字，点击执行动作） */
@Composable
private fun WsAddRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    val c = DshTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = c.ink2)
        Text(title, style = Type.chip.copy(fontSize = 12.5.sp), color = c.ink)
    }
}

/** 抽屉右缘 1px 分隔线（设计稿 border-right:1px solid var(--line)） */
private fun Modifier.rightBorder(color: Color): Modifier = drawBehind {
    drawLine(
        color = color,
        start = Offset(size.width - 0.5f, 0f),
        end = Offset(size.width - 0.5f, size.height),
        strokeWidth = 1f,
    )
}