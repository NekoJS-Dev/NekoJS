package com.tkisor.nekojs.core.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Legacy manifests are permissive; formatVersion 1 opts into a strict pack descriptor. */
public record ScriptPackManifest(
    String id,
    String name,
    String version,
    String description,
    List<String> authors,
    boolean enabledByDefault,
    boolean clientSync,
    JsonObject signature,
    JsonObject config,
    JsonObject raw,
    PackDescriptor descriptor
) {

    public ScriptPackManifest(String id, String name, String version, String description, List<String> authors,
                              boolean enabledByDefault, boolean clientSync, JsonObject signature, JsonObject config, JsonObject raw) {
        this(id, name, version, description, authors, enabledByDefault, clientSync, signature, config, raw,
                PackDescriptor.parse(raw));
    }

    public static final String FILE_NAME = "manifest.json";

    /** 目录名兜底用的 id 规则：小写字母/数字/下划线/连字符，其余字符替换为 {@code _}。 */
    public static String sanitizeId(String dirName) {
        String cleaned = dirName == null ? "" : dirName.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        return cleaned.isBlank() ? "pack" : cleaned;
    }

    /**
     * 解析包目录下的 manifest.json。文件不存在返回 {@code null}（不是包）；
     * 存在但损坏时同样返回 {@code null} 并 WARN——由调用方决定跳过该包。
     */
    public static ScriptPackManifest load(Path packDir, String fallbackId) {
        Path file = packDir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) return null;
        JsonObject root;
        try {
            try (var reader = Files.newBufferedReader(file)) {
                JsonElement el = JsonParser.parseReader(reader);
                root = el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
            }
        } catch (Exception e) {
            com.tkisor.nekojs.NekoJS.LOGGER.warn("Script pack manifest is unreadable, skipping pack {}: {}", packDir, e.toString());
            return null;
        }
        if (root == null) {
            com.tkisor.nekojs.NekoJS.LOGGER.warn("Script pack manifest is not a JSON object, skipping pack {}", packDir);
            return null;
        }
        try {
            return parse(root, fallbackId);
        } catch (IllegalArgumentException invalid) {
            com.tkisor.nekojs.NekoJS.LOGGER.warn("[NEKO-1016] Pack manifest rejected; pack skipped: {}", packDir, invalid);
            return null;
        }
    }

    /** Parses without changing raw signed bytes or applying disk state. */
    public static ScriptPackManifest parse(JsonObject root, String fallbackId) {
        String id = string(root, "id", null);
        if (id == null || id.isBlank()) id = fallbackId;
        return new ScriptPackManifest(
            id,
            string(root, "name", id),
            string(root, "version", "unknown"),
            string(root, "description", ""),
            stringList(root, "authors"),
            bool(root, "enabled", true),
            bool(root, "clientSync", true),
            object(root, "signature"),
            object(root, "config"),
            root
        );
    }

    private static String string(JsonObject root, String key, String fallback) {
        JsonElement el = root.get(key);
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()
            ? el.getAsString()
            : fallback;
    }

    private static boolean bool(JsonObject root, String key, boolean fallback) {
        JsonElement el = root.get(key);
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean()
            ? el.getAsBoolean()
            : fallback;
    }

    private static List<String> stringList(JsonObject root, String key) {
        JsonElement el = root.get(key);
        if (el == null || !el.isJsonArray()) return List.of();
        List<String> out = new ArrayList<>();
        for (JsonElement item : el.getAsJsonArray()) {
            if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                out.add(item.getAsString());
            }
        }
        return List.copyOf(out);
    }

    private static JsonObject object(JsonObject root, String key) {
        JsonElement el = root.get(key);
        return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
    }
}
