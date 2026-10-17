package com.dsh.harness.mobile.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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