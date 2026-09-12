package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.network.ErrorSummaryDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LocalErrorSourceTest {
    @TempDir
    Path gameDir;

    private NekoJSPaths paths;
    private LocalErrorSource source;

    @BeforeEach
    void setUp() throws IOException {
        paths = NekoJSPaths.fromGameDir(gameDir);
        source = LocalErrorSource.forPaths(paths);
        Files.createDirectories(paths.serverScripts());
    }

    @Test
    void colonClassificationOnlyRejectsSchemeLikeFirstSegmentOnEveryOs() {
        LocalErrorSource source = LocalErrorSource.forPaths(paths);

        // A colon in a later segment is a filesystem fact to verify, not a scheme fact.
        assertFalse(source.isVirtualPath("server_scripts/we:ird.js"));
        assertFalse(source.isVirtualPath("server_scripts\\we:ird.js"));
        assertFalse(source.isVirtualPath("/server_scripts/we:ird.js"));

        // Colons before the first separator remain virtual/module-like, including drive-relative forms.
        assertTrue(source.isVirtualPath("we:ird.js"));
        assertTrue(source.isVirtualPath("nekojs:/server_scripts/a.js"));
        assertTrue(source.isVirtualPath("truffle:module/x.js"));
        assertTrue(source.isVirtualPath("https://example.invalid/x.js"));
        assertTrue(source.isVirtualPath("<native-esm>"));

        // Complete Windows drive paths are ordinary absolute paths.
        assertFalse(source.isVirtualPath("C:/nekojs/server_scripts/a.js"));
        assertFalse(source.isVirtualPath("C:\\nekojs\\server_scripts\\a.js"));
    }

    @Test
    void normalScriptResolvesToVerifiedRealFileAndSafeGotoTarget() throws IOException {
        Path file = paths.serverScripts().resolve("normal.js");
        Files.writeString(file, "throw new Error('x')");

        LocalErrorSource.Result result = source.resolve(dto(relative(file), 12), true);

        assertTrue(result.available());
        assertEquals(file.toRealPath(), result.file());
        assertTrue(result.lineKnown());
        assertEquals(12, result.line());
        assertTrue(result.target().gotoLine());
        assertEquals(List.of("--goto", file.toRealPath() + ":12"), result.target().arguments());
    }

    @Test
    void absoluteScriptPathIsAcceptedButMissingAndNonRegularFilesAreNot() throws IOException {
        Path file = paths.serverScripts().resolve("absolute.js");
        Files.writeString(file, "ok");
        assertTrue(source.resolve(dto(file.toString(), -1), true).available());

        assertEquals(LocalErrorSource.Status.MISSING_FILE,
                source.resolve(dto(paths.serverScripts().resolve("missing.js").toString(), -1), true).status());

        Path directory = paths.serverScripts().resolve("directory.js");
        Files.createDirectory(directory);
        assertEquals(LocalErrorSource.Status.NOT_REGULAR_FILE, source.resolve(dto(directory.toString(), -1), true).status());
    }

    @Test
    void remoteServerNeverResolvesEvenALocalMatchingPath() throws IOException {
        Path file = paths.serverScripts().resolve("remote.js");
        Files.writeString(file, "local");

        LocalErrorSource.Result result = source.resolve(dto(relative(file), 5), false);

        assertEquals(LocalErrorSource.Status.REMOTE_SERVER, result.status());
        assertNull(result.file());
        assertNull(result.target());
    }

    @Test
    void invalidUnknownVirtualNetworkAndTraversalPathsAreRejected() throws IOException {
        assertStatus(LocalErrorSource.Status.NO_ERROR, source.resolve(null, true));

        assertStatus(LocalErrorSource.Status.INVALID_PATH, source.resolve(dto(null, -1), true));
        assertStatus(LocalErrorSource.Status.INVALID_PATH, source.resolve(dto("", -1), true));
        assertStatus(LocalErrorSource.Status.INVALID_PATH, source.resolve(dto("Unknown location", -1), true));
        assertStatus(LocalErrorSource.Status.INVALID_PATH, source.resolve(dto("UNKNOWN", -1), true));
        assertStatus(LocalErrorSource.Status.INVALID_PATH, source.resolve(dto("Unknown/2f58a7", -1), true));
        assertStatus(LocalErrorSource.Status.INVALID_PATH, source.resolve(dto("unknown source", -1), true));
        assertStatus(LocalErrorSource.Status.INVALID_PATH, source.resolve(dto("bad\0path.js", -1), true));
        assertStatus(LocalErrorSource.Status.INVALID_PATH, source.resolve(dto("\\\\server\\share\\x.js", -1), true));
        assertStatus(LocalErrorSource.Status.INVALID_PATH, source.resolve(dto("//server/share/x.js", -1), true));

        assertStatus(LocalErrorSource.Status.VIRTUAL_PATH, source.resolve(dto("truffle:module/x.js", -1), true));
        assertStatus(LocalErrorSource.Status.VIRTUAL_PATH, source.resolve(dto("https://example.invalid/x.js", -1), true));

        Path outside = gameDir.resolve("outside.js");
        Files.writeString(outside, "outside");
        assertStatus(LocalErrorSource.Status.OUTSIDE_SCRIPT_ROOT, source.resolve(dto("../outside.js", -1), true));
    }

    @Test
    void nekoRootFileOutsideScriptRootIsRejected() throws IOException {
        Path file = paths.config().resolve("engine.toml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "not a script");

        assertEquals(LocalErrorSource.Status.OUTSIDE_SCRIPT_ROOT,
                source.resolve(dto(relative(file), -1), true).status());
    }

    @Test
    void symlinkEscapeIsRejectedWhenSymlinksCanBeCreatedOnTheHost() throws IOException {
        Path outsideDirectory = gameDir.resolve("outside-directory");
        Files.createDirectories(outsideDirectory);
        Path outsideFile = outsideDirectory.resolve("outside.js");
        Files.writeString(outsideFile, "outside");
        Path link = paths.serverScripts().resolve("linked.js");
        try {
            Files.createSymbolicLink(link, outsideFile);
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.abort("host cannot create symlinks: " + e.getClass().getSimpleName());
            return;
        }

        assertEquals(LocalErrorSource.Status.OUTSIDE_SCRIPT_ROOT, source.resolve(dto(relative(link), 4), true).status());
    }

    @Test
    void symlinkedNekoRootCannotReplaceThePhysicalLocalRoot() throws IOException {
        Path isolatedGameDir = gameDir.resolve("isolated-game");
        Files.createDirectories(isolatedGameDir);
        Path outsideScripts = gameDir.resolve("outside-root").resolve("server_scripts");
        Files.createDirectories(outsideScripts);
        Files.writeString(outsideScripts.resolve("outside.js"), "outside");
        Path rootLink = isolatedGameDir.resolve("nekojs");
        try {
            Files.createSymbolicLink(rootLink, outsideScripts.getParent());
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.abort("host cannot create symlinks: " + e.getClass().getSimpleName());
            return;
        }

        LocalErrorSource isolatedSource = LocalErrorSource.forPaths(NekoJSPaths.fromGameDir(isolatedGameDir));
        LocalErrorSource.Result result = isolatedSource.resolve(dto("server_scripts/outside.js", 3), true);

        assertEquals(LocalErrorSource.Status.OUTSIDE_SCRIPT_ROOT, result.status());
        assertFalse(result.available());
        assertNull(result.file());
        assertNull(result.target());
    }

    @Test
    void vscodeUriEncodesSpacesUnicodeHashPercentAndQuestionMark() throws IOException {
        Path file = paths.serverScripts().resolve("空格 中文 #%.js");
        Files.writeString(file, "ok");
        LocalErrorSource.Result result = source.resolve(dto(relative(file), -1), true);

        String rawUri = result.target().vscodeFileUri().toString();
        assertTrue(rawUri.startsWith("vscode://file/"));
        assertWindowsDriveRootIsPreserved(file.toRealPath(), rawUri);
        assertFalse(rawUri.contains(" "));
        assertFalse(rawUri.contains("#"));
        assertFalse(rawUri.contains("空"));
        assertFalse(rawUri.contains("中文"));
        assertTrue(rawUri.contains("%25"), "literal percent must be encoded once");
        assertFalse(result.target().gotoLine());

        Assumptions.assumeTrue(!isWindows(), "question mark is not a legal Windows filename character");
        Path question = paths.serverScripts().resolve("question ?.js");
        Files.writeString(question, "ok");
        String questionUri = source.resolve(dto(relative(question), -1), true)
                .target().vscodeFileUri().toString();
        assertTrue(questionUri.contains("%3F"), questionUri);
        assertFalse(questionUri.contains("?"));
    }

    @Test
    void colonAfterWindowsDriveDisablesGotoSuffix() {
        assertTrue(LocalErrorSource.Target.lineSuffixIsUnambiguous("C:\\nekojs\\server_scripts\\normal.js"));
        assertFalse(LocalErrorSource.Target.lineSuffixIsUnambiguous("C:\\nekojs\\server_scripts\\we:ird.js"));
        assertFalse(LocalErrorSource.Target.lineSuffixIsUnambiguous("/nekojs/server_scripts/we:ird.js"));
    }

    @Test
    void colonInPosixFilenameDisablesGotoButStillOpensVerifiedFile() throws IOException {
        Assumptions.assumeTrue(!isWindows(), "colon is not a legal Windows filename character");
        Path file = paths.serverScripts().resolve("we:ird.js");
        Files.writeString(file, "ok");

        LocalErrorSource.Result result = source.resolve(dto(relative(file), 9), true);

        assertTrue(result.available());
        assertTrue(result.lineKnown());
        assertFalse(result.target().gotoLine());
        assertEquals(List.of(file.toRealPath().toString()), result.target().arguments());
    }

    private void assertStatus(LocalErrorSource.Status expected, LocalErrorSource.Result actual) {
        assertEquals(expected, actual.status());
        assertFalse(actual.available());
        assertNull(actual.file());
        assertNull(actual.target());
    }

    private String relative(Path file) {
        return paths.root().relativize(file).toString().replace('\\', '/');
    }

    private static void assertWindowsDriveRootIsPreserved(Path file, String rawUri) {
        if (!isWindows()) {
            return;
        }
        String root = file.getRoot().toString();
        Assumptions.assumeTrue(root.length() == 3, "test host does not use a drive-letter game directory");
        assertTrue(rawUri.startsWith("vscode://file/" + root.charAt(0) + ":/"),
                "Windows protocol URI must preserve the drive root; actual: " + rawUri);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    private static ErrorSummaryDTO dto(String path, int line) {
        return new ErrorSummaryDTO("error-id", path, line, 1, "boom", "details");
    }
}








