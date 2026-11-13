package com.tkisor.nekojs.core.fs;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused path checks for {@link NekoJSPaths}: confinement to gameDir and the nekojs root,
 * relative resolution, the script sync allowlist, and rejection of symlink escapes.
 */
class NekoJSPathsTest {

    @TempDir
    Path gameDir;

    @Test
    void verifyInsideGameDirAllowsRelativeWithinRoot() throws Exception {
        NekoJSPaths paths = pathsFor(gameDir);
        Path script = gameDir.resolve("nekojs/server_scripts/a.js");
        Files.createDirectories(script.getParent());
        Files.writeString(script, "console.log('x')");

        assertEquals(script.toRealPath(), paths.verifyInsideGameDir(script));
        assertEquals(script.toRealPath(), paths.verifyInsideGameDir(gameDir.resolve("nekojs/../nekojs/server_scripts/a.js")));
    }

    @Test
    void verifyInsideGameDirRejectsTraversalAndAbsoluteOutside() throws Exception {
        NekoJSPaths paths = pathsFor(gameDir);

        assertThrows(IOException.class, () -> paths.verifyInsideGameDir(gameDir.resolve("../outside.js")));
        assertThrows(IOException.class, () -> paths.verifyInsideGameDir(outsideRootAbsPath()));
        // A path that symlinks to an outside directory (if the host allows creating symlinks).
        Path link = gameDir.resolve("nekojs/link.js");
        try {
            Files.createSymbolicLink(link, gameDir.toAbsolutePath().getRoot());
            Assumptions.assumeTrue(Files.exists(link), "symlink creation unsupported, skipping");
            assertThrows(IOException.class, () -> paths.verifyInsideGameDir(link),
                    "symlink escape must be rejected");
        } catch (UnsupportedOperationException | IOException ignored) {
            // The host does not allow creating symlinks: skip the escape assertion.
        }
    }

    @Test
    void resolveGamePathResolvesAgainstWorkingDirectory() throws Exception {
        NekoJSPaths paths = pathsFor(gameDir);
        Path script = gameDir.resolve("nekojs/server_scripts/b.js");
        Files.createDirectories(script.getParent());
        Files.writeString(script, "console.log('y')");

        Path cwd = gameDir.resolve("nekojs/server_scripts");
        assertEquals(script.toRealPath(), paths.resolveGamePath("b.js", cwd));
        assertEquals(script.toRealPath(), paths.resolveGamePath("./b.js", cwd));
        assertEquals(script.toRealPath(), paths.resolveGamePath("nekojs/server_scripts/b.js", gameDir));
        // Out of bounds: three levels up from server_scripts leaves gameDir.
        assertThrows(IOException.class, () -> paths.resolveGamePath("../../../outside.js", cwd));
        assertThrows(IOException.class, () -> paths.resolveGamePath(outsideRootAbsPath().toString(), cwd));
    }

    @Test
    void resolveNekoWritePathIsConfinedToNekoRoot() throws Exception {
        NekoJSPaths paths = pathsFor(gameDir);
        Path root = gameDir.resolve("nekojs");

        // Resolved relative to the nekojs root (the root itself is the cwd); a missing path comes back
        // in canonical form (the real prefix plus the missing suffix).
        assertEquals(gameDir.toRealPath().resolve("nekojs/cache/data.json"),
                paths.resolveNekoWritePath("cache/data.json", root));
        // Writing outside the nekojs root (straight into gameDir) is rejected.
        assertThrows(IOException.class, () -> paths.resolveNekoWritePath("../data.json", root));
    }

    @Test
    void verifyInsideGameDirForCreateAllowsDeepMissingPaths() throws Exception {
        NekoJSPaths paths = pathsFor(gameDir);
        Path deep = gameDir.resolve("nekojs/cache/new/dir/file.json");

        // A missing path comes back in canonical form: the real prefix plus the missing suffix,
        // consistent across platforms and spellings.
        assertEquals(gameDir.toRealPath().resolve("nekojs/cache/new/dir/file.json"),
                paths.verifyInsideGameDirForCreate(deep));
    }

    /**
     * Regression for the Windows 8.3 short name and case alias: the CI runner's
     * {@code java.io.tmpdir} is the short form {@code C:\Users\RUNNER~1\...}, while
     * {@code toRealPath()} resolves it to the long name. Mixing the two forms once made every
     * existing file fail with "Symlink escape detected", and Windows players with non-ASCII user
     * names hit the same problem. The test simulates that mismatch on the same filesystem with a
     * case-flipped alias, and is skipped when the filesystem is case-sensitive (the alias does not
     * exist).
     */
    @Test
    void verifyInsideGameDirToleratesRealFormAliasOfRoot() throws Exception {
        Path alias = aliasByCase(gameDir);
        // Path.equals is case-insensitive on Windows, so compare strings to confirm the spelling really differs.
        Assumptions.assumeTrue(!alias.toString().equals(gameDir.toString()) && Files.exists(alias),
                "filesystem is case-sensitive or alias does not resolve — nothing to simulate");

        NekoJSPaths paths = pathsFor(gameDir);
        Path script = alias.resolve("nekojs/server_scripts/alias.js");
        Files.createDirectories(script.getParent());
        Files.writeString(script, "console.log('alias')");

        assertEquals(script.toRealPath(), paths.verifyInsideGameDir(script),
                "existing file addressed via alias form must resolve to its real form, not be rejected");
        assertEquals(script.getParent().toRealPath().resolve("not-yet.js"),
                paths.verifyInsideGameDirForCreate(alias.resolve("nekojs/server_scripts/not-yet.js")),
                "missing path addressed via alias form must pass via its existing ancestor and come back in real form");
        assertTrue(paths.isInsideScriptRoot(script),
                "alias-form script path must still be recognized as inside a script root");
    }

    /** Flips the case of the whole path: it resolves to the same path on a case-insensitive filesystem
     * and does not exist on a case-sensitive one. */
    private static Path aliasByCase(Path path) {
        StringBuilder sb = new StringBuilder(path.toString().length());
        for (int i = 0; i < path.toString().length(); i++) {
            char c = path.toString().charAt(i);
            sb.append(Character.isLowerCase(c) ? Character.toUpperCase(c) : Character.toLowerCase(c));
        }
        return Path.of(sb.toString());
    }

    private static NekoJSPaths pathsFor(Path gameDir) throws Exception {
        Constructor<NekoJSPaths> constructor = NekoJSPaths.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(gameDir);
    }

    /**
     * An absolute path outside gameDir that works on every platform: it uses the filesystem root of
     * gameDir. On Windows that is {@code C:\nekojs-test-outside\evil.js}, on Linux CI it is
     * {@code /nekojs-test-outside/evil.js}; both are absolute and necessarily outside gameDir.
     * A hardcoded {@code C:/...} is a relative path on Linux, so it would resolve inside gameDir and
     * the assertion would no longer test anything.
     */
    private Path outsideRootAbsPath() {
        return gameDir.toAbsolutePath().getRoot().resolve("nekojs-test-outside/evil.js");
    }
}
