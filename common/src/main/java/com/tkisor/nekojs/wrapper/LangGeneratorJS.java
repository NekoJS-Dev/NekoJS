package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 语言生成事件的对象（{@code ClientEvents.lang}）。
 *
 * <p>脚本通过 {@code event.add(key, value)} 收集翻译条目；聚合后由
 * {@link #writeTo(Path, String)} 合并写入 {@code <gameDir>/nekojs/assets/lang/<lang>.json}
 * （保留已有条目，新条目覆盖）。
 */
public final class LangGeneratorJS {
    private final String lang;
    private final Map<String, String> entries = new LinkedHashMap<>();

    public LangGeneratorJS(String lang) {
        this.lang = lang == null ? "" : lang;
    }

    /** 语言代码（如 {@code en_us}）；未指定时为空字符串。 */
    public String getLang() {
        return lang;
    }

    /** 添加翻译条目；key 形如 {@code minecraft:item.foo}。 */
    public void add(String key, String value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        entries.put(key, value);
    }

    /** 批量添加翻译条目。 */
    public void addAll(Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        entries.putAll(values);
    }

    /** 已收集的条目（只读视图）。 */
    public Map<String, String> entries() {
        return Map.copyOf(entries);
    }

    /** 合并写入指定语言的 lang JSON 文件（保留已有条目，新条目覆盖）。 */
    public void writeTo(Path assetsRoot, String lang) {
        Objects.requireNonNull(lang, "lang");
        if (entries.isEmpty()) {
            return;
        }
        Path file = assetsRoot.resolve("lang").resolve(lang + ".json");
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            JsonObject merged = new JsonObject();
            if (Files.isRegularFile(file)) {
                JsonElement existing = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                if (existing.isJsonObject()) {
                    existing.getAsJsonObject().entrySet()
                            .forEach(entry -> merged.add(entry.getKey(), entry.getValue()));
                }
            }
            entries.forEach(merged::addProperty);
            Path temp = Files.createTempFile(parent, ".nekojs-lang-", ".tmp");
            try {
                Files.writeString(temp, merged.toString(), StandardCharsets.UTF_8);
                try {
                    Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException error) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Failed to write lang file " + file + ": " + error.getMessage(), error);
        }
    }
}
