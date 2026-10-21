package com.dsh.harness.mobile.data

import android.content.Context
import android.util.Log
import com.dsh.harness.DshBootstrap
import java.io.File

/**
 * 用户自定义 Agent preset 的真实探测（v30.9）。
 *
 * dsh 的 preset 分两处（见 `@deepseek-ai/dsh-agent-presets` README）：
 *   1. **随包内置**：`presets/` 下的 standard / ptc / minimal / cordis 四个，
 *      显示名与描述来自各自的 `preset.yml`（S33 的内置段已按真值写死，不会变）。
 *   2. **用户自建**：`<dshHome>/.agent-presets/` 下的目录，每个含 `preset.yml`。
 *
 * 本类只做第 2 类的探测——S33 的「我的预设」此前画的是设计稿示意数据
 * （`web-dev-standard`），本机并不存在。改为读真实目录：有就列，没有就给空态。
 *
 * ⚠️ 只读目录名与 preset.yml 的 name/description，**不解析 cordis.yml**
 * （那是插件装配清单，且可能含受信任配置，不适合在 UI 展开）。
 */
object DshPresets {

    private const val TAG = "DshPresets"

    /** 一个用户自建 preset。 */
    data class UserPreset(
        /** 目录名（同时是 preset id，session 里用它引用） */
        val id: String,
        /** preset.yml 的 name，缺失时回落目录名 */
        val displayName: String,
        /** preset.yml 的 description，可能为空 */
        val description: String,
        val dir: File,
    )

    /**
     * dsh 的用户 preset 根目录。两处都探：`<home>/.dsh/.agent-presets`（DSH_HOME 语义）
     * 与 `<home>/.agent-presets`（README 的 `<dshHome>` 直挂语义），返回第一个存在的。
     */
    fun userRoots(ctx: Context): List<File> {
        val home = DshBootstrap.home(ctx)
        return listOf(File(home, ".dsh/.agent-presets"), File(home, ".agent-presets"))
            .filter { it.isDirectory }
    }

    /** 列出用户自建 preset（目录序）。读不到 / 不存在时返回空列表。 */
    fun listUserPresets(ctx: Context): List<UserPreset> {
        val out = ArrayList<UserPreset>()
        for (root in userRoots(ctx)) {
            val kids = root.listFiles() ?: continue
            for (d in kids.sortedBy { it.name }) {
                if (!d.isDirectory) continue
                val meta = readPresetYml(File(d, "preset.yml"))
                out.add(
                    UserPreset(
                        id = d.name,
                        displayName = meta.first ?: d.name,
                        description = meta.second ?: "",
                        dir = d,
                    ),
                )
            }
        }
        if (out.isEmpty()) {
            Log.i(TAG, "本机无用户自建 preset（${userRoots(ctx).map { it.absolutePath }}）")
        } else {
            Log.i(TAG, "发现 ${out.size} 个用户自建 preset：${out.map { it.id }}")
        }
        return out
    }

    /**
     * 极简解析 `preset.yml` 的 `name:` / `description:` 两行。
     * 这两项都是单行标量（见内置 preset 的 preset.yml），不做完整 YAML 解析。
     * @return (name, description)，缺失项为 null。
     */
    private fun readPresetYml(f: File): Pair<String?, String?> {
        if (!f.isFile) return null to null
        var name: String? = null
        var desc: String? = null
        try {
            f.forEachLine { line ->
                when {
                    name == null && line.startsWith("name:") ->
                        name = line.removePrefix("name:").trim().trim('"').takeIf { it.isNotEmpty() }
                    desc == null && line.startsWith("description:") ->
                        desc = line.removePrefix("description:").trim().trim('"').takeIf { it.isNotEmpty() }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "读取 ${f.absolutePath} 失败", t)
        }
        return name to desc
    }
}
