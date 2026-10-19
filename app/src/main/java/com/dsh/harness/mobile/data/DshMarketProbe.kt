package com.dsh.harness.mobile.data

import android.content.Context
import android.util.Log
import java.io.File

/**
 * dsh-market 市场本体的探测。
 *
 * ## 事实（2026-10-19 修正）
 * `dsh plugin add` 只是 pnpm 转发器：在 `.dsh/profiles/<name>/` 下跑
 * `pnpm add <pkg>`，包落在**该 profile 目录的 node_modules**里
 * （无 scope，包名就是 `dshmarket`，npm 上 1.66.42 真实存在）。
 * 此前探测 `node_modules/@deepseek-ai/dshmarket` 是错的 ——
 * 那是 dsh 自带的 scope 包位置，市场本体从来不在那里。
 *
 * 注意：dshmarket 是 dsh **web UI** 的市场面板（`client.platform: "web"`），
 * App 的 Compose 复刻市场不依赖它，数据源是 npm 上的 dsh-plugin-catalog 包
 * （见 DshMarketCatalog）。所以这里探测不到只影响"已安装"chip 的真假，
 * 不影响市场列表能不能看。
 *
 * 探测方式保持最简单：只看 profile 目录下的包目录在不在。
 */
object DshMarketProbe {

    private const val TAG = "DshMarketProbe"

    /** 市场本体包名（npm 上无 scope）。 */
    private const val MARKET_PKG = "dshmarket"

    /** 兜底匹配（若将来包名变更） */
    private val CANDIDATES = listOf("dshmarket", "dsh-market", "dsh-plugin-dshmarket")

    fun isInstalled(ctx: Context): Boolean = probe(ctx) != null

    /** 返回命中的 "<profile>/<包名>"；未安装为 null */
    fun probe(ctx: Context): String? = try {
        val profiles = File(DshBootstrapAccess.homeDir(ctx), ".dsh/profiles")
        val hit = if (profiles.isDirectory) {
            profiles.listFiles()?.sortedBy { it.name }?.firstNotNullOfOrNull { dir ->
                val nm = File(dir, "node_modules")
                if (nm.isDirectory) {
                    nm.list()?.firstOrNull { d -> CANDIDATES.any { d == it || d.contains(it, ignoreCase = true) } }
                        ?.let { "${dir.name}/$it" }
                } else null
            }
        } else null
        Log.i(TAG, "市场本体探测：${hit ?: "未安装"}（扫 ${profiles.absolutePath}/*/node_modules）")
        hit
    } catch (t: Throwable) {
        // 探测本身失败不等于"未安装"，但也不能让 UI 崩；当作未装并留日志
        Log.e(TAG, "市场本体探测失败", t)
        null
    }

    /** 供 UI 直接展示的安装命令。与设计稿一致，只把 profile 换成 acp（App 走的是 acp profile）。 */
    const val INSTALL_CMD = "dsh plugin --profile acp add dshmarket"
}