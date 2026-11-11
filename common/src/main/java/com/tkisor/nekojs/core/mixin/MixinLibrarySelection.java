package com.tkisor.nekojs.core.mixin;

import java.io.IOException;
import java.nio.file.*;

/** JDK-only selection shared by early injection gates and workspace preparation. */
public final class MixinLibrarySelection {
    public static final String DIRECTORY = "nekojs/.cache/mixins";
    private MixinLibrarySelection() {}

    public static String selected(Path game) throws IOException {
        Path directory = directory(game);
        Path pointer = directory.resolve("active");
        if (!Files.exists(pointer, LinkOption.NOFOLLOW_LINKS)) return null;
        if (Files.isSymbolicLink(pointer) || !Files.isRegularFile(pointer)) throw new IOException("[NEKO-4090] Invalid managed mixin selection file");
        if (Files.size(pointer) > 65) throw new IOException("[NEKO-4090] Invalid managed mixin selection length");
        String hash = Files.readString(pointer).strip();
        if (hash.equals("-")) return null;
        if (!hash.matches("[0-9a-f]{64}")) throw new IOException("[NEKO-4090] Invalid managed mixin selection hash");
        return hash;
    }

    public static void select(Path game, String hash) throws IOException {
        if (hash != null && !hash.matches("[0-9a-f]{64}")) throw new IOException("[NEKO-4090] Invalid managed mixin selection hash");
        Path directory = directory(game);
        Files.createDirectories(directory);
        selected(game); // Validate existing data before replacing it.
        Path staging = Files.createTempFile(directory, "selection-", ".pending");
        try {
            Files.writeString(staging, hash == null ? "-" : hash);
            Files.move(staging, directory.resolve("active"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    private static Path directory(Path game) throws IOException {
        Path current = game.toAbsolutePath().normalize();
        for (String part : DIRECTORY.split("/")) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) throw new IOException("[NEKO-4090] Managed mixin selection must not traverse symbolic links");
        }
        return current;
    }
}
