package com.dsh.harness.mobile.data

import android.content.Context
import com.dsh.harness.DshBootstrap
import java.io.File

/**
 * 内置插件的启停 —— 唯一真实落点是 profile 的 patch 层：
 *
 * ```
 * <dsh home>/.dsh/profiles/web/cordis.patch.yml
 * ```
 *
 * 这个文件是 Cordis loader 的「补丁层」，在 bundle 层之后应用，格式是一个
 * 顶层 YAML 数组，每条 `- id: <插件 id>` 可带 `name` / `config` / `disabled`：
 *
 * ```yaml
 * - id: hmr
 *   name: "@deepseek-ai/cordis-plugin-hmr"
 *   disabled: true
 * ```
 *
 * 所以「停用插件」= 把 `disabled` 写成 `true`，「启用」= 写成 `false`（不是删键，
 * 显式 false 才能盖住 bundle 层可能存在的 `disabled: true`）。
 *
 * ## 为什么按行改而不是解析 YAML
 * 这个文件里有 `!!js` 表达式和用户手写的注释，任何「解析 → 重新序列化」都会
 * 丢掉注释或改写表达式。改开关只需要动一行 `disabled:`，按行定位是最小侵入。
 */
object DshPluginPatch {

    /** patch 条目 id + 包名（条目不存在时用于新建）。 */
    private data class Entry(val id: String, val pkg: String)

    /**
     * 设置命名空间 → patch 层条目。
     *
     * 注意两者**不一定同名**：`ui-settings`（开发者工具开关）属于
     * `ui-settings-general` 这个插件条目 —— 官方客户端也是按插件 id 定位 config 的。
     */
    private val NS_TO_ENTRY = mapOf(
        "ui-theme" to Entry("ui-theme", "@deepseek-ai/dsh-client-ui-theme"),
        "ui-chat" to Entry("ui-chat", "@deepseek-ai/dsh-client-ui-chat"),
        "ui-conversation" to Entry("ui-conversation", "@deepseek-ai/dsh-client-ui-conversation"),
        "locale" to Entry("locale", "@deepseek-ai/dsh-client-locale"),
        "permission" to Entry("permission", "@deepseek-ai/dsh-permission-presets"),
        "ui-settings" to Entry("ui-settings-general", "@deepseek-ai/dsh-client-ui-settings-general"),
        "session-log-deepseek" to Entry("ui-settings-session-log", "@deepseek-ai/dsh-client-ui-settings-session-log"),
        "agent-preset-registry" to Entry("agent-preset-registry", "@deepseek-ai/dsh-agent-preset-registry"),
    )

    /** 手机端唯一的 web profile 补丁层。 */
    fun patchFile(ctx: Context): File =
        File(DshBootstrap.home(ctx), ".dsh/profiles/web/cordis.patch.yml")

    /**
     * 把一个设置字段写进 patch 层对应条目的 `config:` 块。
     *
     * 为什么要双写：settings 服务与 patch 层是**两套存储**，引擎重启后
     * patch 层的 config 会盖回 settings 的值。真机实测 `ui-theme.fontSize`
     * 走 settings 写完没落盘（改完重进又变回旧值），而 `locale.preference`
     * 写完落到了 patch 层 —— 行为不一致，不能赌。统一「settings + patch」双写。
     *
     * @param ns   设置命名空间（ui-theme / ui-chat / locale / permission …）
     * @param key  字段名（fontSize / preference / defaultPreset …）
     * @param value 已序列化成 YAML 标量的值
     * @return 失败原因；成功返回 null
     */
    fun setConfigValue(ctx: Context, ns: String, key: String, value: String): String? {
        val entry = NS_TO_ENTRY[ns] ?: return "未知的设置命名空间：$ns"
        return setEntryConfig(ctx, entry.id, entry.pkg, key, value)
    }

    /** 在指定条目里就地改/插一个 config 字段（按行改，保留注释与 !!js 表达式）。 */
    fun setEntryConfig(
        ctx: Context,
        entryId: String,
        pkg: String,
        key: String,
        value: String,
    ): String? {
        val file = patchFile(ctx)
        return try {
            if (!file.exists()) return "找不到 cordis.patch.yml"
            val lines = file.readLines().toMutableList()
            val head = lines.indexOfFirst {
                it.trimStart().startsWith("- id:") && idOf(it) == entryId
            }
            if (head < 0) {
                // 条目不存在 → 新建（loader 按 id 定位，写错 id 只会让这条无效，不会破坏别的）
                if (lines.isNotEmpty() && lines.last().isNotBlank()) lines.add("")
                lines.add("- id: $entryId")
                lines.add("  name: \"$pkg\"")
                lines.add("  config:")
                lines.add("    $key: $value")
                file.writeText(lines.joinToString("\n") + "\n")
                return null
            }

            var end = lines.size
            for (i in head + 1 until lines.size) {
                if (lines[i].trimStart().startsWith("- id:")) { end = i; break }
            }
            val at = (head until end).firstOrNull {
                lines[it].trimStart().startsWith("$key:")
            }
            if (at != null) {
                val indent = lines[at].takeWhile { ch -> ch == ' ' }
                lines[at] = "${indent}$key: $value"
            } else {
                val cfg = (head until end).firstOrNull {
                    lines[it].trimStart().startsWith("config:")
                }
                if (cfg != null) {
                    lines.add(cfg + 1, "    $key: $value")
                } else {
                    lines.add(head + 1, "  config:")
                    lines.add(head + 2, "    $key: $value")
                }
            }
            file.writeText(lines.joinToString("\n") + "\n")
            null
        } catch (t: Throwable) {
            t.message ?: "写入 cordis.patch.yml 失败"
        }
    }

    /** 把值格式化成 YAML 标量：含特殊字符就加引号。 */
    fun yamlScalar(v: Any): String = when (v) {
        is Boolean, is Number -> v.toString()
        else -> {
            val s = v.toString()
            val needQuote = s.isEmpty() || s.any {
                it.isWhitespace() || it in ":#{}[]&*!|>'\"%@`," || it == '-'
            }
            if (needQuote) "\"" + s.replace("\"", "\\\"") + "\"" else s
        }
    }

    /**
     * 设置某插件的启停。
     *
     * @param id  dump-config 里该条目的 id（也是 patch 层的定位键）
     * @param pkg 包名；仅在文件里还没有这条时用于新建条目
     * @param enabled true=启用（`disabled: false`），false=停用（`disabled: true`）
     * @return 失败原因；成功返回 null
     */
    fun setEnabled(ctx: Context, id: String, pkg: String, enabled: Boolean): String? {
        val file = patchFile(ctx)
        return try {
            val lines: MutableList<String> =
                if (file.exists()) file.readLines().toMutableList() else mutableListOf()
            val value = if (enabled) "false" else "true"
            val head = lines.indexOfFirst { it.trimStart().startsWith("- id:") && idOf(it) == id }

            if (head >= 0) {
                // 条目边界：下一个 `- id:` 或文件末尾
                var end = lines.size
                for (i in head + 1 until lines.size) {
                    if (lines[i].trimStart().startsWith("- id:")) { end = i; break }
                }
                val at = (head until end).firstOrNull {
                    lines[it].trimStart().startsWith("disabled:")
                }
                if (at != null) {
                    val indent = lines[at].takeWhile { ch -> ch == ' ' }
                    lines[at] = "${indent}disabled: $value"
                } else {
                    lines.add(head + 1, "  disabled: $value")
                }
            } else {
                if (lines.isNotEmpty() && lines.last().isNotBlank()) lines.add("")
                lines.add("- id: $id")
                lines.add("  name: \"$pkg\"")
                lines.add("  disabled: $value")
            }

            file.parentFile?.mkdirs()
            file.writeText(lines.joinToString("\n") + "\n")
            null
        } catch (t: Throwable) {
            t.message ?: "写入 cordis.patch.yml 失败"
        }
    }

    /** 从 `- id: xxx` 行里取出 id（去掉可能存在的行内注释）。 */
    private fun idOf(line: String): String =
        line.substringAfter("- id:", "").trim().trim('"', '\'').substringBefore(" ")

    /**
     * 把「主题」偏好写进 profile 的 patch 层。
     *
     * 和 Agent 预设同理：settings 服务里的 `ui-theme.preference` 只对当前进程有效，
     * 引擎重启后 patch 层的 `- id: ui-theme` 会把它盖回旧值 —— 表现就是
     * 「设了深色，重启又变浅色」。
     *
     * @param pref `system` / `light` / `dark`
     */
    fun setUiThemePreference(ctx: Context, pref: String): String? =
        setEntryConfig(ctx, "ui-theme", "@deepseek-ai/dsh-client-ui-theme", "preference", pref)

    /**
     * 把「新任务默认」写进 profile 的 patch 层。
     *
     * 只写 settings 服务是不够的：真机实测 `settings/update agent-preset-registry
     * {selectedDefault}` 当场生效，但引擎重启后会被 patch 层里
     * `- id: agent-preset-registry` 的 `config.selectedDefault` 覆盖回旧值。
     * 所以两处都要写，patch 层才是重启后的真源。
     */
    fun setAgentPresetDefault(ctx: Context, presetId: String): String? =
        setEntryConfig(
            ctx, "agent-preset-registry", "@deepseek-ai/dsh-agent-preset-registry",
            "selectedDefault", presetId,
        )
}
