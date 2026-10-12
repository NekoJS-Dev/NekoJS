package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.core.registry.ItemAssetDefinition;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/** Writes boot item resources while retaining files modified by scripts or users.
 * All replacements and the ownership index are staged before publication. An I/O failure
 * attempts to restore previous files and retains any restoration failures as causes.
 * This is a process-local transaction, not a crash journal.
 */
public final class RegisteredItemAssets {
    private static final String INDEX = ".nekojs-item-assets.json";
    @FunctionalInterface interface Mover { void move(Path source, Path target) throws IOException; }
    private final Mover mover;

    public RegisteredItemAssets() { this(RegisteredItemAssets::move); }
    RegisteredItemAssets(Mover mover) { this.mover = mover; }

    public synchronized void generate(Path root, List<ItemAssetDefinition> definitions,
                                      boolean clientItemDefinitions, Consumer<String> skipped) {
        DataGeneratorJS destination = new DataGeneratorJS(root);
        DataGeneratorJS ownership = new DataGeneratorJS(root.resolveSibling(".nekojs-item-assets"));
        Map<String, String> owners = readOwners(ownership.resolve("ownership.json"));
        Map<String, String> nextOwners = new TreeMap<>();
        Map<String, String> writes = new TreeMap<>();
        try {
            for (ItemAssetDefinition definition : definitions) {
                Map<String, String> group = definition.files(clientItemDefinitions);
                boolean external = false;
                for (String path : group.keySet()) {
                    Path file = destination.resolve(path);
                    if (Files.exists(file) && !matches(file, owners.get(path))) external = true;
                }
                if (external) {
                    skipped.accept("[NEKO-6008] Automatic item assets skipped for '" + definition.id()
                            + "'; an existing file is externally owned or modified");
                    // Retain ownership of untouched companion files without rewriting the group.
                    for (String path : group.keySet()) {
                        if (matches(destination.resolve(path), owners.get(path))) nextOwners.put(path, owners.get(path));
                    }
                    continue;
                }
                for (var entry : group.entrySet()) {
                    writes.put(entry.getKey(), entry.getValue());
                    nextOwners.put(entry.getKey(), digest(entry.getValue().getBytes(StandardCharsets.UTF_8)));
                }
            }
            List<String> removals = new ArrayList<>();
            for (var entry : owners.entrySet()) {
                if (!nextOwners.containsKey(entry.getKey()) && !writes.containsKey(entry.getKey())
                        && matches(destination.resolve(entry.getKey()), entry.getValue())) removals.add(entry.getKey());
            }
            JsonObject index = new JsonObject();
            nextOwners.forEach(index::addProperty);
            writes.put(INDEX, index.toString());
            var iterator = writes.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                Path target = target(destination, ownership, entry.getKey());
                if (Files.isRegularFile(target)
                        && new String(readBounded(target), StandardCharsets.UTF_8).equals(entry.getValue())) iterator.remove();
            }
            if (writes.isEmpty() && removals.isEmpty()) return;
            publish(root, destination, ownership, writes, removals);
        } catch (IOException error) {
            throw new IllegalStateException("[NEKO-6009] Automatic item asset generation failed", error);
        }
    }

    private void publish(Path root, DataGeneratorJS destination, DataGeneratorJS ownership, Map<String, String> writes,
                         List<String> removals) throws IOException {
        Files.createDirectories(root);
        Path stage = Files.createTempDirectory(root, ".nekojs-item-stage-");
        Map<Path, byte[]> backups = new java.util.LinkedHashMap<>();
        List<Path> changed = new ArrayList<>();
        Throwable publicationFailure = null;
        try {
            DataGeneratorJS staged = new DataGeneratorJS(stage);
            // Stage and validate the entire group before replacing either resource file.
            for (var entry : writes.entrySet()) staged.text(entry.getKey(), entry.getValue());
            List<String> paths = new ArrayList<>(writes.keySet());
            paths.remove(INDEX);
            paths.addAll(removals);
            if (writes.containsKey(INDEX)) paths.add(INDEX);
            long backupBytes = 0;
            for (String path : paths) {
                Path target = target(destination, ownership, path);
                byte[] backup = Files.exists(target) ? readBounded(target) : null;
                backupBytes += backup == null ? 0 : backup.length;
                if (backupBytes > DataGeneratorJS.MAX_GENERATED_TOTAL_BYTES) throw new IOException("Item asset backup exceeds generation limit");
                backups.put(target, backup);
            }
            try {
                for (String path : paths) {
                    Path target = target(destination, ownership, path);
                    changed.add(target);
                    if (writes.containsKey(path)) {
                        Files.createDirectories(target.getParent());
                        mover.move(staged.resolve(path), target);
                    } else Files.deleteIfExists(target);
                }
            } catch (IOException | RuntimeException failure) {
                for (Path target : changed.reversed()) {
                    try {
                        byte[] previous = backups.get(target);
                        if (previous == null) Files.deleteIfExists(target);
                        else {
                            Path restore = Files.createTempFile(target.getParent(), ".nekojs-item-restore-", ".tmp");
                            try { Files.write(restore, previous); move(restore, target); }
                            finally { Files.deleteIfExists(restore); }
                        }
                    } catch (IOException restoreFailure) { failure.addSuppressed(restoreFailure); }
                }
                throw failure;
            }
        } catch (IOException | RuntimeException | Error failure) {
            publicationFailure = failure;
            throw failure;
        } finally {
            try (var files = Files.walk(stage)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            } catch (IOException cleanupFailure) {
                if (publicationFailure == null) throw cleanupFailure;
                publicationFailure.addSuppressed(cleanupFailure);
            }
        }
    }

    private static Path target(DataGeneratorJS destination, DataGeneratorJS ownership, String path) {
        return path.equals(INDEX) ? ownership.resolve("ownership.json") : destination.resolve(path);
    }

    private static Map<String, String> readOwners(Path index) {
        if (!Files.exists(index)) return Map.of();
        try {
            JsonObject json = JsonParser.parseString(new String(readBounded(index), StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> owners = new TreeMap<>();
            for (var entry : json.entrySet()) {
                String path = entry.getKey();
                if (!path.matches("[a-z0-9_.-]+/(models/item|items)/[a-z0-9/._-]+\\.json")
                        || !entry.getValue().isJsonPrimitive() || !entry.getValue().getAsString().matches("[a-f0-9]{64}")) {
                    throw new IOException("Invalid item asset ownership entry");
                }
                String idPath = path.substring(path.indexOf('/') + 1);
                com.tkisor.nekojs.core.registry.ItemDefinitionConfig.resourceId("nekojs:" + idPath);
                owners.put(path, entry.getValue().getAsString());
            }
            return owners;
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("[NEKO-6010] Item asset ownership index is unreadable; generation rejected", error);
        }
    }

    private static boolean matches(Path path, String expected) throws IOException {
        return expected != null && Files.isRegularFile(path) && digest(readBounded(path)).equals(expected);
    }
    private static byte[] readBounded(Path path) throws IOException {
        if (Files.size(path) > DataGeneratorJS.MAX_GENERATED_FILE_BYTES) throw new IOException("Item asset file exceeds generation limit: " + path);
        return Files.readAllBytes(path);
    }
    private static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException error) { throw new AssertionError(error); }
    }
    private static void move(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException error) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }
}
