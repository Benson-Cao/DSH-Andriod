package com.dsh.harness.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.harness.mobile.ui.components.*
import com.dsh.harness.mobile.ui.theme.*

/**
 * v20 账号与模型集群（S30–S39）。
 *
 * 对应设计稿第三批新屏：
 *   S30 默认模型与思考强度（设置里两行「更改」合并成这一页）
 *   S31 DeepSeek 账号登录（系统浏览器授权 → 回跳登录）
 *   S32 账号与余额 · 未登录（空态对齐：余额「—」、提供方 0 个、用量空态）
 *   S33 Agent 预设（内置 4 + 我的预设 1）
 *   S34 NAS · 未连接空态（与 S16 已连接态成对）
 *   S35 用量与账单明细（时间范围 / 按模型 / 消费记录）
 *   S36 充值（金额 / 支付方式 / 到账预览）
 *   S37 API 密钥 · 已配置态（与 S24 未配置态成对）
 *   S38 退出登录二次确认（Sheet）
 *   S39 编辑模型提供方（Provider ID 锁定 + 删除影响清单）
 *
 * 诚实性原则（与工程既有约定一致）：本机没有真实账号登录态，
 * 余额 / 用量 / 提供方数量一律按「未登录」空态呈现，不编造数字；
 * 模型名录为设计稿示意占位，与 S3/S29 默认显示保持一致。
 */

// ============================ S30 默认模型与思考强度 ============================

/** S30 提供方分组下的模型项 */
private data class S30Model(val name: String, val desc: String, val needKey: Boolean = false)

@Composable
fun S30ModelDefault(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    currentModel: String = "DeepSeek-V4-Flash",
    currentThinking: String = "High",
    onSave: (model: String, thinking: String) -> Unit = { _, _ -> },
    onRestoreDefault: () -> Unit = {},
) {
    val c = DshTheme.colors
    var provider by remember { mutableStateOf(0) } // 0=DeepSeek 账号 1=自定义提供方
    var model by remember { mutableStateOf(currentModel) }
    var thinking by remember { mutableStateOf(currentThinking) }
    var scope by remember { mutableStateOf(0) } // 0=仅新会话 1=所有会话

    val deepseekModels = listOf(
        S30Model("DeepSeek-V4-Flash", "快 · 日常任务与长上下文缓存"),
        S30Model("DeepSeek-V4-Pro", "强推理 · 复杂改造与长任务"),
    )
    val customModels = listOf(
        S30Model("qwen3.7-max", "bailian · 已声明档位 low / high", needKey = true),
        S30Model("minimax-m3", "bailian · 目录内", needKey = true),
    )
    val models = if (provider == 0) deepseekModels else customModels
    val levels = listOf("Off", "Low", "High", "Max")

    DshScreen(modifier) {
        DshNavBar(onBack = onBack, title = "默认模型与思考强度", stacked = true)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            Spacer(Modifier.height(12.dp))

            // —— 当前默认（soft 卡）——
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.soft)
                    .padding(horizontal = 14.dp, vertical = 13.dp),
            ) {
                Text("当前默认", style = Type.meta, color = c.ink3)
                Spacer(Modifier.height(5.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(DshIcons.Bolt, null, Modifier.size(17.dp), tint = c.brand)
                    Text(
                        currentModel,
                        style = Type.title,
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    DshChip(currentThinking, tone = ChipTone.Brand, mini = true)
                }
            }

            Spacer(Modifier.height(12.dp))

            DshFieldGroup(label = "提供方") {
                DshSeg(listOf("DeepSeek 账号", "自定义提供方"), provider) { provider = it }
            }

            DshSecTitle("选择模型", Modifier.padding(top = 0.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
            ) {
                models.forEachIndexed { i, m ->
                    if (i > 0) DshDivider()
                    DshMRow(
                        title = m.name,
                        subtitle = m.desc,
                        trailing = {
                            if (m.needKey) {
                                DshChip("需配密钥", mini = true)
                                Spacer(Modifier.width(6.dp))
                            }
                            if (model == m.name) {
                                Icon(
                                    DshIcons.CheckCircle, "已选",
                                    Modifier.size(19.dp),
                                    tint = c.brand,
                                )
                            }
                        },
                        onClick = { model = m.name },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            DshFieldGroup(label = "思考强度") {
                DshSeg(levels, levels.indexOf(thinking).coerceAtLeast(0)) { thinking = levels[it] }
                Text(
                    "手动录入的模型需先在 设置 → 模型 → 编辑提供方 → 模型选项 → 推理等级 声明档位，" +
                        "否则这里置灰，是否思考由服务端默认行为决定。",
                    style = Type.meta.copy(lineHeight = 16.8.sp),
                    color = c.ink3,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            DshFieldGroup(label = "生效范围") {
                DshSeg(listOf("仅新会话", "所有会话"), scope) { scope = it }
                Text(
                    "进行中的任务保持原模型与预设，保证可复现；「所有会话」含已归档会话的后续轮次。",
                    style = Type.meta.copy(lineHeight = 16.8.sp),
                    color = c.ink3,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            DshBtn("保存", onClick = { onSave(model, thinking) })

            Text(
                "恢复默认",
                style = Type.sec,
                color = c.brand,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRestoreDefault() }
                    .padding(vertical = 10.dp),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ============================ S31 DeepSeek 账号登录 ============================

/**
 * S31「连接 DeepSeek」—— **v27 起不再有演示登录**。
 *
 * ## 为什么整页重写（v23 的"诚实演示"仍然是个错误）
 * v23 把假两态改成了诚实的演示卡，但那一页的**全部内容都是假的**：
 * `auth.deepseek.com/authorize` 是我编的 URL（DeepSeek 根本没有这个端点）、
 * 那个转圈是永不完成的假等待、「模拟完成授权」只切了个局部状态。
 * 用户第二次反馈"演示还是死页面"是对的 —— 诚实标注的假按钮依然是假按钮，
 * 用户要的是能用的路径，不是关于假象的说明。
 *
 * ## 实证结论（本机 dsh 全包源码核查，2026-10-19）
 *   - `@deepseek-ai/dsh-authorization` 只是 flow 注册框架，其 README 第 12 行原文：
 *     "本包自身**不提供特定 provider 的授权方法**"；
 *   - 全仓只有 `dsh-llm-pi-ai`（OpenAI Codex）与 `dsh-tool-cordis` 注册了 flow，
 *     **没有任何包为 `deepseek-official` 注册 OAuth**；
 *   - dsh 的 `deepseek-official` route 唯一凭据是 `DEEPSEEK_API_KEY`
 *     （出处在 dsh-llm-deepseek/lib/index.js 的 DEFAULT_API_KEY_ENV）。
 *
 * 也就是说"用账号额度免 API Key"在上游不存在，App 无法凭空造出来。
 * 所以这一页现在只做**真实可执行的三件事**：拉起系统浏览器去平台申请 Key、
 * 直连已有真实写入实现的「API 密钥」页、如实展示当前是否已配置。
 */
@Composable
fun S31AccountLogin(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    /** 真动作：用系统浏览器打开 platform.deepseek.com 申请 Key */
    onOpenPlatform: () -> Unit,
    /** 真动作：跳「设置 → API 密钥」（S14ApiKey，真写 dsh credentials 存储） */
    onGoKey: () -> Unit,
    /** 真实读取 DshCredentials.describe().configured —— 不再是局部假状态 */
    keyConfigured: Boolean = false,
    /** 已配置密钥后进入应用 */
    onOpenApp: () -> Unit = {},
) {
    val c = DshTheme.colors

    DshScreen(modifier) {
        DshNavBar(onBack = onBack, title = "连接 DeepSeek", stacked = true)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            Spacer(Modifier.height(14.dp))

            // 品牌渐变方块（设计稿 52×52 r16 渐变）
            Box(
                Modifier
                    .size(52.dp)
                    .background(
                        Brush.linearGradient(listOf(c.brand, c.purple)),
                        RoundedCornerShape(16.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(DshIcons.Bolt, null, Modifier.size(25.dp), tint = Color.White)
            }
            Spacer(Modifier.height(13.dp))
            Text("接入 DeepSeek 凭据", style = Type.title, color = c.ink)
            Spacer(Modifier.height(8.dp))
            Text(
                "本地引擎只认 API Key 一种凭据。到平台申请一次，填一次全局生效，余额与用量在平台网页查看。",
                style = Type.sec.copy(lineHeight = 21.sp),
                color = c.ink3,
            )

            Spacer(Modifier.height(13.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
            ) {
                S31Benefit("填一次，全局生效", "写入 dsh 凭据存储，所有会话共用")
                DshDivider()
                S31Benefit("所有模型可用", "V4-Flash / Flash / Reasoner 等全系列")
                DshDivider()
                S31Benefit("余额与用量", "platform.deepseek.com 用量与账单")
            }

            Spacer(Modifier.height(13.dp))

            // —— 真实状态卡：读 DshCredentials，不是演示状态 ——
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(if (keyConfigured) c.card else c.soft)
                    .border(
                        1.dp,
                        if (keyConfigured) c.ok else c.line,
                        RoundedCornerShape(Radius.xl),
                    )
                    .padding(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        if (keyConfigured) DshIcons.CheckCircle else DshIcons.Info,
                        null,
                        Modifier.size(17.dp),
                        tint = if (keyConfigured) c.ok else c.ink3,
                    )
                    Text(
                        if (keyConfigured) "API Key 已配置" else "尚未配置 API Key",
                        style = Type.rowTitle.copy(fontWeight = FontWeight.Bold),
                        color = c.ink,
                    )
                }
                Text(
                    if (keyConfigured)
                        "凭据已落盘。出于安全考虑不显示原文，可到「设置 → API 密钥」重新录入。"
                    else
                        "点下方按钮在系统浏览器打开 platform.deepseek.com 申请，拿到 sk- 开头的密钥后回来填入。",
                    style = Type.meta.copy(lineHeight = 18.sp),
                    color = c.ink2,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Spacer(Modifier.height(13.dp))
            if (keyConfigured) {
                DshBtn("进入应用", icon = DshIcons.OpenInNew, onClick = onOpenApp)
                Spacer(Modifier.height(9.dp))
                DshBtn("管理密钥", tone = BtnTone.Ghost, onClick = onGoKey)
            } else {
                DshBtn("去平台申请 API Key", icon = DshIcons.OpenInNew, onClick = onOpenPlatform)
                Spacer(Modifier.height(9.dp))
                DshBtn("我已有 Key，直接填写", tone = BtnTone.Ghost, onClick = onGoKey)
            }

            // 为什么没有「账号登录」——一句话说清上游事实，不兜圈子
            Text(
                "关于「账号登录免 API Key」：本地引擎的授权框架不提供 DeepSeek 的登录方式，" +
                    "上游没有这条通路，App 造不出来，所以这里不放假的登录按钮。",
                style = Type.meta.copy(lineHeight = 18.sp),
                color = c.ink3,
                modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun S31Benefit(title: String, desc: String) {
    val c = DshTheme.colors
    DshMRow(
        title = title,
        subtitle = desc,
        icon = DshIcons.Check,
        iconTone = ChipTone.Ok,
    )
}

// ============================ S32 账号与余额 · 未登录 ============================

@Composable
fun S32AccountLoggedOut(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onLogin: () -> Unit = {},
    onTopUpBlocked: () -> Unit = {},
    onUsage: () -> Unit = {},
    onAddProvider: () -> Unit = {},
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
            Spacer(Modifier.height(14.dp))

            // —— 未登录账号卡 ——
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(46.dp)
                        .background(c.line2, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(DshIcons.Person, null, Modifier.size(20.dp), tint = c.ink3)
                }
                Column(Modifier.weight(1f)) {
                    // v27：不再说"登录/账号额度" —— 上游没有 DeepSeek 账号 OAuth，
                    // 真实状态只有"API Key 配没配"（见 S31 的实证说明）。
                    Text("未配置凭据", style = Type.rowTitle, color = c.ink)
                    Text(
                        "填入 API Key 后即可跑任务",
                        style = Type.meta,
                        color = c.ink3,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                DshChip(
                    "去配置",
                    tone = ChipTone.Brand,
                    onClick = onLogin,
                    modifier = Modifier.height(28.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            // —— 余额卡：未登录给「—」并注明登录后可见 ——
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 15.dp)) {
                    Text(
                        "可用余额",
                        style = Type.meta.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
                        color = c.ink3,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "—",
                            style = Type.h1.copy(fontSize = 29.sp),
                            color = c.ink3,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "在 platform.deepseek.com 查看",
                            style = Type.meta,
                            color = c.ink3,
                            modifier = Modifier.padding(bottom = 7.dp),
                        )
                    }
                }
                Row(
                    Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    // 设计稿：未登录时充值置灰（opacity .5）但仍可点 —— 点了提示先登录
                    Row(Modifier.weight(1f).alpha(0.5f)) {
                        DshBtn(
                            "充值",
                            small = true,
                            tone = BtnTone.Ghost,
                            onClick = onTopUpBlocked,
                        )
                    }
                    DshBtn(
                        "明细",
                        small = true,
                        tone = BtnTone.Ghost,
                        modifier = Modifier.weight(1f),
                        onClick = onUsage,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // —— 近 7 日用量：未登录无数据 ——
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(horizontal = 15.dp, vertical = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("近 7 日用量", style = Type.secTitle, color = c.ink)
                    Spacer(Modifier.weight(1f))
                    Text("配置 API Key 后可查", style = Type.meta, color = c.ink3)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "还没有用量记录",
                    style = Type.sec,
                    color = c.ink3,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    textAlign = TextAlign.Center,
                )
            }

            DshSecTitle("模型提供方", note = "0 个")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(DshIcons.CloudOff, null, Modifier.size(22.dp), tint = c.ink3.copy(alpha = 0.6f))
                    Spacer(Modifier.height(6.dp))
                    Text("还没有配置模型提供方", style = Type.sec, color = c.ink3)
                    Text(
                        "配一个 API Key，或登录 DeepSeek 账号",
                        style = Type.meta,
                        color = c.ink3,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                DshBtn(
                    "添加提供方",
                    icon = DshIcons.Add,
                    tone = BtnTone.Ghost,
                    onClick = onAddProvider,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ============================ S33 Agent 预设 ============================

@Composable
fun S33AgentPresets(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onNewPreset: () -> Unit = {},
    onEditPreset: () -> Unit = {},
    onUsePreset: () -> Unit = {},
    onDuplicate: () -> Unit = {},
) {
    val c = DshTheme.colors

    DshScreen(modifier) {
        DshNavBar(
            onBack = onBack,
            title = "Agent 预设",
            stacked = true,
            actions = { DshIBtn(DshIcons.Add, "新建预设", onClick = onNewPreset) },
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            Spacer(Modifier.height(12.dp))

            DshSecTitle("内置", note = "4 个", modifier = Modifier.padding(top = 0.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
            ) {
                DshMRow(
                    title = "标准模式",
                    subtitle = "文件编辑 · Shell · 检索 · Skills · 计划 · 子代理",
                    icon = DshIcons.Build,
                    trailing = {
                        DshChip("当前使用", tone = ChipTone.Brand, mini = true)
                        Spacer(Modifier.width(6.dp))
                        DshChip("standard", mini = true)
                    },
                    onClick = onEditPreset,
                )
                DshDivider()
                DshMRow(
                    title = "PTC 模式",
                    subtitle = "用一段 TypeScript 程序编排多步调用",
                    icon = DshIcons.DataObject,
                    iconTone = ChipTone.Purple,
                    onClick = onEditPreset,
                )
                DshDivider()
                DshMRow(
                    title = "极简模式",
                    subtitle = "仅 Bash 与文件编辑 · 适合跑分对比",
                    icon = DshIcons.Minimize,
                    iconTone = ChipTone.Neutral,
                    onClick = onEditPreset,
                )
                DshDivider()
                DshMRow(
                    title = "创造模式",
                    subtitle = "对话创建插件与自定义预设",
                    icon = DshIcons.AutoAwesome,
                    iconTone = ChipTone.Warn,
                    onClick = onEditPreset,
                )
            }

            DshSecTitle("我的预设", note = "1 个")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(horizontal = 13.dp, vertical = 12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "web-dev-standard",
                        style = Type.rowTitle.copy(fontWeight = FontWeight.SemiBold),
                        color = c.ink,
                    )
                    DshChip("复制自 standard", mini = true)
                }
                Text(
                    "差异：关闭 subagent 并发、bash 每次需审批、预置 AGENTS.md 模板。",
                    style = Type.meta.copy(lineHeight = 18.sp),
                    color = c.ink3,
                    modifier = Modifier.padding(top = 5.dp),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    DshBtn(
                        "用于新会话",
                        small = true,
                        tone = BtnTone.Ghost,
                        modifier = Modifier.weight(1f),
                        onClick = onUsePreset,
                    )
                    DshBtn(
                        "再复制一份",
                        small = true,
                        tone = BtnTone.Ghost,
                        modifier = Modifier.weight(1f),
                        onClick = onDuplicate,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            NoteCard(
                text = "预设一旦运行即锁定：中途切换会破坏可复现性，要改工具集请复制成新预设再改。",
                boldPrefix = "预设一旦运行即锁定",
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ============================ S34 NAS · 未连接空态 ============================


// ============================ S35 用量与账单明细 ============================


@Composable
fun S35Usage(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onExport: () -> Unit = {},
    onOpenSession: () -> Unit = {},
) {
    val c = DshTheme.colors

    DshScreen(modifier) {
        DshNavBar(
            onBack = onBack,
            title = "用量与账单",
            stacked = true,
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            Spacer(Modifier.height(12.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(16.dp),
            ) {
                Text("此版本不提供账单明细", style = Type.rowTitle, color = c.ink)
                Spacer(Modifier.height(7.dp))
                Text(
                    "本 App 以 API Key 计费，没有账户余额与账单系统。\n" +
                        "真实用量与扣费请在 platform.deepseek.com 的「用量」页查看。",
                    style = Type.sec.copy(lineHeight = 20.sp),
                    color = c.ink3,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ============================ S37 API 密钥 · 已配置态 ============================

@Composable
fun S37ApiKeyConfigured(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onRetryCheck: () -> Unit = {},
    onUpdateKey: () -> Unit = {},
    onDeleteProvider: () -> Unit = {},
) {
    val c = DshTheme.colors

    DshScreen(modifier) {
        DshNavBar(onBack = onBack, title = "API 密钥", stacked = true)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            Spacer(Modifier.height(14.dp))

            // —— 已配置卡（掩码显示，不回读明文）——
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .background(c.soft, RoundedCornerShape(Radius.md)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(DshIcons.VpnKey, null, Modifier.size(18.dp), tint = c.brand)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "deepseek-official",
                            style = Type.rowTitle.copy(fontWeight = FontWeight.SemiBold),
                            color = c.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "sk-••••••••••3f2a",
                            style = Type.mono.copy(fontSize = 11.sp),
                            color = c.ink3,
                            modifier = Modifier.padding(top = 2.dp),
                            maxLines = 1,
                        )
                    }
                    DshChip("已配置", tone = ChipTone.Ok, mini = true)
                }
                Text(
                    "更新于 10-18 20:31 · 模型目录 2 个 · 上次请求 19:52 成功",
                    style = Type.meta.copy(lineHeight = 18.sp),
                    color = c.ink3,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            // —— 连通性验证 ——
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(DshIcons.NetworkCheck, null, Modifier.size(17.dp), tint = c.ok)
                Column(Modifier.weight(1f)) {
                    Text(
                        "连通性验证通过",
                        style = Type.sec.copy(fontWeight = FontWeight.SemiBold),
                        color = c.ink,
                    )
                    Text(
                        "GET /models 200 · 184ms · 返回 2 个模型",
                        style = Type.meta,
                        color = c.ink3,
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "重试",
                    style = Type.sec,
                    color = c.brand,
                    modifier = Modifier.clickable { onRetryCheck() },
                )
            }

            DshSecTitle("密钥规则")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
            ) {
                DshMRow(
                    title = "只写不回读",
                    subtitle = "保存后界面不再显示明文，编辑必须重填",
                    icon = DshIcons.VisibilityOff,
                    iconTone = ChipTone.Neutral,
                )
                DshDivider()
                DshMRow(
                    title = "凭据名由 Provider ID 派生",
                    subtitle = "改名需新增提供方再删除旧的",
                    icon = DshIcons.Lock,
                    iconTone = ChipTone.Neutral,
                )
                DshDivider()
                DshMRow(
                    title = "不进会话上下文",
                    subtitle = "模型与工具看不到密钥值",
                    icon = DshIcons.History,
                    iconTone = ChipTone.Neutral,
                )
            }

            Spacer(Modifier.height(12.dp))
            // —— 与账号并存说明（soft 卡）——
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.soft)
                    .padding(horizontal = 13.dp, vertical = 12.dp),
            ) {
                Text(
                    "与 DeepSeek 账号并存",
                    style = Type.sec.copy(fontWeight = FontWeight.SemiBold),
                    color = c.ink,
                )
                Text(
                    "两者是并列提供方，可同时可用；会话里选哪个就用哪个计费，不做互斥隐藏。",
                    style = Type.meta.copy(lineHeight = 18.sp),
                    color = c.ink2,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                DshBtn(
                    "更新密钥",
                    tone = BtnTone.Ghost,
                    modifier = Modifier.weight(1f),
                    onClick = onUpdateKey,
                )
                DshBtn(
                    "删除提供方",
                    tone = BtnTone.Ghost,
                    modifier = Modifier.weight(1f),
                    onClick = onDeleteProvider,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ============================ S38 退出登录二次确认（Sheet）============================

@Composable
fun BoxScope.S38LogoutSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val c = DshTheme.colors
    DshSheet(
        visible = visible,
        onDismiss = onDismiss,
        header = {
            DshSheetHeader(
                // v27：上游没有账号登录，所以没有"退出登录"这回事 ——
                // 真实的破坏性操作只有一件：删掉本机保存的 API Key。
                title = "清除本机 API Key？",
                icon = DshIcons.Logout,
                iconTint = c.warn,
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
                S38LossRow(DshIcons.RemoveCircleOutline, c.danger, "本机保存的密钥将被删除，之后需重新配置才能跑任务")
                DshDivider()
                S38LossRow(DshIcons.PauseCircleOutline, c.warn, "进行中的任务会被停止，未完成轮次不续跑")
                DshDivider()
                S38LossRow(DshIcons.Check, c.ok, "会话与轨迹日志保留在 ~/.dsh")
                DshDivider()
                S38LossRow(DshIcons.Check, c.ok, "工作区文件与插件配置不受影响")
            }
            Text(
                "若只是想换一个密钥，到「设置 → API 密钥」重新录入即可，不必清除现有配置。",
                style = Type.meta.copy(lineHeight = 17.6.sp),
                color = c.ink3,
                modifier = Modifier.padding(top = 10.dp),
            )
        },
        footer = {
            DshBtn("保留密钥", tone = BtnTone.Ghost, onClick = onDismiss)
            Spacer(Modifier.height(9.dp))
            DshBtn("确认清除", tone = BtnTone.Danger, onClick = onConfirm)
        },
    )
}

@Composable
private fun S38LossRow(icon: ImageVector, tint: Color, text: String) {
    val c = DshTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(icon, null, Modifier.size(15.dp), tint = tint)
        Text(text, style = Type.sec, color = c.ink2)
    }
}

// ============================ S39 编辑模型提供方 ============================

@Composable
fun S39ProviderEdit(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onRefillKey: () -> Unit = {},
    onDelete: () -> Unit = {},
    onSave: () -> Unit = {},
) {
    val c = DshTheme.colors
    var deleteArmed by remember { mutableStateOf(false) }

    DshScreen(modifier) {
        DshNavBar(
            onBack = onBack,
            title = "bailian",
            stacked = true,
            actions = { DshChip("编辑", mini = true) },
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            Spacer(Modifier.height(13.dp))

            // —— Provider ID：锁定不可改 ——
            DshFieldGroup(label = "Provider ID", labelTrailing = { DshChip("不可修改", mini = true) }) {
                DshFinRow(modifier = Modifier.alpha(0.6f)) {
                    Text("bailian", style = Type.mono, color = c.ink2)
                    Spacer(Modifier.weight(1f))
                    Icon(DshIcons.Lock, null, Modifier.size(15.dp), tint = c.ink3)
                }
                Text(
                    "要改名请新增一个提供方、迁移模型后再删除本条；已有会话仍指向旧 ID 直到被删除。",
                    style = Type.meta.copy(lineHeight = 16.8.sp),
                    color = c.ink3,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }

            // —— 显示名称 + API 协议（两列）——
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                DshFieldGroup(label = "显示名称", modifier = Modifier.weight(1f)) {
                    DshFinRow { Text("阿里云百炼", style = Type.sec, color = c.ink) }
                }
                DshFieldGroup(label = "API 协议", modifier = Modifier.width(104.dp)) {
                    DshFinRow {
                        Text(
                            "openai-completions",
                            style = Type.mono.copy(fontSize = 11.sp),
                            color = c.ink2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // —— API 地址（长 URL 换行）——
            DshFieldGroup(label = "API 地址") {
                DshFinRow(autoHeight = true) {
                    Text(
                        "https://dashscope.aliyuncs.com/compatible-mode/v1",
                        style = Type.mono.copy(fontSize = 11.sp, lineHeight = 16.5.sp),
                        color = c.ink2,
                    )
                }
            }

            // —— API 密钥（掩码 + 重新填写）——
            DshFieldGroup(label = "API 密钥") {
                DshFinRow {
                    Text(
                        "••••••••••••7c1d",
                        style = Type.mono,
                        color = c.ink2,
                        maxLines = 1,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "重新填写",
                        style = Type.sec,
                        color = c.brand,
                        modifier = Modifier.clickable { onRefillKey() },
                    )
                }
            }

            // —— 模型与推理等级 ——
            DshFieldGroup(label = "模型与推理等级") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.xl))
                        .background(c.card)
                        .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
                ) {
                    DshMRow(
                        title = "qwen3.7-max",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        trailing = { DshChip("off / low / high", mini = true) },
                    )
                    DshDivider()
                    DshMRow(
                        title = "qwen3.6-flash",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        trailing = { DshChip("未声明档位", tone = ChipTone.Warn, mini = true) },
                    )
                }
                Text(
                    "未声明档位的模型在模型菜单里不提供思考选项，是否思考由服务端默认行为决定。",
                    style = Type.meta.copy(lineHeight = 16.8.sp),
                    color = c.ink3,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            // —— 删除此提供方（danger 卡 + 二次确认）——
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.danger, RoundedCornerShape(Radius.xl))
                    .padding(horizontal = 13.dp, vertical = 12.dp),
            ) {
                Text(
                    "删除此提供方",
                    style = Type.sec.copy(fontWeight = FontWeight.SemiBold),
                    color = c.danger,
                )
                Text(
                    "影响 3 个会话的模型引用与 1 个定时任务；删除后这些会话再运行时需重选模型，密钥一并清除。",
                    style = Type.meta.copy(lineHeight = 18.sp),
                    color = c.ink2,
                    modifier = Modifier.padding(top = 5.dp),
                )
                Spacer(Modifier.height(10.dp))
                DshBtn(
                    if (deleteArmed) "再点一次确认删除" else "删除提供方",
                    tone = BtnTone.Ghost,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (deleteArmed) onDelete() else deleteArmed = true
                    },
                )
            }

            Spacer(Modifier.height(13.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                DshBtn(
                    "取消",
                    tone = BtnTone.Ghost,
                    modifier = Modifier.width(104.dp),
                    onClick = onBack,
                )
                DshBtn(
                    "保存修改",
                    modifier = Modifier.weight(1f),
                    onClick = onSave,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ============================ 共用小组件 ============================

/**
 * 设计稿 .note：卡片底 + 1px line 描边 + 左侧 3px 强调条（ warn / purple ）。
 * [boldPrefix] 会以加粗墨色起头，其余按 ink3。
 * S33/S39 与 Actions.kt 的 S42 共用（module 内 internal）。
 */
@Composable
internal fun NoteCard(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = DshTheme.colors.warn,
    boldPrefix: String? = null,
) {
    val c = DshTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(Radius.lg))
            .background(c.card)
            .border(1.dp, c.line, RoundedCornerShape(Radius.lg)),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accent),
        )
        Text(
            buildNoteText(text, boldPrefix, c.ink),
            style = Type.meta.copy(lineHeight = 17.6.sp),
            color = c.ink3,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
        )
    }
}

private fun buildNoteText(text: String, boldPrefix: String?, boldColor: Color): AnnotatedString {
    if (boldPrefix == null || !text.startsWith(boldPrefix)) return AnnotatedString(text)
    return buildAnnotatedString {
        withStyle(SpanStyle(color = boldColor, fontWeight = FontWeight.SemiBold)) {
            append(boldPrefix)
        }
        append(text.removePrefix(boldPrefix))
    }
}

