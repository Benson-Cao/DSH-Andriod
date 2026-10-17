package com.dsh.harness.mobile.data

import android.content.Context
import android.content.SharedPreferences

/**
 * App 私有偏好（极薄一层）。目前只存一个「是否已走过首次引导」标记，
 * 用于决定冷启动是进 S24 首次配置还是直接进首页。
 * 没有接 DataStore —— 就一个布尔，SharedPreferences 足够，不引新依赖。
 */
object DshPrefs {
    private const val NAME = "dsh_mobile"
    private const val K_ONBOARDED = "onboarded"
    /** S8 工具栏「时长」开关：开启后时间概览条形按真实耗时绘制，且选择会被记住 */
    private const val K_SHOW_DURATION = "show_duration"
    /** S8 工具栏「轮次」开关：默认展开全部轮次 */
    private const val K_EXPAND_ROUNDS = "expand_rounds"
    /** S8 工具栏「调用」开关：默认展开工具调用行 */
    private const val K_SHOW_CALLS = "show_calls"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun onboarded(ctx: Context): Boolean = sp(ctx).getBoolean(K_ONBOARDED, false)

    fun setOnboarded(ctx: Context, v: Boolean = true) {
        sp(ctx).edit().putBoolean(K_ONBOARDED, v).apply()
    }

    fun showDuration(ctx: Context): Boolean = sp(ctx).getBoolean(K_SHOW_DURATION, false)
    fun setShowDuration(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean(K_SHOW_DURATION, v).apply()
    }

    fun expandRounds(ctx: Context): Boolean = sp(ctx).getBoolean(K_EXPAND_ROUNDS, true)
    fun setExpandRounds(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean(K_EXPAND_ROUNDS, v).apply()
    }

    fun showCalls(ctx: Context): Boolean = sp(ctx).getBoolean(K_SHOW_CALLS, true)
    fun setShowCalls(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean(K_SHOW_CALLS, v).apply()
    }
}
