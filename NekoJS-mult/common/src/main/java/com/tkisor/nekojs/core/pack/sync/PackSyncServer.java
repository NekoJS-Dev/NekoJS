package com.tkisor.nekojs.core.pack.sync;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.pack.ScriptPack;
import com.tkisor.nekojs.core.pack.ScriptPackRegistry;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 包分发服务端 gather（平台无关）：{@link #collectSyncPacks()} 取启用的 GLOBAL + WORLD
 * 包中 {@code clientSync != false} 者，生成快照（syncId / 哈希 / manifest 原文 / 内容文件）。
 * 是否分发由平台入口根据 {@link #enabled()} 决定；{@code hashOnly} 模式只发哈希不发 bundle。
 */
public final class PackSyncServer {

    private PackSyncServer() {}

    /** 当前 engine.toml 的 packSync 是否启用（mode != off）。 */
    public static boolean enabled() {
        return ClassFilter.INSTANCE.config().packSyncEnabled();
    }

    /** 当前 mode 是否为 hashOnly（只同步哈希、客户端不执行远端包）。 */
    public static boolean hashOnly() {
        return SandboxConfig.PACK_SYNC_HASH_ONLY.equalsIgnoreCase(ClassFilter.INSTANCE.config().packSyncMode());
    }

    /** gather 待分发包：enabled && clientSync 的 GLOBAL + WORLD 包。 */
    public static List<SyncedPack> collectSyncPacks() {
        List<SyncedPack> out = new ArrayList<>();
        for (ScriptPack pack : ScriptPackRegistry.get().enabledPacks()) {
            if (pack.scope() == com.tkisor.nekojs.core.pack.ScriptPackScope.SERVER_CACHE) continue;
            if (!pack.manifest().clientSync()) continue;
            try {
                byte[] manifestBytes = PackHasher.readManifestBytes(pack.root());
                List<PackContentFile> files = PackHasher.readContentFiles(pack.root());
                out.add(new SyncedPack(
                    syncId(pack),
                    pack.scope().name(),
                    PackHasher.hash(manifestBytes, files),
                    new String(manifestBytes, StandardCharsets.UTF_8),
                    files));
            } catch (Exception e) {
                NekoJS.LOGGER.warn("Failed to snapshot script pack {} for sync: {}", pack, e.toString());
            }
        }
        return out;
    }

    /** syncId = {@code <scope idSegment>:<packId>}，如 {@code packs:demo} / {@code worldpacks:demo}。 */
    public static String syncId(ScriptPack pack) {
        return pack.scope().idSegment() + ":" + pack.id();
    }
}
