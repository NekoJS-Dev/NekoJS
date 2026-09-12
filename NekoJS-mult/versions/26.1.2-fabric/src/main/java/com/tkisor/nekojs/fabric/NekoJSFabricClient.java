package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.fabric.event.FabricClientEventBindings;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric 客户端入口：CLIENT 脚本加载 + 客户端事件桥（当前只接 tick）。
 *
 * <p>与 NeoForge 侧 {@code NekoJSClient} 对应：那边在 mod 事件（client setup，资源就绪前）
 * 里加载 CLIENT 脚本并接客户端总线；这边在 {@code ClientModInitializer} 注册 fabric 桥，
 * 脚本加载挂 {@code CLIENT_STARTED}（初始资源重载后）——时机差异见
 * {@link FabricClientEventBindings}。
 */
public final class NekoJSFabricClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("NekoJS-Fabric");

    @Override
    public void onInitializeClient() {
        LOGGER.info("NekoJS fabric client entrypoint reached.");
        FabricPackSync.registerClient();
        FabricPlayNetwork.registerClient();
        FabricPDataSync.registerClient();
        // 脚本按键绑定：register 入口 + pressed/released/tick 轮询（ClientTickEvents）
        com.tkisor.nekojs.bindings.event.client.KeyBindEvents.register();
        FabricClientEventBindings.register(() -> {
            // root 经 loader entry 的 package-private accessor 获取（fabric entrypoint
            // 由 loader 反射实例化，无法构造注入）；未装配完成时保留原跳过语义
            var root = NekoJSFabricMod.runtimeRootOrNull();
            if (root == null) {
                LOGGER.error("CLIENT_STARTED: RUNTIME_ROOT not assembled yet, skipping CLIENT script load");
                return;
            }
            root.scriptManagerOf(ScriptType.CLIENT).loadScripts();
        });
        LOGGER.info("NekoJS fabric client bindings registered (CLIENT scripts load at CLIENT_STARTED).");
    }
}
