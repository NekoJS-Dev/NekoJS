package com.tkisor.nekojs.core.error;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 真实 VS Code 分派器。
 *
 * <p>Windows 只启动绝对路径的 {@code Code.exe}，绝不执行 {@code .cmd/.bat} shim。CLI 优先用
 * argv 携带 {@code --goto} 行号；每个 exe 尝试失败后先尝试下一个可信安装，再退回 OS 的
 * {@code vscode://file/...} 协议分派。协议分派只打开文件、不伪造行号。</p>
 */
public final class VsCodeProcessOpener implements ErrorLocationOpener {
    private final Environment environment;
    private final Launcher launcher;
    private final ProtocolFallback protocolFallback;

    public VsCodeProcessOpener() {
        this(
                VsCodeProcessOpener::systemSetting,
                new ProcessLauncher(),
                new DesktopProtocolFallback()
        );
    }

    VsCodeProcessOpener(Environment environment, Launcher launcher, boolean protocolFallback) {
        this(environment, launcher, protocolFallback ? new DesktopProtocolFallback() : null);
    }

    VsCodeProcessOpener(
            Environment environment,
            Launcher launcher,
            ProtocolFallback protocolFallback
    ) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.launcher = Objects.requireNonNull(launcher, "launcher");
        this.protocolFallback = protocolFallback;
    }

    @Override
    public OpenResult open(LocalErrorSource.Target target) {
        Objects.requireNonNull(target, "target");
        List<Path> commands;
        try {
            commands = findCommands();
        } catch (IOException | RuntimeException e) {
            return OpenResult.failed("VS Code executable discovery failed: " + e.getClass().getSimpleName());
        }

        if (!commands.isEmpty()) {
            String launchFailure = launchThroughCommands(target, commands);
            if (launchFailure == null) {
                return OpenResult.accepted(target.gotoLine());
            }
            OpenResult fallback = dispatchProtocol(target);
            return fallback.accepted()
                    ? fallback
                    : OpenResult.failed(launchFailure + "; " + fallback.failure());
        }
        return dispatchProtocol(target);
    }

    private String launchThroughCommands(LocalErrorSource.Target target, List<Path> commands) {
        StringBuilder failure = new StringBuilder("VS Code executable launch failed");
        boolean first = true;
        for (Path command : commands) {
            List<String> argv = new ArrayList<>(1 + target.arguments().size());
            argv.add(command.toString());
            argv.addAll(target.arguments());
            try {
                launcher.launch(argv);
                return null;
            } catch (IOException | RuntimeException e) {
                if (first) {
                    failure.append(": ");
                    first = false;
                } else {
                    failure.append(", ");
                }
                failure.append(e.getClass().getSimpleName());
            }
        }
        return failure.toString();
    }

    private OpenResult dispatchProtocol(LocalErrorSource.Target target) {
        if (protocolFallback == null) {
            return OpenResult.failed("VS Code executable not found");
        }
        try {
            protocolFallback.dispatch(target.vscodeFileUri());
            // URL 分派只请求打开文件；此路径不携带 --goto 的行列参数。
            return OpenResult.accepted(false);
        } catch (IOException | RuntimeException | LinkageError e) {
            return OpenResult.failed("VS Code protocol dispatch failed: " + e.getClass().getSimpleName());
        }
    }

    List<Path> findCommands() throws IOException {
        String os = environment.get("os.name");
        String normalizedOs = os == null ? "" : os.toLowerCase(Locale.ROOT);
        boolean windows = normalizedOs.startsWith("windows");
        boolean mac = normalizedOs.startsWith("mac");
        Set<Path> result = new LinkedHashSet<>();

        if (windows) {
            addWindowsCommands(result);
        } else if (mac) {
            addMacCommands(result);
        } else {
            addPathCommands(result, "code", false);
        }
        return List.copyOf(result);
    }

    private void addWindowsCommands(Set<Path> result) {
        // Empty and relative PATH entries are deliberately ignored. A game-controlled working
        // directory must not be able to supply Code.exe through a relative PATH entry.
        for (String entry : pathEntries()) {
            Path directory = parseAbsoluteDirectory(entry);
            if (directory == null) {
                continue;
            }
            addLaunchable(result, directory.resolve("Code.exe"), true);
            if ("bin".equals(lastName(directory))) {
                addLaunchable(result, directory.getParent().resolve("Code.exe"), true);
            }
        }
        addInstallRoot(result, environment.get("LOCALAPPDATA"), "Programs/Microsoft VS Code");
        for (String variable : List.of("ProgramFiles", "ProgramFiles(x86)", "ProgramW6432")) {
            addInstallRoot(result, environment.get(variable), "Microsoft VS Code");
        }
    }

    private void addMacCommands(Set<Path> result) {
        addPathCommands(result, "code", false);
        addLaunchable(
                result,
                Path.of("/Applications/Visual Studio Code.app/Contents/Resources/app/bin/code"),
                false
        );
    }

    private void addPathCommands(Set<Path> result, String name, boolean windows) {
        for (String entry : pathEntries()) {
            Path directory = parseAbsoluteDirectory(entry);
            if (directory != null) {
                addLaunchable(result, directory.resolve(name), windows);
            }
        }
    }

    private void addInstallRoot(Set<Path> result, String root, String suffix) {
        if (root == null || root.isBlank()) {
            return;
        }
        try {
            addLaunchable(result, Path.of(root, suffix).resolve("Code.exe"), true);
        } catch (InvalidPathException ignored) {
            // A malformed installation environment variable is not a dispatchable program.
        }
    }

    private static Path parseAbsoluteDirectory(String entry) {
        if (entry == null || entry.isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(entry).normalize();
            return path.isAbsolute() ? path : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private List<String> pathEntries() {
        String os = environment.get("os.name");
        String normalizedOs = os == null ? "" : os.toLowerCase(Locale.ROOT);
        String path = environment.get(normalizedOs.startsWith("windows") ? "Path" : "PATH");
        if (path == null || path.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String entry : path.split(String.valueOf(File.pathSeparatorChar))) {
            if (!entry.isBlank()) {
                result.add(entry);
            }
        }
        return result;
    }

    private static void addLaunchable(Set<Path> result, Path path, boolean windows) {
        if (path != null
                && path.isAbsolute()
                && Files.isRegularFile(path)
                && Files.isReadable(path)
                && (windows || Files.isExecutable(path))) {
            result.add(path.normalize());
        }
    }

    private static String lastName(Path path) {
        return path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
    }

    private static String systemSetting(String name) {
        if ("os.name".equals(name)) {
            return System.getProperty(name);
        }
        return System.getenv(name);
    }

    interface Environment {
        String get(String name);
    }

    interface Launcher {
        void launch(List<String> argv) throws IOException;
    }

    interface ProtocolFallback {
        void dispatch(URI uri) throws IOException;
    }

    private static final class ProcessLauncher implements Launcher {
        @Override
        public void launch(List<String> argv) throws IOException {
            // 启动即返回：这个结果表示分派请求已接受，不声称 VS Code 窗口已经打开。
            new ProcessBuilder(argv)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        }
    }

    private static final class DesktopProtocolFallback implements ProtocolFallback {
        @Override
        public void dispatch(URI uri) throws IOException {
            if (!Desktop.isDesktopSupported()) {
                throw new UnsupportedOperationException("desktop is unsupported");
            }
            Desktop.getDesktop().browse(uri);
        }
    }
}
