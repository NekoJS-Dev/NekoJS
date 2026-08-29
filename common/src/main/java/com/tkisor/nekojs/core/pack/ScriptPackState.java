package com.tkisor.nekojs.core.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 包级启用状态文件（{@code .neko_pack.state.json}）：优先级高于 manifest 的
 * {@code enabled} 默认值，供 GUI/命令切换包后持久化。读失败视为无状态文件
 * （回退 manifest 默认），写失败仅 WARN 不抛——状态持久化不允许阻断脚本加载。
 */
public record ScriptPackState(boolean enabled) {

    public static final String FILE_NAME = ".neko_pack.state.json";

    /** 读取包目录的状态文件；不存在/损坏/类型不符返回 {@code null}（用 manifest 默认值）。 */
    public static ScriptPackState load(Path packDir) {
        Path file = packDir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) return null;
        try {
            try (var reader = Files.newBufferedReader(file)) {
                JsonElement el = JsonParser.parseReader(reader);
                if (el == null || !el.isJsonObject()) return null;
                JsonElement enabled = el.getAsJsonObject().get("enabled");
                if (enabled == null || !enabled.isJsonPrimitive() || !enabled.getAsJsonPrimitive().isBoolean()) {
                    return null;
                }
                return new ScriptPackState(enabled.getAsBoolean());
            }
        } catch (Exception e) {
            com.tkisor.nekojs.NekoJS.LOGGER.warn(
                "Script pack state file is unreadable, falling back to manifest default: {} ({})",
                file, e.toString());
            return null;
        }
    }

    public static void save(Path packDir, boolean enabled) {
        Path file = packDir.resolve(FILE_NAME);
        try {
            JsonObject root = new JsonObject();
            root.addProperty("enabled", enabled);
            Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Exception e) {
            com.tkisor.nekojs.NekoJS.LOGGER.warn("Failed to persist script pack state {}: {}", file, e.toString());
        }
    }
}
