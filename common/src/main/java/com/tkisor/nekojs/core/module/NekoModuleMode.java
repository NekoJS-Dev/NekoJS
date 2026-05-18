package com.tkisor.nekojs.core.module;

import java.nio.file.Path;
import java.util.Locale;

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
