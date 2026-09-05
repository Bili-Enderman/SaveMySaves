package com.github.savemysaves;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.io.File;

public final class Config {

    /** 配置项语言键前缀，对应 assets/<modid>/lang/*.lang 中的 config.savemysaves.* 键 */
    private static final String LANG_PREFIX = "config.savemysaves";

    private Config() {}

    public static boolean ENABLED = true;
    public static int INTERVAL_MINUTES = 15;
    public static int MAX_BACKUPS = 10;
    public static String BACKUP_DIR_NAME = "_backups";
    public static String LANGUAGE = "zh_CN";

    public static void load(Configuration cfg) {
        cfg.load();

        // 确保 "general" 分类存在并带标题注释（首次生成配置文件时更完整）
        cfg.addCustomCategoryComment("general", "SaveMySaves 配置");

        ENABLED = getBool(cfg, "general", "enabled", ENABLED,
                "是否启用自动定时备份");

        INTERVAL_MINUTES = getInt(cfg, "general", "intervalMinutes", INTERVAL_MINUTES, 1, 1440,
                "自动备份间隔（分钟）。最小 1，最大 1440");

        MAX_BACKUPS = getInt(cfg, "general", "maxBackups", MAX_BACKUPS, 1, 1000,
                "最多保留的备份份数（滚动删除最旧的）。最小 1");

        BACKUP_DIR_NAME = getString(cfg, "general", "backupDirName", BACKUP_DIR_NAME,
                "备份存放目录名，相对于对应世界存档文件夹（建议以下划线开头避免被 MC 识别为存档）");

        LANGUAGE = getString(cfg, "general", "language", LANGUAGE,
                "聊天/命令提示使用的语言（服务端统一渲染，所有客户端看到一致）：zh_CN 简体中文，en_US 英文");

        if (cfg.hasChanged()) {
            cfg.save();
        }
    }

    public static File getBackupDir(File worldDir) {
        return new File(worldDir, BACKUP_DIR_NAME);
    }

    // ---------- 内部工具 ----------

    /**
     * 创建属性并挂上语言键与注释。
     * 语言键 config.savemysaves.<cat>.<key> 与语言文件一一对应，
     * Forge 配置 GUI 显示时用它本地化配置项名称。
     */
    private static Property prop(Configuration cfg, String cat, String key, String def, String comment) {
        // 本版本差异（1.8.9）：Property 无 setComment()，注释走 Configuration.get 四参重载
        Property p = cfg.get(cat, key, def, comment);
        p.setLanguageKey(LANG_PREFIX + "." + cat + "." + key);
        return p;
    }

    private static boolean getBool(Configuration cfg, String cat, String key, boolean def, String comment) {
        Property p = prop(cfg, cat, key, String.valueOf(def), comment);
        return p.getBoolean(def);
    }

    private static int getInt(Configuration cfg, String cat, String key, int def, int min, int max, String comment) {
        Property p = prop(cfg, cat, key, String.valueOf(def), comment + "  [默认: " + def + ", 范围: " + min + "~" + max + "]");
        int v = p.getInt(def);
        if (v < min) v = min;
        if (v > max) v = max;
        p.set(v); // 修正回写
        return v;
    }

    private static String getString(Configuration cfg, String cat, String key, String def, String comment) {
        Property p = prop(cfg, cat, key, def, comment);
        return p.getString();
    }
}
