package com.github.savemysaves;

import net.minecraftforge.common.ForgeConfigSpec;

import java.io.File;

/**
 * 1.16.5 配置：ForgeConfigSpec（1.12.2 的 Configuration 已移除），
 * 文件为 config/savemysaves-common.toml。注释全部使用英文（运行时规范第 4 条）。
 * 数值默认值与 1.12.2 版本一致。
 */
public class Config {

    public static final ForgeConfigSpec SPEC;

    // ---- 配置项句柄 ----
    private static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final ForgeConfigSpec.IntValue INTERVAL_MINUTES;
    private static final ForgeConfigSpec.IntValue MAX_BACKUPS;
    private static final ForgeConfigSpec.ConfigValue<String> BACKUP_DIR_NAME;
    private static final ForgeConfigSpec.ConfigValue<String> LANGUAGE;

    // ---- 缓存值（ModConfigEvent.Loading/Reloading 时刷新）----
    public static boolean cfgEnabled = true;
    public static int cfgIntervalMinutes = 15;
    public static int cfgMaxBackups = 10;
    public static String cfgBackupDirName = "_backups";
    public static String cfgLanguage = "zh_CN";

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("general");

        ENABLED = b.comment("Whether to enable automatic scheduled backups.")
                .define("enabled", true);

        INTERVAL_MINUTES = b.comment("Automatic backup interval in minutes. Min 1, max 1440.")
                .defineInRange("intervalMinutes", 15, 1, 1440);

        MAX_BACKUPS = b.comment("Maximum number of backups to keep per world (oldest rolled out). Min 1.")
                .defineInRange("maxBackups", 10, 1, 1000);

        BACKUP_DIR_NAME = b.comment("Backup directory name, relative to the world folder. Start with an underscore so MC does not treat it as a save.")
                .define("backupDirName", "_backups");

        LANGUAGE = b.comment("Language for chat/command messages (rendered server-side, identical for all clients): zh_CN Simplified Chinese, en_US English.")
                .define("language", "zh_CN");

        b.pop();
        SPEC = b.build();
    }

    /** 将配置值刷入缓存。在 ModConfigEvent.Loading / Reloading 时调用。 */
    public static void refresh() {
        cfgEnabled = ENABLED.get();
        cfgIntervalMinutes = INTERVAL_MINUTES.get();
        cfgMaxBackups = MAX_BACKUPS.get();
        cfgBackupDirName = BACKUP_DIR_NAME.get();
        cfgLanguage = LANGUAGE.get();
    }

    /** 备份目录：用缓存值而非 BACKUP_DIR_NAME.get()，避免配置尚未载入时抛异常。 */
    public static File getBackupDir(File worldDir) {
        return new File(worldDir, cfgBackupDirName);
    }

    static {
        // 类加载即刷新一次，保证配置文件尚未载入时也有正确默认值
        refresh();
    }
}
