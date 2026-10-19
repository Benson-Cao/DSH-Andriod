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

@Composable
fun S31AccountLogin(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onCancelLogin: () -> Unit = {},
    onOpenApp: () -> Unit = {},
    onGoKey: () -> Unit = {},
) {
    val c = DshTheme.colors
    // v23 拆两态：等待授权 →（演示授权）→ 登录成功。
    // 修复点：此前两态同屏展示（用户截图实测），看起来"登录成功"却什么都不能用；
    // 且本 App 未接入真实 OAuth，必须明示演示性质，不拿设计稿的假邮箱充数。
    var authorized by remember { mutableStateOf(false) }

    DshScreen(modifier) {
        DshNavBar(onBack = onBack, title = "登录 DeepSeek 账号", stacked = true)

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
            Text("用账号额度直接跑任务", style = Type.title, color = c.ink)
            Spacer(Modifier.height(8.dp))
            Text(
                "登录后模型列表会多出「DeepSeek 账号」一组，不需要单独申请 API Key，联网搜索也不必再配提供方。",
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
                S31Benefit("免 API Key", "消耗账号余额，充值即用")
                DshDivider()
                S31Benefit("联网搜索开箱可用", "不必再配搜索与抓取提供方")
                DshDivider()
                S31Benefit("余额与用量可查", "设置 → 账号与余额")
            }

            Spacer(Modifier.height(13.dp))
            if (!authorized) {
                // —— 态一：等待授权 ——
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.xl))
                        .background(c.soft)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = c.brand,
                        strokeWidth = 2.dp,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "等待你在系统浏览器完成授权",
                            style = Type.sec.copy(fontWeight = FontWeight.SemiBold),
                            color = c.ink,
                        )
                        Text(
                            "auth.deepseek.com/authorize",
                            style = Type.mono.copy(fontSize = 10.5.sp),
                            color = c.ink3,
                            modifier = Modifier.padding(top = 2.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        "取消",
                        style = Type.sec,
                        color = c.danger,
                        modifier = Modifier.clickable { onCancelLogin() },
                    )
                }

                Spacer(Modifier.height(10.dp))
                // 诚实说明：本 App 没有真实 OAuth，别让用户以为真跳了浏览器
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.xl))
                        .background(c.warnSoft())
                        .padding(12.dp),
                ) {
                    Text(
                        "演示登录",
                        style = Type.sec.copy(fontWeight = FontWeight.SemiBold),
                        color = c.ink,
                    )
                    Text(
                        "本 App 未接入 DeepSeek OAuth 授权流程，不会真正打开系统浏览器。" +
                            "点下方按钮仅演示登录后的界面路径；真实跑任务仍需在「设置 → API 密钥」配置 Key。",
                        style = Type.meta.copy(lineHeight = 18.sp),
                        color = c.ink2,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Spacer(Modifier.height(13.dp))
                DshBtn("模拟完成授权（演示）", icon = DshIcons.Bolt, onClick = { authorized = true })
            } else {
                // —— 态二：登录成功（演示路径的终点）——
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.xl))
                        .background(c.card)
                        .border(1.dp, c.ok, RoundedCornerShape(Radius.xl))
                        .padding(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(DshIcons.CheckCircle, null, Modifier.size(17.dp), tint = c.ok)
                        Text(
                            "登录成功",
                            style = Type.rowTitle.copy(fontWeight = FontWeight.Bold),
                            color = c.ink,
                        )
                        DshChip("已回跳", tone = ChipTone.Ok, mini = true)
                    }
                    Text(
                        "你可以关闭此页面，返回应用继续工作。",
                        style = Type.meta.copy(lineHeight = 18.sp),
                        color = c.ink2,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                Spacer(Modifier.height(13.dp))
                DshBtn("打开应用", icon = DshIcons.OpenInNew, onClick = onOpenApp)
            }

            Text(
                "已有 API Key？去配置密钥",
                style = Type.sec,
                color = c.brand,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onGoKey() }
                    .padding(vertical = 11.dp),
                textAlign = TextAlign.Center,
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
                    Text("未登录", style = Type.rowTitle, color = c.ink)
                    Text(
                        "登录后用账号额度跑任务",
                        style = Type.meta,
                        color = c.ink3,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                DshChip(
                    "登录",
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
                            "登录后可见",
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
                    Text("未登录无数据", style = Type.meta, color = c.ink3)
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

@Composable
fun S34NasEmpty(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onConnect: () -> Unit = {},
) {
    val c = DshTheme.colors
    var protocol by remember { mutableStateOf(0) }

    DshScreen(modifier) {
        DshNavBar(onBack = onBack, title = "NAS 连接", stacked = true)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            Spacer(Modifier.height(14.dp))

            // —— 空态主卡（不显示容量条，改给前置条件）——
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(54.dp)
                        .background(c.line2, RoundedCornerShape(17.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(DshIcons.CloudOff, null, Modifier.size(26.dp), tint = c.ink3)
                }
                Spacer(Modifier.height(11.dp))
                Text("还没有连接 NAS", style = Type.rowTitle, color = c.ink)
                Spacer(Modifier.height(7.dp))
                Text(
                    "连上之后可以把挂载目录当工作区来源，\n或只作为产物备份目标",
                    style = Type.sec.copy(lineHeight = 20.sp),
                    color = c.ink3,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(15.dp))
                DshBtn("连接 NAS", icon = DshIcons.Add, onClick = onConnect)
            }

            DshSecTitle("支持协议")
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                DshChip("WebDAV", tone = ChipTone.On, onClick = { protocol = 0 })
                DshChip("SMB", onClick = { protocol = 1 })
                DshChip("SFTP", onClick = { protocol = 2 })
            }

            DshSecTitle("前置条件")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
            ) {
                DshMRow(
                    title = "与 NAS 处于同一内网",
                    subtitle = "跨网需先打通路由或走 VPN",
                    icon = DshIcons.Wifi,
                    iconTone = ChipTone.Neutral,
                )
                DshDivider()
                DshMRow(
                    title = "共享目录可读写",
                    subtitle = "仅上传映射只能作备份目标",
                    icon = DshIcons.FolderShared,
                    iconTone = ChipTone.Neutral,
                )
                DshDivider()
                DshMRow(
                    title = "凭据只写不回读",
                    subtitle = "与模型密钥同一套保存规则",
                    icon = DshIcons.Shield,
                    iconTone = ChipTone.Neutral,
                )
            }

            DshSecTitle("连不上先查这两项")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    "1. 端口不通：测试连接报超时或 401，先确认端口与 HTTPS\n" +
                        "2. 目录不可写：映射方向改「仅上传」或换共享目录",
                    style = Type.meta.copy(lineHeight = 21.sp),
                    color = c.ink2,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ============================ S35 用量与账单明细 ============================

/** S35 一条消费记录 */
private data class S35Record(
    val title: String,
    val subtitle: String,
    val amount: String,
    val failed: Boolean = false,
)

@Composable
fun S35Usage(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onExport: () -> Unit = {},
    onOpenSession: () -> Unit = {},
) {
    val c = DshTheme.colors
    var range by remember { mutableStateOf(0) }

    val records = listOf(
        S35Record("生成 Q3 数据汇总 PPT", "昨天 19:41 · V4-Flash · 128.4k tok", "¥0.31"),
        S35Record("给销售报表补同比列", "周一 · PTC · 341.2k tok", "¥0.84"),
        S35Record("每日销售日报（定时）", "今 09:00 · V4-Flash · 22.1k tok", "¥0.05"),
        S35Record(
            "数据备份巡检（定时）",
            "今 03:00 · 失败未计费 · MISSING_CREDENTIAL",
            "¥0.00",
            failed = true,
        ),
    )
    val byModel = listOf(
        Triple("DeepSeek-V4-Flash", 0.6f, "¥4.12"),
        Triple("DeepSeek-V4-Pro", 0.4f, "¥2.70"),
    )

    DshScreen(modifier) {
        DshNavBar(
            onBack = onBack,
            title = "用量与账单",
            stacked = true,
            actions = {
                DshIBtn(DshIcons.FileDownload, "导出账单 CSV", onClick = onExport)
            },
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            Spacer(Modifier.height(12.dp))

            DshSeg(listOf("近 7 天", "近 30 天", "本月"), range) { range = it }

            Spacer(Modifier.height(12.dp))

            // —— 三项指标卡（Token / 缓存命中 / 消费）——
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(horizontal = 8.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                S35Stat("2.14M", "Token", Modifier.weight(1f), c.ink)
                Box(Modifier.width(1.dp).height(28.dp).background(c.line2))
                S35Stat("71%", "缓存命中", Modifier.weight(1f), c.ok)
                Box(Modifier.width(1.dp).height(28.dp).background(c.line2))
                S35Stat("¥6.82", "消费", Modifier.weight(1f), c.ink)
            }

            DshSecTitle("按模型", modifier = Modifier.padding(top = 12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(horizontal = 13.dp, vertical = 12.dp),
            ) {
                byModel.forEachIndexed { i, (name, frac, amount) ->
                    if (i > 0) Spacer(Modifier.height(9.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Text(
                            name,
                            style = Type.sec,
                            color = c.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(98.dp),
                        )
                        DshProgressBar(
                            frac,
                            Modifier.weight(1f),
                            color = if (i == 0) c.brand else c.purple,
                        )
                        Text(
                            amount,
                            style = Type.sec.copy(fontWeight = FontWeight.SemiBold),
                            color = c.ink,
                            modifier = Modifier.width(46.dp),
                            textAlign = TextAlign.Right,
                        )
                    }
                }
            }

            DshSecTitle("消费记录", note = "${records.size} 条")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
            ) {
                records.forEachIndexed { i, r ->
                    if (i > 0) DshDivider()
                    DshMRow(
                        title = r.title,
                        subtitle = r.subtitle,
                        icon = if (r.failed) DshIcons.ErrorOutline else null,
                        iconTone = if (r.failed) ChipTone.Warn else null,
                        trailing = { DshRowTail(r.amount) },
                        onClick = onOpenSession,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun S35Stat(value: String, label: String, modifier: Modifier = Modifier, color: Color) {
    val c = DshTheme.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = Type.rowTitle.copy(fontWeight = FontWeight.Bold),
            color = color,
            maxLines = 1,
        )
        Text(label, style = Type.meta.copy(fontSize = 10.sp), color = c.ink3, modifier = Modifier.padding(top = 2.dp))
    }
}

// ============================ S36 充值 ============================

@Composable
fun S36TopUp(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onPay: () -> Unit = {},
    onTransfer: () -> Unit = {},
) {
    val c = DshTheme.colors
    var amount by remember { mutableStateOf(1) } // 0=¥10 1=¥50 2=其他
    var pay by remember { mutableStateOf(0) } // 0=微信 1=支付宝 2=对公

    val amounts = listOf(Triple("¥10", "约 40 万 tok", 10), Triple("¥50", "约 200 万 tok", 50), Triple("其他", "自定义金额", 0))
    val balance = 4.08
    val chosen = amounts[amount].third
    val after = if (chosen > 0) balance + chosen else balance

    DshScreen(modifier) {
        DshNavBar(onBack = onBack, title = "充值余额", stacked = true)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            Spacer(Modifier.height(14.dp))

            // —— 当前余额（soft 卡；赠金与本金分列）——
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.soft)
                    .padding(horizontal = 15.dp, vertical = 13.dp),
            ) {
                Text(
                    "当前充值余额",
                    style = Type.meta.copy(fontWeight = FontWeight.SemiBold),
                    color = c.ink3,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("¥", style = Type.sec, color = c.ink2, modifier = Modifier.padding(bottom = 6.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("4.08", style = Type.h1.copy(fontSize = 26.sp), color = c.ink)
                }
                Text(
                    "赠金余额 ¥0.00 · 赠金不可退",
                    style = Type.meta,
                    color = c.ink3,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }

            Spacer(Modifier.height(14.dp))
            DshFieldGroup(label = "选择金额") {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    amounts.forEachIndexed { i, (a, d, _) ->
                        S36AmountPick(
                            amount = a,
                            desc = d,
                            selected = amount == i,
                            modifier = Modifier.weight(1f),
                            onClick = { amount = i },
                        )
                    }
                }
            }

            DshFieldGroup(label = "支付方式") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.xl))
                        .background(c.card)
                        .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
                ) {
                    DshMRow(
                        title = "微信支付",
                        icon = DshIcons.Chat,
                        iconTone = ChipTone.Ok,
                        trailing = {
                            Icon(
                                if (pay == 0) DshIcons.RadioButtonChecked else DshIcons.RadioButtonUnchecked,
                                null,
                                Modifier.size(19.dp),
                                tint = if (pay == 0) c.brand else c.ink3,
                            )
                        },
                        onClick = { pay = 0 },
                    )
                    DshDivider()
                    DshMRow(
                        title = "支付宝",
                        icon = DshIcons.AccountBalance,
                        trailing = {
                            Icon(
                                if (pay == 1) DshIcons.RadioButtonChecked else DshIcons.RadioButtonUnchecked,
                                null,
                                Modifier.size(19.dp),
                                tint = if (pay == 1) c.brand else c.ink3,
                            )
                        },
                        onClick = { pay = 1 },
                    )
                    DshDivider()
                    DshMRow(
                        title = "对公转账 / 发票",
                        subtitle = "需企业管理员开通",
                        icon = DshIcons.ReceiptLong,
                        iconTone = ChipTone.Neutral,
                        chevron = true,
                        onClick = onTransfer,
                    )
                }
            }

            // —— 到账预览 ——
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("充值后余额", style = Type.sec, color = c.ink3)
                Spacer(Modifier.weight(1f))
                Text(
                    "¥${"%.2f".format(after)}",
                    style = Type.rowTitle.copy(fontWeight = FontWeight.Bold),
                    color = c.ink,
                )
            }

            Spacer(Modifier.height(14.dp))
            DshBtn("支付并充值", onClick = onPay)

            Text(
                "按模型计费，缓存命中的输入部分按折扣价计；余额不足时任务会在请求前停下，不会静默欠费。",
                style = Type.meta.copy(lineHeight = 17.sp),
                color = c.ink3,
                modifier = Modifier.padding(top = 10.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** S36 金额选项卡（居中：金额 + 约算 tok） */
@Composable
private fun S36AmountPick(
    amount: String,
    desc: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = DshTheme.colors
    Column(
        modifier
            .clip(RoundedCornerShape(Radius.xl))
            .background(if (selected) c.soft else c.card)
            .border(
                BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) c.brand else c.line),
                RoundedCornerShape(Radius.xl),
            )
            .clickable { onClick() }
            .padding(vertical = 11.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            amount,
            style = Type.rowTitle.copy(fontWeight = FontWeight.Bold),
            color = if (selected) c.brand else c.ink,
        )
        Text(
            desc,
            style = Type.meta.copy(fontSize = 10.sp),
            color = c.ink3,
            modifier = Modifier.padding(top = 2.dp),
            maxLines = 1,
        )
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
                title = "退出登录？",
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
                S38LossRow(DshIcons.RemoveCircleOutline, c.danger, "账号额度不再可用，需改用 API Key 继续")
                DshDivider()
                S38LossRow(DshIcons.PauseCircleOutline, c.warn, "进行中的任务会被停止，未完成轮次不续跑")
                DshDivider()
                S38LossRow(DshIcons.Check, c.ok, "会话与轨迹日志保留在 ~/.dsh")
                DshDivider()
                S38LossRow(DshIcons.Check, c.ok, "工作区文件与插件配置不受影响")
            }
            Text(
                "若只是想换计费方式，可以在新会话的模型选择里改用 API Key 提供方，不必退出登录。",
                style = Type.meta.copy(lineHeight = 17.6.sp),
                color = c.ink3,
                modifier = Modifier.padding(top = 10.dp),
            )
        },
        footer = {
            DshBtn("留在当前", tone = BtnTone.Ghost, onClick = onDismiss)
            Spacer(Modifier.height(9.dp))
            DshBtn("确认退出", tone = BtnTone.Danger, onClick = onConfirm)
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
