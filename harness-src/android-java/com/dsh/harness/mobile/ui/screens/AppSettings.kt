package com.dsh.harness.mobile.ui.screens

import android.app.Activity
import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.harness.DshBootstrap
import com.dsh.harness.DshProcessManager
import com.dsh.harness.SafConfig
import com.dsh.harness.SafProxyServer
import com.dsh.harness.mobile.data.DshPermissionCatalog
import com.dsh.harness.mobile.data.DshPluginCatalog
import com.dsh.harness.mobile.data.DshPluginPatch
import com.dsh.harness.mobile.data.DshPresets
import com.dsh.harness.mobile.data.DshSettingsStore
import com.dsh.harness.mobile.ui.theme.DshTheme
import com.dsh.harness.mobile.ui.theme.DshWidth
import com.dsh.harness.mobile.ui.theme.ThemePref
import com.dsh.harness.mobile.ui.theme.Radius
import com.dsh.harness.mobile.ui.theme.Space
import com.dsh.harness.mobile.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/* ==========================================================================
 * v31.2 · 设置页 —— 全新响应式布局（对照 dsh web 网页设置面板的内容范围重写）
 *
 * 设计目标（用户明确要求：不参考旧布局，从零设计）：
 *   · 响应式 —— Compact(<600dp) 单栏「大标题 + 分区列表 → 详情推入」；
 *     Medium/Expanded(≥600dp) 双栏「左导航常驻 + 右详情」。
 *   · 内容对齐网页设置面板的四个分区：通用设置 / 模型 / 内置插件 / Agent 预设
 *     （数据通道 = dsh settings 服务的 WS RPC；Compose 侧暂无轻量只读通道，
 *      分区详情降级为「说明 + 一键直达网页面板」，见 SettingsBridge 协议考古注释。）
 *   · App 本体设置（引擎 / 存储 / 关于）收进独立分区，与网页设置分开。
 *   · 视觉从零：超大标题、状态英雄卡、分段控件、悬浮摘要 —— 不复用旧 NavBar /
 *     DshScreen / DshSecTitle 组件。
 * ========================================================================== */

/** 设置分区。 */
private enum class Section(val title: String) {
    Account("账户"),
    General("通用设置"),
    Models("模型"),
    Plugins("内置插件"),
    Presets("Agent 预设"),
    Storage("存储"),
    About("关于"),
}

@Composable
fun AppSettingsScreen(onClose: () -> Unit) {
    val c = DshTheme.colors
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()

    // ---- 引擎状态 ----
    var engineUp by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(true) }
    fun refreshEngine() {
        scope.launch {
            checking = true
            engineUp = withContext(Dispatchers.IO) {
                try { DshProcessManager.isPortOpen(DshProcessManager.DSH_PORT) } catch (_: Throwable) { false }
            }
            checking = false
        }
    }
    LaunchedEffect(Unit) { refreshEngine() }
    // v33 诊断：设置页打开时把 settings/describe 的真实响应落盘到
    // files/dsh-settings-dump.json（离线核对字段名用；失败静默，不影响 UI）。
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try { com.dsh.harness.mobile.data.DshSettingsApi.dumpDescribe(appCtx) } catch (_: Throwable) { }
        }
    }

    // ---- SAF 快照 ----
    var safOn by remember { mutableStateOf(SafConfig.isConfigured(appCtx)) }
    var safName by remember { mutableStateOf(SafConfig.displayName(appCtx)) }
    val safLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try { appCtx.contentResolver.takePersistableUriPermission(uri, flags) } catch (_: Throwable) { }
            val id = try { DocumentsContract.getTreeDocumentId(uri) } catch (_: Throwable) { null }
            val pickName = id?.substringAfterLast(':')?.substringAfterLast('/')
                ?.takeIf { it.isNotBlank() } ?: "外部存储"
            SafConfig.save(appCtx, uri, pickName)
            SafProxyServer.start(appCtx)
        }
        safOn = SafConfig.isConfigured(appCtx)
        safName = SafConfig.displayName(appCtx)
    }
    fun clearSaf() {
        try { SafConfig.clear(appCtx) } catch (_: Throwable) { }
        safOn = SafConfig.isConfigured(appCtx)
        safName = SafConfig.displayName(appCtx)
    }

    // ---- 对话框 ----
    var confirmRestart by remember { mutableStateOf(false) }
    var restarting by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var logText by remember { mutableStateOf("（读取中…）") }

    // ---- dsh 设置分区（自 r154 起全部 Compose 原生化，不再有网页落地页）----
    // v33：四个分区不再深链网页，全部原生 Compose 渲染。
    //   真实数据来自 dsh 设置服务的一元 RPC（settings/describe / settings/update），
    //   见 DshSettingsApi 的通道取证注释；写入走 DshSettingsStore。


    // ---- 导航 ----
    var current by remember { mutableStateOf<Section?>(null) }
    val twoPane = DshTheme.width != DshWidth.Compact

    if (twoPane) {
        BackHandler { onClose() }
        TwoPaneLayout(
            selected = current ?: Section.General,
            onSelect = { section -> current = section },
            onClose = onClose,
            engineUp = engineUp, checking = checking, onRefreshEngine = { refreshEngine() },
            safOn = safOn, safName = safName,
            onPickSaf = { safLauncher.launch(null) }, onClearSaf = { clearSaf() },
            onConfirmRestart = { confirmRestart = true },
            onShowLog = { loadLog { text -> logText = text; showLog = true } },
        )
    } else {
        BackHandler(enabled = current != null) { current = null }
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                if (targetState != null) {
                    (slideInHorizontally(tween(220)) { it / 3 } + fadeIn(tween(220)))
                        .togetherWith(slideOutHorizontally(tween(220)) { -it / 4 } + fadeOut(tween(160)))
                } else {
                    (slideInHorizontally(tween(220)) { -it / 4 } + fadeIn(tween(220)))
                        .togetherWith(slideOutHorizontally(tween(220)) { it / 3 } + fadeOut(tween(160)))
                }
            },
            label = "settings-nav",
        ) { section ->
            if (section == null) {
                HomePane(
                    onOpen = { opened -> current = opened },
                    onClose = onClose,
                    engineUp = engineUp, checking = checking, onRefreshEngine = { refreshEngine() },
                    safOn = safOn, safName = safName,
                )
            } else {
                DetailPane(
                    section = section,
                    onBack = { current = null },
                    safOn = safOn, safName = safName,
                    onPickSaf = { safLauncher.launch(null) }, onClearSaf = { clearSaf() },
                    onConfirmRestart = { confirmRestart = true },
                    onShowLog = { loadLog { text -> logText = text; showLog = true } },
                )
            }
        }
    }

    // ---- 对话框（全局）----
    if (confirmRestart) {
        ConfirmDialog(
            title = "重启引擎",
            body = "重启 dsh 服务进程，进行中的会话连接会中断。确定继续？",
            confirmText = "重启",
            danger = true,
            onDismiss = { confirmRestart = false },
        ) {
            confirmRestart = false
            restarting = true
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        DshProcessManager.restart(appCtx)
                        repeat(25) {
                            delay(1000)
                            if (DshProcessManager.isPortOpen(DshProcessManager.DSH_PORT)) return@withContext true
                        }
                        false
                    } catch (_: Throwable) { false }
                }
                restarting = false
                engineUp = ok
            }
        }
    }
    if (restarting) {
        AlertDialog(
            onDismissRequest = { },
            confirmButton = { },
            title = { Text("正在重启引擎…", style = Type.title, color = c.ink) },
            text = { Text("等待端口 ${DshProcessManager.DSH_PORT} 重新就绪（最多 25 秒）", style = Type.body, color = c.ink2) },
        )
    }
    if (showLog) {
        AlertDialog(
            onDismissRequest = { showLog = false },
            confirmButton = { TextButton(onClick = { showLog = false }) { Text("关闭", color = c.brand) } },
            title = { Text("dsh-web.log · 最后 40 行", style = Type.title, color = c.ink) },
            text = {
                Text(
                    logText, style = Type.mono, color = c.ink2,
                    modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 380.dp),
                )
            },
        )
    }
}

private fun loadLog(onLoaded: (String) -> Unit) {
    Thread {
        val text = try {
            val appCtx = com.dsh.harness.MainActivity.sInstance?.get()?.applicationContext
                ?: return@Thread onLoaded("（App 不在前台）")
            DshProcessManager.tailLog(appCtx, 40).ifBlank { "（日志为空或尚无输出）" }
        } catch (_: Throwable) { "（日志读取失败）" }
        android.os.Handler(android.os.Looper.getMainLooper()).post { onLoaded(text) }
    }.start()
}

// ============================== 布局骨架 ==============================

@Composable
private fun TwoPaneLayout(
    selected: Section,
    onSelect: (Section) -> Unit,
    onClose: () -> Unit,
    engineUp: Boolean, checking: Boolean, onRefreshEngine: () -> Unit,
    safOn: Boolean, safName: String, onPickSaf: () -> Unit, onClearSaf: () -> Unit,
    onConfirmRestart: () -> Unit,
    onShowLog: () -> Unit,
) {
    val c = DshTheme.colors
    Row(Modifier.fillMaxSize().background(c.bg)) {
        Column(
            Modifier.width(300.dp).fillMaxHeight().background(c.card)
                .statusBarsPadding().navigationBarsPadding(),
        ) {
            LargeHeader(
                title = "设置", subtitle = null,
                showBack = true, onBack = onClose, onRefresh = onRefreshEngine,
            )
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.lg)) {
                SectionNavList(
                    selected = selected,
                    onSelect = onSelect,
                    safOn = safOn, safName = safName,
                )
                Spacer(Modifier.height(Space.xl))
            }
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().background(c.bg)
                .statusBarsPadding().navigationBarsPadding(),
        ) {
            DetailBody(
                section = selected,
                safOn = safOn, safName = safName, onPickSaf = onPickSaf, onClearSaf = onClearSaf,
                onConfirmRestart = onConfirmRestart, onShowLog = onShowLog,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HomePane(
    onOpen: (Section) -> Unit,
    onClose: () -> Unit,
    engineUp: Boolean, checking: Boolean, onRefreshEngine: () -> Unit,
    safOn: Boolean, safName: String,
) {
    val c = DshTheme.colors
    Column(
        Modifier.fillMaxSize().background(c.bg)
            .statusBarsPadding().navigationBarsPadding().imePadding(),
    ) {
        LargeHeader(
            title = "设置",
            subtitle = when {
                checking -> "正在检查服务…"
                engineUp -> "服务运行中 · 端口 ${DshProcessManager.DSH_PORT}"
                else -> "服务未运行"
            },
            statusUp = engineUp, statusChecking = checking,
            showBack = true, onBack = onClose, onRefresh = onRefreshEngine,
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.lg),
        ) {
            HeroEngineCard(engineUp = engineUp, checking = checking)
            Spacer(Modifier.height(Space.xl))
            SectionNavList(
                selected = null,
                onSelect = onOpen,
                safOn = safOn, safName = safName,
            )
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

@Composable
private fun DetailPane(
    section: Section,
    onBack: () -> Unit,
    safOn: Boolean, safName: String, onPickSaf: () -> Unit, onClearSaf: () -> Unit,
    onConfirmRestart: () -> Unit,
    onShowLog: () -> Unit,
) {
    val c = DshTheme.colors
    Column(
        Modifier.fillMaxSize().background(c.bg)
            .statusBarsPadding().navigationBarsPadding().imePadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackChip(onBack)
            Spacer(Modifier.width(Space.md))
            Text(section.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.ink)
        }
        DetailBody(
            section = section,
            safOn = safOn, safName = safName, onPickSaf = onPickSaf, onClearSaf = onClearSaf,
            onConfirmRestart = onConfirmRestart, onShowLog = onShowLog,
            modifier = Modifier.weight(1f),
        )
    }
}

// ============================== 通用组件（全新） ==============================

@Composable
private fun BackChip(onBack: () -> Unit) {
    val c = DshTheme.colors
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(c.card).clickable { onBack() },
        contentAlignment = Alignment.Center,
    ) { Text("‹", fontSize = 24.sp, color = c.ink, fontWeight = FontWeight.Bold) }
}

@Composable
private fun LargeHeader(
    title: String,
    subtitle: String?,
    statusUp: Boolean? = null,
    statusChecking: Boolean = false,
    showBack: Boolean,
    onBack: (() -> Unit)?,
    onRefresh: (() -> Unit)?,
) {
    val c = DshTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = Space.lg, end = Space.lg, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack && onBack != null) {
            BackChip(onBack)
            Spacer(Modifier.width(Space.md))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = c.ink)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (statusUp != null || statusChecking) {
                        PulseDot(up = statusUp == true, checking = statusChecking)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(subtitle, style = Type.sec, color = c.ink3)
                }
            }
        }
        if (onRefresh != null) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(c.card).clickable { onRefresh() },
                contentAlignment = Alignment.Center,
            ) { Text("↻", fontSize = 19.sp, color = c.ink2, fontWeight = FontWeight.Bold) }
        }
    }
}

/** 状态点：checking=琥珀；up=绿；down=红。 */
@Composable
private fun PulseDot(up: Boolean, checking: Boolean) {
    val c = DshTheme.colors
    val color = when {
        checking -> c.warn
        up -> c.ok
        else -> c.danger
    }
    Box(Modifier.size(9.dp).clip(CircleShape).background(color))
}

/** 引擎英雄卡。 */
@Composable
private fun HeroEngineCard(engineUp: Boolean, checking: Boolean) {
    val c = DshTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(Radius.r(Radius.xl))
            .background(if (engineUp && !checking) c.ok.copy(alpha = 0.10f) else c.card)
            .border(1.dp, c.line, Radius.r(Radius.xl))
            .padding(Space.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulseDot(up = engineUp, checking = checking)
            Spacer(Modifier.width(Space.sm))
            Text(
                when {
                    checking -> "正在检查引擎…"
                    engineUp -> "引擎运行中"
                    else -> "引擎未运行"
                },
                style = Type.rowTitle, color = c.ink,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "dsh 服务 · 127.0.0.1:${DshProcessManager.DSH_PORT}",
            style = Type.mono, color = c.ink3,
        )
    }
}

/** 分区导航列表（Home 全部 + TwoPane 左栏共用）。 */
@Composable
private fun SectionNavList(
    selected: Section?,
    onSelect: (Section) -> Unit,
    safOn: Boolean, safName: String,
) {
    Column(Modifier.fillMaxWidth()) {
        // v30.37：原标题「网页设置」已名不副实 —— 这四个分区自 r154 起全部是
        // Compose 原生页，走 settings/describe|update 一元 RPC，不再打开任何网页。
        // v30.43：新增「账户」——官方面板里由 dsh-client-ui-settings-account 提供，
        //   我们直接对接 account/* RPC（真实浏览器授权登录），不再是假页面。
        SectionLabel("账号")
        SectionCard {
            NavRow(Section.Account.title, "登录 · 余额 · 用量", selected == Section.Account) { onSelect(Section.Account) }
        }

        Spacer(Modifier.height(Space.xl))
        SectionLabel("dsh 设置")
        SectionCard {
            NavRow(Section.General.title, "外观 · 语言 · 权限", selected == Section.General) { onSelect(Section.General) }
            NavRow(Section.Models.title, "提供商 · 默认模型", selected == Section.Models) { onSelect(Section.Models) }
            NavRow(Section.Plugins.title, "插件开关 · 详情", selected == Section.Plugins) { onSelect(Section.Plugins) }
            NavRow(Section.Presets.title, "预设管理", selected == Section.Presets) { onSelect(Section.Presets) }
        }

        Spacer(Modifier.height(Space.xl))
        SectionLabel("应用")
        SectionCard {
            NavRow(Section.Storage.title, if (safOn) safName else "未授权", selected == Section.Storage) { onSelect(Section.Storage) }
            NavRow(Section.About.title, null, selected == Section.About) { onSelect(Section.About) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, style = Type.secTitle, color = DshTheme.colors.ink3,
        modifier = Modifier.padding(start = 4.dp, bottom = Space.smd),
    )
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    val c = DshTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(Radius.r(Radius.xl))
            .background(c.card).border(1.dp, c.line, Radius.r(Radius.xl)),
        content = content,
    )
}

@Composable
private fun NavRow(title: String, summary: String?, selected: Boolean, onClick: () -> Unit) {
    val c = DshTheme.colors
    Row(
        Modifier.fillMaxWidth()
            .clickable { onClick() }
            .background(if (selected) c.card2 else Color.Transparent)
            .padding(horizontal = Space.lg, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = Type.rowTitle, color = c.ink, modifier = Modifier.weight(1f))
        if (summary != null) {
            Spacer(Modifier.width(Space.sm))
            Text(
                summary, style = Type.sec, color = c.ink3,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp),
            )
        }
        Spacer(Modifier.width(Space.xs))
        Text("›", fontSize = 18.sp, color = c.ink3)
    }
}

// ============================== 详情主体 ==============================

@Composable
private fun DetailBody(
    section: Section,
    safOn: Boolean, safName: String, onPickSaf: () -> Unit, onClearSaf: () -> Unit,
    onConfirmRestart: () -> Unit,
    onShowLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg),
    ) {
        when (section) {
            Section.Account -> AccountPanel()
            Section.General -> GeneralSettingsPage()
            Section.Models -> ModelsPanelV2()
            Section.Plugins -> PluginsPage()
            Section.Presets -> PresetsPanelV2()
            Section.Storage -> StorageDetail(safOn, safName, onPickSaf, onClearSaf, onConfirmRestart, onShowLog)
            Section.About -> AboutDetail()
        }
        Spacer(Modifier.height(Space.xxl))
    }
}

// ============================== v33 · 真实数据页 ==============================

/** 读取设置命名空间（缓存优先；失败给重试）。 */
@Composable
private fun settingsState(): Triple<Map<String, DshSettingsStore.Ns>?, Int, () -> Unit> {
    val appCtx = LocalContext.current.applicationContext
    var map by remember { mutableStateOf(DshSettingsStore.load(appCtx)) }
    var nonce by remember { mutableStateOf(0) }
    LaunchedEffect(nonce) {
        // 首次进入也强制拉一次：缓存可能被 patch 层或其他端改过（真机踩过：
        // 主题写进 patch 了，但控件仍按旧缓存显示「浅色」）。
        map = withContext(Dispatchers.IO) { DshSettingsStore.load(appCtx, force = true) }
    }
    val reload: () -> Unit = { nonce++ }
    return Triple(map, nonce, reload)
}

/** 写入器：成功后刷新本地 map（缓存已由 DshSettingsStore 更新）；失败 toast 原文。 */
@Composable
private fun settingsWriter(onChanged: (Map<String, DshSettingsStore.Ns>?) -> Unit): (String, JSONObject) -> Unit {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    return remember {
        { ns: String, p: JSONObject ->
            scope.launch {
                val ok = withContext(Dispatchers.IO) { DshSettingsStore.update(appCtx, ns, p) }
                if (!ok) {
                    android.widget.Toast.makeText(
                        ctx, DshSettingsStore.lastError ?: "写入失败", android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
                onChanged(DshSettingsStore.load(appCtx))
            }
        }
    }
}

@Composable
private fun LoadingCard(text: String) {
    val c = DshTheme.colors
    SectionCard {
        Column(Modifier.fillMaxWidth().padding(Space.lg)) {
            Text(text, style = Type.sec, color = c.ink3)
        }
    }
}

@Composable
private fun ErrorCard(text: String, onRetry: () -> Unit) {
    val c = DshTheme.colors
    SectionCard {
        Column(Modifier.fillMaxWidth().padding(Space.lg)) {
            Text(text, style = Type.sec, color = c.danger)
            Spacer(Modifier.height(Space.smd))
            GhostButton("重试") { onRetry() }
        }
    }
}

/** 分组选择行（权限/语言/外观共用）。 */
/* ===== v30.40：官方设置面板的表单组件 =====
 * 参照用户提供的官方截图（左：标题+灰色副标题；右：控件；行间 1px 细线；不套卡片）。
 * 原实现是 SectionCard + 胶囊分段控件，是另一套视觉语言 —— 用户「和官方不一样」
 * 指的就是这个。下面三个组件只服务设置页，不影响 App 其它界面。
 */

/** 官方表单行：左标题/副标题，右控件，底部细分隔线。 */
@Composable
private fun FormRow(
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit,
) {
    val c = DshTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = Type.rowTitle, color = c.ink)
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(subtitle, style = Type.sec, color = c.ink3)
                }
            }
            Spacer(Modifier.width(12.dp))
            trailing()
        }
        Box(Modifier.fillMaxWidth().padding(start = Space.lg).height(1.dp).background(c.line2))
    }
}

/** 官方样式的下拉控件：中性底 + 右侧 chevron，点开菜单。 */
@Composable
private fun DropdownControl(
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    val c = DshTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(Radius.r(Radius.sm)).background(c.card2)
                .clickable { open = true }
                .padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value, style = Type.sec, color = c.ink)
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Filled.ExpandMore, contentDescription = null,
                tint = c.ink3, modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.card) {
            options.forEach { (label, v) ->
                DropdownMenuItem(
                    text = { Text(label, style = Type.body) },
                    onClick = { open = false; onSelect(v) },
                )
            }
        }
    }
}

/** 官方「外观」三卡片：浅色 / 深色 / 跟随系统，选中项描边高亮。 */
@Composable
private fun ThemeCards(selected: String, onSelect: (String) -> Unit) {
    val c = DshTheme.colors
    val items = listOf(
        Triple("浅色", "light", Icons.Filled.LightMode),
        Triple("深色", "dark", Icons.Filled.DarkMode),
        Triple("跟随系统", "system", Icons.Filled.SettingsBrightness),
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = 10.dp)) {
        items.forEach { (label, v, icon) ->
            val on = v == selected
            Column(
                Modifier.weight(1f)
                    .padding(end = 8.dp)
                    .clip(Radius.r(Radius.md))
                    .background(if (on) c.soft else c.card2)
                    .border(if (on) 1.5.dp else 1.dp, if (on) c.brand else c.line, Radius.r(Radius.md))
                    .clickable { onSelect(v) }
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    icon, contentDescription = null,
                    tint = if (on) c.brand else c.ink2,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(label, style = Type.sec, color = if (on) c.brand else c.ink)
            }
        }
    }
}

@Composable
private fun SegRow(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val c = DshTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = Type.rowTitle, color = c.ink, modifier = Modifier.weight(1f))
        Row {
            options.forEach { (label, value) ->
                val on = value == selected
                Box(
                    Modifier.padding(start = 6.dp).clip(Radius.r(Radius.sm))
                        // v30.38：选中态由品牌蓝改为「墨色反白」，对齐官方设置面板。
                        // 官方 --dsw-alias-brand-primary = neutral-bluish-1000(#0f1115，浅色)
                        // / neutral-bluish-50(#f9fafb，深色)，即黑底白字 / 白底黑字；
                        // 品牌蓝 #5686fe 只作点缀，不用来刷选中块。
                        .background(if (on) c.ink else c.card2)
                        .clickable { if (!on) onSelect(value) }
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                ) { Text(label, style = Type.sec, color = if (on) c.bg else c.ink2) }
            }
        }
    }
}

/** 数值调节行（字号）。 */
/**
 * 字号滑动条（v34 自绘 / v36 按用户实拍调整）。
 *
 * 原来用 Material3 的 Slider —— 1.3 版把 thumb 画成 4×44dp 的竖条，在设置页里
 * 就是那条突兀的竖线（用户实拍反馈「横栏的竖线去掉」）。M3 的 thumb slot 只能换
 * 形状、换不掉 16dp 的粗轨道，索性自绘。
 *
 * v36 按用户新要求回到官方观感：**粗轨道 + 轨道内刻度点**，只把竖条 thumb
 * 换成圆圈（已选段上是底色点，未选段上是灰点，与官方截图一致）。
 */
@Composable
private fun SliderRow(title: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    val c = DshTheme.colors
    var v by remember(title, value) { mutableStateOf(value.toFloat()) }
    var width by remember { mutableStateOf(0f) }
    val span = (range.last - range.first).coerceAtLeast(1)

    // 手柄半径要从像素宽度里扣掉，否则两端的值点不到边
    fun valueAt(x: Float, w: Float, r: Float): Int {
        val usable = (w - 2 * r).coerceAtLeast(1f)
        val f = ((x - r) / usable).coerceIn(0f, 1f)
        return range.first + Math.round(f * span)
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = Type.rowTitle, color = c.ink, modifier = Modifier.width(84.dp))
        Canvas(
            Modifier.weight(1f).height(40.dp)
                .onSizeChanged { width = it.width.toFloat() }
                .pointerInput(range) {
                    val r = 9.dp.toPx()
                    detectTapGestures { off ->
                        v = valueAt(off.x, width, r).toFloat()
                        onChange(v.toInt())
                    }
                }
                .pointerInput(range) {
                    val r = 9.dp.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = { off -> v = valueAt(off.x, width, r).toFloat() },
                        onDragEnd = { onChange(v.toInt()) },
                        onDragCancel = { onChange(v.toInt()) },
                    ) { change, _ ->
                        change.consume()
                        v = valueAt(change.position.x, width, r).toFloat()
                    }
                },
        ) {
            val r = 9.dp.toPx()            // 手柄半径
            val trackH = 16.dp.toPx()      // 粗轨道（官方设置面板那种观感）
            val cy = size.height / 2f
            val x0 = r
            val x1 = size.width - r
            val f = ((v - range.first) / span.toFloat()).coerceIn(0f, 1f)
            val fx = x0 + (x1 - x0) * f
            val half = trackH / 2f
            val radius = CornerRadius(half, half)
            // 未选段（浅灰条；用 line 而不是 card2 —— card2 浅色下几乎与白底同色，看不出轨道）
            drawRoundRect(
                color = c.line,
                topLeft = Offset(x0, cy - half),
                size = Size((x1 - x0).coerceAtLeast(0f), trackH),
                cornerRadius = radius,
            )
            // 已选段（墨色粗条）
            if (fx - x0 > 1f) {
                drawRoundRect(
                    color = c.ink,
                    topLeft = Offset(x0, cy - half),
                    size = Size(fx - x0, trackH),
                    cornerRadius = radius,
                )
            }
            // 刻度点：已选段上是底色点，未选段上是灰点（图一那种"条内带点"）
            val steps = range.last - range.first
            for (i in 1 until steps) {
                val t = i / steps.toFloat()
                val px = x0 + (x1 - x0) * t
                drawCircle(
                    color = if (px <= fx) c.bg else c.ink3,
                    radius = 2.2.dp.toPx(),
                    center = Offset(px, cy),
                )
            }
            // 手柄：圆圈（底色描边 + 墨色实心），替掉原来的竖条
            drawCircle(color = c.bg, radius = r, center = Offset(fx, cy))
            drawCircle(color = c.ink, radius = r - 2.dp.toPx(), center = Offset(fx, cy))
        }
        Text("${v.toInt()}", style = Type.mono, color = c.ink2, modifier = Modifier.width(30.dp))
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = DshTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.rowTitle, color = c.ink)
            if (subtitle != null) Text(subtitle, style = Type.sec, color = c.ink3)
        }
        Switch(
            checked = checked,
            onCheckedChange = { onChange(it) },
            // v30.40：官方设置面板的开关是**品牌蓝**（图四「显示代码工作视图」实拍），
            // 上一轮改成墨色是误判 —— 墨色只用于文字与选中块，开关保持蓝。
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.brand,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = c.line,
                uncheckedThumbColor = c.card,
                uncheckedBorderColor = c.line,
            ),
        )
    }
}

/* 通用设置（v34）—— 字段与行序对齐官方 dsh-client-ui-settings-general 的
 * settings.general.item 贡献表（order 0..100），逐项真读写 settings 命名空间：
 *   locale.preference / ui-theme.{preference,fontSize,codeFontSize,terminalFontSize,
 *   textFontFamily,codeFontFamily,terminalFontFamily} / ui-chat.{transcriptView,
 *   collapseTiming,linkOpening,performanceUsage} / ui-conversation.busyEnter /
 *   permission.defaultPreset（取自 permissionPresets/catalog）/
 *   ui-settings.enabled（显示代码工作视图）/ session-log-deepseek.enabled。
 * 旧版把 ui-settings.enabled 标成「工作步骤展示」是错的 —— 那是开发者工具开关。
 */
@Composable
private fun GeneralSettingsPage() {
    val appCtx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val (map, nonce, reload) = settingsState()
    var m by remember(map) { mutableStateOf(map) }
    LaunchedEffect(map) { m = map }
    val write = settingsWriter { m = it }
    if (m == null) {
        if (nonce == 0) LoadingCard("正在读取设置…")
        else ErrorCard("无法读取设置（引擎未运行或服务未响应）") { reload() }
        return
    }
    val data = m!!
    val theme = data["ui-theme"]?.value ?: JSONObject()
    val uiSettings = data["ui-settings"]?.value ?: JSONObject()
    val chat = data["ui-chat"]?.value ?: JSONObject()
    val localeV = data["locale"]?.value ?: JSONObject()
    val perm = data["permission"]?.value ?: JSONObject()
    val conv = data["ui-conversation"]?.value ?: JSONObject()
    val sessLog = data["session-log-deepseek"]?.value ?: JSONObject()

    // 设置服务里的值是唯一真源：数据一到就回灌 ThemePref。
    // 宿主 Activity 启动早于引擎就绪时可能没读到，光靠它自己重试仍会不一致。
    val prefNow = theme.optString("preference", "system")
    LaunchedEffect(prefNow) {
        if (prefNow != ThemePref.current) ThemePref.onUserChanged(prefNow)
    }

    // 权限档位来自 host 目录（cordis 可加装 read-only / 实验性 auto），
    // 读不到就用默认两档，保证这一行永远可用。
    var permOptionsRaw by remember { mutableStateOf(DshPermissionCatalog.fallback) }
    var permDefault by remember { mutableStateOf("workspace-write") }
    LaunchedEffect(Unit) {
        val c = withContext(Dispatchers.IO) { DshPermissionCatalog.load(appCtx) }
        if (c != null) {
            permOptionsRaw = c.options
            permDefault = c.defaultPreset ?: permDefault
        }
    }
    // 「自动审查」是**会话级**实验预设，不能当 defaultPreset：
    // 真机写过一次，引擎启动直接报 `permission: unknown preset "auto"`，
    // 权限插件整个不激活。所以这里把它排除在可选档位之外。
    val permOptions = permOptionsRaw.filter { it.value != "auto" }
    // 完全权限必须先过一道确认（官方同样如此），不静默落盘。
    var pendingPerm by remember { mutableStateOf<DshPermissionCatalog.Option?>(null) }

    SectionLabel("界面")
    SectionCard {
        ChoiceRow(
            "界面语言", "管理界面语言偏好和各插件的翻译文案",
            listOf("简体中文" to "zh-CN", "English" to "en-US"),
            localeV.optString("preference", "zh-CN"),
        ) { write("locale", JSONObject().put("preference", it)) }
        ChoiceRow(
            "权限模式", "选择新会话的默认权限模式",
            permOptions.map { permLabel(it) to it.value },
            perm.optString("defaultPreset", permDefault),
        ) { picked ->
            val opt = permOptions.firstOrNull { it.value == picked }
            if (picked == "danger-full-access") {
                pendingPerm = opt ?: DshPermissionCatalog.Option(picked, picked, null)
            } else {
                write("permission", JSONObject().put("defaultPreset", picked))
            }
        }
    }

    Spacer(Modifier.height(Space.lg))
    SectionLabel("外观")
    SectionCard {
        ThemeCards(theme.optString("preference", "system")) { picked ->
            // 先让宿主 Activity 立刻换色，再落盘 —— 否则要等下次进设置才生效。
            ThemePref.onUserChanged(picked)
            // settings 写入会自动镜像到 patch 层（重启后的真源），这里只管写入。
            write("ui-theme", JSONObject().put("preference", picked))
        }
        SliderRow("正文字号", theme.optInt("fontSize", 14), 10..22) {
            write("ui-theme", JSONObject().put("fontSize", it))
        }
        SliderRow("代码字号", theme.optInt("codeFontSize", 11), 10..16) {
            write("ui-theme", JSONObject().put("codeFontSize", it))
        }
        SliderRow("终端字号", theme.optInt("terminalFontSize", 13), 10..20) {
            write("ui-theme", JSONObject().put("terminalFontSize", it))
        }
        FontRow(
            "正文字体", "多个字体用逗号分隔，留空使用默认字体",
            theme.optString("textFontFamily", ""),
        ) { write("ui-theme", JSONObject().put("textFontFamily", it)) }
        FontRow(
            "代码字体", "用于代码块、行内代码与工具输出",
            theme.optString("codeFontFamily", ""),
        ) { write("ui-theme", JSONObject().put("codeFontFamily", it)) }
        FontRow(
            "终端字体", "用于侧栏终端",
            theme.optString("terminalFontFamily", ""),
        ) { write("ui-theme", JSONObject().put("terminalFontFamily", it)) }
    }

    Spacer(Modifier.height(Space.lg))
    SectionLabel("对话")
    SectionCard {
        ChoiceRow(
            "工作步骤展示", "选择希望看到多少工具调用细节",
            listOf("简洁" to "compact", "标准" to "standard", "详细" to "detailed", "完全展开" to "verbose"),
            chat.optString("transcriptView", "detailed"),
        ) { write("ui-chat", JSONObject().put("transcriptView", it)) }
        ChoiceRow(
            "工作步骤收起时机", "选择何时自动收起工作步骤",
            listOf("回答结束后" to "completion", "下次有新消息时" to "next-input"),
            chat.optString("collapseTiming", "completion"),
        ) { write("ui-chat", JSONObject().put("collapseTiming", it)) }
        ChoiceRow(
            "网页链接默认打开方式", "对话中网页链接的打开位置",
            listOf("应用内侧边栏" to "sidebar", "默认浏览器" to "new-tab"),
            chat.optString("linkOpening", "sidebar"),
        ) { write("ui-chat", JSONObject().put("linkOpening", it)) }
        ChoiceRow(
            "繁忙时的发送行为", "智能体运行时 Enter 键和发送按钮的行为",
            listOf("排队发送" to "queue", "插话发送" to "steer"),
            conv.optString("busyEnter", "queue"),
        ) { write("ui-conversation", JSONObject().put("busyEnter", it)) }
        ChoiceRow(
            "性能与用量", "选择性能与用量信息展示的详细程度",
            listOf("简洁" to "compact", "详细" to "detailed"),
            chat.optString("performanceUsage", "detailed"),
        ) { write("ui-chat", JSONObject().put("performanceUsage", it)) }
    }

    Spacer(Modifier.height(Space.lg))
    SectionLabel("开发者与隐私")
    SectionCard {
        SwitchRow(
            "显示代码工作视图",
            "开启后，显示轨迹、本轮代码差异，可选择完整的 Agent 预设切换",
            uiSettings.optBoolean("enabled", true),
        ) { write("ui-settings", JSONObject().put("enabled", it)) }
        SwitchRow(
            "在使用官方模型 API 时上传 Session Log",
            "帮助改进 DeepSeek 模型与产品",
            sessLog.optBoolean("enabled", true),
        ) { write("session-log-deepseek", JSONObject().put("enabled", it)) }
    }

    Spacer(Modifier.height(Space.lg))
    val ver = remember(appCtx) {
        try {
            appCtx.packageManager.getPackageInfo(appCtx.packageName, 0).versionName ?: "?"
        } catch (_: Throwable) {
            "?"
        }
    }
    Text(
        "当前版本：$ver",
        style = Type.sec, color = DshTheme.colors.ink3,
        modifier = Modifier.padding(horizontal = Space.lg),
    )

    val askPerm = pendingPerm
    if (askPerm != null) {
        val isAuto = askPerm.value == "auto"
        AlertDialog(
            onDismissRequest = { pendingPerm = null },
            title = { Text(if (isAuto) "确认启用自动审查（实验）？" else "确认启用完全权限？") },
            text = {
                Text(
                    if (isAuto) {
                        "自动审查不使用沙箱。每次原生工具调用和 PTC 内层调用前，都会由与当前 agent " +
                            "相同的模型进行审查；审查拒绝的调用由你批准或拒绝。此功能仍属实验性，" +
                            "可能误放行或误拒绝，并会消耗额外 token。"
                    } else {
                        "启用完全权限后，新会话将减少确认步骤，并且可以直接执行更多操作，" +
                            "包括敏感操作、文件修改或外部命令。仅建议在你信任后续任务时使用。"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingPerm = null
                    write("permission", JSONObject().put("defaultPreset", askPerm.value))
                }) { Text("我已了解风险，并愿意继续") }
            },
            dismissButton = {
                TextButton(onClick = { pendingPerm = null }) { Text("取消") }
            },
        )
    }
}

/** 权限档位的中文名（与官方 settings.permission 字典一致）。 */
private fun permLabel(o: DshPermissionCatalog.Option): String = when (o.value) {
    "read-only" -> "仅可查看"
    "workspace-write" -> "工作区内修改"
    "danger-full-access" -> "完全权限"
    "auto" -> "自动审查"
    else -> o.name.ifBlank { o.value }
}

/** 官方表单行：左标题+副标题，右下拉。 */
@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String?,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val c = DshTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.rowTitle, color = c.ink)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = Type.sec, color = c.ink3)
            }
        }
        Spacer(Modifier.width(10.dp))
        DropdownControl(
            value = options.firstOrNull { it.second == selected }?.first ?: selected,
            options = options,
            onSelect = onSelect,
        )
    }
}

/** 字体族输入行：失焦时提交，避免每次按键都打一次 RPC。 */
@Composable
private fun FontRow(title: String, subtitle: String, value: String, onCommit: (String) -> Unit) {
    val c = DshTheme.colors
    var txt by remember(value) { mutableStateOf(value) }
    val focus = remember { FocusRequester() }
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = 10.dp)) {
        Text(title, style = Type.rowTitle, color = c.ink)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, style = Type.sec, color = c.ink3)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().clip(Radius.r(Radius.sm))
                .background(c.card2).border(1.dp, c.line, Radius.r(Radius.sm))
                .clickable { focus.requestFocus() }
                .padding(horizontal = 10.dp, vertical = 9.dp),
        ) {
            if (txt.isEmpty()) Text("默认", style = Type.sec, color = c.ink3)
            BasicTextField(
                value = txt,
                onValueChange = { txt = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp, color = c.ink),
                cursorBrush = SolidColor(c.brand),
                modifier = Modifier.fillMaxWidth().heightIn(min = 18.dp)
                    .focusRequester(focus)
                    .onFocusChanged { st -> if (!st.isFocused && txt != value) onCommit(txt) },
            )
        }
    }
}

/** 模型 —— 真实 llm-deepseek.models + agent-default-model 读写。 */
@Composable
private fun ModelsPage() {
    val (map, _, reload) = settingsState()
    var m by remember(map) { mutableStateOf(map) }
    LaunchedEffect(map) { m = map }
    val write = settingsWriter { m = it }
    if (m == null) {
        ErrorCard("无法读取模型清单（引擎未运行或服务未响应）") { reload() }
        return
    }
    val data = m!!
    val llm = data["llm-deepseek"]?.value ?: JSONObject()
    val def = data["agent-default-model"]?.value ?: JSONObject()
    val provider = def.optString("provider", "deepseek-official")
    val current = def.optString("model")

    // v30.41：恢复卡片版式（用户认可的那一版），并补「自定义模型提供商」——
    // 官方模型页里那些 qwen / Ninfer 条目就是 llm-pi-ai.providers 渲染出来的。
    val piAi = data["llm-pi-ai"]?.value
    val providers = piAi?.optJSONObject("providers")

    SectionLabel("默认模型")
    SectionCard {
        val models = llm.optJSONArray("models")
        if (models == null || models.length() == 0) {
            ReadOnlyRow("模型清单", "服务未返回任何模型")
        } else {
            for (i in 0 until models.length()) {
                val mo = models.optJSONObject(i) ?: continue
                val id = mo.optString("id")
                val name = mo.optString("name", id)
                val ctxWin = mo.optLong("contextWindow", 0L)
                ModelRow(
                    title = name,
                    subtitle = id + if (ctxWin > 0) " · 上下文 ${fmtTokens(ctxWin)}" else "",
                    selected = id == current,
                ) {
                    if (id != current) {
                        write(
                            "agent-default-model",
                            JSONObject().put("provider", provider).put("model", id),
                        )
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(Space.lg))
    SectionLabel("自定义模型提供商")
    SectionCard {
        if (providers == null || providers.length() == 0) {
            ReadOnlyRow("暂无自定义提供商", "在 dsh 里添加后出现在这里")
        } else {
            val keys = providers.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val p = providers.optJSONObject(k)
                ReadOnlyRow(
                    k,
                    p?.optString("baseURL")?.takeIf { it.isNotBlank() } ?: "llm-pi-ai",
                )
            }
        }
    }

    Spacer(Modifier.height(Space.md))
    Text(
        "默认模型写入 dsh 设置服务（agent-default-model），新会话立即生效。",
        style = Type.sec, color = DshTheme.colors.ink3,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

private fun fmtTokens(n: Long): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}M"
    n >= 1_000 -> "${n / 1_000}K"
    else -> "$n"
}

@Composable
private fun ModelRow(title: String, subtitle: String, selected: Boolean, onPick: () -> Unit) {
    val c = DshTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable { onPick() }.padding(horizontal = Space.lg, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.rowTitle, color = c.ink)
            Text(subtitle, style = Type.sec, color = c.ink3)
        }
        Box(
            Modifier.size(18.dp).clip(CircleShape)
                .background(if (selected) c.brand else c.card2),
        ) {
            if (selected) Text("✓", fontSize = 11.sp, color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
    }
}

/** 内置插件 —— 真实 node --dump-config 清单。 */
@Composable
private fun PluginsPage() {
    val c = DshTheme.colors
    val appCtx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var catalog by remember { mutableStateOf<DshPluginCatalog.Catalog?>(null) }
    var loading by remember { mutableStateOf(true) }
    var nonce by remember { mutableStateOf(0) }
    // 改过的开关先记在本地：dump-config 要重启引擎才会反映新状态，
    // 不覆盖的话用户会看到「点了开关又跳回去」。
    var overrides by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(nonce) {
        loading = true
        catalog = withContext(Dispatchers.IO) { DshPluginCatalog(appCtx).load() }
        loading = false
    }

    when {
        loading -> LoadingCard("正在读取插件装配清单（node --dump-config，约 4–8 秒）…")
        catalog?.error != null -> ErrorCard("读取失败：${catalog!!.error}") { nonce++ }
        else -> {
            val list = catalog?.plugins ?: emptyList()
            SectionLabel("内置插件 · ${list.size} 个")

            if (note != null) {
                Text(
                    note!!, style = Type.sec, color = c.ok,
                    modifier = Modifier.padding(start = 4.dp, bottom = Space.sm),
                )
            }
            if (error != null) {
                Text(
                    error!!, style = Type.sec, color = c.danger,
                    modifier = Modifier.padding(start = 4.dp, bottom = Space.sm),
                )
            }

            SectionCard {
                list.forEach { p ->
                    val checked = overrides[p.id] ?: p.enabled
                    val managed = p.id in MANAGED_PLUGIN_IDS
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.id, style = Type.rowTitle, color = c.ink)
                            Text(
                                if (managed) "${p.shortName} · 由 DSH Phone 托管"
                                else p.shortName,
                                style = Type.sec, color = c.ink3,
                            )
                        }
                        if (busy == p.id) {
                            Text("保存中…", style = Type.sec, color = c.ink3)
                        } else if (managed) {
                            Text(
                                if (checked) "启用" else "停用",
                                style = Type.sec, color = if (checked) c.ok else c.ink3,
                            )
                        } else {
                            Switch(
                                checked = checked,
                                onCheckedChange = { want ->
                                    busy = p.id
                                    error = null
                                    note = null
                                    scope.launch {
                                        val err = withContext(Dispatchers.IO) {
                                            DshPluginPatch.setEnabled(appCtx, p.id, p.pkg, want)
                                        }
                                        busy = null
                                        if (err != null) {
                                            error = "写入失败：$err"
                                        } else {
                                            overrides = overrides + (p.id to want)
                                            note = "已${if (want) "启用" else "停用"} ${p.id}，" +
                                                "重启引擎后生效（「存储 → 重启引擎」）。"
                                        }
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = c.brand,
                                    checkedThumbColor = Color.White,
                                    uncheckedTrackColor = c.line,
                                    uncheckedThumbColor = c.card,
                                    uncheckedBorderColor = c.line,
                                ),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.md))
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "开关写 profile 的 cordis.patch.yml（Cordis 补丁层），重启引擎后生效。" +
                        "清单本身来自 dsh 自己的 --dump-config。",
                    style = Type.sec, color = c.ink3, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { note = null; nonce++ }) { Text("重新读取") }
            }
        }
    }
}

/** 由 DSH Phone 自己维护的两个条目，不允许在 UI 上翻转。 */
private val MANAGED_PLUGIN_IDS = setOf("tool-web", "experimental-inspector")

/** Agent 预设 —— 内置 patch 文件 + 用户目录 + 服务端注册表（全部真实来源）。 */
@Composable
private fun PresetsPage() {
    val c = DshTheme.colors
    val appCtx = LocalContext.current.applicationContext
    val (map, _, _) = settingsState()
    val builtin = remember {
        try {
            File(DshBootstrap.home(appCtx), "node_modules/@deepseek-ai/dsh-web-app/presets")
                .listFiles()?.mapNotNull { f ->
                    f.name.removeSuffix(".patch.yml").takeIf { f.name.endsWith(".patch.yml") }
                }?.sorted() ?: emptyList()
        } catch (_: Throwable) { emptyList() }
    }
    val userPresets = remember {
        try { DshPresets.listUserPresets(appCtx) } catch (_: Throwable) { emptyList() }
    }
    val registry = map?.get("agent-preset-registry")?.value

    SectionLabel("内置预设 · ${builtin.size} 个")
    SectionCard {
        if (builtin.isEmpty()) ReadOnlyRow("内置预设", "未找到 presets 目录")
        else builtin.forEach { id ->
            ReadOnlyRow(id, "dsh 内置装配补丁（${id}.patch.yml）")
        }
    }

    Spacer(Modifier.height(Space.lg))
    SectionLabel("我的预设 · ${userPresets.size} 个")
    SectionCard {
        if (userPresets.isEmpty()) {
            EmptyHint("暂无自定义预设", "把 preset 目录放到 dsh home 的 .agent-presets 下即可出现")
        } else userPresets.forEach { p ->
            ReadOnlyRow(p.displayName, if (p.description.isBlank()) p.id else p.description)
        }
    }

    if (registry != null && registry.length() > 0) {
        Spacer(Modifier.height(Space.lg))
        SectionLabel("服务端注册表")
        SectionCard {
            val keys = registry.keys()
            while (keys.hasNext()) {
                ReadOnlyRow(keys.next(), "agent-preset-registry")
            }
        }
    }
}

// ---------- 存储 ----------

@Composable
private fun StorageDetail(
    safOn: Boolean, safName: String, onPickSaf: () -> Unit, onClearSaf: () -> Unit,
    onConfirmRestart: () -> Unit, onShowLog: () -> Unit,
) {
    val c = DshTheme.colors
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val homePath = remember {
        try { DshBootstrap.home(appCtx).absolutePath } catch (_: Throwable) { "?" }
    }

    SectionLabel("外部存储（SAF）")
    SectionCard {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("存储目录", style = Type.rowTitle, color = c.ink)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (safOn) "已授权 · $safName" else "未授权（选一个文件夹供 dsh 读写）",
                    style = Type.sec, color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (safOn) {
                GhostButton("更换") { onPickSaf() }
                Spacer(Modifier.width(Space.smd))
                Text(
                    "清除", style = Type.sec, color = c.danger,
                    modifier = Modifier
                        .clip(Radius.r(Radius.sm))
                        .clickable { onClearSaf() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            } else {
                GhostButton("选择文件夹") { onPickSaf() }
            }
        }
    }

    Spacer(Modifier.height(Space.md))
    SectionLabel("工作目录")
    SectionCard {
        ReadOnlyField("dsh home", homePath, mono = true)
    }

    Spacer(Modifier.height(Space.md))
    SectionLabel("引擎")
    SectionCard {
        ActionRow("重启引擎", "中断会话连接后重新拉起服务") { onConfirmRestart() }
        ActionRow("服务日志", "dsh-web.log 最后 40 行") { onShowLog() }
    }
}

// ---------- 关于 ----------

@Composable
private fun AboutDetail() {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val versionName = remember {
        try {
            appCtx.packageManager.getPackageInfo(appCtx.packageName, 0).versionName ?: "?"
        } catch (_: Throwable) { "?" }
    }

    SectionLabel("版本")
    SectionCard {
        ReadOnlyField("App 版本", versionName)
        ReadOnlyField("包名", appCtx.packageName, mono = true)
    }
    Spacer(Modifier.height(Space.md))
    SectionLabel("诊断")
    SectionCard {
        ReadOnlyField("WebView 桥", "dshNative（API 23+ · WebViewCompat）")
        ReadOnlyField(
            "SAF 代理",
            try {
                if (SafProxyServer.isRunning()) "运行中 · 127.0.0.1:${SafProxyServer.port()}" else "未启动"
            } catch (_: Throwable) { "未启动" },
        )
    }
}

// ---------- 原子组件 ----------

/**
 * 整宽空态（v30.37）。
 *
 * 为什么不用 [ReadOnlyRow]：空态的第二行是**一句说明**，不是"标签: 值"的右侧值。
 * 塞进 ReadOnlyRow 会被 `widthIn(max = 210.dp)` 吃掉大半行宽，把左侧标题压成
 * 两行（真机实测「暂无自定义预设」被断成「暂无自定义预 / 设」）。
 * 空态必须整宽排，标题一行、说明一行、都可换行。
 */
@Composable
private fun EmptyHint(title: String, hint: String) {
    val c = DshTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = 14.dp)) {
        Text(title, style = Type.rowTitle, color = c.ink)
        if (hint.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(hint, style = Type.sec, color = c.ink3)
        }
    }
}

@Composable
private fun ReadOnlyRow(label: String, value: String) {
    val c = DshTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Type.rowTitle, color = c.ink, modifier = Modifier.weight(1f))
        Text(
            value, style = Type.sec, color = c.ink3,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 210.dp),
        )
    }
}

@Composable
private fun ReadOnlyField(label: String, value: String, mono: Boolean = false) {
    val c = DshTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = 11.dp)) {
        Text(label, style = Type.sec, color = c.ink3)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = if (mono) Type.mono else Type.body,
            color = c.ink, maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    val c = DshTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }
            .padding(horizontal = Space.lg, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.rowTitle, color = c.ink)
            Text(subtitle, style = Type.sec, color = c.ink3)
        }
        Text("›", fontSize = 18.sp, color = c.ink3)
    }
}

@Composable
private fun GhostButton(text: String, onClick: () -> Unit) {
    val c = DshTheme.colors
    Box(
        Modifier.clip(Radius.r(Radius.md))
            .background(c.card2)
            .border(1.dp, c.line, Radius.r(Radius.md))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(text, style = Type.btnSm, color = c.ink) }
}

@Composable
private fun DetailHint(text: String) {
    val c = DshTheme.colors
    Box(
        Modifier.fillMaxWidth().clip(Radius.r(Radius.xl))
            .background(c.card).border(1.dp, c.line, Radius.r(Radius.xl))
            .padding(Space.xl),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Type.sec, color = c.ink3) }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    danger: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val c = DshTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = Type.title, color = c.ink) },
        text = { Text(body, style = Type.body, color = c.ink2) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = c.ink2) } },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (danger) c.danger else c.brand)
            }
        },
    )
}
