package com.tkisor.nekojs.core.error;

/** Read-only source-map lookup view used by execution diagnostics. */
@FunctionalInterface
public interface NekoSourceMapView {
    SourceMapRegistry.OriginalPosition getMappedPosition(String scriptPath, int jsLine, int jsColumn);

    /**
     * Cache key of the prepared module that registered the source map for this generated
     * path (ticket 30 cache/revision retention), or {@code null} when unknown. Implementations
     * that do not track revisions keep returning {@code null}.
     */
    default String mappedCacheRevision(String scriptPath) {
        return null;
    }
}
