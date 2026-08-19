package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.pack.sync.PackContentFile;
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
 * 配置阶段包分发任务（NeoForge 26.x / 1.21.1 共用）：通过
 * {@link RegisterConfigurationTasksEvent}（FML 官方入口，免 mixin）挂进每个连接的
 * 配置任务队列——排在 NeoForge 自身的通道协商任务之后执行，此时自定义 payload 已可发送。
 *
 * <p>行为：engine.toml packSync 启用时推哈希清单；mode=all 且有包时紧随其后全量推
 * bundle（无请求往返——客户端注册表校验依赖包先到）。本地/单人内存连接跳过。
 */
public class PackSyncConfigurationTask implements ConfigurationTask {

    public static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(NekoJS.MODID + ":pack_sync");

    private final ServerConfigurationPacketListener listener;

    private PackSyncConfigurationTask(ServerConfigurationPacketListener listener) {
        this.listener = listener;
    }

    /** 事件入口：packSync 未启用或本地连接时不注册任务（零开销）。 */
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
            List<PackHashListPayload.HashEntry> hashes = new ArrayList<>();
            for (SyncedPack pack : packs) {
                hashes.add(new PackHashListPayload.HashEntry(pack.syncId(), pack.hash()));
            }
            listener.send(new PackHashListPayload(hashes));
            if (!PackSyncServer.hashOnly() && !hashes.isEmpty()) {
                listener.send(toBundlePayload(packs));
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

    static PackBundlePayload toBundlePayload(List<SyncedPack> packs) {
        List<PackBundlePayload.PackEntry> entries = new ArrayList<>();
        for (SyncedPack pack : packs) {
            List<PackBundlePayload.FileEntry> files = new ArrayList<>();
            for (PackContentFile file : pack.files()) {
                files.add(new PackBundlePayload.FileEntry(file.relativePath(), file.bytes()));
            }
            entries.add(new PackBundlePayload.PackEntry(
                pack.syncId(),
                pack.scopeName(),
                pack.manifestJson().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                files));
        }
        return new PackBundlePayload(entries);
    }
}
