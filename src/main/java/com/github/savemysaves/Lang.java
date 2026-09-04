package com.github.savemysaves;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Map;

/**
 * 服务端侧语言包（设计动机与 1.12.2 相同：客户端可不装本模组，
 * 所有面向玩家的文案在服务端格式化成纯文本下发，任何客户端都能正确显示）。
 * 1.16.5 差异：语言文件为 JSON（en_us.json / zh_cn.json，1.13+ 资源包格式），用 Gson 解析。
 * 语言由配置项 general.language 决定（zh_CN / en_US，默认 zh_CN）；英文作为兜底。
 */
public final class Lang {

    private static final Map<String, String> PRIMARY = new HashMap<>();
    private static final Map<String, String> FALLBACK = new HashMap<>();

    private Lang() {}

    /** 按配置选择语言并加载。英文始终作为兜底。文件名一律小写（1.13+ 资源包约定）。 */
    public static synchronized void load(String lang) {
        PRIMARY.clear();
        FALLBACK.clear();
        loadInto(FALLBACK, "/assets/savemysaves/lang/en_us.json");
        String l = lang == null ? "" : lang.trim().toLowerCase(Locale.ROOT);
        if (!l.isEmpty() && !l.equals("en_us")) {
            if (!loadInto(PRIMARY, "/assets/savemysaves/lang/" + l + ".json")) {
                BackupScheduler.log("语言文件不存在，回退英文: " + lang);
            }
        }
        BackupScheduler.log("语言已加载: 主=" + (l.isEmpty() ? "(en_us)" : l)
                + " (" + PRIMARY.size() + " 条), 兜底=en_us (" + FALLBACK.size() + " 条)");
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
                JsonObject root = new JsonParser().parse(r).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                    if (e.getValue().isJsonPrimitive()) {
                        target.put(e.getKey(), e.getValue().getAsString());
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
