package com.dsh.harness.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dsh.harness.mobile.ui.DshApp
import com.dsh.harness.mobile.ui.Route

/**
 * DeepSeek Harness 原生 UI 入口。
 *
 * 这一层是**纯 Compose 的 18 屏**（对齐 v10 设计稿），
 * 与 termux 的 dsh 运行时并存：dsh 在本地跑 node 服务（端口 3080 + token），
 * 原生 UI 负责界面，后续通过 ACP / HTTP 接数据（见 DshRuntime）。
 */
class ComposeProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DshApp(startRoute = Route.S3Home)
        }
    }
}