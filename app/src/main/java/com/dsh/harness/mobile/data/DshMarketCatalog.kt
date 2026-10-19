package com.dsh.harness.mobile.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * dsh 插件市场的真实数据源（v24 新增）。
 *
 * ## 链路事实（2026-10-19 调研自 dshmarket@1.66.42 源码）
 * 设计稿 S21 说"需先装市场本体"，那是 dsh **web UI** 的市场面板
 * （dshmarket 包 `client.platform: "web"`）。本 App 是 Compose 复刻，
 * 不依赖那个包 —— 直接拉市场自己的精选目录即可拿到真实数据：
 *
 * 1. `GET https://registry.npmmirror.com/dsh-plugin-catalog`
 *    → dist-tags.latest（china 路由第一跳，与 dshmarket regions.js 一致：
 *       catalog 做成 npm 包正是因为 GitHub Pages 主机国内不可达）
 * 2. `GET .../dsh-plugin-catalog-<v>.tgz`（约 2.4MB）
 * 3. 解开 tar.gz → `package/index.json`（约 2.3MB，2953 条精选）
 * 4. 兜底：registry.npmjs.org 同路径 → 都失败则抛错（UI 给重试）
 *
 * index.json 结构（format 1）：
 * - metadata.categories：23 个分类的双语标题（ui/theme/model/session/...）
 * - plugins[]：name / title / description{zh,en} / homepage / repository
 *   / metadata.category / versions[0].version + publishTimestamp
 *
 * 星标与下载量只在 7.2MB 的 plugins.json 里，本 App 不拉 ——
 * 没有的数据不显示（诚实原则）。
 */
object DshMarketCatalog {

    private const val TAG = "DshMarketCatalog"

    /** china 路由：腾讯云 npm 镜像（dshmarket regions.js: NPM_CHINA 的国内可达等价物） */
    private const val NPM_CHINA = "https://registry.npmmirror.com"

    /** global 兜底 */
    private const val NPM_GLOBAL = "https://registry.npmjs.org"

    private const val PKG = "dsh-plugin-catalog"

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    /** 一条市场条目（来自 index.json） */
    data class Entry(
        /** npm 包名，安装命令用它：dsh plugin --profile acp add <name> */
        val name: String,
        /** 展示名（title 字段，缺失时回落 name） */
        val title: String,
        val descZh: String,
        val descEn: String,
        val homepage: String,
        val repo: String,
        /** 具名分类 key（ui/theme/model/...），空 = 未分类 */
        val category: String,
        val version: String,
        /** 发布时间（epoch 秒）；0 = 未知 */
        val publishedAt: Long,
    )

    /** 整个精选目录 */
    data class Catalog(
        /** key -> (中文标题, 英文标题) */
        val categories: Map<String, Pair<String, String>>,
        val entries: List<Entry>,
    )

    /**
     * 拉取并解析精选目录。IO 线程执行；失败抛异常（调用方转错误态）。
     * 先 china 后 global，两条 registry 都试过才放弃。
     */
    suspend fun load(): Catalog = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        for (registry in listOf(NPM_CHINA, NPM_GLOBAL)) {
            try {
                return@withContext loadFrom(registry)
            } catch (t: Throwable) {
                Log.w(TAG, "拉取市场目录失败（$registry）", t)
                lastError = t
            }
        }
        throw lastError ?: IllegalStateException("市场目录拉取失败")
    }

    private fun loadFrom(registry: String): Catalog {
        // 1) latest 版本号
        val meta = JSONObject(httpGet("$registry/$PKG"))
        val latest = meta.getJSONObject("dist-tags").getString("latest")
        Log.i(TAG, "市场目录版本：$latest（$registry）")

        // 2) tarball
        val tgz = httpGetBytes("$registry/$PKG/-/$PKG-$latest.tgz")

        // 3) 解 tar.gz，取 package/index.json
        val indexJson = extractFromTarGz(tgz, "package/index.json")
            ?: throw IllegalStateException("tarball 内没有 package/index.json")

        // 4) 解析
        return parseIndex(indexJson)
    }

    /** 解析 index.json（format 1） */
    internal fun parseIndex(text: String): Catalog {
        val root = JSONObject(text)
        val cats = HashMap<String, Pair<String, String>>()
        root.optJSONObject("metadata")?.optJSONObject("categories")?.let { c ->
            for (key in c.keys()) {
                val t = c.optJSONObject(key)?.optJSONObject("title")
                val zh = t?.optString("zh").orEmpty().ifEmpty { key }
                val en = t?.optString("en").orEmpty().ifEmpty { key }
                cats[key] = zh to en
            }
        }
        val arr = root.optJSONArray("plugins") ?: throw IllegalStateException("index.json 无 plugins 数组")
        val entries = ArrayList<Entry>(arr.length())
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            val name = p.optString("name")
            if (name.isEmpty()) continue
            val desc = p.optJSONObject("description")
            val meta = p.optJSONObject("metadata")
            val cat = meta?.optString("category").orEmpty()
            val ver = p.optJSONArray("versions")?.optJSONObject(0)
            entries += Entry(
                name = name,
                title = p.optString("title").ifEmpty { name },
                descZh = desc?.optString("zh").orEmpty(),
                descEn = desc?.optString("en").orEmpty(),
                homepage = p.optString("homepage"),
                repo = p.optString("repository").ifEmpty { p.optString("homepage") },
                category = cat,
                version = ver?.optString("version").orEmpty(),
                publishedAt = ver?.optLong("publishTimestamp") ?: 0L,
            )
        }
        Log.i(TAG, "市场目录解析：${entries.size} 条 / ${cats.size} 分类")
        return Catalog(cats, entries)
    }

    /**
     * 从 tar.gz 字节流中取一个成员的内容。
     * 自写简易 USTAR 解析（Android 无 commons-compress，也不想引依赖）：
     * 512 字节头，name@0..100、size@124..136（八进制），内容按 512 补齐。
     */
    internal fun extractFromTarGz(tgz: ByteArray, member: String): String? {
        GZIPInputStream(tgz.inputStream()).use { gz ->
            val header = ByteArray(512)
            while (true) {
                if (!readFully(gz, header)) return null
                // 全零头 = 归档结束
                if (header.all { it == 0.toByte() }) return null
                val name = String(header, 0, 100).trim('\u0000', ' ', '\n')
                val sizeStr = String(header, 124, 136).trim('\u0000', ' ', '\n')
                val size = if (sizeStr.isEmpty()) 0 else sizeStr.toLong(8)
                val padded = ((size + 511) / 512) * 512
                if (name == member) {
                    val out = ByteArrayOutputStream()
                    var left = size
                    val buf = ByteArray(16384)
                    while (left > 0) {
                        val n = gz.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        left -= n
                    }
                    // 跳过补齐字节
                    var skip = padded - size
                    while (skip > 0) {
                        val n = gz.skip(minOf(skip, 16384))
                        if (n <= 0) break
                        skip -= n
                    }
                    return out.toString("UTF-8")
                }
                var skip = padded
                while (skip > 0) {
                    val n = gz.skip(minOf(skip, 16384))
                    if (n <= 0) break
                    skip -= n
                }
            }
        }
    }

    private fun readFully(input: InputStream, buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) return off == 0 && buf.isEmpty()
            off += n
        }
        return true
    }

    private fun httpGet(url: String): String = String(httpGetBytes(url))

    private fun httpGetBytes(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Accept", "*/*")
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code for $url")
            }
            return conn.inputStream.use { it.readBytesCompat() }
        } finally {
            conn.disconnect()
        }
    }

    private fun InputStream.readBytesCompat(): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16384)
        while (true) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
