package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.wrapper.clientdata.ClientDataStore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端→客户端键值数据同步包（{@code ClientData.sync} → {@code clientData}）：
 * key + JSON 文本。JSON 作为字符串传输，与 neoforge-1.21.1 的同名包线格式一致
 * （编解码各自实现，字段相同）。
 */
public record ClientDataSyncPacket(String key, String json) implements CustomPacketPayload {

    public ClientDataSyncPacket {
        if (key == null || key.isBlank() || key.length() > ClientDataStore.MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("Client data key must be non-blank and at most "
                    + ClientDataStore.MAX_KEY_LENGTH + " characters");
        }
    }

    public static final Type<ClientDataSyncPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(NekoJS.MODID, "client_data_sync"));

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
