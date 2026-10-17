package com.dsh.harness.mobile.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * API 密钥管理 —— 直接对接 dsh 的 credentials 本地存储。
 *
 * ## 存储格式（2026-10-18 本机实测，不是猜的）
 * 文件：`<dsh home>/.dsh/.credentials.yaml`（credentials-local 的默认路径）
 * 结构：带版本的 YAML，只有 `version` / `refs` / `records` 三个顶层键。
 *   - `refs:`   按**环境变量名**存 API key，`DEEPSEEK_API_KEY: sk-…`
 *   - `records:` 按 `<owner>/<id>` 存插件凭据，每条带 `api-key` 或 `grant` 标签
 *
 * ## 三个必须遵守的约束（全部来自 dsh-credentials-local 的 README）
 * 1. **只写不回读**：`describe` 只报 `{configured, source, writable}`，**永不返回值**。
 *    所以 UI 上密钥一旦保存就只能显示掩码，重新编辑必须重新输入。
 * 2. **空值等于没有**：存空串会被存储拒绝，移除密钥 = 删除该行，不是置空。
 * 3. **文件会被 watcher 热重载**（debounce 100ms）→ 写完立即生效，**不需要重启 dsh**。
 *
 * ## 写入方式：为什么手写 YAML 而不是调 Node
 * dsh CLI **没有** credentials 子命令（`--help` 只有 web / plugin / --dump-config）。
 * 走 `ctx.credentials.set` 得另起一个 node 进程去 boot cordis context，
 * 成本远高于改一个三键 YAML。所以这里做**行级最小编辑**：
 *   - 保留 `records:` 段原样（那是浏览器会话 grant，动了会登出）
 *   - 只在 `refs:` 下增删目标那一行
 *   - 其余字节不碰（文档说"产品写入会保留注释与未触及条目的排版"）
 *
 * ## 实测验证（本机 2026-10-18）
 * 写入 `refs.DEEPSEEK_API_KEY = sk-test-dummy-key-for-probe` 后跑一次真实 prompt，
 * 服务端报错从 `no API key for provider route "deepseek-official"`
 * 变成 `Your api key: ****robe is invalid` —— 掩码后缀正是我写的值，
 * 证明**这条路径真的被读取，且无需重启**。
 */
class DshCredentials(private val context: Context) {

    companion object {
        private const val TAG = "DshCredentials"
        private const val FILE_NAME = ".credentials.yaml"

        /** deepseek-official 这条 provider route 读的环境变量名。
         *  来自 dsh-llm-deepseek/lib/index.js:1838 `DEFAULT_API_KEY_ENV`。 */
        const val DEEPSEEK_KEY = "DEEPSEEK_API_KEY"
        const val DEEPSEEK_ROUTE = "deepseek-official"
        const val DEEPSEEK_MODEL_DEFAULT = "deepseek-v4-flash"
    }

    /** 凭据文件真身。`$DSH_HOME/.credentials.yaml` —— DSH_HOME 即 home 下的 `.dsh`。 */
    fun file(): File {
        val home = DshBootstrapAccess.homeDir(context)
        return File(File(home, ".dsh"), FILE_NAME)
    }

    data class KeyState(
        /** 是否已配置（只反映存在性，绝不携带值） */
        val configured: Boolean = false,
        /** 仅用于界面上确认"是你刚存的那条"：后 4 位，不足以还原 */
        val maskedHint: String = "",
        /** 文件是否还不存在（首次使用） */
        val fileMissing: Boolean = false,
        /** 真实失败原因 */
        val error: String? = null,
    ) {
        val ok: Boolean get() = configured && error == null
    }

    // ==================== 读状态（只读存在性，不回读值） ====================

    suspend fun describe(ref: String = DEEPSEEK_KEY): KeyState = withContext(Dispatchers.IO) {
        val f = file()
        if (!f.exists()) return@withContext KeyState(fileMissing = true)
        try {
            val lines = f.readLines()
            val value = findRefValue(lines, ref)
            if (value.isNullOrEmpty()) {
                KeyState(configured = false)
            } else {
                // 只保留后 4 位用于"是不是刚存的那条"的确认，不构成回读
                KeyState(configured = true, maskedHint = maskTail(value))
            }
        } catch (t: Throwable) {
            Log.e(TAG, "读凭据状态失败", t)
            KeyState(error = t.message ?: t.javaClass.simpleName)
        }
    }

    // ==================== 写 ====================

    /**
     * 保存密钥。返回 [KeyState]。
     *
     * 校验放在这里做，而不是写坏文件让 dsh 启动失败 ——
     * 文档明确说"格式错误的 YAML 会在启动时失败"，那会让整个 App 起不来。
     */
    suspend fun set(ref: String, value: String): KeyState = withContext(Dispatchers.IO) {
        val clean = value.trim()
        // 约束 2：空值 = 没有密钥，存空串会被存储拒绝 → 直接拦在 UI 层
        if (clean.isEmpty()) {
            return@withContext KeyState(error = "密钥不能为空。如需移除请用「删除密钥」。")
        }
        if (clean.any { it == '\n' || it == '\r' }) {
            return@withContext KeyState(error = "密钥不能包含换行。")
        }

        try {
            val f = file()
            val parent = f.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()

            val lines = if (f.exists()) f.readLines().toMutableList() else defaultDoc().toMutableList()
            val idx = indexOfRefLine(lines, ref)

            if (idx >= 0) {
                lines[idx] = "  $ref: $clean"
            } else {
                ensureRefsSection(lines)
                // refs 段的最后一个非空行之后插入；空文档则直接追加到末尾
                lines.add(refInsertPosition(lines), "  $ref: $clean")
            }

            val f2 = writeAtomically(f, lines)
            if (f2 == null) return@withContext KeyState(error = "写入凭据文件失败（权限或空间）")

            Log.i(TAG, "已写入 $ref（长度 ${clean.length}，值不落日志）")
            // 写完不校验值 —— dsh 的下一次请求会告诉我们对不对
            KeyState(configured = true, maskedHint = maskTail(clean))
        } catch (t: Throwable) {
            Log.e(TAG, "保存密钥失败", t)
            KeyState(error = t.message ?: t.javaClass.simpleName)
        }
    }

    /** 删除密钥（文档：移除 = 删除，不是置空）。 */
    suspend fun unset(ref: String = DEEPSEEK_KEY): KeyState = withContext(Dispatchers.IO) {
        try {
            val f = file()
            if (!f.exists()) return@withContext KeyState(fileMissing = true)
            val lines = f.readLines().toMutableList()
            val idx = indexOfRefLine(lines, ref)
            if (idx < 0) return@withContext KeyState(configured = false)
            lines.removeAt(idx)
            if (writeAtomically(f, lines) == null) {
                return@withContext KeyState(error = "写入凭据文件失败")
            }
            Log.i(TAG, "已移除 $ref")
            KeyState(configured = false)
        } catch (t: Throwable) {
            Log.e(TAG, "移除密钥失败", t)
            KeyState(error = t.message ?: t.javaClass.simpleName)
        }
    }

    // ==================== 解析工具 ====================

    /** 只在 `refs:` 分节内找 `  KEY: value`；不会误命中 records 段或注释。 */
    private fun findRefValue(lines: List<String>, ref: String): String? {
        var inRefs = false
        for (raw in lines) {
            val line = raw.trimEnd()
            when {
                // 顶层键：没有前导空格
                line.startsWith("refs:") -> { inRefs = true; continue }
                line.isNotEmpty() && !line.startsWith(" ") && !line.startsWith("#") -> { inRefs = false }
            }
            if (!inRefs) continue
            val t = line.trimStart()
            if (!t.startsWith("$ref:")) continue
            val v = t.substringAfter("$ref:").trim()
            // 去掉可能的引号（文档说值可以任意文本，但手写的人可能会加引号）
            return v.trim('\'', '"')
        }
        return null
    }

    private fun indexOfRefLine(lines: List<String>, ref: String): Int {
        var inRefs = false
        for ((i, raw) in lines.withIndex()) {
            val line = raw.trimEnd()
            when {
                line.startsWith("refs:") -> { inRefs = true; continue }
                line.isNotEmpty() && !line.startsWith(" ") && !line.startsWith("#") -> { inRefs = false }
            }
            if (!inRefs) continue
            if (line.trimStart().startsWith("$ref:")) return i
        }
        return -1
    }

    /** 确认文档里有 `refs:` 顶层键；没有就插在 `version:` 之后。 */
    private fun ensureRefsSection(lines: MutableList<String>) {
        if (lines.any { it.trimStart().startsWith("refs:") }) return
        val vi = lines.indexOfFirst { it.trimStart().startsWith("version:") }
        if (vi >= 0) lines.add(vi + 1, "refs:") else lines.add(0, "refs:")
    }

    /** refs 段里该插哪：最后一个内容行之后；若 refs 是空的（下一行就是别的顶层键或 EOF）就插 refs 下面第一行。 */
    private fun refInsertPosition(lines: List<String>): Int {
        val start = lines.indexOfFirst { it.trimStart().startsWith("refs:") }
        if (start < 0) return lines.size
        var last = start
        for (i in start + 1 until lines.size) {
            val line = lines[i]
            val isTop = line.isNotEmpty() && !line.startsWith(" ")
            if (isTop) break
            if (line.isNotBlank()) last = i
        }
        return last + 1
    }

    /** 新文档骨架。字段顺序与文档示例一致：version / refs / records。 */
    private fun defaultDoc() = listOf(
        "version: 1",
        "refs:",
        "records:",
    )

    /**
     * 原子写：先写临时文件再 rename。
     * dsh 的 watcher 随时可能在读，写一半会让它加载失败。
     */
    private fun writeAtomically(target: File, lines: List<String>): File? = try {
        val tmp = File(target.parentFile, "${FILE_NAME}.tmp")
        tmp.writeText(lines.joinToString("\n") + "\n")
        // dsh 文档：产品以仅属主可访问的权限创建它（POSIX 600）
        try { tmp.setReadable(false, false); tmp.setReadable(true, true) } catch (_: Throwable) {}
        if (!tmp.renameTo(target)) {
            // rename 在某些 Android 文件系统上跨挂载会失败，退化为直接覆盖
            target.writeText(tmp.readText())
            tmp.delete()
        }
        target
    } catch (t: Throwable) {
        Log.e(TAG, "原子写失败", t)
        null
    }

    /** 只给后 4 位。仅用于"是不是刚存的那条"，不足以还原任何密钥。 */
    private fun maskTail(value: String): String {
        val t = value.takeLast(4)
        return if (t.length < 4) "••••" else "••••$t"
    }
}