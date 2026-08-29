package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.fabric.event.FabricClientEventBindings;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric 客户端入口（票 03）：CLIENT 脚本加载 + 客户端事件桥（tick v1）。
 *
 * <p>与 NeoForge 侧 {@code NekoJSClient} 对应：那边在 mod 事件（client setup）里加载
 * CLIENT 脚本并接客户端总线；这边在 {@code ClientModInitializer} 注册 fabric 桥，
 * 脚本加载挂 {@code CLIENT_STARTED}（客户端资源就绪后）。
 */
public final class NekoJSFabricClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("NekoJS-Fabric");

    @Override
    public void onInitializeClient() {
        LOGGER.info("NekoJS fabric client entrypoint reached.");
        FabricClientEventBindings.register(() -> {
            if (NekoJSFabricMod.RUNTIME_ROOT != null) {
                NekoJSFabricMod.RUNTIME_ROOT.scriptManagerOf(ScriptType.CLIENT).loadScripts();
            }
        });
        LOGGER.info("NekoJS fabric client bindings registered (CLIENT scripts load at CLIENT_STARTED).");
    }
}
