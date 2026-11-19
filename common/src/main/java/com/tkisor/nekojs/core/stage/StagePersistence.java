package com.tkisor.nekojs.core.stage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * JSON persistence codec and atomic file writer for server and per-player stage state under
 * {@code <worldRoot>/nekojs/stages/server.json} and
 * {@code <worldRoot>/nekojs/stages/players/<uuid>.json}.
 *
 * <p>Format ({@code schemaVersion: 1}):
 * <pre>{@code
 * {
 *   "schemaVersion": 1,
 *   "permanent": ["iron_age", "nether_unlocked"],
 *   "temporary": {
 *     "double_xp": 1200
 *   }
 * }
 * }</pre>
 *
 * <p>Reads fail loudly with {@link IllegalStateException} ({@code [NEKO-4072]}) on corrupt JSON,
 * unsupported schema versions, or I/O failures so damaged world data is never silently overwritten
 * with empty defaults.
 */
public final class StagePersistence {

    /** Current JSON schema version written and accepted by this persistence layer. */
    public static final int SCHEMA_VERSION = 1;

    private static final int MAX_STAGE_NAME_LENGTH = 256;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private StagePersistence() {
    }

    /**
     * Immutable snapshot of persisted permanent and temporary stage entries.
     *
     * @param schemaVersion format schema version
     * @param permanent     immutable set of permanent stage names
     * @param temporary     immutable map of temporary stage names to remaining ticks ({@code > 0})
     */
    public record StageData(int schemaVersion, Set<String> permanent, Map<String, Long> temporary) {
        /** Empty stage snapshot used when a target file has not been created yet. */
        public static final StageData EMPTY = new StageData(SCHEMA_VERSION, Set.of(), Map.of());

        public StageData {
            permanent = permanent == null || permanent.isEmpty()
                    ? Set.of()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(permanent));
            temporary = temporary == null || temporary.isEmpty()
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(temporary));
        }
    }

    /** Resolves {@code <worldRoot>/nekojs/stages} from a Minecraft world save directory. */
    public static Path resolveWorldStageDir(Path worldRoot) {
        Objects.requireNonNull(worldRoot, "worldRoot");
        return worldRoot.resolve("nekojs").resolve("stages");
    }

    /** Resolves {@code <worldStageDir>/server.json}. */
    public static Path serverFile(Path worldStageDir) {
        Objects.requireNonNull(worldStageDir, "worldStageDir");
        return worldStageDir.resolve("server.json");
    }

    /** Resolves {@code <worldStageDir>/players/<uuid>.json}, rejecting path traversal inputs. */
    public static Path playerFile(Path worldStageDir, String playerUuid) {
        Objects.requireNonNull(worldStageDir, "worldStageDir");
        String safeUuid = sanitizePlayerFileKey(playerUuid);
        return worldStageDir.resolve("players").resolve(safeUuid + ".json");
    }

    /** Reads server stage data from {@code <worldStageDir>/server.json}, or {@link StageData#EMPTY} if absent. */
    public static StageData readServer(Path worldStageDir) {
        return readFile(serverFile(worldStageDir));
    }

    /** Atomically writes server stage data to {@code <worldStageDir>/server.json}. */
    public static void writeServer(Path worldStageDir, Set<String> permanent, Map<String, Long> temporary) {
        writeFile(serverFile(worldStageDir), permanent, temporary);
    }

    /** Reads player stage data from {@code <worldStageDir>/players/<uuid>.json}, or {@link StageData#EMPTY} if absent. */
    public static StageData readPlayer(Path worldStageDir, String playerUuid) {
        return readFile(playerFile(worldStageDir, playerUuid));
    }

    /** Atomically writes player stage data to {@code <worldStageDir>/players/<uuid>.json}. */
    public static void writePlayer(
            Path worldStageDir,
            String playerUuid,
            Set<String> permanent,
            Map<String, Long> temporary) {
        writeFile(playerFile(worldStageDir, playerUuid), permanent, temporary);
    }

    /**
     * Reads stage data from {@code path}. Returns {@link StageData#EMPTY} only when the file does
     * not exist yet; throws {@link IllegalStateException} ({@code [NEKO-4072]}) on corrupt JSON,
     * invalid schema/types, or I/O errors.
     */
    public static StageData readFile(Path path) {
        Objects.requireNonNull(path, "path");
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return StageData.EMPTY;
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException(
                    "[NEKO-4072] Failed to read stage data from " + path,
                    new IOException("Stage data path is not a regular file: " + path));
        }
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (content.isBlank()) {
                throw new JsonParseException("Stage JSON file is empty");
            }
            JsonElement parsed = JsonParser.parseString(content);
            if (!parsed.isJsonObject()) {
                throw new JsonParseException("Expected JSON object at root of stage file");
            }
            return parseObject(parsed.getAsJsonObject());
        } catch (IOException | JsonParseException | IllegalArgumentException | IllegalStateException e) {
            throw new IllegalStateException("[NEKO-4072] Failed to read stage data from " + path, e);
        }
    }

    /**
     * Serializes and atomically writes permanent and temporary stages to {@code path} via a
     * sibling temporary file and {@link StandardCopyOption#ATOMIC_MOVE} (falling back to
     * {@link StandardCopyOption#REPLACE_EXISTING} when the underlying filesystem does not support
     * atomic moves).
     */
    public static void writeFile(Path path, Set<String> permanent, Map<String, Long> temporary) {
        Objects.requireNonNull(path, "path");
        Set<String> permSnapshot = permanent == null ? Set.of() : permanent;
        Map<String, Long> tempSnapshot = temporary == null ? Map.of() : temporary;

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);

        List<String> sortedPermanent = new ArrayList<>();
        for (String stage : permSnapshot) {
            validatePersistedStageName(stage);
            sortedPermanent.add(stage);
        }
        Collections.sort(sortedPermanent);
        JsonArray permArray = new JsonArray();
        for (String stage : sortedPermanent) {
            permArray.add(stage);
        }
        root.add("permanent", permArray);

        List<String> sortedTempKeys = new ArrayList<>();
        for (Map.Entry<String, Long> entry : tempSnapshot.entrySet()) {
            String stage = entry.getKey();
            Long ticks = entry.getValue();
            validatePersistedStageName(stage);
            if (ticks != null && ticks > 0L && !permSnapshot.contains(stage)) {
                sortedTempKeys.add(stage);
            }
        }
        Collections.sort(sortedTempKeys);
        JsonObject tempObject = new JsonObject();
        for (String stage : sortedTempKeys) {
            tempObject.addProperty(stage, tempSnapshot.get(stage));
        }
        root.add("temporary", tempObject);

        byte[] bytes = (GSON.toJson(root) + "\n").getBytes(StandardCharsets.UTF_8);

        Path normalizedTarget = path.toAbsolutePath().normalize();
        Path parent = normalizedTarget.getParent();
        Path tempFile = null;
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tempDir = parent != null ? parent : Path.of(".");
            tempFile = Files.createTempFile(tempDir, "." + normalizedTarget.getFileName() + ".", ".tmp");
            try (FileChannel channel = FileChannel.open(
                    tempFile, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(
                        tempFile,
                        normalizedTarget,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(tempFile, normalizedTarget, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "[NEKO-4072] Failed to write stage data to " + path, e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // Best-effort cleanup of temp file if move failed.
                }
            }
        }
    }

    private static StageData parseObject(JsonObject obj) {
        int version = SCHEMA_VERSION;
        if (obj.has("schemaVersion") && !obj.get("schemaVersion").isJsonNull()) {
            JsonElement verEl = obj.get("schemaVersion");
            if (!verEl.isJsonPrimitive() || !verEl.getAsJsonPrimitive().isNumber()) {
                throw new JsonParseException("Field 'schemaVersion' must be an integer");
            }
            version = verEl.getAsInt();
            if (version != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported stage schemaVersion: " + version);
            }
        }

        Set<String> permanent = new LinkedHashSet<>();
        if (obj.has("permanent") && !obj.get("permanent").isJsonNull()) {
            JsonElement permEl = obj.get("permanent");
            if (!permEl.isJsonArray()) {
                throw new JsonParseException("Field 'permanent' must be a JSON string array");
            }
            for (JsonElement element : permEl.getAsJsonArray()) {
                if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                    throw new JsonParseException("Field 'permanent' must contain only strings");
                }
                String stage = element.getAsString();
                validatePersistedStageName(stage);
                permanent.add(stage);
            }
        }

        Map<String, Long> temporary = new LinkedHashMap<>();
        if (obj.has("temporary") && !obj.get("temporary").isJsonNull()) {
            JsonElement tempEl = obj.get("temporary");
            if (!tempEl.isJsonObject()) {
                throw new JsonParseException("Field 'temporary' must be a JSON object");
            }
            for (Map.Entry<String, JsonElement> entry : tempEl.getAsJsonObject().entrySet()) {
                String stage = entry.getKey();
                validatePersistedStageName(stage);
                JsonElement val = entry.getValue();
                if (val == null || !val.isJsonPrimitive()) {
                    throw new JsonParseException("Temporary stage ticks for '" + stage + "' must be a number");
                }
                JsonPrimitive prim = val.getAsJsonPrimitive();
                if (!prim.isNumber()) {
                    throw new JsonParseException("Temporary stage ticks for '" + stage + "' must be a number");
                }
                long ticks = prim.getAsLong();
                if (ticks > 0L && !permanent.contains(stage)) {
                    temporary.put(stage, ticks);
                }
            }
        }

        return new StageData(version, permanent, temporary);
    }

    private static void validatePersistedStageName(String stage) {
        if (stage == null || stage.isBlank() || stage.length() > MAX_STAGE_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Invalid stage name in persisted stage data: must not be blank or exceed 256 chars");
        }
    }

    private static String sanitizePlayerFileKey(String playerUuid) {
        if (playerUuid == null) {
            throw new IllegalArgumentException("[NEKO-4070] Player UUID must not be blank");
        }
        String trimmed = playerUuid.trim();
        if (trimmed.isEmpty()
                || trimmed.contains("/")
                || trimmed.contains("\\")
                || trimmed.contains("..")
                || trimmed.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("[NEKO-4070] Invalid player UUID for stage file: " + playerUuid);
        }
        return trimmed;
    }
}
