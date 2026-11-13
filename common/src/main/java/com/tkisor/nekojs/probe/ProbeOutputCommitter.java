package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.NekoJS;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Commits probe artifacts: synchronously syncs the rendered 'relative path -> text' maps into the
 * output directory. The TS and Python built-in backends share it.
 *
 * <p><b>Internal tool</b>: it is public so built-in backends in other packages can share it, but
 * it is not a plugin extension point. Third-party backends may or may not use it when they manage
 * their own output, and its signature is not guaranteed to stay stable across versions.
 *
 * <h2>Why the directory is not renamed as a whole</h2>
 * The earlier implementation wrote to a staging directory, renamed the old directory to .old and
 * then renamed staging to the output directory. That does not work reliably on Windows: editors
 * consume the probe output directory, and VS Code or a TypeScript language service locks it, so
 * the rename throws {@link AccessDeniedException}. Retrying does not help because the handles are
 * released only when the editor closes. The commit therefore syncs each file in place:
 * <ol>
 *   <li>Skip files whose content is identical, so the editor does not reload for nothing and lock
 *       contention stays low;</li>
 *   <li>Overwrite files whose content changed, retrying with backoff when a reader briefly holds
 *       the file;</li>
 *   <li>Delete old files this run no longer produces; a failed delete is only a warning and does
 *       not fail the commit;</li>
 *   <li>Also remove empty directories and left-over {@code .staging}/{@code .old} directories.</li>
 * </ol>
 *
 * <p>The cost is that the switch is no longer atomic at directory level. The whole render result
 * is already in memory, so no file is ever half-written, but a write failure partway through
 * leaves the output directory partially updated. That trade-off is more practical than never
 * committing while an editor is open, and the failure message names the file that failed.
 */
public final class ProbeOutputCommitter {

    private static final int WRITE_ATTEMPTS = 4;
    private static final long WRITE_RETRY_DELAY_MS = 75L;

    private ProbeOutputCommitter() {
    }

    /** Statistics for an in-place sync. */
    public record CommitReport(int written, int unchanged, int deleted, List<String> warnings) {
        public CommitReport {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    /**
     * Validates the keys of the rendered artifacts and returns the first illegal relative path, or
     * null when all are legal. A key is illegal when it is null or blank, is absolute, contains a
     * {@code ..} segment, or contains an illegal path character such as NUL. A backend must not
     * have arbitrary filesystem write access, so paths outside the output directory are rejected
     * here.
     */
    public static String firstIllegalRelativePath(Iterable<String> keys) {
        for (String key : keys) {
            if (key == null || key.isBlank()) return String.valueOf(key);
            Path p;
            try {
                p = Path.of(key);
            } catch (RuntimeException e) {
                return key;
            }
            if (p.isAbsolute()) return key;
            for (Path seg : p) {
                if ("..".equals(seg.toString())) return key;
            }
        }
        return null;
    }

    /**
     * Syncs the render result into {@code outputDir} in place: writes files whose content changed,
     * skips identical ones and deletes old files this run no longer produces.
     *
     * @throws IOException a file still cannot be written after retries; the message names that file
     */
    public static CommitReport commitInPlace(Path outputDir, Map<String, String> files) throws IOException {
        Path root = outputDir.toAbsolutePath().normalize();
        Files.createDirectories(root);

        Set<Path> existing = listFiles(root);
        Set<Path> keep = new HashSet<>();
        int written = 0;
        int unchanged = 0;

        for (Map.Entry<String, String> entry : files.entrySet()) {
            Path file = root.resolve(entry.getKey()).normalize();
            if (!file.startsWith(root)) {
                throw new IOException("Probe output path escapes the output directory: " + entry.getKey());
            }
            keep.add(file);
            byte[] content = entry.getValue().getBytes(StandardCharsets.UTF_8);
            if (hasSameContent(file, content)) {
                unchanged++;
                continue;
            }
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            writeWithRetry(file, content);
            written++;
        }

        List<String> warnings = new ArrayList<>();
        int deleted = 0;
        // A stale file that cannot be deleted only becomes a warning: it does not affect the
        // correctness of the new artifacts, while throwing would waste the whole probe run.
        Set<String> undeletable = new TreeSet<>();
        for (Path stale : existing) {
            if (keep.contains(stale)) continue;
            if (deleteWithRetry(stale)) {
                deleted++;
            } else {
                undeletable.add(root.relativize(stale).toString());
            }
        }
        if (!undeletable.isEmpty()) {
            warnings.add("stale probe file(s) could not be removed (locked by another process?): " + undeletable);
            NekoJS.LOGGER.warn("Probe: {} stale file(s) under {} could not be removed: {}",
                    undeletable.size(), root, undeletable);
        }
        pruneEmptyDirs(root);
        warnings.addAll(cleanupLegacyDirs(outputDir));
        return new CommitReport(written, unchanged, deleted, warnings);
    }

    /**
     * Removes the {@code <outputDir>.staging} and {@code <outputDir>.old} directories left by the
     * old directory-swap approach. Best-effort: a failed delete only produces a warning, because
     * neither directory affects the new artifacts.
     */
    public static List<String> cleanupLegacyDirs(Path outputDir) {
        List<String> warnings = new ArrayList<>();
        String name = outputDir.getFileName() == null ? "" : outputDir.getFileName().toString();
        for (String suffix : List.of(".staging", ".old")) {
            Path legacy = outputDir.resolveSibling(name + suffix);
            if (!Files.exists(legacy)) continue;
            try {
                deleteRecursive(legacy);
                NekoJS.LOGGER.info("Probe: removed leftover directory {}", legacy);
            } catch (IOException e) {
                warnings.add("leftover directory could not be removed: " + legacy);
            }
        }
        return warnings;
    }

    /** Absolute normalized paths of all regular files under the directory; empty when it does not exist. */
    private static Set<Path> listFiles(Path root) throws IOException {
        if (!Files.exists(root)) return Set.of();
        Set<Path> found = new HashSet<>();
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile).forEach(p -> found.add(p.toAbsolutePath().normalize()));
        }
        return found;
    }

    /**
     * Whether the content matches what is already on disk exactly. A mismatch, a missing file and a
     * failed read all count as needing a write.
     *
     * <p>Byte length is compared first. A run produces thousands of files, so calling readAllBytes
     * on each of them is too expensive; when the lengths differ the content need not be read.
     */
    private static boolean hasSameContent(Path file, byte[] content) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) != content.length) return false;
            return Arrays.equals(Files.readAllBytes(file), content);
        } catch (IOException e) {
            return false;
        }
    }

    private static void writeWithRetry(Path file, byte[] content) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= WRITE_ATTEMPTS; attempt++) {
            try {
                Files.write(file, content);
                return;
            } catch (IOException e) {
                // A sharing conflict is an AccessDeniedException. When an editor memory-maps the file (the lock
                // shape of a TS language service), Windows reports a plain IOException whose message mentions the
                // user-mapped region. Both cases are retried and wrapped.
                last = e;
                if (attempt == WRITE_ATTEMPTS) break;
                sleepBackoff(attempt);
            }
        }
        throw new IOException("Probe output file is locked by another process: " + file
                + " (close the editor/TypeScript language server holding it and re-run /nekojs probe)", last);
    }

    private static boolean deleteWithRetry(Path file) {
        for (int attempt = 1; attempt <= WRITE_ATTEMPTS; attempt++) {
            try {
                Files.deleteIfExists(file);
                return true;
            } catch (IOException e) {
                if (attempt == WRITE_ATTEMPTS) return false;
                sleepBackoff(attempt);
            }
        }
        return false;
    }

    private static void sleepBackoff(int attempt) {
        try {
            Thread.sleep(WRITE_RETRY_DELAY_MS * attempt);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Deletes empty directories bottom-up and keeps the root directory itself. Failures are
     * silent: a left-over empty directory is harmless.
     */
    private static void pruneEmptyDirs(Path root) throws IOException {
        List<Path> dirs = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isDirectory).filter(p -> !p.equals(root)).forEach(dirs::add);
        }
        dirs.sort(Comparator.reverseOrder());
        for (Path dir : dirs) {
            try (var children = Files.list(dir)) {
                if (children.findAny().isPresent()) continue;
            } catch (IOException e) {
                continue;
            }
            try {
                Files.deleteIfExists(dir);
            } catch (IOException ignored) {
                // An empty directory that cannot be deleted does not matter; the next run tries again.
            }
        }
    }

    /**
     * Recursively deletes a directory and its contents, depth first in reverse order so files go
     * before the directory that holds them. The walk stream is closed with try-with-resources: a
     * leaked file handle keeps the directory locked and makes later deletes fail on Windows. The
     * paths that fail to delete are collected, warned about once (typically a Windows file lock)
     * and the exception is rethrown; the caller decides whether that counts as a failure.
     */
    public static void deleteRecursive(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        List<Path> failed = new ArrayList<>();
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            failed.add(p);
                        }
                    });
        }
        if (!failed.isEmpty()) {
            IOException error = new IOException("Failed to delete locked probe path(s): " + failed);
            NekoJS.LOGGER.warn("Probe: failed to delete {} path(s) under {} (locked by another process?): {}",
                    failed.size(), dir, failed, error);
            throw error;
        }
    }
}
