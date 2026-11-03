package com.dsh.harness.webview;

import android.util.Log;
import android.webkit.WebView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 官方外壳注入器（v34）。
 *
 * <p>取代 r152 之前的 {@code MobileTuning.apply()}（mobile.css / mobile.js /
 * market.css 四件套）。按《WebView 完整技术手册》§6 与用户要求「首页与官方
 * WebView 一模一样」：**不注入任何样式/布局**，只加载
 * {@code assets/dsh/official-bridge.js}（设置入口拦截 + 返回状态上报）。
 *
 * <p>为什么不再用 evaluateJavascript 注入 CSS：手册要求颜色引用官方 token、
 * 只改布局；而现场反馈是自定义外壳把官方页面改得面目全非（自定义汉堡、
 * 抽屉、行样式）。最小注入面 = 最接近官方。
 */
public final class OfficialShell {

    private static final String TAG = "OfficialShell";
    private static final String ASSET = "dsh/official-bridge.js";

    private static volatile String cached;

    private OfficialShell() { }

    /** 幂等：重复调用只是重复执行同一段脚本（脚本自带 __dshOfficialBridge 守卫）。 */
    public static void inject(WebView view) {
        if (view == null) { return; }
        try {
            String js = load(view);
            if (js == null) { return; }
            view.evaluateJavascript(js, null);
        } catch (Throwable t) {
            // 注入是体验增强，绝不影响 dsh 主流程
            Log.w(TAG, "[dsh] official bridge inject 失败: " + t.getMessage());
        }
    }

    private static String load(WebView view) {
        String s = cached;
        if (s != null) { return s; }
        try (InputStream in = view.getContext().getAssets().open(ASSET)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) { bos.write(buf, 0, n); }
            s = new String(bos.toByteArray(), StandardCharsets.UTF_8);
            cached = s;
            return s;
        } catch (Throwable t) {
            Log.w(TAG, "[dsh] 读取 " + ASSET + " 失败: " + t.getMessage());
            return null;
        }
    }
}
