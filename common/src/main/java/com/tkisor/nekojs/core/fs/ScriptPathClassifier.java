package com.tkisor.nekojs.core.fs;

import com.tkisor.nekojs.api.ScriptType;

import java.nio.file.FileSystem;
import java.nio.file.Path;

/** Internal path-segment classifier; all script/package cleanup uses the injected provider. */
public final class ScriptPathClassifier {
    private ScriptPathClassifier() {}

    public static ScriptType fromSegment(Path segment) {
        if (segment == null) return null;
        FileSystem fileSystem = segment.getFileSystem();
        Path candidate = fileSystem.getPath(segment.toString());
        for (ScriptType type : ScriptType.all()) {
            if (candidate.equals(fileSystem.getPath(type.scriptsDirectoryName()))) {
                return type;
            }
        }
        return null;
    }

    /** Scan the full path so GLOBAL/WORLD/SERVER_CACHE package layouts share one rule. */
    public static ScriptType fromPath(Path path) {
        if (path == null) return null;
        for (Path segment : path) {
            ScriptType type = fromSegment(segment);
            if (type != null) return type;
        }
        return null;
    }
}
