package com.github.savemysaves;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 备份调度器：单例，独立后台线程。
 * - 定时：每 INTERVAL_MINUTES 分钟对当前游玩的世界做一次全量压缩备份
 * - 立即：通过 requestImmediate(reason) 触发一次（命令 / 进出世界）
 * - 滚动：每个世界目录独立保留最多 MAX_BACKUPS 份，超出删最旧
 */
public enum BackupScheduler {

    INSTANCE;

    /** 后台工作线程 */
    private Thread worker;
    /** 是否已启动 */
    private final AtomicBoolean running = new AtomicBoolean(false);
    /** 当前是否正在执行一次备份（防重入） */
    private final AtomicBoolean backingUp = new AtomicBoolean(false);

    /** 下一次"定时备份"的绝对时间戳（毫秒）。0 表示需要立即重新计算。 */
    private final AtomicLong nextScheduledTime = new AtomicLong(0);

    /** 待处理的"立即备份"请求原因；null 表示无请求 */
    private volatile String immediateReason;

    // ---------- 生命周期 ----------

    public synchronized void start() {
        if (running.get()) return;
        running.set(true);
        nextScheduledTime.set(System.currentTimeMillis() + Config.INTERVAL_MINUTES * 60_000L);

        worker = new Thread(this::loop, "SaveMySaves-BackupThread");
        worker.setDaemon(true);
        worker.start();

        log("备份调度器已启动，间隔=" + Config.INTERVAL_MINUTES + "分钟，最多保留=" + Config.MAX_BACKUPS + "份");
    }

    public synchronized void shutdown() {
        running.set(false);
        if (worker != null) worker.interrupt();
    }

    /** 外部触发立即备份（命令 / 进出世界） */
    public void requestImmediate(String reason) {
        if (!Config.ENABLED) return;
        this.immediateReason = reason;
        // 注意：不要 interrupt()（1.16.5 版实测事故：带中断标志的线程懒加载
        // ZipUtil 时读 jar 抛 ClosedByInterruptException → NoClassDefFoundError，
        // Error 逃逸杀死线程。轮询模式下最多延迟 500ms 被处理）
    }

    // ---------- 主循环 ----------

    private void loop() {
        // 预热 ZipUtil：强制提前完成类加载，不依赖首次备份时的懒加载
        try {
            Class.forName("com.github.savemysaves.ZipUtil", false, BackupScheduler.class.getClassLoader());
        } catch (Throwable t) {
            logError("ZipUtil 预热失败", new Exception(t));
        }

        while (running.get()) {
            try {
                long now = System.currentTimeMillis();

                // 是否应该触发一次备份
                boolean should = false;
                String reason = null;

                if (immediateReason != null) {
                    reason = immediateReason;
                    immediateReason = null;
                    should = true;
                } else if (now >= nextScheduledTime.get()) {
                    reason = "scheduled";
                    should = true;
                }

                if (should) {
                    tryBackup(reason);
                    // 无论成功与否，重排下一次定时
                    nextScheduledTime.set(System.currentTimeMillis() + Config.INTERVAL_MINUTES * 60_000L);
                }

                // 短步长轮询代替 interrupt 唤醒：手动请求最多延迟 500ms，开销可忽略
                long untilNext = nextScheduledTime.get() - System.currentTimeMillis();
                Thread.sleep(Math.min(500, Math.max(1, untilNext)));
            } catch (InterruptedException e) {
                // shutdown 唤醒：进入下一轮检查 running 标志后自然退出
            } catch (Throwable t) {
                // 必须 catch Throwable：NoClassDefFoundError 等 Error 若逃逸会静默杀死工作线程
                logError("调度器异常", new Exception(t));
            }
        }
    }

    // ---------- 执行备份 ----------

    private void tryBackup(String reason) {
        if (!Config.ENABLED) return;
        if (!backingUp.compareAndSet(false, true)) {
            // 已有备份在进行，跳过本次避免叠加
            return;
        }
        try {
            File worldDir = SaveMySaves.currentWorldDir;
            if (worldDir == null || !worldDir.exists()) {
                // 当前没有正在游玩的世界，跳过（定时备份只备份"正在游玩"的存档）
                return;
            }

            // 通知玩家（服务端用 Lang 渲染成纯文本后群发，任何客户端都能正确显示）
            broadcast("savemysaves.chat.starting." + reason);

            File backupDir = Config.getBackupDir(worldDir);
            backupDir.mkdirs();

            String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            File zip = new File(backupDir, worldDir.getName() + "_" + stamp + ".zip");

            ZipUtil.zipDirectory(worldDir, zip, backupDir); // 排除备份目录自身，避免递归

            // 滚动删除最旧的
            cleanupOldBackups(backupDir, worldDir.getName());

            broadcast("savemysaves.chat.finished", zip.getName());
            log("备份完成：" + zip.getAbsolutePath());
        } catch (Exception e) {
            broadcast("savemysaves.chat.failed", e.getMessage());
            logError("备份失败", e);
        } finally {
            backingUp.set(false);
        }
    }

    /** 删除最旧的备份，使该世界目录下的备份数 <= MAX_BACKUPS */
    private void cleanupOldBackups(File backupDir, String worldName) {
        File[] files = backupDir.listFiles((d, name) -> name.startsWith(worldName + "_") && name.endsWith(".zip"));
        if (files == null) return;
        if (files.length <= Config.MAX_BACKUPS) return;

        // 按最后修改时间排序（旧的在前）
        java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));

        int toDelete = files.length - Config.MAX_BACKUPS;
        for (int i = 0; i < toDelete; i++) {
            if (files[i].delete()) {
                log("已删除旧备份：" + files[i].getName());
            }
        }
    }

    // ---------- 工具 ----------

    /** 群发消息：服务端已用 Lang 渲染成纯文本，原版/Forge 客户端都能直接显示。 */
    private void broadcast(String key, Object... args) {
        try {
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (server != null) {
                server.getPlayerList().sendMessage(text(key, args));
            }
        } catch (Exception ignored) {}
    }

    /** 把语言键 + 参数格式化成纯文本组件（服务端语言包，见 Lang）。 */
    static ITextComponent text(String key, Object... args) {
        return new TextComponentString(Lang.t(key, args));
    }

    static void log(String msg) {
        System.out.println("[SaveMySaves] " + msg);
    }

    static void logError(String msg, Exception e) {
        System.err.println("[SaveMySaves] " + msg + ": " + e);
        e.printStackTrace();
    }
}
