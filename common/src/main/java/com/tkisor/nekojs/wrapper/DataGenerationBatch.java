package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.core.fs.NekoJSPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * One non-Assets data generation batch (ticket 23): candidate collection → validation →
 * atomic publish, shared by the plugin {@code generateData} hook and the script
 * {@code ServerEvents.generateData} event.
 *
 * <p>Lifecycle (driven by {@code PluginGenerationHooks.runGenerateData}):
 * <ol>
 *   <li>{@link #open}: create a fresh candidate directory outside the pack-visible root
 *       ({@code <sibling>/.nekojs-datagen/<stage>/candidate}); all generator writes land there,
 *       so the active pack root is never touched during collection.</li>
 *   <li>{@link #validate}: every candidate file is re-checked for candidate-root inclusion,
 *       JSON files must parse (structure), and every file must be readable back.</li>
 *   <li>{@link #publish}: publish surviving files into the active root with per-file
 *       atomic moves plus a backup/rollback journal, so a mid-publish failure restores the
 *       previous active content (failure retention). Files that exist in the active root but
 *       are not listed in the previous manifest are treated as user-owned and are never
 *       overwritten — the write is skipped and reported instead.</li>
 * </ol>
 *
 * <p>Overwrite policy within one batch: last contributor wins (a script may intentionally
 * override what a plugin produced); every cross-contributor overwrite is recorded in
 * {@link PublishResult#overwriteTrace()} with both owners. Toward the active root the batch
 * only replaces files it published before AND whose content still matches the recorded
 * sha256 — anything else (never-generated files, or generated files edited by hand) is
 * user-owned: the write is skipped and reported, never overwritten. Files produced by
 * earlier batches but absent from the current batch are never deleted: script-generated data
 * is not unconditionally regenerable (data-protection inventory row 16), so the manifest only
 * accumulates.
 *
 * <p>On validation or publish failure the candidate directory is kept for diagnostics; the
 * next {@link #open} for the same stage wipes it. {@link #discard()} deletes it explicitly.
 */
public class DataGenerationBatch {

    /** Sibling state directory (never inside the pack root, so the pack never serves it). */
    public static final String STATE_DIR_NAME = ".nekojs-datagen";

    private final Path activeRoot;
    private final String stage;
    private final Path stateDir;
    private final Path candidateDir;
    private final Path backupDir;
    private final Path manifestFile;
    private final DataGeneratorJS generator;
    private boolean published;

    private String contributor;
    private final Map<String, String> writeOwners = new LinkedHashMap<>();
    private final List<String> overwriteTrace = new ArrayList<>();
    /** Test-only fault injection: when set, a publish move whose TARGET matches fails. */
    private java.util.function.Predicate<Path> moveFailure;

    private DataGenerationBatch(Path activeRoot, String stage) {
        this.activeRoot = activeRoot;
        this.stage = stage;
        Path parent = activeRoot.getParent();
        this.stateDir = (parent == null ? activeRoot : parent).resolve(STATE_DIR_NAME).resolve(stage);
        this.candidateDir = stateDir.resolve("candidate");
        this.backupDir = stateDir.resolve("backup");
        this.manifestFile = stateDir.resolve("manifest.json");
        this.generator = new DataGeneratorJS(candidateDir, stage);
        this.generator.setWriteObserver(this::recordWrite);
    }

    /**
     * Open a fresh batch for {@code stage}: wipes stale candidate/backup scratch of the same
     * stage, recreates the candidate area, and returns a batch whose generator writes only
     * into the candidate area.
     */
    public static DataGenerationBatch open(Path activeRoot, String stage) throws IOException {
        DataGenerationBatch batch = new DataGenerationBatch(activeRoot, stage == null ? "" : stage);
        deleteTree(batch.candidateDir);
        deleteTree(batch.backupDir);
        Files.createDirectories(batch.candidateDir);
        return batch;
    }

    /** The generator shared by every contributor of this batch (plugins and scripts). */
    public DataGeneratorJS generator() {
        return generator;
    }

    /** Active root this batch publishes into (the pack-visible data root). */
    public Path activeRoot() {
        return activeRoot;
    }

    /** Stage label carried by the generator (dispatch key of the script event). */
    public String stage() {
        return stage;
    }

    /** Package-private candidate area location (tests and diagnostics only). */
    Path stateCandidate() {
        return candidateDir;
    }

    /**
     * Label the contributor that is currently writing through the generator. Used for
     * per-file owner attribution (plugin FQN or {@code "script"}); {@code null} clears it.
     */
    public void setContributor(String label) {
        this.contributor = label;
    }

    private void recordWrite(String relativePath, int bytes) {
        String owner = contributor == null ? "" : contributor;
        String previous = writeOwners.put(relativePath, owner);
        if (previous != null && !previous.equals(owner)) {
            overwriteTrace.add(relativePath + " (" + previous + " -> " + owner + ")");
        } else if (previous != null) {
            overwriteTrace.add(relativePath + " (" + owner + " rewrite)");
        }
    }

    /**
     * Validate the candidate area: every regular file must stay inside the candidate root,
     * every {@code .json} file must parse, and every file must be readable back. Returns the
     * validated relative paths in sorted order.
     */
    public List<String> validate() throws IOException {
        List<Path> files = candidateFiles();
        List<String> violations = new ArrayList<>();
        for (Path file : files) {
            String relative = candidateDir.relativize(file).toString().replace('\\', '/');
            if (!NekoJSPaths.isInside(file, candidateDir)) {
                violations.add(relative + ": escapes the candidate root");
                continue;
            }
            String content;
            try {
                content = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                violations.add(relative + ": unreadable (" + e.getMessage() + ")");
                continue;
            }
            if (relative.toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
                try {
                    JsonParser.parseString(content);
                } catch (RuntimeException e) {
                    violations.add(relative + ": invalid JSON (" + e.getMessage() + ")");
                }
            }
        }
        if (!violations.isEmpty()) {
            throw new IllegalStateException("generateData candidate validation failed for stage '"
                    + stage + "'; nothing was published and the previous active data is retained: "
                    + String.join("; ", violations));
        }
        return files.stream().map(f -> candidateDir.relativize(f).toString().replace('\\', '/')).toList();
    }

    /**
     * Validate then publish the candidate files into the active root. Publishing is a single
     * journaled pass: existing generator-owned files are backed up before the atomic move, and
     * any mid-publish failure rolls every already-published file back so the previous active
     * content survives. On success the candidate area is removed, the generator is re-pointed
     * at the active root for read-back, further writes are rejected, and the manifest is
     * updated.
     */
    public PublishResult publish() throws IOException {
        if (published) {
            throw new IllegalStateException("generateData batch for stage '" + stage + "' is already published");
        }
        List<String> relativeFiles = validate();
        Map<String, String> previousManifest = readManifest();

        Files.createDirectories(activeRoot);
        deleteTree(backupDir);
        Files.createDirectories(backupDir);

        List<String> publishedFiles = new ArrayList<>();
        List<String> backedUpFiles = new ArrayList<>();
        List<String> skippedUserFiles = new ArrayList<>();
        try {
            for (String relative : relativeFiles) {
                Path source = candidateDir.resolve(relative);
                Path target = activeRoot.resolve(relative);
                if (Files.exists(target) && !isUnchangedGeneratorOutput(target, relative, previousManifest)) {
                    // Either never produced by NekoJS, or produced but modified afterwards:
                    // user-owned content — never overwritten, the write is skipped and reported.
                    skippedUserFiles.add(relative);
                    continue;
                }
                if (Files.exists(target)) {
                    Path backup = backupDir.resolve(relative);
                    Files.createDirectories(backup.getParent());
                    moveForPublish(target, backup);
                    backedUpFiles.add(relative);
                }
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                moveForPublish(source, target);
                publishedFiles.add(relative);
            }
        } catch (IOException failure) {
            rollback(publishedFiles, backedUpFiles, failure);
            throw new IllegalStateException("generateData publish failed for stage '" + stage
                    + "' at '" + (publishedFiles.size() < relativeFiles.size()
                    ? relativeFiles.get(publishedFiles.size()) : "?")
                    + "'; every published file was rolled back and the previous active data is retained",
                    failure);
        }

        deleteTree(backupDir);
        writeManifest(accumulateManifest(previousManifest, publishedFiles));
        published = true;
        generator.sealForReadBack(activeRoot);
        deleteTree(candidateDir);
        return new PublishResult(List.copyOf(publishedFiles), List.copyOf(skippedUserFiles),
                List.copyOf(overwriteTrace), Map.copyOf(writeOwners));
    }

    /**
     * A target may be replaced only when it was produced by a previous NekoJS batch AND its
     * content still hashes to what that batch published. A missing manifest entry (never
     * generated here) or a hash mismatch (edited by hand afterwards) marks the file as
     * user-owned.
     */
    private static boolean isUnchangedGeneratorOutput(Path target, String relative,
            Map<String, String> previousManifest) {
        String publishedHash = previousManifest.get(relative);
        return publishedHash != null && publishedHash.equals(sha256Of(target));
    }

    /** Restore pre-publish state: published candidates go back, backups are restored. */
    private void rollback(List<String> publishedFiles, List<String> backedUpFiles, IOException cause) {
        List<IOException> cleanupFailures = new ArrayList<>();
        // Reverse order: restore files moved latest first, keeping parent dirs consistent.
        for (int i = publishedFiles.size() - 1; i >= 0; i--) {
            String relative = publishedFiles.get(i);
            try {
                Path target = activeRoot.resolve(relative);
                if (Files.exists(target)) {
                    Path source = candidateDir.resolve(relative);
                    Files.createDirectories(source.getParent());
                    moveForPublish(target, source);
                }
            } catch (IOException e) {
                cleanupFailures.add(e);
            }
        }
        for (int i = backedUpFiles.size() - 1; i >= 0; i--) {
            String relative = backedUpFiles.get(i);
            try {
                Path backup = backupDir.resolve(relative);
                if (Files.exists(backup)) {
                    Path target = activeRoot.resolve(relative);
                    Files.createDirectories(target.getParent());
                    moveForPublish(backup, target);
                }
            } catch (IOException e) {
                cleanupFailures.add(e);
            }
        }
        for (IOException cleanup : cleanupFailures) {
            cause.addSuppressed(cleanup);
        }
    }

    /** Drop the candidate area without publishing (failure path cleanup). */
    public void discard() {
        try {
            deleteTree(candidateDir);
        } catch (IOException ignored) {
            // best-effort; the next open() for the same stage wipes the scratch anyway
        }
    }

    private List<Path> candidateFiles() throws IOException {
        if (!Files.isDirectory(candidateDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(candidateDir)) {
            return stream.filter(Files::isRegularFile)
                    .map(Path::normalize)
                    .sorted()
                    .toList();
        }
    }

    /** Map of generator-owned path → sha256 of the content the batch published ("" never used). */
    private Map<String, String> readManifest() {
        Map<String, String> files = new LinkedHashMap<>();
        if (!Files.isRegularFile(manifestFile)) {
            return files;
        }
        try {
            JsonElement json = JsonParser.parseString(Files.readString(manifestFile, StandardCharsets.UTF_8));
            if (json.isJsonObject()) {
                JsonElement stageElement = json.getAsJsonObject().get("stage");
                JsonElement filesElement = json.getAsJsonObject().get("files");
                if ((stageElement == null
                        || (stageElement.isJsonPrimitive() && stage.equals(stageElement.getAsString())))
                        && filesElement != null && filesElement.isJsonArray()) {
                    for (JsonElement entry : filesElement.getAsJsonArray()) {
                        if (entry.isJsonObject()) {
                            JsonObject object = entry.getAsJsonObject();
                            JsonElement path = object.get("path");
                            JsonElement hash = object.get("sha256");
                            if (path != null && path.isJsonPrimitive() && hash != null
                                    && hash.isJsonPrimitive()) {
                                files.put(path.getAsString(), hash.getAsString());
                            }
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            // Unreadable manifest: fall back to "nothing is generator-owned", i.e. publish
            // only creates and never overwrites. Deletion is never an option for user data.
        }
        return files;
    }

    /**
     * Accumulate only: previously generated files absent from this batch keep their OLD hash
     * (if the user edited such a file, the stale hash keeps marking it user-owned forever);
     * regenerated files record the hash of the content just published. Entries are never
     * removed — script-generated data is not unconditionally regenerable (data protection).
     */
    private Map<String, String> accumulateManifest(Map<String, String> previous,
            List<String> published) throws IOException {
        Map<String, String> all = new LinkedHashMap<>(previous);
        for (String relative : published) {
            all.put(relative, sha256Of(activeRoot.resolve(relative)));
        }
        return all;
    }

    private void writeManifest(Map<String, String> files) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("version", 2);
        json.addProperty("stage", stage);
        JsonArray array = new JsonArray();
        new TreeSet<>(files.keySet()).forEach(path -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("path", path);
            entry.addProperty("sha256", files.get(path));
            array.add(entry);
        });
        json.add("files", array);
        Files.createDirectories(manifestFile.getParent());
        Path temp = Files.createTempFile(manifestFile.getParent(), ".nekojs-manifest-", ".tmp");
        try {
            Files.writeString(temp, json.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(temp, manifestFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, manifestFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** Hex sha256 of a regular file; {@code null} when unreadable (treated as user-owned). */
    private static String sha256Of(Path file) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            try (java.io.InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Single move seam used for backup, publish and rollback moves. Package-private so tests
     * can inject deterministic IO failures; production always performs an atomic move.
     */
    void moveForPublish(Path source, Path target) throws IOException {
        if (moveFailure != null && moveFailure.test(target)) {
            throw new IOException("Injected publish failure for " + target);
        }
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }

    /** Test-only fault injection into {@link #moveForPublish} (see field). */
    void setMoveFailureForTest(java.util.function.Predicate<Path> failWhenTargetMatches) {
        this.moveFailure = failWhenTargetMatches;
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort scratch cleanup; stale scratch is wiped by the next open()
                }
            });
        }
    }

    /** Outcome of a successful publish, for logging and verification. */
    public record PublishResult(
            List<String> published,
            List<String> skippedUserFiles,
            List<String> overwriteTrace,
            Map<String, String> owners) {
    }
}
