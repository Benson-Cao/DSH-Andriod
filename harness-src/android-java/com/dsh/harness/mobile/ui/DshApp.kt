package com.dsh.harness.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dsh.harness.mobile.data.DshMarketProbe
import com.dsh.harness.mobile.data.DshViewModel
import com.dsh.harness.mobile.ui.screens.*
import com.dsh.harness.mobile.ui.theme.DshTheme
import com.dsh.harness.mobile.ui.theme.Motion

/**
 * App 根：把 18 屏串成一条可点击的完整链路。
 *
 * 对应设计稿 §09 页面地图：
 *   一级入口只有一个 —— 侧边栏（v6 取消底部 Tab）。
 *   "手机上最深只到二级"，Sheet 与指令面板是就地浮动层，不新增路由深度。
 *
 * 这里用自建 NavState 而不是 navigation-compose：层级只有两级，
 * 省一个依赖及其版本地狱；且能精确控制"从哪来回哪去"（见 NavState 注释）。
 */
sealed interface Route {
    // 一次性向导
    data object S1Purpose : Route
    data object S2Density : Route

    // 一级页
    data object S3Home : Route
    data object S11Workspace : Route
    data object S12Plugins : Route
    data object S13Automation : Route
    data object S14Settings : Route
    data object S15Account : Route
    data object S16Nas : Route

    // 二级页
    data object S5ChatRunning : Route
    data object S6ChatResult : Route
    data object S8Trajectory : Route
    data object S10Command : Route
    data object S17NasForm : Route
    data object S18NasOffline : Route

    // v11 插件生态：S12 分流 → 内置(S19/S20) 与市场(S21/S22/S23) 两条独立路径
    data object S19BuiltinPlugins : Route
    data class S20BuiltinConfig(val pluginId: String) : Route
    data object S21MarketDiscover : Route
    data class S22MarketDetail(val name: String) : Route
    data object S23MarketInstalled : Route
    // API 密钥录入（S14 设置 → 模型 → API 密钥），写入 dsh credentials 存储
    data object S14ApiKey : Route
}

/** 浮动层（Sheet），独立于路由栈 */
private enum class SheetKind { None, Workspace, NewSession, More, Approval, Diagnostics, InstallConfirm }

/**
 * 导航栈：从哪来回哪去。
 *
 * ⚠️ v11 真机踩坑：原来用**静态映射表**决定返回目标，其中
 * `S15Account to S14Settings` 是硬编码的。但侧栏可以直接进 S15 ——
 * 用户根本没经过 S14，点返回却"跳到 S14"，看起来就是返回键失灵
 * （而其他页因为恰好映射对了，所以能用，更难定位）。
 *
 * 现在 push 记来源、pop 回来源，硬件返回键与界面返回键共用同一套逻辑。
 */
private class NavState(start: Route) {
    private val stack = ArrayDeque<Route>()
    var current: Route by mutableStateOf(start)
        private set

    /** 前进：压入当前页作为来源 */
    fun go(next: Route) {
        if (next == current) return
        stack.addLast(current)
        current = next
    }

    /** 原地替换：切换同一层级的不同页（如 S5 → S6），不留下无意义的中间态 */
    fun replace(next: Route) {
        current = next
    }

    /** 回退；栈空时兜底回首页（一级页按返回不该退出 App） */
    fun backOrHome() {
        current = stack.removeLastOrNull() ?: Route.S3Home
    }

    val canGoBack: Boolean get() = stack.isNotEmpty()
}

@Composable
fun DshApp(startRoute: Route = Route.S3Home) {
    val vm: DshViewModel = viewModel()
    val nav = remember { NavState(startRoute) }
    val route = nav.current
    var sheet by remember { mutableStateOf(SheetKind.None) }
    var drawerOpen by remember { mutableStateOf(false) }
    var dark by remember { mutableStateOf(false) }

    // 冷启动即拉起本地 dsh ACP 进程并握手
    LaunchedEffect(Unit) { vm.connect() }

    val connection by vm.connection.collectAsState()
    val workspace by vm.workspace.collectAsState()
    val messages by vm.messages.collectAsState()
    val tools by vm.tools.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val models by vm.models.collectAsState()
    val events by vm.events.collectAsState()
    val diagnostics by vm.diagnostics.collectAsState()
    val error by vm.error.collectAsState()
    val busy by vm.busy.collectAsState()
    val contextUsage by vm.contextUsage.collectAsState()
    val catalog by vm.catalog.collectAsState()
    val catalogLoading by vm.catalogLoading.collectAsState()
    val apiKey by vm.apiKey.collectAsState()
    val apiKeySaved by vm.apiKeySaved.collectAsState()

    // 市场侧（S21/S22/S23）的数据源。
    // 本机 $HOME/node_modules/@deepseek-ai/ 下没有 dshmarket → 市场本体未安装，
    // 于是市场插件列表为空、页面走安装引导，而不是拿设计稿的示意名充数。
    val ctx = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val marketInstalled = remember { DshMarketProbe.isInstalled(ctx) }
    val marketPlugins = remember { emptyList<MarketPlugin>() }
    val marketInstalledPlugins = remember { emptyList<MarketPlugin>() }

    // S22 的安装确认 Sheet 要盖在详情页上，所以目标插件存在 state 里
    var installTarget by remember { mutableStateOf<MarketPlugin?>(null) }

    // 一次性提示（脱敏日志落盘路径等）
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(2600)
            toast = null
        }
    }

    // 打开侧栏时拉一次真实会话列表（S4）
    LaunchedEffect(drawerOpen) {
        if (drawerOpen) vm.refreshSessions()
    }

    DshTheme(dark = dark) {
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = route,
                // 只淡入淡出会显得平；加一点纵向位移才有"进入下一层"的方向感。
                // 二级页向右滑更符合安卓习惯，但为避免与左右滑混淆，统一用极小的纵向位移。
                transitionSpec = {
                    (fadeIn(tween(Motion.ROUTE, easing = FastOutSlowInEasing)) +
                        slideInVertically(
                            animationSpec = tween(Motion.ROUTE, easing = FastOutSlowInEasing),
                        ) { it / 24 }) togetherWith
                        (fadeOut(tween(Motion.ROUTE, easing = FastOutLinearInEasing)) +
                            slideOutVertically(
                                animationSpec = tween(Motion.ROUTE, easing = FastOutLinearInEasing),
                            ) { -it / 24 })
                },
                label = "route",
            ) { r ->
                when (r) {
                    Route.S1Purpose -> S1OnboardingPurpose(
                        onBack = { nav.backOrHome() },
                        onNext = { nav.go(Route.S2Density) },
                    )

                    Route.S2Density -> S2OnboardingDensity(
                        onBack = { nav.go(Route.S1Purpose) },
                        onSkip = { nav.replace(Route.S3Home) },
                        onEnter = { nav.replace(Route.S3Home) },
                    )

                    Route.S3Home -> S3Home(
                        workspace = workspace?.let { it.substringAfterLast('/') } ?: "选择工作区",
                        workspaceChosen = workspace != null,
                        connectionReady = connection == DshViewModel.ClientPhase.Ready,
                        onOpenDrawer = { drawerOpen = true },
                        onPickWorkspace = { sheet = SheetKind.Workspace },
                        onPickMode = { sheet = SheetKind.NewSession },
                        onSend = { text ->
                            vm.send(text)
                            nav.go(Route.S5ChatRunning)
                        },
                        onShowDiagnostics = { sheet = SheetKind.Diagnostics },
                    )

                    Route.S5ChatRunning -> S5ChatRunning(
                        title = "当前会话",
                        subtitle = if (connection == DshViewModel.ClientPhase.Ready) "已连接 · 标准模式"
                        else "未连接本地引擎",
                        userText = messages.firstOrNull { it.role == DshViewModel.Role.User }?.text.orEmpty(),
                        liveMessages = messages,
                        liveTools = tools,
                        onBack = { nav.backOrHome() },
                        running = busy,
                        contextUsage = contextUsage,
                        onCancel = { vm.cancel() },
                        onOpenMore = { sheet = SheetKind.More },
                        onOpenApproval = { sheet = SheetKind.Approval },
                        onOpenTrajectory = { nav.go(Route.S8Trajectory) },
                        onSend = { vm.send(it) },
                    )

                    Route.S6ChatResult -> S6ChatResult(
                        title = "当前会话",
                        answer = messages.lastOrNull { it.role == DshViewModel.Role.Assistant }?.text
                            ?: "任务已完成，产物已写入工作区。",
                        onBack = { nav.backOrHome() },
                        onOpenWorkspace = { nav.go(Route.S11Workspace) },
                        onPreview = {},
                        onShare = {},
                    )

                    Route.S8Trajectory -> S8Trajectory(
                        onBack = { nav.backOrHome() },
                        events = events,
                        protocolLog = diagnostics,
                    )

                    Route.S10Command -> S10CommandPanel(
                        onBack = { nav.backOrHome() },
                        onPick = { nav.backOrHome() },
                    )

                    Route.S11Workspace -> S11Workspace(
                        // 设计稿 S11 顶栏是「返回 + ☰ 入口」；用户要求此处用返回
                        onBack = { nav.backOrHome() },
                        onOpenDrawer = { drawerOpen = true },
                    )

                    Route.S12Plugins -> S12Plugins(
                        onBack = { nav.backOrHome() },
                        onOpenDrawer = { drawerOpen = true },
                        catalog = catalog,
                        loading = catalogLoading,
                        onRefresh = { vm.refreshCatalog() },
                        onOpenBuiltin = { nav.go(Route.S19BuiltinPlugins) },
                        onOpenMarket = { nav.go(Route.S21MarketDiscover) },
                    )

                    // —— v11 插件生态：官方内置 ——
                    Route.S19BuiltinPlugins -> S19BuiltinPlugins(
                        onBack = { nav.backOrHome() },
                        onOpenPlugin = { id -> nav.go(Route.S20BuiltinConfig(id)) },
                        onSwitchToMarket = { nav.go(Route.S21MarketDiscover) },
                        catalog = catalog,
                        loading = catalogLoading,
                        onRefresh = { vm.refreshCatalog(force = true) },
                    )

                    is Route.S20BuiltinConfig -> S20BuiltinPluginConfig(
                        pluginId = r.pluginId,
                        onBack = { nav.backOrHome() },
                        onBackToSplit = { nav.backOrHome() },
                        catalog = catalog,
                    )

                    // —— v11 插件生态：dsh-market（本机未装市场 → 空态 + 安装引导）——
                    Route.S21MarketDiscover -> S21MarketDiscover(
                        onBack = { nav.backOrHome() },
                        onOpenInstalled = { nav.go(Route.S23MarketInstalled) },
                        onOpenThemes = { nav.go(Route.S23MarketInstalled) },
                        plugins = marketPlugins,
                        marketInstalled = marketInstalled,
                    )

                    is Route.S22MarketDetail -> {
                        val p = marketPlugins.firstOrNull { it.name == r.name }
                        if (p == null) {
                            // 目标插件已不在索引源里（索引刷新过）：退回发现页而不是白屏
                            LaunchedEffect(r.name) { nav.backOrHome() }
                        } else {
                            S22MarketDetail(
                                plugin = p,
                                onBack = { nav.backOrHome() },
                                onConfirmInstall = { installTarget = p; sheet = SheetKind.InstallConfirm },
                            )
                        }
                    }

                    Route.S23MarketInstalled -> S23MarketInstalled(
                        onBack = { nav.backOrHome() },
                        onOpenDiscover = { nav.backOrHome() },
                        onOpenThemes = {},
                        installed = marketInstalledPlugins,
                        marketInstalled = marketInstalled,
                        onExportLog = { toast = vm.exportRedactedLog() },
                    )

                    Route.S13Automation -> S13Automation(onBack = { nav.backOrHome() })

                    Route.S14Settings -> S14Settings(
                        onBack = { nav.backOrHome() },
                        dark = dark,
                        onToggleDark = { dark = it },
                        onNas = { nav.go(Route.S16Nas) },
                        onAccount = { nav.go(Route.S15Account) },
                        apiKeyConfigured = apiKey.ok,
                        onApiKey = { nav.go(Route.S14ApiKey) },
                    )

                    // —— API 密钥：写入 dsh 的 credentials 存储（$DSH_HOME/.credentials.yaml）——
                    Route.S14ApiKey -> S19ApiKey(
                        onBack = { nav.backOrHome() },
                        state = apiKey,
                        savedState = apiKeySaved,
                        onSave = { vm.saveApiKey(it) },
                        onRemove = { vm.removeApiKey() },
                        onRefresh = { vm.refreshApiKey() },
                    )

                    Route.S15Account -> S15Account(onBack = { nav.backOrHome() })

                    Route.S16Nas -> S16Nas(
                        onBack = { nav.backOrHome() },
                        onAdd = { nav.go(Route.S17NasForm) },
                    )

                    Route.S17NasForm -> S17NasForm(
                        onBack = { nav.backOrHome() },
                        onTest = { nav.backOrHome() },
                    )

                    Route.S18NasOffline -> S18NasOffline(
                        onBack = { nav.backOrHome() },
                        onReconnect = { nav.replace(Route.S3Home) },
                        onFallbackLocal = { nav.replace(Route.S3Home) },
                    )
                }
            }

            // —— 浮动层：Sheet 挂在当前屏之上 ——
            when (sheet) {
                SheetKind.Workspace -> S3WorkspaceSheet(
                    visible = true,
                    onDismiss = { sheet = SheetKind.None },
                    onAddLocal = { sheet = SheetKind.None },
                )

                SheetKind.NewSession -> S9NewSessionSheet(
                    visible = true,
                    onDismiss = { sheet = SheetKind.None },
                    liveModels = models,
                    onPickModel = { vm.selectModel(it) },
                    onStart = {
                        sheet = SheetKind.None
                        nav.go(Route.S5ChatRunning)
                    },
                )

                SheetKind.More -> S5MoreSheet(
                    visible = true,
                    onDismiss = { sheet = SheetKind.None },
                    onTrajectory = {
                        sheet = SheetKind.None
                        nav.go(Route.S8Trajectory)
                    },
                )

                SheetKind.Approval -> S7ApprovalSheet(
                    visible = true,
                    onDismiss = { sheet = SheetKind.None },
                    onAllowOnce = {
                        sheet = SheetKind.None
                        nav.replace(Route.S6ChatResult)
                    },
                    onAllowAlways = { sheet = SheetKind.None },
                    onReject = { sheet = SheetKind.None },
                )

                SheetKind.Diagnostics -> S3EngineDiagnostics(
                    visible = true,
                    onDismiss = { sheet = SheetKind.None },
                    onRetry = {
                        sheet = SheetKind.None
                        vm.retry()
                    },
                    log = diagnostics,
                    error = error,
                )

                SheetKind.InstallConfirm -> {
                    val target = installTarget
                    if (target != null) {
                        S22InstallConfirmSheet(
                            plugin = target,
                            visible = true,
                            onDismiss = { sheet = SheetKind.None; installTarget = null },
                            onConfirm = { toast = "安装任务已提交 · 刷新页面即可生效" },
                        )
                    }
                }

                SheetKind.None -> Unit
            }

            // —— 侧边栏：全局唯一一级入口 ——
            S4Drawer(
                visible = drawerOpen,
                onClose = { drawerOpen = false },
                onNewChat = { drawerOpen = false; nav.go(Route.S3Home) },
                onPlugins = { drawerOpen = false; nav.go(Route.S12Plugins) },
                onAutomation = { drawerOpen = false; nav.go(Route.S13Automation) },
                onWorkspace = { drawerOpen = false; nav.go(Route.S11Workspace) },
                onSettings = { drawerOpen = false; nav.go(Route.S14Settings) },
                onAccount = { drawerOpen = false; nav.go(Route.S15Account) },
                liveSessions = sessions,
            )

            // —— 一次性轻提示（脱敏日志路径、安装结果等）——
            toast?.let { msg ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(Motion.MICRO)) + slideInVertically(tween(Motion.MICRO)) { it / 3 },
                    exit = fadeOut(tween(Motion.MICRO)),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Box(
                        Modifier
                            .padding(bottom = 92.dp)
                            .padding(horizontal = 24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DshTheme.colors.ink.copy(alpha = 0.92f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            msg,
                            style = com.dsh.harness.mobile.ui.theme.Type.sec,
                            color = androidx.compose.ui.graphics.Color.White,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // 返回键：先关浮动层 → 再退栈 → 首页时退出 App
        BackHandler(enabled = sheet != SheetKind.None || nav.canGoBack) {
            when {
                sheet != SheetKind.None -> sheet = SheetKind.None
                else -> nav.backOrHome()
            }
        }
    }
}