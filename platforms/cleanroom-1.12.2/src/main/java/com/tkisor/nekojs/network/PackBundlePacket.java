package com.tkisor.nekojs.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 1.12.2 pack bundle packet: {@code [(syncId, scope, manifestJson,
 * files[(relativePath, bytes)])]}, the play-phase degraded-mode equivalent of
 * the NeoForge configuration-phase {@code nekojs:pack_bundle} payload. Wire
 * order matches the NeoForge packet; byte payloads use int-length framing
 * (ByteBufUtils UTF strings are short-length limited and manifests may exceed
 * that).
 *
 * <p>The empty constructor is required by FML for reflective instantiation.
 */
public class PackBundlePacket implements IMessage {

    private List<Pack> packs = new ArrayList<>();

    public PackBundlePacket() {}

    public PackBundlePacket(List<Pack> packs) {
        this.packs = packs;
    }

    public List<Pack> packs() {
        return packs;
    }

    public record FileEntry(String relativePath, byte[] bytes) {}

    public record Pack(String syncId, String scope, String manifestJson, List<FileEntry> files) {
        public Pack {
            files = List.copyOf(files);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int packCount = ByteBufUtils.readVarInt(buf, 5);
        packs = new ArrayList<>(Math.max(0, packCount));
        for (int i = 0; i < packCount; i++) {
            String syncId = ByteBufUtils.readUTF8String(buf);
            String scope = ByteBufUtils.readUTF8String(buf);
            String manifest = readLargeString(buf);
            int fileCount = ByteBufUtils.readVarInt(buf, 5);
            List<FileEntry> files = new ArrayList<>(Math.max(0, fileCount));
            for (int f = 0; f < fileCount; f++) {
                files.add(new FileEntry(ByteBufUtils.readUTF8String(buf), readBytes(buf)));
            }
            packs.add(new Pack(syncId, scope, manifest, files));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeVarInt(buf, packs.size(), 5);
        for (Pack pack : packs) {
            ByteBufUtils.writeUTF8String(buf, pack.syncId());
            ByteBufUtils.writeUTF8String(buf, pack.scope());
            writeLargeString(buf, pack.manifestJson());
            ByteBufUtils.writeVarInt(buf, pack.files().size(), 5);
            for (FileEntry file : pack.files()) {
                ByteBufUtils.writeUTF8String(buf, file.relativePath());
                writeBytes(buf, file.bytes());
            }
        }
    }

    private static byte[] readBytes(ByteBuf buf) {
        int length = buf.readInt();
        byte[] bytes = new byte[Math.max(0, length)];
        buf.readBytes(bytes);
        return bytes;
    }

    private static void writeBytes(ByteBuf buf, byte[] bytes) {
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readLargeString(ByteBuf buf) {
        return new String(readBytes(buf), StandardCharsets.UTF_8);
    }

    private static void writeLargeString(ByteBuf buf, String text) {
        writeBytes(buf, text.getBytes(StandardCharsets.UTF_8));
    }
}
