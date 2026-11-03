package com.dsh.harness.mobile.data

import android.content.Context
import org.json.JSONObject

/**
 * 权限档位目录（v34）。
 *
 * 官方设置的「权限」行不是硬编码选项，而是读 host 的 `permissionPresets/catalog`
 * ——档位由 cordis 配置决定（默认只有 workspace-write / danger-full-access，
 * 加装插件后还会出现 read-only 与实验性的 auto）。所以这里也走同一条 RPC，
 * 拿不到时退回官方默认两档，避免一次读取失败就把整行变成死控件。
 *
 * 通道与鉴权同 [DshSettingsApi]：POST /api/<endpoint> + WebView 同源 cookie。
 */
object DshPermissionCatalog {

    data class Option(val value: String, val name: String, val description: String?)

    data class Catalog(val options: List<Option>, val defaultPreset: String?)

    /** host 读不到时的兜底：DeepSeek 默认配置里一定存在的两档。 */
    val fallback = listOf(
        Option("workspace-write", "workspace-write", null),
        Option("danger-full-access", "danger-full-access", null),
    )

    fun load(ctx: Context): Catalog? {
        val r = DshSettingsApi.call(ctx, "permissionPresets/catalog") ?: return null
        if (!r.optBoolean("ok")) return null
        val v = r.optJSONObject("value") ?: return null
        val arr = v.optJSONArray("options") ?: return null
        val out = ArrayList<Option>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val value = o.optString("value").takeIf { it.isNotBlank() } ?: continue
            val name = o.optString("name").takeIf { it.isNotBlank() } ?: value
            out.add(Option(value, name, o.optString("description").takeIf { it.isNotBlank() }))
        }
        if (out.isEmpty()) return null
        return Catalog(out, v.optString("defaultPreset").takeIf { it.isNotBlank() })
    }
}
