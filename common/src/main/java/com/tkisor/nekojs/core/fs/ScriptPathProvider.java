package com.tkisor.nekojs.core.fs;

import com.tkisor.nekojs.api.ScriptType;

import java.nio.file.FileSystem;
import java.nio.file.Path;

/**
 * Provider-owned path facts shared by execution/cache owners. The classifier implementation
 * remains package-private so callers do not depend on its segment algorithm.
 */
public final class ScriptPathProvider {
    private ScriptPathProvider() {}

    public static ScriptType typeOf(Path path) {
        return ScriptPathLayout.typeOf(path);
    }

    public static ScriptType typeOfSegment(Path segment) {
        return ScriptPathLayout.typeOfSegment(segment);
    }

    public static String authoredPath(Path path) {
        return ScriptPathLayout.authoredPath(path);
    }

    public static String authoredPathText(String pathText, FileSystem fileSystem) {
        return ScriptPathLayout.authoredPathText(pathText, fileSystem);
    }
}
