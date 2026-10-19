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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.harness.SafConfig
import com.dsh.harness.SafProxyServer
import com.dsh.harness.mobile.ui.components.*
import com.dsh.harness.mobile.ui.theme.*

/**
 * S50 存储目录（v30 新增 · SAF 桥方案 A）
 *
 * 取代 v29 删掉的「NAS 连接」整套假页面。本 harness 无 NAS 同步后端，
 * 外部目录挂载走 Android SAF（存储访问框架）：用户在系统选择器里授权一个
 * 文件夹，dsh 通过 [SafTree]/[SafProxyServer] 读写它（详见 [SafConfig] 注释）。
 *
 * 这里只呈现**真实状态**——[SafConfig] 的持久化授权与 [SafProxyServer] 的运行态，
 * 不编造任何连接 / 同步数据。
 */
@Composable
fun S50StorageDir(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    /** 拉起系统目录选择器（由 DshApp 注册的 OpenDocumentTree launcher 实现） */
    onPick: () -> Unit = {},
    /** 挂载 / 卸载后 DshApp 自增，触发本屏重读 SafConfig */
    refreshTick: Int = 0,
) {
    val c = DshTheme.colors
    val ctx = LocalContext.current.applicationContext
    // 卸载后本屏需要立即重读——DshApp 的 refreshTick 管不到这里，加一个本地 tick 兜底
    var localTick by remember { mutableStateOf(0) }

    // SafConfig 不是可观察状态，每次 tick 变化都重新读
    val configured = remember(refreshTick, localTick) { SafConfig.isConfigured(ctx) }
    val name = remember(refreshTick, localTick) { SafConfig.displayName(ctx) }
    val mount = remember(refreshTick, localTick) { SafConfig.mountDir(ctx).absolutePath }
    val running = remember(refreshTick, localTick) { SafProxyServer.isRunning() }
    val port = remember(refreshTick, localTick) { SafProxyServer.port() }

    DshScreen(modifier) {
        DshNavBar(
            onBack = onBack,
            title = "存储目录",
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

            // —— 状态卡：只反映真实授权 / 运行态 ——
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(15.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (configured) DshIcons.FolderOpen else DshIcons.CloudOff,
                        null,
                        Modifier.size(18.dp),
                        tint = if (configured) c.brand else c.ink3,
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        if (configured) "已挂载外部存储" else "未挂载外部存储",
                        style = Type.secTitle,
                        color = c.ink,
                    )
                    Spacer(Modifier.weight(1f))
                    DshChip(
                        if (configured) "已授权" else "未授权",
                        tone = if (configured) ChipTone.Ok else ChipTone.Warn,
                        mini = true,
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (configured) {
                    S50InfoRow("目录名", name)
                    S50InfoRow("挂载点", mount, mono = true)
                    S50InfoRow(
                        "本地代理",
                        if (running) "运行中 · :$port" else "未运行",
                        mono = true,
                    )
                    Text(
                        "dsh 通过本地代理读写该目录；授权跨进程重启有效，可在系统设置里撤销。",
                        style = Type.meta.copy(lineHeight = 16.8.sp),
                        color = c.ink3,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Text(
                        "把手机里的任意文件夹挂到 dsh 工作区。通过系统「选择目录」授权，" +
                            "dsh 即可读写其中的文件——用于跨 App 共享资料，无需 NAS 后端。",
                        style = Type.meta.copy(lineHeight = 16.8.sp),
                        color = c.ink3,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // —— 动作 ——
            if (configured) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    DshBtn(
                        "更换目录",
                        icon = DshIcons.FolderOpen,
                        modifier = Modifier.weight(1f),
                        onClick = onPick,
                    )
                    DshBtn(
                        "卸载",
                        icon = DshIcons.CloudOff,
                        tone = BtnTone.Ghost,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            SafConfig.clear(ctx)
                            SafProxyServer.stop()
                            localTick++
                        },
                    )
                }
            } else {
                DshBtn(
                    "选择目录",
                    icon = DshIcons.Add,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onPick,
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

/** S50 信息行（标签 64px + 内容；内容过长省略号） */
@Composable
private fun S50InfoRow(label: String, value: String, mono: Boolean = false) {
    val c = DshTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Type.sec.copy(color = c.ink3), modifier = Modifier.width(64.dp))
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
