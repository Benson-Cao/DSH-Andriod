package com.dsh.harness.mobile.ui

import android.webkit.WebView
import com.dsh.harness.MainActivity
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/**
 * v31.2 · 设置数据桥 —— Compose 设置页读写 dsh web 设置的通道（当前仅保留骨架）。
 *
 * ══ 协议考古结论（2026-11-02 真机实测，后续接 WS RPC 时直接用）══
 * dsh 0.2.x 的设置（通用/外观/语言/权限/模型/插件/Agent 预设）持久化在服务端
 * settings 文档，网页面板通过 **WebSocket RPC**（dsh-client-connection 网关）读写；
 * HTTP POST /api/settings/describe 是网关的镜像入口，信封 schema（zod，源码
 * dsh-client-connection/lib/index.js "Client request envelope"）：
 *
 * ```json
 * {
 *   "type": "client-request",          // 必填字面量
 *   "rpcId": "任意字符串",
 *   "method": "settings/describe",     // 斜杠形式，必须与 URL path 一致
 *   "payload": { "args": [...] }       // "Remote payload must contain exactly
 * }                                    //  one plain-object args field" —— 形态
 * ```
 * 响应：`{"type":"server-response","rpcId":..,"result":{"ok":true,"value":..}}`。
 * 鉴权走浏览器 cookie；Compose/外部 HTTP 无 cookie → 网关挂起/401。
 * 真机实测：payload 的 args 精确形态仍未校准（`{args:[]}` 报同一错误），
 * 网页前端实际不发 HTTP fetch（hook 抓不到），全部走 WS。
 *
 * ══ 当前形态 ══
 * 保留 evalJson 通用通道（原生页 → WebView 同源 JS 的回程已验证可用，
 * `__dshOpenWebSettings` 同款机制）；describe/mutate 常量留作 WS 打通后的落点，
 * AppSettings v31.2 暂不调用（避免每次进页发必失败请求）。
 */
object SettingsBridge {

    private const val TAG = "SettingsBridge"

    private val seq = AtomicLong(0)

    /** 一次桥调用的结果。 */
    sealed class Result {
        data class Ok(val json: String) : Result()
        data class Err(val message: String) : Result()
    }

    /**
     * 在主 WebView 里执行一段返回 Promise 的 JS 表达式，await 后回传 JSON 文本。
     * expression 必须是一个**表达式**（不是语句），其值是 Promise<string（JSON）>。
     * 超时 timeoutMs 后回调 Err（evaluateJavascript 本身无超时，用线程兜底）。
     */
    fun evalJson(
        expression: String,
        timeoutMs: Long = 8000L,
        onDone: (Result) -> Unit,
    ) {
        val webView: WebView? = try {
            MainActivity.sInstance?.get()?.webViewRef()
        } catch (_: Throwable) { null }

        if (webView == null) {
            onDone(Result.Err("主界面 WebView 不可用"))
            return
        }
        val callId = seq.incrementAndGet()
        // 包一层：Promise 序列化 + 超时 + 异常兜底，全部在 JS 侧完成
        val wrapped = """
        (function(){
          var __p = Promise.resolve().then(function(){ return (%EXPR%); });
          var __t = new Promise(function(_,rej){ setTimeout(function(){ rej(new Error('bridge timeout')); }, %T%); });
          return Promise.race([__p, __t])
            .then(function(v){ return JSON.stringify({__id:%ID%, ok:true, v:v}); })
            .catch(function(e){ return JSON.stringify({__id:%ID%, ok:false, m:String(e && e.message || e)}); });
        })()
        """.trimIndent()
            .replace("%EXPR%", expression)
            .replace("%T%", timeoutMs.toString())
            .replace("%ID%", callId.toString())

        val timeoutTimer = java.util.Timer()
        val delivered = java.util.concurrent.atomic.AtomicBoolean(false)
        timeoutTimer.schedule(object : java.util.TimerTask() {
            override fun run() {
                if (delivered.compareAndSet(false, true)) {
                    onDone(Result.Err("桥调用超时（${timeoutMs}ms）"))
                }
            }
        }, timeoutMs + 1500)

        android.util.Log.i(TAG, "bridge call#$callId expr=${wrapped.take(160)}")
        webView.evaluateJavascript(wrapped) { raw ->
            if (delivered.compareAndSet(false, true)) {
                timeoutTimer.cancel()
                onDone(parseReply(raw, callId))
            }
        }
    }

    private fun parseReply(raw: String?, callId: Long): Result {
        android.util.Log.i(TAG, "bridge raw[${raw?.take(120) ?: "null"}] len=${raw?.length}")
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text == "null") return Result.Err("WebView 无返回")
        return try {
            // evaluateJavascript 返回的是 JS 字符串字面量（带引号+转义），先剥一层
            val unquoted = if (text.startsWith("\"")) {
                JSONArray("[${text}]").getString(0)
            } else text
            val obj = JSONObject(unquoted)
            if (obj.optLong("__id") != callId) return Result.Err("回执序号不匹配")
            if (obj.optBoolean("ok")) Result.Ok(obj.optString("v", "null"))
            else Result.Err(obj.optString("m", "未知错误"))
        } catch (e: Throwable) {
            Result.Err("回执解析失败: ${e.message}")
        }
    }

    // ---------- settings API ----------

    /** settings.describe() 的原始回执（namespaces 数组的 JSON 文本，原样透传）。 */
    fun describe(onDone: (Result) -> Unit) {
        evalJson(DESCRIBE_EXPR, timeoutMs = 10_000L, onDone = onDone)
    }

    /**
     * settings.mutate(ns, ops, expectedRevision)。
     * opsJson: JSONArray([{op:'set',path:[...],value:...},{op:'unset',path:[...]}])
     */
    fun mutate(ns: String, opsJson: String, expectedRevision: Long?, onDone: (Result) -> Unit) {
        val expr = MUTATE_EXPR
            .replace("%NS%", JSONObject.quote(ns))
            .replace("%OPS%", opsJson)
            .replace("%REV%", if (expectedRevision == null) "undefined" else expectedRevision.toString())
        evalJson(expr, timeoutMs = 10_000L, onDone = onDone)
    }

    private const val DESCRIBE_EXPR =
        """fetch('/api/settings/describe',{method:'POST',headers:{'content-type':'application/json'},body:'[]'})
           .then(function(r){ if(!r.ok) throw new Error('HTTP '+r.status); return r.json()); })
           .then(function(d){ return JSON.stringify(d && d.namespaces || []); })"""

    private const val MUTATE_EXPR =
        """fetch('/api/settings/mutate',{method:'POST',headers:{'content-type':'application/json'},
           body:JSON.stringify([%NS%, %OPS%, %REV%])})
           .then(function(r){ if(!r.ok) throw new Error('HTTP '+r.status); return r.json()); })
           .then(function(d){ return JSON.stringify(d); })"""
}
