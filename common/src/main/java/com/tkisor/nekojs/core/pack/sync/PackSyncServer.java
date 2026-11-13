package com.tkisor.nekojs.core.pack.sync;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.pack.ScriptPack;
import com.tkisor.nekojs.core.pack.ScriptPackRegistry;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Server side of pack distribution, independent of the platform. {@link #collectSyncPacks()}
 * takes the enabled GLOBAL and WORLD packs whose {@code clientSync != false} and turns them into
 * snapshots (syncId, hash, raw manifest, content files). The platform entry point decides from
 * {@link #enabled()} whether anything is distributed. In {@code hashOnly} mode only hashes are
 * sent, no bundle.
 */
public final class PackSyncServer {

    /**
     * Id of the configuration-phase push task. Every platform's {@code ConfigurationTask.Type}
     * uses the same string, so the task identity matches on NeoForge and fabric.
     */
    public static final String TASK_ID = NekoJS.MODID + ":pack_sync";

    private PackSyncServer() {}

    /** Whether packSync is enabled in the current engine.toml (mode is not off). */
    public static boolean enabled() {
        return ClassFilter.INSTANCE.config().packSyncEnabled();
    }

    /** Whether the current mode is hashOnly. Only hashes are synced, so the client never executes
     * the remote packs. */
    public static boolean hashOnly() {
        return SandboxConfig.PACK_SYNC_HASH_ONLY.equalsIgnoreCase(ClassFilter.INSTANCE.config().packSyncMode());
    }

    /**
     * Config-phase bundle gate (shared by both loader configuration tasks): the hash list is
     * always pushed first; the full bundle follows only when the mode is not hashOnly and packs
     * were actually gathered. The shared single point keeps the push order from drifting
     * between the two bridges.
     */
    public static boolean shouldSendBundle(int gatheredPackCount) {
        return gatheredPackCount > 0 && !hashOnly();
    }

    /** Gathers the packs to distribute: the enabled GLOBAL and WORLD packs whose clientSync flag is
     * set. */
    public static List<SyncedPack> collectSyncPacks() {
        List<SyncedPack> out = new ArrayList<>();
        for (ScriptPack pack : ScriptPackRegistry.get().enabledPacks()) {
            if (pack.scope() != com.tkisor.nekojs.core.pack.ScriptPackScope.GLOBAL
                    && pack.scope() != com.tkisor.nekojs.core.pack.ScriptPackScope.WORLD) continue;
            if (!pack.manifest().clientSync()) continue;
            try {
                byte[] manifestBytes = PackHasher.readManifestBytes(pack.root());
                List<PackContentFile> files = PackHasher.readContentFiles(pack.root());
                out.add(new SyncedPack(
                    syncId(pack),
                    pack.scope().name(),
                    PackHasher.hash(manifestBytes, files),
                    new String(manifestBytes, StandardCharsets.UTF_8),
                    files));
            } catch (Exception e) {
                NekoJS.LOGGER.warn("Failed to snapshot script pack {} for sync: {}", pack, e.toString());
            }
        }
        return out;
    }

    /** Builds the syncId as {@code <scope idSegment>:<packId>}, for example {@code packs:demo} or
     * {@code worldpacks:demo}. */
    public static String syncId(ScriptPack pack) {
        return pack.scope().idSegment() + ":" + pack.id();
    }
}
