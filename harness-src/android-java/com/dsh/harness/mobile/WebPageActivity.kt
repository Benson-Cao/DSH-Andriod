package com.dsh.harness.mobile

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.dsh.harness.mobile.ui.theme.DshTheme
import com.dsh.harness.mobile.ui.theme.Space
import com.dsh.harness.mobile.ui.theme.ThemePref
import com.dsh.harness.mobile.ui.theme.Type

/**
 * App 内网页容器（v34）。
 *
 * 为什么需要它：账户页的「用量明细」「充值」原来一律 `ACTION_VIEW` 丢给系统浏览器 ——
 * 用户点完就跳出 App，回来还得手动切回来。官方其实是在应用内侧边栏打开的
 * （`ui-chat.linkOpening` 的 `sidebar` 语义）。这里给一个轻量的原生容器：
 * 顶部一条返回栏 + 全屏 WebView，既满足「内嵌」，也不引 CustomTabs 依赖。
 *
 * 只做只读浏览：不注入 JS bridge，不开启文件访问。
 */
class WebPageActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_TITLE = "extra_title"

        fun intent(ctx: Context, url: String, title: String): Intent =
            Intent(ctx, WebPageActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)
                // 调用方传的是 application context（设置页里拿不到 Activity），
                // 不加 NEW_TASK 会抛 "Calling startActivity() from outside of an Activity context"，
                // 然后被兜底逻辑降级成跳系统浏览器 —— 内嵌就废了。
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val url = intent?.getStringExtra(EXTRA_URL).orEmpty()
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "网页" }
        setContent {
            val cfg = LocalConfiguration.current
            val systemDark = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            DshTheme(dark = ThemePref.resolveDark(systemDark)) {
                WebPageScreen(url = url, title = title, onClose = { finish() })
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebPageScreen(url: String, title: String, onClose: () -> Unit) {
    val c = DshTheme.colors
    val ctx = LocalContext.current
    var progress by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var webRef by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        val w = webRef
        if (w != null && w.canGoBack()) w.goBack() else onClose()
    }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        // 顶栏：返回 + 标题（与设置页同一套视觉语言）
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.clickable { onClose() }.padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text("‹", fontSize = 26.sp, color = c.ink)
            }
            Text(
                title,
                style = Type.rowTitle,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        // 加载进度：细线，不占位（loaded 后自动消失）
        if (loading) {
            LinearProgressIndicator(
                progress = { if (progress <= 0) 0.08f else progress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = c.brand,
                trackColor = c.line2,
            )
        } else {
            Box(Modifier.fillMaxWidth().height(2.dp).background(c.line2))
        }

        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f).navigationBarsPadding(),
            factory = { viewCtx ->
                WebView(viewCtx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(0x00000000)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                            loading = newProgress < 100
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(v: WebView?, u: String?, favicon: Bitmap?) {
                            loading = true
                        }

                        override fun onPageFinished(v: WebView?, u: String?) {
                            loading = false
                        }
                    }
                    loadUrl(url)
                    webRef = this
                }
            },
            update = { /* 只在首次创建时加载，避免重组重载 */ },
        )
    }
}
