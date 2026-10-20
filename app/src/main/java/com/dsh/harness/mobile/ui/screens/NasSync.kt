package com.dsh.harness.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.harness.DshSync
import com.dsh.harness.mobile.ui.components.*
import com.dsh.harness.mobile.ui.theme.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ============================ S51 NAS 连接（状态页） ============================
/**
 * S51 NAS 连接（飞牛 fnOS 的 WebDAV 同步）—— 严格对齐设计稿 v11 的 **S16**。
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
 * 诚实约束（v27/v29 既定原则）：设计稿 S16 里**后端没有真实数据**的部分
 * （容量水位条、设备发现名「群晖 DS920+」、3 条映射、只读挂载按钮）
 * 一律不编假——设备名用 URL 主机、映射只列真实的那 1 条、容量与只读挂载略去。
 */
@Composable
fun S51NasSync(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onAdd: () -> Unit = {},
    onHelp: () -> Unit = {},
) {
    val c = DshTheme.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var configured by remember { mutableStateOf(DshSync.isConfigured(ctx)) }
    var url by remember { mutableStateOf(DshSync.url(ctx)) }
    var lastJson by remember { mutableStateOf(DshSync.lastResultJson(ctx)) }
    var connected by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusOk by remember { mutableStateOf(false) }

    fun reload() {
        configured = DshSync.isConfigured(ctx)
        url = DshSync.url(ctx)
        lastJson = DshSync.lastResultJson(ctx)
        status = null
    }

    // 已配置态：实测一次连接，取真实可达性
    LaunchedEffect(Unit) {
        if (configured) {
            testing = true
            val ok = DshSync.testConnection(ctx, url, DshSync.user(ctx), null)
            connected = ok
            testing = false
        }
    }

    fun parseResult(json: String): Pair<String, Boolean> {
        return try {
            val o = org.json.JSONObject(json)
            val up = o.optInt("uploaded", 0)
            val down = o.optInt("downloaded", 0)
            val cf = o.optInt("conflicts", 0)
            val msg = o.optString("message", "")
            when {
                o.optBoolean("ran", false) && msg.isEmpty() ->
                    ("同步完成  ↑$up  ↓$down" + if (cf > 0) "  冲突$cf" else "") to true
                else -> (if (msg.isEmpty()) "本次未执行" else msg) to false
            }
        } catch (_: Throwable) {
            "同步结果无法解析" to false
        }
    }

    DshScreen(modifier) {
        DshNavBar(onBack = onBack, title = "NAS 连接", stacked = true,
            subtitle = "飞牛 NAS · WebDAV")

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            if (!configured) {
                // —— 空态（对应设计稿 S16「未配置」走向）——
                Spacer(Modifier.height(28.dp))
                DshEmpty("尚未配置 NAS 连接", icon = DshIcons.CloudOff)
                Spacer(Modifier.height(16.dp))
                DshBtn("添加 NAS 连接", icon = DshIcons.Add, onClick = onAdd)
                Spacer(Modifier.height(12.dp))
                Text(
                    "需与 NAS 同一内网 · 当前仅支持 WebDAV",
                    style = Type.meta, color = c.ink3,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                // —— 设备卡（S16 顶部）——
                DshCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(Metrics.ICON_BOX)
                                .background(c.soft, RoundedCornerShape(Radius.md)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(DshIcons.Dns, null, Modifier.size(19.dp), tint = c.brand)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(hostOf(url), style = Type.rowTitle, color = c.ink,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("WebDAV · $url", style = Type.meta, color = c.ink3,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        val ct: ChipTone = if (testing) ChipTone.Neutral else if (connected) ChipTone.Ok else ChipTone.Warn
                        val cl: String = if (testing) "连接检测中" else if (connected) "已连接" else "未连接"
                        DshChip(cl, tone = ct, mini = true)
                    }

                    DshDivider()
                    Spacer(Modifier.height(10.dp))

                    // 上次同步 + 断开
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            lastSyncText(lastJson) ?: "尚未同步",
                            style = Type.sec, color = c.ink3,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "断开",
                            style = Type.sec.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                            color = c.danger,
                            modifier = Modifier
                                .clickable(enabled = !busy) {
                                    DshSync.clear(ctx)
                                    reload()
                                    connected = false
                                }
                                .padding(4.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    // 立即同步（真实能力；设计稿的「只读挂载」后端未实现，不编假）
                    DshBtn(
                        "立即同步", icon = DshIcons.Sync, small = true, tone = BtnTone.Ghost,
                        enabled = !busy && !testing,
                        onClick = {
                            busy = true; status = "同步中…"
                            scope.launch(Dispatchers.IO) {
                                DshSync.syncOnce(ctx)
                                val (msg, ok) = parseResult(DshSync.lastResultJson(ctx))
                                lastJson = DshSync.lastResultJson(ctx)
                                status = msg
                                statusOk = ok
                                busy = false
                            }
                        },
                    )
                }

                Spacer(Modifier.height(14.dp))

                // —— 目录映射卡（S16：只列后端真实存在的 1 条）——
                DshSecTitle("目录映射", icon = DshIcons.FolderOpen)
                DshCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(Metrics.ICON_BOX)
                                .background(c.soft, RoundedCornerShape(Radius.md)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(DshIcons.FolderShared, null, Modifier.size(19.dp), tint = c.brand)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(".dsh/storages/", style = Type.rowTitle, color = c.ink,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("↔ dsh-phone/storages", style = Type.sec, color = c.ink3,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        DshChip("双向", tone = ChipTone.Brand, mini = true)
                    }
                }

                Spacer(Modifier.height(14.dp))

                // —— 同步边界软底卡（S16：shield + 编号项，改为真实行为）——
                DshSecTitle("同步边界", icon = DshIcons.Shield)
                SoftCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(DshIcons.Shield, null, Modifier.size(15.dp), tint = c.brand)
                        Spacer(Modifier.width(6.dp))
                        Text("以下行为由同步引擎保证", style = Type.secTitle, color = c.ink2)
                    }
                    Spacer(Modifier.height(10.dp))
                    BoundaryItem("1", "只同步会话 / 工作区数据（.dsh/storages），不同步 node_modules 等平台文件")
                    BoundaryItem("2", "离线或 NAS 不可达时自动跳过，不打断使用")
                    BoundaryItem("3", "冲突保留双份（.conflict-<设备>-<时间戳>.json），不静默覆盖")
                    BoundaryItem("4", "凭据写入后不回读，界面只显示占位，保障安全")
                }

                Spacer(Modifier.height(14.dp))

                // —— 添加 NAS 连接（S16 幽灵按钮）——
                DshBtn("添加 NAS 连接", icon = DshIcons.Add, tone = BtnTone.Ghost, onClick = onAdd)
                Spacer(Modifier.height(10.dp))
                Text(
                    "挂载由系统提供 · 需与 NAS 同一内网 · 仅支持 WebDAV",
                    style = Type.meta, color = c.ink3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // —— 操作结果提示 ——
            if (status != null) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.lg))
                        .background(if (statusOk) c.soft else c.card)
                        .border(1.dp, if (statusOk) c.ok else c.warn, RoundedCornerShape(Radius.lg))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(7.dp).background(if (statusOk) c.ok else c.warn, RoundedCornerShape(50)))
                    Spacer(Modifier.width(9.dp))
                    Text(status!!, style = Type.sec, color = c.ink2)
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

// ============================ S52 添加 NAS 连接（表单页） ============================
/**
 * S52 添加 NAS 连接 —— 严格对齐设计稿 v11 的 **S17**。
 *
 * 诚实约束：设计稿 S17 里的「连接类型 SMB/SFTP」「同步方向」「同步范围」
 * 「冲突处理」「挂载为工作区来源 开关」在后端均**未实现**（当前仅 WebDAV +
 * .dsh/storages 双向选择性同步 + 冲突保留双份）。这些控件不编假，
 * 而是用一段软底说明如实告知用户，并保留 WebDAV 表单做真实连接与保存。
 */
@Composable
fun S52NasAdd(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onSaved: () -> Unit = {},
) {
    val c = DshTheme.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var proto by remember { mutableStateOf(0) }   // 0=WebDAV 1=SMB 2=SFTP
    var addr by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("5005") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusOk by remember { mutableStateOf(false) }

    val webdavOnly = proto == 0
    val canTest = !busy && webdavOnly && addr.isNotBlank() && user.isNotBlank()

    DshScreen(modifier) {
        DshNavBar(onBack = onBack, title = "添加 NAS 连接", stacked = true)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            // —— 连接类型 seg（S17）——
            DshSecTitle("连接类型", icon = DshIcons.Dns)
            DshSeg(listOf("WebDAV", "SMB", "SFTP"), proto) { proto = it }
            if (!webdavOnly) {
                Spacer(Modifier.height(10.dp))
                SoftCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(DshIcons.Info, null, Modifier.size(15.dp), tint = c.warn)
                        Spacer(Modifier.width(6.dp))
                        Text("该协议暂未接入，当前版本仅支持 WebDAV。", style = Type.sec, color = c.ink2)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // —— 地址 + 端口（S17 成对）——
            DshSecTitle("地址", icon = DshIcons.Lan)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NasField("主机", addr, { addr = it }, modifier = Modifier.weight(1f), mono = true,
                    placeholder = "192.168.1.10")
                NasField("端口", port, { port = it }, modifier = Modifier.weight(0.6f), number = true,
                    placeholder = "5005")
            }

            Spacer(Modifier.height(14.dp))

            // —— 账号 + 密码（S17 成对）——
            DshSecTitle("凭据", icon = DshIcons.VpnKey)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NasField("账号", user, { user = it }, modifier = Modifier.weight(1f),
                    placeholder = "NAS 用户名")
                NasField("密码 / Token", pass, { pass = it }, modifier = Modifier.weight(1f),
                    password = true, reveal = reveal, onReveal = { reveal = !reveal },
                    placeholder = "NAS 密码")
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(DshIcons.Lock, null, Modifier.size(13.dp), tint = c.ink3)
                Spacer(Modifier.width(5.dp))
                Text("密钥只写不回读：保存后输入框留空即保留原密码，界面永不显示明文。",
                    style = Type.meta, color = c.ink3)
            }

            Spacer(Modifier.height(14.dp))

            // —— 能力说明软底卡（诚实替代 S17 未实现的方向/范围/冲突/开关）——
            SoftCard {
                Icon(DshIcons.Info, null, Modifier.size(15.dp), tint = c.brand)
                Spacer(Modifier.height(6.dp))
                Text("当前版本仅做 .dsh/storages 双向选择性同步：",
                    style = Type.secTitle, color = c.ink2)
                Spacer(Modifier.height(8.dp))
                BoundaryItem("·", "同步方向固定为双向（暂不支持仅上传 / 仅手动）")
                BoundaryItem("·", "同步范围固定为会话 / 工作区数据")
                BoundaryItem("·", "冲突处理固定为保留双份，不静默覆盖")
                BoundaryItem("·", "挂载为工作区来源、SMB / SFTP 暂未接入")
            }

            // —— 操作结果 ——
            if (status != null) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.lg))
                        .background(if (statusOk) c.soft else c.card)
                        .border(1.dp, if (statusOk) c.ok else c.warn, RoundedCornerShape(Radius.lg))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(7.dp).background(if (statusOk) c.ok else c.warn, RoundedCornerShape(50)))
                    Spacer(Modifier.width(9.dp))
                    Text(status!!, style = Type.sec, color = c.ink2)
                }
            }

            Spacer(Modifier.height(16.dp))

            // —— 取消 + 连接并测试（S17）——
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DshBtn("取消", modifier = Modifier.weight(1f), small = true, tone = BtnTone.Ghost,
                    enabled = !busy, onClick = onBack)
                DshBtn("连接并测试", modifier = Modifier.weight(1f), small = true,
                    enabled = canTest,
                    onClick = {
                        busy = true; status = "连接测试中…"
                        scope.launch(Dispatchers.IO) {
                            val u = buildUrl(addr, port)
                            val ok = DshSync.testConnection(ctx, u, user, if (pass.isBlank()) null else pass)
                            if (ok) {
                                DshSync.configure(ctx, u, user, if (pass.isBlank()) null else pass, 60)
                                status = "连接成功，已保存 NAS 连接"
                                statusOk = true
                                onSaved()
                            } else {
                                status = "连接失败：检查地址 / 账号密码 / 是否开启 WebDAV"
                                statusOk = false
                                busy = false
                            }
                        }
                    },
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

// ============================ 私有组件 ============================

/** 软底说明卡（设计稿 S16/S17 的边界/说明块）。 */
@Composable
private fun SoftCard(content: @Composable ColumnScope.() -> Unit) {
    val c = DshTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xl))
            .background(c.soft)
            .padding(14.dp),
        content = content,
    )
}

/** 编号边界项（单行，可省略号）。 */
@Composable
private fun BoundaryItem(n: String, text: String) {
    val c = DshTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(n, style = Type.sec, color = c.brand, modifier = Modifier.width(16.dp))
        Spacer(Modifier.width(2.dp))
        Text(text, style = Type.sec, color = c.ink2, modifier = Modifier.weight(1f))
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

// ============================ 私有工具 ============================

/** 从 WebDAV URL 取主机标识（设计稿设备名由后端发现，这里如实用主机替代）。 */
private fun hostOf(u: String): String {
    var s = u.trim().replaceFirst("(?i)^https?://".toRegex(), "")
    val slash = s.indexOf('/')
    if (slash >= 0) s = s.substring(0, slash)
    return s.ifBlank { "NAS" }
}

/** 拼出 http://host:port（端口空则不带）。 */
private fun buildUrl(addr: String, port: String): String {
    val a = addr.trim().trimEnd('/')
    val p = port.trim()
    return if (p.isEmpty()) "http://$a" else "http://$a:$p"
}

/** 解析 lastResultJson 的上次同步时间，无数据返回 null。 */
private fun lastSyncText(json: String): String? {
    if (json.isBlank()) return null
    return try {
        val o = org.json.JSONObject(json)
        val at = o.optLong("at", 0L)
        if (at <= 0) return null
        val up = o.optInt("uploaded", 0)
        val down = o.optInt("downloaded", 0)
        val hhmm = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(at))
        "上次同步 $hhmm · ${up + down} 文件"
    } catch (_: Throwable) {
        null
    }
}
