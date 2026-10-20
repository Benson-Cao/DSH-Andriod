package com.dsh.harness;

import android.util.Log;

import jcifs.CIFSContext;
import jcifs.context.SingletonContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * SMB/CIFS 传输实现（基于 jcifs-ng）。对应设计稿 S17「连接类型 · SMB」。
 *
 * 与 WebDAV/SFTP 共用 {@link DshSync} 的同步与冲突策略，只是传输层换成 SMB。
 * 连接串形如 {@code smb://host:port/share}，鉴权用 用户名/密码（域留空）。
 */
final class DshSmbClient implements DshSync.NasTransport {

    private static final String TAG = "DshSmbClient";

    private final String host;
    private final int port;
    private final String share;
    private final CIFSContext ctx;

    DshSmbClient(String host, String port, String share, String user, String pass) {
        this.host = host == null ? "" : host.trim();
        int p;
        try {
            p = (port == null || port.trim().isEmpty()) ? 445 : Integer.parseInt(port.trim());
        } catch (NumberFormatException e) {
            p = 445;
        }
        this.port = p;
        this.share = share == null ? "" : share.trim();
        String u = user == null ? "" : user.trim();
        String pw = pass == null ? "" : pass;
        // 域留空；SMB 默认用 用户名/密码 直连。SingletonContext 读取默认 jcifs 配置。
        this.ctx = SingletonContext.getInstance()
                .withCredentials(new NtlmPasswordAuthenticator("", u, pw));
    }

    private String base() {
        String h = "smb://" + host + (port == 445 ? "" : ":" + port);
        if (share.isEmpty()) return h;
        return h + "/" + (share.startsWith("/") ? share.substring(1) : share);
    }

    private String remotePath(String rel) {
        String b = base();
        return (rel == null || rel.isEmpty()) ? b : (b + "/" + rel);
    }

    @Override
    public boolean ping() {
        try {
            SmbFile root = new SmbFile(base(), ctx);
            root.listFiles();   // 触发连接 + 鉴权
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "ping 失败: " + t.getMessage());
            return false;
        }
    }

    @Override
    public boolean mkdirs(String relPath) {
        try {
            SmbFile f = new SmbFile(remotePath(relPath), ctx);
            if (f.exists()) return true;
            f.mkdirs();   // jcifs-ng 的 mkdirs 会一并创建父目录
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "mkdirs 失败: " + t.getMessage());
            return false;
        }
    }

    @Override
    public boolean put(String relPath, File local) {
        if (!local.isFile()) return false;
        try (InputStream in = new FileInputStream(local);
             OutputStream os = new SmbFile(remotePath(relPath), ctx).getOutputStream()) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "put 失败 " + relPath + ": " + t.getMessage());
            return false;
        }
    }

    @Override
    public boolean get(String relPath, File dest) {
        try {
            File parent = dest.getParentFile();
            if (parent != null) parent.mkdirs();
            try (InputStream in = new SmbFile(remotePath(relPath), ctx).getInputStream();
                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "get 失败 " + relPath + ": " + t.getMessage());
            return false;
        }
    }

    @Override
    public Set<String> list(String relPath) {
        Set<String> names = new LinkedHashSet<>();
        try {
            SmbFile dir = new SmbFile(remotePath(relPath), ctx);
            if (!dir.exists()) return names;
            for (SmbFile f : dir.listFiles()) {
                String name = f.getName();
                if (name.endsWith("/")) name = name.substring(0, name.length() - 1);
                if (name.isEmpty() || name.equals(".") || name.equals("..")) continue;
                names.add(name);
            }
        } catch (Throwable t) {
            Log.w(TAG, "list 失败 " + relPath + ": " + t.getMessage());
        }
        return names;
    }

    @Override
    public long lastModified(String relPath) {
        try {
            return new SmbFile(remotePath(relPath), ctx).lastModified();
        } catch (Throwable t) {
            return 0L;
        }
    }

    @Override
    public void close() {
        // jcifs-ng 无显式连接句柄，GC 回收即可
    }
}
