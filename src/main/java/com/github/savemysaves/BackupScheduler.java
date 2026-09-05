package com.github.savemysaves;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 备份调度器：单例，独立后台线程（逻辑与 1.16.5 一致：定时 + 立即 + 滚动删除）。
 * 1.20.6 差异：
 *   - ServerLifecycleHooks → net.neoforged.neoforge.server.ServerLifecycleHooks
 *   - 群发消息 broadcastMessage(Component, ChatType, UUID) → broadcastSystemMessage(Component, boolean)
 *   - TextComponent → Component.literal
 *   - 日志 Log4j → SLF4J
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
        nextScheduledTime.set(System.currentTimeMillis() + Config.cfgIntervalMinutes * 60_000L);

        worker = new Thread(this::loop, "SaveMySaves-BackupThread");
        worker.setDaemon(true);
        worker.start();

        log("备份调度器已启动，间隔=" + Config.cfgIntervalMinutes + "分钟，最多保留=" + Config.cfgMaxBackups + "份");
    }

    public synchronized void shutdown() {
        running.set(false);
        if (worker != null) worker.interrupt();
    }

    /** 外部触发立即备份（命令）。 */
    public void requestImmediate(String reason) {
        if (!Config.cfgEnabled) return;
        this.immediateReason = reason;
        // 注意：严禁用 interrupt() 唤醒工作线程！
        // 教训（1.16.5 实测事故）：类是懒加载的，若线程带着中断标志执行 tryBackup，
        // 类加载器读 jar 字节码时 NIO 通道会抛 ClosedByInterruptException →
        // NoClassDefFoundError（Error，catch(Exception) 接不住）→ 工作线程静默死亡，
        // 之后所有手动/定时备份全部失效。工作线程以 500ms 步长轮询 immediateReason。
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
                String reason = immediateReason;
                if (reason != null) {
                    immediateReason = null;
                } else if (System.currentTimeMillis() >= nextScheduledTime.get()) {
                    reason = "scheduled";
                }

                if (reason != null) {
                    tryBackup(reason);
                    // 无论成功与否，重排下一次定时
                    nextScheduledTime.set(System.currentTimeMillis() + Config.cfgIntervalMinutes * 60_000L);
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
        if (!Config.cfgEnabled) return;
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
        if (files.length <= Config.cfgMaxBackups) return;

        java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));

        int toDelete = files.length - Config.cfgMaxBackups;
        for (int i = 0; i < toDelete; i++) {
            if (files[i].delete()) {
                log("已删除旧备份：" + files[i].getName());
            }
        }
    }

    // ---------- 工具 ----------

    /** 群发消息：服务端已用 Lang 渲染成纯文本，原版/NeoForge 客户端都能直接显示。 */
    private void broadcast(String key, Object... args) {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                server.getPlayerList().broadcastSystemMessage(text(key, args), false);
            }
        } catch (Exception ignored) {}
    }

    /** 把语言键 + 参数格式化成纯文本组件（服务端语言包，见 Lang）。 */
    static Component text(String key, Object... args) {
        return Component.literal(Lang.t(key, args));
    }

    static void log(String msg) {
        SaveMySaves.LOGGER.info(msg);
    }

    static void logError(String msg, Exception e) {
        SaveMySaves.LOGGER.error(msg, e);
    }
}
