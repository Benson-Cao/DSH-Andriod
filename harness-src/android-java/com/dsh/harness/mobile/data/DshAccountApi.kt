package com.dsh.harness.mobile.data

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.util.TimeZone

/**
 * dsh 账户服务的 Compose 侧客户端（v30.43）。
 *
 * 通道与 [DshSettingsApi] 完全相同（POST /api/<endpoint> 一元 RPC + WebView cookie），
 * 方法表来自 `@deepseek-ai/dsh-api-account-controller/lib/types/index.d.ts`：
 *
 * ```
 * getState()                                        → AccountView
 * getProfile(client)                                → AccountDetails['profile'] | null
 * getBalance(client)                                → AccountDetails['balance'] | null
 * startSignIn(client, callbackOrigin, loginSource)  → AccountView（含 attempt.authorizeUrl）
 * cancelSignIn(attemptId)                           → AccountView
 * signOut(client)                                   → AccountView
 * ```
 * 其中 `client: AccountClientMetadata = { version, locale, timezoneOffsetSeconds }`
 * （见 `dsh-deepseek-account/lib/types/types.d.ts`）。
 *
 * 登录是**浏览器授权码**流程（真机实测）：startSignIn 会异步把 attempt 从
 * `initializing` 推到 `waiting-browser` 并给出 `authorizeUrl`；用户在系统浏览器里
 * 完成授权后，Platform 回调 `callbackOrigin`，Host 完成 exchange/commit，
 * getState 的 status 变成 `credential-stored`。所以这里必须「发起 + 轮询」两步。
 */
object DshAccountApi {
    private const val TAG = "DshAccountApi"

    /** 界面用的安全投影（不碰任何 token 字段）。 */
    data class View(
        val signedIn: Boolean,
        val phase: String?,
        val authorizeUrl: String?,
        val attemptId: String?,
        val errorCode: String?,
        val usageUrl: String,
        val topUpUrl: String,
    )

    /** 展示身份（AccountProfile），字段可空。 */
    data class Profile(val name: String?, val contact: String?, val avatarUrl: String?)

    /** 单个钱包（AccountWallet）。 */
    data class Wallet(val currency: String, val balance: String)

    /**
     * 余额结果。`wallets` 是充值钱包，`bonuses` 是赠送钱包 ——
     * 官方把两者分开渲染，这里保留同样的语义。
     */
    data class Balance(val wallets: List<Wallet>, val bonuses: List<Wallet>) {
        val isEmpty: Boolean get() = wallets.isEmpty() && bonuses.isEmpty()
    }

    private fun client(): JSONObject {
        val tz = try {
            TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000
        } catch (_: Throwable) { 0 }
        return JSONObject()
            .put("version", "0.1.0")
            .put("locale", "zh-CN")
            .put("timezoneOffsetSeconds", tz)
    }

    private fun parse(result: JSONObject?): View? {
        if (result == null) return null
        if (!result.optBoolean("ok", false)) {
            Log.w(TAG, "account rpc 失败: ${result.optJSONObject("error")}")
            return null
        }
        val v = result.optJSONObject("value") ?: return null
        val links = v.optJSONObject("links")
        val attempt = v.optJSONObject("attempt")
        return View(
            signedIn = v.optString("status") == "credential-stored",
            phase = attempt?.optString("phase"),
            authorizeUrl = attempt?.optString("authorizeUrl")?.takeIf { it.isNotBlank() },
            attemptId = attempt?.optString("id")?.takeIf { it.isNotBlank() },
            errorCode = attempt?.optString("errorCode")?.takeIf { it.isNotBlank() },
            usageUrl = links?.optString("usageUrl").orEmpty(),
            topUpUrl = links?.optString("topUpUrl").orEmpty(),
        )
    }

    /** 当前账户状态（含进行中的登录尝试）。 */
    fun state(ctx: Context): View? = parse(DshSettingsApi.call(ctx, "account/getState"))

    /**
     * 发起浏览器登录。
     * @param callbackOrigin 浏览器可达的 loopback origin —— 手机浏览器与 dsh 同机，
     *   因此就是服务自身的 origin。
     */
    fun startSignIn(ctx: Context, callbackOrigin: String): View? = parse(
        DshSettingsApi.call(
            ctx, "account/startSignIn",
            JSONObject()
                .put("client", client())
                .put("callbackOrigin", callbackOrigin)
                // 原生客户端：授权失败时要能回到 App，而不是回到网页的登录态。
                .put("loginSource", "desktop"),
        ),
    )

    fun cancelSignIn(ctx: Context, attemptId: String): View? = parse(
        DshSettingsApi.call(ctx, "account/cancelSignIn", JSONObject().put("attemptId", attemptId)),
    )

    fun signOut(ctx: Context): View? = parse(
        DshSettingsApi.call(ctx, "account/signOut", JSONObject().put("client", client())),
    )

    /** 昵称/头像/联系方式；未登录或查询失败返回 null。 */
    fun profile(ctx: Context): Profile? {
        val r = DshSettingsApi.call(ctx, "account/getProfile", JSONObject().put("client", client()))
            ?: return null
        if (!r.optBoolean("ok", false)) return null
        // 真实结构（dsh-deepseek-account/lib/types/types.d.ts: AccountDetails['profile']）：
        //   { status: 'ready', value: { id, name, contact, avatarUrl } } | { status: 'failed' }
        val box = r.optJSONObject("value") ?: return null
        if (box.optString("status") != "ready") return null
        val v = box.optJSONObject("value") ?: return null
        return Profile(
            name = v.optString("name").takeIf { it.isNotBlank() && it != "null" },
            contact = v.optString("contact").takeIf { it.isNotBlank() && it != "null" },
            avatarUrl = v.optString("avatarUrl").takeIf { it.isNotBlank() && it != "null" },
        )
    }

    /** 钱包余额；未登录或查询失败返回 null。 */
    fun balance(ctx: Context): Balance? {
        val r = DshSettingsApi.call(ctx, "account/getBalance", JSONObject().put("client", client()))
            ?: return null
        if (!r.optBoolean("ok", false)) return null
        // AccountDetails['balance']：
        //   { status: 'ready', value: AccountWallet[], bonusWallets: AccountWallet[] } | { status: 'failed' }
        // AccountWallet = { currency: 'CNY'|'USD', balance: string }
        val box = r.optJSONObject("value") ?: return null
        if (box.optString("status") != "ready") return null
        return Balance(
            wallets = parseWallets(box.optJSONArray("value")),
            bonuses = parseWallets(box.optJSONArray("bonusWallets")),
        )
    }

    private fun parseWallets(arr: org.json.JSONArray?): List<Wallet> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val bal = o.optString("balance").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Wallet(currency = o.optString("currency", "CNY"), balance = bal)
        }
    }
}
