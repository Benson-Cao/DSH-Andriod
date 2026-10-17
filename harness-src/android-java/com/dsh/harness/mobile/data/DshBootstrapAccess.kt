package com.dsh.harness.mobile.data

import android.content.Context
import android.util.Log
import com.dsh.harness.DshBootstrap
import com.dsh.harness.ShellRunner
import java.io.File

/**
 * 访问 termux 运行时环境的薄桥。
 *
 * 这些路径约定全部沿用现有 harness（DshBootstrap / ShellRunner），
 * **不自己另发明一套**——否则 ACP 进程会用错 HOME / node，出现"本机能跑真机起不来"。
 */
object DshBootstrapAccess {

    /** $PREFIX = ctx.filesDir/usr */
    fun prefixDir(ctx: Context): File = DshBootstrap.prefix(ctx)

    /**
     * dsh HOME。
     *
     * ⚠️ v13 真机踩坑：home 与 prefix 是**兄弟目录**，不是子目录！
     *   prefix = filesDir/usr
     *   home   = filesDir/home     ← 不是 filesDir/usr/home
     * 自己拼 `File(prefix, "home")` 会多一层目录，dshBundle 找不到 → ACP 起不来。
     * 直接复用 DshBootstrap.home(ctx)，别自己推路径。
     */
    fun homeDir(ctx: Context): File = DshBootstrap.home(ctx)

    /** node 可执行文件 */
    fun nodeBin(ctx: Context): File = File(prefixDir(ctx), "bin/node")

    /**
     * dsh 入口脚本。现有 web 模式启动的就是这个文件
     * （DshBootstrap 里 "$HOME/node_modules/@deepseek-ai/dsh/lib/bin.js web ..."）。
     */
    fun dshBinJs(ctx: Context): File =
        File(homeDir(ctx), "node_modules/@deepseek-ai/dsh/lib/bin.js")

/** 首次安装时解压 dsh bundle（node + $HOME/node_modules）。有 stamp 判重，幂等。 */
    fun ensureBootstrapped(ctx: Context) {
        DshBootstrap.setupIfNeeded(ctx)
    }

    /**
     * termux 环境变量。复用 ShellRunner.buildEnv —— 它是 harness 里已经验证过的
     * 那一份（含 PATH/HOME/TERMUX_* 与 node 预加载 shim 的 NODE_OPTIONS）。
     *
     * ⚠️ buildEnv 内部第一行就是 prefix(ctx)，**传 null 必然 NPE**。
     * 所以这里必须收 Context，绝不能图省事写 buildEnv(null)——
     * 那样会静默掉进兜底分支，PATH/HOME 变成假值，ACP 进程必然起不来。
     */
fun termuxEnv(ctx: Context): Map<String, String> {
    val m = LinkedHashMap<String, String>()
    try {
        for (kv in ShellRunner.buildEnv(ctx)) {
            val i = kv.indexOf('=')
            if (i > 0) m[kv.substring(0, i)] = kv.substring(i + 1)
        }
    } catch (t: Throwable) {
        // 兜底也用真实路径，不用硬编码
        Log.w("DshBootstrapAccess", "buildEnv 失败，用兜底环境: ${t.message}")
        val p = prefixDir(ctx).absolutePath
        m["PATH"] = "$p/bin:/system/bin"
        m["HOME"] = homeDir(ctx).absolutePath
        m["PREFIX"] = p
        m["TMPDIR"] = "$p/tmp"
    }
    return m
}

    /** ACP 协议要求 cwd 必须是**绝对路径且已存在** */
    fun resolveWorkspace(ctx: Context, path: String?): File {
        val p = if (path.isNullOrBlank()) homeDir(ctx) else File(path)
        return if (p.exists() && p.isDirectory) p else homeDir(ctx)
    }
}