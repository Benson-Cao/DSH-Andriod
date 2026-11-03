package com.dsh.harness.webview;

/**
 * 移动端布局注入（对齐官方 {@code DshWebView} 的 {@code LayoutInjector.getLayoutCSS()}）。
 *
 * <p>原则（来自 WebView 手册 6.2）：<b>颜色一律引用官方 token，不写死字面量</b>。
 * 0.2.x web 已自带响应式布局与暗色主题变量，所以这里<b>不做任何主题/配色覆盖</b>，
 * 只补移动端 WebView 的通病修复，避免影响 dsh 自身渲染：
 *
 * <ol>
 *   <li>视口高度：用 {@code 100dvh} 兜底 {@code 100vh} —— 移动端浏览器地址栏伸缩会让
 *       {@code 100vh} 大于可见区，导致底部工具栏被顶到屏幕外（真机高频问题）。</li>
 *   <li>刘海/手势条：用 {@code env(safe-area-inset-*)} 给根容器留安全区，避免内容被挖孔或
 *       系统手势条遮挡。</li>
 *   <li>横向溢出：{@code overflow-x:hidden} 防止某些窄屏下的横向滚动条。</li>
 * </ol>
 *
 * <p>所有规则都用 dsh 已有的 CSS 变量（如 {@code --ds-surface}）做背景/文字兜底，
 * 取不到变量时退到中性灰，<b>不引入任何硬编码品牌色</b>。
 */
public final class LayoutInjector {

    private LayoutInjector() { }

    /** 返回要注入的布局 CSS 字符串（不含 {@code <style>} 标签）。 */
    public static String getLayoutCss() {
        return ""
            // —— 视口高度：dvh 兜底 vh（修复移动端地址栏伸缩导致的底部被顶出屏幕）——
            + ":root{"
            + "  --dsh-vh:100vh;"
            + "}"
            + "@supports (height:100dvh){"
            + "  :root{--dsh-vh:100dvh;}"
            + "}"
            // 让 dsh 的根布局填满可用高度（它内部常用 100vh）
            + "html,body{"
            + "  height:var(--dsh-vh,100vh);"
            + "  margin:0;"
            + "  overflow-x:hidden;"
            + "}"
            // —— 安全区：刘海屏/手势条，引用 dsh 自己的背景变量做兜底 ——
            + "#root,"
            + ".__dsh_root,"
            + ".dsh-root{"
            + "  padding-top:env(safe-area-inset-top);"
            + "  padding-bottom:env(safe-area-inset-bottom);"
            + "  padding-left:env(safe-area-inset-left);"
            + "  padding-right:env(safe-area-inset-right);"
            + "  box-sizing:border-box;"
            + "}"
            // —— 输入控件不被手势条/键盘顶飞：给可滚动容器一个底部安全边 ——
            + "[data-dsh-scroll],"
            + ".dsh-scroll{"
            + "  padding-bottom:calc(env(safe-area-inset-bottom) + 8px);"
            + "}"
            // —— 抽屉/弹层不被挖孔遮挡：顶部安全区 ——
            + "[role=\"dialog\"],"
            + ".dsh-dialog,"
            + ".dsh-drawer{"
            + "  padding-top:env(safe-area-inset-top);"
            + "}";
    }

    /**
     * 把 CSS 包成可直接 {@code evaluateJavascript} 的注入脚本：
     * 幂等 —— 重复注入不会重复添加（用固定 id 标记）。
     */
    public static String injectScript() {
        String css = getLayoutCss().replace("'", "\\'");
        return "(function(){"
            + "try{"
            + "  if(document.getElementById('dsh-layout-inject'))return;"
            + "  var s=document.createElement('style');"
            + "  s.id='dsh-layout-inject';"
            + "  s.setAttribute('data-dsh-layout','1');"
            + "  s.textContent='" + css + "';"
            + "  (document.head||document.documentElement).appendChild(s);"
            + "}catch(e){}"
            + "})()";
    }
}
