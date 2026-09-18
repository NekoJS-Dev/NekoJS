package com.tkisor.nekojs.core.module;

import java.nio.file.Path;

/** Read-only view of the runtime-owned virtual ESM source registry. */
public interface NekoVirtualModuleView {
    boolean isVirtualModule(Path path);

    boolean isVirtualDirectory(Path path);

    boolean isVirtualPath(Path path);

    String source(Path path);

    String displayPath(Path path);

    String displayPath(String pathOrUri);
}
