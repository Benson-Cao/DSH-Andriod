package com.dsh.harness.mobile.data

import android.app.Application
import android.util.Log
import java.io.File
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.dsh.harness.mobile.ui.screens.EventKind
import com.dsh.harness.mobile.ui.screens.TrajectoryEvent

/**
 * UI 与 ACP 之间的翻译层。
 *
 * 设计稿 → ACP 的对应关系（这是接数据层的全部意义）：
 *   S3 输入框工作区 → newSession(cwd)；未选工作区时输入禁用 → newSession 返回 null 则禁发
 *   S5 发送消息      → prompt(text)
 *   S5 工具调用组    → ToolCall 列表（session/update 的 tool_call 生命周期）
 *   S5 子代理        → subagent 事件
 *   S7 审批 Sheet    → pendingPermission（真实协议请求，用户点选后才应答）
 *   S4 侧栏会话列表  → listSessions(cwd)
 *   S9 模型/思考强度 → setConfigOption("model"/"reasoning_effort")
 */
class DshViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "DshVM"
        /** ACP thought_level 选项里「跟随模型默认」的哨兵值（dsh-acp: PROVIDER_DEFAULT_REASONING_VALUE） */
        const val PROVIDER_DEFAULT_REASONING = "provider-default"
    }

    private val client = DshAcpClient(app)

    /** v30.8：导出用量快照等需要 Context 的地方统一用这个（不依赖 getApplication()） */
    private val appCtx: Application = app

    // ==================== 对话状态 ====================

    data class ChatMessage(
        val id: String,
        val role: Role,            // User / Assistant / Thought
        val text: String,
        val streaming: Boolean = false,
    )

    enum class Role { User, Assistant, Thought }

    data class ToolCall(
        val id: String,
        val name: String,
        val detail: String = "",
        val status: Status = Status.Running,
        val cost: String = "",
    )

    enum class Status { Running, Done, Fail, Wait }

    data class ModelOption(val value: String, val name: String, val group: String)

    /** S4 侧栏的一条会话（来自 ACP session/list） */
    data class Session(
        val id: String,
        val title: String,
        val cwd: String,
        val updatedAt: String,
    )

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions

    /**
     * session/list 是否真的成功取到过。
     * v30.9：S48 归档页要区分「没有会话」与「进程没起来读不到」——
     * 空列表 + false 是后者的诚实空态，不能画成"暂无归档"。
     */
    private val _sessionsLoaded = MutableStateFlow(false)
    val sessionsLoaded: StateFlow<Boolean> = _sessionsLoaded

    /**
     * S8 轨迹用：把 session/update 的事件按时间顺序攒成时间线。
     * ACP 不支持历史回放，所以这里只记录**本次会话**的事件（进程内累积）。
     */
    private val _events = MutableStateFlow<List<com.dsh.harness.mobile.ui.screens.TrajectoryEvent>>(emptyList())
    val events: StateFlow<List<com.dsh.harness.mobile.ui.screens.TrajectoryEvent>> = _events

    // —— UI 观察的状态 ——
    private val _connection = MutableStateFlow(ClientPhase.Idle)
    val connection: StateFlow<ClientPhase> = _connection

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _tools = MutableStateFlow<List<ToolCall>>(emptyList())
    val tools: StateFlow<List<ToolCall>> = _tools

    private val _workspace = MutableStateFlow<String?>(null)
    /** 未选工作区 = null；对应设计稿"未选时输入框禁用" */
    val workspace: StateFlow<String?> = _workspace

    private val _models = MutableStateFlow<List<ModelOption>>(emptyList())
    val models: StateFlow<List<ModelOption>> = _models

    /**
     * S3 输入框右下角 / S29 模型选择器共享的"当前模型 + 思考强度"显示态。
     * 真实引擎连上时，`selectModel`/`selectThinking` 也会回写这里，
     * 让首页那行"DeepSeek-V4-Flash High ▾"始终反映最新选择。
     */
    private val _modelName = MutableStateFlow("DeepSeek-V4-Flash")
    val modelName: StateFlow<String> = _modelName
    // v30.9：值统一为 ACP reasoning_effort 真值（off/low/high/max，小写），
    // 因为回传 session/set_config_option 时必须原样送。显示层用 thinkingLabel() 转中文。
    private val _thinking = MutableStateFlow("high")
    val thinking: StateFlow<String> = _thinking

    /**
     * 当前模型真实支持的思考强度档位（来自 ACP configOptions 的 category=="thought_level"）。
     * 真值：DeepSeek 的 reasoning effort 硬校验只有 off/low/high/max 四档
     * （dsh-llm-deepseek/lib/index.js reasoningEffort()，其他值直接抛 UNSUPPORTED_REASONING_EFFORT），
     * 且**每个模型可不同**（模型不声明 reasoning 时该项整个不出现）。
     * 拿不到时回落四档，不拿设计稿的「低/中/高」充数（「中」不是真档位）。
     */
    private val _thinkingLevels = MutableStateFlow(listOf("off", "low", "high", "max"))
    val thinkingLevels: StateFlow<List<String>> = _thinkingLevels
    private val _thinkingLevelsKnown = MutableStateFlow(false)
    val thinkingLevelsKnown: StateFlow<Boolean> = _thinkingLevelsKnown
    private val _autoModel = MutableStateFlow(false)
    val autoModel: StateFlow<Boolean> = _autoModel

    /**
     * S29：单纯更新显示态（含自定义提供方等占位模型，不下发到真实引擎）。
     * v30.9：thinking 是 ACP 真值 off/low/high/max；保存时若会话活着也顺手下发，
     * 让「默认模型与思考强度」不只改显示。
     */
    fun setModelDisplay(name: String, thinking: String) {
        _modelName.value = name
        _thinking.value = thinking
        if (client.isActive()) {
            viewModelScope.launch { client.setConfigOption("reasoning_effort", thinking) }
        }
    }
    fun setAutoModel(on: Boolean) { _autoModel.value = on }

    private val _busy = MutableStateFlow(false)
    /** 运行中：对应 S5 的"执行中 · 第 N 步"与停止/排队条 */
    val busy: StateFlow<Boolean> = _busy

    /** 上下文占用百分比（来自 usage_update），对应设计稿右上角的 "34%" */
    private val _contextUsage = MutableStateFlow<Int?>(null)
    val contextUsage: StateFlow<Int?> = _contextUsage

    /**
     * v30.8：真实 token 用量。
     *
     * ACP 的 `usage_update` 只给当前上下文的 used/size，不给账单。
     * 这里在**每次 usage_update 时取 used 的增量**累加成「本次会话已用 token」，
     * 并记录最后一次上报的 size 作为上下文窗口上限 —— S35 用量页据此出真实数字。
     */
    private val _tokensUsed = MutableStateFlow(0L)
    val tokensUsed: StateFlow<Long> = _tokensUsed
    private val _tokensWindow = MutableStateFlow(0L)
    val tokensWindow: StateFlow<Long> = _tokensWindow
    /** 累计上报次数（= usage_update 到达的次数，用作"数据点数量"的诚实指标） */
    private val _usageSamples = MutableStateFlow(0)
    val usageSamples: StateFlow<Int> = _usageSamples
    /** 上一次 usage_update 上报的 used（用于算增量） */
    private var lastReportedUsed = 0L

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _diagnostics = MutableStateFlow<List<String>>(emptyList())
    val diagnostics: StateFlow<List<String>> = _diagnostics

    // ==================== 插件（S12 / S19 / S20） ====================

    /**
     * 内置插件目录。数据来自真实 `dsh --dump-config`，不是设计稿的示意名。
     * 本机 2026-10-18 实测：86 个条目，其中 3 个 disabled、33 个带 config。
     */
    private val catalogLoader = DshPluginCatalog(app)

    private val _catalog = MutableStateFlow(DshPluginCatalog.Catalog(emptyList(), null, 0L))
    val catalog: StateFlow<DshPluginCatalog.Catalog> = _catalog

    private val _catalogLoading = MutableStateFlow(false)
    val catalogLoading: StateFlow<Boolean> = _catalogLoading

    /** 进入插件页时才拉一次（跑一次 dump 要几秒，不该在冷启动就付这个代价） */
    fun refreshCatalog(force: Boolean = false) {
        if (_catalogLoading.value) return
        if (!force && _catalog.value.plugins.isNotEmpty()) return
        _catalogLoading.value = true
        viewModelScope.launch {
            val c = catalogLoader.load()
            _catalog.value = c
            _catalogLoading.value = false
            if (c.error != null) Log.w(TAG, "插件目录读取失败：${c.error}")
        }
    }

    /** S23「导出脱敏日志」：返回落盘路径，失败为 null */
    fun exportRedactedLog(): String? = catalogLoader.writeRedactedLog(_catalog.value)?.absolutePath

    /**
     * v30.8：S35「导出用量快照」——把本次会话的真实 token 用量写成 JSONL。
     * 数据全部来自 ACP usage_update 的真实上报，不含任何编造数字。
     * @return 落盘路径；失败为 null
     */
    fun exportUsageSnapshot(): String? = try {
        val dir = java.io.File(DshBootstrapAccess.homeDir(appCtx), ".dsh")
        if (!dir.exists()) dir.mkdirs()
        val f = java.io.File(dir, "usage-snapshot.jsonl")
        val line = org.json.JSONObject().apply {
            put("exportedAt", System.currentTimeMillis())
            put("model", _modelName.value)
            put("thinking", _thinking.value)
            put("tokensUsed", _tokensUsed.value)
            put("tokensWindow", _tokensWindow.value)
            put("usageSamples", _usageSamples.value)
            put("sessionCount", _sessions.value.size)
            put("messageCount", _messages.value.size)
            put("toolCallCount", _tools.value.size)
        }.toString()
        f.writeText(line + "\n")
        f.absolutePath
    } catch (t: Throwable) {
        Log.e(TAG, "写用量快照失败", t)
        null
    }

    // ==================== 插件市场精选目录（S21/S22） ====================

    /**
     * 社区市场精选目录。数据源是 npm 上的 dsh-plugin-catalog 包
     * （china 路由走 npmmirror），与 dshmarket 包自己的 regions.js 一致。
     * null = 还没拉过 / 拉失败。
     */
    private val _marketCatalog = MutableStateFlow<DshMarketCatalog.Catalog?>(null)
    val marketCatalog: StateFlow<DshMarketCatalog.Catalog?> = _marketCatalog

    private val _marketCatalogLoading = MutableStateFlow(false)
    val marketCatalogLoading: StateFlow<Boolean> = _marketCatalogLoading

    /** 拉取失败原因（网络不可达 / 解析失败）；重试会清掉 */
    private val _marketCatalogError = MutableStateFlow<String?>(null)
    val marketCatalogError: StateFlow<String?> = _marketCatalogError

    /** 进入市场页时拉一次；已在 loading 或已有数据则跳过 */
    fun refreshMarketCatalog(force: Boolean = false) {
        if (_marketCatalogLoading.value) return
        if (!force && _marketCatalog.value != null) return
        _marketCatalogLoading.value = true
        _marketCatalogError.value = null
        viewModelScope.launch {
            try {
                _marketCatalog.value = DshMarketCatalog.load()
            } catch (t: Throwable) {
                Log.w(TAG, "市场目录拉取失败", t)
                _marketCatalogError.value = t.message ?: "市场目录拉取失败"
            } finally {
                _marketCatalogLoading.value = false
            }
        }
    }

    // ==================== 权限审批（S7） ====================

    /**
     * 待审批的权限请求（ACP session/request_permission）。null = 当前没有。
     *
     * v25 前客户端在协议层**自动放行**（扫 options 选 allow 直接回），
     * UI 上的 S7 Sheet 只是演示：点「拒绝」命令照样执行（用户实测截图）。
     * 现在请求先落到这个 StateFlow，由 UI 弹 Sheet、用户点选后应答。
     */
    val pendingPermission: StateFlow<DshAcpClient.PendingPermission?> = client.pendingPermission

    /** 用户点了某个协议选项（optionId 原样回传，如 "allow-once"） */
    fun respondPermission(optionId: String) {
        client.respondPermission(optionId)
    }

    /** 用户取消（拖走 Sheet / 返回键）——按 cancelled 应答，dsh 侧视为拒绝 */
    fun cancelPermission() {
        client.respondPermission(null)
    }

    /** 审批 Sheet 展示用：按 toolCallId 找触发工具（协议还没推 tool_call 时返回 null） */
    fun toolCallOf(toolCallId: String): ToolCall? =
        _tools.value.firstOrNull { it.id == toolCallId }

    // ==================== API 密钥（S19 API Key） ====================

    private val credentials = DshCredentials(app)

    /** 密钥状态。**只反映存在性，绝不携带明文** —— dsh 的 describe 也是这个契约。 */
    private val _apiKey = MutableStateFlow(DshCredentials.KeyState())
    val apiKey: StateFlow<DshCredentials.KeyState> = _apiKey

    /** 最近一次写入的结果；null = 还没写过。用于页面区分"保存中/已保存/失败"。 */
    private val _apiKeySaved = MutableStateFlow<DshCredentials.KeyState?>(null)
    val apiKeySaved: StateFlow<DshCredentials.KeyState?> = _apiKeySaved

    /** 读一次密钥状态（进入页面 / 保存后刷新） */
    fun refreshApiKey() {
        viewModelScope.launch {
            _apiKey.value = credentials.describe()
        }
    }

    /**
     * 保存密钥到 dsh 的凭据存储。
     * 写完顺手重读一次状态 —— 能立刻确认文件真的落盘了，而不是假设成功。
     */
    fun saveApiKey(value: String) {
        viewModelScope.launch {
            _apiKeySaved.value = DshCredentials.KeyState(configured = false)
            val r = credentials.set(DshCredentials.DEEPSEEK_KEY, value)
            _apiKeySaved.value = r
            _apiKey.value = credentials.describe()
            Log.i(TAG, "保存 API key：${if (r.error == null) "已写入" else "失败 ${r.error}"}")
        }
    }

    /** 删除密钥。文档：移除 = 删除该行，置空会被存储拒绝。 */
    fun removeApiKey() {
        viewModelScope.launch {
            val r = credentials.unset(DshCredentials.DEEPSEEK_KEY)
            _apiKeySaved.value = r
            _apiKey.value = credentials.describe()
        }
    }

    enum class ClientPhase { Idle, Starting, Ready, Failed }

    init {
        client.onUpdate = { params -> params?.let { onSessionUpdate(it) } }
        viewModelScope.launch {
            // Channel 不是 Flow，要先 receiveAsFlow() 才能在 ViewModel 里消费
            client.logFlow.receiveAsFlow().collect { line ->
                _diagnostics.value = (_diagnostics.value + line).takeLast(200)
            }
        }
        viewModelScope.launch {
            client.lastError.collect { _error.value = it }
        }
    }

    // ==================== 生命周期 ====================

    /** App 冷启动时调用：拉起 dsh ACP 进程并握手 */
    /** 诊断面板的「重试连接」：先停掉半死的进程再重来 */
    fun retry() {
        client.stop()
        _connection.value = ClientPhase.Idle
        _error.value = null
        connect()
    }

    fun connect() {
        if (_connection.value == ClientPhase.Ready || _connection.value == ClientPhase.Starting) return
        _connection.value = ClientPhase.Starting
        viewModelScope.launch {
            val ok = client.start(viewModelScope)
            _connection.value = if (ok) ClientPhase.Ready else ClientPhase.Failed
            if (ok) {
                // 握手成功就立刻建一个默认工作区会话，
                // 让 S3 顶部胶囊有真实数据可显示
                val home = DshBootstrapAccess.resolveWorkspace(getApplication(), null).absolutePath
                newSession(home)
            }
        }
    }

    fun disconnect() {
        client.stop()
        _connection.value = ClientPhase.Idle
        _messages.value = emptyList()
        _tools.value = emptyList()
        _workspace.value = null
    }

    /** 选工作区（S3 的 ws-pill / S3 工作区 Sheet） */
    fun newSession(absolutePath: String) {
        viewModelScope.launch {
            _busy.value = true
            val ok = client.newSession(absolutePath)
            _busy.value = false
            if (ok != null) {
                _workspace.value = absolutePath
                _messages.value = emptyList()
                _tools.value = emptyList()
                _contextUsage.value = null
                // v30.8：新会话 → token 用量归零（否则跨会话累加会算错）
                _tokensUsed.value = 0L
                _tokensWindow.value = 0L
                _usageSamples.value = 0
                lastReportedUsed = 0L
                parseConfigOptions()
            } else {
                _workspace.value = null
                _error.value = "无法创建会话（工作区不可用）"
            }
        }
    }

    /** 发送（S3/S5 的发送键）。没工作区时不发——设计稿规则原样保留。 */
    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        if (_workspace.value == null) {
            _error.value = "先选择工作区"
            return
        }
        _messages.value = _messages.value + ChatMessage(
            id = "u-${System.nanoTime()}", role = Role.User, text = clean,
        )
        viewModelScope.launch {
            _busy.value = true
            // v30.24：首条消息给会话起标题（App 侧注册表，引擎不落盘标题）
            rememberSessionTitle(client.currentSessionId(), clean)
            val stop = client.prompt(clean)
            _busy.value = false
            // 把助手消息的"流式"标记关掉（下一轮 chunk 会另起一条）
            _messages.value = _messages.value.map {
                if (it.streaming) it.copy(streaming = false) else it
            }
            if (stop == null) {
                // 具体原因由 client 的 lastError 提供（比如缺 API key），
                // 这里不覆盖它，避免把有用的诊断信息冲掉
                if (_error.value == null) _error.value = "发送失败，详见顶栏引擎诊断"
            } else if (stop == "cancelled") {
                _error.value = "已停止"
            }
        }
    }

    /** S9 切换模型（真实引擎）；同步回写显示态让首页那行模型名跟着变 */
    fun selectModel(optionValue: String) {
        val opt = _models.value.firstOrNull { it.value == optionValue }
        if (opt != null) _modelName.value = opt.name
        viewModelScope.launch { client.setConfigOption("model", optionValue) }
    }

    /**
     * 解析 session/new 返回的 configOptions，取出真实模型目录。
     * 结构（2026-10-17 本机实测）：
     *   [ { id:"model", category:"model", type:"select",
     *       options:[ { group:"deepseek-official", name:"DeepSeek",
     *                   options:[ { value:"[\"deepseek-official\",\"deepseek-v4-flash\"]",
     *                              name:"DeepSeek-V4-Flash", description:"..." } ] } ] } ]
     * 注意 value 是 **JSON 字符串**，回传给 set_config_option 时要原样送。
     */
    private fun parseConfigOptions() {
        val arr = client.configOptions()
        val levels = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val opt = arr.optJSONObject(i) ?: continue
            if (opt.optString("category") != "thought_level") continue
            val opts = opt.optJSONArray("options") ?: continue
            for (k in 0 until opts.length()) {
                val v = opts.optJSONObject(k)?.optString("value") ?: continue
                if (v.isNotEmpty() && v != PROVIDER_DEFAULT_REASONING) levels.add(v)
            }
        }
        if (levels.isNotEmpty()) {
            _thinkingLevels.value = levels
            _thinkingLevelsKnown.value = true
        }
        val out = ArrayList<ModelOption>()
        for (i in 0 until arr.length()) {
            val opt = arr.optJSONObject(i) ?: continue
            if (opt.optString("category") != "model") continue
            val groups = opt.optJSONArray("options") ?: continue
            for (g in 0 until groups.length()) {
                val groupObj = groups.optJSONObject(g) ?: continue
                val groupName = groupObj.optString("group")
                val items = groupObj.optJSONArray("options") ?: continue
                for (k in 0 until items.length()) {
                    val it = items.optJSONObject(k) ?: continue
                    val v = it.optString("value")
                    val n = it.optString("name")
                    if (v.isNotEmpty() && n.isNotEmpty()) {
                        out.add(ModelOption(value = v, name = n, group = groupName))
                    }
                }
            }
        }
        if (out.isNotEmpty()) {
            _models.value = out
            Log.i(TAG, "已解析 ${out.size} 个模型：${out.map { it.name }}")
        }
    }

    /** S9 切换思考强度（真实引擎）；同步回写显示态 */
    fun selectThinking(value: String) {
        _thinking.value = value
        viewModelScope.launch { client.setConfigOption("reasoning_effort", value) }
    }

    // ==================== v30.24 会话标题（App 侧注册表） ====================

    /**
     * ACP session/list 的 title 恒为空（dsh 引擎不落盘标题，会话本体是
     * session.v3.jsonl.zstd，App 侧没有 zstd 解码器读不回历史消息）。
     * 所以标题由 App 自己记：对本会话发出的**第一条消息**取前 24 字，
     * 存 `<home>/.dsh/session-titles.json`（原子写：tmp+rename）。
     * 展示优先级：引擎 title > 本地注册表 > 「未命名会话」。
     */
    private val sessionTitles = HashMap<String, String>()
    private var sessionTitlesLoaded = false

    private fun titlesFile(): File =
        File(DshBootstrapAccess.homeDir(getApplication()), ".dsh/session-titles.json")

    /** 只在 refreshSessions 前调用一次；读失败静默（等于全部回落"未命名会话"） */
    private fun loadSessionTitlesLocked() {
        if (sessionTitlesLoaded) return
        sessionTitlesLoaded = true
        try {
            val f = titlesFile()
            if (!f.exists()) return
            val obj = JSONObject(f.readText())
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.optString(k)
                if (k.isNotEmpty() && v.isNotEmpty()) sessionTitles[k] = v
            }
        } catch (t: Throwable) {
            Log.w(TAG, "会话标题注册表读取失败", t)
        }
    }

    /** 原子写注册表（tmp + rename），只动自己的文件，绝不碰 sessions/ 本体 */
    private fun saveSessionTitlesLocked() {
        try {
            val obj = JSONObject()
            sessionTitles.forEach { (k, v) -> obj.put(k, v) }
            val text = obj.toString()
            val f = titlesFile()
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(f)) {
                f.writeText(text)
                tmp.delete()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "会话标题注册表写入失败", t)
        }
    }

    /** 首条消息 → 标题（first-wins：已有标题的会话不覆盖） */
    private fun rememberSessionTitle(id: String?, firstMessage: String) {
        if (id.isNullOrEmpty()) return
        val clean = firstMessage.replace(Regex("\\s+"), " ").trim()
        if (clean.isEmpty()) return
        synchronized(sessionTitles) {
            if (sessionTitles.containsKey(id)) return
            sessionTitles[id] = if (clean.length <= 24) clean else clean.take(24) + "…"
            saveSessionTitlesLocked()
        }
    }

    /**
     * v30.25：从侧栏点击会话 → 恢复该会话并切到对话页。
     * 恢复后清空本地消息/工具/用量（ACP 不回放历史），让 S5 以干净态进入该会话；
     * workspace 设为该会话的 cwd，输入区恢复可用。
     */
    fun openSession(session: Session) {
        viewModelScope.launch {
            _busy.value = true
            val ok = client.resumeSession(session.id)
            _busy.value = false
            if (ok != null) {
                _workspace.value = session.cwd.ifEmpty { null }
                _messages.value = emptyList()
                _tools.value = emptyList()
                _contextUsage.value = null
                lastReportedUsed = 0L
                _tokensUsed.value = 0L
                _tokensWindow.value = 0L
                _usageSamples.value = 0
                parseConfigOptions()
            } else {
                _error.value = "恢复会话失败（引擎无响应）"
            }
        }
    }

    /**
     * S4 侧栏拉会话列表（来自 ACP session/list）。
     * v30.25 修正：抽屉是**全局会话列表**，必须列出全部会话，不能按当前工作区(_workspace)过滤——
     * 否则 openSession 把 _workspace 设成某会话的 cwd 后，刷新只列该工作区的会话，抽屉直接变空。
     */
    fun refreshSessions() {
        viewModelScope.launch {
            val arr = client.listSessions(null)
            if (arr == null) {
                _sessions.value = emptyList()
                _sessionsLoaded.value = false
                return@launch
            }
            _sessionsLoaded.value = true
            synchronized(sessionTitles) { loadSessionTitlesLocked() }
            val list = ArrayList<Session>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("sessionId")
                val localTitle = synchronized(sessionTitles) { sessionTitles[id] }
                list.add(
                    Session(
                        id = id,
                        title = o.optString("title").ifEmpty {
                            localTitle ?: "未命名会话"
                        },
                        cwd = o.optString("cwd"),
                        updatedAt = o.optString("updatedAt"),
                    )
                )
            }
            _sessions.value = list
        }
    }

    /**
     * v30.23：删除会话（真实后端 = 会话目录删除）。
     *
     * ACP 协议没注册 session/delete（dsh-acp 只有 new/list/resume/close/setConfigOption/
     * prompt/cancel），但会话本体就是 `<home>/.dsh/sessions/<cwd分组>/<sessionId>/` 下的
     * session.v3.jsonl.zstd —— App 自己拥有这个文件系统，直接删目录再刷新列表，
     * session/list 自然不再返回它。这是真删除，不是画假按钮。
     */
    fun deleteSession(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val root = File(DshBootstrapAccess.homeDir(getApplication()), ".dsh/sessions")
            var removed = false
            root.listFiles()?.forEach { group ->
                if (!group.isDirectory) return@forEach
                val dir = File(group, id)
                if (dir.exists() && dir.isDirectory) {
                    removed = dir.deleteRecursively() || removed
                }
            }
            Log.i(TAG, "deleteSession($id) removed=$removed")
            synchronized(sessionTitles) {
                if (sessionTitles.remove(id) != null) saveSessionTitlesLocked()
            }
            refreshSessions()
        }
    }

    /**
     * v30.23：归档会话 = 把会话目录移出 sessions/（session/list 扫描不到即从列表消失），
     * 落到 `<home>/.dsh/sessions-archive/<cwd分组>/<sessionId>/`，文件原样保留可恢复。
     */
    fun archiveSession(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val home = DshBootstrapAccess.homeDir(getApplication())
            val root = File(home, ".dsh/sessions")
            val archive = File(home, ".dsh/sessions-archive")
            var moved = false
            root.listFiles()?.forEach { group ->
                if (!group.isDirectory) return@forEach
                val dir = File(group, id)
                if (dir.exists() && dir.isDirectory) {
                    val dest = File(File(archive, group.name), id)
                    dest.parentFile?.mkdirs()
                    moved = dir.renameTo(dest) || moved
                }
            }
            Log.i(TAG, "archiveSession($id) moved=$moved")
            refreshSessions()
        }
    }

    /** 停止当前提示词（S5 的"停止"按钮） */
    fun cancel() {
        viewModelScope.launch {
            client.cancel()
            _busy.value = false
        }
    }

    fun clearError() { _error.value = null }

    // ==================== 协议事件 → UI 状态 ====================

    /**
     * session/update 的标准形状（见 dsh-acp README 协议表）：
     *   { sessionId, update: { sessionUpdate: "<kind>", ... } }
     * 具体 kind 在本机实测里见过 user_message_chunk / agent_message_chunk /
     * agent_thought_chunk / tool_call / tool_call_update 等；
     * 这里对未知 kind 采取"忽略但不崩"的策略。
     */
    private fun onSessionUpdate(params: JSONObject) {
        val update = params.optJSONObject("update") ?: return
        val kind = update.optString("sessionUpdate")
        Log.i(TAG, "session/update kind=$kind")

        when (kind) {
            "user_message_chunk" -> {
                val t = chunkText(update)
                if (t.isNotEmpty()) {
                    _messages.value = _messages.value + ChatMessage(
                        id = "u-${System.nanoTime()}", role = Role.User, text = t,
                    )
                    pushEvent(EventKind.User, "用户输入", t.take(60))
                    // 一轮 = 一次用户提示触发一次模型请求（请求 #N 与轮次同号）
                    pushEvent(EventKind.Request, "模型请求", "")
                }
            }

            "agent_message_chunk" -> {
                val t = chunkText(update)
                if (t.isEmpty()) return
                val cur = _messages.value
                val last = cur.lastOrNull()
                // 连续 chunk 合并到同一条助手消息上（形成打字效果）
                if (last != null && last.role == Role.Assistant && last.streaming) {
                    _messages.value = cur.dropLast(1) + last.copy(text = last.text + t)
                } else {
                    _messages.value = cur + ChatMessage(
                        id = "a-${System.nanoTime()}",
                        role = Role.Assistant, text = t, streaming = true,
                    )
                }
            }

            "agent_thought_chunk" -> {
                val t = chunkText(update)
                if (t.isEmpty()) return
                val cur = _messages.value
                val last = cur.lastOrNull()
                if (last != null && last.role == Role.Thought && last.streaming) {
                    _messages.value = cur.dropLast(1) + last.copy(text = last.text + t)
                } else {
                    _messages.value = cur + ChatMessage(
                        id = "th-${System.nanoTime()}",
                        role = Role.Thought, text = t, streaming = true,
                    )
                }
            }

            "tool_call" -> {
                val id = update.optString("toolCallId")
                val title = update.optString("title").ifEmpty { update.optString("kind") }
                // ⚠️ rawInput 是**解析后的 JSON 对象**（见 dsh-acp/lib/index.js:596 的
                // parseToolArguments），不是字符串。用 optString 取对象会得到 ""，
                // 工具行的参数列在真机上是空的。
                val detail = describeToolInput(update.opt("rawInput"))
                _tools.value = _tools.value + ToolCall(
                    id = id,
                    name = title,
                    detail = detail,
                )
                // 子代理在官方语义里是「子工具」记录，而非独立大卡
                val kind = if (title.contains("subagent", ignoreCase = true)) EventKind.SubTool else EventKind.Tool
                pushEvent(kind, title, detail)
            }

            "tool_call_update" -> {
                val id = update.optString("toolCallId")
                val st = when (update.optString("status")) {
                    "completed" -> Status.Done
                    "failed" -> Status.Fail
                    else -> Status.Running
                }
                _tools.value = _tools.value.map {
                    if (it.id == id) it.copy(status = st) else it
                }
            }

            // 上下文占用 —— 对应设计稿 S5 右上角那个 "34%" chip
            "usage_update" -> {
                val used = update.optLong("used", 0L)
                val size = update.optLong("size", 0L)
                if (size > 0) {
                    _contextUsage.value = (used * 100.0 / size).toInt().coerceIn(0, 100)
                }
                // v30.8：累加真实 token 用量。
                // usage_update 的 used 是**当前上下文总量**，不是增量；
                // 所以增量 = used - 上次上报的 used（首次 = used 本身）。
                if (used > 0) {
                    val delta = maxOf(0L, used - lastReportedUsed)
                    lastReportedUsed = used
                    _tokensUsed.value = _tokensUsed.value + delta
                    _tokensWindow.value = maxOf(_tokensWindow.value, size)
                    _usageSamples.value = _usageSamples.value + 1
                }
            }

            else -> Unit  // 未知 kind 静默忽略，前端不该因协议扩展而崩
        }
    }

    /**
     * 把 rawInput 变成一行可读的参数摘要。
     * 协议给的是对象（parseToolArguments 的结果），常见形如
     *   {"path":"a.xlsx"} / {"command":"ls"} / {"path":"a","content":"..."}
     * 优先取 path / command / file_path 这类"人能看懂"的首要字段，
     * 都取不到再退回紧凑 JSON 并截断。
     */
    private fun describeToolInput(raw: Any?): String {
        when (raw) {
            null, JSONObject.NULL -> return ""
            is String -> return raw
            is JSONObject -> {
                for (k in listOf("path", "command", "file_path", "filePath", "url", "query")) {
                    val v = raw.optString(k)
                    if (v.isNotEmpty()) return v
                }
                // 取第一个非空值，避免 content 之类的长文本占满一行
                val keys = raw.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = raw.optString(k)
                    if (v.isNotEmpty() && k != "content") return "$k: $v"
                }
                return raw.toString().take(60)
            }
            else -> return raw.toString().take(60)
        }
    }

    /** ACP 的 chunk 结构是 { content: { type:"text", text } } 或直接 text 字段 */
    private fun chunkText(update: JSONObject): String {
        update.optString("text").takeIf { it.isNotEmpty() }?.let { return it }
        val content = update.optJSONObject("content") ?: return ""
        return content.optString("text")
    }

    /** 当前轮次：每来一条用户消息开新一轮（账本按「第 N 轮」分组用） */
    private var curRound = 0

    /** 往时间线追加一个事件（上限 200 条，防止长会话无限增长）。
     *  v13：记录类型用 EventKind，一轮 = 一条用户消息触发的 用户 + 请求#N + 工具/子工具。 */
    private fun pushEvent(kind: EventKind, title: String, detail: String, cost: String = "") {
        if (kind == EventKind.User) curRound++
        val inRound = kind != EventKind.Compress && kind != EventKind.Approval
        _events.value = (_events.value + TrajectoryEvent(
            kind = kind,
            title = title.ifBlank { "—" },
            detail = detail,
            cost = cost,
            round = if (inRound) curRound else 0,
            reqIndex = if (kind == EventKind.Request) curRound else 0,
        )).takeLast(200)
    }

    override fun onCleared() {
        super.onCleared()
        client.stop()
    }
}