package com.tkisor.nekojs.fabric.resource;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.core.pack.ScriptPack;
import com.tkisor.nekojs.core.pack.ScriptPackRegistry;
import com.tkisor.nekojs.core.pack.ScriptPackScope;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Refreshes native client resources after accepted remote bundles and local connection changes. */
public final class FabricClientPackResources {
    private FabricClientPackResources() {}

    public static List<ScriptPack> packs() {
        boolean local = isLocalConnection(Minecraft.getInstance());
        return ScriptPackRegistry.get().allPacks().stream()
                .filter(pack -> pack.scope() != ScriptPackScope.WORLD || local).toList();
    }

    /** Returns a request hook; native reloads start on a later client tick, outside sync's latch. */
    public static Runnable register(Supplier<NekoRuntimeRoot> rootSupplier) {
        var requested = new AtomicBoolean();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!handler.getConnection().isMemoryConnection()
                    || ScriptPackRegistry.get().worldPacks().isEmpty()) return;
            var root = rootSupplier.get();
            if (root != null) {
                try {
                    root.reload(ScriptType.CLIENT);
                } catch (Exception failure) {
                    NekoJS.LOGGER.error("[NEKO-1017] CLIENT reload after local world pack activation failed", failure);
                    root.errorTracker().recordCallbackError(ScriptType.CLIENT, "world_pack_login", failure);
                }
            }
            requested.set(true);
        });
        ClientTickEvents.END_CLIENT_TICK.register(new ClientTickEvents.EndTick() {
            private boolean local;
            private boolean reloading;

            @Override public void onEndTick(Minecraft client) {
                boolean connected = isLocalConnection(client) && !ScriptPackRegistry.get().worldPacks().isEmpty();
                if (local != connected) {
                    local = connected;
                    requested.set(true);
                }
                if (reloading || !requested.getAndSet(false)) return;
                reloading = true;
                // Completion may run on a worker. Publish all adapter state on the client thread.
                try {
                    client.reloadResourcePacks().whenComplete((ignored, failure) -> client.execute(() -> {
                        reloading = false;
                        if (failure != null) reportReloadFailure(failure);
                    }));
                } catch (RuntimeException failure) {
                    reloading = false;
                    reportReloadFailure(failure);
                }
            }
        });
        return () -> requested.set(true);
    }

    private static boolean isLocalConnection(Minecraft client) {
        return client.getConnection() != null && client.getConnection().getConnection().isMemoryConnection();
    }

    private static void reportReloadFailure(Throwable failure) {
        NekoJS.LOGGER.error("[NEKO-6011] Directory pack resource reload failed; inspect native resource recovery", failure);
    }
}
