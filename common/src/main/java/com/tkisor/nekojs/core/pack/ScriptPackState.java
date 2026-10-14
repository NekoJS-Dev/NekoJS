package com.tkisor.nekojs.core.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.fs.NekoJSPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Local enablement intent; a saved value does not imply runtime activation. */
public record ScriptPackState(boolean enabled) {

    public static final String FILE_NAME = ".neko_pack.state.json";

    /** Legacy discovery falls back to the manifest for missing or unreadable state. */
    public static ScriptPackState load(Path packDir) {
        Path file = packDir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) return null;
        try {
            return read(file);
        } catch (IOException | RuntimeException e) {
            NekoJS.LOGGER.warn(
                    "[NEKO-1014] Script pack state is unreadable; discovery uses the manifest default: {}",
                    file, e);
            return null;
        }
    }

    private static ScriptPackState read(Path file) throws IOException {
        try (var reader = Files.newBufferedReader(file)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element != null && element.isJsonObject()) {
                JsonElement enabled = element.getAsJsonObject().get("enabled");
                if (enabled != null && enabled.isJsonPrimitive() && enabled.getAsJsonPrimitive().isBoolean()) {
                    return new ScriptPackState(enabled.getAsBoolean());
                }
            }
            throw new IOException("[NEKO-1014] Script pack state must contain a boolean enabled field: " + file);
        }
    }

    /** Atomically replaces valid local state, retaining the original on failure. */
    public static boolean save(Path packDir, boolean enabled) {
        Path file = packDir.resolve(FILE_NAME);
        Path temporary = null;
        try {
            // State may not redirect a local operation outside its discovered pack.
            if (!NekoJSPaths.isInside(file, packDir)) {
                throw new IOException("[NEKO-1013] Script pack state path escapes its pack: " + file);
            }
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("[NEKO-1013] Script pack state is not a regular file: " + file);
                }
                read(file);
            }
            JsonObject root = new JsonObject();
            root.addProperty("enabled", enabled);
            temporary = Files.createTempFile(packDir, ".neko_pack.state-", ".tmp");
            Files.writeString(temporary, root.toString());
            // Do not fall back to truncating or deleting the previous state.
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException | RuntimeException e) {
            NekoJS.LOGGER.warn("[NEKO-1013] Script pack state update failed; previous state retained: {}", file, e);
            return false;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException e) {
                    NekoJS.LOGGER.warn("[NEKO-1013] Script pack state temporary file cleanup failed: {}", temporary, e);
                }
            }
        }
    }
}
