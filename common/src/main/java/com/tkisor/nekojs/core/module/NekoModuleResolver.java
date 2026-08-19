package com.tkisor.nekojs.core.module;

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

    public NekoModuleResolver(NekoJSPaths paths, ScriptFilePolicy filePolicy) {
        this.paths = paths;
        this.filePolicy = filePolicy;
    }

    public NekoModuleResolver() {
        this(NekoJSPaths.get(), ScriptFilePolicy.legacyRuntime());
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
        if (!isFileSpecifier(specifier)) {
            return resolveBareModule(specifier);
        }
        Path parent = pathFromLoaderPath(parentPath);
        Path baseDirectory = Files.isDirectory(parent) ? parent : parent.getParent();
        if (baseDirectory == null) {
            baseDirectory = paths.root();
        }
        return resolveFileModule(baseDirectory.resolve(specifier).normalize());
    }

    /**
     * require 语义的严格解析：与 {@link #resolve} 相同，但 bare specifier 在 node_modules
     * 未命中时**不降级为 SPECIAL**（SPECIAL 兜底只服务于 define.js 的 builtin/java: 分类），
     * 直接抛错——对应 Node 的 {@code require.resolve} MODULE_NOT_FOUND 语义。
     */
    public NekoResolvedModule resolveForRequire(String parentPath, String specifier) throws IOException {
        NekoResolvedModule resolved = resolve(parentPath, specifier);
        if (resolved.special() && resolved.kind() == NekoModuleKind.SPECIAL) {
            throw new IOException("Cannot resolve module: " + specifier);
        }
        return resolved;
    }

    private NekoResolvedModule resolveBareModule(String specifier) throws IOException {
        Path nodeModules = paths.nodeModules().toAbsolutePath().normalize();
        Path requested = nodeModules.resolve(specifier).normalize();
        try {
            return resolveFileModule(requested, nodeModules);
        } catch (IOException exception) {
            if (isMissingModule(exception)) {
                return NekoResolvedModule.special(specifier, NekoModuleKind.SPECIAL);
            }
            throw exception;
        }
    }

    private boolean isMissingModule(IOException exception) {
        String message = exception.getMessage();
        return message != null && (message.startsWith("Module file does not exist:")
                || message.startsWith("Cannot resolve module:"));
    }

    private NekoResolvedModule resolveFileModule(Path requested) throws IOException {
        return resolveFileModule(requested, null);
    }

    private NekoResolvedModule resolveFileModule(Path requested, Path containmentRoot) throws IOException {
        Path verified = verifyModulePath(requested, containmentRoot);
        if (Files.isRegularFile(verified)) {
            if (!isLoadableModule(verified)) {
                throw new IOException("Unsupported module file type: " + loaderPath(verified));
            }
            return moduleRecord(verified, containmentRoot);
        }
        if (hasExtension(verified)) {
            throw new IOException("Module file does not exist: " + loaderPath(verified));
        }

        for (String extension : extensionsForCandidates()) {
            Path candidate = verifyModulePath(verified.resolveSibling(verified.getFileName() + extension), containmentRoot);
            if (Files.isRegularFile(candidate)) {
                return moduleRecord(candidate, containmentRoot);
            }
        }
        if (Files.isDirectory(verified)) {
            for (String extension : extensionsForCandidates()) {
                Path candidate = verifyModulePath(verified.resolve("index" + extension), containmentRoot);
                if (Files.isRegularFile(candidate)) {
                    return moduleRecord(candidate, containmentRoot);
                }
            }
        }
        throw new IOException("Cannot resolve module: " + loaderPath(verified));
    }

    private NekoResolvedModule moduleRecord(Path path, Path containmentRoot) throws IOException {
        Path verified = verifyModulePath(path, containmentRoot);
        Path canonical = verified.toRealPath();
        NekoModuleKind kind = isJson(canonical) ? NekoModuleKind.JSON : NekoModuleKind.SCRIPT;
        return new NekoResolvedModule(canonical, loaderPath(canonical), loaderPath(canonical.getParent()), null, kind);
    }

    private List<String> extensionsForCandidates() {
        return filePolicy.candidateExtensionsWithJson();
    }

    private Path verifyModulePath(Path path) throws IOException {
        return verifyModulePath(path, null);
    }

    private Path verifyModulePath(Path path, Path containmentRoot) throws IOException {
        Path verified = paths.verifyInsideGameDir(path);
        if (containmentRoot != null && !verified.toAbsolutePath().normalize().startsWith(containmentRoot)) {
            throw new IOException("Bare module path escapes node_modules: " + loaderPath(verified));
        }
        if (Files.exists(verified)) {
            Path realPath = verified.toRealPath();
            if (!realPath.startsWith(paths.gameDir().normalize().toAbsolutePath())) {
                throw new IOException("Symlink escape detected: " + realPath);
            }
            if (containmentRoot != null && !realPath.startsWith(containmentRoot.toRealPath())) {
                throw new IOException("Bare module path escapes node_modules: " + loaderPath(realPath));
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
        return null;
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
        // 与 resources/nekojs/node/modules.list 注册表保持同步：bare 内置名必须在此声明，
        // 否则只有在 node_modules 未装同名包时才会经 SPECIAL 兜底命中（优先级与 Node 相反）
        return specifier.equals("fs")
                || specifier.equals("path")
                || specifier.equals("util")
                || specifier.equals("assert")
                || specifier.equals("test")
                || specifier.equals("timers")
                || specifier.equals("process")
                || specifier.equals("events")
                || specifier.equals("buffer")
                || specifier.equals("module")
                || specifier.equals("os")
                || specifier.equals("crypto");
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
