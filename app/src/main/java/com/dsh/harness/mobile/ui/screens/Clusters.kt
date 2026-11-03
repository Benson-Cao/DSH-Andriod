package com.dsh.harness.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.harness.mobile.ui.components.*
import com.dsh.harness.mobile.ui.data.RemoteHost
import com.dsh.harness.mobile.ui.data.RemoteTask
import com.dsh.harness.mobile.ui.data.RemoteHostLoader
import com.dsh.harness.mobile.ui.Route
import com.dsh.harness.mobile.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * v23 两块全新集群的屏实现。
 *
 * - 本地模型集群（S51–S53）：本 harness 当前没有本地模型运行时，依然是诚实态壳
 *   （底部 HonestNote 标注「演示数据 · 未接入本地模型运行时」）。
 * - 遥控电脑集群（S54–S60）：数据层已接通 —— 手机端通过 [RemoteHostLoader] 从 NAS 上
 *   读取桌面端发布的 `dsh-phone/remote/host.json`（宿主在线/锁/心跳 + 任务队列）。
 *   当 NAS 未配置、或远端没有该文件、或解析失败时，回落到内置 [SAMPLE_HOST] 演示数据，
 *   并标注「未从 NAS 读取到宿主状态」。桌面端发布脚本见仓库 `scripts/publish-host-state.js`。
 */

// ============================ 小工具 ============================

/** 带指定描边色的卡片（警告/危险卡用，避免与 DshCard 默认描边叠加成双线）。 */
@Composable
private fun BorderedCard(
    borderColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = DshTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xl))
            .background(c.card)
            .border(1.dp, borderColor, RoundedCornerShape(Radius.xl))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        content = content,
    )
}

/** 事件流终端块（S55 / S59 的 JSONL 实时尾随）。mono + 行内色。 */
@Composable
private fun TermLog(lines: List<Pair<Color?, String>>, modifier: Modifier = Modifier) {
    val c = DshTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xl))
            .background(c.code)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        lines.forEach { (tint, text) ->
            Text(
                text,
                style = Type.mono,
                color = tint ?: c.ink2,
                modifier = Modifier.padding(vertical = 1.5.dp),
            )
        }
    }
}

/** 底部诚实说明：演示数据 / 暂未接入真后端。 */
@Composable
private fun HonestNote(text: String, modifier: Modifier = Modifier) {
    val c = DshTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(DshIcons.Info, null, Modifier.size(13.dp), tint = c.ink3)
        Text(text, style = Type.meta, color = c.ink3, lineHeight = 16.sp)
    }
}

// =====================================================================
//  本地模型集群  S51 – S53
// =====================================================================

@Composable
fun S51LocalModel(onBack: () -> Unit, onGo: (Route) -> Unit) {
    val c = DshTheme.colors
    DshScreen {
        DshStatusBarSpacer()
        DshNavBar(
            title = "本地模型",
            onBack = onBack,
            actions = { DshIBtn(DshIcons.Add, "安装", onClick = { onGo(Route.S52LocalModelDownload) }) },
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            DshCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.size(34.dp).background(c.soft, RoundedCornerShape(Radius.md)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(DshIcons.Memory, null, Modifier.size(18.dp), tint = c.brand) }
                    Column(Modifier.weight(1f)) {
                        Text("运行时未加载", style = Type.rowTitle, color = c.ink)
                        Text("加载后常驻约 3.0 GB 内存", style = Type.sec, color = c.ink3, modifier = Modifier.padding(top = 2.dp))
                    }
                    DshChip("离线", tone = ChipTone.Warn, mini = true)
                }
            }
            DshSecTitle("已安装", note = "2 个 · 共 3.4 GB")
            DshCard {
                Column {
                    DshMRow(
                        title = "Qwen3-4B-Instruct",
                        subtitle = "2.7 GB · 实测约 9 tok/s · 4 线程",
                        icon = DshIcons.Psychology,
                        iconTone = ChipTone.Brand,
                        trailing = { DshChip("Q4_K_M", mini = true) },
                        onClick = { onGo(Route.S53LocalModelSettings) },
                    )
                    DshDivider()
                    DshMRow(
                        title = "Qwen3-1.7B",
                        subtitle = "0.7 GB · 实测约 22 tok/s · 适合摘要",
                        icon = DshIcons.Psychology,
                        iconTone = ChipTone.Neutral,
                        trailing = { DshChip("Q4_K_M", mini = true) },
                        onClick = { onGo(Route.S53LocalModelSettings) },
                        chevron = true,
                    )
                }
            }
            BorderedCard(c.warnSoft()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(DshIcons.Warning, null, Modifier.size(15.dp), tint = c.warn)
                    Text("别把它设成默认模型", style = Type.sec.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                }
                Text(
                    "4B 的工具调用（function calling）不稳定，驱动 Agent 会大量调用失败。它适合单轮生成，不适合多步工具编排。",
                    style = Type.sec, color = c.ink2, modifier = Modifier.padding(top = 7.dp), lineHeight = 17.sp,
                )
            }
            DshSecTitle("建议用途")
            DshCard {
                Column {
                    DshMRow(title = "隐私敏感处理", subtitle = "总结本地文档、翻译 · 不出设备", trailing = { DshChip("推荐", tone = ChipTone.Ok, mini = true) })
                    DshDivider()
                    DshMRow(title = "离线兜底", subtitle = "没网时仍可问答", trailing = { DshChip("推荐", tone = ChipTone.Ok, mini = true) })
                    DshDivider()
                    DshMRow(title = "会话标题 / 轨迹摘要", subtitle = "高频小活，不值得消耗云端额度", trailing = { DshChip("推荐", tone = ChipTone.Ok, mini = true) })
                    DshDivider()
                    DshMRow(title = "完整 Agent 任务", subtitle = "多步工具调用 · 请用云端模型", trailing = { DshChip("不建议", tone = ChipTone.Warn, mini = true) })
                }
            }
            HonestNote("演示数据 · 本机构建尚未接入本地模型运行时，加载 / 下载为占位交互")
            Spacer(Modifier.navigationBarsPadding())
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun S52LocalModelDownload(onBack: () -> Unit, onGo: (Route) -> Unit) {
    val c = DshTheme.colors
    var source by remember { mutableStateOf(0) }
    var quant by remember { mutableStateOf(0) }
    DshScreen {
        DshStatusBarSpacer()
        DshNavBar(title = "安装本地模型", leading = { DshIBtn(DshIcons.Close, "取消", onClick = onBack) })
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            DshCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Qwen3-4B-Instruct", style = Type.rowTitle.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                    DshChip("GGUF", tone = ChipTone.Brand, mini = true)
                }
                Text("通义千问 3 代 40 亿参数指令模型，支持思考 / 非思考模式切换。", style = Type.sec, color = c.ink2, modifier = Modifier.padding(top = 8.dp), lineHeight = 17.sp)
            }
            DshFieldGroup(label = "下载来源") {
                DshSeg(listOf("ModelScope", "HuggingFace"), source) { source = it }
                Text("国内网络优先 ModelScope，速度通常快数倍。", style = Type.meta, color = c.ink3, modifier = Modifier.padding(top = 6.dp))
            }
            DshFieldGroup(label = "量化档位", labelTrailing = { Text("越高质量越好，体积与内存越大", style = Type.meta, color = c.ink3) }) {
                DshCard {
                    Column {
                        DownloadQuantRow("Q4_K_M", "2.7 GB · 常驻约 3.0 GB 内存", selected = quant == 0) { quant = 0 }
                        DshDivider()
                        DownloadQuantRow("Q5_K_M", "3.1 GB · 质量略好，速度略慢", selected = quant == 1) { quant = 1 }
                        DshDivider()
                        DownloadQuantRow("Q8_0", "4.4 GB · 内存吃紧的机器慎选", selected = quant == 2) { quant = 2 }
                    }
                }
            }
            DshCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("下载进度", style = Type.sec.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                    Spacer(Modifier.weight(1f))
                    Text("58% · 1.6 / 2.7 GB", style = Type.meta.copy(fontWeight = FontWeight.SemiBold), color = c.ink3)
                }
                DshProgressBar(0.58f, Modifier.padding(top = 9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 9.dp)) {
                    DshChip("暂停", onClick = {})
                    DshChip("仅 Wi-Fi", onClick = {})
                    DshChip("校验", onClick = {})
                }
                Text("2.7 GB 在移动网络下中断概率高，断点续传与校验不是可选项。", style = Type.meta, color = c.ink3, modifier = Modifier.padding(top = 8.dp), lineHeight = 16.sp)
            }
            DshFieldGroup(label = "存储位置") {
                DshFinRow(
                    leading = { Icon(DshIcons.Storage, null, Modifier.size(15.dp), tint = c.ink3) },
                    trailing = { Icon(DshIcons.ChevronRight, null, Modifier.size(16.dp), tint = c.ink3) },
                ) {
                    Text("本机存储 · 启动较快", style = Type.sec, color = c.ink)
                }
                Text("放 NAS 每次启动要多加载几 GB，慢。建议本机存储，NAS 只做备份与分发。", style = Type.meta, color = c.ink3, modifier = Modifier.padding(top = 6.dp))
            }
            HonestNote("演示数据 · 下载为占位进度，未接入真实模型仓库")
            Spacer(Modifier.navigationBarsPadding())
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun DownloadQuantRow(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    val c = DshTheme.colors
    DshMRow(
        title = title,
        subtitle = sub,
        selected = selected,
        onClick = onClick,
        trailing = if (selected) {
            { Icon(DshIcons.CheckCircle, null, Modifier.size(19.dp), tint = c.brand) }
        } else null,
    )
}

@Composable
fun S53LocalModelSettings(onBack: () -> Unit, onGo: (Route) -> Unit) {
    val c = DshTheme.colors
    var threads by remember { mutableStateOf(1) }
    var ctx by remember { mutableStateOf(2) }
    var think by remember { mutableStateOf(false) }
    var resident by remember { mutableStateOf(true) }
    var lowBat by remember { mutableStateOf(true) }
    DshScreen {
        DshStatusBarSpacer()
        DshNavBar(
            title = "运行设置",
            onBack = onBack,
            actions = {
                Text(
                    "加载",
                    style = Type.btnSm,
                    color = c.brand,
                    modifier = Modifier
                        .padding(end = 14.dp)
                        .clickable { },
                )
            },
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            DshCard {
                Text("Qwen3-4B-Instruct · Q4_K_M", style = Type.rowTitle.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 9.dp)) {
                    Stat("9", "tok/s", "实测速度", modifier = Modifier.weight(1f))
                    Stat("3.0", "GB", "常驻内存", modifier = Modifier.weight(1f))
                    Stat("1.2", "s", "首字延迟", modifier = Modifier.weight(1f))
                }
            }
            DshFieldGroup(label = "推理线程", labelTrailing = { Text("本机 8 核", style = Type.meta, color = c.ink3) }) {
                DshSeg(listOf("2", "4", "6", "8"), threads) { threads = it }
                Text("超过物理大核数不会更快，反而抢温控。", style = Type.meta, color = c.ink3, modifier = Modifier.padding(top = 6.dp))
            }
            DshFieldGroup(label = "上下文长度") {
                DshSeg(listOf("4K", "8K", "16K", "32K"), ctx) { ctx = it }
                Text("上下文是内存大头。32K 时 4B 约需 4.5 GB，中端机容易 OOM。", style = Type.meta, color = c.ink3, modifier = Modifier.padding(top = 6.dp))
            }
            DshCard {
                Column {
                    SwitchRow("思考模式", "Qwen3 支持切换 · 开启后更慢但质量更好", think) { think = it }
                    DshDivider()
                    SwitchRow("常驻后台", "保持加载，省去重复加载时间", resident) { resident = it }
                    DshDivider()
                    SwitchRow("低电量时自动卸载", "低于 20% 释放内存", lowBat) { lowBat = it }
                }
            }
            BorderedCard(c.warnSoft()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(DshIcons.Rocket, null, Modifier.size(15.dp), tint = c.warn)
                    Text("性能预期", style = Type.sec.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                }
                Text("旗舰机 4B Q4 约 8–15 tok/s，中端机 3–6 tok/s。跑一段摘要要等十几秒，属于可接受；用来做多轮对话会很煎熬。", style = Type.sec, color = c.ink2, modifier = Modifier.padding(top = 7.dp), lineHeight = 17.sp)
            }
            HonestNote("演示数据 · 运行参数未接入本地模型运行时")
            Spacer(Modifier.navigationBarsPadding())
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun Stat(value: String, unit: String, label: String, modifier: Modifier = Modifier) {
    val c = DshTheme.colors
    Column(modifier) {
        Text(label, style = Type.meta, color = c.ink3)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = Type.title.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), color = c.ink)
            Text(" $unit", style = Type.sec, color = c.ink3)
        }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    DshMRow(
        title = title,
        subtitle = sub,
        onClick = { onChange(!checked) },
        trailing = { DshSwitch(checked, onChange = onChange) },
    )
}

// =====================================================================
//  遥控电脑集群  S54 – S60
// =====================================================================

/**
 * 内置演示数据：与 v23 设计稿 S54–S60 示例一致。
 * 当 NAS 未配置、或远端没有 host.json、或解析失败时，S54–S60 回落到此（并标注）。
 * 当 NAS 已配置且远端有数据，则改用 [RemoteHostLoader] 读到的真实宿主状态。
 */
private val SAMPLE_HOST = RemoteHost(
    online = true, name = "desktop", lockOwner = "desktop", lockTask = "重构订单模块",
    lockHeartbeatAt = 0L, lockTimeoutSec = 300, readOnly = false, heartbeatAt = 0L,
    tasks = listOf(
        RemoteTask("t3", "删除 37 个归档文件", "desktop · 整理下载目录 · rm -rf ~/Downloads/archive/*",
            "pending_approval", "desktop", waiting = "4 分钟"),
        RemoteTask("t1", "重构订单模块", "正在改 OrderService · 12 min · 38 步",
            "running", "desktop", progress = 0.62f,
            note = "当前：改写 OrderService.create() · 第 3 / 5 个文件",
            events = listOf(
                "" to "[21:33] tool fs_write  src/OrderService.ts",
                "ok" to "        +42 −18",
                "" to "[21:33] tool bash    npm test -- orders",
                "ok" to "        14 passed",
                "" to "[21:34] thinking  下一步处理退款分支…",
                "warn" to "[21:34] ▍",
            ), canHandoff = true, canPause = true),
        RemoteTask("t2", "总结今日纪要", "本机执行 · Qwen3-4B · 1 min 12s",
            "running", "local", progress = 0.78f,
            events = listOf(
                "" to "[21:41] 载入本地模型 Qwen3-4B",
                "" to "[21:41] 读取 3 份会议记录 · 12 KB",
                "ok" to "        未调用任何工具",
                "" to "[21:42] 生成摘要中… 78%",
            )),
        RemoteTask("t4", "批量重命名 240 张图", "预计 8 min · 超本机续航，已排队",
            "queued", "desktop", position = 1, submittedAt = "21:36"),
        RemoteTask("t5", "重跑失败用例并汇总", "第 2 位 · 21:37 提交",
            "queued", "desktop", position = 2, submittedAt = "21:37"),
    ),
)

/** 事件流色键 → 颜色（null 走默认 ink2）。 */
private fun evColor(c: DshColors, key: String): Color? = when (key) {
    "ok" -> c.ok
    "warn" -> c.warn
    "danger" -> c.danger
    else -> null
}

/** 读取远端宿主状态（NAS 已配置且远端有 host.json 时为真实数据，否则 null）。 */
@Composable
private fun rememberRemoteHost(): RemoteHost? {
    val ctx = LocalContext.current
    var host by remember { mutableStateOf<RemoteHost?>(null) }
    LaunchedEffect(Unit) {
        host = withContext(Dispatchers.IO) { RemoteHostLoader.load(ctx) }
    }
    return host
}

@Composable
fun S54HostOverview(onBack: () -> Unit, onGo: (Route) -> Unit) {
    val c = DshTheme.colors
    val hostData = rememberRemoteHost()
    val data = hostData ?: SAMPLE_HOST
    DshScreen {
        DshStatusBarSpacer()
        DshNavBar(
            title = "任务",
            leading = { DshIBtn(DshIcons.Menu, "菜单", onClick = onBack) },
            actions = { DshIBtn(DshIcons.Search, "搜索", onClick = { onGo(Route.S45GlobalSearch) }) },
        )
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.card2)
                .border(0.5.dp, c.line)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(7.dp).background(if (data.online) c.ok else c.ink3, RoundedCornerShape(50)))
            Text(if (data.online) "${data.name} 在线" else "${data.name} 离线", style = Type.sec.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
            if (data.lockOwner.isNotEmpty()) Text("· 锁在 ${data.lockOwner}", style = Type.sec, color = c.ink3)
            Spacer(Modifier.weight(1f))
            val hb = if (data.heartbeatAt > 0) ((System.currentTimeMillis() - data.heartbeatAt) / 1000).toInt().coerceAtLeast(0) else 12
            Text("心跳 ${hb}s 前", style = Type.meta, color = c.ink3)
            DshChip(if (data.readOnly) "本机只读" else "正常", mini = true, tone = if (data.readOnly) ChipTone.Warn else ChipTone.Ok, onClick = { onGo(Route.S58LockConflict) })
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            val approvals = data.tasks.filter { it.state == "pending_approval" }
            DshSecTitle("待审批", note = if (approvals.isEmpty()) "0" else "${approvals.size} · 已阻塞 4 分钟")
            if (approvals.isEmpty()) {
                DshCard { Text("暂无待审批任务", style = Type.sec, color = c.ink3, modifier = Modifier.padding(12.dp)) }
            } else {
                BorderedCard(c.warnSoft()) {
                    approvals.forEachIndexed { i, t ->
                        if (i > 0) DshDivider()
                        DshMRow(
                            title = t.title, subtitle = t.subtitle, icon = DshIcons.Shield, iconTone = ChipTone.Warn,
                            trailing = { DshChip("等待你", tone = ChipTone.Warn, mini = true) }, chevron = true, onClick = {},
                        )
                    }
                }
            }
            val running = data.tasks.filter { it.state == "running" }
            DshSecTitle("运行中", note = "${running.size}")
            DshCard {
                Column {
                    running.forEachIndexed { i, t ->
                        if (i > 0) DshDivider()
                        DshMRow(
                            title = t.title, subtitle = t.subtitle,
                            icon = if (t.hostTag == "local") DshIcons.Smartphone else DshIcons.Sync,
                            iconTone = if (t.hostTag == "local") ChipTone.Brand else ChipTone.Neutral,
                            trailing = { DshChip(if (t.hostTag == "local") "本机" else t.hostTag, tone = if (t.hostTag == "local") ChipTone.Brand else ChipTone.Neutral, mini = true) },
                            chevron = true,
                            onClick = { onGo(if (t.hostTag == "local") Route.S59LocalRun else Route.S55TaskMonitor) },
                        )
                    }
                }
            }
            val queued = data.tasks.filter { it.state == "queued" }
            DshSecTitle("队列", note = "${queued.size} · 电脑离线时自动续跑")
            DshCard {
                Column {
                    queued.forEachIndexed { i, t ->
                        if (i > 0) DshDivider()
                        DshMRow(
                            title = t.title, subtitle = "第 ${t.position} 位 · ${t.submittedAt} 提交",
                            icon = DshIcons.Schedule, iconTone = ChipTone.Neutral,
                            trailing = { DshChip("等待中", mini = true) }, chevron = true,
                            onClick = { onGo(Route.S57Queue) },
                        )
                    }
                }
            }
            DshCard {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(DshIcons.SwapHoriz, null, Modifier.size(15.dp), tint = c.brand)
                    Text("两端跑的是同一个任务", style = Type.sec.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                }
                Text("对话日志在 NAS 上，谁接手都从同一份历史继续。列表里标的是当前宿主，不是任务归属。", style = Type.sec, color = c.ink2, modifier = Modifier.padding(top = 7.dp), lineHeight = 17.sp)
            }
            if (hostData != null) {
                HonestNote("已连接 NAS 宿主 · 数据来自 dsh-phone/remote/host.json")
            } else {
                HonestNote("演示数据 · 未从 NAS 读取到宿主状态（未配置 NAS 或远端无 host.json）")
            }
            Spacer(Modifier.navigationBarsPadding())
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun S55TaskMonitor(onBack: () -> Unit, onGo: (Route) -> Unit) {
    val c = DshTheme.colors
    val hostData = rememberRemoteHost()
    val data = hostData ?: SAMPLE_HOST
    val task = data.tasks.firstOrNull { it.state == "running" && it.hostTag != "local" }
        ?: data.tasks.firstOrNull { it.state == "running" }
    DshScreen {
        DshStatusBarSpacer()
        DshNavBar(
            title = task?.title ?: "任务监视", onBack = onBack,
            actions = { DshIBtn(DshIcons.SwapHoriz, "转交", onClick = { onGo(Route.S56Handoff) }) },
        )
        Column(
            Modifier.fillMaxWidth().background(c.card2).border(0.5.dp, c.line).padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                DshChip("${task?.hostTag ?: "desktop"} 执行中", tone = ChipTone.Brand, mini = true)
                Text("12 min · 38 步 · 6 次工具调用", style = Type.meta, color = c.ink3)
            }
            DshProgressBar(task?.progress ?: 0.62f, Modifier.padding(top = 8.dp))
            if (task?.note?.isNotEmpty() == true)
                Text(task.note, style = Type.meta, color = c.ink3, modifier = Modifier.padding(top = 6.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            DshSecTitle("实时事件", note = "尾随 JSONL")
            TermLog(task?.events?.map { (k, l) -> evColor(c, k) to l } ?: emptyList())
            DshCard {
                Column {
                    DshMRow(title = "转交到本机", subtitle = "接管后续步骤，在手机上继续跑", icon = DshIcons.Smartphone, iconTone = ChipTone.Brand, chevron = true, onClick = { onGo(Route.S56Handoff) })
                    DshDivider()
                    DshMRow(title = "查看完整轨迹", subtitle = "38 个事件 · 可按来源筛选", icon = DshIcons.Timeline, iconTone = ChipTone.Neutral, chevron = true, onClick = { onGo(Route.S8Trajectory) })
                    DshDivider()
                    DshMRow(title = "请求暂停", subtitle = "当前步骤结束后暂停", icon = DshIcons.Sync, iconTone = ChipTone.Neutral, onClick = {})
                }
            }
            if (hostData == null) HonestNote("演示数据 · 未从 NAS 读取到宿主状态")
            Spacer(Modifier.navigationBarsPadding())
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun S56Handoff(onBack: () -> Unit, onGo: (Route) -> Unit) {
    val c = DshTheme.colors
    val hostData = rememberRemoteHost()
    val data = hostData ?: SAMPLE_HOST
    val task = data.tasks.firstOrNull { it.state == "running" && it.hostTag != "local" }
        ?: data.tasks.firstOrNull { it.state == "running" }
    var after by remember { mutableStateOf(0) }
    DshScreen {
        DshStatusBarSpacer()
        DshNavBar(title = "转交任务", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            BorderedCard(c.warnSoft()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(DshIcons.BatteryFull, null, Modifier.size(16.dp), tint = c.warn)
                    Text("本机不适合继续跑", style = Type.sec.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                }
                Column(Modifier.padding(top = 8.dp)) {
                    Text("· 已跑 9 分钟，预计还需 15 分钟以上", style = Type.sec, color = c.ink2, lineHeight = 17.sp)
                    Text("· 电量 18%，后台进程可能被系统回收", style = Type.sec, color = c.ink2, lineHeight = 17.sp)
                    Text("· 剩余步骤含大量文件读写，走网络盘会明显变慢", style = Type.sec, color = c.ink2, lineHeight = 17.sp)
                }
            }
            DshSecTitle("正在转交的任务")
            DshCard {
                DshMRow(title = task?.title ?: "重构订单模块", subtitle = task?.subtitle ?: "正在改 OrderService · 12 min · 38 步", icon = DshIcons.Sync, iconTone = ChipTone.Neutral)
            }
            DshSecTitle("转交给谁")
            DshCard {
                Column {
                    DshMRow(title = data.name, subtitle = "${data.name} · 预计 30 秒内接手", icon = DshIcons.Hub, iconTone = ChipTone.Ok, trailing = { DshChip("在线", tone = ChipTone.Ok, mini = true) }, onClick = {})
                    DshDivider()
                    DshMRow(title = "仅排队，等电脑在线", subtitle = "适合现在就要关掉手机的场景", icon = DshIcons.Schedule, iconTone = ChipTone.Neutral, chevron = true, onClick = { onGo(Route.S57Queue) })
                }
            }
            DshFieldGroup(label = "转交后本机做什么") {
                DshSeg(listOf("继续盯着", "仅通知结果", "不再关注"), after) { after = it }
            }
            DshCard {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(DshIcons.History, null, Modifier.size(15.dp), tint = c.brand)
                    Text("对话不会断", style = Type.sec.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                }
                Text("对话日志是追加写的，新宿主读全量历史即可重建上下文。你看到的是同一个会话，不是新建的。", style = Type.sec, color = c.ink2, modifier = Modifier.padding(top = 7.dp), lineHeight = 17.sp)
            }
            BorderedCard(c.dangerSoft()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(DshIcons.ErrorOutline, null, Modifier.size(15.dp), tint = c.danger)
                    Text("需你实测的一点", style = Type.sec.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                }
                Text("跨设备接管依赖 session 能被另一个进程完整重放。DSH 有 session 回滚 / 分叉插件说明它是可操作的，但跨设备接管是否原生顺滑，请在你的版本上先拿小任务验一次。", style = Type.sec, color = c.ink2, modifier = Modifier.padding(top = 7.dp), lineHeight = 17.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                DshBtn("继续在本机跑", tone = BtnTone.Ghost, modifier = Modifier.weight(1f), onClick = {})
                DshBtn("确认转交", modifier = Modifier.weight(1f), onClick = { onGo(Route.S54HostOverview) })
            }
            if (hostData == null) HonestNote("演示数据 · 转交/排队依赖 NAS 任务队列，本构建尚未接入")
            Spacer(Modifier.navigationBarsPadding())
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun S57Queue(onBack: () -> Unit, onGo: (Route) -> Unit) {
    val c = DshTheme.colors
    val hostData = rememberRemoteHost()
    val data = hostData ?: SAMPLE_HOST
    val queued = data.tasks.filter { it.state == "queued" }
    var notifyOnline by remember { mutableStateOf(true) }
    var notifyStale by remember { mutableStateOf(false) }
    DshScreen {
        DshStatusBarSpacer()
        DshNavBar(title = queued.firstOrNull()?.title ?: "排队等待", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Spacer(Modifier.height(14.dp))
            DshCard {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(DshIcons.History, null, Modifier.size(34.dp), tint = c.ink3)
                    Text("排队中 · 第 ${queued.firstOrNull()?.position ?: 1} 位", style = Type.rowTitle.copy(fontWeight = FontWeight.SemiBold), color = c.ink, modifier = Modifier.padding(top = 8.dp))
                    Text("等待 ${data.name} 在线后自动开始", style = Type.sec, color = c.ink3, modifier = Modifier.padding(top = 5.dp))
                    Text("队列存在 NAS 上，不依赖本机在线", style = Type.meta, color = c.ink3, modifier = Modifier.padding(top = 9.dp), lineHeight = 16.sp)
                }
            }
            DshCard {
                Column {
                    DshMRow(title = "电脑上线时提醒我", subtitle = "任务开始时推一条通知", onClick = { notifyOnline = !notifyOnline }, trailing = { DshSwitch(notifyOnline) { notifyOnline = it } })
                    DshDivider()
                    DshMRow(title = "电脑离线超过 1 天则提醒我复查", subtitle = "避免任务无限期挂着", onClick = { notifyStale = !notifyStale }, trailing = { DshSwitch(notifyStale) { notifyStale = it } })
                }
            }
            DshSecTitle("已排队的任务", note = "${queued.size}")
            DshCard {
                Column {
                    queued.forEachIndexed { i, t ->
                        if (i > 0) DshDivider()
                        DshMRow(title = t.title, subtitle = "第 ${t.position} 位 · ${t.submittedAt} 提交", trailing = { DshChip("等待中", mini = true) })
                    }
                }
            }
            DshCard {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(DshIcons.Warning, null, Modifier.size(15.dp), tint = c.brand)
                    Text("要不要在手机上硬跑？", style = Type.sec.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                }
                Text("不建议。这个任务要读写 240 个文件，走网络盘会非常慢，而且大概率跑不完就被系统回收了。", style = Type.sec, color = c.ink2, modifier = Modifier.padding(top = 7.dp), lineHeight = 17.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.padding(top = 9.dp)) {
                    DshBtn("留在队列", tone = BtnTone.Ghost, modifier = Modifier.weight(1f), onClick = {})
                    DshBtn("改主意", modifier = Modifier.weight(1f), onClick = { onGo(Route.S56Handoff) })
                }
            }
            if (hostData == null) HonestNote("演示数据 · 队列依赖 NAS，本构建尚未接入")
            Spacer(Modifier.navigationBarsPadding())
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun S58LockConflict(onBack: () -> Unit, onGo: (Route) -> Unit) {
    val c = DshTheme.colors
    val hostData = rememberRemoteHost()
    val data = hostData ?: SAMPLE_HOST
    DshScreen {
        DshStatusBarSpacer()
        DshNavBar(title = "工作区被占用", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Spacer(Modifier.height(14.dp))
            BorderedCard(c.warnSoft()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(DshIcons.Lock, null, Modifier.size(17.dp), tint = c.warn)
                    Text("${data.lockOwner.ifEmpty { data.name }} 正在执行", style = Type.sec.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                }
                Text("owner: ${data.lockOwner.ifEmpty { data.name }} · task: ${data.lockTask.ifEmpty { "未知" }}", style = Type.mono.copy(fontSize = 10.5.sp), color = c.ink2, modifier = Modifier.padding(top = 9.dp))
                Text("heartbeat: 12s 前 · 超时 ${data.lockTimeoutSec / 60} 分钟自动释放", style = Type.mono.copy(fontSize = 10.5.sp), color = c.ink2)
            }
            DshSecTitle("你现在可以")
            DshCard {
                Column {
                    DshMRow(title = "只读查看", subtitle = "看进度、看轨迹、看 diff，不写入", icon = DshIcons.Visibility, iconTone = ChipTone.Ok, trailing = { DshChip("推荐", tone = ChipTone.Ok, mini = true) }, onClick = {})
                    DshDivider()
                    DshMRow(title = "排队，等它跑完", subtitle = "desktop 释放锁后自动接手", icon = DshIcons.Schedule, iconTone = ChipTone.Neutral, chevron = true, onClick = { onGo(Route.S57Queue) })
                    DshDivider()
                    DshMRow(title = "请求对方交出控制权", subtitle = "对方同意后再接管", icon = DshIcons.Hub, iconTone = ChipTone.Neutral, onClick = {})
                }
            }
            if (hostData == null) HonestNote("演示数据 · 锁状态依赖 NAS 宿主，本构建尚未接入")
            Spacer(Modifier.navigationBarsPadding())
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun S59LocalRun(onBack: () -> Unit, onGo: (Route) -> Unit) {
    val c = DshTheme.colors
    val hostData = rememberRemoteHost()
    val data = hostData ?: SAMPLE_HOST
    val task = data.tasks.firstOrNull { it.state == "running" && it.hostTag == "local" }
        ?: data.tasks.firstOrNull { it.hostTag == "local" }
    DshScreen {
        DshStatusBarSpacer()
        DshNavBar(title = task?.title ?: "本机运行", onBack = onBack, actions = { DshIBtn(DshIcons.SwapHoriz, "转交", onClick = { onGo(Route.S56Handoff) }) })
        Column(Modifier.fillMaxWidth().background(c.card2).border(0.5.dp, c.line).padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                DshChip("本机执行", tone = ChipTone.Brand, mini = true)
                Text("Qwen3-4B · 1 min 12s · 无工具调用", style = Type.meta, color = c.ink3)
            }
            DshProgressBar(task?.progress ?: 0.78f, Modifier.padding(top = 8.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            DshCard {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(DshIcons.BatteryFull, null, Modifier.size(15.dp), tint = c.ok)
                    Text("本机状态良好", style = Type.sec.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                    MiniStat("电量", "82%")
                    MiniStat("内存", "2.9 / 8 GB")
                    MiniStat("网络", "Wi-Fi")
                    MiniStat("唤醒锁", "已持有")
                }
            }
            TermLog(task?.events?.map { (k, l) -> evColor(c, k) to l } ?: emptyList())
            DshCard {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(DshIcons.Rocket, null, Modifier.size(15.dp), tint = c.brand)
                    Text("本机适合跑什么", style = Type.sec.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                }
                Text("单轮摘要、翻译、隐私文档处理。不适合多步工具编排——4B 的 function calling 不稳。", style = Type.sec, color = c.ink2, modifier = Modifier.padding(top = 7.dp), lineHeight = 17.sp)
            }
            if (hostData == null) HonestNote("演示数据 · 本机运行为占位，未接入本地模型运行时")
            Spacer(Modifier.navigationBarsPadding())
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun S60Takeover(onBack: () -> Unit, onGo: (Route) -> Unit) {
    val c = DshTheme.colors
    val hostData = rememberRemoteHost()
    val data = hostData ?: SAMPLE_HOST
    DshScreen {
        DshStatusBarSpacer()
        DshNavBar(title = "接管任务", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            DshCard {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(DshIcons.Hub, null, Modifier.size(16.dp), tint = c.brand)
                    Text("${data.lockOwner.ifEmpty { data.name }} 正在跑：${data.lockTask.ifEmpty { "未知任务" }}", style = Type.sec.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                }
                Text("已跑 12 分钟 · 38 步 · 当前改到第 3 / 5 个文件", style = Type.sec, color = c.ink2, modifier = Modifier.padding(top = 8.dp), lineHeight = 17.sp)
            }
            DshSecTitle("接管后能续上")
            DshCard {
                Column {
                    TakeoverRow("对话历史", "38 个事件全在 NAS 上，可完整重放", ok = true)
                    DshDivider()
                    TakeoverRow("工作区文件", "已写入的改动都在，diff 完整", ok = true)
                    DshDivider()
                    TakeoverRow("当前任务目标", "从最后一条指令继续", ok = true)
                }
            }
            DshSecTitle("接管后续不上")
            DshCard {
                Column {
                    TakeoverRow("正在执行的命令", "活进程在电脑内存里，跟不过来", ok = false)
                    DshDivider()
                    TakeoverRow("后台服务与端口", "npm run dev 之类的需要重开", ok = false)
                    DshDivider()
                    TakeoverRow("环境变量与登录态", "需在本机重新配置", ok = false)
                }
            }
            BorderedCard(c.warnSoft()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(DshIcons.Warning, null, Modifier.size(15.dp), tint = c.warn)
                    Text("走网络盘会很慢", style = Type.sec.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                }
                Text("本机的「工作区」实际是 NAS 目录，大量小文件读写和搜索会明显变慢。接管前先确认这个任务是不是文件密集型的。", style = Type.sec, color = c.ink2, modifier = Modifier.padding(top = 7.dp), lineHeight = 17.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                DshBtn("放弃", tone = BtnTone.Ghost, modifier = Modifier.weight(1f), onClick = onBack)
                DshBtn("请求接管", modifier = Modifier.weight(1f), onClick = { onGo(Route.S59LocalRun) })
            }
            if (hostData == null) HonestNote("演示数据 · 接管依赖 NAS 宿主状态，本构建尚未接入")
            Spacer(Modifier.navigationBarsPadding())
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    val c = DshTheme.colors
    Column {
        Text(label, style = Type.meta, color = c.ink3)
        Text(value, style = Type.sec.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
    }
}

@Composable
private fun TakeoverRow(title: String, sub: String, ok: Boolean) {
    val c = DshTheme.colors
    DshMRow(
        title = title,
        subtitle = sub,
        trailing = { Icon(if (ok) DshIcons.CheckCircle else DshIcons.Close, null, Modifier.size(19.dp), tint = if (ok) c.ok else c.danger) },
    )
}
