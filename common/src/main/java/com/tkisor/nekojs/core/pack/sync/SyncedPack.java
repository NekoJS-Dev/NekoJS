package com.tkisor.nekojs.core.pack.sync;

import java.util.List;

/**
 * Snapshot of one script pack to distribute; the platform-independent transfer unit. Carries the
 * {@code syncId} (scope segment and pack id, for example {@code packs:demo} or
 * {@code worldpacks:demo}), the scope enum name, the raw manifest JSON, the content file list and
 * the pack hash. Each platform converts between this record and its own payload or ByteBuf
 * encoding.
 */
public record SyncedPack(
    String syncId,
    String scopeName,
    String hash,
    String manifestJson,
    List<PackContentFile> files
) {

    public SyncedPack {
        files = List.copyOf(files);
    }

    /** Encodes a syncId as a directory name: {@code packs:demo} → {@code packs_demo}. The scope
     * segment is fixed and contains no underscore, and the pack id is sanitized to
     * {@code [a-z0-9_-]}, so replacing {@code :} is reversible and collision-free. */
    public static String encodeSyncId(String syncId) {
        return syncId.replace(':', '_');
    }

    public static SyncedPack of(String syncId, String scopeName, String hash, String manifestJson, List<PackContentFile> files) {
        return new SyncedPack(syncId, scopeName, hash, manifestJson, files);
    }
}
