package com.dsh.harness.mobile.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * DeepSeek Harness · ACP 客户端
 *
 * 为什么是 ACP 而不是 HTTP：dsh 只暴露 `web`（端口 + token + WebView）和
 * `@deepseek-ai/dsh-acp`（JSON-RPC over stdio）。后者无端口、无 token、
 * 进程级可控，且协议面正好覆盖设计稿需要的数据：
 *   session/new        → S3 首页输入
 *   session/prompt     → S5 发送消息
 *   session/update     → S5/S6 消息流、thought、工具生命周期、上下文用量
 *   session/request_permission → S7 审批 Sheet
 *   session/list       → S4 侧栏会话分组
 *   session/close      → 返回/关闭
 *
 * 协议契约（本机 2026-10-17 实测 validate 过initialize + session/new）：
 *   - stdout 是**换行分隔**的 JSON-RPC frame，一行一个 JSON；
 *   - `session/new` 的 `mcpServers` 是**必填**，缺了会返回 -32602；
 *   - `session/prompt` 每个会话一次一个提示词；
 *   - 服务端可能反向发起 `session/request_permission`，客户端要回一个 optionId。
 */
class DshAcpClient(private val context: Context) {

    companion object {
        private const val TAG = "DshAcp"
        /** dsh 启动冷启可能要 6~15s（node 冷启动），握手要等得起 */
        private const val HANDSHAKE_TIMEOUT_MS = 30_000L
        /** 单轮提示词可能包含多轮工具调用，给足时间 */
        private const val PROMPT_TIMEOUT_MS = 600_000L
    }

    // ==================== 对外状态 ====================

    enum class Phase { Idle, Starting, Ready, Failed }

    private val _phase = MutableStateFlow(Phase.Idle)
    val phase: StateFlow<Phase> = _phase

    private val _log = Channel<String>(Channel.UNLIMITED)
    /** 诊断用：把协议层与进程层的事件都放出来，便于 adb logcat / UI 排查 */
    val logFlow = _log

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    /**
     * 待用户审批的权限请求（ACP `session/request_permission`，设计稿 S7）。
     * null = 当前没有待确认的操作。
     *
     * 协议实证（dsh-acp lib/index.js:1115-1137）：params 形如
     *   { sessionId, toolCall:{toolCallId},
     *     options:[ {optionId:"allow-once",name:"Allow once",kind:"allow_once"},
     *               {optionId:"reject-once",name:"Reject",kind:"reject_once"} ] }
     * 应答 `{outcome:{outcome:"selected",optionId}}` 或 `{outcome:"cancelled"}`。
     * **没有 allow_always** —— 服务端只有一次性语义，UI 不能伪造"始终允许"。
     */
    private val _pendingPermission = MutableStateFlow<PendingPermission?>(null)
    val pendingPermission: StateFlow<PendingPermission?> = _pendingPermission

    private var nextId = 1
    private var sessionId: String? = null
    private var process: Process? = null

    @Volatile
    private var reader: BufferedReader? = null

    // ==================== 启动 ====================

    /**
     * 拉起 `node <dsh>/lib/bin.js --profile acp`。
     * 约定沿用现有 harness：node 在 $PREFIX/bin/node，环境变量读 termux 的 environment 文件。
     */
    suspend fun start(scope: CoroutineScope): Boolean = withContext(Dispatchers.IO) {
        if (_phase.value == Phase.Ready) return@withContext true

        _phase.value = Phase.Starting
        _lastError.value = null

        // 首次安装时 bundle 还没解压（node / dsh 都不存在）→ 必须先跑 bootstrap。
        // setupIfNeeded 内部有 stamp 判重，后续启动是秒级返回。
        try {
            emit("确保运行环境（bootstrap）…")
            DshBootstrapAccess.ensureBootstrapped(context)
        } catch (t: Throwable) {
            emit("bootstrap 异常：${t.message}")
        }

        val node = DshBootstrapAccess.nodeBin(context)
        if (!node.exists()) {
            fail("找不到 node：${node.absolutePath}")
            return@withContext false
        }

        // dsh 入口：$HOME/node_modules/@deepseek-ai/dsh/lib/bin.js（与 web 模式同一个文件）
        val binJs = DshBootstrapAccess.dshBinJs(context)
        if (!binJs.exists()) {
            fail(
                "找不到 dsh 入口：${binJs.absolutePath}\n" +
                    "（bundle 未解压？可先用终端入口跑一次初始化）"
            )
            return@withContext false
        }

        val env = DshBootstrapAccess.termuxEnv(context)

        return@withContext try {
            val pb = ProcessBuilder(
                node.absolutePath,
                // 真机 bundle 里的 node 是 v24+，cordis-plugin-hmr 需要 --expose-internals
                "--expose-internals",
                binJs.absolutePath,
                "--profile", "acp",
            )
            pb.directory(DshBootstrapAccess.homeDir(context))
            pb.environment().clear()
            env.forEach { (k, v) -> pb.environment()[k] = v }
            pb.redirectErrorStream(false)

            val p = pb.start()
            process = p

            // stderr 单独收：ACP 规定 stdout 只承载协议流量，日志必须走 stderr
            scope.launch(Dispatchers.IO) {
                try {
                    BufferedReader(InputStreamReader(p.errorStream)).use { er ->
                        var line: String? = er.readLine()
                        while (line != null) {
                            emit("[stderr] $line")
                            line = er.readLine()
                        }
                    }
                } catch (_: Exception) {
                }
            }

            // stdout：协议主循环
            val br = BufferedReader(InputStreamReader(p.inputStream))
            reader = br
            scope.launch(Dispatchers.IO) { readLoop(br) }

            emit("进程已启动 pid=${safePid(p)}")
            handshake()
        } catch (e: Exception) {
            fail("启动失败：${e.message}")
            false
        }
    }

    /** initialize +（可选）建一个会话 */
    private suspend fun handshake(): Boolean {
        val ok = request(
            "initialize",
            JSONObject()
                .put("protocolVersion", 1)
                .put("clientCapabilities", JSONObject())
        )
        if (!ok) {
            fail("initialize 无响应")
            return false
        }
        _phase.value = Phase.Ready
        emit("initialize 完成 → Ready")
        return true
    }

    // ==================== JSON-RPC 收发 ====================

    /** 发一个请求并等待同 id 的响应。 */
    private suspend fun request(method: String, params: JSONObject): Boolean {
        val id = nextId++
        val msg = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id)
            .put("method", method)
            .put("params", params)
        if (!send(msg)) return false
        // 等待 pending[id] 被 readLoop 兑现
        val deadline = System.currentTimeMillis() + HANDSHAKE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val r = pending[id]
            if (r != null) {
                pending.remove(id)
                return r
            }
            delay(30)
        }
        emit("请求超时：$method")
        return false
    }

    private val pending = java.util.concurrent.ConcurrentHashMap<Int, Boolean>()

    /** id → 结果。session/new 需要返回值，所以单独存。 */
    private val results = java.util.concurrent.ConcurrentHashMap<Int, JSONObject>()

    private fun send(obj: JSONObject): Boolean = try {
        val p = process
        if (p == null) {
            emit("send 失败：进程未启动")
            false
        } else {
            p.outputStream.write((obj.toString() + "\n").toByteArray())
            p.outputStream.flush()
            true
        }
    } catch (e: Exception) {
        emit("send 异常：${e.message}")
        false
    }

    private fun readLoop(br: BufferedReader) {
        try {
            while (true) {
                val line = br.readLine() ?: break
                if (line.isBlank()) continue
                val m = try {
                    JSONObject(line)
                } catch (e: Exception) {
                    emit("解析失败：${line.take(200)}")
                    continue
                }
                handle(m)
            }
        } catch (e: Exception) {
            emit("读取循环结束：${e.message}")
        } finally {
            if (_phase.value == Phase.Ready) _phase.value = Phase.Idle
        }
    }

    private fun handle(m: JSONObject) {
        // 1) 响应：有 id 有 result/error
        if (m.has("id") && !m.has("method")) {
            val id = m.getInt("id")
            if (m.has("error")) {
                emit("✗ id=$id error=${m.get("error")}")
                _lastError.value = m.optJSONObject("error")?.optString("message")
                    ?: m.optString("error")
            } else {
                results[id] = m.optJSONObject("result") ?: JSONObject()
                pending[id] = true
            }
            return
        }

        // 2) 服务端反向请求：权限审批（对应设计稿 S7）
        val method = m.optString("method")
        if (method == "session/request_permission") {
            handlePermissionRequest(m)
            return
        }

        // 3) 通知：session/update 等
        if (method.startsWith("session/")) {
            onUpdate?.invoke(m.optJSONObject("params"))
        }
    }

    /** 上层回调：把协议事件转成 UI 状态 */
    var onUpdate: ((JSONObject?) -> Unit)? = null

    /**
     * 权限请求：**只记录，不自动应答**。
     *
     * v24 及以前这里扫 options 自动选 allow 直接回协议 —— 真机上
     * 「点拒绝命令照样跑」，因为 UI 的三按钮和协议层完全脱节
     * （v25 用户实测截图：S7 Sheet 三个按钮都只是关窗）。
     * 现在只把请求存进 pendingPermission StateFlow 供 UI 弹 Sheet，用户选完再应答。
     */
    private fun handlePermissionRequest(m: JSONObject) {
        val id = m.optInt("id", -1)
        val params = m.optJSONObject("params")
        val toolCallId = params?.optJSONObject("toolCall")?.optString("toolCallId").orEmpty()
        val opts = ArrayList<PendingPermission.PermissionOption>()
        params?.optJSONArray("options")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                opts.add(
                    PendingPermission.PermissionOption(
                        optionId = o.optString("optionId"),
                        name = o.optString("name").ifEmpty { o.optString("kind") },
                        kind = o.optString("kind"),
                    )
                )
            }
        }
        // 服务端在收到应答前不会再发第二个（request/response 语义），
        // 但真出现就把旧的按取消应答掉，避免 dsh 侧那轮永久挂起。
        _pendingPermission.value?.let { stale ->
            emit("覆盖未应答的权限请求 id=${stale.requestId}（按取消应答）")
            sendPermissionOutcome(stale.requestId, null)
        }
        _pendingPermission.value = PendingPermission(
            requestId = id,
            toolCallId = toolCallId,
            options = opts,
        )
        emit("权限请求 toolCall=$toolCallId options=${opts.map { it.optionId }}（等待用户）")
    }

    /**
     * 用户对权限请求的应答。optionId = null 表示取消（拖走 Sheet / 返回键），
     * 协议层按 `outcome:"cancelled"` 回，dsh 侧视为拒绝。
     */
    fun respondPermission(optionId: String?) {
        val p = _pendingPermission.value ?: return
        _pendingPermission.value = null
        sendPermissionOutcome(p.requestId, optionId)
        emit("权限应答 → ${optionId ?: "cancelled"}")
    }

    private fun sendPermissionOutcome(requestId: Int, optionId: String?) {
        val outcome = JSONObject()
        if (optionId == null) {
            outcome.put("outcome", "cancelled")
        } else {
            outcome.put("outcome", "selected")
            outcome.put("optionId", optionId)
        }
        send(
            JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", requestId)
                .put("result", JSONObject().put("outcome", outcome))
        )
    }

    // ==================== 高层 API ====================

    /** session/new 返回的完整配置选项（含真实模型目录），供 UI 渲染 S9 */
    private var lastConfigOptions: JSONArray = JSONArray()

    fun configOptions(): JSONArray = lastConfigOptions

    /** 创建会话，返回 sessionId。设计稿：未选工作区时禁用输入，所以 cwd 由 UI 传。 */
    suspend fun newSession(cwd: String): String? = withContext(Dispatchers.IO) {
        val id = nextId++
        val params = JSONObject()
            .put("cwd", cwd)
            // mcpServers 是必填，空数组即可（本机实测：缺它会 -32602）
            .put("mcpServers", JSONArray())
        val msg = JSONObject()
            .put("jsonrpc", "2.0").put("id", id)
            .put("method", "session/new").put("params", params)
        if (!send(msg)) return@withContext null

        val deadline = System.currentTimeMillis() + HANDSHAKE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            results[id]?.let {
                val sid = it.optString("sessionId")
                if (sid.isNotEmpty()) {
                    sessionId = sid
                    lastConfigOptions = it.optJSONArray("configOptions") ?: JSONArray()
                    emit("会话已建：$sid（配置项 ${lastConfigOptions.length()} 个）")
                    return@withContext sid
                }
            }
            delay(30)
        }
        _lastError.value = "session/new 超时"
        null
    }

    /**
     * 发送提示词，**并等待本轮结束**。
     *
     * 协议说明：「每个会话一次一个提示词；Agent 空闲且有序更新交付后才结算」——
     * 也就是说 response 到达 == 本轮跑完。所以这里同步等待，
     * 否则 ViewModel 的 busy 永远不复位（上一版就踩了这个坑）。
     */
    suspend fun prompt(text: String): String? = withContext(Dispatchers.IO) {
        val sid = sessionId ?: return@withContext null
        val id = nextId++
        val params = JSONObject()
            .put("sessionId", sid)
            .put("prompt", JSONArray().put(JSONObject().put("type", "text").put("text", text)))
        if (!send(JSONObject()
                .put("jsonrpc", "2.0").put("id", id)
                .put("method", "session/prompt").put("params", params))) return@withContext null

        // 单轮可能跑很久（多轮工具调用），给足 10 分钟
        val deadline = System.currentTimeMillis() + PROMPT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            results.remove(id)?.let { r ->
                val stop = r.optString("stopReason")
                emit("本轮结束 stopReason=$stop")
                return@withContext stop.ifEmpty { "end_turn" }
            }
            delay(100)
        }
        emit("等待本轮结束超时")
        "timeout"
    }

    /** 列出持久会话（S4 侧栏用） */
    suspend fun listSessions(cwd: String? = null): JSONArray? = withContext(Dispatchers.IO) {
        val id = nextId++
        val params = JSONObject()
        if (cwd != null) params.put("cwd", cwd)
        if (!send(JSONObject()
                .put("jsonrpc", "2.0").put("id", id)
                .put("method", "session/list").put("params", params))) return@withContext null
        val deadline = System.currentTimeMillis() + HANDSHAKE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            results[id]?.let { return@withContext it.optJSONArray("sessions") ?: JSONArray() }
            delay(30)
        }
        null
    }

    /** 中断当前提示词（协议：session/cancel）。这是唯一真实的"停止"路径。 */
    suspend fun cancel(): Boolean = withContext(Dispatchers.IO) {
        val sid = sessionId ?: return@withContext false
        send(JSONObject()
            .put("jsonrpc", "2.0").put("id", nextId++)
            .put("method", "session/cancel")
            .put("params", JSONObject().put("sessionId", sid)))
        emit("已发送 session/cancel")
        true
    }

    /** 关闭会话 */
    suspend fun closeSession() = withContext(Dispatchers.IO) {
        val sid = sessionId ?: return@withContext
        send(JSONObject()
            .put("jsonrpc", "2.0").put("id", nextId++)
            .put("method", "session/close")
            .put("params", JSONObject().put("sessionId", sid)))
        sessionId = null
    }

    /** 切换模型 / 思考强度（S9 新会话设置用） */
    suspend fun setConfigOption(optionId: String, value: String): Boolean = withContext(Dispatchers.IO) {
        val sid = sessionId ?: return@withContext false
        val id = nextId++
        if (!send(JSONObject()
                .put("jsonrpc", "2.0").put("id", id)
                .put("method", "session/set_config_option")
                .put("params", JSONObject()
                    .put("sessionId", sid)
                    .put("optionId", optionId)
                    .put("value", value)))) return@withContext false
        true
    }

    // ==================== 生命周期 ====================

    fun stop() {
        try { process?.destroy() } catch (_: Exception) {}
        process = null
        sessionId = null
        // 进程没了，挂起的审批也不可能再有应答 → 清掉，别让 UI 卡在一张死 Sheet 上
        _pendingPermission.value = null
        _phase.value = Phase.Idle
    }

    private fun fail(msg: String) {
        _lastError.value = msg
        _phase.value = Phase.Failed
        emit("✗ $msg")
    }

    private fun emit(line: String) {
        Log.i(TAG, line)
        _log.trySend(line)
    }

    /** Process.pid() 是 API 26+；本工程 minSdk 21，用反射兜一下 */
    private fun safePid(p: Process): String = try {
        val m = Process::class.java.getMethod("pid")
        m.invoke(p).toString()
    } catch (_: Throwable) {
        "?"
    }

    /**
     * 一条待审批的权限请求（ACP `session/request_permission` 的解析结果）。
     *
     * 协议实证见 [handlePermissionRequest] 的注释：options 在真实 dsh 里固定是
     * allow-once / reject-once 两个**一次性**选项，所以设计稿的
     * 「本会话始终允许此类操作」没有协议对应物 —— S7 的按钮必须按 options
     * 渲染，而不是按设计稿硬编码三个。
     */
    data class PendingPermission(
        val requestId: Int,
        val toolCallId: String,
        val options: List<PermissionOption>,
    ) {
        data class PermissionOption(
            val optionId: String,
            val name: String,
            val kind: String,
        )
    }
}