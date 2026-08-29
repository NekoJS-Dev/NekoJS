package com.tkisor.nekojs.core.pack.sync;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.pack.ScriptPackManifest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 服务器下发包的客户端落盘缓存：{@code nekojs/server_packs/<bucket>/<syncId 编码>/}。
 *
 * <p>写入纪律：先整目录删除再重建（同 bucket 同包的旧文件不残留）；每个文件相对路径做
 * 路径穿越校验（绝对路径 / {@code ..} 逃逸一律拒绝）。写入后调用方必须
 * {@link #loadPack 从盘重扫}并重算哈希对照预期（写盘完整性自检）——本类不自行对照。
 */
public final class ServerPackCache {

    private ServerPackCache() {}

    /** bucket 目录：{@code nekojs/server_packs/<bucket>}（不存在则创建）。 */
    public static Path bucketDir(String bucket) {
        Path dir = NekoJSPaths.get().serverPacks().resolve(bucket);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            NekoJS.LOGGER.warn("Failed to create server pack cache bucket {}: {}", dir, e.toString());
        }
        return dir;
    }

    /** 落盘一个包：删旧目录 → 写 manifest 原文 → 按相对路径写内容文件（穿越校验）。 */
    public static void persistPack(Path bucketDir, String syncId, String manifestJson, List<PackContentFile> files) {
        Path packDir = bucketDir.resolve(SyncedPack.encodeSyncId(syncId));
        deleteRecursively(packDir);
        try {
            Files.createDirectories(packDir);
            Files.writeString(packDir.resolve(ScriptPackManifest.FILE_NAME), manifestJson, StandardCharsets.UTF_8);
            for (PackContentFile file : files) {
                Path target = resolveInside(packDir, file.relativePath());
                if (target == null) {
                    throw new IOException("Path traversal rejected: " + file.relativePath());
                }
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                Files.write(target, file.bytes());
            }
        } catch (IOException e) {
            NekoJS.LOGGER.warn("Failed to persist server pack {} to {}: {}", syncId, packDir, e.toString());
        }
    }

    /** 从盘重扫缓存的包：返回 manifest 原文 + 内容文件 + 重算哈希；目录缺失/无 manifest 返回 null。 */
    public static CachedPack loadPack(Path bucketDir, String syncId) {
        Path packDir = bucketDir.resolve(SyncedPack.encodeSyncId(syncId));
        if (!Files.isDirectory(packDir)) return null;
        try {
            byte[] manifestBytes = PackHasher.readManifestBytes(packDir);
            List<PackContentFile> files = PackHasher.readContentFiles(packDir);
            String manifestJson = new String(manifestBytes, StandardCharsets.UTF_8);
            return new CachedPack(manifestJson, files, PackHasher.hash(manifestBytes, files));
        } catch (Exception e) {
            NekoJS.LOGGER.warn("Failed to load cached server pack {}: {}", packDir, e.toString());
            return null;
        }
    }

    /** 路径穿越校验：相对路径 normalize 后必须仍在 packDir 内；非法返回 null。 */
    static Path resolveInside(Path packDir, String relativePath) {
        Path normalized = Path.of(relativePath).normalize();
        if (normalized.isAbsolute() || normalized.startsWith("..")) return null;
        Path resolved = packDir.resolve(normalized).normalize();
        return resolved.startsWith(packDir.normalize()) ? resolved : null;
    }

    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 单文件删除失败留给下一次整目录重建兜底
                }
            });
        } catch (IOException e) {
            NekoJS.LOGGER.warn("Failed to delete server pack cache dir {}: {}", dir, e.toString());
        }
    }

    /** 从盘重扫的包快照。 */
    public record CachedPack(String manifestJson, List<PackContentFile> files, String hash) {}
}
