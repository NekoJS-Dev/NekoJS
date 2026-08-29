package com.tkisor.nekojs.resource;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.pack.ScriptPack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.WorldDataConfiguration;
import org.jspecify.annotations.NonNull;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

/**
 * Mounts {@code data/} directories of enabled script packs as synthetic server data packs
 * (port of Katton's ScriptPackDataManager to NekoJS pack model).
 *
 * <p>For each enabled {@link ScriptPack} whose root contains a {@code data/} directory a
 * synthetic {@link Pack} is supplied through a {@link RepositorySource} reflectively
 * installed into the server's {@link PackRepository} {@code sources} set. Pack ids use the
 * {@code nekojs_pack_<scope>_<packId>} prefix, are forced to {@link Pack.Position#TOP} and
 * are scrubbed back out of {@code WorldDataConfiguration} after each reload so they never
 * persist into the save. The {@code data/} directories are content-hashed: an unchanged
 * signature skips the resource reload entirely.</p>
 *
 * <p>Client {@code assets/} mounting is intentionally out of scope here (follow-up).</p>
 */
public final class ScriptPackDataManager {

    private ScriptPackDataManager() {}

    private static final String PACK_ID_PREFIX = "nekojs_pack_";

    private record DataEntry(String packId, String title, Path dataDir, ScriptPackScopeName scope, String dataHash) {}

    /** Scope tag used for stable pack ids; mirrors {@link com.tkisor.nekojs.core.pack.ScriptPackScope}. */
    private enum ScriptPackScopeName {
        GLOBAL, WORLD;

        static ScriptPackScopeName of(ScriptPack pack) {
            return pack.scope() == com.tkisor.nekojs.core.pack.ScriptPackScope.WORLD ? WORLD : GLOBAL;
        }
    }

    private static volatile PackRepository installedRepository;
    private static volatile List<DataEntry> activeEntries = List.of();
    private static volatile String activeSignature = "";

    private static final RepositorySource REPOSITORY_SOURCE = output -> {
        for (DataEntry entry : activeEntries) {
            Pack pack = createPack(entry);
            if (pack != null) {
                output.accept(pack);
            }
        }
    };

    /** True when at least one of the packs carries a {@code data/} directory worth mounting. */
    public static boolean hasDataPacks(List<ScriptPack> packs) {
        for (ScriptPack pack : packs) {
            if (Files.isDirectory(pack.root().resolve("data"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Installs the repository source (once per repository) and refreshes the active entry
     * list from the given packs. Returns true when a resource reload is needed (content
     * signature changed, or the repository changed while entries exist).
     */
    public static synchronized boolean activateForServer(MinecraftServer server, List<ScriptPack> packs) {
        PackRepository repository = server.getPackRepository();
        boolean repositoryChanged = installedRepository != repository;
        String previousSignature = activeSignature;
        if (!installRepositorySource(repository)) {
            return false;
        }

        List<DataEntry> entries = new ArrayList<>();
        for (ScriptPack pack : packs) {
            DataEntry entry = createEntry(pack);
            if (entry != null) {
                entries.add(entry);
            }
        }
        String nextSignature = String.join("\u001f", entries.stream()
                .map(e -> e.packId() + "\u001e" + e.dataDir() + "\u001e" + e.dataHash())
                .toList());

        boolean shouldReload;
        if (repositoryChanged) {
            shouldReload = !nextSignature.isEmpty() || !previousSignature.isEmpty();
        } else {
            shouldReload = !nextSignature.equals(previousSignature);
        }

        activeEntries = List.copyOf(entries);
        activeSignature = nextSignature;
        return shouldReload;
    }

    /** True when all active nekojs pack ids are currently part of the repository selection. */
    public static boolean isSelectionIntact(MinecraftServer server) {
        PackRepository repository = server.getPackRepository();
        if (installedRepository != repository || activeEntries.isEmpty()) {
            return activeEntries.isEmpty();
        }
        Set<String> selected = Set.copyOf(repository.getSelectedIds());
        for (DataEntry entry : activeEntries) {
            if (!selected.contains(entry.packId())) {
                return false;
            }
        }
        return true;
    }

    /** Drops all mounted state (server stopped: the repository dies with the server). */
    public static synchronized void reset() {
        installedRepository = null;
        activeEntries = List.of();
        activeSignature = "";
    }

    /**
     * Re-selects (and reloads) server resources with the nekojs packs included. Runs on the
     * server thread; when called from another thread the work is scheduled there instead.
     */
    public static boolean reloadServerResources(MinecraftServer server) {
        if (server.isSameThread()) {
            return reloadServerResourcesOnServerThread(server);
        }
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        server.execute(() -> result.complete(reloadServerResourcesOnServerThread(server)));
        try {
            return result.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            NekoJS.LOGGER.warn("Failed to reload server resources for script pack data", e);
            return false;
        }
    }

    private static boolean reloadServerResourcesOnServerThread(MinecraftServer server) {
        try {
            PackRepository repository = server.getPackRepository();
            if (!installRepositorySource(repository)) {
                return false;
            }

            repository.reload();
            Set<String> requestedIds = new LinkedHashSet<>(repository.getSelectedIds());
            for (DataEntry entry : activeEntries) {
                requestedIds.add(entry.packId());
            }
            repository.setSelected(requestedIds);
            List<String> selectedIds = List.copyOf(repository.getSelectedIds());

            NekoJS.LOGGER.info("Reloading server resources for {} nekojs script pack(s)", activeEntries.size());
            CompletableFuture<Void> reloadFuture = server.reloadResources(selectedIds);
            waitForReload(server, reloadFuture);
            restoreWorldDataConfiguration(server);
            return true;
        } catch (Exception e) {
            NekoJS.LOGGER.warn("Failed to mount nekojs script pack data", e);
            return false;
        }
    }

    /** Removes the nekojs pack ids from worldData so they never persist into the save. */
    private static void restoreWorldDataConfiguration(MinecraftServer server) {
        WorldDataConfiguration current = server.getWorldData().getDataConfiguration();
        DataPackConfig packs = current.dataPacks();
        List<String> enabled = packs.getEnabled().stream().filter(id -> !isNekojsPackId(id)).toList();
        List<String> disabled = packs.getDisabled().stream().filter(id -> !isNekojsPackId(id)).toList();
        if (enabled.size() == packs.getEnabled().size() && disabled.size() == packs.getDisabled().size()) {
            return;
        }
        server.getWorldData().setDataConfiguration(
                new WorldDataConfiguration(new DataPackConfig(enabled, disabled), current.enabledFeatures()));
    }

    private static void waitForReload(MinecraftServer server, CompletableFuture<Void> future) {
        if (server.isSameThread()) {
            server.managedBlock((BooleanSupplier) future::isDone);
            future.join();
        } else {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                NekoJS.LOGGER.warn("Script pack data resource reload failed", e);
            }
        }
    }

    private static boolean installRepositorySource(PackRepository repository) {
        if (installedRepository == repository) {
            return true;
        }
        Set<RepositorySource> sources = readSources(repository);
        if (sources == null) {
            NekoJS.LOGGER.warn("Cannot access server data pack repository sources; script pack data not mounted");
            return false;
        }
        for (RepositorySource source : sources) {
            if (source == REPOSITORY_SOURCE) {
                installedRepository = repository;
                return true;
            }
        }
        Set<RepositorySource> updated = new LinkedHashSet<>(sources);
        updated.add(REPOSITORY_SOURCE);
        writeField(repository, "sources", updated);
        installedRepository = repository;
        return true;
    }

    private static DataEntry createEntry(ScriptPack pack) {
        Path dataDir = pack.root().resolve("data");
        if (!Files.isDirectory(dataDir)) {
            return null;
        }
        String dataHash = hashDirectory(dataDir);
        if (dataHash == null) {
            return null;
        }
        String packId = PACK_ID_PREFIX + ScriptPackScopeName.of(pack).name().toLowerCase(Locale.ROOT)
                + "_" + sanitizePackId(pack.id());
        return new DataEntry(packId, pack.name() + " data", dataDir, ScriptPackScopeName.of(pack), dataHash);
    }

    private static Pack createPack(DataEntry entry) {
        PackLocationInfo location = new PackLocationInfo(
                entry.packId(),
                Component.literal(entry.title()),
                NekoJSPackSource.PACK_SOURCE_NEKO,
                Optional.empty());
        return Pack.readMetaAndCreate(
                location,
                new Pack.ResourcesSupplier() {
                    @Override
                    public @NonNull PackResources openPrimary(PackLocationInfo locationInfo) {
                        return new NekoJSPathPackResources(entry.packId(), entry.dataDir().getParent(), PackType.SERVER_DATA);
                    }

                    @Override
                    public @NonNull PackResources openFull(@NonNull PackLocationInfo locationInfo, Pack.@NonNull Metadata metadata) {
                        return openPrimary(locationInfo);
                    }
                },
                PackType.SERVER_DATA,
                new PackSelectionConfig(true, Pack.Position.TOP, false));
    }

    private static String hashDirectory(Path dataDir) {
        try (Stream<Path> stream = Files.walk(dataDir)) {
            List<Path> files = stream.filter(Files::isRegularFile).sorted().toList();
            if (files.isEmpty()) {
                return null;
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Path file : files) {
                String relative = dataDir.relativize(file).toString().replace('\\', '/');
                digest.update(relative.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(Files.readAllBytes(file));
                digest.update((byte) 0);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            NekoJS.LOGGER.warn("Failed to hash script pack data directory {}", dataDir, e);
            return null;
        }
    }

    private static boolean isNekojsPackId(String id) {
        return id != null && id.startsWith(PACK_ID_PREFIX);
    }

    private static String sanitizePackId(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            out.append(Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' ? c : '_');
        }
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    private static Set<RepositorySource> readSources(PackRepository repository) {
        Object raw = readField(repository, "sources");
        return raw instanceof Set<?> set ? (Set<RepositorySource>) set : null;
    }

    // ------------------------------------------------------------------
    // Minimal reflective field IO (final fields on PackRepository need the
    // sun.misc.Unsafe fallback — same strategy Katton's ReflectUtil uses).
    // ------------------------------------------------------------------

    private static Object readField(Object target, String name) {
        Field field = findField(target.getClass(), name);
        if (field == null) {
            return null;
        }
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception e) {
            try {
                return unsafeGet(field, target);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private static void writeField(Object target, String name, Object value) {
        Field field = findField(target.getClass(), name);
        if (field == null) {
            NekoJS.LOGGER.warn("Cannot write field '{}' on {}", name, target.getClass().getName());
            return;
        }
        try {
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            try {
                unsafePutObject(field, target, value);
            } catch (Exception ex) {
                NekoJS.LOGGER.warn("Failed to write field '{}' on {}", name, target.getClass().getName(), ex);
            }
        }
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    @SuppressWarnings("removal")
    private static sun.misc.Unsafe theUnsafe() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (sun.misc.Unsafe) unsafeField.get(null);
    }

    @SuppressWarnings("removal")
    private static Object unsafeGet(Field field, Object target) throws Exception {
        return theUnsafe().getObject(target, theUnsafe().objectFieldOffset(field));
    }

    @SuppressWarnings("removal")
    private static void unsafePutObject(Field field, Object target, Object value) throws Exception {
        theUnsafe().putObject(target, theUnsafe().objectFieldOffset(field), value);
    }
}
