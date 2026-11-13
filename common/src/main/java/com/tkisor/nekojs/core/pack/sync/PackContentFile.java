package com.tkisor.nekojs.core.pack.sync;

import java.util.Arrays;

/**
 * One file of pack content, the unit of transfer and hashing: its path relative to the pack
 * ({@code /}-separated, starting at {@code startup_scripts/...}) and its raw bytes. The path is
 * checked for traversal when {@link ServerPackCache#persistPack} writes the pack to disk.
 */
public record PackContentFile(String relativePath, byte[] bytes) {

    public PackContentFile {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Pack content file path must be non-blank");
        }
        bytes = bytes == null ? new byte[0] : bytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PackContentFile other)) return false;
        return relativePath.equals(other.relativePath) && Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return 31 * relativePath.hashCode() + Arrays.hashCode(bytes);
    }
}
