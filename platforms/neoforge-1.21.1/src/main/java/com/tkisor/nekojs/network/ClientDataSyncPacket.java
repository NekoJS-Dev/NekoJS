package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.wrapper.clientdata.ClientDataStore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-to-client key-value data sync packet ({@code ClientData.sync} →
 * {@code clientData}): key + JSON text. The JSON travels as a string so the
 * wire format matches the neoforge-26-shared packet of the same name.
 */
public record ClientDataSyncPacket(String key, String json) implements CustomPacketPayload {

    public ClientDataSyncPacket {
        if (key == null || key.isBlank() || key.length() > ClientDataStore.MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("Client data key must be non-blank and at most "
                    + ClientDataStore.MAX_KEY_LENGTH + " characters");
        }
    }

    public static final Type<ClientDataSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NekoJS.MODID, "client_data_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientDataSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, ClientDataSyncPacket::key,
                    ByteBufCodecs.STRING_UTF8, ClientDataSyncPacket::json,
                    ClientDataSyncPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
