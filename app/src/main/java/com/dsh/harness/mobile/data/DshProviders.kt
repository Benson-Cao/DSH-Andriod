package com.dsh.harness.mobile.data

import android.content.Context
import android.util.Log
import com.dsh.harness.DshBootstrap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 自定义模型提供方（S25）的本地真实存储。
 *
 * ⚠️ 过渡方案：dsh 侧 provider 装配 API 暂未打通，这里先把录入落盘到
 * `$HOME/.dsh/providers.json`，保证「录入即保存、可回显、不丢」。
 * 字段与 Web 端 设置 → 模型 → 添加自定义提供方 对齐：Provider ID / 显示名称 /
 * API 地址 / 协议 / 密钥 / 模型目录。
 *
 * 密钥存本地文件——这是 App 私有录入（非 dsh 凭据服务），与 S24 走
 * `DshCredentials` 的 DeepSeek 官方 key 是两条不同路径，UI 上已区分清楚。
 */
object DshProviders {
    private const val TAG = "DshProviders"

    data class Provider(
        val id: String,
        val name: String,
        val apiUrl: String,
        val protocol: String,
        val apiKey: String,
        val models: List<String> = emptyList(),
    ) {
        val ok: Boolean
            get() = id.matches(Regex("^[a-z][a-z0-9]*$")) && name.isNotBlank()
                    && apiUrl.isNotBlank() && apiKey.isNotBlank()
    }

    private fun file(ctx: Context): File {
        val home = DshBootstrap.home(ctx)
        val dir = File(home, ".dsh")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "providers.json")
    }

    fun list(ctx: Context): List<Provider> {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Provider(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    apiUrl = o.optString("apiUrl"),
                    protocol = o.optString("protocol", "openai-completions"),
                    apiKey = o.optString("apiKey"),
                    models = (0 until o.optJSONArray("models").length()).map { o.optJSONArray("models").optString(it) },
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "读取提供方失败", t)
            emptyList()
        }
    }

    /** 追加一个提供方；id 重复则覆盖旧条目。返回落盘后的完整列表（失败返回 null）。 */
    fun add(ctx: Context, p: Provider): List<Provider>? {
        if (!p.ok) {
            Log.w(TAG, "提供方校验未过，不写入：$p")
            return null
        }
        val all = list(ctx).filter { it.id != p.id }.toMutableList()
        all.add(p)
        return try {
            val arr = JSONArray()
            all.forEach { p ->
                arr.put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("apiUrl", p.apiUrl)
                    put("protocol", p.protocol)
                    put("apiKey", p.apiKey)
                    put("models", JSONArray(p.models))
                })
            }
            file(ctx).writeText(arr.toString(2))
            all
        } catch (t: Throwable) {
            Log.e(TAG, "写入提供方失败", t)
            null
        }
    }
}
