package com.tkisor.nekojs.core.pack.sync;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.pack.ScriptPackManifest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
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
    private static volatile IOException nextCommitFailureForTests;

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
            writePack(packDir, manifestJson, files);
        } catch (IOException e) {
            NekoJS.LOGGER.warn("Failed to persist server pack {} to {}: {}", syncId, packDir, e.toString());
        }
    }

    /** Create a same-filesystem staging root for one bundle replacement. */
    static Path createStagingRoot(Path bucketDir) throws IOException {
        Files.createDirectories(bucketDir);
        return Files.createTempDirectory(bucketDir, ".nekojs-stage-");
    }

    /** Write one pack below a staging root without touching the active bucket entry. */
    static void stagePack(Path stagingRoot, String syncId, String manifestJson,
                          List<PackContentFile> files) throws IOException {
        Path packDir = resolvePackDir(stagingRoot, syncId);
        deleteRecursively(packDir);
        writePack(packDir, manifestJson, files);
    }

    /**
     * Replace staged pack directories while retaining the previous physical directories until
     * the caller commits the runtime replacement. A failed reload can therefore restore the
     * exact old files, including a replacement with the same sync id.
     */
    static PhysicalReplacement replaceStaged(Path stagingRoot, Path bucketDir,
                                              Collection<String> syncIds) throws IOException {
        PhysicalReplacement replacement = new PhysicalReplacement(stagingRoot, bucketDir);
        try {
            for (String syncId : syncIds) {
                replacement.replace(syncId);
            }
            return replacement;
        } catch (IOException failure) {
            try {
                replacement.restore();
            } catch (IOException cleanupFailure) {
                throw new ReplacementFailure("Failed to restore server pack files after replacement setup failure",
                        replacement, failure, cleanupFailure);
            }
            throw failure;
        }
    }

    private static Path resolvePackDir(Path root, String syncId) throws IOException {
        Path packDir = root.resolve(SyncedPack.encodeSyncId(syncId)).normalize();
        if (!packDir.startsWith(root.normalize())) {
            throw new IOException("Invalid server pack id: " + syncId);
        }
        return packDir;
    }

    private static void writePack(Path packDir, String manifestJson, List<PackContentFile> files)
            throws IOException {
        if (manifestJson == null || files == null) {
            throw new IOException("Server pack manifest/files must not be null");
        }
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
        if (relativePath == null) return null;
        Path normalized = Path.of(relativePath).normalize();
        if (normalized.isAbsolute() || normalized.startsWith("..")) return null;
        Path resolved = packDir.resolve(normalized).normalize();
        return resolved.startsWith(packDir.normalize()) ? resolved : null;
    }

    static void deleteRecursively(Path dir) {
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

    /** Strict deletion used by transactional replacement; a failed delete must reach the caller. */
    static void deleteRecursivelyStrict(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) return;
        List<Path> paths;
        try (Stream<Path> stream = Files.walk(dir)) {
            paths = stream.sorted(Comparator.reverseOrder()).toList();
        }
        IOException failure = null;
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
                if (Files.exists(path)) {
                    throw new IOException("Path still exists after delete: " + path);
                }
            } catch (IOException deleteFailure) {
                if (failure == null) failure = deleteFailure;
                else failure.addSuppressed(deleteFailure);
            }
        }
        if (failure != null) throw failure;
        if (Files.exists(dir)) {
            throw new IOException("Directory still exists after delete: " + dir);
        }
    }

    /** Deterministic failure seam for the physical-commit rollback tests. */
    static void failNextCommitForTests(IOException failure) {
        nextCommitFailureForTests = failure;
    }

    /** 从盘重扫的包快照。 */
    public record CachedPack(String manifestJson, List<PackContentFile> files, String hash) {}

    static final class PhysicalReplacement {
        private final Path stagingRoot;
        private final Path bucketDir;
        private final Path backupRoot;
        private final RecursiveDelete deleteOperation;
        private final List<Entry> entries = new ArrayList<>();
        private boolean restored;

        PhysicalReplacement(Path stagingRoot, Path bucketDir) throws IOException {
            this(stagingRoot, bucketDir, ServerPackCache::deleteRecursivelyStrict);
        }

        PhysicalReplacement(Path stagingRoot, Path bucketDir, RecursiveDelete deleteOperation) throws IOException {
            this.stagingRoot = stagingRoot;
            this.bucketDir = bucketDir;
            this.backupRoot = stagingRoot.resolve(".rollback");
            this.deleteOperation = deleteOperation;
            Files.createDirectories(backupRoot);
        }

        void replace(String syncId) throws IOException {
            Path staged = resolvePackDir(stagingRoot, syncId);
            Path target = resolvePackDir(bucketDir, syncId);
            Path backup = resolvePackDir(backupRoot, syncId);
            if (!Files.isDirectory(staged)) {
                throw new IOException("Staged server pack is missing: " + syncId);
            }
            Entry entry = new Entry(target, backup, Files.exists(target));
            entries.add(entry);
            if (entry.hadOriginal()) {
                Files.createDirectories(backup.getParent());
                Files.move(target, backup);
            }
            Files.move(staged, target);
        }

        void restore() throws IOException {
            if (restored) return;
            IOException failure = null;
            for (int i = entries.size() - 1; i >= 0; i--) {
                Entry entry = entries.get(i);
                try {
                    deleteOperation.delete(entry.target());
                    if (entry.hadOriginal() && Files.exists(entry.backup())) {
                        Files.move(entry.backup(), entry.target());
                    } else if (entry.hadOriginal()) {
                        throw new IOException("Rollback backup is missing: " + entry.backup());
                    }
                } catch (IOException cleanupFailure) {
                    if (failure == null) failure = cleanupFailure;
                    else failure.addSuppressed(cleanupFailure);
                }
            }
            if (failure != null) throw failure;
            deleteOperation.delete(stagingRoot);
            restored = true;
        }

        void commit() throws IOException {
            IOException injected = nextCommitFailureForTests;
            nextCommitFailureForTests = null;
            if (injected != null) throw injected;
            deleteOperation.delete(stagingRoot);
            restored = true;
        }

        private record Entry(Path target, Path backup, boolean hadOriginal) {}
    }

    @FunctionalInterface
    interface RecursiveDelete {
        void delete(Path path) throws IOException;
    }

    static final class ReplacementFailure extends IOException {
        private final PhysicalReplacement replacement;

        ReplacementFailure(String message, PhysicalReplacement replacement,
                           IOException original, IOException cleanupFailure) {
            super(message, original);
            this.replacement = replacement;
            addSuppressed(cleanupFailure);
        }

        PhysicalReplacement replacement() {
            return replacement;
        }
    }
}
