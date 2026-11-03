package com.dsh.harness.mobile.data

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import com.dsh.harness.DshProcessManager
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * dsh 设置服务的真实数据客户端（v33）。
 *
 * 通道来源（源码取证，非猜测）：
 *   · `@deepseek-ai/dsh-client-connection/lib/client.browser.js`
 *     的 `createWebConnectionRpc().call()` —— 一元 RPC 就是一条普通 HTTP POST：
 *       POST /api/<endpoint>
 *       content-type: application/json
 *       body: {"type":"client-request","rpcId":"<uuid>","method":"<endpoint>","payload":{"args":{...}}}
 *     响应：{"type":"server-response","rpcId":"<同一个>","result":{"ok":true,"value":...}}
 *   · `@deepseek-ai/dsh-api-settings-controller/lib/typert.remote-client.d.ts`
 *     的方法表：settings/describe、settings/update、settings/replace、settings/mutate，
 *     以及 credentials/describe|set|unset。
 *
 * 鉴权：WebView 已经把会话 cookie 存在 CookieManager（同源 127.0.0.1:端口），
 *   这里直接取来放进 Cookie 头。**没有 cookie 的请求会被服务端挂起不返回**
 *   （真机实测：裸请求 60s 无响应），所以 cookie 是必须的，不是可选项。
 */
object DshSettingsApi {
    private const val TAG = "DshSettingsApi"

    private fun baseUrl(): String = "http://127.0.0.1:${DshProcessManager.DSH_PORT}"

    /** 取 WebView 同源 cookie；拿不到返回空串（调用方按失败处理）。 */
    private fun cookie(): String {
        return try {
            CookieManager.getInstance().getCookie(baseUrl()) ?: ""
        } catch (t: Throwable) {
            Log.w(TAG, "读取 cookie 失败", t)
            ""
        }
    }

    /**
     * 一元 RPC。返回 `result` 对象（含 ok/value/error）；网络或协议失败返回 null。
     * @param args 方法参数对象（会包进 payload.args）
     */
    fun call(ctx: Context, endpoint: String, args: JSONObject = JSONObject()): JSONObject? {
        val rpcId = UUID.randomUUID().toString()
        val payload = JSONObject()
            .put("type", "client-request")
            .put("rpcId", rpcId)
            .put("method", endpoint)
            .put("payload", JSONObject().put("args", args))
        val body = payload.toString().toByteArray(Charsets.UTF_8)
        val ck = cookie()
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("${baseUrl()}/api/$endpoint").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 6000
                readTimeout = 10000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                if (ck.isNotEmpty()) setRequestProperty("Cookie", ck)
            }
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                Log.w(TAG, "$endpoint HTTP $code: ${text.take(300)}")
                return null
            }
            val envelope = JSONObject(text)
            if (envelope.optString("type") != "server-response") {
                Log.w(TAG, "$endpoint 非 server-response: ${text.take(300)}")
                return null
            }
            if (envelope.optString("rpcId") != rpcId) {
                Log.w(TAG, "$endpoint rpcId 不匹配")
                return null
            }
            envelope.optJSONObject("result")
        } catch (t: Throwable) {
            Log.w(TAG, "$endpoint 失败: ${t.javaClass.simpleName}: ${t.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** settings/describe → result.value（部署事实 + 全部设置命名空间）。 */
    fun describe(ctx: Context): JSONObject? = call(ctx, "settings/describe")?.optJSONObject("value")

    /**
     * 首轮诊断：把 describe 的完整响应落到 files/dsh-settings-dump.json。
     * 目的是让「真实字段名」可被离线核对（不靠猜字段写 UI）。
     * @return 落盘文件；失败返回 null
     */
    fun dumpDescribe(ctx: Context): File? {
        val result = call(ctx, "settings/describe") ?: return null
        return try {
            val f = File(ctx.filesDir, "dsh-settings-dump.json")
            f.writeText(result.toString(2), Charsets.UTF_8)
            Log.i(TAG, "settings dump -> ${f.absolutePath} (${f.length()}B)")
            f
        } catch (t: Throwable) {
            Log.w(TAG, "写 dump 失败", t)
            null
        }
    }
}
