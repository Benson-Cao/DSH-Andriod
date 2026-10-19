package com.dsh.harness.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.dsh.harness.DshSync
import com.dsh.harness.mobile.ui.components.*
import com.dsh.harness.mobile.ui.theme.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ============================ S51 NAS 连接 ============================
/**
 * S51 NAS 连接（飞牛 fnOS 的 WebDAV 同步）。
 *
 * 这是**真** NAS 能力：底层 [DshSync] + [com.dsh.harness.DshWebDavClient]
 * 是纯 JDK 实现的 WebDAV 客户端，凭据存 SharedPreferences，只同步
 * `.dsh/storages/`（会话/工作区数据），不同步 node_modules 等平台相关文件。
 *
 * 与「外部存储(SAF)」（`S50StorageDir`）是**两套独立能力**：
 *  - SAF = 用户授权一个本地目录给 dsh 透明读写（DocumentsProvider）；
 *  - NAS = 把会话数据同步到飞牛 NAS 的 WebDAV（跨端共享）。
 * 两者不混为一谈（用户明确要求「nas是nas，saf桥是saf桥」）。
 *
 * 凭证安全：密码不回填输入框、不提供读取接口（[DshSync] 设计如此）；
 * 保存时密码留空 = 保留已存密码，只有输入新密码才覆盖。
 */
@Composable
fun S51NasSync(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    val c = DshTheme.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf(DshSync.url(ctx)) }
    var user by remember { mutableStateOf(DshSync.user(ctx)) }
    var pass by remember { mutableStateOf("") }            // 密码绝不回填
    var interval by remember { mutableStateOf(DshSync.intervalSec(ctx).toString()) }
    var reveal by remember { mutableStateOf(false) }
    var configured by remember { mutableStateOf(DshSync.isConfigured(ctx)) }
    var busy by remember { mutableStateOf(false) }
    var lastJson by remember { mutableStateOf(DshSync.lastResultJson(ctx)) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusOk by remember { mutableStateOf(false) }

    fun reload() {
        configured = DshSync.isConfigured(ctx)
        url = DshSync.url(ctx)
        user = DshSync.user(ctx)
        interval = DshSync.intervalSec(ctx).toString()
        pass = ""
        lastJson = DshSync.lastResultJson(ctx)
    }

    fun parseResult(json: String): Pair<String, Boolean> {
        return try {
            val o = org.json.JSONObject(json)
            val up = o.optInt("uploaded", 0)
            val down = o.optInt("downloaded", 0)
            val cf = o.optInt("conflicts", 0)
            val msg = o.optString("message", "")
            when {
                msg == "ok" || msg.isEmpty() -> ("同步完成  ↑$up  ↓$down" + if (cf > 0) "  冲突$cf" else "") to true
                else -> msg to false
            }
        } catch (_: Throwable) {
            "同步结果无法解析" to false
        }
    }

    DshScreen(modifier) {
        DshNavBar(onBack = onBack, title = "NAS 连接", stacked = true)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            // —— 状态卡 ——
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(8.dp).background(
                        if (configured) c.ok else c.warn, CircleShape,
                    ),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (configured) "已配置 NAS 同步" else "尚未配置 NAS",
                        style = Type.rowTitle, color = c.ink,
                    )
                    Text(
                        if (configured) DshSync.url(ctx).ifBlank { "—" }
                        else "飞牛 NAS（fnOS）WebDAV 同步",
                        style = Type.meta, color = c.ink3,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // —— 表单 ——
            NasField("WebDAV 地址", url, { url = it }, mono = true,
                placeholder = "http://192.168.1.10:5005")
            Spacer(Modifier.height(10.dp))
            NasField("账号", user, { user = it }, placeholder = "NAS 用户名")
            Spacer(Modifier.height(10.dp))
            NasField("密码", pass, { pass = it }, password = true, reveal = reveal,
                onReveal = { reveal = !reveal },
                placeholder = if (configured) "留空 = 不修改" else "NAS 密码")
            Spacer(Modifier.height(10.dp))
            NasField("同步间隔（秒，≥30）", interval, { interval = it }, number = true,
                placeholder = "60")

            Spacer(Modifier.height(14.dp))

            // —— 操作按钮 ——
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DshBtn(
                    "测试连接", modifier = Modifier.weight(1f), small = true,
                    tone = BtnTone.Ghost,
                    enabled = !busy && url.isNotBlank() && user.isNotBlank(),
                    onClick = {
                        busy = true; status = null
                        scope.launch(Dispatchers.IO) {
                            val ok = DshSync.testConnection(
                                ctx, url, user, if (pass.isBlank()) null else pass,
                            )
                            status = if (ok) "连接成功，WebDAV 可达" else "连接失败：检查地址 / 账号密码 / 是否开启 WebDAV"
                            statusOk = ok
                            busy = false
                        }
                    },
                )
                DshBtn(
                    "保存", modifier = Modifier.weight(1f), small = true,
                    enabled = !busy && url.isNotBlank() && user.isNotBlank(),
                    onClick = {
                        val iv = interval.toIntOrNull() ?: 60
                        DshSync.configure(ctx, url, user, if (pass.isBlank()) null else pass, iv)
                        reload()
                        status = "配置已保存"
                        statusOk = true
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DshBtn(
                    "立即同步", modifier = Modifier.weight(1f), small = true,
                    tone = BtnTone.Ghost,
                    enabled = !busy && configured,
                    onClick = {
                        busy = true; status = "同步中…"
                        scope.launch(Dispatchers.IO) {
                            DshSync.syncOnce(ctx)
                            val (msg, ok) = parseResult(DshSync.lastResultJson(ctx))
                            status = msg
                            statusOk = ok
                            busy = false
                        }
                    },
                )
                DshBtn(
                    "断开", modifier = Modifier.weight(1f), small = true,
                    tone = BtnTone.Ghost,
                    enabled = !busy && configured,
                    onClick = {
                        DshSync.clear(ctx)
                        reload()
                        status = "已断开 NAS 同步"
                        statusOk = false
                    },
                )
            }

            // —— 结果提示 ——
            if (status != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.lg))
                        .background(if (statusOk) c.soft else c.card)
                        .border(1.dp, if (statusOk) c.ok else c.warn, RoundedCornerShape(Radius.lg))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(7.dp).background(if (statusOk) c.ok else c.warn, CircleShape))
                    Spacer(Modifier.width(9.dp))
                    Text(status!!, style = Type.sec, color = c.ink2)
                }
            }

            Spacer(Modifier.height(14.dp))
            DshSecTitle("说明", icon = DshIcons.Info)
            Text(
                "· 目标端：飞牛 NAS（fnOS）WebDAV，默认端口 5005（HTTP）/ 5006（HTTPS）。\n" +
                    "· 团队文件夹需勾「允许通过文件共享协议挂载到其他设备」，否则连上但看不到目录。\n" +
                    "· 只同步会话 / 工作区数据（.dsh/storages），不同步 node_modules 等平台相关文件。\n" +
                    "· 这是「NAS 同步」，与「外部存储(SAF)」是两套独立能力，互不影响。",
                style = Type.meta.copy(lineHeight = 18.sp), color = c.ink3,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** NAS 表单输入行（密码支持显隐，数字限定 Number 键盘）。 */
@Composable
private fun NasField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    password: Boolean = false,
    reveal: Boolean = false,
    onReveal: (() -> Unit)? = null,
    number: Boolean = false,
    placeholder: String = "",
) {
    val c = DshTheme.colors
    Column(modifier.fillMaxWidth()) {
        Text(label, style = Type.secTitle, color = c.ink3,
            modifier = Modifier.padding(bottom = 7.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(Radius.lg))
                .background(c.card)
                .border(1.dp, if (value.isNotEmpty()) c.brand else c.line, RoundedCornerShape(Radius.lg))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(
                    TextStyle(
                        fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                        fontSize = 13.sp, color = c.ink,
                    )
                ),
                cursorBrush = SolidColor(c.brand),
                visualTransformation = if (password && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (number) KeyboardType.Number
                    else if (password) KeyboardType.Password else KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                decorationBox = { inner ->
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, style = Type.meta, color = c.ink3)
                    }
                    inner()
                },
            )
            if (password && onReveal != null) {
                Spacer(Modifier.width(8.dp))
                DshIBtn(
                    if (reveal) DshIcons.VisibilityOff else DshIcons.Visibility,
                    if (reveal) "隐藏" else "显示", size = 22.dp, onClick = onReveal,
                )
            }
        }
    }
}
