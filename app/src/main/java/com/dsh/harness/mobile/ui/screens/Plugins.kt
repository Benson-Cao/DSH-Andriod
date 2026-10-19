package com.dsh.harness.mobile.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.dsh.harness.mobile.data.DshEnvProbe
import com.dsh.harness.mobile.data.DshPluginCatalog
import com.dsh.harness.mobile.ui.components.*
import com.dsh.harness.mobile.ui.theme.*

/**
 * v11 设计稿 §05 插件生态 —— 官方内置与 dsh-market **两版**。
 *
 * ## 为什么拆两版（设计稿 v11 变更说明原文）
 * > 官方内置插件是随发行带上的 Cordis bundle（一百多个，启停即时生效、
 * > **不存在来源确认这一步**）；dsh-market 是社区精选市场，得先
 * > `dsh plugin --profile web add dshmarket` 把市场本身装进去。
 *
 * 此前混在一屏里会误导：内置插件不该出现"确认来源"，市场插件也不可能启停即生效。
 *
 * ## 数据真伪（这一版最重要的改动）
 * S19 的插件列表**不再是空的**。真身是 `DshPluginCatalog.load()`：
 * 跑一次 `dsh --profile acp --dump-config` 并解析，本机实测 86 个条目、
 * 3 个 disabled、33 个带 config。每个 id / 包名 / 启停状态都来自 dsh 自己。
 *
 * 市场侧（S21/S22/S23）本机**未安装 dshmarket**，所以按设计稿的规则显示
 * "需先安装市场本体"与 CLI 命令，而不是拿设计稿里的示意插件名充数。
 */

// ============================ 共享数据与小组件 ============================

/** S12/S19 分类筛选。设计稿给了四个语义类，但 dsh 不给分类字段，
 *  所以这里只提供"全部 / 仅停用 / 带配置"这种**可从真实数据判定**的筛选。 */
private enum class PlugFilter(val label: String) {
    All("全部"),
    Disabled("仅停用"),
    Configured("带配置"),
}

private fun DshPluginCatalog.Builtin.matches(f: PlugFilter): Boolean = when (f) {
    PlugFilter.All -> true
    PlugFilter.Disabled -> !enabled
    PlugFilter.Configured -> configKeys.isNotEmpty()
}

/** 分类 chips 行。设计稿是横滑的多个 chip，这里用同一套组件 + 横向滚动。 */
@Composable
private fun PlugFilterRow(
    selected: PlugFilter,
    onSelect: (PlugFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        PlugFilter.entries.forEach { f ->
            DshChip(
                text = f.label,
                tone = if (f == selected) ChipTone.On else ChipTone.Neutral,
                onClick = { onSelect(f) },
            )
        }
    }
}

/** 加载中占位。设计稿没有 loading 态组件，但真实跑 dump 要几秒，必须有交代。 */
@Composable
private fun PlugLoading(modifier: Modifier = Modifier) {
    val c = DshTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = c.brand,
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(10.dp))
        Text("正在读取插件配置…", style = Type.sec, color = c.ink3)
    }
}

/** 出错态：把真实原因显示出来，不做"暂无数据"式的遮掩。 */
@Composable
private fun PlugError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val c = DshTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(DshIcons.ErrorOutline, null, Modifier.size(22.dp), tint = c.danger.copy(alpha = 0.8f))
        Spacer(Modifier.height(8.dp))
        Text("读取插件配置失败", style = Type.rowTitle, color = c.ink)
        Spacer(Modifier.height(4.dp))
        Text(
            message,
            style = Type.meta,
            color = c.ink3,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        DshBtn("重试", tone = BtnTone.Ghost, small = true, modifier = Modifier.widthIn(max = 160.dp), onClick = onRetry)
    }
}

/** 说明卡：设计稿里每个市场/内置页顶部那张 soft 底说明条。 */
@Composable
private fun PlugNoteCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    background: Color? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = DshTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xl))
            .background(background ?: c.soft)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = iconTint)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.rowTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = Type.meta, color = c.ink3, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing()
    }
}

/** 列表卡容器：白底 + 圆角 + 细边，内部行由调用方给 DshDivider 分隔。 */
@Composable
private fun PlugCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = DshTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xl))
            .background(c.card)
            .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
        content = content,
    )
}

/**
 * 一个内置插件行。设计稿 .mrow + 行内开关右对齐；
 * 停用项用警示色图标 + "已停用" chip（设计稿：状态不靠颜色单独表达）。
 */
@Composable
private fun BuiltinRow(
    plugin: DshPluginCatalog.Builtin,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DshTheme.colors
    val tone = if (plugin.enabled) ChipTone.On else ChipTone.Warn
    val icon = pluginIcon(plugin)
    DshMRow(
        title = plugin.id,
        subtitle = buildString {
            append(plugin.shortName)
            if (plugin.configKeys.isNotEmpty()) append(" · ${plugin.configKeys.size} 项配置")
            if (!plugin.enabled) append(" · 需重启生效")
        },
        icon = icon,
        iconTone = tone,
        onClick = onClick,
        modifier = modifier,
        trailing = {
            if (!plugin.enabled) {
                DshChip("已停用", tone = ChipTone.Warn, mini = true)
            } else {
                // 开关态用只读形态表达：真实启停由配置文件决定，页面上不给假交互
                Icon(DshIcons.CheckCircle, "已启用", Modifier.size(17.dp), tint = c.ok)
            }
        },
    )
}

/** 按插件 id 的语义给一个图标。纯展示映射，不影响任何行为。 */
private fun pluginIcon(p: DshPluginCatalog.Builtin): ImageVector = when {
    p.id.contains("llm") -> DshIcons.Hub
    p.id.contains("session") -> DshIcons.Forum
    p.id.contains("agent") -> DshIcons.Psychology
    p.id.contains("timer") || p.id.contains("job") || p.id.contains("schedule") -> DshIcons.Schedule
    p.id.contains("fs") || p.id.contains("file") || p.id.contains("skill") -> DshIcons.Folder
    p.id.contains("auth") || p.id.contains("credential") || p.id.contains("settings") -> DshIcons.Lock
    p.id.contains("log") || p.id.contains("title") -> DshIcons.Subject
    p.id.contains("hmr") || !p.enabled -> DshIcons.StopCircle
    else -> DshIcons.SettingsInputComponent
}

// ============================ S12 插件 · 分流入口 ============================

/**
 * S12 插件分流。
 *
 * 设计稿布局：状态栏 5% ｜ 导航 6% ｜ **两版分流卡 9%** ｜ 三段 Tab 6% ｜
 *   分类 chips 6% ｜ 已安装列表 50% ｜ 创建插件入口卡 14% ｜ home 4%
 *
 * 与旧版的差别（v11 变更）：
 *  1. 顶部新增**两版分流卡**——官方内置 / dsh-market，点进去走完全不同的流程；
 *  2. "已安装"列表改用真实 dump 数据，不再是永久空态；
 *  3. 顶栏从汉堡改成返回（用户明确要求，二级页不该有汉堡）。
 */
@Composable
fun S12Plugins(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit = {},
    catalog: DshPluginCatalog.Catalog = DshPluginCatalog.Catalog(emptyList(), null, 0L),
    loading: Boolean = false,
    onRefresh: () -> Unit = {},
    onOpenBuiltin: () -> Unit = {},
    onOpenMarket: () -> Unit = {},
    /** v20：顶栏搜索 → S45 全局搜索 */
    onSearch: () -> Unit = {},
    /** v20：配置 tab「创建自己的插件」→ S42 会话式创建 */
    onCreatePlugin: () -> Unit = {},
    /** v20：发现 tab 索引源条 → S44 索引源管理 */
    onAddSource: () -> Unit = {},
) {
    val c = DshTheme.colors
    var tab by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf(PlugFilter.All) }

    // 进入页面时拉一次真实目录（跑 dump 要几秒，不能放冷启动）
    LaunchedEffect(Unit) { onRefresh() }

    val shown = remember(catalog.plugins, filter) {
        catalog.plugins.filter { it.matches(filter) }
    }

    DshScreen(modifier) {
        DshNavBar(
            onBack = onBack,
            title = "插件",
            stacked = true,
            actions = {
                DshIBtn(DshIcons.Search, "搜索", onClick = onSearch)
                DshIBtn(DshIcons.SettingsInputComponent, "插件配置", onClick = { tab = 2 })
            },
        )

        // —— 两版分流卡（v11 新增）——
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.lg).padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            SplitCard(
                icon = DshIcons.Build,
                iconTint = c.brand,
                title = "官方内置插件",
                desc = "${catalog.plugins.size} 个随发行内置\n启停即生效，无来源确认",
                highlighted = true,
                modifier = Modifier.weight(1f),
                onClick = onOpenBuiltin,
            )
            SplitCard(
                icon = DshIcons.Storefront,
                iconTint = c.purple,
                title = "dsh-market",
                desc = "社区精选市场 · 需先装\n装完在设置里多一个 tab",
                highlighted = false,
                modifier = Modifier.weight(1f),
                onClick = onOpenMarket,
            )
        }

        // —— 三段 Tab ——
        Column(Modifier.padding(horizontal = Space.lg).padding(top = 10.dp)) {
            DshTabs(listOf("已安装", "发现", "配置"), tab) { tab = it }
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
                0 -> {
                    PlugFilterRow(filter, { filter = it })
                    Spacer(Modifier.height(10.dp))

                    when {
                        loading -> PlugLoading()
                        catalog.error != null -> PlugError(catalog.error!!, onRefresh)
                        catalog.plugins.isEmpty() -> DshEmpty("尚未读取到内置插件", icon = DshIcons.Extension)
                        else -> {
                            Text(
                                "共 ${catalog.plugins.size} 个 · 启用 ${catalog.plugins.count { it.enabled }} · " +
                                    "停用 ${catalog.plugins.count { !it.enabled }}",
                                style = Type.meta,
                                color = c.ink3,
                                modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
                            )
                            PlugCard {
                                shown.forEachIndexed { i, p ->
                                    BuiltinRow(p, onClick = onOpenBuiltin)
                                    if (i != shown.lastIndex) DshDivider()
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // 发现：索引源条置顶。本机未装市场 → 如实说明，不列示意插件
                    PlugNoteCard(
                        icon = DshIcons.Hub,
                        iconTint = c.brand,
                        title = "索引源：尚未添加",
                        subtitle = "预置为空，需自行添加符合规范的索引文件",
                        trailing = {
                            Text("添加", style = Type.chip, color = c.brand, fontWeight = FontWeight.SemiBold)
                        },
                        modifier = Modifier.clickable { onAddSource() },
                    )
                    Spacer(Modifier.height(12.dp))
                    DshEmpty("没有可浏览的插件", icon = DshIcons.Search)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "点击安装会先看来源确认：展示软件包出处、Git 仓库、提交号与目录。" +
                            "索引内容由维护者决定，不等于官方审核过的应用商店。",
                        style = Type.meta,
                        color = c.ink3,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }

                else -> {
                    PlugCard {
                        MrowLink("插件分流", "官方内置与 dsh-market 是两套路径", DshIcons.CallSplit) {
                            tab = 0
                        }
                        DshDivider()
                        MrowLink("内置插件目录", "真实读取自 dsh --dump-config", DshIcons.Build) {
                            onOpenBuiltin()
                        }
                        DshDivider()
                        MrowLink("插件市场", "社区精选 · 需先安装市场本体", DshIcons.Storefront) {
                            onOpenMarket()
                        }
                        DshDivider()
                        MrowLink("实验性组合包", "自定义插件市场与兼容层", DshIcons.Science, last = true) {}
                    }
                    Spacer(Modifier.height(12.dp))
                    PlugNoteCard(
                        icon = DshIcons.AutoAwesome,
                        iconTint = c.purple,
                        title = "创建自己的插件",
                        subtitle = "用 JavaScript 扩展工具与指令",
                        modifier = Modifier.clickable { onCreatePlugin() },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** 分流卡：设计稿里 flex:1 的两张小卡，选中侧用品牌色描边 + soft 底。 */
@Composable
private fun SplitCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    desc: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DshTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.xl))
            .background(if (highlighted) c.soft else c.card)
            .border(
                1.dp,
                if (highlighted) c.brand else c.line,
                RoundedCornerShape(Radius.xl),
            )
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(15.dp), tint = iconTint)
            Spacer(Modifier.width(6.dp))
            Text(
                title,
                style = Type.rowTitle.copy(fontSize = 12.5.sp),
                color = if (highlighted) c.brand else c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(desc, style = Type.meta, color = c.ink3, maxLines = 2)
    }
}

/** 「配置」Tab 里的纯链接行。 */
@Composable
private fun MrowLink(
    title: String,
    subtitle: String,
    icon: ImageVector,
    last: Boolean = false,
    onClick: () -> Unit,
) {
    DshMRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconTone = ChipTone.Neutral,
        chevron = true,
        onClick = onClick,
        modifier = if (last) Modifier.padding(bottom = 0.dp) else Modifier,
    )
}

// ============================ S19 官方内置插件 · 已安装 ============================

/**
 * S19 内置插件列表。
 *
 * 设计稿布局：状态栏 5% ｜ 导航 6% ｜ 说明卡 8% ｜ 分类 chips 5% ｜
 *   插件列表 46% ｜ 回滚与 dump-config 说明 8% ｜ 切市场版按钮 7% ｜ home 4%
 *   "行内开关右对齐、与整行同高可点；停用项用琥珀色图标与「已停用」标签"
 *
 * 本屏是**全 App 唯一拿到真实插件清单的地方**（86 条，来自 dump-config）。
 */
@Composable
fun S19BuiltinPlugins(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenPlugin: (String) -> Unit = {},
    onSwitchToMarket: () -> Unit = {},
    catalog: DshPluginCatalog.Catalog = DshPluginCatalog.Catalog(emptyList(), null, 0L),
    loading: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    val c = DshTheme.colors
    var filter by remember { mutableStateOf(PlugFilter.All) }
    LaunchedEffect(Unit) { onRefresh() }

    val shown = remember(catalog.plugins, filter) { catalog.plugins.filter { it.matches(filter) } }

    DshScreen(modifier) {
        DshNavBar(
            onBack = onBack,
            title = "内置插件",
            stacked = true,
            actions = { DshIBtn(DshIcons.Search, "搜索", onClick = {}) },
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg)
                .padding(top = 10.dp),
        ) {
            PlugNoteCard(
                icon = DshIcons.Build,
                iconTint = c.brand,
                title = "随发行内置 ${catalog.plugins.size} 个能力插件",
                subtitle = if (catalog.elapsedMs > 0)
                    "实时读取自 dsh --dump-config · 耗时 ${catalog.elapsedMs}ms"
                else "实时读取自 dsh --dump-config",
                trailing = { DshChip("Cordis", mini = true) },
            )
            Spacer(Modifier.height(10.dp))
            PlugFilterRow(filter, { filter = it })
            Spacer(Modifier.height(10.dp))

            when {
                loading -> PlugLoading()
                catalog.error != null -> PlugError(catalog.error!!, onRefresh)
                catalog.plugins.isEmpty() -> DshEmpty("尚未读取到内置插件", icon = DshIcons.Extension)
                else -> {
                    PlugCard {
                        shown.forEachIndexed { i, p ->
                            BuiltinRow(p, onClick = { onOpenPlugin(p.id) })
                            if (i != shown.lastIndex) DshDivider()
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "内置插件即 Cordis 的 bundle：卸载会自动回滚它注册过的副作用，不留残留工具。" +
                            "dsh --profile web --dump-config 可看实际启动的插件树。",
                        style = Type.meta,
                        color = c.ink3,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            DshBtn(
                text = "切到 dsh-market 社区市场版",
                icon = DshIcons.SwapHoriz,
                tone = BtnTone.Ghost,
                onClick = onSwitchToMarket,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ============================ S20 内置插件 · 单个配置 ============================

/**
 * S20 单个内置插件配置。
 *
 * 设计稿布局：说明卡 13%(能力 Consumer + 依赖标签) ｜ 设置 4 行 22% ｜
 *   覆盖优先级 14%(4 级编号) ｜ provider 替换 15% ｜ 双按钮 + 返回 9%
 *   "provider 切换卡带「整产品行为变更」警示标签"
 *
 * 真实字段全部来自 dump-config：包名、启停、config 的键名列表、所属分组。
 * 没有真实值的行（如"最大重试与退避 = 3"）**一律不画**，
 * 改成把这个键名列出来让人自己去配置文件里看 —— 不编数值。
 */
@Composable
fun S20BuiltinPluginConfig(
    pluginId: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onBackToSplit: () -> Unit = {},
    catalog: DshPluginCatalog.Catalog = DshPluginCatalog.Catalog(emptyList(), null, 0L),
) {
    val c = DshTheme.colors
    val plugin = remember(catalog.plugins, pluginId) {
        catalog.plugins.firstOrNull { it.id == pluginId }
    }

    DshScreen(modifier) {
        DshNavBar(
            onBack = onBack,
            title = plugin?.id ?: "插件配置",
            stacked = true,
            actions = {
                DshChip(
                    text = if (plugin?.enabled == true) "已启用" else "已停用",
                    tone = if (plugin?.enabled == true) ChipTone.Ok else ChipTone.Warn,
                )
            },
        )

        if (plugin == null) {
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
            ) {
                DshEmpty("未找到该插件（可能未随当前 profile 启动）", icon = DshIcons.ErrorOutline)
            }
            return@DshScreen
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg)
                .padding(top = 12.dp),
        ) {
            // —— 说明卡 ——
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(14.dp),
            ) {
                Text(plugin.id, style = Type.title, color = c.ink)
                Spacer(Modifier.height(4.dp))
                Text(plugin.pkg, style = Type.mono, color = c.ink3)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DshChip("Cordis bundle", mini = true)
                    DshChip(plugin.scope.substringAfterLast(", ").trim(), mini = true)
                    if (plugin.configKeys.isNotEmpty()) {
                        DshChip("${plugin.configKeys.size} 项配置", tone = ChipTone.On, mini = true)
                    }
                    if (!plugin.enabled) DshChip("已停用", tone = ChipTone.Warn, mini = true)
                }
            }
            Spacer(Modifier.height(12.dp))

            // —— 设置行：只列真实存在的键 ——
            if (plugin.configKeys.isNotEmpty()) {
                PlugCard {
                    plugin.configKeys.forEachIndexed { i, k ->
                        DshMRow(
                            title = k,
                            subtitle = "在 ${plugin.scope.substringBefore(",")} 的配置文件里修改",
                            icon = DshIcons.Tune,
                            iconTone = ChipTone.Neutral,
                            modifier = if (i == plugin.configKeys.lastIndex) Modifier else Modifier,
                        )
                        if (i != plugin.configKeys.lastIndex) DshDivider()
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "本页不提供在线改值：dsh 的配置优先级由 Profile 与 cordis.patch.yml 决定，" +
                        "在手机上改写容易和文件里的值打架，反而查不清问题。",
                    style = Type.meta,
                    color = c.ink3,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            } else {
                PlugNoteCard(
                    icon = DshIcons.Tune,
                    iconTint = c.ink3,
                    title = "该插件无额外配置",
                    subtitle = "启停状态由 Profile 装配决定",
                )
            }
            Spacer(Modifier.height(12.dp))

            // —— 配置覆盖优先级（设计稿固定四条，属于 Cordis 机制而非数据）——
            DshSecTitle("配置覆盖优先级", note = "后者覆盖前者")
            PlugCard {
                Column(Modifier.padding(14.dp)) {
                    listOf(
                        "Profile 中各 bundle 的声明顺序" to false,
                        "cordis.patch.yml（Profile 级）" to false,
                        "cordis.patch.yml（Home 级）" to false,
                        "--patch 命令行覆盖" to true,
                    ).forEachIndexed { i, (text, hot) ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                "${i + 1}",
                                style = Type.sec,
                                color = if (hot) c.brand else c.ink3,
                                modifier = Modifier.width(14.dp),
                            )
                            Text(
                                text,
                                style = Type.sec,
                                color = if (hot) c.brand else c.ink2,
                                lineHeight = 19.sp,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // —— 双按钮 ——
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                DshBtn(
                    text = "查看生效配置",
                    icon = DshIcons.FileCopy,
                    tone = BtnTone.Ghost,
                    small = true,
                    modifier = Modifier.weight(1f),
                    onClick = {},
                )
                DshBtn(
                    text = if (plugin.enabled) "停用并回滚" else "启用",
                    tone = BtnTone.Ghost,
                    small = true,
                    modifier = Modifier.weight(1f),
                    enabled = false,
                    onClick = {},
                )
            }
            Spacer(Modifier.height(9.dp))
            DshBtn("返回插件分流", tone = BtnTone.Ghost, onClick = onBackToSplit)
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ============================ S21 dsh-market · 发现 ============================

/**
 * S21 市场发现。
 *
 * 设计稿布局：搜索卡 6% ｜ 索引源条 9% ｜ 分类横滑 5% ｜ 排序段 + CLI 6% ｜
 *   插件卡 ×4 47% ｜ 生效提示 3% ｜ **市场 tab 栏 52px** ｜ home 4%
 *
 * ⚠️ 本机没装 dshmarket（`node_modules/@deepseek-ai/` 下无 dshmarket），
 * 所以市场列表**没有真实数据源**。按设计稿自己的规则处理：
 * 显示"需先安装市场本体" + 那条 CLI 命令，而不是拿稿子里的示意插件名充数。
 */
@Composable
fun S21MarketDiscover(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenInstalled: () -> Unit = {},
    onOpenThemes: () -> Unit = {},
    /** 真实市场插件；本机为空（未装市场），空时显示安装引导 */
    plugins: List<MarketPlugin> = emptyList(),
    /** 市场本体是否已安装 */
    marketInstalled: Boolean = false,
    /** v20：搜索卡 → S45 全局搜索 */
    onSearch: () -> Unit = {},
) {
    val c = DshTheme.colors
    var cat by remember { mutableStateOf(0) }
    var sort by remember { mutableStateOf(0) }
    val cats = listOf("全部", "UI 增强", "主题与外观", "模型与账号接入", "会话与消息", "记忆", "工具与能力", "技能包", "工作流与自动化")

    DshScreen(modifier, bottomBar = false) {
        DshNavBar(
            onBack = onBack,
            title = "插件市场",
            stacked = true,
            actions = { DshChip("dsh-market", tone = ChipTone.Purple) },
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg)
                .padding(top = 10.dp),
        ) {
            // 搜索卡（设计稿里是静态占位，点它进 S45 全局搜索）
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .clickable { onSearch() }
                    .padding(horizontal = 11.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(DshIcons.Search, null, Modifier.size(16.dp), tint = c.ink3)
                Spacer(Modifier.width(8.dp))
                Text("搜索插件，比如：通知、皮肤", style = Type.chip, color = c.ink3)
            }
            Spacer(Modifier.height(10.dp))

            PlugNoteCard(
                icon = DshIcons.Hub,
                iconTint = c.purple,
                title = if (marketInstalled) "精选列表 awesome-dsh-plugin" else "市场本体尚未安装",
                subtitle = if (marketInstalled)
                    "plugins.json · CI 每日同步 · 内置离线快照备用"
                else
                    "发现 / 主题 / 已安装三个 tab 都要先装市场本体才会出现",
                trailing = {
                    DshChip(
                        text = if (marketInstalled) "今日已同步" else "未安装",
                        tone = if (marketInstalled) ChipTone.Ok else ChipTone.Warn,
                        mini = true,
                    )
                },
            )
            Spacer(Modifier.height(10.dp))

            // 分类横滑
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                cats.forEachIndexed { i, label ->
                    DshChip(label, tone = if (cat == i) ChipTone.On else ChipTone.Neutral, onClick = { cat = i })
                }
            }
            Spacer(Modifier.height(9.dp))

            // 排序段 + CLI
            Row(verticalAlignment = Alignment.CenterVertically) {
                DshSeg(
                    options = listOf("最热", "最新", "最早"),
                    selectedIndex = sort,
                    onSelect = { sort = it },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                DshChip("CLI", icon = DshIcons.Terminal, tone = ChipTone.Warn)
            }
            Spacer(Modifier.height(10.dp))

            if (plugins.isEmpty()) {
                // 安装引导：把设计稿给的那条命令原样交出去
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.xl))
                        .background(c.warnSoft())
                        .padding(14.dp),
                ) {
                    Text("先安装市场本体", style = Type.rowTitle, color = c.ink)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "社区市场是第三方插件，安装前会展示来源仓库、提交号与目录，" +
                            "并默认禁止执行构建脚本。",
                        style = Type.sec,
                        color = c.ink2,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    DshCode(com.dsh.harness.mobile.data.DshMarketProbe.INSTALL_CMD)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "装完回到本页，「发现 / 主题 / 已安装」三个 tab 即生效。",
                        style = Type.meta,
                        color = c.ink3,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    plugins.forEach { p -> MarketCard(p) }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(DshIcons.Info, null, Modifier.size(12.dp), tint = c.ink3)
                    Spacer(Modifier.width(5.dp))
                    Text("多数插件刷新页面即生效，无需重启服务", style = Type.meta, color = c.ink3)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // —— 市场专属底栏（设计稿 .mkt-bar 52px）——
        MktBar(0, onOpenInstalled = onOpenInstalled, onOpenThemes = onOpenThemes)
    }
}

/** S21 的插件卡：名称 / 星标 / 描述 / 作者 + 标签 + 安装按钮。 */
@Composable
private fun MarketCard(p: MarketPlugin) {
    val c = DshTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xl))
            .background(c.card)
            .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
            .clickable { }
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(p.toneBg(c)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(p.icon, null, Modifier.size(15.dp), tint = p.toneFg(c))
            }
            Spacer(Modifier.width(8.dp))
            Text(p.name, style = Type.rowTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            if (p.stars.isNotEmpty()) {
                Icon(DshIcons.Star, null, Modifier.size(12.dp), tint = c.ink3)
                Spacer(Modifier.width(3.dp))
                Text(p.stars, style = Type.meta, color = c.ink3)
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(p.desc, style = Type.sec, color = c.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.author, style = Type.meta, color = c.ink3, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            p.tags.forEach { t ->
                DshChip(t, mini = true)
                Spacer(Modifier.width(5.dp))
            }
            Spacer(Modifier.weight(1f))
            DshChip("安装", tone = ChipTone.Brand)
        }
    }
}

/** 市场插件。数据来自索引源；本机未装市场 → 列表为空。 */
data class MarketPlugin(
    val name: String,
    val author: String,
    val desc: String,
    val stars: String = "",
    val version: String = "",
    val tags: List<String> = emptyList(),
    val icon: ImageVector = DshIcons.Extension,
    val tone: ChipTone = ChipTone.On,
    /** S22 安装确认层要展示的来源信息，全部来自索引文件 */
    val repo: String = "",
    val commit: String = "",
    val dir: String = "",
    val entry: String = "",
    val capabilities: List<String> = emptyList(),
    val inCuratedList: Boolean = false,
    val hasBuildScript: Boolean = false,
    val sameNameConflict: Boolean = false,
) {
    fun toneBg(c: DshColors): Color = when (tone) {
        ChipTone.Purple -> c.purpleSoft()
        ChipTone.Ok -> c.okSoft()
        ChipTone.Warn -> c.warnSoft()
        ChipTone.Danger -> c.dangerSoft()
        else -> c.soft
    }
    fun toneFg(c: DshColors): Color = when (tone) {
        ChipTone.Purple -> c.purple
        ChipTone.Ok -> c.ok
        ChipTone.Warn -> c.warn
        ChipTone.Danger -> c.danger
        else -> c.brand
    }
}

/** 市场底栏。设计稿 .mkt-bar：h52、图标 20 + 文字 10、选中品牌色加粗。 */
@Composable
private fun MktBar(selected: Int, onOpenInstalled: () -> Unit, onOpenThemes: () -> Unit) {
    val c = DshTheme.colors
    Column(Modifier.fillMaxWidth().background(c.tabbg)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .navigationBarsPadding(),
        ) {
            MktTab("发现", DshIcons.Explore, selected == 0, Modifier.weight(1f)) {}
            MktTab("主题", DshIcons.Palette, selected == 1, Modifier.weight(1f), onOpenThemes)
            MktTab("已安装", DshIcons.FileDownload, selected == 2, Modifier.weight(1f), onOpenInstalled)
        }
    }
}

@Composable
private fun RowScope.MktTab(
    label: String,
    icon: ImageVector,
    on: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = DshTheme.colors
    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (on) c.brand else c.ink3)
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = Type.meta.copy(
                fontSize = 10.sp,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (on) c.brand else c.ink3,
        )
    }
}

// ============================ S22 市场详情 + 安装确认 ============================

/**
 * S22 详情与安装确认。
 *
 * 设计稿：背景是详情页（能力 / 兼容 / 同类），前景是**安装前必须过的确认层**：
 * 来源仓库 / 提交 / 目录、精选校验、构建脚本默认禁止、同名冲突、终端类提醒。
 *   "收录 ≠ 背书：插件是第三方代码，只装你信任的来源。"
 *
 * 没有真实插件时（S21 空态）进不来这屏；所以这里不做假的示意数据。
 */
@Composable
fun S22MarketDetail(
    plugin: MarketPlugin,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onConfirmInstall: () -> Unit = {},
) {
    val c = DshTheme.colors
    DshScreen(modifier) {
        DshNavBar(onBack = onBack, title = plugin.name, stacked = true)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg)
                .padding(top = 12.dp),
        ) {
            // 头卡
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(Radius.md))
                            .background(plugin.toneBg(c)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(plugin.icon, null, Modifier.size(22.dp), tint = plugin.toneFg(c))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(plugin.name, style = Type.title, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            listOfNotNull(
                                plugin.author.ifEmpty { null },
                                plugin.stars.ifEmpty { null },
                                plugin.version.ifEmpty { null },
                            ).joinToString(" · "),
                            style = Type.meta,
                            color = c.ink3,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(plugin.desc, style = Type.sec, color = c.ink2, lineHeight = 18.sp)
                if (plugin.tags.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        plugin.tags.forEach { DshChip(it, mini = true) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            if (plugin.capabilities.isNotEmpty()) {
                DshSecTitle("包含能力", note = "${plugin.capabilities.size} 项", modifier = Modifier.padding(top = 0.dp))
                PlugCard {
                    plugin.capabilities.forEachIndexed { i, cap ->
                        DshMRow(
                            title = cap,
                            icon = DshIcons.Check,
                            iconTone = ChipTone.Ok,
                            onClick = {},
                        )
                        if (i != plugin.capabilities.lastIndex) DshDivider()
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            InstallConfirmBody(plugin)

            Spacer(Modifier.height(12.dp))
            DshBtn(
                text = "安装",
                icon = DshIcons.FileDownload,
                onClick = onConfirmInstall,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * 安装前确认层的正文。设计稿把它做成 Sheet，
 * 这里既能被 S22 内嵌，也能被 SheetKind.InstallConfirm 复用。
 */
@Composable
fun InstallConfirmBody(plugin: MarketPlugin, modifier: Modifier = Modifier) {
    val c = DshTheme.colors
    Column(modifier.fillMaxWidth()) {
        DshCode(
            buildString {
                appendLine("仓库  ${plugin.repo.ifEmpty { "—" }}")
                append("提交  ${plugin.commit.ifEmpty { "—" }}")
                if (plugin.dir.isNotEmpty()) appendLine("目录  ${plugin.dir}")
                if (plugin.entry.isNotEmpty()) append("入口  ${plugin.entry}")
            }.trimEnd()
        )
        Spacer(Modifier.height(11.dp))
        PlugCard {
            ConfirmRow(
                ok = plugin.inCuratedList,
                text = if (plugin.inCuratedList) "在 awesome-dsh-plugin 精选清单内" else "不在精选清单内",
                right = if (plugin.inCuratedList) "通过" else "未收录",
            )
            DshDivider()
            ConfirmRow(
                ok = !plugin.sameNameConflict,
                text = if (plugin.sameNameConflict) "存在同名插件冲突" else "无同名插件冲突",
            )
            if (plugin.hasBuildScript) {
                DshDivider()
                ConfirmRow(ok = false, text = "含构建脚本，默认禁止执行")
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Top) {
            Icon(DshIcons.Warning, null, Modifier.size(13.dp).padding(top = 1.dp), tint = c.warn)
            Spacer(Modifier.width(7.dp))
            Text(
                "收录 ≠ 背书：插件是第三方代码，只装你信任的来源。" +
                    "本次会话新装的插件可随后实时移除。",
                style = Type.meta,
                color = c.ink3,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun ConfirmRow(ok: Boolean, text: String, right: String? = null) {
    val c = DshTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (ok) DshIcons.Check else DshIcons.Warning,
            null,
            Modifier.size(15.dp),
            tint = if (ok) c.ok else c.warn,
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = Type.sec, color = if (ok) c.ink else c.warn, modifier = Modifier.weight(1f))
        if (right != null) {
            Text(
                right,
                style = Type.sec.copy(fontWeight = FontWeight.SemiBold),
                color = if (ok) c.ok else c.warn,
            )
        }
    }
}

// ============================ S23 市场已安装与更新 ============================

/**
 * S23 已安装与更新。
 *
 * 设计稿布局：统计条 8%(已装 / 待更新 / 未生效 + 全部更新) ｜ 列表 46% ｜
 *   备份警示卡 8% ｜ 运行环境与脱敏日志 11% ｜ 卸载按钮 7% ｜ 市场底栏 52px
 *   "卸载两步确认"
 *
 * 本机未装市场 → 已安装列表为空，页面显示引导而不是设计稿里那 5 条示意数据。
 */
@Composable
fun S23MarketInstalled(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenDiscover: () -> Unit = {},
    onOpenThemes: () -> Unit = {},
    installed: List<MarketPlugin> = emptyList(),
    marketInstalled: Boolean = false,
    onExportLog: () -> Unit = {},
) {
    val c = DshTheme.colors
    val updatable = installed.count { it.version.isNotEmpty() }

    // 运行环境自检：真实数据（DshEnvProbe），点行展开明细；「同步」按钮重跑
    val appCtx = LocalContext.current.applicationContext
    var envReport by remember { mutableStateOf<DshEnvProbe.Report?>(null) }
    var envOpen by remember { mutableStateOf(false) }
    var envRun by remember { mutableIntStateOf(0) }
    LaunchedEffect(envRun) { envReport = DshEnvProbe.probe(appCtx) }

    DshScreen(modifier, bottomBar = false) {
        DshNavBar(
            onBack = onBack,
            title = "已安装",
            stacked = true,
            actions = { DshIBtn(DshIcons.Sync, "重跑自检", onClick = { envRun++; envOpen = false }) },
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg)
                .padding(top = 10.dp),
        ) {
            // 统计条
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatCell(installed.size.toString(), "已装", c.ink)
                Box(Modifier.width(1.dp).height(24.dp).background(c.line))
                StatCell(updatable.toString(), "待更新", c.warn)
                Box(Modifier.width(1.dp).height(24.dp).background(c.line))
                StatCell("0", "未生效", c.danger)
                Spacer(Modifier.weight(1f))
                DshChip("全部更新", tone = ChipTone.Brand, enabled = installed.isNotEmpty(), onClick = {})
            }
            Spacer(Modifier.height(11.dp))

            if (installed.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.xl))
                        .background(c.card)
                        .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (marketInstalled) "还没有从市场装过插件" else "市场本体尚未安装",
                        style = Type.rowTitle,
                        color = c.ink,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (marketInstalled) "去「发现」看看有什么可装的"
                        else "装完市场本体后，这里会列出每个插件的版本与生效状态",
                        style = Type.meta,
                        color = c.ink3,
                    )
                    Spacer(Modifier.height(12.dp))
                    DshBtn(
                        text = "去发现页",
                        tone = BtnTone.Ghost,
                        small = true,
                        modifier = Modifier.widthIn(max = 180.dp),
                        onClick = onOpenDiscover,
                    )
                }
            } else {
                PlugCard {
                    installed.forEachIndexed { i, p ->
                        DshMRow(
                            title = p.name,
                            subtitle = p.version.ifEmpty { p.desc },
                            icon = p.icon,
                            iconTone = p.tone,
                            trailing = {
                                when {
                                    p.version.isEmpty() -> DshChip("已启用", tone = ChipTone.Ok, mini = true)
                                    p.tone == ChipTone.Warn -> DshChip("有更新", tone = ChipTone.Warn, mini = true)
                                    else -> DshChip("已是最新", tone = ChipTone.Ok, mini = true)
                                }
                            },
                        )
                        if (i != installed.lastIndex) DshDivider()
                    }
                }
            }
            Spacer(Modifier.height(11.dp))

            // 备份警示：0 已装时没有"全部更新"这个动作，警示无意义 → 不显示
            if (installed.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.xl))
                        .background(c.warnSoft())
                        .border(1.dp, c.warn.copy(alpha = 0.28f), RoundedCornerShape(Radius.xl))
                        .padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(DshIcons.Warning, null, Modifier.size(18.dp), tint = c.warn)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "全部更新前建议先备份配置目录：新版本可能改配置格式，导致启动时解析失败。",
                        style = Type.meta,
                        color = c.ink2,
                        lineHeight = 17.sp,
                    )
                }
                Spacer(Modifier.height(11.dp))
            }

            PlugCard {
                // 自检数据是真跑出来的（DshEnvProbe）：node / dsh 主程序 / home / API 密钥
                val report = envReport
                DshMRow(
                    title = "运行环境自检",
                    subtitle = "node · dsh 主程序 · API 密钥 · 本机真实检测",
                    icon = DshIcons.Terminal,
                    iconTone = ChipTone.Neutral,
                    trailing = {
                        when {
                            report == null -> DshChip("检测中", tone = ChipTone.Neutral, mini = true)
                            report.allOk -> DshChip("正常", tone = ChipTone.Ok, mini = true)
                            else -> DshChip("${report.missing} 项待修", tone = ChipTone.Warn, mini = true)
                        }
                    },
                    onClick = { if (report != null) envOpen = !envOpen },
                )
                AnimatedVisibility(visible = envOpen && report != null) {
                    Column(Modifier.padding(top = 2.dp)) {
                        report!!.checks.forEachIndexed { i, chk ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 13.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    if (chk.ok) DshIcons.CheckCircle else DshIcons.Warning,
                                    null,
                                    Modifier.size(15.dp),
                                    tint = if (chk.ok) c.ok else c.warn,
                                )
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(chk.name, style = Type.rowTitle, color = c.ink)
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        chk.detail,
                                        style = Type.meta.copy(fontSize = 10.5.sp),
                                        color = c.ink3,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (!chk.ok) {
                                    Spacer(Modifier.width(8.dp))
                                    DshChip("缺失", tone = ChipTone.Warn, mini = true)
                                }
                            }
                            if (i != report.checks.lastIndex) DshDivider()
                        }
                    }
                }
                DshDivider()
                DshMRow(
                    title = "导出脱敏日志",
                    subtitle = "home 路径与密钥形状打码 · 不主动上传",
                    icon = DshIcons.FileCopy,
                    iconTone = ChipTone.Neutral,
                    chevron = true,
                    onClick = onExportLog,
                )
            }
            Spacer(Modifier.height(12.dp))
            DshBtn(
                text = "卸载（两步确认）",
                icon = DshIcons.DeleteOutline,
                tone = BtnTone.Ghost,
                enabled = installed.isNotEmpty(),
                onClick = {},
            )
            Spacer(Modifier.height(12.dp))
        }

        MktBar(2, onOpenInstalled = {}, onOpenThemes = onOpenThemes)
    }
}

@Composable
private fun StatCell(num: String, label: String, color: Color) {
    val c = DshTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(58.dp)) {
        Text(num, style = Type.h1.copy(fontSize = 17.sp), color = color)
        Text(label, style = Type.meta.copy(fontSize = 9.5.sp), color = c.ink3)
    }
}

// ============================ S22 安装确认 Sheet ============================

/**
 * 安装确认 Sheet。设计稿里它是盖在 S22 之上的 mask+sheet，
 * 标题「确认来源后安装」带警示图标，底部取消 / 确认安装 + 单独授权构建脚本。
 */
@Composable
fun BoxScope.S22InstallConfirmSheet(
    plugin: MarketPlugin,
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    DshSheet(
        visible = visible,
        onDismiss = onDismiss,
        maxHeightRatio = 0.84f,
        header = {
            DshSheetHeader(
                title = "确认来源后安装",
                icon = DshIcons.VerifiedUser,
                iconTint = DshTheme.colors.warn,
            )
        },
        content = {
            InstallConfirmBody(plugin)
            Spacer(Modifier.height(4.dp))
        },
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                DshBtn(
                    text = "取消",
                    tone = BtnTone.Ghost,
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                )
                DshBtn(
                    text = "确认安装",
                    modifier = Modifier.weight(1.5f),
                    onClick = { onConfirm(); onDismiss() },
                )
            }
            if (plugin.hasBuildScript) {
                Spacer(Modifier.height(9.dp))
                DshBtn(
                    text = "单独授权构建脚本",
                    tone = BtnTone.Ghost,
                    onClick = {},
                )
            }
        },
    )
}