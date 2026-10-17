package com.dsh.harness.mobile.data

import android.app.Application
import android.util.Log
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

/**
 * UI 与 ACP 之间的翻译层。
 *
 * 设计稿 → ACP 的对应关系（这是接数据层的全部意义）：
 *   S3 输入框工作区 → newSession(cwd)；未选工作区时输入禁用 → newSession 返回 null 则禁发
 *   S5 发送消息      → prompt(text)
 *   S5 工具调用组    → ToolCall 列表（session/update 的 tool_call 生命周期）
 *   S5 子代理        → subagent 事件
 *   S7 审批 Sheet    → PendingPermission（客户端目前自动放行，UI 上可改为手动）
 *   S4 侧栏会话列表  → listSessions(cwd)
 *   S9 模型/思考强度 → setConfigOption("model"/"reasoning_effort")
 */
class DshViewModel(app: Application) : AndroidViewModel(app) {

    companion object { private const val TAG = "DshVM" }

    private val client = DshAcpClient(app)

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

    private val _busy = MutableStateFlow(false)
    /** 运行中：对应 S5 的"执行中 · 第 N 步"与停止/排队条 */
    val busy: StateFlow<Boolean> = _busy

    /** 上下文占用百分比（来自 usage_update），对应设计稿右上角的 "34%" */
    private val _contextUsage = MutableStateFlow<Int?>(null)
    val contextUsage: StateFlow<Int?> = _contextUsage

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

    /** S9 切换模型 */
    fun selectModel(optionValue: String) {
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

    /** S9 切换思考强度 */
    fun selectThinking(value: String) {
        viewModelScope.launch { client.setConfigOption("reasoning_effort", value) }
    }

    /** S4 侧栏拉会话列表（来自 ACP session/list） */
    fun refreshSessions() {
        viewModelScope.launch {
            val cwd = _workspace.value
            val arr = client.listSessions(cwd)
            if (arr == null) {
                _sessions.value = emptyList()
                return@launch
            }
            val list = ArrayList<Session>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                list.add(
                    Session(
                        id = o.optString("sessionId"),
                        title = o.optString("title").ifEmpty { "未命名会话" },
                        cwd = o.optString("cwd"),
                        updatedAt = o.optString("updatedAt"),
                    )
                )
            }
            _sessions.value = list
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
                    pushEvent("U", "用户输入", t.take(60))
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
                pushEvent("T", title, detail)
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

    /** 往时间线追加一个事件（上限 200 条，防止长会话无限增长） */
    private fun pushEvent(tag: String, title: String, detail: String) {
        _events.value = (_events.value + com.dsh.harness.mobile.ui.screens.TrajectoryEvent(
            tag, title.ifBlank { "—" }, detail
        )).takeLast(200)
    }

    override fun onCleared() {
        super.onCleared()
        client.stop()
    }
}