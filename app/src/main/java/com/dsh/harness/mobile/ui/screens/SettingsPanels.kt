package com.dsh.harness.mobile.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.harness.DshBootstrap
import com.dsh.harness.DshProcessManager
import com.dsh.harness.mobile.data.DshAccountApi
import com.dsh.harness.mobile.data.DshPluginPatch
import com.dsh.harness.mobile.data.DshPresets
import com.dsh.harness.mobile.data.DshSettingsApi
import com.dsh.harness.mobile.data.DshSettingsStore
import com.dsh.harness.mobile.ui.theme.DshTheme
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
 * v30.43 · 设置页的三块「真面板」
 *
 * 这三页原来都是只读清单（用户反馈「内容比官方少、好多死页面」）。本文件按
 * 官方实现补齐，数据全部走真实服务：
 *   · 账户   —— account/getState|startSignIn|cancelSignIn|signOut|getProfile|getBalance
 *   · 模型   —— llm/listProviders + llm/listConfigurableProviders + settings/replace
 *                + credentials/set|unset
 *   · Agent 预设 —— 内置 presets 目录下的 patch.yml + .agent-presets 用户目录
 *
 * 文案（模式名/描述/「新任务默认」等）取自官方
 * `dsh-client-ui-agent-preset/lib/client.js` 的中文字典，非自创。
 * ========================================================================== */

// ---------------------------------------------------------------- 原子组件

@Composable
private fun SCard(content: @Composable ColumnScope.() -> Unit) {
    val c = DshTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(Radius.r(Radius.xl))
            .background(c.card).border(1.dp, c.line, Radius.r(Radius.xl)),
        content = content,
    )
}

@Composable
private fun SLabel(text: String) {
    Text(
        text, style = Type.secTitle, color = DshTheme.colors.ink3,
        modifier = Modifier.padding(start = 4.dp, bottom = Space.smd),
    )
}

@Composable
private fun SRow(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = DshTheme.colors
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = Space.lg, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.rowTitle, color = c.ink)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = Type.sec, color = c.ink3, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Space.sm))
            trailing()
        }
    }
}

@Composable
private fun SButton(text: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val c = DshTheme.colors
    val bg = when {
        !enabled -> c.card2
        primary -> c.ink
        else -> c.card2
    }
    val fg = when {
        !enabled -> c.ink3
        primary -> c.bg
        else -> c.ink
    }
    Box(
        Modifier.clip(Radius.r(Radius.md))
            .background(bg)
            .then(if (primary || !enabled) Modifier else Modifier.border(1.dp, c.line, Radius.r(Radius.md)))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(text, style = Type.sec.copy(fontWeight = FontWeight.SemiBold), color = fg) }
}

@Composable
private fun SChip(text: String, tone: Color) {
    Box(
        Modifier.clip(Radius.r(Radius.sm))
            .background(tone.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) { Text(text, fontSize = 11.sp, color = tone, fontWeight = FontWeight.Medium) }
}

@Composable
private fun SField(
    label: String,
    value: String,
    placeholder: String = "",
    secret: Boolean = false,
    onValue: (String) -> Unit,
) {
    val c = DshTheme.colors
    // 整块输入框都可点：只让 BasicTextField 自己响应点击的话，
    // 有效命中区只有一行文字高，点在框内边距上不会聚焦（真机实测很难点中）。
    val focus = remember { FocusRequester() }
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = 9.dp)) {
        Text(label, style = Type.sec, color = c.ink3)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().clip(Radius.r(Radius.md))
                .background(c.card2).border(1.dp, c.line, Radius.r(Radius.md))
                .clickable { focus.requestFocus() }
                .padding(horizontal = 12.dp, vertical = 11.dp),
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, style = Type.body, color = c.ink3)
            }
            BasicTextField(
                value = value,
                onValueChange = onValue,
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = c.ink),
                cursorBrush = SolidColor(c.brand),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth().heightIn(min = 20.dp).focusRequester(focus),
            )
        }
    }
}

/**
 * 钱包金额。host 的 `balance` 是保留精度的十进制字符串（真机会给到 16 位小数），
 * 这里按货币习惯收敛到 2 位，避免把「¥0.8614215000000000」原样丢给用户。
 */
private fun money(w: DshAccountApi.Wallet): String {
    val sym = if (w.currency == "CNY") "¥" else "$"
    val rounded = try {
        java.math.BigDecimal(w.balance).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
    } catch (_: Throwable) {
        w.balance
    }
    return sym + rounded
}

/** 打开系统浏览器（App 内不引 CustomTabs 依赖）。 */
private fun openInBrowser(ctx: Context, url: String): Boolean = try {
    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: Throwable) { false }

/**
 * 在 App 内打开网页（v34）。
 *
 * 账户页的「用量明细」「充值」是 DeepSeek 平台页面，跳系统浏览器会把用户甩出 App。
 * 这里走自带的 [com.dsh.harness.mobile.WebPageActivity]，失败时再退回浏览器。
 */
private fun openInAppWeb(ctx: Context, url: String, title: String) {
    try {
        ctx.startActivity(com.dsh.harness.mobile.WebPageActivity.intent(ctx, url, title))
    } catch (t: Throwable) {
        // 兜底前必须留下痕迹：上次没打日志，应用内打开失败被静默降级成跳浏览器，
        // 表现就是「点了没反应，只是弹出了 Edge」。
        android.util.Log.w("DshSettingsPanels", "应用内打开失败，回退系统浏览器：${t.message}", t)
        openInBrowser(ctx, url)
    }
}

// ---------------------------------------------------------------- 账户

@Composable
fun AccountPanel() {
    val c = DshTheme.colors
    val appCtx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    var view by remember { mutableStateOf<DshAccountApi.View?>(null) }
    var profile by remember { mutableStateOf<DshAccountApi.Profile?>(null) }
    var balance by remember { mutableStateOf<DshAccountApi.Balance?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pollToken by remember { mutableStateOf(0) }

    suspend fun refresh() {
        loading = true
        val r = withContext(Dispatchers.IO) { DshAccountApi.state(appCtx) }
        view = r
        if (r?.signedIn == true) {
            profile = withContext(Dispatchers.IO) { DshAccountApi.profile(appCtx) }
            balance = withContext(Dispatchers.IO) { DshAccountApi.balance(appCtx) }
        } else {
            profile = null
            balance = null
        }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    // 登录是浏览器授权轮询。
    //
    // 关键时序（真机实测）：startSignIn 同步返回时 attempt 往往还是 initializing，
    // authorizeUrl 要 ~1.2s 后才生成。所以这里不能在发起后立刻判失败，
    // 而是发起即开始轮询，由轮询首次看到 URL 时再拉起浏览器。
    LaunchedEffect(pollToken) {
        if (pollToken == 0) return@LaunchedEffect
        var guard = 0
        var misses = 0
        var opened: String? = null
        while (guard < 240) {
            delay(1200)
            guard++
            val r = withContext(Dispatchers.IO) { DshAccountApi.state(appCtx) }
            if (r == null) {
                // 偶发一次拿不到不算失败；连续 3 次才认为服务不可用
                if (++misses >= 3) { error = "无法连接 dsh 服务，请稍后重试"; break }
                continue
            }
            misses = 0
            view = r
            val url = r.authorizeUrl
            if (url != null && url != opened) {
                opened = url
                if (!openInBrowser(appCtx, url)) error = "没有可用的浏览器，无法完成授权"
            }
            if (r.signedIn) {
                profile = withContext(Dispatchers.IO) { DshAccountApi.profile(appCtx) }
                balance = withContext(Dispatchers.IO) { DshAccountApi.balance(appCtx) }
                error = null
                break
            }
            if (r.phase == "failed" || r.phase == "cancelled" || r.phase == "expired") {
                error = when (r.errorCode) {
                    "no-response" -> "授权服务无响应，请检查网络后重试"
                    "network" -> "网络异常，授权未能完成"
                    "protocol" -> "授权响应异常，请重试"
                    "expired" -> "授权已过期，请重新登录"
                    "storage" -> "本地凭据写入失败"
                    else -> "登录未完成（${r.phase}）"
                }
                break
            }
        }
    }

    val v = view
    SLabel("账号")
    SCard {
        when {
            loading && v == null -> SRow("正在读取账号状态…")
            v?.signedIn == true -> {
                val name = profile?.name ?: "已登录"
                val contact = profile?.contact.orEmpty()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(42.dp).clip(CircleShape).background(c.soft),
                        contentAlignment = Alignment.Center,
                    ) { Text(name.take(1), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.brand) }
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = Type.rowTitle, color = c.ink)
                        if (contact.isNotBlank()) {
                            Text(contact, style = Type.sec, color = c.ink3)
                        }
                    }
                    SChip("已登录", c.ok)
                }
                // 余额：官方把「充值钱包」和「赠送钱包」分开列，这里同构渲染。
                val bal = balance
                if (bal != null && !bal.isEmpty) {
                    bal.wallets.forEach { w ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("余额", style = Type.rowTitle, color = c.ink, modifier = Modifier.weight(1f))
                            Text(
                                money(w),
                                fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.ink,
                            )
                        }
                    }
                    bal.bonuses.forEach { w ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("赠送余额", style = Type.rowTitle, color = c.ink, modifier = Modifier.weight(1f))
                            Text(money(w), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink2)
                        }
                    }
                } else if (bal == null) {
                    SRow("余额", "暂时读不到（网络或登录态刚刷新）")
                }
                if (v.usageUrl.isNotBlank()) {
                    SRow("用量明细", v.usageUrl, trailing = { Text("›", fontSize = 18.sp, color = c.ink3) }) {
                        // v34：改为应用内 WebView，不再跳到系统浏览器
                        openInAppWeb(appCtx, v.usageUrl, "用量明细")
                    }
                }
                if (v.topUpUrl.isNotBlank()) {
                    SRow("充值", v.topUpUrl, trailing = { Text("›", fontSize = 18.sp, color = c.ink3) }) {
                        openInAppWeb(appCtx, v.topUpUrl, "充值")
                    }
                }
                Row(Modifier.fillMaxWidth().padding(Space.lg), horizontalArrangement = Arrangement.End) {
                    SButton(if (busy) "退出中…" else "退出登录", enabled = !busy) {
                        busy = true
                        scope.launch {
                            withContext(Dispatchers.IO) { DshAccountApi.signOut(appCtx) }
                            busy = false
                            refresh()
                        }
                    }
                }
            }
            else -> {
                SRow(
                    "未登录",
                    "登录 DeepSeek 账号后可在 App 内查看余额与用量；不登录也可以只用 API Key。",
                )
                val phase = v?.phase
                if (phase == "waiting-browser" || phase == "initializing" || phase == "exchanging" || phase == "committing") {
                    SRow(
                        "等待浏览器授权…",
                        "已在浏览器打开授权页。完成授权后会自动回到这里。",
                        trailing = { SChip("进行中", c.warn) },
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        if (v.authorizeUrl != null) {
                            SButton("重新打开授权页") { openInBrowser(appCtx, v.authorizeUrl) }
                        }
                        SButton("取消") {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    v.attemptId?.let { DshAccountApi.cancelSignIn(appCtx, it) }
                                }
                                pollToken = 0
                                refresh()
                            }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(Space.lg)) {
                        SButton(if (busy) "正在发起…" else "登录 DeepSeek 账号", primary = true, enabled = !busy) {
                            busy = true
                            error = null
                            scope.launch {
                                val origin = "http://127.0.0.1:${DshProcessManager.DSH_PORT}"
                                val r = withContext(Dispatchers.IO) { DshAccountApi.startSignIn(appCtx, origin) }
                                busy = false
                                if (r == null) {
                                    error = "发起登录失败：dsh 服务未响应"
                                } else {
                                    view = r
                                    error = null
                                    // 不在这里判 authorizeUrl —— 它稍后才生成，
                                    // 交给轮询首次看到时自动拉起浏览器。
                                    pollToken++
                                }
                            }
                        }
                    }
                }
                if (error != null) {
                    Text(
                        error!!, style = Type.sec, color = c.danger,
                        modifier = Modifier.padding(horizontal = Space.lg, vertical = 6.dp),
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(Space.sm))
    Text(
        "登录走 DeepSeek 平台的浏览器授权（回调 127.0.0.1:${DshProcessManager.DSH_PORT}），" +
            "不经过任何第三方。Token 只存在 dsh 自己的凭据文件里。",
        style = Type.sec, color = c.ink3,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

// ---------------------------------------------------------------- 模型

/** 一个可选提供商（来自 llm/listConfigurableProviders）。 */
private data class ProviderMeta(
    val id: String,
    val displayName: String,
    val defaultBaseURL: String?,
    val declared: Boolean,
)

private object ProviderApi {
    /** 已注册路由：id + 展示名。 */
    fun registered(ctx: Context): List<Pair<String, String>> {
        val r = DshSettingsApi.call(ctx, "llm/listProviders") ?: return emptyList()
        if (!r.optBoolean("ok", false)) return emptyList()
        val arr = r.optJSONArray("value") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            id to o.optString("name", id)
        }
    }

    fun directory(ctx: Context): List<ProviderMeta> {
        val r = DshSettingsApi.call(ctx, "llm/listConfigurableProviders") ?: return emptyList()
        if (!r.optBoolean("ok", false)) return emptyList()
        val arr = r.optJSONArray("value") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("provider").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ProviderMeta(
                id = id,
                displayName = o.optString("displayName", id),
                defaultBaseURL = o.optString("defaultBaseURL").takeIf { it.isNotBlank() },
                declared = o.optBoolean("declared", false),
            )
        }
    }

    fun deriveKeyRef(provider: String): String =
        provider.uppercase().replace(Regex("[^A-Z0-9]+"), "_") + "_API_KEY"

    /** 自定义 provider 写在 llm-pi-ai.providers.<id>。 */
    fun upsertProvider(ctx: Context, id: String, entry: JSONObject): String? {
        val r = DshSettingsApi.call(
            ctx, "settings/update",
            JSONObject().put("ns", "llm-pi-ai")
                .put("patch", JSONObject().put("providers", JSONObject().put(id, entry))),
        )
        if (r == null) return "写入失败：服务无响应"
        if (!r.optBoolean("ok", false)) {
            return r.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() } ?: "写入被拒绝"
        }
        DshSettingsStore.load(ctx, force = true)
        return null
    }

    /** 删除 provider：replace 整个 providers 字典（merge 语义删不掉键）。 */
    fun removeProvider(ctx: Context, id: String): String? {
        val cur = DshSettingsStore.get(ctx, "llm-pi-ai")?.value ?: JSONObject()
        val providers = cur.optJSONObject("providers") ?: JSONObject()
        if (!providers.has(id)) return null
        val next = JSONObject()
        val keys = providers.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k != id) next.put(k, providers.opt(k))
        }
        val r = DshSettingsApi.call(
            ctx, "settings/replace",
            JSONObject().put("ns", "llm-pi-ai").put("section", JSONObject().put("providers", next)),
        )
        if (r == null) return "删除失败：服务无响应"
        if (!r.optBoolean("ok", false)) {
            return r.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() } ?: "删除被拒绝"
        }
        // 顺带清掉派生出来的凭据引用（失败不阻断）
        DshSettingsApi.call(ctx, "credentials/unset", JSONObject().put("ref", deriveKeyRef(id)))
        DshSettingsStore.load(ctx, force = true)
        return null
    }

    fun setCredential(ctx: Context, ref: String, value: String): String? {
        val r = DshSettingsApi.call(ctx, "credentials/set", JSONObject().put("ref", ref).put("value", value))
        if (r == null) return "密钥保存失败：服务无响应"
        if (!r.optBoolean("ok", false)) {
            return r.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() } ?: "密钥被拒绝"
        }
        return null
    }
}

@Composable
fun ModelsPanelV2() {
    val c = DshTheme.colors
    val appCtx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var registered by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var directory by remember { mutableStateOf<List<ProviderMeta>>(emptyList()) }
    var nsData by remember { mutableStateOf<Map<String, DshSettingsStore.Ns>?>(null) }
    var tab by remember { mutableStateOf(0) }          // 0=第三方 1=自定义 API
    var picked by remember { mutableStateOf("deepseek-official") }
    var apiKey by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }
    var customBase by remember { mutableStateOf("") }
    var customApi by remember { mutableStateOf("openai-completions") }
    // host 要求自定义路由必须自带 models（否则报 "resolves no models"），
    // 官方表单也是逐行填 id / name，这里先用 id 列表。
    var customModels by remember { mutableStateOf(listOf("")) }
    var error by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }

    LaunchedEffect(refresh) {
        loading = true
        // 全部走 IO：settings/describe + 两个 llm RPC 都是网络往返，不能压主线程
        val snap = withContext(Dispatchers.IO) {
            Triple(
                ProviderApi.registered(appCtx),
                ProviderApi.directory(appCtx),
                DshSettingsStore.load(appCtx, force = refresh > 0),
            )
        }
        registered = snap.first
        directory = snap.second
        nsData = snap.third
        if (directory.isNotEmpty() && directory.none { it.id == picked }) picked = directory.first().id
        loading = false
    }

    // ---- 默认模型（原有能力保留） ----
    val data = nsData
    val llm = data?.get("llm-deepseek")?.value ?: JSONObject()
    val def = data?.get("agent-default-model")?.value ?: JSONObject()
    val defaultProvider = def.optString("provider", "deepseek-official")
    val defaultModel = def.optString("model")

    SLabel("提供商")
    SCard {
        if (loading) SRow("正在读取提供商…")
        else {
            registered.forEach { (id, name) ->
                // 官方的提供商行带「编辑 / 删除」。内置两条（官方 / 账号路由）由 host 装配，
                // 没有可改的字段，只显示徽章；其余（llm-pi-ai.providers 里的自定义项）可编辑。
                val editable = id != "deepseek-official" && id != "deepseek-account"
                SRow(
                    name.ifBlank { id },
                    id,
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            when {
                                id == "deepseek-official" -> SChip("内置", c.ok)
                                id == "deepseek-account" -> SChip("账号", c.brand)
                                else -> {
                                    SButton("编辑") { editing = id }
                                    SButton("删除") { pendingDelete = id }
                                }
                            }
                        }
                    },
                    onClick = if (editable) ({ editing = id }) else null,
                )
            }
            if (registered.isEmpty()) SRow("没有可用的提供商", "服务未返回路由列表")
        }
    }

    Spacer(Modifier.height(Space.lg))
    SLabel("默认模型")
    SCard {
        val models = llm.optJSONArray("models")
        if (models == null || models.length() == 0) {
            SRow("模型清单", "服务未返回任何模型")
        } else {
            for (i in 0 until models.length()) {
                val mo = models.optJSONObject(i) ?: continue
                val id = mo.optString("id")
                val name = mo.optString("name", id)
                val ctxWin = mo.optLong("contextWindow", 0L)
                val selected = id == defaultModel
                SRow(
                    name,
                    id + if (ctxWin > 0) " · 上下文 ${fmtTokensV2(ctxWin)}" else "",
                    trailing = { if (selected) SChip("当前", c.ok) },
                ) {
                    if (!selected) {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                DshSettingsApi.call(
                                    appCtx, "settings/update",
                                    JSONObject().put("ns", "agent-default-model").put(
                                        "patch",
                                        JSONObject().put("provider", defaultProvider).put("model", id),
                                    ),
                                )
                                DshSettingsStore.load(appCtx, force = true)
                            }
                            refresh++
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(Space.lg))
    SLabel("添加提供商")
    SCard {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = 10.dp)) {
            SButton(if (tab == 0) "第三方模型提供商" else "第三方模型提供商", primary = tab == 0) { tab = 0 }
            Spacer(Modifier.width(Space.sm))
            SButton("自定义模型 API", primary = tab == 1) { tab = 1 }
        }
        if (tab == 0) {
            Text(
                "从内置目录中选择 OpenAI、Anthropic、Kimi 等提供商，填入其 API 密钥即可使用。",
                style = Type.sec, color = c.ink3,
                modifier = Modifier.padding(horizontal = Space.lg, vertical = 2.dp),
            )
            SRow(
                "提供商",
                directory.firstOrNull { it.id == picked }?.displayName ?: picked,
                trailing = { Text("›", fontSize = 18.sp, color = c.ink3) },
            ) { picking = true }
            SField("API 密钥", apiKey, "留空则使用已有的环境变量 / 已存凭据", secret = true) { apiKey = it }
        } else {
            Text(
                "手写一条 OpenAI 兼容或 Anthropic 路由：需要名称、协议与 Base URL。",
                style = Type.sec, color = c.ink3,
                modifier = Modifier.padding(horizontal = Space.lg, vertical = 2.dp),
            )
            SField("名称（路由 id）", customName, "例如 my-vllm") { customName = it }
            SRow("协议", customApi, trailing = { Text("›", fontSize = 18.sp, color = c.ink3) }) {
                customApi = when (customApi) {
                    "openai-completions" -> "openai-responses"
                    "openai-responses" -> "anthropic-messages"
                    else -> "openai-completions"
                }
            }
            SField("Base URL", customBase, "https://api.example.com/v1") { customBase = it }
            // host 校验：自定义路由必须自带 models，否则整条 provider 被拒。
            customModels.forEachIndexed { i, v ->
                SField(
                    if (i == 0) "模型 id" else "模型 id ${i + 1}", v, "例如 deepseek-chat",
                ) { nv -> customModels = customModels.toMutableList().also { it[i] = nv } }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = 2.dp)) {
                SButton("添加模型") { customModels = customModels + "" }
                if (customModels.size > 1) {
                    Spacer(Modifier.width(Space.sm))
                    SButton("删掉最后一个") {
                        customModels = customModels.dropLast(1)
                    }
                }
            }
            SField("API 密钥", apiKey, "sk-…", secret = true) { apiKey = it }
        }
        if (error != null) {
            Text(error!!, style = Type.sec, color = c.danger, modifier = Modifier.padding(horizontal = Space.lg, vertical = 4.dp))
        }
        if (note != null) {
            Text(note!!, style = Type.sec, color = c.ok, modifier = Modifier.padding(horizontal = Space.lg, vertical = 4.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(Space.lg),
            horizontalArrangement = Arrangement.spacedBy(Space.sm, Alignment.End),
        ) {
            SButton("清空") {
                apiKey = ""; customName = ""; customBase = ""
                customModels = listOf(""); error = null; note = null
            }
            SButton("保存", primary = true) {
                error = null
                note = null
                scope.launch {
                    val id = if (tab == 0) picked else customName.trim()
                    if (id.isBlank()) { error = "请填写名称"; return@launch }
                    val meta = directory.firstOrNull { it.id == id }
                    val base = if (tab == 0) meta?.defaultBaseURL.orEmpty() else customBase.trim()
                    val ids = if (tab == 0) emptyList() else customModels.map { it.trim() }.filter { it.isNotBlank() }
                    if (tab == 1 && ids.isEmpty()) {
                        error = "至少要填一个模型 id：host 要求自定义路由在配置里声明 models"
                        return@launch
                    }
                    val entry = JSONObject().put("displayName", meta?.displayName ?: id)
                    if (tab == 1) entry.put("api", customApi)
                    if (base.isNotBlank()) entry.put("baseURL", base)
                    if (ids.isNotEmpty()) {
                        entry.put("models", org.json.JSONArray().apply {
                            ids.forEach { put(JSONObject().put("id", it)) }
                        })
                    }
                    entry.put("apiKeyEnv", ProviderApi.deriveKeyRef(id))
                    val e1 = withContext(Dispatchers.IO) { ProviderApi.upsertProvider(appCtx, id, entry) }
                    if (e1 != null) { error = e1; return@launch }
                    var e2: String? = null
                    if (apiKey.isNotBlank()) {
                        e2 = withContext(Dispatchers.IO) {
                            ProviderApi.setCredential(appCtx, ProviderApi.deriveKeyRef(id), apiKey.trim())
                        }
                    }
                    if (e2 != null) error = e2 else {
                        note = "已保存 $id"
                        apiKey = ""
                        refresh++
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(Space.sm))
    Text(
        "第三方提供商写 llm-pi-ai.providers，密钥写 dsh 凭据库（不落明文设置）。",
        style = Type.sec, color = c.ink3, modifier = Modifier.padding(horizontal = 4.dp),
    )

    // ---- 提供商目录选择器（官方是下拉，这里用弹窗列表）----
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("选择提供商") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    if (directory.isEmpty()) {
                        Text("目录为空：dsh 服务未返回可配置提供商", style = Type.sec, color = c.ink3)
                    }
                    directory.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { picked = m.id; picking = false }
                                .padding(vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(m.displayName, style = Type.rowTitle, color = c.ink)
                                Text(m.id, style = Type.sec, color = c.ink3)
                            }
                            if (m.id == picked) Text("✓", color = c.brand, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text("取消") } },
        )
    }

    // ---- 编辑已有自定义提供商 ----
    val editId = editing
    if (editId != null) {
        val cur = nsData?.get("llm-pi-ai")?.value?.optJSONObject("providers")?.optJSONObject(editId)
        var eName by remember(editId) { mutableStateOf(cur?.optString("displayName") ?: editId) }
        var eBase by remember(editId) { mutableStateOf(cur?.optString("baseURL").orEmpty()) }
        var eApi by remember(editId) { mutableStateOf(cur?.optString("api") ?: "openai-completions") }
        var eKey by remember(editId) { mutableStateOf("") }
        var eErr by remember(editId) { mutableStateOf<String?>(null) }
        var eBusy by remember(editId) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("编辑 $editId") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    SField("显示名称", eName, editId) { eName = it }
                    SField("Base URL", eBase, "https://api.example.com/v1") { eBase = it }
                    SRow(
                        "协议", eApi,
                        trailing = { Text("›", fontSize = 18.sp, color = c.ink3) },
                    ) { eApi = nextApiProtocol(eApi) }
                    SField("API 密钥", eKey, "留空表示不改动已有密钥", secret = true) { eKey = it }
                    if (eErr != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(eErr!!, style = Type.sec, color = c.danger)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !eBusy, onClick = {
                    eBusy = true
                    eErr = null
                    scope.launch {
                        // 以现有条目为底稿再改：models / compat / headers / reasoning
                        // 这些表单里没有的字段必须原样保留，否则编辑一次就被抹掉。
                        val entry = JSONObject(cur?.toString() ?: "{}")
                        entry.put("displayName", eName.ifBlank { editId })
                        if (eBase.isNotBlank()) entry.put("baseURL", eBase.trim())
                        entry.put("api", eApi)
                        entry.put("apiKeyEnv", ProviderApi.deriveKeyRef(editId))
                        var e = withContext(Dispatchers.IO) { ProviderApi.upsertProvider(appCtx, editId, entry) }
                        if (e == null && eKey.isNotBlank()) {
                            e = withContext(Dispatchers.IO) {
                                ProviderApi.setCredential(appCtx, ProviderApi.deriveKeyRef(editId), eKey.trim())
                            }
                        }
                        eBusy = false
                        if (e != null) eErr = e else { editing = null; refresh++ }
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } },
        )
    }

    val del = pendingDelete
    if (del != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除提供商") },
            text = { Text("将从 llm-pi-ai.providers 移除「$del」，并清除它派生的凭据引用。已建的会话不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        val e = withContext(Dispatchers.IO) { ProviderApi.removeProvider(appCtx, del) }
                        if (e != null) error = e else { note = "已删除 $del"; refresh++ }
                    }
                }) { Text("删除", color = c.danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

/** llm-pi-ai 的三种 api 协议轮转（与 dsh llm-pi-ai 的 `api` 字段取值一致）。 */
private fun nextApiProtocol(cur: String): String = when (cur) {
    "openai-completions" -> "openai-responses"
    "openai-responses" -> "anthropic-messages"
    else -> "openai-completions"
}

private fun fmtTokensV2(n: Long): String = when {    n >= 1_000_000 -> "${n / 1_000_000}M"
    n >= 1_000 -> "${n / 1_000}K"
    else -> "$n"
}
// ---------------------------------------------------------------- Agent 预设

/**
 * 一张预设卡。数据全部来自 host 的 `agentPresets/list`（typert 严格 schema）：
 *
 * ```
 * { presets: [ { id, isDefault, name?, description?, broken? } ] }
 * ```
 *
 * 内置判定与官方 client 一致：没有发布 `name` 且 id 属于四个出厂预设
 * （`BUILT_IN_PRESET_KEYS`，见 dsh-client-ui-agent-preset/lib/client.js）。
 * 默认值不在预设里，而是设置字段 `agent-preset-registry.selectedDefault`。
 */
private data class PresetRow(
    val id: String,
    val name: String,
    val description: String,
    val builtIn: Boolean,
    val isDefault: Boolean,
    val broken: String?,
)

/** 出厂预设的中文文案，与官方 locale 字典逐字对齐。 */
private val BUILTIN_PRESET_COPY: Map<String, Pair<String, String>> = mapOf(
    "standard" to ("标准模式" to
        "处理代码、文件和资料，适合大多数任务。Agent 会按需使用检索、编辑和终端等工具。"),
    "ptc" to ("PTC 模式" to
        "包含标准模式的所有能力，更适合批量调用工具，并对结果进行筛选、整理、去重、统计或汇总的任务。"),
    "minimal" to ("极简模式" to
        "Agent 使用终端和图片读取工具，并支持上下文压缩，适合测试和对比其基础表现。"),
    "cordis" to ("创造模式" to
        "用对话定制 DSH：让 Agent 编写插件，添加新功能或界面；也能组合工具和提示词，创建自己的模式。"),
)

/** 官方「模式说明 / 如何使用」帮助内容（guide* 文案）。 */
private data class PresetGuide(val intro: String, val explanation: String, val usage: String)

private val PRESET_GUIDES: Map<String, PresetGuide> = mapOf(
    "standard" to PresetGuide(
        "新建任务时选择「标准模式」，说明要完成什么、相关文件在哪里，以及怎样判断任务完成。",
        "### 工作方式\n" +
            "Agent 直接调用工具来读写文件、检索资料和执行终端命令。包含 Skills、计划、目标、子 Agent、工作流和上下文压缩等能力。\n\n" +
            "### 什么时候选\n" +
            "日常编程、文件处理和资料整理可以从这里开始。标准模式也能编写脚本、批量处理文件；PTC 改变的是工具调用方式，批量任务并不必须使用 PTC。",
        "### 修复一个问题\n" +
            "搜索表单连续提交两次后，结果会消失。请定位原因、修复问题并运行相关测试，最后说明原因和修改内容。\n\n" +
            "预期产出：代码修改、相关测试结果，以及问题原因说明。\n\n" +
            "### 整理项目资料\n" +
            "阅读项目中的 Markdown 记录，整理已经达成的结论和仍待确认的问题，并附上对应文件链接。\n\n" +
            "预期产出：一份带来源引用的总结，方便回到原文核对。",
    ),
    "ptc" to PresetGuide(
        "新建任务时选择「PTC 模式」，说明输入文件、处理规则和输出格式。代码由 Agent 编写。",
        "### 怎样调用工具\n" +
            "PTC 是 Programmatic Tool Calling，即通过程序调用工具。当前内置预设让 Agent 通过 run_code 编写 TypeScript 程序，使用生成的工具 SDK 发起调用。程序可以组织循环、条件判断、错误处理，以及适合并发执行的调用。\n\n" +
            "### 哪些结果交给模型\n" +
            "工具返回的数据先交给程序，经过筛选、计算或合并，再通过输出或返回值交给模型；图片结果会另行附加。程序中的工具调用仍会被记录，也仍受工具权限约束。\n\n" +
            "### 与标准模式的区别\n" +
            "两种模式都能编程、批量处理文件。标准模式直接向模型提供各个工具；PTC 让模型用代码组织工具调用。当前 PTC 预设未启用 workflow 工具。速度和 token 用量取决于具体任务与结果处理方式。",
        "### 批量检查配置文件\n" +
            "检查 configs/ 下所有 JSON 文件，按照 schema.json 找出缺失字段和不合法的值。每个问题写成 CSV 中的一行；读取失败的文件也记入报告，继续检查其余文件。保留原文件。\n\n" +
            "预期产出：问题汇总和一份 CSV 报告。\n\n" +
            "### 汇总错误日志\n" +
            "分析 logs/ 下的日志，按服务和错误类型统计次数，列出出现最多的十类错误，每类保留一条示例。完整统计另存为 CSV。\n\n" +
            "预期产出：高频错误摘要和完整统计表。",
    ),
    "minimal" to PresetGuide(
        "新建任务时选择「极简模式」。做对照测试时，保持模型、权限、任务输入和工作区起始状态一致。",
        "### 保留哪些能力\n" +
            "提供持久 Shell 和 read_image 工具，并使用固定系统提示词。读取图片需要支持图片输入的模型。上下文压力达到阈值时自动压缩较早历史；Agent 空闲时可输入 /compact 手动压缩。内置预设不加载 Skills、计划，也不注入标准运行时上下文。\n\n" +
            "### 什么时候选\n" +
            "适合作为实验和对照测试的基线。Agent 仍能通过终端命令读写文件、运行脚本，但缺少管理长任务的内置辅助能力。工具少，不代表对新手更容易。",
        "### 对比基础修复表现\n" +
            "运行这个项目的测试，找出失败原因，做最小修复，再运行相关测试并报告结果。\n\n" +
            "分别用标准模式和极简模式，从相同的工作区状态执行这条任务，对比完成情况、工具调用和最终修改。极简模式会通过终端命令完成这些操作。",
    ),
    "cordis" to PresetGuide(
        "新建任务时选择「创造模式」，说明希望增加什么能力、从哪里使用，以及怎样验证效果。",
        "### 可以创造什么\n" +
            "创造模式具备标准任务工具，并增加运行时检查、持久化插件管理，以及 Cordis 插件和 Agent 预设的开发指引。可以编写插件来添加功能或界面，也可以组合工具和提示词，创建适合特定任务的模式。\n\n" +
            "### 插件与模式的关系\n" +
            "插件为 DSH 增加能力，例如工具、服务连接或界面入口。模式是一份 Agent 预设，用来选择任务可用的工具，并约定 Agent 的工作方式。自定义模式中也可以使用自己开发的插件。\n\n" +
            "### 怎样让成果生效\n" +
            "可以要求 Agent 完成安装并验证实际效果。插件可能即时加载，也可能需要重启，取决于修改内容；新建的模式在创建新任务时选择。",
        "### 给侧栏加一个入口\n" +
            "做一个插件，在侧栏增加「本周日程」入口，点击后按天列出这周的任务。装好后告诉我怎么验证。\n\n" +
            "### 定制一个写作模式\n" +
            "创建一个只保留读写文件与终端、并固定中文写作规范的模式，命名为「写作模式」。",
    ),
)

private object PresetApi {
    fun list(ctx: Context): List<PresetRow> {
        val r = DshSettingsApi.call(ctx, "agentPresets/list") ?: return emptyList()
        if (!r.optBoolean("ok", false)) return emptyList()
        val arr = r.optJSONObject("value")?.optJSONArray("presets") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val publishedName = o.optString("name").takeIf { it.isNotBlank() && it != "null" }
            val builtIn = publishedName == null && BUILTIN_PRESET_COPY.containsKey(id)
            val copy = BUILTIN_PRESET_COPY[id]
            PresetRow(
                id = id,
                name = (if (builtIn) copy?.first else publishedName) ?: id,
                description = (if (builtIn) copy?.second else o.optString("description").takeIf {
                    it.isNotBlank() && it != "null"
                }) ?: "暂无描述。",
                builtIn = builtIn,
                isDefault = o.optBoolean("isDefault", false),
                broken = o.optString("broken").takeIf { it.isNotBlank() && it != "null" },
            )
        }
    }

    /** 写 `agent-preset-registry.selectedDefault`（官方 writeDefaultPreset 的同一个字段）。 */
    fun setDefault(ctx: Context, id: String): String? {
        val r = DshSettingsApi.call(
            ctx, "settings/update",
            JSONObject().put("ns", "agent-preset-registry")
                .put("patch", JSONObject().put("selectedDefault", id)),
        )
        if (r == null) return "保存失败：服务无响应"
        if (!r.optBoolean("ok", false)) {
            return r.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() } ?: "保存被拒绝"
        }
        // settings 只管当前进程；重启后的真源是 profile 的 patch 层，必须一起写。
        val patchErr = DshPluginPatch.setAgentPresetDefault(ctx, id)
        DshSettingsStore.load(ctx, force = true)
        return patchErr?.let { "已写入设置，但 patch 层写入失败：$it" }
    }
}

@Composable
fun PresetsPanelV2() {
    val c = DshTheme.colors
    val appCtx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<PresetRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var guide by remember { mutableStateOf<Pair<String, PresetGuide>?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        loading = true
        rows = withContext(Dispatchers.IO) { PresetApi.list(appCtx) }
        loading = false
    }

    val shown = if (query.isBlank()) rows else rows.filter {
        it.name.contains(query, true) || it.description.contains(query, true) || it.id.contains(query, true)
    }

    // 搜索 —— 与官方同一位置（卡片列表上方）
    Box(
        Modifier.fillMaxWidth().clip(Radius.r(Radius.md))
            .background(c.card2).border(1.dp, c.line, Radius.r(Radius.md))
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        if (query.isEmpty()) Text("搜索预设", style = Type.body, color = c.ink3)
        BasicTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, color = c.ink),
            cursorBrush = SolidColor(c.brand), modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(Space.sm))
    Text(
        "选择新任务使用的 Agent 预设",
        style = Type.sec, color = c.ink3,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
    if (error != null) {
        Spacer(Modifier.height(Space.xs))
        Text(error!!, style = Type.sec, color = c.danger, modifier = Modifier.padding(horizontal = 4.dp))
    }
    Spacer(Modifier.height(Space.lg))

    if (loading && rows.isEmpty()) {
        SLabel("内置")
        SCard { SRow("正在读取预设…") }
        return
    }

    // ---- 内置 ----
    val builtIns = shown.filter { it.builtIn }
    if (builtIns.isNotEmpty()) SLabel("内置")
    builtIns.forEach { p ->
        Spacer(Modifier.height(Space.smd))
        PresetCardView(
            row = p, saving = saving == p.id,
            onMakeDefault = {
                error = null
                saving = p.id
                scope.launch {
                    val e = withContext(Dispatchers.IO) { PresetApi.setDefault(appCtx, p.id) }
                    saving = null
                    if (e != null) error = e else reload++
                }
            },
            onGuide = { PRESET_GUIDES[p.id]?.let { g -> guide = p.name to g } },
        )
    }

    // ---- 自定义 ----
    Spacer(Modifier.height(Space.lg))
    SLabel("自定义")
    val customs = shown.filter { !it.builtIn }
    if (customs.isEmpty()) {
        SCard {
            Column(Modifier.fillMaxWidth().padding(Space.lg)) {
                Text("还没有自定义模式", style = Type.rowTitle, color = c.ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    "在「创造模式」里对 Agent 说「创建一个模式」，或在 dsh 的 .agent-presets 下放入声明后，会出现在这里。",
                    style = Type.sec, color = c.ink3,
                )
            }
        }
    } else {
        customs.forEach { p ->
            Spacer(Modifier.height(Space.smd))
            PresetCardView(
                row = p, saving = saving == p.id,
                onMakeDefault = {
                    error = null
                    saving = p.id
                    scope.launch {
                        val e = withContext(Dispatchers.IO) { PresetApi.setDefault(appCtx, p.id) }
                        saving = null
                        if (e != null) error = e else reload++
                    }
                },
                onGuide = null,
            )
        }
    }

    // ---- 帮助弹窗（模式说明 / 如何使用）----
    val g = guide
    if (g != null) {
        PresetGuideDialog(title = g.first, guide = g.second) { guide = null }
    }
}

@Composable
private fun PresetCardView(
    row: PresetRow,
    saving: Boolean,
    onMakeDefault: () -> Unit,
    onGuide: (() -> Unit)?,
) {
    val c = DshTheme.colors
    SCard {
        Column(Modifier.fillMaxWidth().padding(Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.name, style = Type.title, color = c.ink, modifier = Modifier.weight(1f))
                if (row.builtIn) SChip("内置", c.ink3)
                if (row.isDefault) {
                    Spacer(Modifier.width(6.dp))
                    SChip("新任务默认", c.ok)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(row.description, style = Type.sec, color = c.ink2)
            if (row.broken != null) {
                Spacer(Modifier.height(6.dp))
                Text("无法使用：${row.broken}", style = Type.sec, color = c.danger)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onGuide != null) {
                    Text(
                        "模式说明", style = Type.sec, color = c.brand,
                        modifier = Modifier.clickable { onGuide() },
                    )
                    Spacer(Modifier.width(Space.lg))
                    Text(
                        "如何使用", style = Type.sec, color = c.brand,
                        modifier = Modifier.clickable { onGuide() },
                    )
                }
                Spacer(Modifier.weight(1f))
                when {
                    row.broken != null -> Unit
                    row.isDefault -> Text("新任务默认", style = Type.sec, color = c.ink3)
                    else -> SButton(if (saving) "保存中…" else "设为新任务默认", enabled = !saving) {
                        onMakeDefault()
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetGuideDialog(title: String, guide: PresetGuide, onClose: () -> Unit) {
    val c = DshTheme.colors
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(guide.intro, style = Type.body, color = c.ink2)
                Spacer(Modifier.height(12.dp))
                Text(renderGuide(guide.explanation), style = Type.body, color = c.ink)
                Spacer(Modifier.height(14.dp))
                Text("如何使用", style = Type.rowTitle, color = c.ink)
                Spacer(Modifier.height(6.dp))
                Text(renderGuide(guide.usage), style = Type.body, color = c.ink2)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("关闭") } },
    )
}

/** 帮助文案用 `###` 分节、`>` 标示例任务，这里做最小渲染：只去掉标记，保留正文。 */
private fun renderGuide(s: String): String =
    s.replace("### ", "").replace("> ", "")
