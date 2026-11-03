package com.dsh.harness.mobile.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * 设置命名空间的进程内缓存 + 写回（v33）。
 *
 * dsh 的 `settings/describe` 一次返回 20 个命名空间（本机实测 80KB），
 * 每个页面的每次进入都重拉太浪费 —— 这里缓存一份，写回成功后原位更新。
 * 写走 `settings/update(ns, patch, expectedRevision)`（乐观并发：revision
 * 不一致服务端回 settings-conflict，我们只把错误原文抛给 UI）。
 */
object DshSettingsStore {
    private const val TAG = "DshSettingsStore"

    /** 一个命名空间的真实视图。 */
    data class Ns(
        val id: String,
        val value: JSONObject,
        val revision: Int,
    )

    @Volatile
    private var cache: Map<String, Ns>? = null

    @Volatile
    var lastError: String? = null
        private set

    /** 读缓存或拉取。失败时返回旧缓存（可能为 null）。 */
    fun load(ctx: Context, force: Boolean = false): Map<String, Ns>? {
        if (!force) cache?.let { return it }
        val value = DshSettingsApi.describe(ctx)
        if (value == null) {
            lastError = "设置服务无响应（引擎是否在运行？）"
            return cache
        }
        val arr = value.optJSONArray("namespaces")
        if (arr == null) {
            lastError = "describe 返回缺少 namespaces"
            return cache
        }
        val map = LinkedHashMap<String, Ns>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("ns")
            if (id.isBlank()) continue
            map[id] = Ns(
                id = id,
                value = o.optJSONObject("value") ?: JSONObject(),
                revision = o.optInt("revision", 0),
            )
        }
        cache = map
        lastError = null
        return map
    }

    fun get(ctx: Context, ns: String): Ns? = load(ctx)?.get(ns)

    /**
     * `settings/update`：把 patch 合并进命名空间。
     * @return 成功返回 true；失败把服务端错误写进 [lastError]
     */
    fun update(ctx: Context, ns: String, patch: JSONObject): Boolean = update(ctx, ns, patch, retried = false)

    /**
     * 带一次冲突重试的写入。
     *
     * 真机踩过：设置页里「写 settings」和「写 cordis patch 层」是两条通路，
     * 谁先跑谁把 revision 顶上去，后跑的带着旧 expectedRevision 必被
     * `settings-conflict` 拒掉（表现为控件改了、重启又回去了）。
     * 这里遇到冲突就强拉一次 describe 拿新 revision，再写一次。
     */
    private fun update(ctx: Context, ns: String, patch: JSONObject, retried: Boolean): Boolean {
        val rev = cache?.get(ns)?.revision
        val args = JSONObject().put("ns", ns).put("patch", patch)
        if (rev != null) args.put("expectedRevision", rev)
        val result = DshSettingsApi.call(ctx, "settings/update", args)
        if (result == null) {
            lastError = "写入失败：无响应"
            return false
        }
        if (!result.optBoolean("ok", false)) {
            val err = result.optJSONObject("error")
            val code = err?.optString("code") ?: ""
            val message = err?.optString("message")?.takeIf { it.isNotBlank() } ?: "写入被拒绝"
            if (!retried && (code.contains("conflict") || message.contains("changed since it was read"))) {
                load(ctx, force = true)
                return update(ctx, ns, patch, retried = true)
            }
            lastError = message
            Log.w(TAG, "update $ns 失败: $lastError")
            return false
        }
        // SettingsNamespaceView：{ns, value, revision, ...}（宽松解析，字段缺失按旧值保留）
        val view = result.optJSONObject("value")
        val newValue = view?.optJSONObject("value") ?: patch
        val newRev = view?.optInt("revision", (rev ?: 0) + 1) ?: ((rev ?: 0) + 1)
        val merged = JSONObject(cache?.get(ns)?.value?.toString() ?: "{}")
        val keys = patch.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            merged.put(k, patch.get(k))
        }
        val m = cache?.toMutableMap() ?: LinkedHashMap()
        m[ns] = Ns(ns, if (newValue.length() > 0) newValue else merged, newRev)
        cache = m
        lastError = null
        mirrorToPatch(ctx, ns, patch)
        return true
    }

    /**
     * 把刚写进去的字段**镜像**到 profile 的 patch 层。
     *
     * 为什么必须双写：settings 服务与 cordis patch 层是两套存储，引擎重启后
     * patch 层的 config 会盖回 settings 的值。真机实测：`locale.preference`、
     * `permission.defaultPreset` 写完落到了 patch 层，而 `ui-theme.fontSize`
     * 写完没落（改完重进又变回旧值）——行为不一致，不能赌。
     * 统一「settings + patch」双写，改什么就一定重启后还在。
     */
    private fun mirrorToPatch(ctx: Context, ns: String, patch: JSONObject) {
        val keys = patch.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = patch.get(k)
            // 嵌套结构（providers / presets 之类）不镜像：patch 里是 YAML 树，
            // 按行改写不了，硬写会把文件搞坏。
            if (v is JSONObject || v is JSONArray) continue
            val err = DshPluginPatch.setConfigValue(ctx, ns, k, DshPluginPatch.yamlScalar(v))
            if (err != null) Log.w(TAG, "patch 镜像失败 $ns.$k：$err")
        }
    }
}
