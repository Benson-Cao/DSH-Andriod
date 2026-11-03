package com.dsh.harness.mobile.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dsh.harness.mobile.data.DshSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * dsh 的「主题」偏好（设置 → 通用设置 → 外观 → 主题）。
 *
 * 为什么需要这个对象：设置页宿主 [com.dsh.harness.mobile.SettingsActivity] 原来
 * 只按系统 uiMode 决定深浅（`Configuration.UI_MODE_NIGHT_YES`），完全没读
 * `ui-theme.preference` —— 于是用户在设置里点「深色」时，设置页自己的配色不动，
 * 看起来就是「深色模式不生效」。
 *
 * 取值与 dsh web 端一致：`system` / `light` / `dark`。null 表示还没读到
 * （首帧），调用方按系统值处理，读到后再重组，不会闪错。
 */
object ThemePref {
    /** 当前偏好；null = 尚未从 dsh 设置服务读到。 */
    var current: String? by mutableStateOf(null)
        private set

    /** 按偏好决定是否用深色；[systemDark] 是系统当前的 uiMode 判定。 */
    fun resolveDark(systemDark: Boolean): Boolean = when (current) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }

    /** 从 dsh 设置服务读取一次（失败静默，保持 null → 跟随系统）。 */
    suspend fun load(ctx: Context) {
        val pref = withContext(Dispatchers.IO) {
            try {
                DshSettingsStore.get(ctx, "ui-theme")?.value?.optString("preference")
            } catch (_: Throwable) {
                null
            }
        }
        if (!pref.isNullOrBlank()) current = pref
    }

    /**
     * 用户在设置页改了主题：先乐观更新，界面立刻变色；
     * 写入本身由设置页的 settings/update 负责。
     */
    fun onUserChanged(pref: String) {
        current = pref
    }
}
