package com.dsh.harness.mobile.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.harness.mobile.data.DshCredentials
import com.dsh.harness.mobile.ui.components.*
import com.dsh.harness.mobile.ui.theme.*

/**
 * API 密钥录入页。
 *
 * ## 对应的真实存储（不是自己造一套）
 * dsh 的 `dsh-credentials-local` 存在 `$DSH_HOME/.credentials.yaml`，
 * API key 走 `refs:` 分节、按**环境变量名**索引。deepseek-official 这条
 * provider route 读的名字是 `DEEPSEEK_API_KEY`
 * （来自 `dsh-llm-deepseek/lib/index.js:1838 DEFAULT_API_KEY_ENV`）。
 * 写入由 [DshCredentials] 完成，文件被 dsh 的 watcher 热重载 → **保存即生效，无需重启**。
 *
 * ## 设计稿定的规则（v11 §07 与 S17 一致）
 * > "密钥只写不回读，编辑时需重新填写"
 *
 * dsh 侧的 `describe` 也**永不返回值**。所以本页：
 *   - 已配置态只显示 `••••` + 末 4 位，不做"查看明文"；
 *   - 点"修改"进入的是**重新输入**，不是"载入旧值再编辑"；
 *   - 提供独立的"删除密钥"，因为文档说移除 = 删除该行，置空会被拒绝。
 *
 * ## 为什么不给"测试连接"假按钮
 * 真正验证密钥有效性要发一次真实模型请求（花钱、慢）。本页只承诺
 * "已写入 credentials 文件"，有效性由下一次对话的结果说话 ——
 * 顶栏引擎诊断会把 `Your api key: ****xxxx is invalid` 这类真实报错透出来。
 */
@Composable
fun S19ApiKey(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    state: DshCredentials.KeyState = DshCredentials.KeyState(),
    /** 写文件的结果；null 表示还没写 */
    savedState: DshCredentials.KeyState? = null,
    onSave: (String) -> Unit = {},
    onRemove: () -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    val c = DshTheme.colors
    var input by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var confirmingRemove by remember { mutableStateOf(false) }

    // 进入页面拉一次真实状态（只读存在性，不回读值）
    LaunchedEffect(Unit) { onRefresh() }

    val effective = savedState ?: state
    val busy = savedState != null && savedState.error == null && savedState.configured

    DshScreen(modifier) {
        DshNavBar(onBack = onBack, title = "API 密钥", stacked = true)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg)
                .padding(top = 12.dp),
        ) {
            // —— 状态头卡 ——
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(if (effective.ok) c.okSoft() else c.card)
                    .border(
                        1.dp,
                        if (effective.ok) c.ok.copy(alpha = 0.3f) else c.line,
                        RoundedCornerShape(Radius.xl),
                    )
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(9.dp).background(
                            if (effective.ok) c.ok else c.warn,
                            CircleShape,
                        )
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        when {
                            effective.error != null -> "读取失败"
                            effective.ok -> "已配置"
                            effective.fileMissing -> "尚未配置"
                            else -> "未配置"
                        },
                        style = Type.title,
                        color = c.ink,
                    )
                    Spacer(Modifier.weight(1f))
                    if (effective.ok) {
                        DshChip(effective.maskedHint, tone = ChipTone.Ok, mini = true)
                    } else {
                        DshChip("待填", tone = ChipTone.Warn, mini = true)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        effective.error != null -> effective.error
                        effective.ok -> "密钥保存在 dsh 的凭据存储中，只写不回读。"
                        effective.fileMissing -> "还没有凭据文件，保存时会自动创建。"
                        else -> "填入 DeepSeek API Key 后才能发起模型请求。"
                    },
                    style = Type.sec,
                    color = c.ink2,
                    lineHeight = 18.sp,
                )
            }

            Spacer(Modifier.height(12.dp))

            // —— 输入区 ——
            Column(Modifier.fillMaxWidth()) {
                Text("密钥", style = Type.secTitle, color = c.ink3, modifier = Modifier.padding(bottom = 7.dp))
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
                            TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = c.ink,
                            )
                        ),
                        cursorBrush = SolidColor(c.brand),
                        visualTransformation = if (reveal) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    )
                    if (input.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        DshIBtn(
                            icon = DshIcons.Close,
                            contentDescription = "清空",
                            size = 22.dp,
                            onClick = { input = "" },
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    DshIBtn(
                        icon = if (reveal) DshIcons.VisibilityOff else DshIcons.Visibility,
                        contentDescription = if (reveal) "隐藏" else "显示",
                        size = 22.dp,
                        onClick = { reveal = !reveal },
                    )
                }
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(DshIcons.Lock, null, Modifier.size(12.dp), tint = c.ink3)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "明文仅本次输入可见，保存后不再回读",
                        style = Type.meta,
                        color = c.ink3,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // —— 存入位置 ——
            Text("存入位置", style = Type.secTitle, color = c.ink3, modifier = Modifier.padding(bottom = 7.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(Radius.xl))
                    .padding(13.dp),
            ) {
                KV("提供方", DshCredentials.DEEPSEEK_ROUTE)
                Spacer(Modifier.height(7.dp))
                KV("环境变量名", DshCredentials.DEEPSEEK_KEY)
                Spacer(Modifier.height(7.dp))
                KV("文件", ".dsh/.credentials.yaml  ·  refs 分节")
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(DshIcons.Info, null, Modifier.size(12.dp).padding(top = 1.dp), tint = c.ink3)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "保存在 dsh 自己的凭据存储里，而不是 App 私有偏好 —— " +
                            "这样网页端、命令行与 App 读到的是同一份密钥。",
                        style = Type.meta,
                        color = c.ink3,
                        lineHeight = 17.sp,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // —— 生效说明 ——
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(c.soft)
                    .padding(13.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(DshIcons.Bolt, null, Modifier.size(15.dp), tint = c.brand)
                    Spacer(Modifier.width(7.dp))
                    Text("保存后立即生效", style = Type.rowTitle, color = c.ink)
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    "dsh 的凭据文件有热重载（100ms 去抖），写完下一次请求就用新密钥，" +
                        "不需要重启引擎或 App。",
                    style = Type.sec,
                    color = c.ink2,
                    lineHeight = 18.sp,
                )
            }

            Spacer(Modifier.height(14.dp))

            // —— 操作区 ——
            if (effective.ok) {
                // 已配置态：不给"保存"，只给"重新输入"与"删除"
                if (input.isNotEmpty()) {
                    DshBtn("保存新密钥", enabled = input.trim().length >= 8, onClick = { onSave(input) })
                    Spacer(Modifier.height(9.dp))
                }
                DshBtn(
                    text = if (confirmingRemove) "确认删除密钥" else "删除密钥",
                    icon = DshIcons.DeleteOutline,
                    tone = BtnTone.Ghost,
                    onClick = {
                        if (confirmingRemove) {
                            confirmingRemove = false
                            input = ""
                            onRemove()
                        } else {
                            confirmingRemove = true
                        }
                    },
                )
                if (confirmingRemove) {
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "再点一次确认。删除后模型请求会回到「未配置密钥」状态。",
                        style = Type.meta,
                        color = c.danger,
                    )
                }
            } else {
                DshBtn(
                    text = if (busy) "保存中…" else "保存密钥",
                    enabled = input.trim().length >= 8 && !busy,
                    onClick = { onSave(input) },
                )
                if (input.isNotEmpty() && input.trim().length < 8) {
                    Spacer(Modifier.height(7.dp))
                    Text("密钥看起来太短，请检查是否复制完整", style = Type.meta, color = c.warn)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 设置页里的「模型 · 密钥」行用的小键值对。 */
@Composable
private fun KV(k: String, v: String) {
    val c = DshTheme.colors
    Row(verticalAlignment = Alignment.Top) {
        Text(k, style = Type.sec, color = c.ink3, modifier = Modifier.width(76.dp))
        Text(
            v,
            style = Type.mono,
            color = c.ink2,
            modifier = Modifier.weight(1f),
            overflow = TextOverflow.Ellipsis,
        )
    }
}