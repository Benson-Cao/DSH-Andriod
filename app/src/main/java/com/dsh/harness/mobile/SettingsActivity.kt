package com.dsh.harness.mobile

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import com.termux.R
import com.dsh.harness.mobile.ui.screens.AppSettingsScreen
import com.dsh.harness.mobile.ui.theme.DshTheme
import com.dsh.harness.mobile.ui.theme.ThemePref

/**
 * App 原生设置页宿主（v31）。
 *
 * 与主界面（MainActivity/WebView）**相互独立**：不在 WebView 上叠视图，
 * 而是一个轻量透明转场的独立 Activity —— 返回键、生命周期、SAF 授权回执
 * 都归自己管，不碰 MainActivity 的返回键三层闸门状态机。
 *
 * 入口：桌面长按图标快捷方式「App 设置」（res/xml/shortcuts.xml）。
 *
 * 深浅色：跟随系统（Theme.AppCompat.DayNight + uiMode 判定）。
 * WebView 内的深浅由 dsh web 自管，App 层不越权。
 */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // v30.44：深浅色不再只看系统 —— 设置里有「主题（跟随系统 / 浅色 / 深色）」，
            // 之前这里完全没读 ui-theme.preference，所以点「深色」设置页毫无反应。
            // 现在：preference 优先，未读到（首帧）时回落系统 uiMode。
            val cfg = LocalConfiguration.current
            val systemDark = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            // v30.45：只读一次不够 —— Activity 刚起来时 dsh 引擎可能还没就绪
            //（describe 返回 null），ThemePref 会永远停在 null 而回落系统值，
            // 表现就是「设置里选的是深色，页面却是浅色」。这里退避重试几轮。
            LaunchedEffect(Unit) {
                repeat(8) { attempt ->
                    ThemePref.load(applicationContext)
                    if (ThemePref.current != null) return@LaunchedEffect
                    kotlinx.coroutines.delay(600L + attempt * 200L)
                }
            }
            DshTheme(dark = ThemePref.resolveDark(systemDark)) {
                AppSettingsScreen(onClose = { finish() })
            }
        }
        // 进入转场：从右滑入（API 34+ 系统自动沿用 windowAnimation，低版本显式指定）
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    override fun finish() {
        super.finish()
        // 退出转场：向右滑出，与进入方向对称
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
