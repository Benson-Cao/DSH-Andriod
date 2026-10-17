package com.dsh.harness.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.harness.mobile.ui.components.*
import com.dsh.harness.mobile.ui.theme.*

/**
 * S27 Termux 终端 · 本机启动 dsh（v14 新增）
 *
 * 全稿唯一按 Android 宿主视角画的一屏：状态栏与终端配色**故意脱离 App 主题**
 * （深色等宽），其余仍回落 App 主题卡片。命令与约束取自官方运行方式：
 *   - npx @deepseek-ai/dsh web 拉起 Web UI（默认本机访问，--public-url 才外放）
 *   - 端口占用用 --port 改端口
 *   - --profile headless 一次性批处理，跑完即退不留常驻进程
 *   - 进程守护 / termux-wake-lock 说明长任务在 Android 上不被系统回收的前提
 *
 * 职责划清：Termux 只起服务和看日志，任务进展与轨迹回到 Harness 界面。
 * 版本号 / 端口号 / 路径为示意占位。
 */
@Composable
fun S27Termux(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenUi: () -> Unit,
    onStop: () -> Unit,
    onExportLog: () -> Unit,
    onCopyCommand: (String) -> Unit = {},
) {
    val c = DshTheme.colors
    val termNavBg = Color(0xFF0B0F14)
    val termTitle = Color(0xFFE7EDF5)
    val termIcon = Color(0xFFC9D6E4)
    val termBg = Color(0xFF0B0F14)
    val termFg = Color(0xFFC9D6E4)
    val termPrompt = Color(0xFF7A8AA0)
    val termOk = Color(0xFF7FE58A)
    val termKey = Color(0xFF4D6BFE)
    val termWarn = Color(0xFFC97A00)
    val termErr = Color(0xFFE02E3A)
    val termPath = Color(0xFFA9B7FF)

    DshScreen(modifier) {
        // —— 深色顶栏（脱离 App 主题）——
        Row(
            Modifier
                .fillMaxWidth()
                .height(Metrics.NAV_H)
                .background(termNavBg)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DshIBtn(DshIcons.Menu, "返回", tint = termIcon, onClick = onBack)
            Text("Termux", style = Type.title, color = termTitle, modifier = Modifier.weight(1f))
            // bash 标签
            Row(
                Modifier
                    .height(22.dp)
                    .background(Color(0xFF16202C), RoundedCornerShape(11.dp))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("bash", style = Type.meta, color = termOk)
            }
            DshIBtn(DshIcons.Add, "新建会话", tint = termIcon, onClick = {})
            DshIBtn(DshIcons.MoreVert, "更多", tint = termIcon, onClick = {})
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg)
                .padding(top = 10.dp),
        ) {
            // —— 终端输出区（深色等宽）——
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(termBg)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(12.dp),
            ) {
                TERMINAL_LINES.forEach { line ->
                    Text(
                        buildAnnotatedString {
                            line.forEach { (t, tone) ->
                                val col = when (tone) {
                                    TermTone.FG -> termFg
                                    TermTone.PROMPT -> termPrompt
                                    TermTone.OK -> termOk
                                    TermTone.KEY -> termKey
                                    TermTone.WARN -> termWarn
                                    TermTone.ERR -> termErr
                                    TermTone.PATH -> termPath
                                }
                                withStyle(SpanStyle(color = col)) { append(t) }
                            }
                        },
                        style = Type.mono.copy(fontSize = 12.sp, lineHeight = 18.sp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // —— 快捷指令横滑 ——
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                TERMINAL_QUICK.forEach { cmd ->
                    DshChip(cmd, mini = true, onClick = { onCopyCommand(cmd) })
                }
            }

            // —— 本机服务状态卡 ——
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(13.dp)
                    .padding(top = 4.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(DshIcons.Terminal, null, Modifier.size(17.dp), tint = c.brand)
                    Text("本机服务状态", style = Type.secTitle, color = c.ink, modifier = Modifier.weight(1f))
                    DshChip(":3081 运行中", tone = ChipTone.Ok, mini = true)
                }
                TermRow("工作区", "~/storage/shared/Documents/dsh-ws", true)
                TermRow("会话存储", "~/.dsh/sessions · SQLite", false)
                TermRow("进程守护", "需通知常驻 + 唤醒锁，息屏后长任务才不被回收", false)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("日志", style = Type.sec.copy(color = c.ink3), modifier = Modifier.width(72.dp))
                    Text("~/.dsh/logs/web.log", style = Type.mono.copy(fontSize = 11.5.sp), color = c.ink2, modifier = Modifier.weight(1f))
                    Text(
                        "导出",
                        style = Type.sec,
                        color = c.brand,
                        modifier = Modifier.clickable { onExportLog() },
                    )
                }
            }

            // —— 打开 UI / 停止 ——
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                DshBtn(
                    "在浏览器打开 UI",
                    icon = DshIcons.OpenInNew,
                    modifier = Modifier.weight(1.6f),
                    onClick = onOpenUi,
                )
                DshBtn(
                    "停止",
                    tone = BtnTone.Ghost,
                    modifier = Modifier.weight(1f),
                    onClick = onStop,
                )
            }

            Text(
                "Termux 侧只负责起服务和看日志，任务与轨迹仍在 Harness 界面里看；headless 适合一次性跑完就退出的批处理。",
                style = Type.meta.copy(lineHeight = 16.8.sp),
                color = c.ink3,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
            )
        }
    }
}

/** 终端状态行（标签 72px + 内容） */
@Composable
private fun TermRow(label: String, value: String, mono: Boolean) {
    val c = DshTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Type.sec.copy(color = c.ink3), modifier = Modifier.width(72.dp))
        Text(
            value,
            style = if (mono) Type.mono.copy(fontSize = 11.5.sp) else Type.sec,
            color = c.ink2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 终端色调：普通 / 提示符$ / 成功 / [dsh]键 / 警告 / 错误 / 路径高亮 */
private enum class TermTone { FG, PROMPT, OK, KEY, WARN, ERR, PATH }

/** 一行 = 若干带色调的文本段 */
private typealias Seg = Pair<String, TermTone>

/** S27 终端输出（示意占位，对应设计稿 12 行） */
private val TERMINAL_LINES: List<List<Seg>> = listOf(
    listOf(Seg("$ ", TermTone.PROMPT), Seg("node -v", TermTone.FG)),
    listOf(Seg("v22.11.0", TermTone.OK)),
    listOf(Seg("$ ", TermTone.PROMPT), Seg("npx @deepseek-ai/dsh web", TermTone.FG)),
    listOf(Seg("[dsh] ", TermTone.KEY), Seg("profile \"web\" 首次启动，已按内置模板初始化", TermTone.FG)),
    listOf(Seg("[dsh] ", TermTone.KEY), Seg("载入 bundle：llm · session · agent · api-gateway · tools · sandbox · ui", TermTone.FG)),
    listOf(
        Seg("[dsh] ", TermTone.KEY), Seg("Web UI 已启动", TermTone.FG),
        Seg("  http://localhost:3080", TermTone.PATH),
    ),
    listOf(
        Seg("提示", TermTone.WARN), Seg("  默认仅本机可访问；要从别的设备打开需指定 ", TermTone.FG),
        Seg("--public-url", TermTone.PATH),
    ),
    listOf(Seg("$ ", TermTone.PROMPT), Seg("dsh web --port 3081", TermTone.FG)),
    listOf(
        Seg("✗ 端口 3080 已被占用", TermTone.ERR),
        Seg("，改用 3081 重试 → 启动成功", TermTone.FG),
    ),
    listOf(
        Seg("$ ", TermTone.PROMPT),
        Seg("dsh --profile headless ", TermTone.FG),
        Seg("\"把 report.md 的季度数据画成柱状图\"", TermTone.PATH),
    ),
    listOf(Seg("[done] ", TermTone.OK), Seg("输出结果后退出，不留常驻进程", TermTone.FG)),
    listOf(Seg("$ ", TermTone.PROMPT), Seg("▎", TermTone.PATH)),
)

/** 快捷指令（横滑） */
private val TERMINAL_QUICK = listOf(
    "node -v", "dsh web", "--port 3081", "headless", "termux-wake-lock", "termux-setup-storage",
)
