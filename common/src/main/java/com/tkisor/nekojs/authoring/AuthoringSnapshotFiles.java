package com.tkisor.nekojs.authoring;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.*;

/** Publishes one immutable snapshot before atomically switching its discovery pointer. */
public final class AuthoringSnapshotFiles {
    private AuthoringSnapshotFiles() {}

    public static void publish(Path directory, JsonObject document) throws IOException {
        String digest = AuthoringJson.hashJson(document);
        Path file = directory.resolve(digest + ".json");
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".authoring-", ".tmp");
        try {
            // Keep the full JSON tree as the only in-memory snapshot representation.
            try (var writer = Files.newBufferedWriter(temporary, java.nio.charset.StandardCharsets.UTF_8)) {
                AuthoringJson.GSON.toJson(document, writer);
            }
            if (Files.exists(file)) {
                if (Files.mismatch(temporary, file) != -1) throw new IOException("[NEKO-1019] Authoring snapshot content identity mismatch.");
            } else Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
        atomicWrite(directory.resolve("current.json"), AuthoringJson.obj("file", file.getFileName().toString(), "sha256", digest,
                "snapshotId", document.get("snapshotId"), "environmentId", document.get("environmentId")).toString());
    }

    public static JsonObject load(Path directory, String environmentId) throws IOException {
        Path pointer = directory.resolve("current.json");
        if (!Files.exists(pointer)) return null;
        return load(directory, environmentId, readBounded(pointer, 4096));
    }

    /** Loads exactly the already-read pointer so publication races cannot mismatch cached identity and content. */
    static JsonObject load(Path directory, String environmentId, String pointerText) throws IOException {
        JsonObject ref = AuthoringJson.parse(pointerText);
        String digest = AuthoringJson.string(ref, "sha256", "");
        if (!digest.matches("[0-9a-f]{64}") || !AuthoringJson.string(ref, "file", "").equals(digest + ".json")) throw new IOException("[NEKO-1019] Invalid authoring snapshot pointer.");
        if (!environmentId.equals(AuthoringJson.string(ref, "environmentId", ""))) return null;
        Path file = directory.resolve(digest + ".json");
        if (!file.toRealPath().getParent().equals(directory.toRealPath())) throw new IOException("[NEKO-1019] Authoring snapshot resolves outside its directory.");
        String text = readBounded(file, 128 * 1024 * 1024);
        if (!AuthoringJson.hash(text).equals(digest)) throw new IOException("[NEKO-1019] Authoring snapshot checksum mismatch.");
        JsonObject document = AuthoringJson.parse(text);
        if (!environmentId.equals(AuthoringJson.string(document, "environmentId", "")) || !AuthoringJson.string(ref, "snapshotId", "").equals(AuthoringJson.string(document, "snapshotId", ""))) throw new IOException("[NEKO-1019] Authoring snapshot identity mismatch.");
        return document;
    }

    static String readBounded(Path path, int limit) throws IOException {
        byte[] bytes;
        try (var input = Files.newInputStream(path)) { bytes = input.readNBytes(limit + 1); }
        if (bytes.length > limit) throw new IOException("[NEKO-1019] Authoring JSON file exceeds the size limit.");
        return java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
    }

    public static void atomicWrite(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), ".authoring-", ".tmp");
        try {
            Files.writeString(temporary, text);
            // A failed atomic replacement preserves the old pointer and is reported to the caller.
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
