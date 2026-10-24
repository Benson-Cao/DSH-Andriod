package com.dsh.harness.mobile.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
    /** v22 修订版：新增「导出数据」→ S46 导出页 */
    onExport: () -> Unit = {},
    /** v20：会话分组「已归档 · 全部」→ S48 归档管理 */
    onArchived: () -> Unit = {},
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

        // —— 一级入口 4 行：行式（不是按钮），行高约 34 ——
        Column(Modifier.padding(vertical = 2.dp)) {
            NavRow("新会话", DshIcons.Add, selected = selectedNav == 0, onClick = onNewChat)
            NavRow("插件", DshIcons.Extension, selected = selectedNav == 1, badge = "12", onClick = onPlugins)
            NavRow("自动化任务", DshIcons.Schedule, selected = selectedNav == 2, badge = "3", onClick = onAutomation)
            NavRow("工作区", DshIcons.Folder, selected = selectedNav == 3, badge = "3", onClick = onWorkspace)
        }

        // —— 会话分组区：只有这里滚动（设计稿明确）——
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 6.dp),
        ) {
            // v22 修订版：侧栏新增手势提示（设计稿 9.5px ink3 + swipe_left 图标）
            Row(
                Modifier.padding(start = 10.dp, top = 2.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(DshIcons.SwipeLeft, null, Modifier.size(11.dp), tint = c.ink3)
                Text(
                    "左滑会话可删除 / 归档",
                    style = Type.meta.copy(fontSize = 9.5.sp),
                    color = c.ink3,
                )
            }
            if (liveSessions.isNotEmpty()) {
                // 真实数据：按 cwd 分组，与设计稿「按工作区分组」一致
                GroupHeader("会话", more = liveSessions.size.toString())
                liveSessions.forEach { s ->
                    SessionRow(
                        title = s.title,
                        time = s.updatedAt.takeLast(5),
                        selected = false,
                    )
                }
            } else {
                GroupHeader("工作区会话", more = "搜索")
                SessionRow("生成 Q3 数据汇总 PPT", "19:32", selected = true)
                SessionRow("准备明天评审会的议题清单", "昨天")

                GroupHeader("数据分析项目", more = "12")
                SessionRow("给销售报表补上同比 / 环比列", "周一")
                SessionRow("关于代码运行能力的分析", "上周")

                GroupHeader("DSH 插件开发")
                // 空态：设计稿要求四态覆盖，「暂无会话」是真实承载点之一
                Text(
                    "暂无会话",
                    style = Type.meta.copy(fontSize = 11.5.sp),
                    color = c.ink3,
                    modifier = Modifier.padding(start = 24.dp, top = 7.dp, bottom = 7.dp),
                )

                GroupHeader("已归档", more = "全部", onMoreClick = onArchived)
                SessionRow("认识 DeepSeek Harness", "8/13")
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
                    "DeepSeek 凭据",
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

            // v22 修订版：Termux 行已从侧栏移除（见 onTermux 注释）
            NavRow(
                title = "导出数据",
                icon = DshIcons.FileDownload,
                selected = false,
                onClick = onExport,
            )

            NavRow(
                title = "存储目录",
                icon = DshIcons.FolderOpen,
                selected = false,
                onClick = onStorage,
            )

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
) {
    val c = DshTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) c.soft else Color.Transparent)
            .clickable { }
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