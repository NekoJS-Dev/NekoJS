package com.tkisor.nekojs.authoring;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/** Finds exported authoring data without loading or creating runtime configuration. */
final class AuthoringLocator {
    private AuthoringLocator() {}

    record Location(AuthoringPaths paths, boolean legacy) {}

    static Location resolve(Path gameDirectory, String directory) throws IOException {
        if (directory != null) return new Location(AuthoringPaths.resolve(gameDirectory, directory), false);
        AuthoringPaths config = AuthoringPaths.resolve(gameDirectory, "nekojs/config");
        Path locator = config.verify(config.root().resolve("ai-location.json"));
        if (present(locator)) {
            try {
                JsonObject value = AuthoringJson.parse(AuthoringSnapshotFiles.readBounded(locator, 4096));
                AuthoringJson.keys(value, "", "schemaVersion", "directory");
                AuthoringJson.integer(value.get("schemaVersion"), "/schemaVersion", 1, 1);
                return new Location(AuthoringPaths.resolve(gameDirectory, AuthoringJson.string(value, "directory", "")), false);
            } catch (AuthoringException failure) {
                throw new IOException("[NEKO-1019] Invalid exported authoring location.", failure);
            }
        }
        AuthoringPaths defaults = AuthoringPaths.resolve(gameDirectory, AuthoringPaths.DEFAULT_DIRECTORY);
        if (present(defaults.environment())) return new Location(defaults, false);
        return new Location(AuthoringPaths.resolve(gameDirectory, "nekojs/ai"), true);
    }

    private static boolean present(Path path) throws IOException {
        try {
            Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return true;
        } catch (NoSuchFileException missing) {
            return false;
        }
    }
}
