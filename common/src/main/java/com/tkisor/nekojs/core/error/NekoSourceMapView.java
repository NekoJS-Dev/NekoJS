package com.tkisor.nekojs.core.error;

/** Read-only source-map lookup view used by execution diagnostics. */
@FunctionalInterface
public interface NekoSourceMapView {
    SourceMapRegistry.OriginalPosition getMappedPosition(String scriptPath, int jsLine, int jsColumn);
}
