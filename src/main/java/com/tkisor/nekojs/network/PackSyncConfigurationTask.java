//? if neoforge {
package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.pack.sync.PackSyncServer;
import com.tkisor.nekojs.core.pack.sync.SyncedPack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.server.network.ConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
/**
 * Pack distribution task for the configuration phase (shared by NeoForge 26.x and 1.21.1). It hooks
 * into each connection's configuration task queue through {@link RegisterConfigurationTasksEvent},
 * the official FML entry point, so no mixin is needed. The task runs after NeoForge's own channel
 * negotiation tasks, at which point custom payloads can already be sent.
 *
 * <p>Behavior: when packSync in engine.toml is enabled it sends the hash list; when mode=all and
 * there are packs it sends the full bundle right after that, with no request round trip, because the
 * client registry check needs the packs to arrive first. Local/singleplayer memory connections are
 * skipped.
 */
public class PackSyncConfigurationTask implements ConfigurationTask {

    public static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(PackSyncServer.TASK_ID);

    private final ServerConfigurationPacketListener listener;

    private PackSyncConfigurationTask(ServerConfigurationPacketListener listener) {
        this.listener = listener;
    }

    /** Event entry point: registers no task when packSync is off or the connection is local, so it costs nothing. */
    public static void register(RegisterConfigurationTasksEvent event) {
        if (!PackSyncServer.enabled()) return;
        ServerConfigurationPacketListener listener = event.getListener();
        if (listener.getConnection().isMemoryConnection()) return;
        event.register(new PackSyncConfigurationTask(listener));
    }

    @Override
    public void start(Consumer<Packet<?>> sender) {
        try {
            List<SyncedPack> packs = PackSyncServer.collectSyncPacks();
            PackHashListPayload hashes = PackHashListPayload.of(packs);
            listener.send(hashes);
            if (PackSyncServer.shouldSendBundle(packs.size())) {
                listener.send(PackBundlePayload.of(packs));
            }
        } catch (Exception e) {
            NekoJS.LOGGER.error("Failed to push script pack sync during configuration", e);
        } finally {
            listener.finishCurrentTask(type());
        }
    }

    @Override
    public ConfigurationTask.Type type() {
        return TYPE;
    }
}
//?}
