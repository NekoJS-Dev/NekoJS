package com.tkisor.nekojs.network;

import com.tkisor.nekojs.core.pack.sync.PackContentFile;
import com.tkisor.nekojs.core.pack.sync.PackSyncClient;
import com.tkisor.nekojs.core.pack.sync.SyncedPack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side handlers for the 1.12.2 pack sync packets (degraded mode).
 *
 * <p>Unlike NeoForge 26.x/1.21.1 there is no configuration phase on 1.12.2
 * Forge: the server pushes hashes + bundle on the play channel right after
 * login (see {@link PackSyncServerPusher}) and the client executes on its main
 * thread via {@code addScheduledTask}. Registry-validation ordering does not
 * apply here — 1.12.2 has no frozen-registry validation gate that script
 * output would have to precede, so no Netty-thread latch is needed. This is
 * the documented behavioral difference versus the configuration-phase
 * platforms.
 *
 * <p>Rejections still disconnect (v1 UX parity): the common pipeline returns a
 * message and the handler drops the player back to the multiplayer screen with
 * the {@code /nekojs trust <address>} hint.
 */
public final class PackSyncMessageHandler {

    private PackSyncMessageHandler() {}

    /** Hash list handler: forwards to the common pipeline on the main thread. */
    public static class HashList implements IMessageHandler<PackHashListPacket, IMessage> {

        @Override
        public IMessage onMessage(PackHashListPacket message, MessageContext ctx) {
            String address = resolveServerAddress();
            List<PackSyncClient.HashEntry> entries = new ArrayList<>();
            for (PackHashListPacket.Entry entry : message.entries()) {
                entries.add(new PackSyncClient.HashEntry(entry.syncId(), entry.hash()));
            }
            Minecraft.getMinecraft().addScheduledTask(() -> PackSyncClient.handleHashList(address, entries));
            return null;
        }
    }

    /** Bundle handler: runs the pipeline; on rejection disconnects with the reason. */
    public static class Bundle implements IMessageHandler<PackBundlePacket, IMessage> {

        @Override
        public IMessage onMessage(PackBundlePacket message, MessageContext ctx) {
            List<SyncedPack> packs = new ArrayList<>();
            for (PackBundlePacket.Pack pack : message.packs()) {
                List<PackContentFile> files = new ArrayList<>();
                for (PackBundlePacket.FileEntry file : pack.files()) {
                    files.add(new PackContentFile(file.relativePath(), file.bytes()));
                }
                packs.add(SyncedPack.of(pack.syncId(), pack.scope(), null, pack.manifestJson(), files));
            }
            Minecraft.getMinecraft().addScheduledTask(() -> {
                PackSyncClient.Outcome outcome = PackSyncClient.handleBundle(packs);
                if (outcome.shouldDisconnect()) {
                    disconnectWithReason(outcome.disconnect());
                }
            });
            return null;
        }
    }

    /** Server address for bucketing: the entry the user connected to (play phase, always known here). */
    static String resolveServerAddress() {
        try {
            net.minecraft.client.multiplayer.ServerData server = Minecraft.getMinecraft().getCurrentServerData();
            if (server != null && server.serverIP != null && !server.serverIP.trim().isEmpty()) {
                return server.serverIP.trim().toLowerCase();
            }
        } catch (Throwable ignored) {
            // fall through to unknown
        }
        return "unknown";
    }

    /** Vanilla-style client-side disconnect: quit packet, drop world, show the reason. */
    private static void disconnectWithReason(String reason) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world != null) {
            mc.world.sendQuittingDisconnectingPacket();
        }
        mc.loadWorld(null);
        mc.displayGuiScreen(new GuiDisconnected(
            new GuiMultiplayer(new GuiMainMenu()), "disconnect.kicked", new TextComponentString(reason)));
    }
}
