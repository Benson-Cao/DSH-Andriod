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
import com.dsh.harness.mobile.data.DshPrefs
import com.dsh.harness.mobile.data.DshViewModel
import com.dsh.harness.mobile.ui.screens.*
import com.dsh.harness.mobile.ui.theme.DshTheme
import com.dsh.harness.mobile.ui.theme.Motion
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

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
    // 一次性向导（v13 §01 首次配置：S24 填密钥 → 可选 S25 → S1 用途 → S2 密度 → S3）
    data object S24FirstKey : Route
    data object S25Provider : Route
    data object S1Purpose : Route
    data object S2Density : Route

    // 一级页
    data object S3Home : Route
    data object S11Workspace : Route
    data object S12Plugins : Route
    data object S13Automation : Route
    data object S14Settings : Route
    data object S15Account : Route

    // 二级页
    data object S5ChatRunning : Route
    data object S6ChatResult : Route
    data object S8Trajectory : Route
    data object S10Command : Route

    // v11 插件生态：S12 分流 → 内置(S19/S20) 与市场(S21/S22/S23) 两条独立路径
    data object S19BuiltinPlugins : Route
    data class S20BuiltinConfig(val pluginId: String) : Route
    data object S21MarketDiscover : Route
    data class S22MarketDetail(val name: String) : Route
    data object S23MarketInstalled : Route
    // API 密钥录入（S14 设置 → 模型 → API 密钥），写入 dsh credentials 存储
    data object S14ApiKey : Route
    // v14 新增：本机 Termux 终端（入口挂 S4 侧栏与 S14 设置）
    data object S27Termux : Route

    // —— v20 新增 S30–S49（账号与模型集群 / 动作落地页）——
    data object S30ModelDefault : Route      // 默认模型与思考强度
    data object S31AccountLogin : Route      // DeepSeek 账号登录
    data object S32AccountLoggedOut : Route  // 账号与余额 · 未登录
    data object S33AgentPresets : Route      // Agent 预设列表
    data object S35Usage : Route             // 用量与账单明细
    data object S37ApiKeyConfigured : Route  // API 密钥 · 已配置态
    data object S39ProviderEdit : Route      // 编辑模型提供方
    data object S40NewTask : Route           // 新建自动化任务
    data object S41PickFolder : Route        // 选择工作区目录
    data object S42CreatePlugin : Route      // 创造模式创建插件
    data object S43AddModel : Route          // 添加模型与推理等级
    data object S44IndexSource : Route       // 索引源与市场管理
    data object S45GlobalSearch : Route      // 全局搜索
    data object S46Export : Route            // 数据导出
    data object S47GitCommit : Route         // Git 提交
    data object S48Archived : Route          // 归档会话
    data object S49NewTermuxSession : Route  // Termux 新建终端会话
}

/** 浮动层（Sheet），独立于路由栈。
 *  v25：不再有 Approval —— S7 审批 Sheet 由引擎的 pendingPermission 状态驱动
 *  （ACP session/request_permission），不是用户手动唤起的浮动层。 */
private enum class SheetKind {
    None, Workspace, NewSession, More, Diagnostics, Model,
    /** v20：S3 右上角「本机服务与快捷入口」 */
    Service,
    /** v20：S38 退出登录二次确认 */
    Logout,
}

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

    // —— 全局连接态 ——
    // v27：删掉了 accountLoggedIn 这个假状态（local mutableState，与真实凭据脱钩）。
    // 「是否可跑任务」现在只有一个真相源：apiKey.configured（读 dsh 凭据存储）。
    // v29：同样删掉 nasConnected 假状态 —— 本 harness 无 NAS 同步后端，连接态无从谈起。
    // svc Sheet 的「代码视图」开关（S3 首页与 Sheet 共享，提升到根）
    var codeViewOn by remember { mutableStateOf(true) }
    // v23：S3 输入框内容与「@提及」Sheet 状态提升到根 ——
    // 路由切去 S10 指令面板再回来时，屏内 remember 会随 AnimatedContent
    // 退出组合而丢失，选中的指令/会话就回填不进输入框（实测"选了没反应"）。
    var homeInput by remember { mutableStateOf("") }
    var mentionOpen by remember { mutableStateOf(false) }

    // 冷启动即拉起本地 dsh ACP 进程并握手
    LaunchedEffect(Unit) { vm.connect() }

    val connection by vm.connection.collectAsState()
    val workspace by vm.workspace.collectAsState()
    val messages by vm.messages.collectAsState()
    val tools by vm.tools.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val models by vm.models.collectAsState()
    val modelName by vm.modelName.collectAsState()
    val thinking by vm.thinking.collectAsState()
    val autoModel by vm.autoModel.collectAsState()
    val events by vm.events.collectAsState()
    val diagnostics by vm.diagnostics.collectAsState()
    val error by vm.error.collectAsState()
    val busy by vm.busy.collectAsState()
    val contextUsage by vm.contextUsage.collectAsState()
    val catalog by vm.catalog.collectAsState()
    val catalogLoading by vm.catalogLoading.collectAsState()
    val apiKey by vm.apiKey.collectAsState()
    val apiKeySaved by vm.apiKeySaved.collectAsState()
    // v24：社区市场精选目录（真实数据源 = npm 上的 dsh-plugin-catalog 包）
    val marketCatalog by vm.marketCatalog.collectAsState()
    val marketCatalogLoading by vm.marketCatalogLoading.collectAsState()
    val marketCatalogError by vm.marketCatalogError.collectAsState()
    // v25：真实权限审批（ACP session/request_permission）；null = 当前无待确认操作
    val pendingPermission by vm.pendingPermission.collectAsState()

    // 市场侧（S21/S22/S23）的数据源。
    // 本机 $HOME/node_modules/@deepseek-ai/ 下没有 dshmarket → 市场本体未安装，
    // 于是市场插件列表为空、页面走安装引导，而不是拿设计稿的示意名充数。
    val ctx = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val marketInstalled = remember { DshMarketProbe.isInstalled(ctx) }

    // 首启动：没走过引导且没配密钥 → 进 S24 首次配置（在副作用里改路由，不在组合期）
    LaunchedEffect(startRoute, apiKey.configured) {
        if (startRoute == Route.S3Home && !DshPrefs.onboarded(ctx) && !apiKey.configured) {
            nav.replace(Route.S24FirstKey)
        }
    }

    /** 全局复制助手（开放平台链接等） */
    fun copyText(c: Context, text: String) {
        val cm = c.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("dsh", text))
    }

    /**
     * 拉起系统浏览器打开外链（v27）。返回是否成功。
     *
     * 用 Intent.ACTION_VIEW 而非 CustomTabs：**不引新依赖**——
     * androidx.browser 不在离线缓存里，且 ACTION_VIEW 零依赖即可把用户送到
     * 真实页面，这正是 v23 那个假 URL 该有的行为。
     */
    fun openUrl(url: String): Boolean = try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (_: Throwable) {
        // 没有可用浏览器时至少把地址复制到剪贴板，不静默失败
        copyText(ctx, url)
        false
    }

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

    // v24：进入市场页才拉精选目录（2.4MB tarball + 2.3MB 解析，
    // 不该在冷启动就付这个代价；已有数据则 VM 内自动跳过）
    LaunchedEffect(route) {
        if (route is Route.S21MarketDiscover || route is Route.S22MarketDetail) {
            vm.refreshMarketCatalog()
        }
        // v27：S31 的状态卡要显示**真实**是否已配置密钥，不能用进页时的旧快照
        if (route is Route.S31AccountLogin || route is Route.S32AccountLoggedOut) {
            vm.refreshApiKey()
        }
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
                    Route.S24FirstKey -> S24OnboardingKey(
                        onNext = { nav.go(Route.S1Purpose) },
                        // 设计稿 S24：用 DeepSeek 账号登录 → S31 登录页
                        onUseBalance = { nav.go(Route.S31AccountLogin) },
                        onAddProvider = { nav.go(Route.S25Provider) },
                        onToggleDark = { dark = !dark },
                        onCopyPlatform = {
                            copyText(ctx, "platform.deepseek.com")
                            toast = "已复制 platform.deepseek.com"
                        },
                        state = apiKey,
                        onSave = { vm.saveApiKey(it) },
                    )

                    Route.S25Provider -> S25Provider(
                        onBack = { nav.go(Route.S24FirstKey) },
                        onCancel = { nav.go(Route.S24FirstKey) },
                        // 设计稿：创建提供方后落 S37 已配置态
                        onCreated = { nav.go(Route.S37ApiKeyConfigured) },
                        onAddModel = { nav.go(Route.S43AddModel) },
                    )

                    Route.S1Purpose -> S1OnboardingPurpose(
                        onBack = { nav.go(Route.S24FirstKey) },
                        onNext = { nav.go(Route.S2Density) },
                    )

                    Route.S2Density -> S2OnboardingDensity(
                        onBack = { nav.go(Route.S1Purpose) },
                        onSkip = {
                            DshPrefs.setOnboarded(ctx)
                            nav.replace(Route.S3Home)
                        },
                        onEnter = {
                            DshPrefs.setOnboarded(ctx)
                            nav.replace(Route.S3Home)
                        },
                    )

                    Route.S3Home -> S3Home(
                        workspace = workspace?.let { it.substringAfterLast('/') } ?: "选择工作区",
                        workspaceChosen = workspace != null,
                        modelName = modelName,
                        thinking = thinking,
                        connectionReady = connection == DshViewModel.ClientPhase.Ready,
                        onOpenDrawer = { drawerOpen = true },
                        onPickWorkspace = { sheet = SheetKind.Workspace },
                        onPickMode = { sheet = SheetKind.NewSession },
                        // v24：「+」= 添加附件/引用 → 与 @ 同一个「提及」选择器
                        onAddAttachment = { mentionOpen = true },
                        onPickModel = { sheet = SheetKind.Model },
                        onSend = { text ->
                            vm.send(text)
                            nav.go(Route.S5ChatRunning)
                            // 发送后清空草稿：回来时不该看到上一轮的旧输入
                            homeInput = ""
                        },
                        // 发送键不可用被点时给出原因（此前静默禁用 = "点不到"）
                        onSendBlocked = { reason -> toast = reason },
                        input = homeInput,
                        onInputChange = { homeInput = it },
                        // 输入 "/" → S10 斜杠指令面板；选中指令回填输入框
                        onSlashTrigger = { nav.go(Route.S10Command) },
                        // 输入 "@" → 提及文件/会话 Sheet
                        onMentionTrigger = { mentionOpen = true },
                        onShowDiagnostics = { sheet = SheetKind.Diagnostics },
                        // v20：右上角描边菜单按钮 → 「本机服务与快捷入口」Sheet
                        onOpenServices = { sheet = SheetKind.Service },
                    )

                    Route.S5ChatRunning -> S5ChatRunning(
                        title = "当前会话",
                        subtitle = if (connection == DshViewModel.ClientPhase.Ready) "已连接 · 标准模式"
                        else "未连接本地引擎",
                        userText = messages.firstOrNull { it.role == DshViewModel.Role.User }?.text.orEmpty(),
                        liveMessages = messages,
                        liveTools = tools,
                        events = events,
                        onBack = { nav.backOrHome() },
                        running = busy,
                        contextUsage = contextUsage,
                        onCancel = { vm.cancel() },
                        onOpenMore = { sheet = SheetKind.More },
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
                        events = events,
                        onOpenTrajectory = { nav.go(Route.S8Trajectory) },
                    )

                    Route.S8Trajectory -> S8Trajectory(
                        onBack = { nav.backOrHome() },
                        events = events,
                        protocolLog = diagnostics,
                        onExport = { toast = vm.exportRedactedLog() },
                    )

                    Route.S10Command -> S10CommandPanel(
                        onBack = { nav.backOrHome() },
                        // 选中指令回填输入框再返回（此前 onPick 丢弃选中值，
                        // 是"选了指令没反应"的根因）
                        onPick = { cmd ->
                            homeInput = cmd
                            nav.backOrHome()
                        },
                    )

                    Route.S11Workspace -> S11Workspace(
                        // 设计稿 S11 顶栏是「返回 + ☰ 入口」；用户要求此处用返回
                        onBack = { nav.backOrHome() },
                        onOpenDrawer = { drawerOpen = true },
                        onSearch = { nav.go(Route.S45GlobalSearch) },
                        onAddWorkspace = { nav.go(Route.S41PickFolder) },
                    )

                    Route.S12Plugins -> S12Plugins(
                        onBack = { nav.backOrHome() },
                        onOpenDrawer = { drawerOpen = true },
                        catalog = catalog,
                        loading = catalogLoading,
                        onRefresh = { vm.refreshCatalog() },
                        onOpenBuiltin = { nav.go(Route.S19BuiltinPlugins) },
                        onOpenMarket = { nav.go(Route.S21MarketDiscover) },
                        onSearch = { nav.go(Route.S45GlobalSearch) },
                        onCreatePlugin = { nav.go(Route.S42CreatePlugin) },
                        onAddSource = { nav.go(Route.S44IndexSource) },
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

                    // —— v11 插件生态：dsh-market（v24 起为真实精选目录数据）——
                    Route.S21MarketDiscover -> S21MarketDiscover(
                        onBack = { nav.backOrHome() },
                        onOpenInstalled = { nav.go(Route.S23MarketInstalled) },
                        onOpenThemes = { nav.go(Route.S23MarketInstalled) },
                        catalog = marketCatalog,
                        loading = marketCatalogLoading,
                        error = marketCatalogError,
                        onRetry = { vm.refreshMarketCatalog(force = true) },
                        marketInstalled = marketInstalled,
                        onSearch = { nav.go(Route.S45GlobalSearch) },
                        onOpenDetail = { nav.go(Route.S22MarketDetail(it)) },
                    )

                    is Route.S22MarketDetail -> {
                        // 取本地 val：by 委托属性做不了 smart cast
                        val catalog = marketCatalog
                        val entry = catalog?.entries?.firstOrNull { it.name == r.name }
                        if (catalog == null || entry == null) {
                            // 目录还没拉到 / 目标插件已不在索引源里：退回发现页而不是白屏
                            LaunchedEffect(r.name) { nav.backOrHome() }
                        } else {
                            S22MarketDetail(
                                plugin = entry.toMarketPlugin(catalog.categories),
                                onBack = { nav.backOrHome() },
                                // 复制安装命令；真机 Termux 默认无 pnpm，把前提说清楚
                                onCopyInstall = {
                                    copyText(ctx, "dsh plugin --profile acp add ${entry.name}")
                                    toast = "已复制安装命令 · 需 Termux 内有 pnpm（dsh plugin 只是 pnpm 转发器）"
                                },
                            )
                        }
                    }

                    Route.S23MarketInstalled -> S23MarketInstalled(
                        onBack = { nav.backOrHome() },
                        onOpenDiscover = { nav.backOrHome() },
                        onOpenThemes = {},
                        installed = emptyList(),
                        marketInstalled = marketInstalled,
                        onExportLog = { toast = vm.exportRedactedLog() },
                    )

                    Route.S13Automation -> S13Automation(
                        onBack = { nav.backOrHome() },
                        onNewTask = { nav.go(Route.S40NewTask) },
                    )

                    Route.S14Settings -> S14Settings(
                        onBack = { nav.backOrHome() },
                        dark = dark,
                        onToggleDark = { dark = it },
                        keyConfigured = apiKey.configured,
                        onAccount = {
                            // 未登录进 S31 登录页；已登录进 S15 账号与余额
                            nav.go(if (apiKey.configured) Route.S15Account else Route.S31AccountLogin)
                        },
                        onBalance = {
                            nav.go(if (apiKey.configured) Route.S15Account else Route.S32AccountLoggedOut)
                        },
                        apiKeyConfigured = apiKey.ok,
                        onApiKey = {
                            // 已配置 → S37 已配置态；未配置 → S24 首次配置（本 App 的密钥录入屏）
                            nav.go(if (apiKey.ok) Route.S37ApiKeyConfigured else Route.S14ApiKey)
                        },
                        onModelDefault = { nav.go(Route.S30ModelDefault) },
                        onAddProvider = { nav.go(Route.S25Provider) },
                        onPresets = { nav.go(Route.S33AgentPresets) },
                        onSearch = { nav.go(Route.S45GlobalSearch) },
                        onTermux = { nav.go(Route.S27Termux) },
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

                    Route.S15Account -> S15Account(
                        onBack = { nav.backOrHome() },
                        onUsage = { nav.go(Route.S35Usage) },
                        onLogout = { sheet = SheetKind.Logout },
                        onAddProvider = { nav.go(Route.S25Provider) },
                        onEditProvider = { nav.go(Route.S39ProviderEdit) },
                    )

                    Route.S27Termux -> S27Termux(
                        onBack = { nav.backOrHome() },
                        onOpenUi = { toast = "已在浏览器打开 dsh web（本机 3081）" },
                        onStop = { toast = "服务已停止，会话日志保留" },
                        // 设计稿 S27：日志行导出 → S46 数据导出
                        onExportLog = { nav.go(Route.S46Export) },
                        onCopyCommand = { cmd ->
                            copyText(ctx, cmd)
                            toast = "已复制 $cmd"
                        },
                        onNewSession = { nav.go(Route.S49NewTermuxSession) },
                    )

                    // —— v20 S30–S39：账号与模型集群 ——
                    Route.S30ModelDefault -> S30ModelDefault(
                        onBack = { nav.backOrHome() },
                        currentModel = modelName,
                        currentThinking = thinking,
                        onSave = { m, t ->
                            vm.setModelDisplay(m, t)
                            toast = "默认模型已设为 $m · $t"
                            nav.backOrHome()
                        },
                        onRestoreDefault = { toast = "已恢复默认模型与思考强度" },
                    )

                    Route.S31AccountLogin -> S31AccountLogin(
                        onBack = { nav.backOrHome() },
                        // v27：真动作 —— 拉起系统浏览器去平台申请 Key
                        onOpenPlatform = {
                            if (!openUrl("https://platform.deepseek.com/api_keys")) {
                                toast = "没有可用浏览器，地址已复制到剪贴板"
                            }
                        },
                        keyConfigured = apiKey.configured,
                        onOpenApp = {
                            nav.go(Route.S15Account)
                        },
                        onGoKey = { nav.go(Route.S14ApiKey) },
                    )

                    Route.S32AccountLoggedOut -> S32AccountLoggedOut(
                        onBack = { nav.backOrHome() },
                        onLogin = { nav.go(Route.S31AccountLogin) },
                        onTopUpBlocked = { toast = "此版本不提供充值" },
                        onUsage = { nav.go(Route.S35Usage) },
                        onAddProvider = { nav.go(Route.S25Provider) },
                    )

                    Route.S33AgentPresets -> S33AgentPresets(
                        onBack = { nav.backOrHome() },
                        onNewPreset = { nav.go(Route.S40NewTask) },
                        onEditPreset = { toast = "预设编辑将在后续版本开放" },
                        onUsePreset = { nav.go(Route.S40NewTask) },
                        onDuplicate = { toast = "已复制预设副本" },
                    )

                    Route.S35Usage -> S35Usage(
                        onBack = { nav.backOrHome() },
                        onExport = { nav.go(Route.S46Export) },
                        onOpenSession = { nav.go(Route.S5ChatRunning) },
                    )

                    Route.S37ApiKeyConfigured -> S37ApiKeyConfigured(
                        onBack = { nav.backOrHome() },
                        onRetryCheck = { toast = "密钥校验通过" },
                        onUpdateKey = { nav.go(Route.S14ApiKey) },
                        onDeleteProvider = { nav.go(Route.S39ProviderEdit) },
                    )

                    Route.S39ProviderEdit -> S39ProviderEdit(
                        onBack = { nav.backOrHome() },
                        onRefillKey = { nav.go(Route.S14ApiKey) },
                        onDelete = { toast = "提供方已删除（演示）" },
                        onSave = {
                            toast = "提供方已保存"
                            nav.backOrHome()
                        },
                    )

                    // —— v20 S40–S48：动作落地页 ——
                    Route.S40NewTask -> S40NewTask(
                        onBack = { nav.backOrHome() },
                        onUsePreset = { nav.go(Route.S33AgentPresets) },
                        onPickWorkspace = { nav.go(Route.S41PickFolder) },
                        onPickModel = { sheet = SheetKind.Model },
                        onSave = {
                            toast = "任务已保存并启用（jobs-local）"
                            nav.backOrHome()
                        },
                    )

                    Route.S41PickFolder -> S41PickFolder(
                        onBack = { nav.backOrHome() },
                        onNewFolder = { toast = "新建文件夹需存储授权 · 演示页" },
                        onOpenTermux = { nav.go(Route.S27Termux) },
                        onConfirm = { name ->
                            toast = "已选择「$name」作工作区"
                            nav.backOrHome()
                        },
                    )

                    Route.S42CreatePlugin -> S42CreatePlugin(
                        onBack = { nav.backOrHome() },
                        onHotReload = { toast = "插件已热重载" },
                        onKeepDraft = { toast = "草稿已保留在本地" },
                        onExportSource = { toast = "源码已导出到工作区" },
                        onOpenPlugins = { nav.go(Route.S12Plugins) },
                    )

                    Route.S43AddModel -> S43AddModel(
                        onBack = { nav.backOrHome() },
                        onOpenProvider = { nav.go(Route.S25Provider) },
                        onSave = {
                            toast = "模型与推理等级映射已保存"
                            nav.backOrHome()
                        },
                    )

                    Route.S44IndexSource -> S44IndexSource(
                        onBack = { nav.backOrHome() },
                        onOpenMarket = { nav.go(Route.S21MarketDiscover) },
                        onVerify = { toast = "索引文件校验通过 · SHA-256 匹配" },
                        onAdd = {
                            toast = "索引源已添加并切换"
                            nav.backOrHome()
                        },
                    )

                    Route.S45GlobalSearch -> S45GlobalSearch(
                        onBack = { nav.backOrHome() },
                        onClear = { toast = "已清空搜索历史" },
                        onOpenSession = { nav.go(Route.S5ChatRunning) },
                        onOpenFile = { nav.go(Route.S11Workspace) },
                        onOpenPlugin = { nav.go(Route.S12Plugins) },
                        onOpenCommand = { nav.go(Route.S10Command) },
                        onRecent = { toast = "已填入搜索词：$it" },
                    )

                    Route.S46Export -> S46Export(
                        onBack = { nav.backOrHome() },
                        onDone = {
                            toast = vm.exportRedactedLog()
                            nav.backOrHome()
                        },
                    )

                    Route.S47GitCommit -> S47GitCommit(
                        onBack = { nav.backOrHome() },
                        onCommit = { toast = "已提交到本地仓库" },
                        onPush = { toast = "推送需配置远端 · 演示页" },
                        onRegenerate = { toast = "正在重新生成提交说明…" },
                        onManualEdit = { toast = "已切到手动编辑" },
                        onBackToSession = { nav.go(Route.S5ChatRunning) },
                        onOpenWorkspace = { nav.go(Route.S11Workspace) },
                    )

                    Route.S48Archived -> S48Archived(
                        onBack = { nav.backOrHome() },
                        onBatch = { toast = "已进入批量选择" },
                        onExportAll = { nav.go(Route.S46Export) },
                        onRestore = { toast = "会话已恢复到当前工作区" },
                        onDeleteSelected = { toast = "删除需二次确认 · 演示页" },
                        onOpenSession = { nav.go(Route.S5ChatRunning) },
                        onBackToDrawer = { drawerOpen = true },
                    )

                    Route.S49NewTermuxSession -> S49NewTermuxSession(
                        onBack = { nav.backOrHome() },
                        onPickWorkspace = { nav.go(Route.S41PickFolder) },
                        onCreate = {
                            // 建完回到 S27 终端窗口（会话列表里可切换），不压新栈
                            toast = "终端会话已创建"
                            nav.backOrHome()
                        },
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

                SheetKind.Model -> Unit

                // —— v20：S3 右上角「本机服务与快捷入口」——
                SheetKind.Service -> S3ServiceSheet(
                    visible = true,
                    onDismiss = { sheet = SheetKind.None },
                    serviceRunning = connection == DshViewModel.ClientPhase.Ready,
                    servicePort = ":3081",
                    codeViewOn = codeViewOn,
                    onCodeViewChange = { codeViewOn = it },
                    onTermux = {
                        sheet = SheetKind.None
                        nav.go(Route.S27Termux)
                    },
                    onNewSession = {
                        sheet = SheetKind.None
                        nav.go(Route.S49NewTermuxSession)
                    },
                    onExport = {
                        sheet = SheetKind.None
                        nav.go(Route.S46Export)
                    },
                    onUsage = {
                        sheet = SheetKind.None
                        nav.go(if (apiKey.configured) Route.S15Account else Route.S32AccountLoggedOut)
                    },
                    onAllSettings = {
                        sheet = SheetKind.None
                        nav.go(Route.S14Settings)
                    },
                )

                // —— v20：S38 退出登录二次确认 ——
                SheetKind.Logout -> S38LogoutSheet(
                    visible = true,
                    onDismiss = { sheet = SheetKind.None },
                    onConfirm = {
                        sheet = SheetKind.None
                        // v27：真动作 —— 真的把凭据从 dsh 凭据存储里删掉
                        // （dsh 契约：移除 = 删除那一行，置空会被存储拒绝）
                        vm.removeApiKey()
                        toast = "已清除本机 API Key"
                    },
                )

                SheetKind.None -> Unit
            }

            // —— S29 模型选择器（点 S3 输入框右下角模型名唤起）——
            if (sheet == SheetKind.Model) {
                S29ModelSheet(
                    visible = true,
                    onDismiss = { sheet = SheetKind.None },
                    selModelName = modelName,
                    selThinking = thinking,
                    autoOn = autoModel,
                    onSelect = { name, t -> vm.setModelDisplay(name, t) },
                    onAuto = { vm.setAutoModel(it) },
                    onAccount = {
                        sheet = SheetKind.None
                        nav.go(if (apiKey.configured) Route.S15Account else Route.S32AccountLoggedOut)
                    },
                    onDone = { sheet = SheetKind.None },
                )
            }

            // —— v23：S3 输入框敲 "@" 的「提及文件或会话」Sheet ——
            if (mentionOpen) {
                S3MentionSheet(
                    visible = true,
                    onDismiss = { mentionOpen = false },
                    sessions = sessions,
                    onPickSession = { s ->
                        mentionOpen = false
                        // 选中会话 → 把「@标题 」追加到输入框（保留已输入的指令前缀）
                        homeInput = homeInput.trimEnd() + "@" + s.title + " "
                    },
                )
            }

            // —— v25：真实权限审批 Sheet（ACP session/request_permission）——
            // 引擎反向请求时自动弹出，与当前路由/当前 Sheet 无关 —— 这是进程级模态，
            // dsh 那轮提示词正挂着等应答。按钮按协议 options 渲染，optionId 原样回传。
            pendingPermission?.let { perm ->
                val tc = vm.toolCallOf(perm.toolCallId)
                S7ApprovalSheet(
                    visible = true,
                    permission = perm,
                    toolName = tc?.name.orEmpty(),
                    toolDetail = tc?.detail.orEmpty(),
                    onCancel = { vm.cancelPermission() },
                    onRespond = { vm.respondPermission(it) },
                )
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
                onAccount = {
                    drawerOpen = false
                    nav.go(if (apiKey.configured) Route.S15Account else Route.S32AccountLoggedOut)
                },
                onTermux = { drawerOpen = false; nav.go(Route.S27Termux) },
                onArchived = { drawerOpen = false; nav.go(Route.S48Archived) },
                liveSessions = sessions,
                keyConfigured = apiKey.configured,
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

        // 返回键：先取消权限审批 → 关浮动层 → 再退栈 → 首页时退出 App
        // v25：审批 Sheet 是引擎级模态（dsh 那轮挂着等应答），优先级最高；
        // 按取消应答 = 协议层的 rejected/cancelled，不能假装没发生。
        BackHandler(
            enabled = pendingPermission != null || mentionOpen ||
                sheet != SheetKind.None || nav.canGoBack,
        ) {
            when {
                pendingPermission != null -> vm.cancelPermission()
                mentionOpen -> mentionOpen = false
                sheet != SheetKind.None -> sheet = SheetKind.None
                else -> nav.backOrHome()
            }
        }
    }
}