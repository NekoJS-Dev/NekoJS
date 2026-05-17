package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.fs.NekoJSPaths;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NekoModuleResolver {
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
            baseDirectory = NekoJSPaths.ROOT;
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
        return new NekoResolvedModule(canonical, loaderPath(canonical), loaderPath(canonical.getParent()), null, kind, requestedMode(canonical));
    }

    private List<String> extensionsForCandidates() {
        List<String> extensions = new ArrayList<>(ScriptCompilerRegistry.current().supportedExtensionsInOrder());
        extensions.add(".json");
        return extensions;
    }

    private Path verifyModulePath(Path path) throws IOException {
        Path verified = NekoJSPaths.verifyInsideGameDir(path);
        if (Files.exists(verified)) {
            Path realPath = verified.toRealPath();
            if (!realPath.startsWith(NekoJSPaths.GAME_DIR.normalize().toAbsolutePath())) {
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
        Path resolved = parsed.isAbsolute() ? parsed : NekoJSPaths.ROOT.resolve(parsed);
        return verifyModulePath(resolved.normalize());
    }

    private NekoModuleKind specialKind(String specifier) throws IOException {
        if (isFileSpecifier(specifier)) {
            return null;
        }
        if (specifier.startsWith("java:")) {
            validateJavaPackageSpecifier(specifier);
            return NekoModuleKind.JAVA_PACKAGE;
        }
        if (specifier.startsWith("node:") || isBuiltinSpecifier(specifier)) {
            return NekoModuleKind.BUILTIN;
        }
        return NekoModuleKind.SPECIAL;
    }

    private void validateJavaPackageSpecifier(String specifier) throws IOException {
        String body = specifier.substring("java:".length());
        if (body.isBlank()) {
            throw new IOException("Java module package must not be blank");
        }
        int dot = body.lastIndexOf('.');
        String lastSegment = dot < 0 ? body : body.substring(dot + 1);
        if (!lastSegment.isEmpty() && Character.isUpperCase(lastSegment.charAt(0))) {
            throw new IOException("Invalid java: package module specifier: " + specifier + ". Use package-level modules like java:java.lang and named imports such as import { Integer } from 'java:java.lang'.");
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
        return isJson(path) || NekoJSPaths.isSupportedScriptFile(path);
    }

    private boolean isJson(Path path) {
        Path fileNamePath = path.getFileName();
        return fileNamePath != null && fileNamePath.toString().toLowerCase(Locale.ROOT).endsWith(".json");
    }

    private NekoModuleMode requestedMode(Path path) {
        Path fileNamePath = path.getFileName();
        if (fileNamePath == null) {
            return NekoModuleMode.AUTO;
        }
        String fileName = fileNamePath.toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".mjs")) {
            return NekoModuleMode.ESM;
        }
        if (fileName.endsWith(".cjs")) {
            return NekoModuleMode.COMMONJS;
        }
        return NekoModuleMode.AUTO;
    }

    private String loaderPath(Path path) {
        if (path == null) {
            return "";
        }
        Path absolute = path.normalize().toAbsolutePath();
        try {
            return NekoJSPaths.ROOT.relativize(absolute).toString().replace('\\', '/');
        } catch (IllegalArgumentException ignored) {
            return absolute.toString().replace('\\', '/');
        }
    }
}
