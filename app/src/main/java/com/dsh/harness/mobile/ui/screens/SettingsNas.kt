package com.dsh.harness.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.harness.mobile.ui.components.*
import com.dsh.harness.mobile.ui.theme.*

// ============================ S14 设置 ============================

/**
 * S14 设置（v20 重构）
 *
 * v20 设计稿结构（原 3291 行稿 1749–1788 行）：
 *   通用 2 行 ｜ **模型 5 行**（DeepSeek 账号→S31 / 账号与余额→S32·S15 /
 *   默认模型与思考强度→S30 / API 密钥→S37·S24 / 自定义提供方→S25）｜
 *   Agent 预设 1 行→S33 ｜ 开发者工具（三合一）｜ 存储与同步 NAS→S34·S16 ｜
 *   顶栏搜索→S45
 *
 * 与交付版的差异（都遵循诚实性原则）：
 *  1. 通用组保留原 3 行 —— v20 把「展示档位 + 繁忙 Enter」合并成一行
 *     「工作过程展示」，但合并会弄丢可用的分段控件与开关，且设计稿的
 *     S14b 子页并不存在，故不做纯装饰性合并；
 *  2. 账号/NAS 两行的副标题与 chip 按**真实登录态/连接态**渲染，
 *     不显示设计稿的演示数值（「2 个提供方」「1 个已连」）；
 *  3. 「本机服务 · Termux」行按用户明确要求保留在开发者工具组。
 */
@Composable
fun S14Settings(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    dark: Boolean = false,
    onToggleDark: (Boolean) -> Unit,
    /** 是否已登录 DeepSeek 账号。无真实登录态，默认 false → 行走诚实空态 */
    /** v27：改名 —— 这里传的是真实的「是否已配置 API Key」，不再是虚构的登录态 */
    keyConfigured: Boolean = false,
    /** NAS 是否已连接。默认 false → S34 空态 */
    nasConnected: Boolean = false,
    /** DeepSeek 账号行：未登录 → S31 登录；已登录 → S15 账号与余额 */
    onAccount: () -> Unit,
    /** 账号与余额行：未登录 → S32；已登录 → S15 */
    onBalance: () -> Unit,
    /** 密钥是否已配置（只读存在性）。S14「模型」组的密钥状态点用它。 */
    apiKeyConfigured: Boolean = false,
    /** API 密钥行：未配置 → S24 首次配置；已配置 → S37 已配置态 */
    onApiKey: () -> Unit,
    /** 默认模型与思考强度 → S30 */
    onModelDefault: () -> Unit,
    /** 自定义提供方 → S25 新建提供方表单 */
    onAddProvider: () -> Unit,
    /** Agent 预设 → S33 */
    onPresets: () -> Unit,
    /** NAS 连接行：未连接 → S34；已连接 → S16 */
    onNas: () -> Unit,
    /** v20：顶栏搜索 → S45 全局搜索 */
    onSearch: () -> Unit = {},
    /** v14：本机 Termux 终端入口（用户明确要求保留在设置列表里） */
    onTermux: () -> Unit = {},
) {
    val c = DshTheme.colors
    var density by remember { mutableStateOf(1) }
    var busyEnter by remember { mutableStateOf(false) }
    var devtools by remember { mutableStateOf(true) }

    DshScreen(modifier) {
        DshNavBar(
            onBack = onBack,
            title = "设置",
            stacked = true,
            actions = { DshIBtn(DshIcons.Search, "搜索", onClick = onSearch) },
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            DshSecTitle("通用", Modifier.padding(top = 12.dp))
            SettingsGroup {
                SettingRow("语言与外观", "简体中文 · 跟随系统深浅") {
                    DshSwitch(dark) { onToggleDark(it) }
                }
                DshDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("展示档位", style = Type.rowTitle, color = c.ink, modifier = Modifier.weight(1f))
                    DshSeg(
                        listOf("聚焦", "关键", "完整"),
                        density,
                        Modifier.width(150.dp),
                    ) { density = it }
                }
                DshDivider()
                SettingRow("繁忙时 Enter", if (busyEnter) "排队发送" else "直接打断当前任务") {
                    DshSwitch(busyEnter) { busyEnter = it }
                }
            }

            DshSecTitle("模型", note = "密钥只写不回读")
            SettingsGroup {
                // 1. DeepSeek 凭据 → S31
                // v27：上游没有账号登录（见 S31 实证说明），这里只反映真实密钥状态，
                // 不再编"账号额度"这种上游不存在的计费方式。
                SettingRow(
                    title = "DeepSeek 凭据",
                    subtitle = if (keyConfigured) "已配置 API Key · 可直接跑任务"
                    else "未配置 · 点此到平台申请并填入",
                    icon = DshIcons.Bolt,
                    statusDot = if (keyConfigured) StatusDot.Ok else StatusDot.Warn,
                    onClick = onAccount,
                ) {
                    DshChip(
                        if (keyConfigured) "已配置" else "去配置",
                        tone = if (keyConfigured) ChipTone.Ok else ChipTone.Warn,
                        mini = true,
                    )
                }
                DshDivider()
                // 2. 余额与用量 → S32（未配置）/ S15（已配置）
                SettingRow(
                    title = "余额与用量",
                    subtitle = if (keyConfigured) "在 platform.deepseek.com 查看"
                    else "需先配置 API Key 才能查询",
                    icon = DshIcons.AccountBalanceWallet,
                    onClick = onBalance,
                ) {
                    DshChip(
                        if (keyConfigured) "已连接" else "未配置",
                        tone = if (keyConfigured) ChipTone.Ok else ChipTone.Warn,
                        mini = true,
                    )
                }
                DshDivider()
                // 3. 默认模型与思考强度 → S30
                SettingRow(
                    title = "默认模型与思考强度",
                    subtitle = "DeepSeek-V4-Flash · 高",
                    icon = DshIcons.AutoFixHigh,
                    onClick = onModelDefault,
                ) {
                    DshChip("更改", tone = ChipTone.On, mini = true)
                }
                DshDivider()
                // 4. API 密钥 → S37（已配置）/ S24（未配置）
                SettingRow(
                    title = "API 密钥",
                    subtitle = if (apiKeyConfigured)
                        "deepseek-official · 已配置 · 只写不回读"
                    else
                        "deepseek-official · 未配置",
                    icon = DshIcons.VpnKey,
                    statusDot = if (apiKeyConfigured) StatusDot.Ok else StatusDot.Warn,
                    onClick = onApiKey,
                ) {
                    DshChip(
                        if (apiKeyConfigured) "已配置" else "未配置",
                        tone = if (apiKeyConfigured) ChipTone.Ok else ChipTone.Warn,
                        mini = true,
                    )
                }
                DshDivider()
                // 5. 自定义提供方 → S25（真实提供方数为 0，不显示设计稿的「2 个」）
                SettingRow(
                    title = "自定义提供方",
                    subtitle = "尚未添加 · 手动录入需声明推理等级",
                    icon = DshIcons.VpnKey,
                    statusDot = StatusDot.Warn,
                    onClick = onAddProvider,
                ) {
                    DshChip("去添加", tone = ChipTone.Warn, mini = true)
                }
            }

            DshSecTitle("Agent 预设", note = "1 个")
            SettingsGroup {
                SettingRow(
                    title = "预设 · 标准模式",
                    subtitle = "另有 PTC / 极简 / 创造 · 运行后锁定不可切换",
                    icon = DshIcons.Build,
                    onClick = onPresets,
                ) {
                    DshChip("当前", tone = ChipTone.Brand, mini = true)
                }
            }

            DshSecTitle("开发者工具")
            SettingsGroup {
                // v20 设计稿：开发者工具（三合一）单行 + 开关
                SettingRow("开发者工具（三合一）", "会话原始日志 · 双向定位 · 内嵌 Host 调试") {
                    DshSwitch(devtools) { devtools = it }
                }
                DshDivider()
                // 用户明确要求保留：本机服务 · Termux
                SettingRow(
                    title = "本机服务 · Termux",
                    subtitle = "在 Termux 里拉起 dsh web",
                    icon = DshIcons.Terminal,
                    onClick = onTermux,
                ) {
                    DshChip(":3081", tone = ChipTone.Ok, mini = true)
                }
            }

            DshSecTitle("存储与同步")
            SettingsGroup {
                SettingRow(
                    title = "NAS 连接",
                    subtitle = if (nasConnected)
                        "群晖 DS920+ · WebDAV · 可挂载为工作区来源"
                    else
                        "未连接",
                    icon = DshIcons.Dns,
                    onClick = onNas,
                ) {
                    DshChip(
                        if (nasConnected) "1 个已连" else "未连接",
                        tone = if (nasConnected) ChipTone.Ok else ChipTone.Warn,
                        mini = true,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "DeepSeek Harness · v0.2.1-alpha.4\n网页版与桌面端同源",
                style = Type.meta.copy(lineHeight = 17.sp),
                color = c.ink3,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    val c = DshTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xl))
            .background(c.card)
            .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
        content = content,
    )
}

/** 标题右侧的状态点。设计稿用 6px 圆点，null = 不显示。 */
enum class StatusDot { Ok, Warn }

/**
 * 设置行。
 *
 * ⚠️ v12 真机踩坑：原来签名是 (title, subtitle, icon, trailing)，
 * 调用处写成 `SettingRow("NAS 连接", ...) { onNas() }` —— 那个尾 lambda
 * 被当成 **trailing 插槽**（渲染在行最右侧的 0 宽度区域），整行其实不可点。
 * 真机上触摸落到相邻的"账号与余额"行上 → 表现为"点设置跳到账号页"。
 * 正解：**onClick 独立成参数**并绑到整行；trailing 只放视觉元素。
 *
 * ⚠️ 加参数必须放在 trailing **之前**并全用命名参数调用 ——
 * 否则老调用点的尾 lambda 会静默改语义（v13 又踩过一次）。
 */
@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    icon: ImageVector? = null,
    statusDot: StatusDot? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val c = DshTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(17.dp), tint = c.ink3)
            Spacer(Modifier.width(9.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = Type.rowTitle, color = c.ink, maxLines = 1)
                if (statusDot != null) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.size(6.dp).background(
                            if (statusDot == StatusDot.Ok) c.ok else c.warn,
                            CircleShape,
                        )
                    )
                }
            }
            Text(subtitle, style = Type.meta, color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}

// ============================ S15 账号与余额 ============================

/**
 * S15 账号与余额
 * 布局：账号卡 11% ｜ 余额卡 20%(大数字 32px + 两按钮) ｜ 7 日用量 22%(柱图 62px + 三指标)
 *      ｜ 提供方列表 11% ｜ 添加提供方 7% ｜ 退出登录降为文字链
 *   "余额区用软渐变底与列表区做层级切分"
 */
@Composable
fun S15Account(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    /** 近 7 日用量（0~1）；空表示还没有用量数据 */
    usage: List<Float> = emptyList(),
    /** 余额；null 表示取不到 */
    balance: String? = null,
    /** 已配置的模型提供方 */
    providers: List<ProviderItem> = emptyList(),
    /** v20：充值 → S36 */
    onTopUp: () -> Unit = {},
    /** v20：明细 → S35 用量与账单 */
    onUsage: () -> Unit = {},
    /** v20：退出登录 → S38 二次确认 Sheet（由调用方挂） */
    onLogout: () -> Unit = {},
    /** v20：添加提供方 → S25 新建提供方表单 */
    onAddProvider: () -> Unit = {},
    /** v20：提供方行 → S39 编辑模型提供方 */
    onEditProvider: () -> Unit = {},
) {
    val c = DshTheme.colors

    DshScreen(modifier) {
        DshNavBar(onBack = onBack, title = "账号与余额", stacked = true)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            Spacer(Modifier.height(12.dp))

            // —— 账号卡 ——
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .background(Brush.linearGradient(listOf(c.brand, c.purple)), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "D",
                        style = Type.rowTitle,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("DeepSeek 账号", style = Type.rowTitle, color = c.ink)
                    Text("benson@example.com", style = Type.sec, color = c.ink3, maxLines = 1)
                }
                DshChip("已登录", tone = ChipTone.Ok, mini = true)
            }

            Spacer(Modifier.height(12.dp))

            // —— 余额卡：软渐变底，与列表区做层级切分 ——
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(
                        Brush.linearGradient(
                            listOf(c.brand.copy(alpha = 0.16f), c.purple.copy(alpha = 0.10f))
                        )
                    )
                    .padding(16.dp),
            ) {
                Text("可用余额", style = Type.sec, color = c.ink2)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        balance ?: "--",
                        style = Type.h1.copy(fontSize = 32.sp),
                        color = if (balance != null) c.ink else c.ink3,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DshBtn("充值", small = true, tone = BtnTone.Ghost, modifier = Modifier.weight(1f), onClick = onTopUp)
                    DshBtn("明细", small = true, modifier = Modifier.weight(1f), onClick = onUsage)
                }
            }

            Spacer(Modifier.height(12.dp))

            // —— 7 日用量柱图 ——
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(14.dp),
            ) {
                Text("近 7 日用量", style = Type.secTitle, color = c.ink3)
                Spacer(Modifier.height(12.dp))
                if (usage.isEmpty()) {
                    Text(
                        "还没有用量记录",
                        style = Type.sec,
                        color = c.ink3,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(52.dp))
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(62.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        usage.forEachIndexed { i, v ->
                            Column(
                                Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height((62 * v).dp)
                                        .background(
                                            if (i == usage.lastIndex) c.brand else c.brand.copy(alpha = 0.28f),
                                            RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                                        ),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            DshSecTitle("模型提供方", Modifier.padding(top = 0.dp), note = if (providers.isEmpty()) null else "${providers.size} 个")
            if (providers.isEmpty()) {
                DshEmpty("还没有配置模型提供方", icon = DshIcons.AccountTree)
            } else {
                SettingsGroup {
                    providers.forEachIndexed { i, pv ->
                        SettingRow(pv.name, pv.desc, onClick = onEditProvider) {}
                        if (i != providers.lastIndex) DshDivider()
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            DshBtn("添加提供方", icon = DshIcons.Add, tone = BtnTone.Ghost, onClick = onAddProvider)

            Spacer(Modifier.height(16.dp))
            // v27：没有"登录"这回事，真实的破坏性操作是清除凭据 ——
            // 仍降为文字链避免误触，但文案必须说清它到底删什么
            Text(
                "清除本机 API Key",
                style = Type.chip,
                color = c.danger,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLogout() }
                    .padding(vertical = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** 一个模型提供方 */
data class ProviderItem(
    val name: String,
    val desc: String,
)

@Composable
private fun MiniStat(value: String, label: String, modifier: Modifier = Modifier) {
    val c = DshTheme.colors
    Column(modifier) {
        Text(value, style = Type.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = c.ink)
        Text(label, style = Type.meta.copy(fontSize = 10.sp), color = c.ink3)
    }
}

// ============================ S16 NAS 连接 ============================

/**
 * S16 NAS 连接
 * 布局：设备卡 26%(名称行 + 容量进度条 + 上次同步行 + 两按钮) ｜ 目录映射 22%(3 行)
 *      ｜ 同步边界卡 18%(4 条编号说明) ｜ 添加入口 8% ｜ 内网提示 3%
 *   "设备卡不折叠；「断开」右对齐放在状态行尾部，避免与立即同步误触"
 */
@Composable
fun S16Nas(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    /** 已配置的目录映射；空表示还没配（不再填演示数据） */
    mappings: List<MappingItem> = emptyList(),
) {
    val c = DshTheme.colors

    DshScreen(modifier) {
        DshNavBar(onBack = onBack, title = "NAS 连接", stacked = true)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            Spacer(Modifier.height(12.dp))

            // —— 设备卡 ——
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
                            .size(Metrics.ICON_BOX)
                            .clip(RoundedCornerShape(Radius.md))
                            .background(c.line2),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(DshIcons.Dns, null, Modifier.size(19.dp), tint = c.ink3)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("未连接任何 NAS", style = Type.rowTitle, color = c.ink)
                        Text("添加连接后才能挂载远端目录", style = Type.meta, color = c.ink3)
                    }
                    DshChip("未连接", tone = ChipTone.Neutral, mini = true)
                }

                Spacer(Modifier.height(14.dp))
                DshEmpty("没有可同步的设备", icon = DshIcons.Dns)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DshBtn("添加连接", icon = DshIcons.Add, small = true, modifier = Modifier.weight(1f), onClick = onAdd)
                    DshBtn("设置", small = true, tone = BtnTone.Ghost, modifier = Modifier.weight(1f), onClick = {})
                }
            }

            Spacer(Modifier.height(12.dp))

            // —— 目录映射 3 行 ——
            DshSecTitle("目录映射", Modifier.padding(top = 0.dp), note = "3 条")
            SettingsGroup {
                if (mappings.isEmpty()) {
                    DshEmpty("还没有配置目录映射", icon = DshIcons.Folder)
                } else {
                    mappings.forEachIndexed { i, m ->
                        MappingRow(m.remote, m.local, m.dir, m.tone)
                        if (i != mappings.lastIndex) DshDivider()
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // —— 同步边界卡：4 条编号说明 ——
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.soft)
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(DshIcons.Info, null, Modifier.size(15.dp), tint = c.brand)
                    Spacer(Modifier.width(7.dp))
                    Text("同步边界", style = Type.rowTitle, color = c.ink)
                }
                Spacer(Modifier.height(9.dp))
                listOf(
                    "只同步映射内的路径，映射之外一律不碰",
                    "挂载掉线自动转只读，写入类操作禁用",
                    "删除操作不双向传播，冲突按下方规则处理",
                    "大文件 > 100 MB 跳过，改为生成占位清单",
                ).forEachIndexed { i, line ->
                    Row(Modifier.padding(bottom = if (i == 3) 0.dp else 6.dp)) {
                        Text("${i + 1}.", style = Type.meta, color = c.brand, modifier = Modifier.width(16.dp))
                        Text(
                            line,
                            style = Type.meta.copy(lineHeight = 17.sp),
                            color = c.ink2,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            DshBtn("添加 NAS 连接", icon = DshIcons.Add, tone = BtnTone.Ghost, onClick = onAdd)

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(DshIcons.Wifi, null, Modifier.size(12.dp), tint = c.ink3)
                Spacer(Modifier.width(5.dp))
                Text("仅在同局域网内直连，不经过任何中转服务", style = Type.meta, color = c.ink3)
            }
        }
    }
}

/** 一条 NAS 目录映射 */
data class MappingItem(
    val remote: String,
    val local: String,
    val dir: String,
    val tone: ChipTone,
)

@Composable
private fun MappingRow(remote: String, local: String, dir: String, tone: ChipTone) {
    val c = DshTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(remote, style = Type.mono, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(local, style = Type.meta, color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        DshChip(dir, tone = tone, mini = true)
    }
}

// ============================ S17 添加 NAS 表单 ============================

/**
 * S17 添加 NAS 连接（表单）
 * 布局：连接类型 seg ｜ 地址+端口 ｜ 共享目录+挂载点 ｜ 凭据（账号 + 密码含明文切换）
 *      ｜ 同步方向 ｜ 冲突处理 ｜ 开关组 ｜ 取消+连接并测试
 *   "字段一律两两同排压高度；密码只写不回读"
 */
@Composable
fun S17NasForm(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onTest: () -> Unit,
) {
    val c = DshTheme.colors
    var type by remember { mutableStateOf(0) }
    var dir by remember { mutableStateOf(1) }
    var showPw by remember { mutableStateOf(false) }
    var readOnly by remember { mutableStateOf(true) }
    var asWorkspace by remember { mutableStateOf(true) }

    DshScreen(modifier) {
        DshNavBar(onBack = onBack, title = "添加 NAS 连接", stacked = true)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            Spacer(Modifier.height(12.dp))

            DshSecTitle("连接类型", Modifier.padding(top = 0.dp))
            DshSeg(listOf("WebDAV", "SMB", "NFS"), type) { type = it }

            Spacer(Modifier.height(14.dp))
            DshSecTitle("地址", Modifier.padding(top = 0.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FormField("192.168.1.42", Modifier.weight(1f))
                FormField("5005", Modifier.width(86.dp))
            }

            Spacer(Modifier.height(14.dp))
            DshSecTitle("目录", Modifier.padding(top = 0.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FormField("vault/dsh-sync", Modifier.weight(1f), label = "共享目录")
                FormField("~/dsh/nas", Modifier.weight(1f), label = "挂载点")
            }

            Spacer(Modifier.height(14.dp))
            DshSecTitle("凭据", Modifier.padding(top = 0.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FormField("benson", Modifier.width(108.dp))
                FormField(
                    if (showPw) "deepseek2026" else "••••••••••",
                    Modifier.weight(1f),
                    visual = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                    trailing = {
                        Text(
                            if (showPw) "隐藏" else "明文",
                            style = Type.meta,
                            color = c.brand,
                            modifier = Modifier.clickable { showPw = !showPw },
                        )
                    },
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "密码只写入本地凭据库，不回显、不上云。",
                style = Type.meta.copy(fontSize = 10.sp),
                color = c.ink3,
            )

            Spacer(Modifier.height(14.dp))
            DshSecTitle("同步方向", Modifier.padding(top = 0.dp))
            DshSeg(listOf("双向", "仅上传", "仅下载"), dir) { dir = it }

            Spacer(Modifier.height(14.dp))
            DshSecTitle("冲突处理", Modifier.padding(top = 0.dp))
            DshSeg(listOf("保留双份", "以新为准", "以旧为准"), 0) {}

            Spacer(Modifier.height(14.dp))
            SettingsGroup {
                SettingRow("离线时转只读", "挂载掉线后禁用写入类操作") {
                    DshSwitch(readOnly) { readOnly = it }
                }
                DshDivider()
                SettingRow("挂载为工作区来源", "出现在首页工作区列表里") {
                    DshSwitch(asWorkspace) { asWorkspace = it }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DshBtn("取消", tone = BtnTone.Ghost, modifier = Modifier.weight(1f), onClick = onBack)
                DshBtn("连接并测试", icon = DshIcons.Wifi, modifier = Modifier.weight(1.6f), onClick = onTest)
            }
        }
    }
}

/** 表单字段：品牌描边 + soft 软环（设计稿"输入聚焦用品牌描边 + 3px 软环"） */
@Composable
private fun FormField(
    value: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    visual: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = DshTheme.colors
    Column(modifier) {
        if (label != null) {
            Text(label, style = Type.meta.copy(fontSize = 10.sp), color = c.ink3)
            Spacer(Modifier.height(4.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(c.card)
                .border(1.dp, c.brand.copy(alpha = 0.55f), RoundedCornerShape(Radius.md))
                .padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = {},
                singleLine = true,
                textStyle = Type.body.copy(color = c.ink),
                cursorBrush = SolidColor(c.brand),
                visualTransformation = visual,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                Spacer(Modifier.width(6.dp))
                trailing()
            }
        }
    }
}

// ============================ S18 NAS 离线只读 ============================

/**
 * S18 工作区 = NAS 挂载 · 离线只读（降级态）
 * 布局：导航 6%(胶囊换 nas-mount) ｜ 降级条 7%(琥珀底 + 重连) ｜ Hero 淡显 12%
 *      ｜ 输入卡禁用态 13% ｜ 断连期间仍可 26%(3 行) ｜ 切回本地 7% ｜ 触发规则脚注 6%
 *   "禁用态用透明度 + 灰底 + 锁徽标三重表达，避免「点了没反应」的错觉；
 *    可继续的动作单独成卡，不让用户以为整个产品挂了"
 */
@Composable
fun S18NasOffline(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onReconnect: () -> Unit,
    onFallbackLocal: () -> Unit,
) {
    val c = DshTheme.colors

    DshScreen(modifier) {
        DshNavBar(
            onBack = onBack,
            stacked = true,
            leading = {
                Text("会话", style = Type.title, color = c.ink.copy(alpha = 0.5f))
                Spacer(Modifier.width(8.dp))
                DshWsPill("nas-mount", icon = DshIcons.CloudOff)
            },
            actions = { DshIBtn(DshIcons.MoreVert, "更多", onClick = {}) },
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            Spacer(Modifier.height(10.dp))

            // —— 降级条：琥珀底 ——
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.warnSoft())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(DshIcons.CloudOff, null, Modifier.size(16.dp), tint = c.warn)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("NAS 已掉线 · 只读模式", style = Type.rowTitle, color = c.warn)
                    Text("最后同步 14 分钟前", style = Type.meta.copy(fontSize = 10.sp), color = c.ink3)
                }
                Text(
                    "重连",
                    style = Type.chip,
                    color = c.warn,
                    modifier = Modifier.clickable { onReconnect() },
                )
            }

            Spacer(Modifier.height(18.dp))

            // —— Hero 淡显（对应 S3 布局位置）——
            Text(
                "探索未至之境",
                style = Type.hero,
                color = c.ink.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            Spacer(Modifier.height(18.dp))

            // —— 输入卡禁用态：透明度 + 灰底 + 锁徽标 三重表达 ——
            Column(
                Modifier
                    .fillMaxWidth()
                    .alpha(0.55f)
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.line2)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(DshIcons.Lock, null, Modifier.size(15.dp), tint = c.ink3)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "工作区离线，无法创建新任务",
                        style = Type.input.copy(fontSize = 14.sp),
                        color = c.ink3,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DshIBtn(DshIcons.AddCircleOutline, "添加", enabled = false, onClick = {})
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(c.line),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(DshIcons.Lock, null, Modifier.size(16.dp), tint = c.ink3)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // —— 断连期间仍可：单独成卡，不让用户以为整个产品挂了 ——
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(14.dp),
            ) {
                Text("断连期间仍可", style = Type.secTitle, color = c.ink3)
                Spacer(Modifier.height(9.dp))
                OfflineRow("查看已缓存的会话与产物", DshIcons.Visibility) {}
                Spacer(Modifier.height(8.dp))
                OfflineRow("导出本地副本", DshIcons.FileDownload) {}
                Spacer(Modifier.height(8.dp))
                OfflineRow("切回本地工作区继续", DshIcons.HomeWork, highlight = true, onClick = onFallbackLocal)
            }

            Spacer(Modifier.height(12.dp))
            DshBtn("切回本地工作区继续", tone = BtnTone.Ghost, onClick = onFallbackLocal)

            Spacer(Modifier.height(14.dp))
            Text(
                "触发规则：连续 3 次心跳超时，或挂载进程退出。恢复连接后会自动回到可写状态，" +
                    "断连期间的写操作不会补执行。",
                style = Type.meta.copy(lineHeight = 17.6.sp),
                color = c.ink3,
            )
        }
    }
}

@Composable
private fun OfflineRow(
    text: String,
    icon: ImageVector,
    highlight: Boolean = false,
    onClick: () -> Unit,
) {
    val c = DshTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon, null,
            Modifier.size(16.dp),
            tint = if (highlight) c.brand else c.ink3,
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text,
            style = Type.chip,
            color = if (highlight) c.brand else c.ink2,
            modifier = Modifier.weight(1f),
        )
        Icon(DshIcons.ChevronRight, null, Modifier.size(16.dp), tint = c.ink3)
    }
}