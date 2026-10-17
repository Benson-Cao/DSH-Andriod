package com.dsh.harness.mobile.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * 内置插件目录 —— 唯一真实数据源是 dsh 自己的 `--dump-config`。
 *
 * ## 为什么不是"读文件"或"写死清单"
 * 设计稿 S19 写「随发行内置 100+ 个能力插件」，但**具体是哪 86 个、谁被停用、
 * 每个插件带什么 config，只有 dsh 自己知道**（Cordis 在启动时按 profile 装配 bundle）。
 * 所以这里不去猜、不去抄设计稿里的示意名，而是真跑一次：
 *
 *   node --expose-internals $HOME/node_modules/@deepseek-ai/dsh/lib/bin.js \
 *        --profile acp --dump-config
 *
 * 本机 2026-10-18 实测：输出 348 行 YAML、86 个插件条目，
 * 其中 hmr / session-title-llm / skill-badge 三个是 `disabled: true`。
 *
 * ## 输出格式（照抄实测）
 * ```
 * # == @deepseek-ai/dsh-base
 * - id: timer
 *   name: '@deepseek-ai/cordis-plugin-timer'
 * - id: hmr
 *   name: '@deepseek-ai/cordis-plugin-hmr'
 *   disabled: true
 *   config:
 *     root:
 *       - .
 * ```
 * 缩进 2 空格是条目的顶层键；`config:` 下面更深的内容整体原样收进 rawConfig，
 * 不做深解析（页面只需要"这个插件带配置"的提示与键名）。
 */
class DshPluginCatalog(private val context: Context) {

    companion object {
        private const val TAG = "DshPluginCatalog"
        /** 本机实测 node 冷启到出 dump 约 4~8s，给 40s 余量 */
        private const val TIMEOUT_S = 40L
    }

    /**
     * 一个内置插件。字段全部来自 dump-config，不含任何推测值。
     *
     * @param scope       dump 里 `# == ...` 分组，决定它来自 base 还是 acp-app 的补丁
     * @param configKeys  config 下的键名（仅键，不含值 —— 值可能含路径与提示词全文）
     * @param rawConfig   config 块原文，用于 S20 的展示
     */
    data class Builtin(
        val id: String,
        val pkg: String,
        val enabled: Boolean,
        val scope: String,
        val configKeys: List<String>,
        val rawConfig: String,
    ) {
        /** S19 副标题用：把 npm 包名压成一句人能读的说明 */
        val shortName: String get() = pkg.removePrefix("@deepseek-ai/")
    }

    data class Catalog(
        val plugins: List<Builtin>,
        /** dump 失败时的原因（真实 stderr/异常文本），UI 直接显示 */
        val error: String? = null,
        /** 实际跑这条命令的耗时，毫秒 */
        val elapsedMs: Long = 0L,
    )

    /**
     * 跑一次 dump-config 并解析。任何异常都**如实回传**到 [Catalog.error]，
     * 不静默吞掉 —— 之前"顶栏永远无连接"就是被 catch 吞掉才查了半天的。
     */
    suspend fun load(): Catalog = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        var proc: Process? = null
        try {
            DshBootstrapAccess.ensureBootstrapped(context)

            val node = DshBootstrapAccess.nodeBin(context)
            if (!node.exists()) {
                return@withContext Catalog(emptyList(), "找不到 node：${node.absolutePath}")
            }
            val binJs = DshBootstrapAccess.dshBinJs(context)
            if (!binJs.exists()) {
                return@withContext Catalog(emptyList(), "找不到 dsh 入口：${binJs.absolutePath}")
            }

            val pb = ProcessBuilder(
                node.absolutePath,
                "--expose-internals",
                binJs.absolutePath,
                "--profile", "acp",
                "--dump-config",
            )
            pb.directory(DshBootstrapAccess.homeDir(context))
            pb.environment().clear()
            DshBootstrapAccess.termuxEnv(context).forEach { (k, v) -> pb.environment()[k] = v }
            // dump 走 stdout，错误合并进来好定位
            pb.redirectErrorStream(true)

            proc = pb.start()
            val text = BufferedReader(InputStreamReader(proc.inputStream)).use { br ->
                br.readText()
            }
            if (!proc.waitFor(TIMEOUT_S, TimeUnit.SECONDS)) {
                proc.destroy()
                return@withContext Catalog(emptyList(), "读取插件配置超时（${TIMEOUT_S}s）")
            }
            val code = proc.exitValue()
            val parsed = parse(text)
            Log.i(TAG, "dump-config exit=$code, 解析出 ${parsed.size} 个插件，耗时 ${System.currentTimeMillis() - t0}ms")
            if (parsed.isEmpty()) {
                // dump 有输出但一条都没解析出来 → 说明格式变了，必须报出来而不是显示"0 个插件"
                Catalog(emptyList(), "插件配置解析失败，原始输出前 200 字：${text.take(200)}")
            } else {
                Catalog(parsed, null, System.currentTimeMillis() - t0)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "dump-config 失败", t)
            Catalog(emptyList(), t.message ?: t.javaClass.simpleName)
        } finally {
            try { proc?.destroy() } catch (_: Throwable) {}
        }
    }

    /**
     * 解析 dump-config 的 YAML。这里**不引入 YAML 库**：
     * 输出格式被 dsh 固定成"两空格缩进 + `- id:` 列表"，用行扫描更可控，
     * 也避免为一个只读列表拖进 snakeyaml（Android 上体积敏感）。
     */
    fun parse(text: String): List<Builtin> {
        val out = ArrayList<Builtin>()
        var scope = ""
        var curId: String? = null
        var curPkg = ""
        var disabled = false
        var cfgKeys = ArrayList<String>()
        var cfgLines = StringBuilder()
        var inConfig = false

        fun flush() {
            val id = curId
            if (!id.isNullOrEmpty()) {
                out.add(
                    Builtin(
                        id = id,
                        pkg = curPkg,
                        enabled = !disabled,
                        scope = scope,
                        configKeys = ArrayList(cfgKeys),
                        rawConfig = cfgLines.toString().trimEnd(),
                    )
                )
            }
            curId = null
            curPkg = ""
            disabled = false
            cfgKeys = ArrayList()
            cfgLines = StringBuilder()
            inConfig = false
        }

        for (raw in text.lines()) {
            val line = raw.replace("\r", "")
            if (line.isBlank()) continue

            // 分组头：# == @deepseek-ai/dsh-base[, patched by ...]
            if (line.startsWith("# ==")) {
                flush()
                scope = line.removePrefix("# ==").trim()
                continue
            }

            // 新条目起点：顶格 "- id: xxx"
            if (line.startsWith("- id:")) {
                flush()
                curId = line.removePrefix("- id:").trim().trim('\'', '"')
                continue
            }

            if (curId == null) continue

            if (inConfig) {
                // config 块内部：4 空格以下仍是 config 的内容，超过就是新的一级键
                val indent = line.takeWhile { it == ' ' }.length
                if (indent >= 4) {
                    cfgLines.append(line).append('\n')
                    continue
                }
                inConfig = false
            }

            val trimmed = line.trim()
            when {
                trimmed.startsWith("name:") -> {
                    curPkg = trimmed.removePrefix("name:").trim().trim('\'', '"')
                }
                trimmed.startsWith("disabled:") -> {
                    disabled = trimmed.removePrefix("disabled:").trim() == "true"
                }
                trimmed.startsWith("config:") -> {
                    inConfig = true
                }
                // 其余一级键（如 inject:）本页不消费，原样忽略
                else -> Unit
            }
        }
        flush()

        // config 键名：从 rawConfig 里回捞"顶层键"（缩进 4 的那层）
        return out.map { b ->
            if (b.configKeys.isEmpty() && b.rawConfig.isNotEmpty()) {
                val keys = b.rawConfig.lines()
                    .filter { it.startsWith("    ") && !it.startsWith("      ") }
                    .map { it.trim().substringBefore(":").trim() }
                    .filter { it.isNotEmpty() && it != "-" }
                    .distinct()
                b.copy(configKeys = keys)
            } else b
        }
    }

    /** 导出脱敏日志用的临时文件（S23「导出脱敏日志」按钮用） */
    fun writeRedactedLog(catalog: Catalog): File? = try {
        val f = File(DshBootstrapAccess.homeDir(context), ".dsh/plugin-catalog.redacted.txt")
        f.parentFile?.mkdirs()
        f.writeText(
            buildString {
                appendLine("# dsh 内置插件目录（脱敏：仅 id / 包名 / 配置键名，不含配置值）")
                appendLine("# 共 ${catalog.plugins.size} 个，其中启用 ${catalog.plugins.count { it.enabled }}")
                catalog.plugins.forEach {
                    appendLine("- ${it.id}  ${it.pkg}  ${if (it.enabled) "enabled" else "disabled"}  [${it.scope}]")
                    it.configKeys.forEach { k -> appendLine("    · $k") }
                }
            }
        )
        f
    } catch (t: Throwable) {
        Log.e(TAG, "写脱敏日志失败", t)
        null
    }
}