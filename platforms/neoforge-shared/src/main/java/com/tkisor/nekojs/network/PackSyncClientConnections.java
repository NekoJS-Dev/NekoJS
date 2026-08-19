package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.NekoJSMod;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.pack.sync.PackSyncClient;
import com.tkisor.nekojs.platform.Platform;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * 包分发的客户端连接接线（NeoForge 26.x / 1.21.1 共用）：
 *
 * <ul>
 *   <li>安装 CLIENT 脚本重载钩子（{@link PackSyncClient} 管线在激活/卸载
 *       SERVER_CACHE 包后触发整类型重载，与 F3+T 的 CLIENT reload 同路径）。</li>
 *   <li>断线/离开世界（含被信任关口断开）时卸载远端包缓存集合并重载客户端脚本
 *       （缓存文件保留，二次连入按哈希复用）。</li>
 * </ul>
 *
 * <p>仅客户端注册（由各平台 {@code NekoJSNetwork} 在 Dist.CLIENT 分支调用 {@link #install}）；
 * 本类引用的 {@code ClientPlayerNetworkEvent} 为客户端专属事件类，专用服务器不加载本类。
 */
public final class PackSyncClientConnections {

    private PackSyncClientConnections() {}

    public static void install() {
        PackSyncClient.installClientReloadHook(PackSyncClientConnections::reloadClientScripts);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(PackSyncClientConnections::onLoggingOut);
    }

    private static void reloadClientScripts() {
        if (!Platform.isClient() || NekoJSMod.RUNTIME_ROOT == null) return;
        var manager = NekoJSMod.RUNTIME_ROOT.scriptManagerOrNull(ScriptType.CLIENT);
        if (manager == null) return;
        NekoJS.LOGGER.debug("Reloading CLIENT scripts after server pack sync change");
        NekoJSMod.RUNTIME_ROOT.reload(ScriptType.CLIENT);
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PackSyncClient.handleDisconnect();
    }
}
