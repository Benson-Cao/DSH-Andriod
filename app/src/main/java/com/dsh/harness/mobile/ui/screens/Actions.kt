package com.dsh.harness.mobile.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.harness.mobile.ui.components.*
import com.dsh.harness.mobile.ui.theme.*

/**
 * v20 动作与工具集群（S40–S48）。
 *
 * 对应设计稿第三批新屏：
 *   S40 新建自动化任务（S13「+ 新建」落地页：名称 / 提示词 / 工作区 / 模式模型 / 触发 / 通知）
 *   S41 选择工作区目录（S3「添加本地文件夹」落地页：授权前置 + 目录浏览 + NAS 并列）
 *   S42 创造模式 · 创建插件（对话式草稿 + bundle 结构 + 热加载 / 导出）
 *   S43 添加模型与推理等级（S25「添加模型」落地页：档位 ↔ API 参数映射）
 *   S44 索引源与市场管理（S12「添加索引源」落地页：订阅 / 校验 / 不预置声明）
 *   S45 全局搜索（会话 / 文件 / 插件 / 指令四类分组 + 关键词高亮）
 *   S46 数据导出（内容多选 / 格式 / 脱敏 / 目标位置 / 打包进度）
 *   S47 Git 提交（staged / unstaged 分组 + Agent 起草说明 + 提交与推送分离）
 *   S48 归档会话（按工作区分组 + 批量恢复删除 + 单条导出）
 *
 * 诚实性原则：S45 的搜索结果为设计稿示意数据（本机无真实检索索引）；
 * S46/S47 的打包进度与 diff 统计同样为演示值，真实实现需接 DshViewModel。
 */

// ============================ S40 新建自动化任务 ============================

/**
 * S40 新建自动化任务。
 *
 * 布局（设计稿骨架表）：状态栏 5% ｜ 导航 6%(返回 + 标题 + 用预设) ｜
 * 任务名称 8% ｜ 提示词 12%(正文 + 两个附件 chip) ｜ 工作区/权限两列 10% ｜
 * 模式与模型 14%(seg + 模型行) ｜ 触发 14%(seg + Cron + 下次) ｜
 * 通知开关卡 10% ｜ jobs-local 脚注 6% ｜ 取消/保存 8%。
 */
@Composable
fun S40NewTask(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onUsePreset: () -> Unit = {},
    onPickWorkspace: () -> Unit = {},
    onPickModel: () -> Unit = {},
    onSave: () -> Unit = {},
) {
    val c = DshTheme.colors
    var mode by remember { mutableStateOf(0) }      // 标准 / PTC / 极简 / 创造
    var trigger by remember { mutableStateOf(0) }   // 每天 / 每周 / 每月 / Cron
    var writeBack by remember { mutableStateOf(true) }
    var notifyFail by remember { mutableStateOf(true) }
    val modes = listOf("标准", "PTC", "极简", "创造")
    val triggers = listOf("每天", "每周", "每月", "Cron")

    DshScreen(modifier) {
        DshNavBar(
            title = "新建自动化任务",
            onBack = onBack,
            stacked = true,
            actions = { DshChip("用预设", onClick = onUsePreset) },
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            Spacer(Modifier.height(12.dp))

            // —— 任务名称 ——
            DshFieldGroup(label = "任务名称") {
                DshFinRow {
                    Text("每日销售日报", style = Type.sec, color = c.ink)
                }
            }

            // —— 提示词（正文 + 附件 chip）——
            DshFieldGroup(label = "提示词") {
                DshCard {
                    Text(
                        "读取昨日订单表 → 追加同比 / 环比列 → 输出 report_YYYYMMDD.md 并回写到本会话",
                        style = Type.sec.copy(lineHeight = 20.5.sp),
                        color = c.ink2,
                    )
                    Row(
                        Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DshChip("@sales_q3.xlsx", mini = true)
                        DshChip("/goal", mini = true)
                    }
                }
            }

            // —— 工作区 + 权限（两列，权限列固定 112）——
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                DshFieldGroup(label = "工作区", modifier = Modifier.weight(1f)) {
                    DshFinRow(onClick = onPickWorkspace) {
                        Text(
                            "默认工作区",
                            style = Type.mono.copy(fontSize = 11.sp),
                            color = c.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.weight(1f))
                        Icon(DshIcons.ExpandMore, null, Modifier.size(15.dp), tint = c.ink3)
                    }
                }
                DshFieldGroup(label = "权限", modifier = Modifier.width(112.dp)) {
                    DshFinRow {
                        Text(
                            "工作区内修改",
                            style = Type.sec.copy(fontSize = 12.sp),
                            color = c.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // —— 模式与模型 ——
            DshFieldGroup(label = "模式与模型") {
                DshSeg(modes, mode) { mode = it }
                DshFinRow(
                    onClick = onPickModel,
                    modifier = Modifier.padding(top = 7.dp).height(36.dp),
                ) {
                    Text(
                        "DeepSeek-V4-Flash",
                        style = Type.sec.copy(fontSize = 12.sp),
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("High", style = Type.meta, color = c.ink3)
                    Spacer(Modifier.weight(1f))
                    Icon(DshIcons.ExpandMore, null, Modifier.size(15.dp), tint = c.ink3)
                }
            }

            // —— 触发规则（seg + Cron + 下次执行）——
            DshFieldGroup(label = "触发") {
                DshSeg(triggers, trigger) { trigger = it }
                Row(
                    Modifier.padding(top = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    DshFinRow(modifier = Modifier.weight(1f).height(36.dp)) {
                        Text(
                            "0 9 * * *",
                            style = Type.mono,
                            color = c.ink,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    DshFinRow(modifier = Modifier.weight(1f).height(36.dp)) {
                        Text(
                            "下次 明天 09:00",
                            style = Type.sec.copy(fontSize = 12.sp),
                            color = c.ink,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // —— 通知开关卡 ——
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
            ) {
                DshMRow(
                    title = "完成后回写会话",
                    trailing = { DshSwitch(writeBack) { writeBack = it } },
                )
                DshDivider()
                DshMRow(
                    title = "失败时通知",
                    trailing = { DshSwitch(notifyFail) { notifyFail = it } },
                )
            }

            Text(
                "任务由本机 jobs-local 驱动：需 dsh 服务在跑（见 S27），息屏前建议开启唤醒锁。",
                style = Type.meta.copy(lineHeight = 16.8.sp),
                color = c.ink3,
                modifier = Modifier.padding(top = 10.dp),
            )

            Row(
                Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DshBtn(
                    "取消",
                    Modifier.weight(1f),
                    tone = BtnTone.Ghost,
                    onClick = onBack,
                )
                DshBtn("保存并启用", Modifier.weight(1.4f), onClick = onSave)
            }
        }
    }
}

// ============================ S41 选择工作区目录 ============================

/** S41 目录行数据 */
private data class S41Folder(
    val name: String,
    val icon: ImageVector,
    val iconTone: ChipTone? = null,
    val tail: String? = null,
    val chip: String? = null,
    val chipTone: ChipTone = ChipTone.Neutral,
    val writable: Boolean = true,
)

/**
 * S41 选择工作区目录。
 *
 * 布局：状态栏 5% ｜ 导航 6%(返回 + 选择文件夹 + 新建) ｜ 当前路径条 7% ｜
 * 目录列表 34%(本地三条 + NAS + 只读) ｜ 存储权限告警 11% ｜
 * 主按钮 8% ｜ 脚注 6%。
 */
@Composable
fun S41PickFolder(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onNewFolder: () -> Unit = {},
    onOpenTermux: () -> Unit = {},
    onConfirm: (String) -> Unit = {},
) {
    val c = DshTheme.colors
    var sel by remember { mutableStateOf(0) }
    val folders = listOf(
        S41Folder("数据分析项目", DshIcons.Folder, tail = "12 会话"),
        S41Folder("DSH 插件开发", DshIcons.Folder, iconTone = ChipTone.Purple, tail = "3 会话"),
        S41Folder("汇报材料", DshIcons.Folder, iconTone = ChipTone.Neutral, tail = "新"),
        S41Folder("nas-mount", DshIcons.Cloud, iconTone = ChipTone.Ok, chip = "NAS"),
        S41Folder(
            "backup/charts", DshIcons.CloudOff, iconTone = ChipTone.Warn,
            chip = "只读", chipTone = ChipTone.Warn, writable = false,
        ),
    )

    DshScreen(modifier) {
        DshNavBar(
            title = "选择文件夹",
            onBack = onBack,
            stacked = true,
            actions = { DshChip("新建", onClick = onNewFolder) },
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            Spacer(Modifier.height(12.dp))

            // —— 当前路径条 ——
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(DshIcons.FolderOpen, null, Modifier.size(15.dp), tint = c.ink3)
                Text(
                    "/storage/self/primary/Documents",
                    style = Type.mono.copy(fontSize = 11.sp),
                    color = c.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(DshIcons.ChevronRight, null, Modifier.size(15.dp), tint = c.ink3)
            }

            Spacer(Modifier.height(10.dp))

            // —— 目录列表 ——
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
            ) {
                folders.forEachIndexed { i, f ->
                    if (i > 0) DshDivider()
                    DshMRow(
                        title = f.name,
                        icon = f.icon,
                        iconTone = f.iconTone,
                        onClick = if (f.writable) ({ sel = i }) else null,
                        trailing = {
                            if (f.chip != null) {
                                DshChip(f.chip, tone = f.chipTone, mini = true)
                            } else if (f.tail != null) {
                                DshRowTail(f.tail)
                            }
                        },
                    )
                }
            }

            // —— 存储权限告警（warn 淡底 + 去处理）——
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 11.dp)
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.warnSoft())
                    .border(
                        1.dp,
                        c.warn.copy(alpha = 0.28f),
                        RoundedCornerShape(Radius.xl),
                    )
                    .padding(11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(DshIcons.ErrorOutline, null, Modifier.size(16.dp), tint = c.warn)
                Text(
                    "未授予存储权限时列表只会显示 Harness 私有目录。跑在 Termux 下需先执行 termux-setup-storage",
                    style = Type.meta.copy(lineHeight = 17.6.sp),
                    color = c.ink2,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "去处理",
                    style = Type.sec.copy(fontSize = 11.5.sp),
                    color = c.brand,
                    modifier = Modifier.clickable { onOpenTermux() },
                )
            }

            DshBtn(
                "用「${folders[sel].name}」作工作区",
                Modifier.padding(top = 14.dp),
                onClick = { onConfirm(folders[sel].name) },
            )

            Text(
                "选定后所有读写与命令只在该目录内执行；只读映射目录不能被选作写入工作区。",
                style = Type.meta.copy(lineHeight = 16.8.sp),
                color = c.ink3,
                modifier = Modifier.padding(top = 9.dp),
            )
        }
    }
}

// ============================ S42 创造模式 · 创建插件 ============================

/**
 * S42 创造模式 · 创建自己的插件。
 *
 * 布局：状态栏 5% ｜ 导航 6%(返回 + 标题 + 创造模式 chip) ｜
 * 用户气泡 8% ｜ 思考折叠 9% ｜ 助手气泡 8% ｜ 插件草稿卡 30%(结构 chip + 代码块) ｜
 * 四动作网格 14% ｜ purple 脚注 8%。
 */
@Composable
fun S42CreatePlugin(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onHotReload: () -> Unit = {},
    onKeepDraft: () -> Unit = {},
    onExportSource: () -> Unit = {},
    onOpenPlugins: () -> Unit = {},
) {
    val c = DshTheme.colors
    val draftCode = "// package.json\n" +
        "{ \"name\": \"dsh-usage-bar\",\n" +
        "  \"dsh\": { \"bundle\": \"usage-bar\", \"entry\": \"src/index.ts\" } }\n" +
        "\n" +
        "// src/index.ts\n" +
        "export function definePlugin(ctx) {\n" +
        "  ctx.ui.composer.before(useUsageBar())\n" +
        "  ctx.on('llm.usage', render)\n" +
        "}"

    DshScreen(modifier) {
        DshNavBar(
            onBack = onBack,
            title = "创建自己的插件",
            stacked = true,
            actions = {
                // 设计稿 h22 / 10.5px 的紫色 chip（DshChip 只有 h26 与 h20 两档，这里按稿复刻）
                Row(
                    Modifier
                        .height(22.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(c.purpleSoft())
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "创造模式",
                        style = Type.meta.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold),
                        color = c.purple,
                    )
                }
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

            // —— 用户气泡（右对齐，品牌底白字，右下角小圆角）——
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    "给会话加一个实时 token 用量条，放在输入框上方",
                    style = Type.body,
                    color = Color.White,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 15.dp,
                                topEnd = 15.dp,
                                bottomEnd = 5.dp,
                                bottomStart = 15.dp,
                            ),
                        )
                        .background(c.brand)
                        .padding(horizontal = 13.dp, vertical = 10.dp),
                )
            }

            // —— 思考折叠（带右侧判断提示）——
            DshFold(
                title = "已思考 · 3.2s",
                detail = "需要订阅模型请求事件并挂载到 composer 上方；只读状态、不改请求内容，可安全热加载。",
                modifier = Modifier.fillMaxWidth(),
                initiallyOpen = true,
                trailing = {
                    Text("判断为 UI 侧插件", style = Type.meta, color = c.ink3, maxLines = 1)
                },
            )

            // —— 助手气泡（左对齐纯文本，内嵌 mono）——
            Text(
                buildPluginAnswer(c.mono),
                style = Type.body,
                color = c.ink,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .padding(top = 2.dp),
            )

            // —— 插件草稿卡 ——
            DshCard(modifier = Modifier.padding(top = 11.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Icon(DshIcons.Description, null, Modifier.size(17.dp), tint = c.brand)
                    Text(
                        "插件草稿",
                        style = Type.sec.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                        color = c.ink,
                    )
                    Spacer(Modifier.weight(1f))
                    DshChip("结构校验通过", tone = ChipTone.Ok, mini = true)
                }
                Spacer(Modifier.height(9.dp))
                DshCode(draftCode, fontSize = 10.5.sp)
            }

            // —— 四动作网格（2×2）——
            Column(
                Modifier.padding(top = 9.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    DshBtn(
                        "热加载预览",
                        Modifier.weight(1f),
                        icon = DshIcons.Bolt,
                        tone = BtnTone.Ghost,
                        small = true,
                        onClick = onHotReload,
                    )
                    DshBtn(
                        "保留草稿",
                        Modifier.weight(1f),
                        icon = DshIcons.Save,
                        tone = BtnTone.Ghost,
                        small = true,
                        onClick = onKeepDraft,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    DshBtn(
                        "导出源码",
                        Modifier.weight(1f),
                        icon = DshIcons.FileDownload,
                        tone = BtnTone.Ghost,
                        small = true,
                        onClick = onExportSource,
                    )
                    DshBtn(
                        "去插件配置",
                        Modifier.weight(1f),
                        icon = DshIcons.Extension,
                        tone = BtnTone.Ghost,
                        small = true,
                        onClick = onOpenPlugins,
                    )
                }
            }

            // —— purple 脚注（NoteCard 同构）——
            NoteCard(
                text = "创造模式自带标准模式全部能力（读文件、跑命令、搜索、子代理），额外提供内存热加载与源码导出；卸载插件会回滚它注册的服务与事件。",
                accent = c.purple,
                modifier = Modifier.padding(top = 11.dp),
            )
        }
    }
}

/** S42 助手气泡里的 mono 片段（dsh-usage-bar） */
private fun buildPluginAnswer(monoColor: Color): AnnotatedString = buildAnnotatedString {
    append("可以，做一个 UI 插件：新增 ")
    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = monoColor)) {
        append("dsh-usage-bar")
    }
    append(" bundle，注册一个 composer 上方插槽并订阅请求计量事件。")
}

// ============================ S43 添加模型与推理等级 ============================

/**
 * S43 添加模型与推理等级。
 *
 * 布局：状态栏 5% ｜ 导航 6%(返回 + 添加模型 + bailian) ｜
 * 模型 ID / 显示名称两列 9% ｜ 上下文 / 输出两列 9% ｜
 * 推理等级标题 5% ｜ 档位映射三行 18% ｜ 添加映射按钮 7% ｜
 * 蓝色说明卡 11% ｜ 保存模型 8%。
 */
@Composable
fun S43AddModel(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenProvider: () -> Unit = {},
    onSave: () -> Unit = {},
) {
    val c = DshTheme.colors
    // v30.9：档位映射做成屏内真实可增删列表（此前「添加档位映射」是空回调）。
    // 默认三档是 DashScope/百炼的官方档位，用户可追加自定义 label/param 或删除自定义档位。
    val baseMappings = listOf(
        Triple("Off", "enable_thinking=false", ChipTone.Neutral),
        Triple("Low", "thinking_budget=1024", ChipTone.Neutral),
        Triple("High", "thinking_budget=8192", ChipTone.Neutral),
    )
    var customMappings by remember { mutableStateOf(listOf<Triple<String, String, ChipTone>>()) }
    var newLabel by remember { mutableStateOf("") }
    var newParam by remember { mutableStateOf("") }
    val mappings = baseMappings + customMappings

    DshScreen(modifier) {
        DshNavBar(
            title = "添加模型",
            onBack = onBack,
            stacked = true,
            actions = { DshChip("bailian", onClick = onOpenProvider) },
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            Spacer(Modifier.height(12.dp))

            // —— 模型 ID + 显示名称 ——
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                DshFieldGroup(label = "模型 ID", modifier = Modifier.weight(1f)) {
                    DshFinRow {
                        Text("qwen3.7-plus", style = Type.mono, color = c.ink2, maxLines = 1)
                    }
                }
                DshFieldGroup(label = "显示名称", modifier = Modifier.weight(1f)) {
                    DshFinRow {
                        Text("Qwen3.7 Plus", style = Type.sec, color = c.ink, maxLines = 1)
                    }
                }
            }

            // —— 上下文长度 + 最大输出 ——
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                DshFieldGroup(label = "上下文长度", modifier = Modifier.weight(1f)) {
                    DshFinRow {
                        Text("1,000,000", style = Type.mono, color = c.ink2, maxLines = 1)
                    }
                }
                DshFieldGroup(label = "最大输出", modifier = Modifier.weight(1f)) {
                    DshFinRow {
                        Text("384,000", style = Type.mono, color = c.ink2, maxLines = 1)
                    }
                }
            }

            // —— 推理等级（界面显示 ↔ API 参数）——
            DshSecTitle("推理等级", Modifier.padding(top = 6.dp), note = "界面显示 ↔ API 参数")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
            ) {
                mappings.forEachIndexed { i, (label, param, _) ->
                    if (i > 0) DshDivider()
                    S43MapRow(
                        label = label,
                        param = param,
                        // 只有自定义档位可删；官方三档是服务端语义，不给删。
                        onDelete = if (i >= baseMappings.size) {
                            {
                                val target = Triple(label, param, ChipTone.Neutral)
                                customMappings = customMappings.filterNot { it == target }
                            }
                        } else null,
                    )
                }
            }

            // —— 新增档位：两个真输入框 + 添加 ——
            Row(
                Modifier.padding(top = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                DshFieldGroup(label = "档位名", modifier = Modifier.weight(1f)) {
                    DshFinRow {
                        BasicTextField(
                            value = newLabel,
                            onValueChange = { newLabel = it.take(16) },
                            singleLine = true,
                            textStyle = Type.mono.copy(color = c.ink),
                            cursorBrush = SolidColor(c.brand),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (newLabel.isEmpty()) {
                                    Text("如 Medium", style = Type.mono, color = c.ink3)
                                }
                                inner()
                            },
                        )
                    }
                }
                DshFieldGroup(label = "API 参数", modifier = Modifier.weight(1.4f)) {
                    DshFinRow {
                        BasicTextField(
                            value = newParam,
                            onValueChange = { newParam = it.take(64) },
                            singleLine = true,
                            textStyle = Type.mono.copy(color = c.ink),
                            cursorBrush = SolidColor(c.brand),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (newParam.isEmpty()) {
                                    Text("thinking_budget=4096", style = Type.mono, color = c.ink3)
                                }
                                inner()
                            },
                        )
                    }
                }
            }
            DshBtn(
                if (customMappings.isEmpty()) "添加档位映射" else "再添加一个档位",
                Modifier.padding(top = 9.dp),
                icon = DshIcons.Add,
                tone = BtnTone.Ghost,
                height = 40.dp,
                enabled = newLabel.isNotBlank() && newParam.isNotBlank(),
                onClick = {
                    customMappings = customMappings + Triple(
                        newLabel.trim(),
                        newParam.trim(),
                        ChipTone.Neutral,
                    )
                    newLabel = ""
                    newParam = ""
                },
            )

            // —— 蓝色说明卡（soft）——
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 11.dp)
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.soft)
                    .padding(11.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(DshIcons.Info, null, Modifier.size(15.dp), tint = c.brand)
                Text(
                    "这里增加一个「最高档」不会让模型凭空获得新能力；只声明服务端真实支持的等级。未声明的模型不提供思考选项，是否思考由服务端默认决定。",
                    style = Type.meta.copy(lineHeight = 18.2.sp),
                    color = c.ink2,
                )
            }

            DshBtn("保存模型", Modifier.padding(top = 14.dp), onClick = onSave)
        }
    }
}

/** S43 单行档位映射：52dp 档位 chip ↔ 居中 ↔ 右对齐参数 + 26dp 删除钮 */
@Composable
private fun S43MapRow(label: String, param: String, onDelete: (() -> Unit)? = null) {
    val c = DshTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .width(52.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.line2),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = Type.meta, color = c.ink2)
        }
        Text(
            "↔",
            style = Type.mono,
            color = c.ink3,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Text(
            param,
            style = Type.mono,
            color = c.ink,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // v30.9：官方三档不提供删除（onDelete == null → 渲染不可点的空位），自定义档位可删。
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(Radius.md))
                .then(
                    if (onDelete != null) Modifier.clickable { onDelete() } else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                DshIcons.Close,
                if (onDelete != null) "删除该档位" else "官方档位不可删除",
                Modifier.size(if (onDelete != null) 15.dp else 0.dp),
                tint = c.ink3,
            )
        }
    }
}

// ============================ S44 索引源与市场管理 ============================

/**
 * S44 索引源与市场管理。
 *
 * 布局：状态栏 5% ｜ 导航 6%(返回 + 插件市场源 + 去发现) ｜
 * 已订阅两条 12% ｜ 索引文件地址 + 校验结果 20% ｜
 * 「不预置/不等于审核」告警 13% ｜ 名称 8% ｜ 取消/添加并切换 8%。
 */
@Composable
fun S44IndexSource(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenMarket: () -> Unit = {},
    onVerify: () -> Unit = {},
    onAdd: () -> Unit = {},
) {
    val c = DshTheme.colors
    var marketOn by remember { mutableStateOf(true) }

    DshScreen(modifier) {
        DshNavBar(
            title = "插件市场源",
            onBack = onBack,
            stacked = true,
            actions = { DshChip("去发现", onClick = onOpenMarket) },
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            Spacer(Modifier.height(12.dp))

            // —— 已订阅 ——
            DshSecTitle("已订阅", Modifier.padding(top = 0.dp), note = "2 个")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
            ) {
                DshMRow(
                    title = "awesome-dsh-plugin",
                    icon = DshIcons.Hub,
                    iconTone = ChipTone.Ok,
                    trailing = { DshChip("已同步", tone = ChipTone.Ok, mini = true) },
                )
                DshDivider()
                DshMRow(
                    title = "dsh-market",
                    icon = DshIcons.Storefront,
                    iconTone = ChipTone.Purple,
                    trailing = { DshSwitch(marketOn) { marketOn = it } },
                )
            }

            // —— 索引文件地址 + 校验结果 ——
            DshFieldGroup(label = "索引文件地址", modifier = Modifier.padding(top = 14.dp)) {
                DshFinRow {
                    Text(
                        "https://example.com/dsh-index.json",
                        style = Type.mono.copy(fontSize = 11.sp),
                        color = c.ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "校验",
                        style = Type.sec.copy(fontWeight = FontWeight.SemiBold),
                        color = c.brand,
                        modifier = Modifier.clickable { onVerify() },
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 7.dp)
                        .clip(RoundedCornerShape(Radius.xl))
                        .background(c.card)
                        .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(DshIcons.CheckCircle, null, Modifier.size(15.dp), tint = c.ok)
                    Text(
                        "manifest 字段完整 · 条目 612 · 无重名冲突",
                        style = Type.meta,
                        color = c.ink2,
                        modifier = Modifier.weight(1f),
                    )
                    DshChip("SHA-256 已记录", tone = ChipTone.Ok, mini = true)
                }
            }

            // —— 不预置 / 不等于审核告警 ——
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 11.dp)
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(11.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(DshIcons.Warning, null, Modifier.size(15.dp), tint = c.warn)
                Text(
                    buildSourceNote(c.ink),
                    style = Type.meta.copy(lineHeight = 18.7.sp),
                    color = c.ink2,
                )
            }

            // —— 名称 ——
            DshFieldGroup(label = "名称", modifier = Modifier.padding(top = 12.dp)) {
                DshFinRow {
                    Text("team-picks", style = Type.mono, color = c.ink2, maxLines = 1)
                }
            }

            Row(
                Modifier.padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DshBtn("取消", Modifier.weight(1f), tone = BtnTone.Ghost, onClick = onBack)
                DshBtn("添加并切换", Modifier.weight(1.3f), onClick = onAdd)
            }
        }
    }
}

/** S44 告警文案：末句「不等于官方审核」加粗 */
private fun buildSourceNote(ink: Color): AnnotatedString = buildAnnotatedString {
    append("DSH 不预置索引源：刚开启该能力时目录为空属正常，需要先添加来源。目录里收录了什么由维护者决定，官方提供的是浏览与安装机制，")
    withStyle(SpanStyle(color = ink, fontWeight = FontWeight.Bold)) {
        append("不等于官方审核")
    }
    append("。")
}

// ============================ S45 全局搜索 ============================

/**
 * S45 全局搜索。
 *
 * 布局：状态栏 5% ｜ 导航 7%(返回 + 搜索框内嵌) ｜ 分类 seg 6% ｜
 * 会话 2 条 12% ｜ 文件 3 条 15% ｜ 插件与指令 2 条 12% ｜
 * 最近搜索 10% ｜ 克制提示 6%。nav 不加 stacked（设计稿 .nav 无 stk）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun S45GlobalSearch(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onClear: () -> Unit = {},
    onOpenSession: () -> Unit = {},
    onOpenFile: () -> Unit = {},
    onOpenPlugin: () -> Unit = {},
    onOpenCommand: () -> Unit = {},
    onRecent: (String) -> Unit = {},
) {
    val c = DshTheme.colors
    val keyword = "同比"
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("全部", "会话", "文件", "插件", "指令")
    val recents = listOf("环比", "subagent", "端口占用", "脱敏日志")

    // 光标闪烁（设计稿 .cur：1.5×14 品牌色，step-end 1s）
    val inf = rememberInfiniteTransition()
    val curAlpha by inf.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            tween(550, delayMillis = 550, easing = LinearEasing),
            RepeatMode.Reverse,
        ),
        label = "searchCursor",
    )

    DshScreen(modifier) {
        // 设计稿 S45 的 .nav 没有 stk：自绘一行，搜索框占满中段
        Row(
            Modifier
                .fillMaxWidth()
                .height(Metrics.NAV_H)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DshIBtn(DshIcons.ArrowBack, "返回", onClick = onBack)
            DshFinRow(modifier = Modifier.weight(1f).height(36.dp)) {
                Icon(DshIcons.Search, null, Modifier.size(15.dp), tint = c.ink3)
                Text(keyword, style = Type.sec.copy(fontSize = 13.sp), color = c.ink)
                Box(
                    Modifier
                        .width(1.5.dp)
                        .height(14.dp)
                        .alpha(curAlpha)
                        .background(c.brand),
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    DshIcons.Close, null, Modifier.size(15.dp).clickable { onClear() },
                    tint = c.ink3,
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            Spacer(Modifier.height(10.dp))

            DshSeg(tabs, tab) { tab = it }

            Spacer(Modifier.height(11.dp))

            // —— 会话（2）——
            DshSecTitle("会话", Modifier.padding(top = 0.dp), note = "2")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
            ) {
                S45HitRow(
                    icon = DshIcons.Forum,
                    title = "生成 Q3 数据汇总 PPT",
                    sub = "已按部门维度补齐同比 / 环比…",
                    tail = "19:52",
                    keyword = keyword,
                    hl = c.ink,
                    onClick = onOpenSession,
                )
                DshDivider()
                S45HitRow(
                    icon = DshIcons.Forum,
                    title = "给销售报表补上同比 / 环比列",
                    sub = "PTC 模式 · 8 行 × 14 列",
                    tail = "周一",
                    keyword = keyword,
                    hl = c.ink,
                    onClick = onOpenSession,
                )
            }

            // —— 文件（3）——
            DshSecTitle("文件", note = "3")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
            ) {
                S45HitRow(
                    icon = DshIcons.TableChart,
                    iconTone = ChipTone.Ok,
                    title = "sales_q3.xlsx",
                    sub = "列 q3_ly · 同比口径说明",
                    tail = "2.3 MB",
                    keyword = keyword,
                    hl = c.ink3,
                    onClick = onOpenFile,
                )
                DshDivider()
                S45HitRow(
                    icon = DshIcons.Code,
                    iconTone = ChipTone.Purple,
                    title = "aggregate.py",
                    sub = "out[\"yoy\"] = pct(...)",
                    tail = "+18 −4",
                    keyword = "yoy",
                    hl = c.ink3,
                    onClick = onOpenFile,
                )
                DshDivider()
                S45HitRow(
                    icon = DshIcons.Article,
                    iconTone = ChipTone.Neutral,
                    title = "口径说明.md",
                    sub = "「同比使用 2025 年同期公开口径」",
                    tail = "6.4 KB",
                    keyword = keyword,
                    hl = c.ink3,
                    onClick = onOpenFile,
                )
            }

            // —— 插件与指令 ——
            DshSecTitle("插件与指令")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
            ) {
                DshMRow(
                    title = "dsh-plugin-git-inspect",
                    subtitle = "只读 Git 检查 · 差异 / 日志 / 冲突",
                    icon = DshIcons.Extension,
                    iconTone = ChipTone.Purple,
                    trailing = { DshChip("安装", tone = ChipTone.Brand, mini = true) },
                    onClick = onOpenPlugin,
                )
                DshDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenCommand() }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "/goal",
                        style = Type.mono,
                        color = c.brand,
                        modifier = Modifier.width(62.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text("设定长期目标", style = Type.rowTitle, color = c.ink, maxLines = 1)
                        Text(
                            "跨轮次持续追踪，支持图文",
                            style = Type.sec,
                            color = c.ink3,
                            maxLines = 1,
                        )
                    }
                }
            }

            // —— 最近搜索（设计稿 flex-wrap，窄机可换行）——
            DshCard(modifier = Modifier.padding(top = 11.dp)) {
                Text(
                    "最近搜索",
                    style = Type.meta.copy(
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.4.sp,
                    ),
                    color = c.ink3,
                )
                Spacer(Modifier.height(7.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    recents.forEach { r ->
                        DshChip(r, onClick = { onRecent(r) })
                    }
                }
            }

            Text(
                "无结果时不自动换词重搜，会明确告诉你没找到；文件搜索范围限定在当前工作区目录内。",
                style = Type.meta.copy(lineHeight = 16.8.sp),
                color = c.ink3,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/** S45 单条命中：主/副标题里的关键词加粗高亮 */
@Composable
private fun S45HitRow(
    icon: ImageVector,
    title: String,
    sub: String,
    tail: String,
    keyword: String,
    hl: Color,
    iconTone: ChipTone? = null,
    onClick: () -> Unit,
) {
    val c = DshTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(Metrics.ICON_BOX)
                .background(
                    when (iconTone) {
                        null, ChipTone.On -> c.soft
                        ChipTone.Ok -> c.okSoft()
                        ChipTone.Warn -> c.warnSoft()
                        ChipTone.Danger -> c.dangerSoft()
                        ChipTone.Purple -> c.purpleSoft()
                        else -> c.soft
                    },
                    RoundedCornerShape(Radius.md),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon, null, Modifier.size(19.dp),
                tint = when (iconTone) {
                    null, ChipTone.On -> c.brand
                    ChipTone.Neutral -> c.ink2
                    ChipTone.Ok -> c.ok
                    ChipTone.Warn -> c.warn
                    ChipTone.Danger -> c.danger
                    ChipTone.Purple -> c.purple
                    else -> c.brand
                },
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                highlightKeyword(title, keyword, hl),
                style = Type.rowTitle,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                highlightKeyword(sub, keyword, hl),
                style = Type.sec,
                color = c.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DshRowTail(tail)
    }
}

/** 关键词加粗高亮：保留原色，仅加粗（设计稿 <b> 语义） */
private fun highlightKeyword(text: String, keyword: String, hl: Color): AnnotatedString =
    buildAnnotatedString {
        if (keyword.isEmpty()) {
            append(text)
            return@buildAnnotatedString
        }
        var start = 0
        while (true) {
            val idx = text.indexOf(keyword, start, ignoreCase = true)
            if (idx < 0) {
                append(text.substring(start))
                return@buildAnnotatedString
            }
            append(text.substring(start, idx))
            withStyle(SpanStyle(color = hl, fontWeight = FontWeight.Bold)) {
                append(text.substring(idx, idx + keyword.length))
            }
            start = idx + keyword.length
        }
    }

// ============================ S46 数据导出 ============================

/**
 * S46 数据导出。
 *
 * 布局：状态栏 5% ｜ 导航 6% ｜ 导出内容四开关 24% ｜ 格式 seg 7% ｜
 * 脱敏卡 10% ｜ 导出到 seg 7% ｜ 打包进度卡 16% ｜ 完成 8% ｜ 脚注 6%。
 */
@Composable
fun S46Export(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onDone: () -> Unit = {},
) {
    val c = DshTheme.colors
    var fmt by remember { mutableStateOf(0) }        // JSONL / Markdown / ZIP
    var dest by remember { mutableStateOf(0) }       // 本地 / NAS / 剪贴板
    var sessions by remember { mutableStateOf(true) }
    var trace by remember { mutableStateOf(true) }
    var changes by remember { mutableStateOf(false) }
    var config by remember { mutableStateOf(false) }
    var redact by remember { mutableStateOf(true) }
    val fmts = listOf("JSONL", "Markdown", "ZIP")
    val dests = listOf("本地", "NAS", "剪贴板")

    DshScreen(modifier) {
        DshNavBar(title = "导出数据", onBack = onBack, stacked = true)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            Spacer(Modifier.height(12.dp))

            // —— 导出内容（四开关）——
            DshSecTitle("导出内容", Modifier.padding(top = 0.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
            ) {
                DshMRow(
                    title = "会话记录",
                    subtitle = "含分组与正文双向定位",
                    icon = DshIcons.Forum,
                    trailing = { DshSwitch(sessions) { sessions = it } },
                )
                DshDivider()
                DshMRow(
                    title = "轨迹日志",
                    subtitle = "append-only 原始事件流",
                    icon = DshIcons.Timeline,
                    iconTone = ChipTone.Purple,
                    trailing = { DshSwitch(trace) { trace = it } },
                )
                DshDivider()
                DshMRow(
                    title = "工作区变更清单",
                    subtitle = "6 个文件 · +214 −38",
                    icon = DshIcons.Difference,
                    iconTone = ChipTone.Neutral,
                    trailing = { DshSwitch(changes) { changes = it } },
                )
                DshDivider()
                DshMRow(
                    title = "配置快照",
                    subtitle = "settings.yaml 与 bundle 清单",
                    icon = DshIcons.Settings,
                    iconTone = ChipTone.Warn,
                    trailing = { DshSwitch(config) { config = it } },
                )
            }

            // —— 格式 ——
            DshFieldGroup(label = "格式", modifier = Modifier.padding(top = 14.dp)) {
                DshSeg(fmts, fmt) { fmt = it }
            }

            // —— 脱敏导出（soft 卡 + 开关）——
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 9.dp)
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.soft)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(DshIcons.Shield, null, Modifier.size(16.dp), tint = c.brand)
                Column(Modifier.weight(1f)) {
                    Text(
                        "脱敏导出",
                        style = Type.sec.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                        color = c.ink,
                    )
                    Text(
                        "home 路径与密钥形状打码，只生成纯文本",
                        style = Type.meta,
                        color = c.ink2,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                DshSwitch(redact) { redact = it }
            }

            // —— 导出到 ——
            DshFieldGroup(label = "导出到") {
                DshSeg(dests, dest) { dest = it }
            }

            // —— 打包进度（演示值）——
            DshCard(modifier = Modifier.padding(top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        color = c.brand,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "正在打包",
                        style = Type.sec.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                        color = c.ink,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "68%",
                        style = Type.sec.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        color = c.ink,
                    )
                }
                Spacer(Modifier.height(8.dp))
                DshProgressBar(0.68f)
                Text(
                    "dsh-export-20261018-2116.jsonl · 4.2 MB · 1,284 条事件",
                    style = Type.meta,
                    color = c.ink3,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }

            DshBtn("完成", Modifier.padding(top = 13.dp), onClick = onDone)

            Text(
                "导出生成在本地，工具不会主动上传任何内容；轨迹日志含系统提示词与审批事件，外发前请再确认一次脱敏范围。",
                style = Type.meta.copy(lineHeight = 16.8.sp),
                color = c.ink3,
                modifier = Modifier.padding(top = 9.dp),
            )
        }
    }
}

// ============================ S47 Git 提交 ============================

/** S47 变更行数据 */
private data class S47Change(
    val path: String,
    val status: String,          // M / A
    val add: String? = null,
    val del: String? = null,
    val staged: Boolean,
)

/**
 * S47 Git 提交。
 *
 * 布局：状态栏 5% ｜ 导航 8%(返回 + 提交变更 + 分支副题 + 工作区 chip) ｜
 * 已暂存 3 行 16% ｜ 未暂存 2 行 10% ｜ 提交说明卡 20%(Agent 起草 + 两个 chip) ｜
 * 提交/推送按钮行 9% ｜ 来源会话卡 9%。
 */
@Composable
fun S47GitCommit(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onCommit: () -> Unit = {},
    onPush: () -> Unit = {},
    onRegenerate: () -> Unit = {},
    onManualEdit: () -> Unit = {},
    onBackToSession: () -> Unit = {},
    onOpenWorkspace: () -> Unit = {},
) {
    val c = DshTheme.colors
    val staged = listOf(
        S47Change("scripts/aggregate.py", "M", add = "+18", del = "−4", staged = true),
        S47Change("AGENTS.md", "A", add = "+36", staged = true),
        S47Change("charts/ ×9", "A", add = "+9", staged = true),
    )
    val unstaged = listOf(
        S47Change("Q3数据汇总.pptx", "M", staged = false),
        S47Change("report.md · README.md", "M", staged = false),
    )

    DshScreen(modifier) {
        DshNavBar(
            onBack = onBack,
            title = "提交变更",
            subtitle = "main · 6 未提交 · ahead 2",
            stacked = true,
            actions = { DshChip("数据分析项目", mini = true) },
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            Spacer(Modifier.height(11.dp))

            // —— 已暂存（3）——
            DshSecTitle("已暂存", Modifier.padding(top = 0.dp), note = "3")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
            ) {
                staged.forEachIndexed { i, ch ->
                    if (i > 0) DshDivider()
                    S47ChangeRow(
                        ch = ch,
                        actionText = "unstage",
                        actionColor = c.ink3,
                        onAction = onOpenWorkspace,
                    )
                }
            }

            // —— 未暂存（3 个文件、2 行）——
            DshSecTitle("未暂存", note = "3")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
            ) {
                unstaged.forEachIndexed { i, ch ->
                    if (i > 0) DshDivider()
                    S47ChangeRow(
                        ch = ch,
                        actionText = "stage",
                        actionColor = c.brand,
                        onAction = onOpenWorkspace,
                    )
                }
            }

            // —— 提交说明（Agent 起草）——
            DshFieldGroup(label = "提交说明", modifier = Modifier.padding(top = 13.dp), labelTrailing = {
                Row(
                    Modifier
                        .height(17.dp)
                        .clip(RoundedCornerShape(8.5.dp))
                        .background(c.purpleSoft())
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Agent 起草",
                        style = Type.meta.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
                        color = c.purple,
                    )
                }
            }) {
                DshCard {
                    Text(
                        "feat(report): 按部门汇总 Q3 并补同比 / 环比",
                        style = Type.sec.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        color = c.ink,
                    )
                    Text(
                        "- aggregate.py 增加 yoy / mom 计算\n" +
                            "- charts/ 生成 9 张部门柱状图\n" +
                            "- 同比口径使用 2025 年同期公开口径并标注来源",
                        style = Type.meta.copy(lineHeight = 18.7.sp),
                        color = c.ink2,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
                Row(
                    Modifier.padding(top = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    DshChip("重新生成", icon = DshIcons.AutoAwesome, onClick = onRegenerate)
                    DshChip("手动编辑", onClick = onManualEdit)
                }
            }

            // —— 提交到本地 / 推送（分开）——
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DshBtn(
                    "提交到本地",
                    Modifier.weight(1.3f),
                    icon = DshIcons.Source,
                    onClick = onCommit,
                )
                DshBtn(
                    "推送",
                    Modifier.weight(1f),
                    tone = BtnTone.Ghost,
                    onClick = onPush,
                )
            }

            // —— 来源会话 ——
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 11.dp)
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(DshIcons.Forum, null, Modifier.size(15.dp), tint = c.ink3)
                Text(
                    "这批变更来自「生成 Q3 数据汇总 PPT」",
                    style = Type.meta.copy(fontSize = 11.5.sp),
                    color = c.ink2,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "回到会话",
                    style = Type.sec.copy(fontSize = 11.5.sp),
                    color = c.brand,
                    modifier = Modifier.clickable { onBackToSession() },
                )
            }
        }
    }
}

/** S47 单行变更：26dp 状态 chip + 路径 + 增删计数 + stage/unstage */
@Composable
private fun S47ChangeRow(
    ch: S47Change,
    actionText: String,
    actionColor: Color,
    onAction: () -> Unit,
) {
    val c = DshTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            Modifier
                .width(26.dp)
                .height(19.dp)
                .clip(RoundedCornerShape(9.5.dp))
                .background(if (ch.staged) c.okSoft() else c.warnSoft()),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                ch.status,
                style = Type.meta.copy(fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold),
                color = if (ch.staged) c.ok else c.warn,
            )
        }
        Text(
            ch.path,
            style = Type.sec.copy(fontSize = 13.sp),
            color = c.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (ch.add != null) {
            Text(
                ch.add,
                style = Type.meta.copy(fontSize = 11.sp),
                color = c.ok,
            )
        }
        if (ch.del != null) {
            Text(
                ch.del,
                style = Type.meta.copy(fontSize = 11.sp),
                color = c.danger,
            )
        }
        Text(
            actionText,
            style = Type.meta.copy(fontSize = 11.sp),
            color = actionColor,
            modifier = Modifier.clickable { onAction() },
        )
    }
}

// ============================ S48 归档会话 ============================

/** S48 归档行数据 */
private data class S48ArchivedSession(val title: String, val meta: String)

/**
 * S48 归档会话。
 *
 * 布局：状态栏 5% ｜ 导航 6%(返回 + 已归档 + 批量) ｜ 汇总 soft 卡 8% ｜
 * 默认工作区 3 行 16% ｜ 数据分析项目 2 行 11% ｜ 返回侧栏/删除所选 9% ｜ 脚注 6%。
 */
@Composable
fun S48Archived(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onBatch: () -> Unit = {},
    onExportAll: () -> Unit = {},
    onRestore: (String) -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onOpenSession: () -> Unit = {},
    onBackToDrawer: () -> Unit = {},
) {
    val c = DshTheme.colors
    val defaultWs = listOf(
        S48ArchivedSession("认识 DeepSeek Harness", "8-13 · 已思考 折叠 6 段"),
        S48ArchivedSession("写一个贪吃蛇游戏", "8-14 · 极简模式"),
        S48ArchivedSession("88 页论文翻译", "8-14 · PTC 模式"),
    )
    val dataWs = listOf(
        S48ArchivedSession("零号项目相关 APP 运行状态设计", "9-02 · 含 2 个子代理"),
        S48ArchivedSession("梳理接口文档并补充示例", "9-05 · 14 个文件变更"),
    )

    DshScreen(modifier) {
        DshNavBar(
            title = "已归档",
            onBack = onBack,
            stacked = true,
            actions = { DshChip("批量", onClick = onBatch) },
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            Spacer(Modifier.height(11.dp))

            // —— 汇总 soft 卡 ——
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.soft)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(DshIcons.Inventory2, null, Modifier.size(15.dp), tint = c.brand)
                Text(
                    "27 个归档会话 · 共 41.2 MB",
                    style = Type.meta.copy(fontSize = 11.5.sp),
                    color = c.ink2,
                    modifier = Modifier.weight(1f),
                )
                DshChip("导出全部", onClick = onExportAll)
            }

            // —— 默认工作区（12）——
            DshSecTitle("默认工作区", Modifier.padding(top = 11.dp), note = "12")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
            ) {
                defaultWs.forEachIndexed { i, s ->
                    if (i > 0) DshDivider()
                    S48ArchivedRow(s = s, onOpen = onOpenSession, onRestore = { onRestore(s.title) })
                }
            }

            // —— 数据分析项目（9）——
            DshSecTitle("数据分析项目", note = "9")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl)),
            ) {
                dataWs.forEachIndexed { i, s ->
                    if (i > 0) DshDivider()
                    S48ArchivedRow(s = s, onOpen = onOpenSession, onRestore = { onRestore(s.title) })
                }
            }

            // —— 返回侧栏 / 删除所选 ——
            Row(
                Modifier.padding(top = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DshBtn(
                    "返回侧栏",
                    Modifier.weight(1f),
                    tone = BtnTone.Ghost,
                    onClick = onBackToDrawer,
                )
                DshBtn(
                    "删除所选",
                    Modifier.weight(1f),
                    tone = BtnTone.Ghost,
                    contentColor = c.danger,
                    onClick = onDeleteSelected,
                )
            }

            Text(
                "归档只影响列表可见性，append-only 轨迹日志与已写入工作区的文件都完整保留；恢复后回到原工作区分组。",
                style = Type.meta.copy(lineHeight = 16.8.sp),
                color = c.ink3,
                modifier = Modifier.padding(top = 9.dp),
            )
        }
    }
}

/** S48 单行归档会话：标题 + 元信息，右侧「恢复 ›」 */
@Composable
private fun S48ArchivedRow(
    s: S48ArchivedSession,
    onOpen: () -> Unit,
    onRestore: () -> Unit,
) {
    val c = DshTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(s.title, style = Type.rowTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.meta, style = Type.sec, color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            "恢复 ›",
            style = Type.meta.copy(fontWeight = FontWeight.SemiBold),
            color = c.ink3,
            modifier = Modifier.clickable { onRestore() },
        )
    }
}
