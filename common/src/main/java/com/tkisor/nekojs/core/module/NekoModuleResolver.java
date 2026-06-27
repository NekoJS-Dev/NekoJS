package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.fs.NekoJSPaths;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 实例化模块解析器：构造器接收 {@link NekoJSPaths} 和 {@link ScriptFilePolicy}。
 * 负责 entry resolve → file module resolve → extension candidates → index fallback。
 */
public final class NekoModuleResolver {
    private final NekoJSPaths paths;
    private final ScriptFilePolicy filePolicy;
    private final ScriptCompilerRegistry compilers;

    public NekoModuleResolver(NekoJSPaths paths, ScriptFilePolicy filePolicy, ScriptCompilerRegistry compilers) {
        this.paths = paths;
        this.filePolicy = filePolicy;
        this.compilers = compilers;
    }

    public NekoModuleResolver() {
        this(NekoJSPaths.get(), ScriptFilePolicy.legacyRuntime(), ScriptCompilerRegistry.current());
    }

    public NekoResolvedModule resolveEntry(String entryPath) throws IOException {
        return resolveFileModule(pathFromLoaderPath(entryPath));
    }

    public NekoResolvedModule resolve(String parentPath, String specifier) throws IOException {
        if (specifier == null || specifier.isBlank()) {
            throw new IOException("Module specifier must not be blank");
        }
        NekoModuleKind specialKind = specialKind(specifier);
        if (specialKind != null) {
            return NekoResolvedModule.special(specifier, specialKind);
        }
        Path parent = pathFromLoaderPath(parentPath);
        Path baseDirectory = Files.isDirectory(parent) ? parent : parent.getParent();
        if (baseDirectory == null) {
            baseDirectory = paths.root();
        }
        return resolveFileModule(baseDirectory.resolve(specifier).normalize());
    }

    private NekoResolvedModule resolveFileModule(Path requested) throws IOException {
        Path verified = verifyModulePath(requested);
        if (Files.isRegularFile(verified)) {
            if (!isLoadableModule(verified)) {
                throw new IOException("Unsupported module file type: " + loaderPath(verified));
            }
            return moduleRecord(verified);
        }
        if (hasExtension(verified)) {
            throw new IOException("Module file does not exist: " + loaderPath(verified));
        }

        for (String extension : extensionsForCandidates()) {
            Path candidate = verifyModulePath(verified.resolveSibling(verified.getFileName() + extension));
            if (Files.isRegularFile(candidate)) {
                return moduleRecord(candidate);
            }
        }
        if (Files.isDirectory(verified)) {
            for (String extension : extensionsForCandidates()) {
                Path candidate = verifyModulePath(verified.resolve("index" + extension));
                if (Files.isRegularFile(candidate)) {
                    return moduleRecord(candidate);
                }
            }
        }
        throw new IOException("Cannot resolve module: " + loaderPath(verified));
    }

    private NekoResolvedModule moduleRecord(Path path) throws IOException {
        Path verified = verifyModulePath(path);
        Path canonical = verified.toRealPath();
        NekoModuleKind kind = isJson(canonical) ? NekoModuleKind.JSON : NekoModuleKind.SCRIPT;
        return new NekoResolvedModule(canonical, loaderPath(canonical), loaderPath(canonical.getParent()), null, kind);
    }

    private List<String> extensionsForCandidates() {
        return filePolicy.candidateExtensionsWithJson();
    }

    private Path verifyModulePath(Path path) throws IOException {
        Path verified = paths.verifyInsideGameDir(path);
        if (Files.exists(verified)) {
            Path realPath = verified.toRealPath();
            if (!realPath.startsWith(paths.gameDir().normalize().toAbsolutePath())) {
                throw new IOException("Symlink escape detected: " + realPath);
            }
        }
        return verified;
    }

    private Path pathFromLoaderPath(String path) throws IOException {
        if (path == null || path.isBlank()) {
            throw new IOException("Module path must not be blank");
        }
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("file:")) {
            return verifyModulePath(Path.of(URI.create(normalized)).normalize());
        }
        if (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        Path parsed = Path.of(normalized);
        Path resolved = parsed.isAbsolute() ? parsed : paths.root().resolve(parsed);
        return verifyModulePath(resolved.normalize());
    }

    private NekoModuleKind specialKind(String specifier) throws IOException {
        if (isFileSpecifier(specifier)) {
            return null;
        }
        if (isJavaSpecifier(specifier)) {
            validateJavaSpecifier(specifier);
            return NekoModuleKind.JAVA_MODULE;
        }
        if (specifier.startsWith("node:") || isBuiltinSpecifier(specifier)) {
            return NekoModuleKind.BUILTIN;
        }
        return NekoModuleKind.SPECIAL;
    }

    private boolean isJavaSpecifier(String specifier) {
        return specifier.startsWith("java:");
    }

    private void validateJavaSpecifier(String specifier) throws IOException {
        String body = specifier.substring("java:".length()).replace('\\', '/').trim();
        if (body.isBlank()) {
            throw new IOException("Java module specifier must not be blank");
        }
        if (body.startsWith("/") || body.endsWith("/") || body.contains("..") || body.contains(".")) {
            throw new IOException("Invalid Java module specifier: " + specifier);
        }
        if (!body.matches("[A-Za-z_$][A-Za-z0-9_$]*(/[A-Za-z_$][A-Za-z0-9_$]*)*")) {
            throw new IOException("Invalid Java module specifier: " + specifier);
        }
    }

    private boolean isBuiltinSpecifier(String specifier) {
        return specifier.equals("fs")
                || specifier.equals("path")
                || specifier.equals("util")
                || specifier.equals("assert")
                || specifier.equals("test")
                || specifier.equals("timers")
                || specifier.equals("process")
                || specifier.equals("events")
                || specifier.equals("buffer")
                || specifier.equals("module");
    }

    private boolean isFileSpecifier(String specifier) {
        return specifier.startsWith("./")
                || specifier.startsWith("../")
                || specifier.startsWith("/")
                || specifier.matches("^[A-Za-z]:[\\\\/].*");
    }

    private boolean hasExtension(Path path) {
        Path fileNamePath = path.getFileName();
        if (fileNamePath == null) return false;
        String fileName = fileNamePath.toString();
        return fileName.lastIndexOf('.') > 0;
    }

    private boolean isLoadableModule(Path path) {
        return isJson(path) || filePolicy.isSupportedScriptFile(path);
    }

    private boolean isJson(Path path) {
        Path fileNamePath = path.getFileName();
        return fileNamePath != null && fileNamePath.toString().toLowerCase(Locale.ROOT).endsWith(".json");
    }

    private String loaderPath(Path path) {
        if (path == null) {
            return "";
        }
        Path absolute = path.normalize().toAbsolutePath();
        try {
            return paths.root().relativize(absolute).toString().replace('\\', '/');
        } catch (IllegalArgumentException ignored) {
            return absolute.toString().replace('\\', '/');
        }
    }
}
