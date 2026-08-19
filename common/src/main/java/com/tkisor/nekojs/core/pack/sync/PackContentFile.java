package com.tkisor.nekojs.core.pack.sync;

import java.util.Arrays;

/**
 * 包内容文件（传输/哈希单位）：包内相对路径（{@code /} 分隔，{@code startup_scripts/...} 起）
 * 与原始字节。路径在 {@link ServerPackCache#persistPack 落盘} 时做路径穿越校验。
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
