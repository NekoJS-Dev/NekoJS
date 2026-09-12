package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.network.ErrorSummaryDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ErrorOpenServiceTest {
    @TempDir
    Path gameDir;

    private NekoJSPaths paths;
    private Path file;

    @BeforeEach
    void setUp() throws IOException {
        paths = NekoJSPaths.fromGameDir(gameDir);
        Files.createDirectories(paths.serverScripts());
        file = paths.serverScripts().resolve("open.js");
        Files.writeString(file, "throw new Error('x')");
    }

    @Test
    void clickResolvesValidatesAndDispatchesOnProvidedExecutor() throws IOException {
        RecordingOpener opener = new RecordingOpener(null);
        ErrorOpenService service = new ErrorOpenService(LocalErrorSource.forPaths(paths), opener);

        ErrorOpenService.Result result = service.openAsync(dto(relative(file), 7), true, Runnable::run).join();

        assertEquals(ErrorOpenService.Outcome.DISPATCH_ACCEPTED, result.outcome());
        assertTrue(result.accepted());
        assertTrue(result.lineRequested());
        assertEquals(LocalErrorSource.Status.LOCAL_FILE, result.locationStatus());
        assertEquals(1, opener.targets.size());
        assertEquals(file.toRealPath(), opener.targets.getFirst().file());
    }

    @Test
    void remoteLocationIsReportedWithoutCallingOpener() {
        RecordingOpener opener = new RecordingOpener(null);
        ErrorOpenService service = new ErrorOpenService(LocalErrorSource.forPaths(paths), opener);

        ErrorOpenService.Result result = service.openAsync(dto(relative(file), 7), false, Runnable::run).join();

        assertEquals(ErrorOpenService.Outcome.LOCATION_UNAVAILABLE, result.outcome());
        assertEquals(LocalErrorSource.Status.REMOTE_SERVER, result.locationStatus());
        assertTrue(opener.targets.isEmpty());
    }

    @Test
    void openerFailureAndExceptionDoNotThrowOrCrashCaller() {
        ErrorOpenService service = new ErrorOpenService(
                LocalErrorSource.forPaths(paths), new RecordingOpener("injected failure"));
        assertEquals(ErrorOpenService.Outcome.DISPATCH_FAILED,
                service.openAsync(dto(relative(file), -1), true, Runnable::run).join().outcome());

        service = new ErrorOpenService(LocalErrorSource.forPaths(paths), target -> {
            throw new IllegalStateException("boom");
        });
        ErrorOpenService.Result result = service.openAsync(dto(relative(file), -1), true, Runnable::run).join();

        assertEquals(ErrorOpenService.Outcome.DISPATCH_FAILED, result.outcome());
        assertEquals("IllegalStateException", result.detail());
    }

    @Test
    void executorRejectionBecomesFailedResultInsteadOfThrowingToScreen() {
        ErrorOpenService service = new ErrorOpenService(
                LocalErrorSource.forPaths(paths), new RecordingOpener(null));

        ErrorOpenService.Result result = service.openAsync(
                dto(relative(file), -1), true,
                command -> { throw new java.util.concurrent.RejectedExecutionException("closed"); }
        ).join();

        assertEquals(ErrorOpenService.Outcome.DISPATCH_FAILED, result.outcome());
        assertEquals("RejectedExecutionException", result.detail());
    }

    @Test
    void missingFileIsRevalidatedAtClickTime() {
        Path missing = paths.serverScripts().resolve("selected-then-deleted.js");
        ErrorOpenService service = new ErrorOpenService(LocalErrorSource.forPaths(paths), new RecordingOpener(null));

        ErrorOpenService.Result result = service.openAsync(dto(relative(missing), -1), true, Runnable::run).join();

        assertEquals(ErrorOpenService.Outcome.LOCATION_UNAVAILABLE, result.outcome());
        assertEquals(LocalErrorSource.Status.MISSING_FILE, result.locationStatus());
    }

    @Test
    void processOpenerPrefersCodeExeBesideBinAndNeverUsesCmd() throws IOException {
        Path install = Files.createDirectories(gameDir.resolve("VS Code"));
        Path bin = Files.createDirectories(install.resolve("bin"));
        Files.writeString(bin.resolve("code.cmd"), "test fixture; never launched");
        Path exe = install.resolve("Code.exe");
        Files.writeString(exe, "test fixture; never launched");

        List<String> launched = new ArrayList<>();
        VsCodeProcessOpener opener = new VsCodeProcessOpener(
                name -> windowsEnvironment(Map.of("Path", bin.toString())).get(name),
                (VsCodeProcessOpener.Launcher) argv -> launched.addAll(argv),
                false
        );
        ErrorLocationOpener.OpenResult result = opener.open(targetWithoutLine());

        assertTrue(result.accepted());
        assertEquals(List.of(exe.toString(), file.toRealPath().toString()), launched);
        assertFalse(launchedCmdWasLaunched(launched));
    }

    @Test
    void programFilesInstallIsSupportedWithoutPath() throws IOException {
        Path install = Files.createDirectories(gameDir.resolve("Microsoft VS Code"));
        Path exe = install.resolve("Code.exe");
        Files.writeString(exe, "test fixture; never launched");

        List<String> launched = new ArrayList<>();
        VsCodeProcessOpener opener = new VsCodeProcessOpener(
                name -> windowsEnvironment(Map.of("ProgramFiles", gameDir.toString())).get(name),
                (VsCodeProcessOpener.Launcher) argv -> launched.addAll(argv),
                false
        );
        ErrorLocationOpener.OpenResult result = opener.open(targetWithoutLine());

        assertTrue(result.accepted());
        assertEquals(List.of(exe.toString(), file.toRealPath().toString()), launched);
    }

    @Test
    void cmdAndRelativePathEntryDoNotBecomeExecutableAndFallbackIsExplicit() throws IOException {
        Path relativeBin = Files.createDirectories(gameDir.resolve("relative-bin"));
        Files.writeString(relativeBin.resolve("code.cmd"), "must not launch");
        Files.writeString(gameDir.resolve("Code.exe"), "must not launch from working directory");

        List<String> launched = new ArrayList<>();
        VsCodeProcessOpener opener = new VsCodeProcessOpener(
                name -> windowsEnvironment(Map.of(
                        "Path", "relative-bin;;",
                        "LOCALAPPDATA", gameDir.resolve("does-not-exist").toString()
                )).get(name),
                (VsCodeProcessOpener.Launcher) argv -> launched.addAll(argv),
                false
        );
        ErrorLocationOpener.OpenResult result = opener.open(targetWithoutLine());

        assertEquals(ErrorLocationOpener.Status.FAILED, result.status());
        assertEquals("VS Code executable not found", result.failure());
        assertTrue(launched.isEmpty());
    }

    @Test
    void failedExeLaunchFallsBackToProtocolWithoutClaimingLine() throws IOException {
        Path install = Files.createDirectories(gameDir.resolve("Microsoft VS Code"));
        Files.writeString(install.resolve("Code.exe"), "test fixture; never launched");
        List<String> attempted = new ArrayList<>();
        List<URI> dispatched = new ArrayList<>();
        VsCodeProcessOpener opener = new VsCodeProcessOpener(
                name -> windowsEnvironment(Map.of("ProgramFiles", gameDir.toString())).get(name),
                (VsCodeProcessOpener.Launcher) argv -> {
                    attempted.addAll(argv);
                    throw new IOException("blocked");
                },
                (VsCodeProcessOpener.ProtocolFallback) dispatched::add
        );
        ErrorLocationOpener.OpenResult result = opener.open(targetWithoutLine());

        assertTrue(result.accepted());
        assertFalse(result.lineRequested());
        assertEquals(2, attempted.size(), "one Code.exe command plus one file argument");
        assertEquals(List.of(targetWithoutLine().vscodeFileUri()), dispatched);
    }

    private static Map<String, String> windowsEnvironment(Map<String, String> overrides) {
        Map<String, String> values = new HashMap<>();
        values.put("os.name", "Windows NT");
        values.put("Path", "");
        values.put("LOCALAPPDATA", "");
        values.put("ProgramFiles", "");
        values.put("ProgramFiles(x86)", "");
        values.put("ProgramW6432", "");
        values.putAll(overrides);
        return values;
    }

    private LocalErrorSource.Target targetWithoutLine() throws IOException {
        Path realFile = file.toRealPath();
        return new LocalErrorSource.Target(realFile, URI.create("vscode://file/test"), false, -1);
    }

    private static boolean launchedCmdWasLaunched(List<String> argv) {
        return argv.stream().anyMatch(part -> part.endsWith("code.cmd"));
    }

    private String relative(Path target) {
        return paths.root().relativize(target).toString().replace('\\', '/');
    }

    private static ErrorSummaryDTO dto(String path, int line) {
        return new ErrorSummaryDTO("id", path, line, 1, "boom", "details");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");
    }

    private static final class RecordingOpener implements ErrorLocationOpener {
        private final String failure;
        private final List<LocalErrorSource.Target> targets = new ArrayList<>();

        private RecordingOpener(String failure) {
            this.failure = failure;
        }

        @Override
        public OpenResult open(LocalErrorSource.Target target) {
            targets.add(target);
            return failure == null ? OpenResult.accepted(target.gotoLine()) : OpenResult.failed(failure);
        }
    }
}








