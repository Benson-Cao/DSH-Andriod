package com.dsh.harness.mobile.data

import android.content.Context
import android.util.Log
import java.io.File

/**
 * dsh-market 市场本体的探测。
 *
 * ## 事实（本机 2026-10-18 实测）
 * `ls $HOME/node_modules/@deepseek-ai/` 共 200+ 个包，**没有 dshmarket** ——
 * 也就是说设计稿 S21 说的"需先 `dsh plugin --profile web add dshmarket`
 * 把市场本身装进去"，在这台设备上确实还没发生。
 *
 * 所以市场三屏（S21/S22/S23）在未安装前**没有真实数据源**。
 * 与其拿设计稿里的示意插件名（dsh-web-ui-all 4.2k 星之类）冒充真实条目，
 * 不如如实呈现"未安装"并给出安装命令 —— 这也是设计稿自己的规则。
 *
 * 探测方式刻意保持最简单：只看包目录在不在。
 * 不去执行 `dsh plugin list`（那是 CLI 且要另起进程），
 * 也不猜索引源格式 —— 索引源本身还没有内容可解析。
 */
object DshMarketProbe {

    private const val TAG = "DshMarketProbe"

    /** 市场本体包名。dsh 的第三方插件统一装在 $HOME/node_modules 下。 */
    private const val MARKET_PKG = "dshmarket"

    /** 本机实测市场包目录名（若将来装上，靠它兜底匹配） */
    private val CANDIDATES = listOf("dshmarket", "dsh-market", "dsh-plugin-dshmarket")

    fun isInstalled(ctx: Context): Boolean = probe(ctx) != null

    /** 返回命中的包名；未安装为 null */
    fun probe(ctx: Context): String? = try {
        val nm = File(DshBootstrapAccess.homeDir(ctx), "node_modules/@deepseek-ai")
        val hit = if (nm.isDirectory) {
            nm.list()?.firstOrNull { dir -> CANDIDATES.any { dir == it || dir.contains(it, ignoreCase = true) } }
        } else null
        Log.i(TAG, "市场本体探测：${hit ?: "未安装"}（扫 ${nm.absolutePath}）")
        hit
    } catch (t: Throwable) {
        // 探测本身失败不等于"未安装"，但也不能让 UI 崩；当作未装并留日志
        Log.e(TAG, "市场本体探测失败", t)
        null
    }

    /** 供 UI 直接展示的安装命令。与设计稿一致，只把 profile 换成 acp（App 走的是 acp profile）。 */
    const val INSTALL_CMD = "dsh plugin --profile acp add dshmarket"
}