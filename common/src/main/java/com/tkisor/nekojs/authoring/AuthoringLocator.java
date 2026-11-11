package com.tkisor.nekojs.authoring;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/** Finds exports using read-only output configuration and never creates files. */
final class AuthoringLocator {
    private AuthoringLocator() {}

    record Location(AuthoringPaths paths, boolean legacy) {}

    static Location resolve(Path gameDirectory, String directory) throws IOException {
        if (directory != null) return new Location(AuthoringPaths.resolve(gameDirectory, directory), false);
        AuthoringPaths config = AuthoringPaths.resolve(gameDirectory, "nekojs/config");
        Path file = config.verify(config.root().resolve("ai.toml"));
        if (present(file)) {
            return new Location(AuthoringPaths.resolve(gameDirectory, AuthoringPaths.readConfiguredDirectory(file)), false);
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
