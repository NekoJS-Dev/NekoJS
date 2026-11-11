package com.tkisor.nekojs.authoring;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/** Immutable, game-relative authoring destinations shared by runtime and offline tooling; never creates files. */
public final class AuthoringPaths {
    public static final String DEFAULT_DIRECTORY = ".github/neko_ai";
    private final Path gameDirectory;
    private final Path root;

    private AuthoringPaths(Path gameDirectory, Path root) {
        this.gameDirectory = gameDirectory;
        this.root = root;
    }

    /** Reads only the output selection; parsing never creates configuration or enables runtime services. */
    static String readConfiguredDirectory(Path file) throws IOException {
        try {
            return configuredDirectory(new TomlParser().parse(AuthoringSnapshotFiles.readBounded(file, 64 * 1024)));
        } catch (RuntimeException failure) {
            throw new IOException("[NEKO-1019] Invalid authoring output configuration in ai.toml.", failure);
        }
    }

    static String configuredDirectory(UnmodifiableConfig config) throws IOException {
        if (config.contains("output") && !(config.getRaw("output") instanceof UnmodifiableConfig)) {
            throw new IOException("[NEKO-1019] Authoring output must be a TOML table.");
        }
        Object value = config.getOrElse("output.directory", DEFAULT_DIRECTORY);
        if (!(value instanceof String directory)) throw invalidDirectory();
        validateRelativeDirectory(directory);
        return directory;
    }

    /** Resolves an explicit portable slash-separated directory; no default or global path state is consulted. */
    public static AuthoringPaths resolve(Path gameDirectory, String configuredDirectory) throws IOException {
        if (gameDirectory == null) throw new IOException("[NEKO-1019] The authoring game directory is required.");
        validateRelativeDirectory(configuredDirectory);
        Path game = gameDirectory.toRealPath();
        requireDirectory(game);
        try {
            Path relative = game.getFileSystem().getPath(configuredDirectory);
            if (relative.isAbsolute()) throw invalidDirectory();
            Path root = canonical(game.resolve(relative));
            if (!root.startsWith(game) || root.equals(game)) {
                throw new IOException("[NEKO-1019] Authoring output resolves outside the selected game directory.");
            }
            requireDirectoryIfPresent(root);
            return new AuthoringPaths(game, root);
        } catch (InvalidPathException failure) {
            throw new IOException("[NEKO-1019] Invalid authoring output directory.", failure);
        }
    }

    public Path gameDirectory() { return gameDirectory; }
    public Path root() throws IOException { return verify(root); }
    public Path bundled() throws IOException { return child("bundled"); }
    public Path current() throws IOException { return child("current.json"); }
    public Path environment() throws IOException { return child("environment.json"); }
    public Path connection() throws IOException { return child("connection.json"); }
    public Path snapshots() throws IOException { return child("snapshots"); }
    public Path agents() throws IOException { return child("agents"); }
    public Path instructions() throws IOException { return child("AGENTS.md"); }
    public Path bootstrap() throws IOException { return child("README.md"); }

    /**
     * Verifies existing read paths and missing write destinations against the captured physical root.
     * Recheck immediately before I/O: this rejects later symlink retargeting, not concurrent filesystem replacement.
     */
    public Path verify(Path path) throws IOException {
        if (path == null || !path.isAbsolute()) {
            throw new IOException("[NEKO-1019] An absolute authoring destination is required.");
        }
        if (!gameDirectory.toRealPath().equals(gameDirectory) || !canonical(root).equals(root)) {
            throw new IOException("[NEKO-1019] The selected authoring root changed on disk.");
        }
        requireDirectory(gameDirectory);
        requireDirectoryIfPresent(root);
        Path absolute = path.toAbsolutePath().normalize();
        if (!absolute.startsWith(root)) {
            throw new IOException("[NEKO-1019] Authoring path escapes the selected output directory.");
        }
        Path physical = canonical(absolute);
        if (!physical.startsWith(gameDirectory) || !physical.startsWith(root)) {
            throw new IOException("[NEKO-1019] Authoring path resolves outside the selected output directory.");
        }
        return physical;
    }

    private Path child(String name) throws IOException { return verify(root.resolve(name)); }

    private static void validateRelativeDirectory(String value) throws IOException {
        if (value == null || value.isBlank() || !value.equals(value.strip()) || value.startsWith("/")) {
            throw invalidDirectory();
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isISOControl(ch) || ch == '\\' || ch == '"' || ":<>|?*".indexOf(ch) >= 0) throw invalidDirectory();
        }
        for (String segment : value.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")
                    || segment.endsWith(".") || segment.endsWith(" ")) throw invalidDirectory();
        }
    }

    private static IOException invalidDirectory() {
        return new IOException("[NEKO-1019] Authoring output directory must be a normalized game-relative path using forward slashes.");
    }

    private static void requireDirectory(Path path) throws IOException {
        if (!Files.readAttributes(path, BasicFileAttributes.class).isDirectory()) {
            throw new IOException("[NEKO-1019] The selected authoring directory is not a directory.");
        }
    }

    private static void requireDirectoryIfPresent(Path path) throws IOException {
        try { requireDirectory(path); }
        catch (NoSuchFileException missing) {
            // A missing destination is valid; validation must not create it.
        }
    }

    private static Path canonical(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path ancestor = absolute;
        while (ancestor != null) {
            try {
                Files.readAttributes(ancestor, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            } catch (NoSuchFileException missing) {
                ancestor = ancestor.getParent();
                continue;
            }
            // Resolving an existing dangling link must fail, not be retried as a missing component.
            Path real = ancestor.toRealPath();
            if (ancestor.equals(absolute)) return real;
            requireDirectory(real);
            return real.resolve(ancestor.relativize(absolute));
        }
        throw new IOException("[NEKO-1019] No existing ancestor was found for the authoring path.");
    }
}
