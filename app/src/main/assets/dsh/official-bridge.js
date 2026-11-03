/**
 * 官方 WebView 外壳（v34）。
 *
 * 设计约束来自《WebView 完整技术手册》§6：**官方 UI 样式 100% 保留，只改布局**。
 * 而用户进一步要求「首页与官方 WebView 一模一样」，所以本脚本**一行 CSS 都不注入**，
 * 只做两件与样式无关的事：
 *
 *   ① 设置入口拦截：点官方侧栏/导航里的「设置」→ 拉起原生 Compose 设置页
 *      （网页设置列表页不再出现；Compose 侧已有 general/models/plugins/presets 四页）。
 *   ② 返回键状态上报：只有当页面上存在**可见的** role=dialog 时才上报
 *      setBackHandled(true)，让原生返回走 JS；否则原生按 history.back() 处理，
 *      与浏览器行为一致。
 *
 * ⚠️ 不再包含（全部删除，勿再加回）：
 *   · 自定义顶栏/汉堡按钮、抽屉样式、卡片/列表重绘、市场页样式；
 *   · 任何颜色字面量或主题覆盖（手册要求颜色引用 --dsw-alias-* token）；
 *   · 首页布局重排（官方 0.2.x 自带响应式布局）。
 */
(function () {
  if (window.__dshOfficialBridge) { return; }
  window.__dshOfficialBridge = 1;

  function norm(t) { return String(t || '').replace(/\s+/g, '').trim(); }

  /** 判断元素是不是官方「设置」入口（图标按钮通常只有 aria-label/title）。 */
  function isSettings(el) {
    var al = (el.getAttribute('aria-label') || '') + ' ' + (el.getAttribute('title') || '');
    if (/设置|settings/i.test(al)) { return true; }
    var t = norm(el.textContent);
    return t === '设置' || t === 'Settings';
  }

  // 捕获阶段拦截，阻止官方面板打开（面板一旦打开就是被用户否掉的网页设置页）。
  document.addEventListener('click', function (e) {
    var n = e.target, hop = 0;
    while (n && n.nodeType === 1 && hop < 6) {
      if (isSettings(n)) {
        e.preventDefault();
        e.stopPropagation();
        try {
          if (window.dshNative && window.dshNative.openAppSettings) {
            window.dshNative.openAppSettings();
          }
        } catch (err) { /* 桥未就绪：什么都不做，绝不抛到页面 */ }
        return;
      }
      n = n.parentElement;
      hop++;
    }
  }, true);

  /** 当前是否有用户可见的对话框（几何 + computed style 双证据，避免误判）。 */
  function visibleDialog() {
    var list = document.querySelectorAll('[role="dialog"]');
    for (var i = 0; i < list.length; i++) {
      var el = list[i];
      try {
        var r = el.getBoundingClientRect();
        if (r.width < 40 || r.height < 40) { continue; }
        var st = window.getComputedStyle(el);
        if (st.visibility === 'hidden' || st.display === 'none') { continue; }
        if (parseFloat(st.opacity || '1') < 0.05) { continue; }
        return el;
      } catch (e) { /* 过渡窗口量不出来：按不可见处理，下一轮再试 */ }
    }
    return null;
  }

  var lastReported = null;
  function syncBackState() {
    var now = !!visibleDialog();
    if (now === lastReported) { return; }
    lastReported = now;
    try {
      if (window.dshNative && window.dshNative.setBackHandled) {
        window.dshNative.setBackHandled(now);
      }
    } catch (e) { }
  }

  // 原生返回键在 jsHandled=true 时调用它。
  window.__dshBack = function () {
    if (visibleDialog()) {
      try { document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })); } catch (e) { }
      return;
    }
    try { window.history.back(); } catch (e) { }
  };

  /* ---- 主题跟随（v30.38）------------------------------------------------
   * dsh 的深浅色存在 ui-theme.preference 里。用户在原生设置页改完返回时，
   * WebView 不会 reload，原生侧的 probeTheme 也不会再跑 → 系统栏停在旧颜色
   * （真机实测：dsh 已转深色，状态栏仍浅色，顶部一条白边）。
   * 这里持续采样页面的真实深浅色，**变化才过桥**（JS 侧去重，不会打爆桥）。
   *
   * 判据优先级：权威属性 > body 背景亮度。
   *   ① body/html 的 data-ds-dark-theme 是 dsh 官方深色标记（权威信号，
   *      详见 DshWebViewClient.probeTheme 的历史注释）；
   *   ② 没有标记时按背景亮度算 —— 比直接用 prefers-color-scheme 准，因为
   *      「dsh 选浅色 + 系统深色」时 matchMedia 会返回 dark，但页面其实是浅的。
   */
  var lastDark = null;

  function themeIsDark() {
    var h = document.documentElement, b = document.body;
    if (!h || !b) { return null; }
    if (b.hasAttribute('data-ds-dark-theme') || h.hasAttribute('data-ds-dark-theme')
        || h.classList.contains('dark') || b.classList.contains('dark')) { return true; }
    if (b.hasAttribute('data-ds-light-theme') || h.hasAttribute('data-ds-light-theme')) { return false; }
    try {
      var bg = window.getComputedStyle(b).backgroundColor || '';
      var m = /rgba?\((\d+)[,\s]+(\d+)[,\s]+(\d+)/.exec(bg);
      if (!m) { return null; }
      var lum = 0.299 * parseInt(m[1], 10) + 0.587 * parseInt(m[2], 10) + 0.114 * parseInt(m[3], 10);
      return lum < 128;
    } catch (e) { return null; }
  }

  function syncTheme() {
    var d = themeIsDark();
    if (d === null || d === lastDark) { return; }
    lastDark = d;
    try {
      if (window.dshNative && window.dshNative.applyDshTheme) {
        window.dshNative.applyDshTheme(d);
      }
    } catch (e) { /* 桥未就绪：下一轮再试 */ }
  }

  /* ---- 窄屏抽屉：主内容整体右移出屏，而不是被压扁（v30.39）--------------
   * 现象（用户截图实锤）：抽屉展开时 dsh 把侧栏撑到约 80% 视口，主内容只剩
   * 几十 px，标题被压成竖排单字（「探/索/未/至/之/境」）。
   *
   * 为什么不改 grid-template-columns：那一列是 dsh 用 JS 算出来写进内联样式的
   * （client.js 里有 `style.gridTemplateColumns` 后接 settle()），我们改完会被
   * 它下一帧覆盖。改成给主内容设整屏宽 + translateX：布局宽度由我们固定，
   * 位移只影响绘制，不参与 dsh 的宽度计算，互不打架。
   */
  var layoutMoved = false;

  function syncLayout() {
    try {
      var side = document.querySelector('[class*="_sidebarCol"]');
      var center = document.querySelector('[class*="_centerCol"]');
      if (!side || !center) { return; }
      var vw = window.innerWidth || document.documentElement.clientWidth || 360;
      var sideW = side.getBoundingClientRect().width;
      var open = sideW > vw * 0.5;   // 侧栏超过半屏 = 抽屉展开
      if (open) {
        center.style.setProperty('width', vw + 'px', 'important');
        center.style.setProperty('min-width', vw + 'px', 'important');
        center.style.setProperty('flex-shrink', '0', 'important');
        center.style.setProperty('transition', 'transform .2s cubic-bezier(.2,.7,.3,1)', 'important');
        center.style.setProperty('transform', 'translateX(' + sideW + 'px)', 'important');
        layoutMoved = true;
      } else if (layoutMoved) {
        ['width', 'min-width', 'flex-shrink', 'transition', 'transform'].forEach(function (p) {
          center.style.removeProperty(p);
        });
        layoutMoved = false;
      }
    } catch (e) { }
  }

  /* ---- 目录选择器：屏蔽 dsh 运行时内部目录（v30.39）----------------------
   * 工作区选择器以 dsh home 为根，而 home 里装着 dsh 自己的 node_modules
   * 与 SAF 桥目录，用户会看到并误选（截图里被红框标出）。官方 picker 只把
   * 点开头的算隐藏目录（host-directory-picker-browse: hidden = name.startsWith('.')），
   * 没有 ignore 配置项，只能在 UI 层挡掉。
   *
   * 只动 role=dialog 里的行，且文本必须**精确等于**这些名字 —— 用户自己
   * 工作区里真有同名目录时也不会误伤（那时它不在这个对话框里）。
   */
  var INTERNAL = { 'node_modules': 1, '.dsh': 1, '.cache': 1, '.tmp': 1 };

  function hideInternalDirs() {
    try {
      var dlgs = document.querySelectorAll('[role="dialog"]');
      for (var i = 0; i < dlgs.length; i++) {
        var d = dlgs[i];
        if (d.getBoundingClientRect().width < 120) { continue; }
        var leaves = d.querySelectorAll('*');
        for (var j = 0; j < leaves.length; j++) {
          var el = leaves[j];
          if (el.children.length !== 0) { continue; }          // 只看叶子，避免命中容器
          if (!INTERNAL[(el.textContent || '').trim()]) { continue; }
          var row = el, k = 0;
          while (row.parentElement && k < 4) {                  // 向上找整行
            row = row.parentElement; k++;
            var cs = window.getComputedStyle(row);
            if (cs.cursor === 'pointer' || row.getAttribute('role') === 'button') { break; }
          }
          if (row.style.display !== 'none') { row.style.display = 'none'; }
        }
      }
    } catch (e) { }
  }

  // 与返回状态上报共用同一个心跳，避免再起一个定时器（移动端省电）。
  setInterval(function () { syncBackState(); syncTheme(); syncLayout(); hideInternalDirs(); }, 500);
  syncBackState();
  setTimeout(syncTheme, 400);
})();
