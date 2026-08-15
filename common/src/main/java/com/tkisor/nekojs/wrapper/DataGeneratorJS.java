package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.core.JsonObjectAdapter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import graal.graalvm.polyglot.Value;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * 数据/资产生成事件的对象（{@code generateData} / {@code generateAssets}）。
 *
 * <p>脚本通过 {@code event.json(path, obj)} 把 JSON 写入 NekoJS 的磁盘 pack 目录
 * （{@code <gameDir>/nekojs/data} 或 {@code <gameDir>/nekojs/assets}），该目录已由
 * {@code NekoJSPackLoader} 注册为 datapack / resource pack，懒读磁盘保证 reload 时序正确。
 *
 * <p>写盘使用 sibling temp + atomic move，避免 reload 中途读到半写的文件。
 */
public final class DataGeneratorJS {
    /** Hard per-write size cap for generated files (16 MiB). */
    public static final int MAX_GENERATED_FILE_BYTES = 16 * 1024 * 1024;
    /** Hard cumulative size cap per generator instance (64 MiB). */
    public static final long MAX_GENERATED_TOTAL_BYTES = 64 * 1024 * 1024L;

    private final Path root;
    private final String stage;
    private long generatedBytes;

    public DataGeneratorJS(Path root) {
        this(root, "");
    }

    public DataGeneratorJS(Path root, String stage) {
        this.root = Objects.requireNonNull(root, "root");
        this.stage = stage == null ? "" : stage;
    }

    /** 当前生成阶段（如 {@code after_mods}）；未指定时为空字符串。 */
    public String getStage() {
        return stage;
    }

    /** 写入 JSON 文件；{@code value} 为 JS 对象（自动序列化）或 JSON 字符串。 */
    public void json(String path, Object value) {
        String content = value instanceof Value graalValue
                ? JsonObjectAdapter.convertValueToJson(graalValue).toString()
                : value instanceof String text ? text : String.valueOf(value);
        write(path, content);
    }

    /** 写入纯文本文件。 */
    public void text(String path, String content) {
        write(path, Objects.requireNonNull(content, "content"));
    }

    /** 写入纯文本文件（{@code text} 的别名）。 */
    public void add(String path, String content) {
        text(path, content);
    }

    /** 读取已生成的 JSON（不存在时返回 null）。 */
    public JsonElement getJson(String path) {
        try {
            Path file = resolve(path);
            if (!Files.isRegularFile(file)) {
                return null;
            }
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException error) {
            return null;
        }
    }

    private void write(String path, String content) {
        Path target = resolve(Objects.requireNonNull(path, "path"));
        int bytes = content.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_GENERATED_FILE_BYTES) {
            throw new IllegalStateException(
                    "Generated file " + path + " is " + bytes + " bytes, exceeding the per-file limit of "
                            + MAX_GENERATED_FILE_BYTES + " bytes");
        }
        if (generatedBytes + bytes > MAX_GENERATED_TOTAL_BYTES) {
            throw new IllegalStateException(
                    "Generated file " + path + " would reach " + (generatedBytes + bytes)
                            + " bytes, exceeding the total limit of " + MAX_GENERATED_TOTAL_BYTES + " bytes");
        }
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = Files.createTempFile(parent, ".nekojs-gen-", ".tmp");
            try {
                Files.writeString(temp, content, StandardCharsets.UTF_8);
                try {
                    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException error) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Failed to write generated file " + path + ": " + error.getMessage(), error);
        }
        generatedBytes += bytes;
    }

    private Path resolve(String path) {
        Path relative = Path.of(path).normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new IllegalArgumentException("Generated file path must be a relative path: " + path);
        }
        Path target = root.resolve(relative).normalize();
        Path verified;
        try {
            verified = NekoJSPaths.get().verifyInsideGameDirForCreate(target);
        } catch (IOException error) {
            throw new IllegalArgumentException("Generated file path escapes the game directory: " + path, error);
        }
        if (!verified.startsWith(root.normalize().toAbsolutePath())) {
            throw new IllegalArgumentException("Generated file path escapes its root: " + path);
        }
        return verified;
    }
}
