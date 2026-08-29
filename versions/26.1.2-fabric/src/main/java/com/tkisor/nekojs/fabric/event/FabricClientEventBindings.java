package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.client.ClientTickEventJS;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * 客户端事件面的 fabric 桥 v1：tickPre / tickPost + CLIENT 脚本加载钩子。
 *
 * <p>组名与总线名和 NeoForge 侧一致（NeoForge 的 ClientEvents 组另有 generateAssets/lang
 * 等 dispatch 事件与更多 tick 外总线——随各事件的 fabric 桥落地逐个加入）。
 * CLIENT 脚本在 ClientLifecycleEvents.CLIENT_STARTED 加载（NeoForge 侧在
 * client setup 期加载，时机等价：都在客户端资源就绪后）。
 */
public final class FabricClientEventBindings {

    /** 与 NeoForge 侧 bindings/event/client/ClientEvents 同名（fabric 子集）。 */
    public static final EventGroup CLIENT_EVENTS = EventGroup.of("ClientEvents");

    public static final EventBusJS<ClientTickEventJS, Void> TICK_PRE =
            CLIENT_EVENTS.client("tickPre", ClientTickEventJS.class);

    public static final EventBusJS<ClientTickEventJS, Void> TICK_POST =
            CLIENT_EVENTS.client("tickPost", ClientTickEventJS.class);

    private FabricClientEventBindings() {}

    /**
     * @param loadClientScripts CLIENT 脚本加载动作（客户端资源就绪后、CLIENT_STARTED 时机）
     */
    public static void register(Runnable loadClientScripts) {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> loadClientScripts.run());
        ClientTickEvents.START_CLIENT_TICK.register(client -> TICK_PRE.post(ClientTickEventJS.INSTANCE));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            TICK_POST.post(ClientTickEventJS.INSTANCE);
            // 与 NeoForge 侧 NekoJSClient#onClientTickPost 同：tick 上冲刷 CLIENT 侧 node timers
            com.tkisor.nekojs.fabric.NekoJSFabricMod.flushClientNodeTimers();
        });
    }
}
