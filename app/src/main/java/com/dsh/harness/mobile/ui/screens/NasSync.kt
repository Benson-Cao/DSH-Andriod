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
import androidx.compose.ui.draw.alpha
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

// ============================ S51 NAS 连接（状态页，照设计稿 v20 S16） ============================
/**
 * S51 NAS 连接（飞牛 fnOS 的 WebDAV 同步）—— 严格对齐设计稿 v20 的 **S16**。
 *
 * 真能力：底层 [DshSync] + [com.dsh.harness.DshWebDavClient]（纯 JDK WebDAV 客户端，
 * 凭据存 SharedPreferences，只同步 `.dsh/storages/`，不同步 node_modules）。
 *
 * 与「外部存储(SAF)」（`S50StorageDir`）是**两套独立能力**（用户：nas是nas，saf桥是saf桥）。
 *
 * 诚实约束（项目既定 v27/v29 原则，设计稿 v20 的样例数据不编假）：
 *  - 设备发现名「群晖 DS920+」后端无法获得 → 用 URL 主机替代；
 *  - 容量水位条（218 GB / 1.0 TB）后端无数据 → 略去；
 *  - 3 条映射（工作区/单向/定时）实为设计样例 → 只列后端真实存在的 1 条；
 *  - 「只读挂载」「挂载为工作区来源」后端未实现 → 按钮保留布局但点按如实提示暂未接入。
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
        DshNavBar(
            onBack = onBack, title = "NAS 连接", stacked = true,
            subtitle = "飞牛 NAS · WebDAV",
            actions = {
                DshIBtn(DshIcons.HelpOutline, "说明", onClick = {
                    status = "NAS 同步仅同步会话/工作区数据（.dsh/storages），不同步 node_modules 等平台文件；凭据加密保存、只写不回读。"
                    statusOk = true
                    onHelp()
                })
            },
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            if (!configured) {
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

                    // 设计稿 v20 的容量水位条（218 GB / 1.0 TB）后端无数据，按诚实原则略去。

                    DshDivider()
                    Spacer(Modifier.height(10.dp))

                    // 上次同步 + 断开
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(DshIcons.Sync, null, Modifier.size(15.dp), tint = if (connected) c.ok else c.ink3)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            lastSyncText(lastJson) ?: "尚未同步",
                            style = Type.sec, color = c.ink2,
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
                                    status = "已断开 NAS 同步"
                                    statusOk = false
                                }
                                .padding(4.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    // 立即同步（真实）+ 只读挂载（后端未实现，如实提示）
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DshBtn(
                            "立即同步", icon = DshIcons.Sync, modifier = Modifier.weight(1.4f),
                            small = true, tone = BtnTone.Ghost,
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
                        DshBtn(
                            "只读挂载", modifier = Modifier.weight(1f), small = true, tone = BtnTone.Ghost,
                            onClick = {
                                status = "NAS 只读挂载暂未接入（当前版本仅做 .dsh/storages 双向同步）"
                                statusOk = false
                            },
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // —— 目录映射（S16：只列后端真实存在的 1 条）——
                DshSecTitle("目录映射", note = "1 条", icon = DshIcons.FolderOpen)
                DshCard {
                    DshMRow(
                        title = ".dsh/storages/ ↔ dsh-phone/storages",
                        subtitle = "会话与轨迹 · 双向",
                        icon = DshIcons.FolderShared,
                        iconTone = ChipTone.Brand,
                        trailing = { DshChip("双向", tone = ChipTone.Brand, mini = true) },
                    )
                }

                Spacer(Modifier.height(14.dp))

                // —— 同步边界软底卡（S16：shield，改写为真实行为）——
                DshSecTitle("同步边界", icon = DshIcons.Shield)
                SoftCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(DshIcons.Shield, null, Modifier.size(15.dp), tint = c.brand)
                        Spacer(Modifier.width(6.dp))
                        Text("以下行为由同步引擎保证", style = Type.secTitle, color = c.ink2)
                    }
                    Spacer(Modifier.height(10.dp))
                    BoundaryItem("1", "同步只作用于 .dsh/storages/，不同步 node_modules 等平台文件")
                    BoundaryItem("2", "写向 NAS 之外的路径照旧走审批 Sheet（S7）")
                    BoundaryItem("3", "NAS 离线或不可达时自动跳过，不阻塞使用")
                    BoundaryItem("4", "凭据加密保存、只写不回读，不进会话上下文")
                }

                Spacer(Modifier.height(14.dp))

                // —— 添加 NAS 连接（S16 幽灵按钮）——
                DshBtn("添加 NAS 连接", icon = DshIcons.Add, tone = BtnTone.Ghost, onClick = onAdd)
                Spacer(Modifier.height(10.dp))
                Text(
                    "挂载由系统提供 · 需与 NAS 同一内网",
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

// ============================ S52 添加 NAS 连接（表单页，照设计稿 v20 S17） ============================
/**
 * S52 添加 NAS 连接 —— 严格对齐设计稿 v20 的 **S17**。
 *
 * 诚实约束：v20 S17 里的「连接类型 SMB/SFTP」「共享目录 / 本地挂载点」
 * 「同步方向」「同步范围」「冲突处理」「离线时转只读 / 挂载为工作区来源 开关」
 * 在后端均**未实现**（当前仅 WebDAV + .dsh/storages 双向选择性同步 + 冲突保留双份）。
 * 这些控件按 v20 布局渲染但置灰并标注「暂未接入」，不编假、不参与保存；
 * 仅 WebDAV 的 地址/端口/账号/密码 走真实连接与保存。
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
    var shareDir by remember { mutableStateOf("") }
    var mountPoint by remember { mutableStateOf("") }
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
            FieldLabelRow("连接类型") {
                if (!webdavOnly) DshChip("该协议暂未接入", tone = ChipTone.Warn, mini = true)
            }
            DshSeg(listOf("WebDAV", "SMB", "SFTP"), proto) {
                proto = it
                if (it != 0) {
                    status = "该协议暂未接入，当前版本仅支持 WebDAV"
                    statusOk = false
                }
            }

            Spacer(Modifier.height(14.dp))

            // —— 地址 + 端口（S17 成对，真实）——
            FieldLabelRow("地址 / 端口")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NasField("地址", addr, { addr = it }, modifier = Modifier.weight(1f), mono = true,
                    placeholder = "192.168.1.10")
                NasField("端口", port, { port = it }, modifier = Modifier.weight(0.6f), number = true,
                    placeholder = "5005")
            }

            Spacer(Modifier.height(14.dp))

            // —— 共享目录 + 本地挂载点（S17 成对，暂未接入）——
            FieldLabelRow("共享目录 / 本地挂载点") {
                DshChip("暂未接入", tone = ChipTone.Warn, mini = true)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.alpha(0.55f),
            ) {
                NasField("共享目录", shareDir, { shareDir = it }, modifier = Modifier.weight(1f), mono = true,
                    placeholder = "/vault/dsh-sync")
                NasField("本地挂载点", mountPoint, { mountPoint = it }, modifier = Modifier.weight(1f), mono = true,
                    placeholder = "~/dsh/nas-mount")
            }

            Spacer(Modifier.height(14.dp))

            // —— 账号 + 密码（S17 成对，真实）——
            FieldLabelRow("账号 / 密码")
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
                Text("密钥只写不回读，编辑时需重新填写。", style = Type.meta, color = c.ink3)
            }

            Spacer(Modifier.height(14.dp))

            // —— 同步方向（S17，暂未接入）——
            FieldLabelRow("同步方向") { DshChip("暂未接入", tone = ChipTone.Warn, mini = true) }
            DshSeg(listOf("双向", "仅上传", "仅手动"), 0,
                modifier = Modifier.alpha(0.55f), onSelect = {})

            Spacer(Modifier.height(14.dp))

            // —— 同步范围（S17，暂未接入）——
            FieldLabelRow("同步范围") { DshChip("暂未接入", tone = ChipTone.Warn, mini = true) }
            DshCard(
                Modifier.alpha(0.55f)
            ) {
                DshMRow(
                    title = ".dsh/storages/（会话与轨迹）",
                    subtitle = "当前版本固定范围",
                    trailing = { Text("1 项 ›", style = Type.meta, color = c.ink3) },
                )
            }

            Spacer(Modifier.height(14.dp))

            // —— 冲突处理（S17，暂未接入）——
            FieldLabelRow("冲突处理") { DshChip("暂未接入", tone = ChipTone.Warn, mini = true) }
            DshSeg(listOf("保留两份", "以最新为准"), 0,
                modifier = Modifier.alpha(0.55f), onSelect = {})

            Spacer(Modifier.height(14.dp))

            // —— 两个开关（S17，暂未接入）——
            FieldLabelRow("挂载选项") { DshChip("暂未接入", tone = ChipTone.Warn, mini = true) }
            DshCard(Modifier.alpha(0.55f)) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("离线时转只读", style = Type.rowTitle, color = c.ink)
                        Text("避免任务写到半空目录", style = Type.sec, color = c.ink3)
                    }
                    DshSwitch(checked = true, onChange = {})
                }
                DshDivider()
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("挂载为工作区来源", style = Type.rowTitle, color = c.ink)
                        Text("新会话可直接选该目录", style = Type.sec, color = c.ink3)
                    }
                    DshSwitch(checked = false, onChange = {})
                }
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

/** 字段组标签行（标签 + 右侧可选「暂未接入」等标注）。 */
@Composable
private fun FieldLabelRow(label: String, trailing: (@Composable () -> Unit)? = null) {
    val c = DshTheme.colors
    Row(
        Modifier.padding(bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = Type.secTitle, color = c.ink3)
        if (trailing != null) trailing()
    }
}

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
