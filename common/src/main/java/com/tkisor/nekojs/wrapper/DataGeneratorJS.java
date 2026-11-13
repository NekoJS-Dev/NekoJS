package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.core.JsonObjectAdapter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Event object for data and asset generation ({@code generateData} / {@code generateAssets}).
 *
 * <p>Scripts call {@code event.json(path, obj)} to write JSON into NekoJS's on-disk pack directory
 * ({@code <gameDir>/nekojs/data} or {@code <gameDir>/nekojs/assets}). {@code NekoJSPackLoader}
 * registers that directory as a datapack or resource pack and reads it lazily from disk, so the
 * reload timing stays correct.
 *
 * <p>Writes use a sibling temp file plus an atomic move, so a reload never reads a half-written
 * file.
 */
@Doc("Event object for generateData/generateAssets; writes files into NekoJS's disk pack directory.")
public final class DataGeneratorJS {
    /** Hard per-write size cap for generated files (16 MiB). */
    @Doc("Per-file size cap for generated files, in bytes (16 MiB).")
    public static final int MAX_GENERATED_FILE_BYTES = 16 * 1024 * 1024;
    /** Hard cumulative size cap per generator instance (64 MiB). */
    @Doc("Cumulative size cap per generator instance, in bytes (64 MiB).")
    public static final long MAX_GENERATED_TOTAL_BYTES = 64 * 1024 * 1024L;

    /**
     * Per-write observer installed by {@link DataGenerationBatch} (same package): receives the
     * pack-relative path of every successful write for owner attribution and overwrite policy.
     * Not part of the script-facing surface.
     */
    interface WriteObserver {
        void onWrite(String relativePath, int bytes);
    }

    private Path root;
    private final String stage;
    private long generatedBytes;
    private WriteObserver writeObserver;
    private boolean sealed;
    private Path sealedReadRoot;

    /** Creates a generator for the given pack root; the stage is an empty string. */
    public DataGeneratorJS(Path root) {
        this(root, "");
    }

    /** Creates a generator for the given pack root and stage name. */
    public DataGeneratorJS(Path root, String stage) {
        this.root = Objects.requireNonNull(root, "root");
        this.stage = stage == null ? "" : stage;
    }

    /** Package-private: {@link DataGenerationBatch} installs per-write observation (contributor attribution and overwrite policy). */
    void setWriteObserver(WriteObserver observer) {
        this.writeObserver = observer;
    }

    /**
     * Package-private: called by {@link DataGenerationBatch} after the batch published
     * successfully — read-back is re-rooted to the published active root (keeping the same
     * {@code getJson} path), and further writes are permanently rejected (no bypassing the
     * candidate area once the batch is closed).
     */
    void sealForReadBack(Path publishedRoot) {
        this.sealed = true;
        this.sealedReadRoot = publishedRoot;
    }

    /** The current generation stage (for example {@code after_mods}); an empty string when none was given. */
    @Doc("Returns the generation stage this event belongs to.")
    @Return("stage name such as 'after_mods', or empty string when unspecified")
    public String getStage() {
        return stage;
    }

    /** Writes a JSON file; {@code value} is a JS object (serialized automatically) or a JSON string. */
    @Doc("Writes a JSON file; the value may be a JS object (auto-serialized) or a JSON string.")
    @Param(name = "path", value = "relative path inside the pack root; must not escape it")
    @Param(name = "value", value = "JS object, JS array, or JSON string")
    public void json(String path, Object value) {
        // JS null and undefined arrive as Java null. A literal "null" file is never what a script
        // author wants, so reject here instead of letting the serializer write garbage.
        if (value == null) {
            throw new IllegalArgumentException(
                    "value must not be null or undefined (path: " + path
                            + "); pass a JS object, JS array, or a JSON string");
        }
        // A String argument is the pre-encoded JSON-string form and is written verbatim; structural
        // validation stays with the generation batch. All other shapes go through the shared
        // host-value conversion: on the real engine, script objects arrive as Map/List host
        // proxies, never as Value.
        String content = value instanceof String text
                ? text
                : JsonObjectAdapter.convertHostValueToJson(value).toString();
        write(path, content);
    }

    /** Writes a plain text file. */
    @Doc("Writes plain text to a file.")
    @Param(name = "path", value = "relative path inside the pack root; must not escape it")
    @Param(name = "content", value = "text content to write; must not be null")
    public void text(String path, String content) {
        write(path, Objects.requireNonNull(content, "content"));
    }

    /** Writes a plain text file; an alias of {@code text}. */
    @Doc("Writes plain text to a file; alias of text(path, content).")
    @Param(name = "path", value = "relative path inside the pack root; must not escape it")
    @Param(name = "content", value = "text content to write; must not be null")
    public void add(String path, String content) {
        text(path, content);
    }

    /** Reads back a generated JSON file; returns null when it does not exist. */
    @Doc("Reads back a previously generated JSON file.")
    @Param(name = "path", value = "relative path inside the pack root")
    @Return("parsed JSON, or null when the file is missing, unreadable, or invalid")
    public JsonElement getJson(String path) {
        try {
            Path file = resolveForRead(path);
            if (!Files.isRegularFile(file)) {
                return null;
            }
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException error) {
            return null;
        }
    }

    /** After publish, read-back resolves against the published active root; during collection it resolves against the current (candidate) root. */
    private Path resolveForRead(String path) {
        if (sealed && sealedReadRoot != null) {
            Path relative = Path.of(path).normalize();
            return sealedReadRoot.resolve(relative).normalize();
        }
        return resolve(path);
    }

    private void write(String path, String content) {
        if (sealed) {
            throw new IllegalStateException(
                    "This generation batch is already published; writing is no longer allowed");
        }
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
        if (writeObserver != null) {
            writeObserver.onWrite(NekoJSPaths.canonicalForm(root).relativize(target).toString().replace('\\', '/'), bytes);
        }
    }

    /**
     * Package-private: {@link AssetGeneratorJS} in the same package reuses this containment check
     * for binary writes, so it cannot be bypassed.
     */
    Path resolve(String path) {
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
        if (!NekoJSPaths.isInside(verified, root)) {
            throw new IllegalArgumentException("Generated file path escapes its root: " + path);
        }
        return verified;
    }
}
