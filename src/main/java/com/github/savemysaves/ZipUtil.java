package com.github.savemysaves;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 目录压缩工具：将指定目录打包为单个 .zip，
 * 自动跳过目标备份目录自身（防止备份目录里的旧 zip 被反复打进新 zip 导致无限膨胀）。
 */
public final class ZipUtil {

    private ZipUtil() {}

    /**
     * 将 rootDir 压缩为 destZip。
     * @param excludeDir 要排除的目录（通常是备份目录），可为 null
     */
    public static void zipDirectory(File rootDir, File destZip, File excludeDir) throws IOException {
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            throw new IOException("源目录不存在：" + rootDir);
        }

        // 临时工作：先把 excludeDir 移出（若存在），压缩完恢复——简单起见改为"遍历时跳过"
        destZip.getParentFile().mkdirs();

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(destZip)))) {
            zos.setLevel(6); // 平衡速度与体积
            String rootPath = rootDir.getAbsolutePath();
            walkAndZip(rootDir, rootPath, zos, excludeDir == null ? null : excludeDir.getAbsoluteFile());
        }
    }

    private static void walkAndZip(File dir, String rootPath, ZipOutputStream zos, File excludeAbs) throws IOException {
        File[] children = dir.listFiles();
        if (children == null) return;

        for (File f : children) {
            // 跳过备份目录本身
            if (excludeAbs != null && f.getAbsoluteFile().equals(excludeAbs)) {
                continue;
            }
            // 跳过目标 zip 自身（若恰好在同一目录）
            // 标准化路径
            String rel = toRelative(f, rootPath);
            if (f.isDirectory()) {
                // 目录也作为条目写入（保证空目录保留），但先递归
                // 为避免重复条目，目录不单独写条目，文件条目会携带完整路径
                walkAndZip(f, rootPath, zos, excludeAbs);
            } else {
                writeEntry(zos, rel, f);
            }
        }
    }

    private static void writeEntry(ZipOutputStream zos, String relPath, File file) throws IOException {
        // 跳过 session.lock：游戏通过 FileChannel.lock() 全程持有该文件的字节锁，
        // Windows 上 FileLock 是强制锁，同 JVM 内其他通道读取锁定区域也直接抛
        // IOException（"另一个程序已锁定文件的一部分"），该文件内容对备份亦无意义。
        if ("session.lock".equals(file.getName())) {
            return;
        }

        // 先读入内存再写条目：单文件读取失败只跳过该文件，
        // 不能让整个备份报废（正在游玩时部分文件可能被游戏/其他程序短暂锁定）。
        byte[] data;
        try {
            data = readWithRetry(file, 3, 50);
        } catch (IOException e) {
            SaveMySaves.LOGGER.warn("[SaveMySaves] 跳过被锁定的文件：" + relPath + "（" + e.getMessage() + "）");
            return;
        }

        // ZIP 条目用 /
        String entryName = relPath.replace(File.separatorChar, '/');
        ZipEntry entry = new ZipEntry(entryName);
        entry.setTime(file.lastModified());
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }

    /** 带重试的整文件读取：瞬时锁（文件正被改写/替换）通常几十毫秒内释放。 */
    private static byte[] readWithRetry(File file, int attempts, long retryDelayMs) throws IOException {
        IOException last = null;
        for (int i = 0; i < attempts; i++) {
            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[64 * 1024];
                int len;
                while ((len = bis.read(buf)) > 0) {
                    bos.write(buf, 0, len);
                }
                return bos.toByteArray();
            } catch (IOException e) {
                last = e;
                if (i < attempts - 1) {
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        throw last;
    }

    /** 计算文件相对 root 的路径 */
    private static String toRelative(File file, String rootPath) {
        String abs = file.getAbsolutePath();
        if (abs.startsWith(rootPath)) {
            String rel = abs.substring(rootPath.length());
            if (rel.startsWith(File.separator)) rel = rel.substring(1);
            return rel;
        }
        return file.getName();
    }
}
