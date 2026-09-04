package com.github.savemysaves;

import net.minecraft.Util;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 备份调度器：单例，独立后台线程（逻辑与 1.12.2 一致：定时 + 立即 + 滚动删除）。
 * 1.16.5 差异：
 *   - FMLCommonHandler.getMinecraftServerInstance() → ServerLifecycleHooks.getCurrentServer()
 *   - TextComponentString → TextComponent（official 映射，1.16.5 字面文本组件为 TextComponent）
 *   - 群发消息走 PlayerList.broadcastMessage(Component, ChatType, UUID)
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

    /** 外部触发立即备份（命令） */
    public void requestImmediate(String reason) {
        if (!Config.cfgEnabled) return;
        this.immediateReason = reason;
        // 唤醒工作线程
        if (worker != null) worker.interrupt();
    }

    // ---------- 主循环 ----------

    private void loop() {
        while (running.get()) {
            try {
                long now = System.currentTimeMillis();

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
                    nextScheduledTime.set(System.currentTimeMillis() + Config.cfgIntervalMinutes * 60_000L);
                }

                long sleep = nextScheduledTime.get() - System.currentTimeMillis();
                if (sleep > 0) {
                    Thread.sleep(sleep);
                }
            } catch (InterruptedException e) {
                // 被唤醒：重新检查是否有立即请求
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logError("调度器异常", e);
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

    /** 群发消息：服务端已用 Lang 渲染成纯文本，原版/Forge 客户端都能直接显示。 */
    private void broadcast(String key, Object... args) {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                server.getPlayerList().broadcastMessage(text(key, args), ChatType.SYSTEM, Util.NIL_UUID);
            }
        } catch (Exception ignored) {}
    }

    /** 把语言键 + 参数格式化成纯文本组件（服务端语言包，见 Lang）。 */
    static TextComponent text(String key, Object... args) {
        return new TextComponent(Lang.t(key, args));
    }

    static void log(String msg) {
        SaveMySaves.LOGGER.info(msg);
    }

    static void logError(String msg, Exception e) {
        SaveMySaves.LOGGER.error(msg, e);
    }
}
