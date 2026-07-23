package com.tkisor.nekojs.core.compiler;

import java.nio.file.Path;
import java.util.Objects;

public record NekoSourceFile(Path path, String source, String extension) {
    public NekoSourceFile {
        path = Objects.requireNonNull(path, "path");
        source = source == null ? "" : source;
        extension = extension == null ? "" : extension;
    }
}
