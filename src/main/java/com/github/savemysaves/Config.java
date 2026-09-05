package com.github.savemysaves;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.io.File;

/**
 * 1.20.6 配置：ModConfigSpec（NeoForge 的 TOML 配置系统，取代 Forge 的 ForgeConfigSpec），
 * 文件为 config/savemysaves-common.toml。注释全部使用英文（运行时规范第 4 条）。
 * 数值默认值与 1.16.5 版本一致。
 */
public class Config {

    public static final ModConfigSpec SPEC;

    // ---- 配置项句柄 ----
    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.IntValue INTERVAL_MINUTES;
    private static final ModConfigSpec.IntValue MAX_BACKUPS;
    private static final ModConfigSpec.ConfigValue<String> BACKUP_DIR_NAME;
    private static final ModConfigSpec.ConfigValue<String> LANGUAGE;

    // ---- 缓存值（ModConfigEvent.Loading/Reloading 时刷新）----
    public static boolean cfgEnabled = true;
    public static int cfgIntervalMinutes = 15;
    public static int cfgMaxBackups = 10;
    public static String cfgBackupDirName = "_backups";
    public static String cfgLanguage = "zh_CN";

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
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

    // 注意：不要在类加载/static 块中调用 refresh()！
    // NeoForge 20.6 起 ModConfigSpec.ConfigValue.get() 在配置未加载时直接抛
    // IllegalStateException（"Cannot get config value before config is loaded"，
    // 1.16.5 的 ForgeConfigSpec 是静默返回默认值，行为已收紧，未来生产环境也会抛）。
    // 缓存字段已在声明处带默认值，refresh() 仅在 ModConfigEvent（配置已加载）时调用。
}
