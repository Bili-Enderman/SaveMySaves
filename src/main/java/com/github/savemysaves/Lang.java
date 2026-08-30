package com.github.savemysaves;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.Map;

/**
 * 服务端侧语言包：从本模组 jar 内加载 .lang 文件，由服务端统一把聊天/命令提示渲染成纯文本。
 *
 * <p>设计动机：这是服务端侧工具，客户端不需要装本模组。若把翻译键（TextComponentTranslation）
 * 发给客户端，未装本模组的客户端没有语言文件，会显示 savemysaves.* 裸键名。
 * 因此所有面向玩家的文案都在服务端用这里格式化后，以 TextComponentString 下发——
 * 任何客户端（原版 / Forge 未装模组 / Forge 已装模组）都能正确显示。</p>
 *
 * <p>语言由配置项 general.language 决定（zh_CN 简体中文 / en_US 英文，默认 zh_CN）；
 * 英文作为兜底，避免指定语言文件缺失时出现裸键名。</p>
 */
public final class Lang {

    private static final Map<String, String> PRIMARY = new HashMap<>();
    private static final Map<String, String> FALLBACK = new HashMap<>();

    private Lang() {}

    /** 按配置选择语言并加载。英文始终作为兜底。 */
    public static synchronized void load(String lang) {
        PRIMARY.clear();
        FALLBACK.clear();
        loadInto(FALLBACK, "/assets/savemysaves/lang/en_US.lang");
        if (lang != null && !lang.isEmpty() && !lang.equals("en_US")) {
            boolean ok = loadInto(PRIMARY, "/assets/savemysaves/lang/" + lang + ".lang");
            if (!ok) {
                BackupScheduler.log("语言文件不存在，回退英文: " + lang);
            }
        }
        BackupScheduler.log("语言已加载: 主=" + (lang == null ? "(en_US)" : lang)
                + " (" + PRIMARY.size() + " 条), 兜底=en_US (" + FALLBACK.size() + " 条)");
    }

    /** 取 key 的译文并做参数替换；缺键时回退英文，再缺则返回键名本身。 */
    public static String t(String key, Object... args) {
        String fmt = PRIMARY.get(key);
        if (fmt == null) fmt = FALLBACK.get(key);
        if (fmt == null) return key;
        if (args.length == 0) return fmt;
        try {
            return String.format(fmt, args);
        } catch (IllegalFormatException e) {
            return key;
        }
    }

    private static boolean loadInto(Map<String, String> target, String path) {
        try (InputStream in = SaveMySaves.class.getResourceAsStream(path)) {
            if (in == null) return false;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        target.put(line.substring(0, eq).trim(), line.substring(eq + 1));
                    }
                }
            }
            return !target.isEmpty();
        } catch (Exception e) {
            BackupScheduler.logError("语言文件读取失败: " + path, e);
            return false;
        }
    }
}
