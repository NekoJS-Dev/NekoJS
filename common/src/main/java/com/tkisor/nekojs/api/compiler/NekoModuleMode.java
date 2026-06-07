package com.tkisor.nekojs.api.compiler;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Module format hint: auto-detect, force CommonJS, or force ESM.
 * Moved from {@code core.module} to {@code api.compiler} so that
 * {@link NekoIRProgram} in the api layer can reference it directly.
 */
public enum NekoModuleMode {
    AUTO,
    COMMONJS,
    ESM;

    public static NekoModuleMode fromExtension(String extension) {
        if (extension == null) {
            return AUTO;
        }
        return switch (extension.toLowerCase(Locale.ROOT).trim()) {
            case ".mjs", "mjs" -> ESM;
            case ".cjs", "cjs" -> COMMONJS;
            default -> AUTO;
        };
    }

    public static NekoModuleMode fromPath(Path path) {
        if (path == null || path.getFileName() == null) {
            return AUTO;
        }
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? AUTO : fromExtension(fileName.substring(dot));
    }
}
