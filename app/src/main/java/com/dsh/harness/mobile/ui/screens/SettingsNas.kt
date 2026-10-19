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
import androidx.compose.ui.platform.LocalContext
import com.dsh.harness.SafConfig
import com.dsh.harness.DshSync
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
    /** v20：顶栏搜索 → S45 全局搜索 */
    onSearch: () -> Unit = {},
    /** v14：本机 Termux 终端入口（用户明确要求保留在设置列表里） */
    onTermux: () -> Unit = {},
    /** v30：SAF 存储目录桥入口（外部存储，与 NAS 是两回事） */
    onStorage: () -> Unit = {},
    /** v30.2：NAS 连接入口（飞牛 fnOS WebDAV 同步，独立能力） */
    onNas: () -> Unit = {},
) {
    val c = DshTheme.colors
    // v30：真实读取 SAF 授权态，副标题随真实状态变化（不编"已连"假数值）
    val ctx = LocalContext.current
    val safOn = remember { SafConfig.isConfigured(ctx) }
    val safName = remember { SafConfig.displayName(ctx) }
    val nasOn = remember { DshSync.isConfigured(ctx) }
    val nasUrl = remember { DshSync.url(ctx) }
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

            DshSecTitle("外部存储 (SAF)")
            SettingsGroup {
                // v30：SAF 存储目录桥 —— 用户授权一个本地目录给 dsh 透明读写（DocumentsProvider）。
                // 这是「外部存储」，与下面的「NAS 同步」是两套独立能力
                // （用户原话："nas是nas，saf桥是saf桥"）。副标题反映真实授权态，不编假数值。
                SettingRow(
                    title = "存储目录",
                    subtitle = if (safOn) "已挂载：$safName" else "未选择外部存储 · 点此授权",
                    icon = DshIcons.FolderOpen,
                    statusDot = if (safOn) StatusDot.Ok else StatusDot.Warn,
                    onClick = onStorage,
                ) {
                    DshChip(
                        if (safOn) "已挂载" else "去授权",
                        tone = if (safOn) ChipTone.Ok else ChipTone.Warn,
                        mini = true,
                    )
                }
            }

            DshSecTitle("NAS 同步", note = "飞牛 NAS · WebDAV")
            SettingsGroup {
                // v30.2：真 NAS 能力入口（DshSync + DshWebDavClient，飞牛 fnOS WebDAV）。
                // 与 SAF 桥完全分开；副标题反映真实配置态，不编"已连"假数值。
                SettingRow(
                    title = "NAS 连接",
                    subtitle = if (nasOn) "已配置：$nasUrl" else "未配置 · 点此连接飞牛 NAS",
                    icon = DshIcons.Storage,
                    statusDot = if (nasOn) StatusDot.Ok else StatusDot.Warn,
                    onClick = onNas,
                ) {
                    DshChip(
                        if (nasOn) "已连接" else "去连接",
                        tone = if (nasOn) ChipTone.Ok else ChipTone.Warn,
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
                    Text("DeepSeek 凭据", style = Type.rowTitle, color = c.ink)
                    Text("已配置 API Key · 可直接跑任务", style = Type.sec, color = c.ink3, maxLines = 1)
                }
                DshChip("已配置", tone = ChipTone.Ok, mini = true)
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
                Text("计费方式", style = Type.sec, color = c.ink2)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "API Key 计费",
                        style = Type.h1.copy(fontSize = 26.sp),
                        color = c.ink,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "本版本无应用内余额，按实际调用计费；额度与扣费在 platform.deepseek.com 查看",
                    style = Type.meta,
                    color = c.ink3,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
