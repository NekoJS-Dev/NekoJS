//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.dynamic;

import com.tkisor.nekojs.core.dynamic.txn.DynamicSyncMessage;
import com.tkisor.nekojs.core.dynamic.txn.DynamicSyncWireCodec;
import com.tkisor.nekojs.network.DynamicRegistrySyncPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;

import java.util.function.Consumer;

/** Runs before frozen-registry sync; only real activation releases the early task. */
public final class DynamicRegistryConfigurationTask implements ConfigurationTask {
    public static final Type TYPE = new Type("nekojs:dynamic_registry_sync");
    private final ServerConfigurationPacketListener listener;

    private DynamicRegistryConfigurationTask(ServerConfigurationPacketListener listener) {
        this.listener = listener;
    }

    public static void registerEarly(ServerConfigurationPacketListener listener, Consumer<ConfigurationTask> tasks) {
        if (DynamicRegistryFacade.runtime().activationEngine() != null
                && !listener.getConnection().isMemoryConnection()) {
            tasks.accept(new DynamicRegistryConfigurationTask(listener));
        }
    }

    @Override
    public void start(Consumer<Packet<?>> sender) {
        var engine = DynamicRegistryFacade.runtime().activationEngine();
        if (engine == null || !(engine.transport() instanceof NeoForgeDynamicSyncTransport transport)
                || !listener.hasChannel(DynamicRegistrySyncPacket.TYPE)
                || !(listener instanceof ServerCommonPacketListenerImpl common)) {
            listener.disconnect(Component.translatable("nekojs.dynamic_registry.configuration_failed"));
            return;
        }
        String participantId = common.getOwner().id().toString();
        transport.joinConfiguration(new NeoForgeDynamicSyncTransport.ConfigurationConnection() {
            public String participantId() { return participantId; }
            public boolean connected() { return listener.getConnection().isConnected(); }
            public void send(DynamicSyncMessage message) {
                listener.send(new DynamicRegistrySyncPacket(DynamicSyncWireCodec.encodeServerMessage(message)));
            }
            public void finishTask() { listener.finishCurrentTask(TYPE); }
            public void disconnect() {
                listener.disconnect(Component.translatable("nekojs.dynamic_registry.configuration_failed"));
            }
        }, engine);
    }

    @Override
    public Type type() { return TYPE; }
}
//?}
//?}
