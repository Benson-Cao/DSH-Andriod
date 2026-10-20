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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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

// ============================ S51 NAS 连接（状态页，照设计稿 v22 S16/S34） ============================
/**
 * S51 NAS 连接 —— 严格对齐设计稿 v22 的 **S16（已连接）** 与 **S34（未连接空态）**。
 *
 * - S34 空态：cloud_off 图标 + 还没有连接 NAS + 说明 + 「连接 NAS」主按钮 +
 *   支持协议 chips + 前置条件 3 行（各带副标题）+ 「连不上先查这两项」编号文案 +
 *   底部互链「已连接？查看连接后状态」。
 * - S16 已连接：设备卡（名称 / 协议·地址 mono / 已连接 chip）+ 上次同步 + 断开（右对齐）
 *   + 立即同步 / 只读挂载；目录映射逐条标方向 chip；软底卡写同步边界 4 条；
 *   末尾「添加 NAS 连接」+ footer。
 * - v30.7 新增：NAS 断连时顶部横幅「NAS 连接中断 · 已转只读」（设计稿 S18 同款），
 *   数据源是 [DshSync.readOnlyNow]——由**挂载状态**触发，不是 Agent 判断。
 *
 * 诚实约束（项目既定原则，设计稿样例数据不编假）：
 *  - 容量水位条后端无数据 → 略去，改为显示真实的上次同步时间与文件数；
 *  - 目录映射条数由实际生效的方向/范围算出，不写死「3 条」。
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
    var proto by remember { mutableStateOf(DshSync.type(ctx)) }
    var display by remember { mutableStateOf(DshSync.url(ctx)) }
    var lastJson by remember { mutableStateOf(DshSync.lastResultJson(ctx)) }
    var connected by remember { mutableStateOf(DshSync.lastPingOk(ctx)) }
    var testing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusOk by remember { mutableStateOf(false) }
    // v30.7：S17 同步选项的回显（S16 目录映射 chip / S18 只读横幅都读它）
    var direction by remember { mutableStateOf(DshSync.direction(ctx)) }
    var scopeMask by remember { mutableStateOf(DshSync.scopeMask(ctx)) }
    var conflict by remember { mutableStateOf(DshSync.conflictPolicy(ctx)) }
    var offlineRO by remember { mutableStateOf(DshSync.offlineReadOnly(ctx)) }
    var mountAsWs by remember { mutableStateOf(DshSync.mountAsWorkspace(ctx)) }

    val protoName = when (proto) { "smb" -> "SMB"; "sftp" -> "SFTP"; else -> "WebDAV" }

    fun reload() {
        configured = DshSync.isConfigured(ctx)
        proto = DshSync.type(ctx)
        display = DshSync.url(ctx)
        lastJson = DshSync.lastResultJson(ctx)
        direction = DshSync.direction(ctx)
        scopeMask = DshSync.scopeMask(ctx)
        conflict = DshSync.conflictPolicy(ctx)
        offlineRO = DshSync.offlineReadOnly(ctx)
        mountAsWs = DshSync.mountAsWorkspace(ctx)
        status = null
    }

    // 已配置态：实测一次连接，取真实可达性（并写入 lastPing 缓存供 S18 只读判断）
    LaunchedEffect(Unit) {
        if (configured) {
            testing = true
            connected = DshSync.probeHealth(ctx)
            testing = false
        }
    }

    // v30.7：断连横幅。降级由挂载状态触发（lastPingOk=false），不是 Agent 判断。
    val readOnlyDegraded = configured && offlineRO && !connected && !testing

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
            subtitle = if (configured) "设置 › NAS 已连接" else "设置 › NAS 未连接",
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
                // —— 空态（严格对齐设计稿 v22 S34）——
                Spacer(Modifier.height(14.dp))
                DshCard {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(54.dp)
                                .background(c.line2, RoundedCornerShape(17.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(DshIcons.CloudOff, null, Modifier.size(26.dp), tint = c.ink3)
                        }
                        Spacer(Modifier.height(11.dp))
                        Text("还没有连接 NAS", style = Type.secTitle, color = c.ink)
                        Spacer(Modifier.height(7.dp))
                        Text(
                            "连上之后可以把挂载目录当工作区来源，\n或只作为产物备份目标",
                            style = Type.sec, color = c.ink3,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Spacer(Modifier.height(15.dp))
                        DshBtn(
                            "连接 NAS", icon = DshIcons.Add,
                            modifier = Modifier.fillMaxWidth(), onClick = onAdd,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // —— 支持协议（S34 独立段，WebDAV 为默认高亮）——
                DshSecTitle("支持协议")
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    DshChip("WebDAV", tone = ChipTone.Brand, mini = true)
                    DshChip("SMB", tone = ChipTone.Neutral, mini = true)
                    DshChip("SFTP", tone = ChipTone.Neutral, mini = true)
                }

                Spacer(Modifier.height(16.dp))

                // —— 前置条件（S34：三行均带副标题）——
                DshSecTitle("前置条件")
                DshCard {
                    DshMRow(
                        "与 NAS 处于同一内网",
                        subtitle = "跨网需先打通路由或走 VPN",
                        icon = DshIcons.Wifi, iconTone = ChipTone.Neutral,
                    )
                    DshDivider()
                    DshMRow(
                        "共享目录可读写",
                        subtitle = "仅上传映射只能作备份目标",
                        icon = DshIcons.FolderShared, iconTone = ChipTone.Neutral,
                    )
                    DshDivider()
                    DshMRow(
                        "凭据只写不回读",
                        subtitle = "与模型密钥同一套保存规则",
                        icon = DshIcons.Shield, iconTone = ChipTone.Neutral,
                    )
                }

                Spacer(Modifier.height(16.dp))

                // —— 连不上先查这两项（S34：卡内编号连续文案）——
                DshSecTitle("连不上先查这两项")
                DshCard {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                        TroubleshootItem("1", "端口不通", "测试连接报超时或 401，先确认端口与 HTTPS")
                        TroubleshootItem("2", "目录不可写", "映射方向改「仅上传」或换共享目录")
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "已连接？查看连接后状态",
                    style = Type.sec,
                    color = c.brand,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { status = "已保存的连接会显示在这里：设备卡 · 目录映射 · 同步边界"; statusOk = true }
                        .padding(vertical = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            } else {
                // —— v30.7：断连只读横幅（设计稿 S18 同款，文案对齐）——
                if (readOnlyDegraded) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 2.dp)
                            .clip(RoundedCornerShape(Radius.lg))
                            .background(c.warn.copy(alpha = 0.12f))
                            .border(1.dp, c.warn.copy(alpha = 0.32f), RoundedCornerShape(Radius.lg))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(DshIcons.ErrorOutline, null, Modifier.size(17.dp), tint = c.warn)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text("NAS 连接中断 · 已转只读", style = Type.secTitle, color = c.warn)
                            Text(
                                "写入与同步已暂停 · " + (lastSyncText(lastJson) ?: "尚未同步过"),
                                style = Type.meta, color = c.ink2,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        DshBtn(
                            "重连", small = true, modifier = Modifier.widthIn(min = 64.dp),
                            enabled = !testing,
                            onClick = {
                                testing = true; status = "重连中…"
                                scope.launch(Dispatchers.IO) {
                                    val ok = DshSync.probeHealth(ctx)
                                    connected = ok
                                    testing = false
                                    status = if (ok) "已恢复连接，同步与写入已放开"
                                    else "仍连不上——展开下方「连不上查这两项」逐条排查"
                                    statusOk = ok
                                }
                            },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // —— 设备卡（S16 顶部）——
                DshCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(34.dp)
                                .background(c.soft, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(DshIcons.Dns, null, Modifier.size(18.dp), tint = c.brand)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(hostOf(display), style = Type.rowTitle, color = c.ink,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("$protoName · $display", style = Type.meta, color = c.ink3,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        val ct: ChipTone = if (testing) ChipTone.Neutral else if (connected) ChipTone.Ok else ChipTone.Warn
                        val cl: String = when {
                            testing -> "检测中"
                            connected -> "已连接"
                            offlineRO -> "已转只读"
                            else -> "未连接"
                        }
                        DshChip(cl, tone = ct, mini = true)
                    }

                    DshDivider()
                    Spacer(Modifier.height(10.dp))

                    // 上次同步 + 断开（S16：断开右对齐、danger 色）
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(DshIcons.Sync, null, Modifier.size(15.dp), tint = if (connected) c.ok else c.ink3)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            lastSyncText(lastJson) ?: "尚未同步",
                            style = Type.sec, color = c.ink2,
                            modifier = Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "断开",
                            style = Type.sec.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                            color = c.danger,
                            modifier = Modifier
                                .clickable(enabled = !busy) {
                                    DshSync.clear(ctx)
                                    reload()
                                    connected = false
                                    status = "已断开连接并卸载挂载点"
                                    statusOk = false
                                }
                                .padding(4.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    // 立即同步（真实，受「仅上传」等选项约束）+ 只读挂载
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DshBtn(
                            "立即同步", icon = DshIcons.Sync, modifier = Modifier.weight(1.4f),
                            small = true, tone = BtnTone.Ghost,
                            enabled = !busy && !testing && connected,
                            onClick = {
                                busy = true; status = "同步中…"
                                scope.launch(Dispatchers.IO) {
                                    DshSync.syncOnce(ctx)
                                    val (msg, ok) = parseResult(DshSync.lastResultJson(ctx))
                                    lastJson = DshSync.lastResultJson(ctx)
                                    connected = DshSync.lastPingOk(ctx)
                                    status = msg
                                    statusOk = ok
                                    busy = false
                                }
                            },
                        )
                        DshBtn(
                            if (mountAsWs) "取消工作区挂载" else "只读挂载",
                            icon = if (mountAsWs) DshIcons.CloudOff else DshIcons.Cloud,
                            modifier = Modifier.weight(1.3f),
                            small = true, tone = BtnTone.Ghost,
                            onClick = {
                                val on = !mountAsWs
                                DshSync.setMountAsWorkspace(ctx, on)
                                mountAsWs = on
                                status = if (on)
                                    "已把 $display 挂为工作区来源：新会话可直接选该目录"
                                else "已取消工作区挂载，回到本地工作区"
                                statusOk = on
                            },
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // —— 目录映射（S16：逐条标方向 chip；条数与方向由真实配置算出）——
                val dirText = when (direction) {
                    DshSync.DIR_UPLOAD -> "仅上传"
                    DshSync.DIR_MANUAL -> "仅手动"
                    else -> "双向"
                }
                val scopeCount = listOf(
                    DshSync.SCOPE_SESSION, DshSync.SCOPE_WORKSPACE, DshSync.SCOPE_ARTIFACT,
                ).count { (scopeMask and it) != 0 }
                val scopeText = listOfNotNull(
                    if ((scopeMask and DshSync.SCOPE_SESSION) != 0) "会话" else null,
                    if ((scopeMask and DshSync.SCOPE_WORKSPACE) != 0) "工作区文件" else null,
                    if ((scopeMask and DshSync.SCOPE_ARTIFACT) != 0) "产物与轨迹" else null,
                ).joinToString(" · ").ifBlank { "未选范围" }
                DshSecTitle("目录映射", note = "2 条", icon = DshIcons.FolderOpen)
                DshCard {
                    val remoteRoot = (DshSync.share(ctx).ifBlank { "/dsh-phone" }).let {
                        if (it.endsWith("/")) it.dropLast(1) else it
                    } + "/" + remoteRootName()
                    DshMRow(
                        title = ".dsh/storages ↔ ${hostOf(display)}$remoteRoot",
                        subtitle = "$scopeText · $dirText",
                        icon = DshIcons.FolderShared,
                        iconTone = ChipTone.Brand,
                        trailing = { DshChip(dirText, tone = ChipTone.Brand, mini = true) },
                    )
                    DshDivider()
                    DshMRow(
                        title = hostOf(display) + remoteRoot,
                        subtitle = if (mountAsWs) "已作工作区来源 · $dirText"
                        else "未挂载 · 在下方表单开「挂载为工作区来源」",
                        icon = if (mountAsWs) DshIcons.Cloud else DshIcons.FolderOpen,
                        iconTone = if (mountAsWs) ChipTone.Brand else ChipTone.Neutral,
                        trailing = {
                            DshChip(
                                if (mountAsWs) "工作区" else "待挂载",
                                tone = if (mountAsWs) ChipTone.Brand else ChipTone.Neutral, mini = true,
                            )
                        },
                    )
                }

                Spacer(Modifier.height(14.dp))

                // —— 同步边界软底卡（S16 v22：措辞更新，4 条）——
                SoftCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(DshIcons.Shield, null, Modifier.size(16.dp), tint = c.brand)
                        Spacer(Modifier.width(8.dp))
                        Text("同步边界", style = Type.secTitle, color = c.ink2)
                    }
                    Spacer(Modifier.height(9.dp))
                    BoundaryItem("1", "工作区仍限定在所选目录，NAS 只作来源与备份目标")
                    BoundaryItem("2", "写向 NAS 之外的路径照旧走审批 Sheet（S7）")
                    BoundaryItem("3", if (offlineRO) "NAS 离线自动转只读，禁用写入类操作"
                    else "已关闭「离线转只读」，掉线期间会直接跳过同步")
                    BoundaryItem("4", "凭据加密保存、只写不回读，不进会话上下文")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "冲突策略：" + (if (conflict == DshSync.CF_NEWEST) "以最新为准（可能覆盖本地）"
                        else "保留两份（不丢对话）") + " · 范围 $scopeCount 项",
                        style = Type.meta, color = c.ink3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(12.dp))

                // —— 添加 NAS 连接（S16 幽灵按钮）+ footer ——
                DshBtn("添加 NAS 连接", icon = DshIcons.Add, tone = BtnTone.Ghost, onClick = onAdd)
                Spacer(Modifier.height(10.dp))
                Text(
                    "挂载由系统提供 · 需与 Harness 同一内网",
                    style = Type.meta, color = c.ink3,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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

// ============================ S52 添加 NAS 连接（表单页，照设计稿 v22 S17） ============================
/**
 * S52 添加 NAS 连接 —— 严格对齐设计稿 v22 的 **S17**。
 *
 * 连接类型支持 **WebDAV / SMB / SFTP 三种协议，且都能真实连接**：
 * 「连接并测试」会按所选协议建立真实会话（WebDAV=HttpURLConnection / SFTP=JSch /
 * SMB=jcifs-ng）并鉴权，成功后保存。
 *
 * v30.7：**取消端口、共享目录、同步方向、同步范围、冲突处理与两个开关的
 * 「暂未接入」灰显**。v22 把它们都画成可填控件，后端也已实现，所以这里是真控件：
 *  - 同步方向：双向 / 仅上传 / 仅手动 —— [DshSync.syncOnceInner] 会真的按方向跳过下载段、
 *    且「仅手动」让定时任务只探活不搬数据（见 [com.dsh.harness.DshService]）；
 *  - 同步范围：三项勾选决定哪些文件参与同步（[DshSync.inScope]）；
 *  - 冲突处理：保留两份 / 以最新为准 —— 后者不再留冲突副本；
 *  - 离线转只读：控制 NAS 断连时是否禁止写入（[DshSync.readOnlyNow]）；
 *  - 挂载为工作区来源：让 NAS 目录出现在工作区选择器里。
 *
 * 本地挂载点仍**不填**：Android 应用无法把网络文件系统挂成本地目录（需 root，
 * SAF 也不支持 SMB/NFS，见 `docs/nas-sync-feasibility.md`）。这一项如实说明，
 * 而不是做个假的路径输入框。
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
    // v30.7：S17 的五个同步选项——真控件、真保存、真生效
    var direction by remember { mutableStateOf(DshSync.direction(ctx)) }
    var scopeMask by remember { mutableStateOf(DshSync.scopeMask(ctx)) }
    var conflict by remember { mutableStateOf(DshSync.conflictPolicy(ctx)) }
    var offlineRO by remember { mutableStateOf(DshSync.offlineReadOnly(ctx)) }
    var mountAsWs by remember { mutableStateOf(DshSync.mountAsWorkspace(ctx)) }

    val typeOf: (Int) -> String = { p -> when (p) { 1 -> "smb"; 2 -> "sftp"; else -> "webdav" } }
    val defaultPort: (Int) -> String = { p -> when (p) { 1 -> "445"; 2 -> "22"; else -> "5005" } }
    // SMB 必须填共享目录才能连接；其余协议只需地址+账号
    val canTest = !busy && addr.isNotBlank() && user.isNotBlank()
        && (proto != 1 || shareDir.isNotBlank())

    val dirIndex = when (direction) {
        DshSync.DIR_UPLOAD -> 1
        DshSync.DIR_MANUAL -> 2
        else -> 0
    }
    val cfIndex = if (conflict == DshSync.CF_NEWEST) 1 else 0

    DshScreen(modifier) {
        DshNavBar(onBack = onBack, title = "添加 NAS 连接", stacked = true)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            // —— 连接类型 seg（S17，三种协议均真实可用）——
            FieldLabelRow("连接类型")
            DshSeg(listOf("WebDAV", "SMB", "SFTP"), proto) {
                proto = it
                port = defaultPort(it)
                status = null
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

            // —— 共享目录 + 本地挂载点（S17 成对）——
            FieldLabelRow("共享目录 / 本地挂载点")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NasField("共享目录", shareDir, { shareDir = it },
                    modifier = Modifier.weight(1f), mono = true,
                    placeholder = if (proto == 1) "dsh" else "/vault/dsh-sync")
                // v30.7：本地挂载点如实说明不可用，不做假输入框
                Column(Modifier.weight(1f)) {
                    Text("本地挂载点", style = Type.secTitle, color = c.ink3,
                        modifier = Modifier.padding(bottom = 7.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(Radius.lg))
                            .background(c.soft)
                            .border(1.dp, c.line, RoundedCornerShape(Radius.lg))
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(DshIcons.CloudOff, null, Modifier.size(14.dp), tint = c.ink3)
                        Spacer(Modifier.width(6.dp))
                        Text("由同步引擎代替挂载", style = Type.meta, color = c.ink3,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (proto == 1) "共享目录即远端根：同步内容落在 <共享目录>/dsh-phone/storages。"
                else "共享目录为远端基础路径：同步内容落在 <共享目录>/dsh-phone/storages。",
                style = Type.meta, color = c.ink3,
                modifier = Modifier.fillMaxWidth(),
            )

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

            Spacer(Modifier.height(16.dp))

            // —— 同步方向（v30.7：真实生效，保存时写入 DshSync.direction）——
            FieldLabelRow("同步方向")
            DshSeg(listOf("双向", "仅上传", "仅手动"), dirIndex) {
                direction = when (it) {
                    1 -> DshSync.DIR_UPLOAD
                    2 -> DshSync.DIR_MANUAL
                    else -> DshSync.DIR_BOTH
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                when (dirIndex) {
                    1 -> "仅上传：只把本机变化推上去，NAS 上的改动不会覆盖本地"
                    2 -> "仅手动：定时任务只探活不搬数据，需要时点「立即同步」"
                    else -> "双向：本机与 NAS 互相合并，两边改过同一文件按下方策略处理"
                },
                style = Type.meta, color = c.ink3,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(14.dp))

            // —— 同步范围（v30.7：三项可勾，真实过滤同步文件）——
            FieldLabelRow("同步范围") {
                Text(
                    "${listOf(
                        DshSync.SCOPE_SESSION, DshSync.SCOPE_WORKSPACE, DshSync.SCOPE_ARTIFACT,
                    ).count { (scopeMask and it) != 0 }} 项",
                    style = Type.meta, color = c.ink3,
                )
            }
            DshCard {
                ScopeCheckRow("会话", "会话索引与记录（session*）", DshSync.SCOPE_SESSION, scopeMask) {
                    scopeMask = scopeMask xor DshSync.SCOPE_SESSION
                }
                DshDivider()
                ScopeCheckRow("工作区文件", "工作区配置与状态（workspace*）", DshSync.SCOPE_WORKSPACE, scopeMask) {
                    scopeMask = scopeMask xor DshSync.SCOPE_WORKSPACE
                }
                DshDivider()
                ScopeCheckRow("产物与轨迹", "产物索引与执行轨迹（其余 json）", DshSync.SCOPE_ARTIFACT, scopeMask) {
                    scopeMask = scopeMask xor DshSync.SCOPE_ARTIFACT
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "不同步 .dsh/profiles（node_modules 等平台相关文件，整目录同步会互相破坏环境）",
                style = Type.meta, color = c.ink3,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(14.dp))

            // —— 冲突处理（v30.7：真实分支）——
            FieldLabelRow("冲突处理")
            DshSeg(listOf("保留两份", "以最新为准"), cfIndex) {
                conflict = if (it == 1) DshSync.CF_NEWEST else DshSync.CF_KEEP
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (cfIndex == 1) "以最新为准：直接采信 NAS 版本，本地改动不留副本（可能覆盖）"
                else "保留两份：本地另存 .conflict-设备-时间戳，再采信远端（不丢对话）",
                style = Type.meta, color = c.ink3,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(14.dp))

            // —— 两个开关（v30.7：真实生效）——
            DshCard {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("离线时转只读", style = Type.rowTitle, color = c.ink)
                        Text("避免任务写到半空目录", style = Type.sec, color = c.ink3)
                    }
                    DshSwitch(checked = offlineRO, onChange = { offlineRO = it })
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
                    DshSwitch(checked = mountAsWs, onChange = { mountAsWs = it })
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
                            val t = typeOf(proto)
                            // v30.6：testConnectionEx 返回具体失败原因，不再吞成一句"失败"
                            val result = DshSync.testConnectionEx(
                                ctx, t, addr, port, shareDir, user, if (pass.isBlank()) null else pass)
                            if (result == "ok") {
                                // v30.7：S17 的五个选项随连接一起收口保存
                                DshSync.configure(
                                    ctx, t, addr, port, shareDir, user,
                                    if (pass.isBlank()) null else pass, 60,
                                    direction, scopeMask, conflict, offlineRO, mountAsWs)
                                status = buildString {
                                    append("连接成功，已保存 NAS 连接（")
                                    append(t.uppercase(Locale.getDefault()))
                                    append("）")
                                    if (dirIndex != 0) append(" · ${if (dirIndex == 1) "仅上传" else "仅手动"}")
                                    if (cfIndex == 1) append(" · 以最新为准")
                                    if (mountAsWs) append(" · 已挂为工作区来源")
                                }
                                statusOk = true
                                onSaved()
                            } else {
                                status = "连接失败：$result"
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

/** 排障编号项（图二：编号 + 标题：说明）。 */
@Composable
private fun TroubleshootItem(n: String, title: String, desc: String) {
    val c = DshTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(n, style = Type.rowTitle, color = c.brand, modifier = Modifier.width(16.dp))
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    append(title + "：")
                    withStyle(SpanStyle(color = c.ink3)) { append(desc) }
                },
                style = Type.sec, color = c.ink,
            )
        }
    }
}

/**
 * 同步范围勾选行（v30.7 S17：三项真勾选，bit 位掩码）。
 *
 * 用方框 + 对勾而不是 [DshSwitch]——设计稿里这是「勾选列表项」不是「设置开关」，
 * 且三项同卡时开关会让人误以为能整组关掉。
 */
@Composable
private fun ScopeCheckRow(
    title: String,
    desc: String,
    bit: Int,
    mask: Int,
    onToggle: () -> Unit,
) {
    val c = DshTheme.colors
    val on = (mask and bit) != 0
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 9.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 勾选框：外框 18dp，勾用文字 tick（避免再引一个矢量图标）
        Box(
            Modifier.size(18.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (on) c.brand else androidx.compose.ui.graphics.Color.Transparent)
                .border(1.5.dp, if (on) c.brand else c.line, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (on) {
                Text("✓", style = Type.meta.copy(fontSize = 11.sp), color = c.bg)
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.rowTitle, color = c.ink)
            Text(desc, style = Type.sec, color = c.ink3)
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

// ============================ 私有工具 ============================

/** 从连接串取主机标识（WebDAV/SMB/SFTP 通用）。 */
private fun hostOf(u: String): String {
    var s = u.trim().replaceFirst("(?i)^(https?|smb|sftp)://".toRegex(), "")
    val slash = s.indexOf('/')
    if (slash >= 0) s = s.substring(0, slash)
    val at = s.lastIndexOf('@')
    if (at >= 0) s = s.substring(at + 1)
    return s.ifBlank { "NAS" }
}

/**
 * 远端同步根的末段（`dsh-phone/storages` → `storages`）。
 *
 * 目录映射标题要显示"远端落在哪"，但 [com.dsh.harness.DshSync.REMOTE_ROOT] 是
 * 包内可见的，Compose 侧读不到，所以这里按同样的规则拼末段。
 */
private fun remoteRootName(): String {
    // 与 DshSync.REMOTE_ROOT = "dsh-phone/storages" 保持一致
    return "dsh-phone/storages".substringAfterLast('/')
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
