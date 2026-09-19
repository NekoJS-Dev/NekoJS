package com.tkisor.nekojs.core.fs;

import com.tkisor.nekojs.api.ScriptType;

import java.nio.file.Path;
import java.nio.file.FileSystem;

/** Narrow cross-package bridge for the package-private script path facts. */
final class ScriptPathLayout {
    private ScriptPathLayout() {}

    static ScriptType typeOf(Path path) {
        return ScriptPathClassifier.fromPath(path);
    }

    static ScriptType typeOfSegment(Path segment) {
        return ScriptPathClassifier.fromSegment(segment);
    }

    static String authoredPath(Path path) {
        return ScriptPathClassifier.authoredPath(path);
    }

    static String authoredPathText(String pathText, FileSystem fileSystem) {
        return ScriptPathClassifier.authoredPathText(pathText, fileSystem);
    }
}
