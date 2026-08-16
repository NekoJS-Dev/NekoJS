package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.core.fs.NekoJSPaths;

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
@Doc("Event object for ClientEvents.lang; collects translation entries and merges them into the lang JSON.")
public final class LangGeneratorJS {
    /** Hard per-write size cap for the merged lang JSON text (16 MiB). */
    @Doc("Size cap for the merged lang JSON text, in bytes (16 MiB).")
    public static final int MAX_GENERATED_FILE_BYTES = 16 * 1024 * 1024;

    private final String lang;
    private final Map<String, String> entries = new LinkedHashMap<>();

    /** 以语言代码构造（null 视为空字符串）。 */
    public LangGeneratorJS(String lang) {
        this.lang = lang == null ? "" : lang;
    }

    /** 语言代码（如 {@code en_us}）；未指定时为空字符串。 */
    @Doc("Returns the language code this generator collects entries for.")
    @Return("lang code such as 'en_us', or empty string when unspecified")
    public String getLang() {
        return lang;
    }

    /** 添加翻译条目；key 形如 {@code minecraft:item.foo}。 */
    @Doc("Adds one translation entry.")
    @Param(name = "key", value = "translation key such as 'minecraft:item.foo'")
    @Param(name = "value", value = "translated text")
    public void add(String key, String value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        entries.put(key, value);
    }

    /** 批量添加翻译条目。 */
    @Doc("Adds all entries from a map of translation keys to texts.")
    @Param(name = "values", value = "map of translation key to translated text")
    public void addAll(Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        entries.putAll(values);
    }

    /** 已收集的条目（只读视图）。 */
    @Doc("Returns the entries collected so far.")
    @Return("a copy of the collected entries; later additions are not reflected")
    public Map<String, String> entries() {
        return Map.copyOf(entries);
    }

    /** 合并写入指定语言的 lang JSON 文件（保留已有条目，新条目覆盖）。 */
    @Doc("Merges the collected entries into the lang JSON file, keeping existing entries.")
    @Param(name = "assetsRoot", value = "assets root directory to write under, e.g. <gameDir>/nekojs/assets")
    @Param(name = "lang", value = "language code like 'en_us'; letters, digits and underscore only")
    public void writeTo(Path assetsRoot, String lang) {
        Objects.requireNonNull(assetsRoot, "assetsRoot");
        Objects.requireNonNull(lang, "lang");
        if (!lang.matches("^[A-Za-z0-9_]{1,64}$")) {
            throw new IllegalArgumentException(
                    "Invalid lang code (only [A-Za-z0-9_]{1,64} is allowed): " + lang);
        }
        if (entries.isEmpty()) {
            return;
        }
        Path file = assetsRoot.resolve("lang").resolve(lang + ".json");
        Path verified;
        try {
            verified = NekoJSPaths.get().verifyInsideGameDirForCreate(file);
        } catch (IOException error) {
            throw new IllegalArgumentException("Lang file escapes the game directory: " + file, error);
        }
        if (!verified.startsWith(assetsRoot.normalize().toAbsolutePath())) {
            throw new IllegalArgumentException("Lang file escapes the assets root: " + verified);
        }
        try {
            JsonObject merged = new JsonObject();
            if (Files.isRegularFile(verified)) {
                JsonElement existing = JsonParser.parseString(Files.readString(verified, StandardCharsets.UTF_8));
                if (existing.isJsonObject()) {
                    existing.getAsJsonObject().entrySet()
                            .forEach(entry -> merged.add(entry.getKey(), entry.getValue()));
                }
            }
            entries.forEach(merged::addProperty);
            String content = merged.toString();
            int bytes = content.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > MAX_GENERATED_FILE_BYTES) {
                throw new IllegalStateException(
                        "Lang file " + verified + " would be " + bytes + " bytes, exceeding the limit of "
                                + MAX_GENERATED_FILE_BYTES + " bytes");
            }
            Path parent = verified.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = Files.createTempFile(parent, ".nekojs-lang-", ".tmp");
            try {
                Files.writeString(temp, content, StandardCharsets.UTF_8);
                try {
                    Files.move(temp, verified, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException error) {
                    Files.move(temp, verified, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Failed to write lang file " + verified + ": " + error.getMessage(), error);
        }
    }
}
