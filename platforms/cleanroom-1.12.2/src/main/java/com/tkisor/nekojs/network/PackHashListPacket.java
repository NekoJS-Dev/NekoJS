package com.tkisor.nekojs.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.12.2 pack hash list packet: {@code [(syncId, hash)]}, the play-phase
 * degraded-mode equivalent of the NeoForge configuration-phase
 * {@code nekojs:pack_hashes} payload (same field order, varint counts, UTF
 * strings — only the framing differs: FML discriminator instead of payload id).
 * An empty list is meaningful: the client clears remote packs and reloads
 * client scripts.
 *
 * <p>The empty constructor is required by FML for reflective instantiation.
 */
public class PackHashListPacket implements IMessage {

    private List<Entry> entries = new ArrayList<>();

    public PackHashListPacket() {}

    public PackHashListPacket(List<Entry> entries) {
        this.entries = entries;
    }

    public List<Entry> entries() {
        return entries;
    }

    public record Entry(String syncId, String hash) {}

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = ByteBufUtils.readVarInt(buf, 5);
        entries = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(ByteBufUtils.readUTF8String(buf), ByteBufUtils.readUTF8String(buf)));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeVarInt(buf, entries.size(), 5);
        for (Entry entry : entries) {
            ByteBufUtils.writeUTF8String(buf, entry.syncId());
            ByteBufUtils.writeUTF8String(buf, entry.hash());
        }
    }
}
