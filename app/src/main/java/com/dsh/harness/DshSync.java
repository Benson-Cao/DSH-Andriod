package com.dsh.harness;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 把 dsh 的**会话/工作区数据**同步到 NAS。
 *
 * ## 支持的传输协议
 *
 * - WebDAV：飞牛 fnOS 的 WebDAV（{@link DshWebDavClient}，纯 JDK，零第三方库）。
 * - SFTP：基于 SSH 的文件传输（{@link DshSftpClient}，依赖 JSch）。
 * - SMB：Windows/NAS 的 SMB/CIFS 共享（{@link DshSmbClient}，依赖 jcifs-ng）。
 *
 * 三者共用同一套同步与冲突策略，只是「传输层」不同。选择哪种协议由
 * {@code type} 配置字段决定，{@link #createTransport} 据此返回对应的
 * {@link NasTransport} 实现。
 *
 * ## 同步什么、不同步什么（这是本类最重要的设计约束）
 *
 * 同步：{@code .dsh/storages/} 下的会话索引与工作区数据 —— 这是跨端该共享的部分。
 * **不同步**：{@code .dsh/profiles/}（含 node_modules / cordis.yml / 平台二进制）。
 *
 * <p>⚠️ 绝对不能整个 {@code .dsh} 目录同步：那些文件是**平台相关**的
 *（Termux 的 {@code .so} 与 Windows 的 {@code .node} 根本无法互换），
 * 整目录同步会把两端的插件环境互相破坏，且症状隐蔽（"装好的插件突然失效"）。
 *
 * ## 冲突策略：保留双份，不静默覆盖
 *
 * 判定：远端存在同名文件，且**本地与远端内容不同**，且本地 mtime 比上次同步新
 * → 说明两边都改过。此时把本地另存为 {@code xxx.conflict-<设备>-<时间戳>.json}，
 * 再用远端版本作为最新同步下去。
 *
 * <p>这样做的理由：会话数据丢一份的代价远高于多留一个文件。用户自己合并即可，
 * 而静默覆盖（按 mtime 决胜）会让人**在几天后才发现丢了对话**。
 *
 * ## 为什么不用「目录直接指向 NAS」
 *
 * Android 应用无法把网络文件系统挂成普通目录（需 root；SAF 不支持 SMB/NFS），
 * 详见 {@code docs/nas-sync-feasibility.md}。所以数据留本地，只同步选定文件。
 */
public final class DshSync {

    private static final String TAG = "DshSync";
    private static final String PREFS = "dsh_sync";

    /** 传输层接口：三种协议（WebDAV / SFTP / SMB）共用同一组动作。 */
    public interface NasTransport {
        /** 连通性 + 鉴权探测。能连且凭证正确返回 true。永不抛异常。 */
        boolean ping();
        /** 递归创建远端目录（已存在视为成功）。 */
        boolean mkdirs(String relPath);
        /** 上传本地文件到远端 relPath。 */
        boolean put(String relPath, File local);
        /** 下载远端 relPath 到本地 dest（覆盖）。 */
        boolean get(String relPath, File dest);
        /** 列出远端某目录的直接子项文件名（不含路径）。失败返回空集合。 */
        java.util.Set<String> list(String relPath);
        /** 远端文件最后修改时间（毫秒）；取不到返回 0（调用方据此放弃覆盖）。 */
        long lastModified(String relPath);
        /** 释放底层连接。 */
        void close();
    }

    /** 远端根目录（相对各协议的基础连接，如 <share>/dsh-phone/storages）。 */
    static final String REMOTE_ROOT = "dsh-phone/storages";
    /** 本地源目录（相对 DshBootstrap.home()）。 */
    private static final String LOCAL_REL = ".dsh/storages";
    /** 冲突副本后缀。 */
    private static final String CONFLICT_MARK = ".conflict-";

    /** 同步结果，供 UI 展示。 */
    public static final class Result {
        public boolean ran = false;          // 是否真的执行了（未配置则false）
        public int uploaded = 0;
        public int downloaded = 0;
        public int conflicts = 0;
        public String message = "";
        public boolean ok() { return ran && "".equals(message); }
    }

    private DshSync() {}

    // ================= 最近一次结果（跨线程共享）=================
    private static volatile String sLastResultJson = "{}";

    /** 最近一次同步结果的 JSON，供 {@code DshSafBridge.syncLastResult()} 返回。 */
    public static String lastResultJson(Context ctx) {
        return sLastResultJson;
    }

    private static void recordResult(Result r) {
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("ran", r.ran);
            o.put("uploaded", r.uploaded);
            o.put("downloaded", r.downloaded);
            o.put("conflicts", r.conflicts);
            o.put("message", r.message);
            o.put("at", System.currentTimeMillis());
            sLastResultJson = o.toString();
        } catch (Throwable ignored) { }
    }

    // ================= 配置 =================

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * 旧版本把连接信息存成单个 {@code url}（仅 WebDAV）。新版本拆成
     * type/host/port/share。这里做一次迁移，保证已存的 WebDAV 连接不丢。
     */
    private static void migrateIfNeeded(Context ctx) {
        SharedPreferences p = prefs(ctx);
        if (!p.getString("host", "").isEmpty()) return;          // 已迁移
        String u = p.getString("url", "");
        String us = p.getString("user", "");
        if (u.isEmpty() || us.isEmpty()) return;
        StringBuilder h = new StringBuilder(), po = new StringBuilder(), sh = new StringBuilder();
        parseUrl(u, h, po, sh);
        String type = u.startsWith("smb://") ? "smb" : (u.startsWith("sftp://") ? "sftp" : "webdav");
        p.edit()
            .putString("type", type)
            .putString("host", h.toString())
            .putString("port", po.toString())
            .putString("share", sh.toString())
            .apply();
    }

    /** 把 http(s)://host:port/path 拆成 host / port / share(含前导/)。 */
    private static void parseUrl(String url, StringBuilder host, StringBuilder port, StringBuilder share) {
        String s = url == null ? "" : url.trim();
        int sc = s.indexOf("://");
        String rest = sc >= 0 ? s.substring(sc + 3) : s;
        int slash = rest.indexOf('/');
        String auth = slash >= 0 ? rest.substring(0, slash) : rest;
        String sh = slash >= 0 ? rest.substring(slash) : "";
        int at = auth.lastIndexOf('@');
        if (at >= 0) auth = auth.substring(at + 1);              // 去掉 user:pass@
        int colon = auth.lastIndexOf(':');
        if (colon >= 0) {
            host.append(auth.substring(0, colon));
            port.append(auth.substring(colon + 1));
        } else {
            host.append(auth);
        }
        share.append(sh);
    }

    /** 由 type/host/port/share 拼出给用户看的连接串（也写回 url 字段）。 */
    static String displayUrl(String type, String host, String port, String share) {
        String h;
        if ("smb".equals(type)) h = "smb://" + host;
        else if ("sftp".equals(type)) h = "sftp://" + host;
        else h = "http://" + host;
        if (port != null && !port.isEmpty()) h += ":" + port;
        if (share != null && !share.isEmpty()) {
            if (!share.startsWith("/")) h += "/";
            h += share;
        }
        return h;
    }

    public static boolean isConfigured(Context ctx) {
        migrateIfNeeded(ctx);
        SharedPreferences p = prefs(ctx);
        return p.getString("host", "").trim().length() > 0
            && p.getString("user", "").trim().length() > 0;
    }

    /** 新签名：按协议保存完整连接配置。pass == null 表示「保留原密码」。 */
    public static void configure(Context ctx, String type, String host, String port,
                                 String share, String user, String pass, int intervalSec) {
        SharedPreferences.Editor e = prefs(ctx).edit()
            .putString("type", type == null ? "webdav" : type.trim())
            .putString("host", host == null ? "" : host.trim())
            .putString("port", port == null ? "" : port.trim())
            .putString("share", share == null ? "" : share.trim())
            .putString("user", user == null ? "" : user.trim())
            .putString("url", displayUrl(type, host, port, share))
            .putInt("interval", intervalSec < 30 ? 30 : intervalSec);
        if (pass != null) e.putString("pass", pass);
        e.apply();
        Log.i(TAG, "同步配置已更新：type=" + type + " host=" + host + " interval=" + intervalSec + "s");
    }

    /** 旧签名（WebDAV 兼容，SAF 桥仍在用）：从 url 解析成 host/port 后走新逻辑。 */
    public static void configure(Context ctx, String url, String user, String pass, int intervalSec) {
        migrateIfNeeded(ctx);
        StringBuilder h = new StringBuilder(), po = new StringBuilder(), sh = new StringBuilder();
        parseUrl(url, h, po, sh);
        String type = url.startsWith("smb://") ? "smb" : (url.startsWith("sftp://") ? "sftp" : "webdav");
        configure(ctx, type, h.toString(), po.toString(), sh.toString(), user, pass, intervalSec);
    }

    public static int intervalSec(Context ctx) {
        return prefs(ctx).getInt("interval", 60);
    }

    public static String type(Context ctx) {
        migrateIfNeeded(ctx);
        String t = prefs(ctx).getString("type", "webdav");
        return t == null || t.isEmpty() ? "webdav" : t;
    }

    public static String host(Context ctx) {
        migrateIfNeeded(ctx);
        return prefs(ctx).getString("host", "");
    }

    public static String port(Context ctx) {
        migrateIfNeeded(ctx);
        return prefs(ctx).getString("port", "");
    }

    public static String share(Context ctx) {
        migrateIfNeeded(ctx);
        return prefs(ctx).getString("share", "");
    }

    public static String user(Context ctx) {
        return prefs(ctx).getString("user", "");
    }

    /** 给用户看的连接串（含协议）。 */
    public static String url(Context ctx) {
        migrateIfNeeded(ctx);
        String stored = prefs(ctx).getString("url", "");
        if (!stored.isEmpty()) return stored;
        return displayUrl(type(ctx), host(ctx), port(ctx), share(ctx));
    }

    /** 连通性测试（设置页「测试连接」）。不落盘；pass 为 null 时用已存密码。 */
    public static boolean testConnection(Context ctx, String type, String host, String port,
                                         String share, String user, String pass) {
        if (host == null || host.trim().isEmpty()) return false;
        if (user == null || user.trim().isEmpty()) return false;
        NasTransport t = createTransport(
            type, host.trim(), port == null ? "" : port.trim(),
            share == null ? "" : share.trim(), user.trim(),
            pass == null ? prefs(ctx).getString("pass", "") : pass);
        try {
            return t.ping();
        } catch (Throwable e) {
            Log.w(TAG, "testConnection 异常", e);
            return false;
        } finally {
            try { t.close(); } catch (Throwable ignored) { }
        }
    }

    /** 旧签名（S51 沿用）：从 url 解析协议后走新逻辑。 */
    public static boolean testConnection(Context ctx, String url, String user, String pass) {
        StringBuilder h = new StringBuilder(), po = new StringBuilder(), sh = new StringBuilder();
        parseUrl(url, h, po, sh);
        String type = url.startsWith("smb://") ? "smb" : (url.startsWith("sftp://") ? "sftp" : "webdav");
        return testConnection(ctx, type, h.toString(), po.toString(), sh.toString(), user, pass);
    }

    /** 清除 NAS 同步配置（凭据一并删除）。设置页「断开」按钮调用。 */
    public static void clear(Context ctx) {
        prefs(ctx).edit()
            .remove("url").remove("user").remove("pass").remove("interval")
            .remove("type").remove("host").remove("port").remove("share")
            .apply();
        Log.i(TAG, "同步配置已清除");
    }

    // 密码不提供读取接口 —— 避免被日志/UI 意外带出

    /** 设备标识：用于冲突副本名，让用户一眼看出是哪台设备产生的冲突。 */
    static String deviceTag() {
        String m = Build.MODEL == null ? "" : Build.MODEL.trim();
        if (m.length() == 0) return "dev";
        String s = m.replaceAll("[\\s/\\\\]+", "-");
        return s.length() > 20 ? s.substring(0, 20) : s;
    }

    // ================= 传输工厂 =================

    /** 用当前已存配置创建传输实例。 */
    static NasTransport createTransport(Context ctx) {
        SharedPreferences p = prefs(ctx);
        return createTransport(
            p.getString("type", "webdav"),
            p.getString("host", ""),
            p.getString("port", ""),
            p.getString("share", ""),
            p.getString("user", ""),
            p.getString("pass", ""));
    }

    /** 用给定参数创建传输实例（按协议分派实现）。 */
    static NasTransport createTransport(String type, String host, String port,
                                       String share, String user, String pass) {
        if ("smb".equals(type)) return new DshSmbClient(host, port, share, user, pass);
        if ("sftp".equals(type)) return new DshSftpClient(host, port, share, user, pass);
        // 默认 WebDAV：host/port 拼成 http://host:port
        String u = "http://" + (host == null ? "" : host)
            + (port == null || port.isEmpty() ? "" : ":" + port);
        return new DshWebDavClient(u, user, pass);
    }

    // ================= 同步主流程 =================

    /**
     * 执行一次同步。**必须在后台线程调用**（会做网络 IO）。
     */
    public static Result syncOnce(Context ctx) {
        Result r;
        try {
            r = syncOnceInner(ctx);
        } catch (Throwable t) {
            r = new Result();
            r.message = "同步异常: " + t.getClass().getSimpleName();
            Log.w(TAG, "syncOnce 外层兜底", t);
        }
        recordResult(r);
        return r;
    }

    private static Result syncOnceInner(Context ctx) {
        Result r = new Result();
        SharedPreferences p = prefs(ctx);
        String host = p.getString("host", "").trim();
        String user = p.getString("user", "").trim();
        String pass = p.getString("pass", "");
        if (host.length() == 0 || user.length() == 0) {
            r.message = "未配置";
            return r;
        }
        r.ran = true;

        File localDir = new File(DshBootstrap.home(ctx), LOCAL_REL);
        //⚠️ 本地目录不存在 = 用户还没产生过任何会话，不做任何事（也不该为此在 NAS 上建目录）
        if (!localDir.isDirectory()) {
            r.message = "尚无会话数据";
            return r;
        }
        File[] localFiles = localDir.listFiles();
        if (localFiles == null || localFiles.length == 0) {
            r.message = "本地无文件";
            return r;
        }

        NasTransport tx = createTransport(ctx);
        try {
            if (!tx.ping()) {
                r.message = "无法连接 NAS（检查地址/凭证/是否开启对应服务）";
                return r;
            }
            if (!tx.mkdirs(REMOTE_ROOT)) {
                r.message = "无法创建远端目录（检查共享目录权限）";
                return r;
            }

            String stampKey = "lastSync_" + REMOTE_ROOT;
            long lastSync = p.getLong(stampKey, 0L);
            java.util.Set<String> remoteNames = tx.list(REMOTE_ROOT);

            // ---- 1) 上传：本地比上次同步新、或远端没有 ----
            for (File f : localFiles) {
                if (!f.isFile()) continue;
                if (f.getName().contains(CONFLICT_MARK)) continue;   // 冲突副本不参与同步
                String remotePath = REMOTE_ROOT + "/" + f.getName();
                boolean remoteExists = remoteNames.contains(f.getName());
                boolean changed = f.lastModified() > lastSync;
                if (!changed && !remoteExists) continue;
                if (!changed && remoteExists) continue;               // 交给下载分支
                if (tx.put(remotePath, f)) {
                    r.uploaded++;
                    p.edit().putLong("synced_" + remotePath, f.lastModified()).apply();
                } else {
                    r.message = "上传失败: " + f.getName();
                }
            }

            // ---- 2) 下载：远端更新（mtime 比上次同步新）或本地缺失 ----
            for (String name : remoteNames) {
                if (name.contains(CONFLICT_MARK)) continue;
                File local = new File(localDir, name);
                String remotePath = REMOTE_ROOT + "/" + name;
                long remoteStamp = tx.lastModified(remotePath);   // 0 =取不到
                boolean localExists = local.isFile();

                if (!localExists) {
                    if (tx.get(remotePath, local)) {
                        r.downloaded++;
                        Log.i(TAG, "从 NAS 拉取: " + name);
                    }
                    continue;
                }
                if (remoteStamp <= 0) continue;                  // 取不到 mtime 不冒险覆盖
                boolean remoteNewer = remoteStamp > lastSync;
                boolean localChanged = local.lastModified() > lastSync;
                if (!remoteNewer) continue;

                if (localChanged) {
                    // ⚠️ 两边都改过 → 保留双份，绝不静默覆盖
                    File kept = keepConflictCopy(local);
                    if (kept != null) r.conflicts++;
                    Log.w(TAG, "检测到冲突（本地与 NAS 都改过），已保留双份: " + name);
                    if (tx.get(remotePath, local)) r.downloaded++;
                } else {
                    if (tx.get(remotePath, local)) r.downloaded++;
                }
            }

            p.edit().putLong(stampKey, System.currentTimeMillis()).apply();
            Log.i(TAG, "同步完成: ↑" + r.uploaded + " ↓" + r.downloaded
                    + " 冲突" + r.conflicts);
            if ("".equals(r.message)) r.message = "ok";
        } catch (Throwable t) {
            r.message = "同步异常: " + t.getClass().getSimpleName();
            Log.w(TAG, "同步异常", t);
        } finally {
            try { tx.close(); } catch (Throwable ignored) { }
        }
        return r;
    }

    /**
     * 把本地文件另存为冲突副本，返回新文件。
     */
    static File keepConflictCopy(File src) {
        try {
            String name = src.getName();
            int dot = name.lastIndexOf('.');
            String stem = dot > 0 ? name.substring(0, dot) : name;
            String ext = dot > 0 ? name.substring(dot) : "";
            String ts = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                    .format(new Date());
            File dst = new File(src.getParentFile(),
                    stem + CONFLICT_MARK + deviceTag() + "-" + ts + ext);
            java.io.FileInputStream in = new java.io.FileInputStream(src);
            java.io.FileOutputStream out = new java.io.FileOutputStream(dst);
            try {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } finally {
                try { in.close(); } catch (Exception ignored) { }
                try { out.close(); } catch (Exception ignored) { }
            }
            Log.i(TAG, "冲突副本: " + dst.getName());
            return dst;
        } catch (Throwable t) {
            Log.w(TAG, "保留冲突副本失败: " + t.getMessage());
            return null;
        }
    }
}
