package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C 配置阶段包哈希清单（{@code nekojs:pack_hashes}）：{@code [(syncId, hash)]}。
 * 空清单同样有效——客户端据此清空远端包并重载客户端脚本。
 *
 * <p>线格式（与 neoforge-26-shared 同名包线格式一致）：varint 数量 + 每项 UTF(syncId) + UTF(hash)。
 */
public record PackHashListPayload(List<HashEntry> entries) implements CustomPacketPayload {

    public record HashEntry(String syncId, String hash) {}

    private static final int MAX_SYNC_ID_LENGTH = 256;
    private static final int MAX_HASH_LENGTH = 128;
    private static final int MAX_ENTRIES = 256;

    public PackHashListPayload {
        entries = List.copyOf(entries);
    }

    public static final Type<PackHashListPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NekoJS.MODID, "pack_hashes"));

    public static final StreamCodec<FriendlyByteBuf, PackHashListPayload> STREAM_CODEC =
            StreamCodec.of(PackHashListPayload::write, PackHashListPayload::read);

    private static PackHashListPayload read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count > MAX_ENTRIES) {
            throw new IllegalArgumentException("Too many pack hash entries: " + count);
        }
        List<HashEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new HashEntry(buf.readUtf(MAX_SYNC_ID_LENGTH), buf.readUtf(MAX_HASH_LENGTH)));
        }
        return new PackHashListPayload(entries);
    }

    private static void write(FriendlyByteBuf buf, PackHashListPayload payload) {
        buf.writeVarInt(payload.entries.size());
        for (HashEntry entry : payload.entries) {
            buf.writeUtf(entry.syncId(), MAX_SYNC_ID_LENGTH);
            buf.writeUtf(entry.hash(), MAX_HASH_LENGTH);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
