//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.network;

import com.tkisor.nekojs.platform.compat.McPlatformCompat;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Exercises the actual node facade: initial clients need the same channel before frozen-registry sync. */
class DynamicSyncPhaseRegistrationTest {
    private record Registration(String id, Set<ConnectionProtocol> phases) {}

    @Test
    void theSingleDynamicChannelIsAvailableDuringConfigurationAndPlay() {
        var registrar = new RecordingRegistrar();
        McPlatformCompat.get().registerDynamicSyncPayload(registrar);
        assertEquals(List.of(new Registration("nekojs:dynamic_registry_sync",
                Set.of(ConnectionProtocol.CONFIGURATION, ConnectionProtocol.PLAY))), registrar.registrations);
    }

    private static final class RecordingRegistrar extends PayloadRegistrar {
        private final List<Registration> registrations = new ArrayList<>();

        RecordingRegistrar() {
            super("1");
        }

        @Override
        public <T extends CustomPacketPayload> PayloadRegistrar playBidirectional(CustomPacketPayload.Type<T> type,
                StreamCodec<? super RegistryFriendlyByteBuf, T> codec, IPayloadHandler<T> server, IPayloadHandler<T> client) {
            registrations.add(new Registration(type.id().toString(), Set.of(ConnectionProtocol.PLAY)));
            return this;
        }

        @Override
        public <T extends CustomPacketPayload> PayloadRegistrar commonBidirectional(CustomPacketPayload.Type<T> type,
                StreamCodec<? super FriendlyByteBuf, T> codec, IPayloadHandler<T> server, IPayloadHandler<T> client) {
            registrations.add(new Registration(type.id().toString(),
                    Set.of(ConnectionProtocol.CONFIGURATION, ConnectionProtocol.PLAY)));
            return this;
        }
    }
}
//?}
//?}
