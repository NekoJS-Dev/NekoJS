package com.tkisor.nekojs.authoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AuthoringPathsTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {".github/nekojs", "github/nekojs", "nekojs/ai", "custom/docs/AI tools"})
    void resolvesExplicitDirectoriesAndChildrenWithoutCreatingAnything(String configured) throws Exception {
        Path game = Files.createDirectory(directory.resolve("game"));
        AuthoringPaths paths = AuthoringPaths.resolve(game, configured);
        Path root = game.toRealPath().resolve(configured);

        assertEquals(game.toRealPath(), paths.gameDirectory());
        assertEquals(root, paths.root());
        assertEquals(root.resolve("bundled"), paths.bundled());
        assertEquals(root.resolve("current.json"), paths.current());
        assertEquals(root.resolve("environment.json"), paths.environment());
        assertEquals(root.resolve("connection.json"), paths.connection());
        assertEquals(root.resolve("snapshots"), paths.snapshots());
        assertEquals(root.resolve("agents"), paths.agents());
        assertEquals(root.resolve("AGENTS.md"), paths.instructions());
        assertEquals(root.resolve("README.md"), paths.bootstrap());
        assertEquals(root.resolve("snapshots/new/nested.json"), paths.verify(root.resolve("snapshots/new/nested.json")));
        assertFalse(Files.exists(root));
        assertEmpty(game);
    }

    @Test
    void rejectsAbsoluteTraversalAndInvalidValuesWithoutWriting() throws Exception {
        Path game = Files.createDirectory(directory.resolve("game"));
        assertThrows(IOException.class, () -> AuthoringPaths.resolve(game, null));
        for (String value : List.of("", " ", ".", "..", "../outside", "a/../outside", "./ai", "a/./ai",
                "/absolute", "//server/share", "C:/ai", "C:ai", "C:\\ai", "\\\\server\\share",
                "a\\b", "a:b", "a//b", "a/", "a?b", "a*b", "a|b", "a<b", "a>b", "a\"b",
                "a\u0000b", "a\nb", " ai", "ai ", "ai.", "a. /b")) {
            assertThrows(IOException.class, () -> AuthoringPaths.resolve(game, value), value);
        }
        assertEmpty(game);
    }

    @Test
    void rejectsMissingOrNonDirectoryGameAndNonDirectoryOutput() throws Exception {
        assertThrows(IOException.class, () -> AuthoringPaths.resolve(null, "ai"));
        assertThrows(IOException.class, () -> AuthoringPaths.resolve(directory.resolve("missing"), "ai"));
        Path file = Files.writeString(directory.resolve("file"), "unchanged");
        assertThrows(IOException.class, () -> AuthoringPaths.resolve(file, "ai"));
        assertThrows(IOException.class, () -> AuthoringPaths.resolve(directory, "file"));
        assertThrows(IOException.class, () -> AuthoringPaths.resolve(directory, "file/nested"));
        assertEquals("unchanged", Files.readString(file));
    }

    @Test
    void verifiesExistingReadAndMissingWritePathsButRejectsSiblingPrefixAndRelativeInputs() throws Exception {
        Path game = Files.createDirectory(directory.resolve("game"));
        Path root = Files.createDirectories(game.resolve("output"));
        Path existing = Files.writeString(root.resolve("current.json"), "retained");
        AuthoringPaths paths = AuthoringPaths.resolve(game, "output");

        assertEquals(existing.toRealPath(), paths.current());
        assertEquals(root.toRealPath().resolve("missing/deeper.json"), paths.verify(root.resolve("missing/deeper.json")));
        assertThrows(IOException.class, () -> paths.verify(game.resolve("output-other/file.json")));
        assertThrows(IOException.class, () -> paths.verify(root.resolve("../outside.json")));
        assertThrows(IOException.class, () -> paths.verify(Path.of("current.json")));
        assertThrows(IOException.class, () -> paths.verify(null));
        assertEquals("retained", Files.readString(existing));
        assertFalse(Files.exists(root.resolve("missing")));
    }

    @Test
    void rejectsConfiguredSymlinkEscapesIncludingMissingNestedSuffix() throws Exception {
        Path game = Files.createDirectory(directory.resolve("game"));
        Path outside = Files.createDirectory(directory.resolve("outside"));
        link(game.resolve("escape"), outside);

        assertThrows(IOException.class, () -> AuthoringPaths.resolve(game, "escape"));
        assertThrows(IOException.class, () -> AuthoringPaths.resolve(game, "escape/missing/deeper"));
        assertEmpty(outside);
    }

    @Test
    void canonicalizesInternalAliasesAndKeepsResolvedRootIndependentOfAliasChanges() throws Exception {
        Path game = Files.createDirectory(directory.resolve("game"));
        Path real = Files.createDirectory(game.resolve("physical"));
        Path alias = game.resolve("alias");
        link(alias, real);
        AuthoringPaths paths = AuthoringPaths.resolve(game, "alias/missing");
        Path expected = real.toRealPath().resolve("missing");
        assertEquals(expected, paths.root());

        Files.delete(alias);
        Path outside = Files.createDirectory(directory.resolve("outside"));
        link(alias, outside);
        assertEquals(expected, paths.root());
        assertEquals(expected.resolve("current.json"), paths.current());
        assertFalse(Files.exists(expected));
        assertEmpty(outside);
    }

    @Test
    void rejectsChildSymlinksForExistingReadsAndMissingWritesEvenWithinGameDirectory() throws Exception {
        Path game = Files.createDirectory(directory.resolve("game"));
        Path root = Files.createDirectory(game.resolve("ai"));
        Path sibling = Files.createDirectory(game.resolve("not-ai"));
        Path existing = Files.writeString(sibling.resolve("existing.json"), "unchanged");
        AuthoringPaths paths = AuthoringPaths.resolve(game, "ai");
        link(root.resolve("snapshots"), sibling);
        link(root.resolve("current.json"), existing);

        assertThrows(IOException.class, paths::snapshots);
        assertThrows(IOException.class, paths::current);
        assertThrows(IOException.class, () -> paths.verify(root.resolve("snapshots/existing.json")));
        assertThrows(IOException.class, () -> paths.verify(root.resolve("snapshots/new/nested.json")));
        assertEquals("unchanged", Files.readString(existing));
        assertFalse(Files.exists(sibling.resolve("new")));
    }

    @Test
    void rejectsRootCreatedAsAnEscapingSymlinkAfterResolution() throws Exception {
        Path game = Files.createDirectory(directory.resolve("game"));
        Path outside = Files.createDirectory(directory.resolve("outside"));
        AuthoringPaths paths = AuthoringPaths.resolve(game, "ai");
        link(game.resolve("ai"), outside);

        assertThrows(IOException.class, paths::root);
        assertThrows(IOException.class, paths::environment);
        assertThrows(IOException.class, () -> paths.verify(game.resolve("ai/missing/file")));
        assertEmpty(outside);
    }

    @Test
    void rejectsDanglingSymlinksRatherThanTreatingThemAsMissingDirectories() throws Exception {
        Path game = Files.createDirectory(directory.resolve("game"));
        link(game.resolve("dangling"), directory.resolve("does-not-exist"));
        assertThrows(IOException.class, () -> AuthoringPaths.resolve(game, "dangling/nested"));
        AuthoringPaths paths = AuthoringPaths.resolve(game, "ai");
        Files.createDirectory(game.resolve("ai"));
        link(game.resolve("ai/current.json"), game.resolve("ai/missing.json"));
        assertThrows(IOException.class, paths::current);
    }

    private static void assertEmpty(Path directory) throws IOException {
        try (var entries = Files.list(directory)) {
            assertEquals(0, entries.count(), "path resolution must not create output");
        }
    }

    private static void link(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | FileSystemException unavailable) {
            assumeTrue(false, "Symbolic links are unavailable on this filesystem: " + unavailable);
        }
    }
}
