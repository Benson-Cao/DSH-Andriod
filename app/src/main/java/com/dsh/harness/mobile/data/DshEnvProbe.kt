package com.dsh.harness.mobile.data

import android.content.Context
import android.util.Log

/**
 * S23「运行环境自检」的真实数据源。
 *
 * 之前这行是写死的「1 项待修」演示数据（违反"无演示数据"原则），现已换成真实检测。
 * 每一项都是实打实的文件/凭据状态，不猜、不编：
 *
 *  - node 运行时：`$PREFIX/bin/node`（DshBootstrapAccess.nodeBin，与 ACP 启动同一份）
 *  - dsh 主程序：`$HOME/node_modules/@deepseek-ai/dsh/lib/bin.js`（ACP / web 共用入口）
 *  - home 目录：`$HOME`（注意与 prefix 是兄弟目录，不是子目录——v13 真机踩坑）
 *  - API 密钥：`.credentials.yaml` 的 refs 里是否配了 DEEPSEEK_API_KEY。
 *    没 key 时 dsh 实测报 `no API key for provider route "deepseek-official"`，
 *    工具调用不会出现——所以它是"能不能真跑任务"的必要条件。
 *
 * 密钥详情绝不回读明文（dsh credentials 只写不回读约束），只报 配置/未配置。
 */
object DshEnvProbe {
    private const val TAG = "DshEnvProbe"

    data class Check(val name: String, val ok: Boolean, val detail: String)

    data class Report(val checks: List<Check>, val elapsedMs: Long) {
        val missing: Int get() = checks.count { !it.ok }
        val allOk: Boolean get() = missing == 0
    }

    suspend fun probe(ctx: Context): Report {
        val t0 = System.currentTimeMillis()
        val home = DshBootstrapAccess.homeDir(ctx)
        val node = DshBootstrapAccess.nodeBin(ctx)
        val binJs = DshBootstrapAccess.dshBinJs(ctx)

        val key = try {
            DshCredentials(ctx).describe()
        } catch (t: Throwable) {
            Log.w(TAG, "凭据状态读取失败", t)
            null
        }

        val checks = listOf(
            Check(
                name = "node 运行时",
                ok = node.exists(),
                detail = if (node.exists()) node.absolutePath
                else "缺 ${node.absolutePath} · bundle 未解压？",
            ),
            Check(
                name = "dsh 主程序",
                ok = binJs.exists(),
                detail = if (binJs.exists()) binJs.absolutePath
                else "缺 ${binJs.absolutePath} · bundle 未解压？",
            ),
            Check(
                name = "home 目录",
                ok = home.isDirectory,
                detail = if (home.isDirectory) home.absolutePath
                else "缺 ${home.absolutePath}",
            ),
            Check(
                name = "API 密钥",
                ok = key?.configured == true,
                detail = if (key == null) "凭据状态读取失败"
                else if (key?.configured == true) "deepseek-official · 已配置"
                else "未配置 · 到 设置 → API 密钥 录入",
            ),
        )
        val r = Report(checks, System.currentTimeMillis() - t0)
        Log.i(TAG, "环境自检：${r.missing} 项缺失，耗时 ${r.elapsedMs}ms")
        return r
    }
}
