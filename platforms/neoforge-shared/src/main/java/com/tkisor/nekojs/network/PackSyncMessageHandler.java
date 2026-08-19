package com.tkisor.nekojs.network;

import com.tkisor.nekojs.core.pack.sync.PackSyncClient;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * 包分发 payload 的客户端接收处理器（NeoForge 26.x / 1.21.1 共用，payload 类各平台
 * 同 FQCN 复制、线格式一致）。
 *
 * <p>配置阶段时序（全盘参考 Katton）：NeoForge 在网络线程收包 → {@code prepareMainThreadWork}
 * 登记 latch → {@code enqueueWork} 切主线程执行落盘/验签/信任/脚本执行 → 网络线程
 * {@code awaitMainThreadWork} 阻塞直到主线程完成。网络线程阻塞期间连接的后续包
 * （注册表同步、finish configuration）不会推进——保证原版注册表校验时远端脚本产物已就位。
 *
 * <p>地址解析：配置阶段 {@code Minecraft#getCurrentServer()} 依赖 play 阶段的
 * ClientPacketListener（此时尚未建立），因此从连接的远端套接字地址取 hostname——
 * 未信任断连消息中打印的即 bucket 所用地址，用户照抄 {@code /nekojs trust} 即可。
 */
public final class PackSyncMessageHandler {

    private PackSyncMessageHandler() {}

    /** S2C 配置阶段哈希清单。本地/单人内存连接跳过（同步语义只针对真实远端连接）。 */
    public static void handleHashListOnClient(PackHashListPayload payload, IPayloadContext context) {
        if (context.connection().isMemoryConnection()) return;
        String address = resolveServerAddress(context);
        List<PackSyncClient.HashEntry> entries = new ArrayList<>();
        for (PackHashListPayload.HashEntry entry : payload.entries()) {
            entries.add(new PackSyncClient.HashEntry(entry.syncId(), entry.hash()));
        }
        PackSyncClient.prepareMainThreadWork();
        context.enqueueWork(() -> {
            try {
                PackSyncClient.handleHashList(address, entries);
            } finally {
                PackSyncClient.completeMainThreadWork();
            }
        });
        PackSyncClient.awaitMainThreadWork();
    }

    /** S2C 配置阶段 bundle：管线拒绝（验签失败/完整性失败/未信任）时断连并展示原因。 */
    public static void handleBundleOnClient(PackBundlePayload payload, IPayloadContext context) {
        if (context.connection().isMemoryConnection()) return;
        List<com.tkisor.nekojs.core.pack.sync.SyncedPack> packs = new ArrayList<>();
        for (PackBundlePayload.PackEntry pack : payload.packs()) {
            List<com.tkisor.nekojs.core.pack.sync.PackContentFile> files = new ArrayList<>();
            for (PackBundlePayload.FileEntry file : pack.files()) {
                files.add(new com.tkisor.nekojs.core.pack.sync.PackContentFile(file.relativePath(), file.bytes()));
            }
            packs.add(com.tkisor.nekojs.core.pack.sync.SyncedPack.of(
                pack.syncId(), pack.scope(), null, pack.manifestJsonText(), files));
        }
        PackSyncClient.prepareMainThreadWork();
        context.enqueueWork(() -> {
            try {
                PackSyncClient.Outcome outcome = PackSyncClient.handleBundle(packs);
                if (outcome.shouldDisconnect()) {
                    context.disconnect(Component.literal(outcome.disconnect()));
                }
            } finally {
                PackSyncClient.completeMainThreadWork();
            }
        });
        PackSyncClient.awaitMainThreadWork();
    }

    static String resolveServerAddress(IPayloadContext context) {
        SocketAddress remote = context.connection().getRemoteAddress();
        if (remote instanceof InetSocketAddress isa) {
            String host = isa.getHostString();
            if (host != null && !host.isBlank()) {
                return host.trim().toLowerCase();
            }
        }
        return remote != null ? remote.toString() : "unknown";
    }
}
