package com.dsh.harness.mobile.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import com.dsh.harness.mobile.data.DshProviders
import com.dsh.harness.mobile.ui.components.*
import com.dsh.harness.mobile.ui.theme.*

/**
 * S1 首次引导 · 用途选择（多选）
 *
 * 设计稿布局（S3 布局骨架表原文）：
 *   状态栏 5% ｜ 顶栏(返回+步骤) 6% ｜ 品牌区 24%(图标58 + 标题 + 副文)
 *   ｜ 选择题 42%(题干9% + 3 张 pick 卡各11%) ｜ 留白 13% ｜ 进度点 2% ｜ CTA 8% ｜ home 3%
 *   滚动策略："整页可竖滚；CTA 吸底不跟随滚动"
 */
@Composable
fun S1OnboardingPurpose(
    onBack: () -> Unit,
    onNext: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
    initial: Set<Int> = setOf(0, 1),
) {
    val c = DshTheme.colors
    var picked by remember { mutableStateOf(initial) }

    val options = listOf(
        Triple("办公与创作", "编辑文档、整理数据、分析表格、制作演示文稿", DshIcons.Description),
        Triple("代码与开发", "修改代码、调试问题、运行命令、管理项目文件", DshIcons.Code),
        Triple("构建自己的 Harness", "编写插件、自定义 Agent 预设（进阶）", DshIcons.Extension),
    )

    DshScreen(modifier) {
        DshNavBar(
            leading = {
                DshIBtn(DshIcons.ArrowBack, "返回", onClick = onBack)
                Text(
                    "设置向导",
                    style = Type.rowTitle,
                    color = c.ink2,
                    modifier = Modifier.padding(start = 2.dp),
                )
            },
            actions = {
                Text("1 / 2", style = Type.sec, color = c.ink3)
            },
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // —— 品牌区：58×58 r18 渐变方块 + 标题 + 副文 ——
            Spacer(Modifier.height(22.dp))
            Box(
                Modifier
                    .size(58.dp)
                    .background(
                        Brush.linearGradient(listOf(c.brand, c.purple)),
                        RoundedCornerShape(18.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(DshIcons.Bolt, null, Modifier.size(30.dp), tint = Color.White)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "现在，开箱即用",
                style = Type.h1,
                color = c.ink,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "下载安装即开始，不用先过终端\n网页版与桌面端同源，界面一致",
                style = Type.chip,
                color = c.ink3,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(26.dp))

            // —— 题干 ——
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "你希望 DeepSeek Harness\n帮你做什么？",
                    style = Type.title,
                    color = c.ink,
                    lineHeight = 23.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "我们会根据你的选择调整界面和工具，以更适合你的工作方式。可同时勾选。",
                    style = Type.sec,
                    color = c.ink3,
                    lineHeight = 18.6.sp,
                )
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    options.forEachIndexed { i, (title, desc, icon) ->
                        DshPick(
                            title = title,
                            desc = desc,
                            icon = icon,
                            selected = picked.contains(i),
                        ) {
                            picked = if (picked.contains(i)) picked - i else picked + i
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // —— 吸底：进度点 + CTA（不随内容滚动）——
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg)
                .padding(bottom = 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(16.dp)
                        .height(5.dp)
                        .background(c.brand, RoundedCornerShape(3.dp)),
                )
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier
                        .size(5.dp)
                        .background(c.line, RoundedCornerShape(3.dp)),
                )
            }
            Spacer(Modifier.height(12.dp))
            DshBtn(
                text = "继续",
                enabled = picked.isNotEmpty(),
                onClick = { onNext(picked) },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * S2 首次引导 · 工作过程密度（单选）
 *
 * 布局（设计稿骨架表）：
 *   状态栏 5% ｜ 顶栏 6% ｜ 图标块 9% ｜ 题干 6% ｜ 三档单选 38% ｜ 提示卡 12%
 *   ｜ 留白 14% ｜ 跳过+进入 8% ｜ home 3%
 *   "单选组内部不换行，卡片高度固定 76px"
 */
@Composable
fun S2OnboardingDensity(
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onEnter: (Int) -> Unit,
    modifier: Modifier = Modifier,
    initial: Int = 1,
) {
    val c = DshTheme.colors
    var sel by remember { mutableStateOf(initial) }

    val options = listOf(
        "聚焦结果" to "只看结果，保持界面简洁明了",
        "关键细节" to "结果优先，仅展示重要步骤和关键操作",
        "完整过程" to "查看完整工作过程，方便调试和排查问题",
    )
    val icons = listOf(DshIcons.FilterNone, DshIcons.Tune, DshIcons.Subject)

    DshScreen(modifier) {
        DshNavBar(
            leading = {
                DshIBtn(DshIcons.ArrowBack, "返回", onClick = onBack)
                Text(
                    "设置向导",
                    style = Type.rowTitle,
                    color = c.ink2,
                    modifier = Modifier.padding(start = 2.dp),
                )
            },
            actions = { Text("2 / 2", style = Type.sec, color = c.ink3) },
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            // —— 图标块 46×46 r14 soft 底 brand 字 ——
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .size(46.dp)
                    .background(c.soft, RoundedCornerShape(Radius.xl)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(DshIcons.Tune, null, Modifier.size(24.dp), tint = c.brand)
            }
            Spacer(Modifier.height(16.dp))

            Text(
                "你希望看到多少工作过程？",
                style = Type.h1.copy(fontSize = 19.sp, letterSpacing = (-0.3).sp),
                color = c.ink,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "这只会影响工作过程的展示方式，\n不会影响 DeepSeek Harness 的工作能力。",
                style = Type.sec,
                color = c.ink3,
                lineHeight = 18.6.sp,
            )
            Spacer(Modifier.height(18.dp))

            // —— 三档单选，卡片高度固定 76（设计稿明确不允许撑高）——
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                options.forEachIndexed { i, (title, desc) ->
                    Box(Modifier.height(76.dp)) {
                        DshPick(
                            title = title,
                            desc = desc,
                            icon = icons[i],
                            selected = sel == i,
                            modifier = Modifier.fillMaxHeight(),
                            titleTrailing = if (i == 1) {
                                {
                                    DshChip("推荐", tone = ChipTone.Brand, mini = true)
                                }
                            } else null,
                        ) { sel = i }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            // —— 提示卡：soft 底 + 透明描边 ——
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(c.soft, RoundedCornerShape(Radius.xl))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Icon(
                    DshIcons.Info, null,
                    Modifier.size(16.dp).padding(top = 1.dp),
                    tint = c.brand,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "后续可在 设置 → 通用 → 工作过程展示 修改；「完整过程」会同时开放轨迹（Trajectory）入口。",
                    style = Type.meta.copy(lineHeight = 18.4.sp),
                    color = c.ink2,
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        // —— 吸底：跳过（定宽 104）+ 进入应用（flex1）——
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DshBtn(
                text = "跳过",
                tone = BtnTone.Ghost,
                modifier = Modifier.width(104.dp),
                onClick = onSkip,
            )
            DshBtn(
                text = "进入应用",
                icon = DshIcons.ArrowForward,
                modifier = Modifier.weight(1f),
                onClick = { onEnter(sel) },
            )
        }
    }
}

// ============================ S24 首次配置 · API 密钥 ============================
/**
 * v13 §01 的第一屏：对齐 Web 端首次访问那一屏。
 * 一个输入框 + 一行格式校验，「继续」在校验不过时置灰；次选走 DeepSeek 账号余额，免申请 Key。
 *
 * 真实存储走 [DshCredentials]（与 S14 设置页同一套），写入即被 dsh 凭据 watcher 热重载。
 * 密钥只写不回读：已进入本屏说明还没配过，所以这里就是纯输入态，没有"显示已有值"。
 */
@Composable
fun S24OnboardingKey(
    modifier: Modifier = Modifier,
    onNext: () -> Unit,
    onUseBalance: () -> Unit = {},
    onAddProvider: () -> Unit = {},
    onToggleDark: () -> Unit = {},
    onCopyPlatform: () -> Unit = {},
    state: com.dsh.harness.mobile.data.DshCredentials.KeyState =
        com.dsh.harness.mobile.data.DshCredentials.KeyState(),
    onSave: (String) -> Unit = {},
) {
    val c = DshTheme.colors
    var input by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    val valid = input.trim().length >= 8

    DshScreen(modifier) {
        // —— 品牌栏：字标 + HARNESS 标签 + 暗色切换 ——
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = Space.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .background(
                        Brush.linearGradient(listOf(c.brand, c.purple)),
                        RoundedCornerShape(7.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) { Icon(DshIcons.Bolt, null, Modifier.size(13.dp), tint = Color.White) }
            Text("deepseek", style = Type.title.copy(fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = c.ink)
            DshChip("HARNESS", tone = ChipTone.Brand, mini = true, modifier = Modifier.padding(start = 1.dp))
            Spacer(Modifier.weight(1f))
            DshIBtn(DshIcons.DarkMode, "切换深色 / 浅色", onClick = onToggleDark)
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            Spacer(Modifier.height(30.dp))
            Box(
                Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(Brush.linearGradient(listOf(c.brand, c.purple))),
                contentAlignment = Alignment.Center,
            ) { Icon(DshIcons.VpnKey, null, Modifier.size(27.dp), tint = Color.White) }
            Spacer(Modifier.height(15.dp))
            Text("配置 API 密钥", style = Type.h1.copy(fontSize = 20.sp), color = c.ink)
            Spacer(Modifier.height(9.dp))
            Text(
                "首次访问 DeepSeek Harness 需要你配置一个 DeepSeek 的 API Key。" +
                    "还没有的话，先去 API 开放平台申请。",
                style = Type.sec,
                color = c.ink3,
                lineHeight = 21.sp,
            )

            // —— 开放平台链接卡（可复制）——
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .clickable { onCopyPlatform() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(DshIcons.OpenInNew, null, Modifier.size(16.dp), tint = c.brand)
                Text(
                    "platform.deepseek.com",
                    style = Type.mono.copy(fontSize = 12.5.sp),
                    color = c.ink2,
                    modifier = Modifier.weight(1f),
                )
                Text("去申请", style = Type.meta.copy(fontSize = 11.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = c.brand)
            }

            // —— API Key 输入 ——
            Spacer(Modifier.height(16.dp))
            Text("API Key", style = Type.secTitle, color = c.ink3, modifier = Modifier.padding(bottom = 7.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(Radius.lg))
                    .background(c.card)
                    .border(1.dp, if (input.isNotEmpty()) c.brand else c.line, RoundedCornerShape(Radius.lg))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.merge(
                        TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = c.ink, letterSpacing = 1.sp),
                    ),
                    cursorBrush = SolidColor(c.brand),
                    visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                )
                Spacer(Modifier.width(6.dp))
                DshIBtn(DshIcons.VisibilityOff, "明文仅本次输入可见", size = 22.dp, onClick = { reveal = !reveal })
                DshIBtn(DshIcons.ContentPaste, "粘贴", size = 22.dp, onClick = {
                    clipboard.getText()?.toString()?.let { input = it.trim() }
                })
            }
            Spacer(Modifier.height(7.dp))
            AnimatedVisibility(visible = valid) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(DshIcons.CheckCircle, null, Modifier.size(13.dp), tint = c.ok)
                    Spacer(Modifier.width(5.dp))
                    Text("格式校验通过", style = Type.meta.copy(fontSize = 11.sp), color = c.ok)
                }
            }

            // —— 继续 ——
            Spacer(Modifier.height(16.dp))
            DshBtn(
                text = if (state.configured) "已写入 · 继续" else "继续",
                enabled = valid,
                onClick = {
                    if (valid) {
                        onSave(input.trim())
                        onNext()
                    }
                },
            )

            // —— 或 ——
            Row(
                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).height(1.dp).background(c.line))
                Text("或", style = Type.meta, color = c.ink3, modifier = Modifier.padding(horizontal = 10.dp))
                Box(Modifier.weight(1f).height(1.dp).background(c.line))
            }

            // —— 改用账号余额 ——
            DshBtn(
                text = "改用 DeepSeek 账号余额",
                icon = DshIcons.AccountBalanceWallet,
                tone = BtnTone.Ghost,
                onClick = onUseBalance,
            )

            Spacer(Modifier.height(14.dp))
            // —— 接入其他模型提供方 ——
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .clickable { onAddProvider() }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(DshIcons.Tune, null, Modifier.size(16.dp), tint = c.ink2)
                Column(Modifier.weight(1f)) {
                    Text("接入其他模型提供方", style = Type.rowTitle, color = c.ink)
                    Spacer(Modifier.height(2.dp))
                    Text("OpenAI 兼容端点、Anthropic 协议等", style = Type.meta, color = c.ink3)
                }
                Icon(DshIcons.ChevronRight, null, Modifier.size(16.dp), tint = c.ink3)
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "密钥只写不回读；保存后如需修改必须重新填写，界面不会显示已有值。",
                style = Type.meta,
                color = c.ink3,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

// ============================ S25 添加自定义提供方 ============================
/**
 * v13 §01 的可选屏：字段与按钮沿用 Web 端 设置 → 模型 → 添加自定义提供方。
 * 真实存储写本地 `.dsh/providers.json`（[DshProviders]），录入即落盘、可回显；
 * 托 dsh 侧 provider 装配 API 打通前的过渡方案，UI 与校验已对齐官方。
 */
@Composable
fun S25Provider(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onCancel: () -> Unit = {},
    onCreated: (String) -> Unit = {},
    /** v20：「添加模型」→ S43 添加模型与推理等级 */
    onAddModel: () -> Unit = {},
) {
    val c = DshTheme.colors
    val ctx = androidx.compose.ui.platform.LocalContext.current.applicationContext
    var pid by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf("openai-completions") }
    var key by remember { mutableStateOf("") }
    var models by remember { mutableStateOf(listOf<String>()) }
    var modelInput by remember { mutableStateOf("") }

    val protoOptions = listOf("openai-completions", "openai-responses", "anthropic-messages")
    val pidOk = pid.matches(Regex("^[a-z][a-z0-9]*$"))
    val canCreate = pidOk && name.isNotBlank() && url.isNotBlank() && key.isNotBlank()

    // v30.9：两处空回调接真实现 ———
    //  「打开配置文件」= 复制本机真实 providers.json 路径到剪贴板；
    //  「获取可用模型」= 按 OpenAI 兼容协议 GET {base}/models 带 Bearer key 拉真实列表。
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    fun notice(msg: String) =
        android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var fetching by remember { mutableStateOf(false) }
    var fetchErr by remember { mutableStateOf<String?>(null) }

    DshScreen(modifier) {
        DshNavBar(
            onBack = onBack,
            title = "模型",
            stacked = true,
            actions = {
                DshChip(
                    text = "打开配置文件",
                    icon = DshIcons.Description,
                    mini = true,
                    onClick = {
                        val f = com.dsh.harness.mobile.data.DshProviders.providersFile(ctx)
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(f.absolutePath))
                        notice("已复制路径：${f.absolutePath}")
                    },
                )
            },
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg)
                .padding(top = 13.dp),
        ) {
            Text("填入各提供方的 API 密钥即可使用其模型。", style = Type.meta, color = c.ink3, lineHeight = 18.sp)
            Spacer(Modifier.height(12.dp))

            S25Field("Provider ID", value = pid, mono = true, onChange = { pid = it }) {
                if (pid.isNotEmpty() && !pidOk) {
                    Text("以小写字母开头，仅字母数字；在请求中唯一标识该提供方。", style = Type.meta.copy(fontSize = 10.5.sp), color = c.warn, lineHeight = 15.sp)
                }
            }
            S25Field("显示名称", value = name, onChange = { name = it })
            S25Field("API 地址", value = url, mono = true, onChange = { url = it })

            // —— 协议（下拉 chips）——
            Text("API 协议", style = Type.secTitle, color = c.ink3, modifier = Modifier.padding(bottom = 7.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                protoOptions.forEach { p ->
                    DshChip(p, tone = if (protocol == p) ChipTone.Brand else ChipTone.Neutral, mini = true, onClick = { protocol = p })
                }
            }

            Spacer(Modifier.height(12.dp))
            S25Field("API 密钥", value = key, mono = true, pwd = true, onChange = { key = it })

            // —— 模型目录 ——
            Spacer(Modifier.height(12.dp))
            Text("模型目录", style = Type.secTitle, color = c.ink3, modifier = Modifier.padding(bottom = 7.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.xl)).background(c.card).border(1.dp, c.line, RoundedCornerShape(Radius.xl)).padding(11.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("已添加 ${models.size} 个", style = Type.meta, color = c.ink2)
                        if (models.isNotEmpty()) {
                            Spacer(Modifier.height(3.dp))
                            Text(models.joinToString(" / "), style = Type.meta.copy(fontSize = 11.5.sp), color = c.ink3, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    DshBtn(
                        text = if (fetching) "拉取中…" else "获取可用模型",
                        small = true,
                        tone = BtnTone.Ghost,
                        enabled = !fetching && url.isNotBlank() && key.isNotBlank(),
                        modifier = Modifier.padding(start = 8.dp),
                        onClick = {
                            fetchErr = null
                            fetching = true
                            scope.launch {
                                val base = url.trim().trimEnd('/')
                                val endpoint = if (base.endsWith("/v1")) "$base/models" else "$base/v1/models"
                                val r = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                                            requestMethod = "GET"
                                            connectTimeout = 12000
                                            readTimeout = 15000
                                            setRequestProperty("Authorization", "Bearer ${key.trim()}")
                                            setRequestProperty("Accept", "application/json")
                                        }
                                        try {
                                            val code = conn.responseCode
                                            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                                                ?.bufferedReader()?.use { it.readText() } ?: ""
                                            code to body
                                        } finally {
                                            conn.disconnect()
                                        }
                                    }
                                }
                                fetching = false
                                r.onSuccess { (code, body) ->
                                    if (code !in 200..299) {
                                        fetchErr = "HTTP $code · " + body.take(120)
                                        notice("拉取失败：HTTP $code")
                                    } else {
                                        val ids = runCatching {
                                            val arr = JSONObject(body).optJSONArray("data")
                                            (0 until (arr?.length() ?: 0)).mapNotNull { i ->
                                                arr!!.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }
                                            }
                                        }.getOrDefault(emptyList())
                                        if (ids.isEmpty()) {
                                            fetchErr = "返回体里没有 data[].id（不是 OpenAI 兼容格式）"
                                            notice("未解析到模型列表")
                                        } else {
                                            models = (models + ids).distinct().sorted()
                                            notice("已拉到 ${ids.size} 个模型")
                                        }
                                    }
                                }.onFailure { e ->
                                    fetchErr = (e.message ?: e.javaClass.simpleName).take(140)
                                    notice("拉取失败")
                                }
                            }
                        },
                    )
                }
            }
            if (fetchErr != null) {
                Spacer(Modifier.height(7.dp))
                Text(
                    "拉取失败：$fetchErr",
                    style = Type.meta.copy(fontSize = 10.5.sp, lineHeight = 16.sp),
                    color = c.danger,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.lg)).background(c.card).border(1.dp, c.line, RoundedCornerShape(Radius.lg)).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = modelInput,
                    onValueChange = { modelInput = it },
                    modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.merge(TextStyle(fontSize = 13.sp, color = c.ink)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                DshIBtn(DshIcons.Add, "添加模型", size = 22.dp, onClick = {
                    val m = modelInput.trim()
                    if (m.isNotEmpty() && !models.contains(m)) { models = models + m; modelInput = "" }
                })
            }

            Spacer(Modifier.height(13.dp))
            // 设计稿 S25：「添加模型」ghost h40 → S43（声明档位映射后模型菜单才有思考选项）
            DshBtn(
                text = "添加模型",
                icon = DshIcons.Add,
                tone = BtnTone.Ghost,
                height = 40.dp,
                onClick = onAddModel,
            )

            Spacer(Modifier.height(13.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DshBtn(text = "取消", tone = BtnTone.Ghost, modifier = Modifier.width(100.dp), onClick = onCancel)
                DshBtn(
                    text = "创建提供方",
                    enabled = canCreate,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        DshProviders.add(ctx, com.dsh.harness.mobile.data.DshProviders.Provider(
                            id = pid, name = name, apiUrl = url, protocol = protocol, apiKey = key, models = models,
                        ))
                        onCreated(pid)
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun S25Field(
    label: String,
    value: String,
    mono: Boolean = false,
    pwd: Boolean = false,
    onChange: (String) -> Unit,
    note: @Composable (() -> Unit)? = null,
) {
    val c = DshTheme.colors
    Text(label, style = Type.secTitle, color = c.ink3, modifier = Modifier.padding(bottom = 7.dp, top = 12.dp))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.lg)).background(c.card).border(1.dp, c.line, RoundedCornerShape(Radius.lg)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f).padding(vertical = 11.dp),
            singleLine = true,
            textStyle = LocalTextStyle.current.merge(
                TextStyle(fontSize = 13.sp, color = c.ink, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default),
            ),
            cursorBrush = SolidColor(c.brand),
            visualTransformation = if (pwd && value.isNotEmpty()) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (pwd) KeyboardType.Password else KeyboardType.Text, imeAction = ImeAction.Done),
        )
    }
    if (note != null) Spacer(Modifier.height(5.dp))
    note?.invoke()
}