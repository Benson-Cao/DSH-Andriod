package com.dsh.harness.mobile.ui.data

import android.content.Context
import com.dsh.harness.DshSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 遥控电脑集群的「宿主状态」数据模型 + 加载器。
 *
 * 数据来源：NAS 上由桌面端发布（或手机端回写）的 [DshSync.REMOTE_HOST_PATH]
 * （dsh-phone/remote/host.json）。手机端只读，桌面端负责写。
 *
 * 当 NAS 未配置、或远端没有该文件、或解析失败时 [load] 返回 null —— 调用方应回落到
 * 内置演示数据，并标注「未从 NAS 读取到宿主状态」。
 */

/** 单条任务（运行 / 待审批 / 排队）。 */
data class RemoteTask(
    val id: String,
    val title: String,
    val subtitle: String,
    /** running | pending_approval | queued */
    val state: String,
    /** 当前宿主标签：desktop（电脑）/ local（本机） */
    val hostTag: String,
    val progress: Float = 0f,
    val note: String = "",
    val waiting: String = "",
    val position: Int = 0,
    val submittedAt: String = "",
    /** 事件流（S55 / S59 的实时尾随）：(色键, 文案) */
    val events: List<Pair<String, String>> = emptyList(),
    val canHandoff: Boolean = false,
    val canPause: Boolean = false,
)

/** 宿主（电脑）整体状态 + 任务列表。 */
data class RemoteHost(
    val online: Boolean,
    val name: String,
    val lockOwner: String,
    val lockTask: String,
    val lockHeartbeatAt: Long,
    val lockTimeoutSec: Int,
    val readOnly: Boolean,
    val heartbeatAt: Long,
    val tasks: List<RemoteTask>,
)

object RemoteHostLoader {
    /** 远端宿主状态 JSON 路径（与桌面发布脚本约定一致）。 */
    const val REMOTE_PATH = DshSync.REMOTE_HOST_PATH

    /** 后台线程读取并解析；任何失败返回 null。 */
    suspend fun load(ctx: Context): RemoteHost? = withContext(Dispatchers.IO) {
        val text = DshSync.readRemoteText(ctx, REMOTE_PATH) ?: return@withContext null
        try {
            parse(text)
        } catch (t: Throwable) {
            null
        }
    }

    private fun parse(text: String): RemoteHost {
        val root = JSONObject(text)
        val h = root.optJSONObject("host") ?: JSONObject()
        val tasks = mutableListOf<RemoteTask>()
        val arr = root.optJSONArray("tasks") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val t = arr.optJSONObject(i) ?: continue
            val ev = mutableListOf<Pair<String, String>>()
            val evArr = t.optJSONArray("events")
            if (evArr != null) {
                for (j in 0 until evArr.length()) {
                    val e = evArr.optJSONObject(j) ?: continue
                    ev.add(e.optString("c", "") to e.optString("l", ""))
                }
            }
            tasks.add(
                RemoteTask(
                    id = t.optString("id", "t$i"),
                    title = t.optString("title", ""),
                    subtitle = t.optString("subtitle", ""),
                    state = t.optString("state", "running"),
                    hostTag = t.optString("host", "desktop"),
                    progress = t.optDouble("progress", 0.0).toFloat(),
                    note = t.optString("note", ""),
                    waiting = t.optString("waiting", ""),
                    position = t.optInt("position", 0),
                    submittedAt = t.optString("submittedAt", ""),
                    events = ev,
                    canHandoff = t.optBoolean("canHandoff", false),
                    canPause = t.optBoolean("canPause", false),
                )
            )
        }
        return RemoteHost(
            online = h.optBoolean("online", true),
            name = h.optString("name", "desktop"),
            lockOwner = h.optString("lockOwner", ""),
            lockTask = h.optString("lockTask", ""),
            lockHeartbeatAt = h.optLong("lockHeartbeatAt", 0L),
            lockTimeoutSec = h.optInt("lockTimeoutSec", 300),
            readOnly = h.optBoolean("readOnly", false),
            heartbeatAt = h.optLong("heartbeatAt", 0L),
            tasks = tasks,
        )
    }
}
