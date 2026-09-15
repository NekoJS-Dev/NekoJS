package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.client.ClientTickEventJS;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

/**
 * 客户端事件面的 fabric 桥 v1：tickPre / tickPost + CLIENT 脚本加载钩子。
 *
 * <p>组名与总线名和 NeoForge 侧一致（NeoForge 的 ClientEvents 组另有 generateAssets/lang
 * 等 dispatch 事件与更多 tick 外总线——随各事件的 fabric 桥落地逐个加入）。
 * 已知时机差异：NeoForge 侧 CLIENT 脚本在 client setup（construct 期 enqueueWork，
 * 资源就绪前）加载；fabric 侧只能挂 {@code CLIENT_STARTED}（初始资源重载之后）。
 * 对 tick 类总线无影响；construct~start 之间触发的注册类事件在 fabric 上会错过。
 */
public final class FabricClientEventBindings {

    /** 与 NeoForge 侧 bindings/event/client/ClientEvents 同名（fabric 子集）。 */
    public static final EventGroup CLIENT_EVENTS = EventGroup.of("ClientEvents");

    public static final EventBusJS<ClientTickEventJS, Void> TICK_PRE =
            CLIENT_EVENTS.client("tickPre", ClientTickEventJS.class);

    public static final EventBusJS<ClientTickEventJS, Void> TICK_POST =
            CLIENT_EVENTS.client("tickPost", ClientTickEventJS.class);

    // tick：tickPost 的裸名别名（与 NeoForge 侧一致）。
    @Deprecated
    public static final EventBusJS<ClientTickEventJS, Void> TICK =
            CLIENT_EVENTS.client("tick", ClientTickEventJS.class);

    private FabricClientEventBindings() {}

    /**
     * @param loadClientScripts CLIENT 脚本加载动作（客户端资源就绪后、CLIENT_STARTED 时机）
     */
    public static void register(Runnable loadClientScripts) {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> loadClientScripts.run());
        ClientTickEvents.START_CLIENT_TICK.register(client -> TICK_PRE.post(ClientTickEventJS.INSTANCE));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            TICK_POST.post(ClientTickEventJS.INSTANCE);
            TICK.post(ClientTickEventJS.INSTANCE);
            // 与 NeoForge 侧 NekoJSClient#onClientTickPost 同：tick 上冲刷 CLIENT 侧 node timers
            com.tkisor.nekojs.fabric.NekoJSFabricMod.flushClientNodeTimers();
        });
        // 物品 tooltip（孪生 ItemEvents.TOOLTIP，按物品 id dispatch）：lines 是渲染前的
        // 可变列表，监听器 mutate 即生效（fabric 回调不可取消整段渲染，删空列表即近似取消）
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) ->
                com.tkisor.nekojs.bindings.event.ItemEvents.TOOLTIP.post(
                        new com.tkisor.nekojs.wrapper.event.item.ItemTooltipEventJS(stack, lines),
                        stack.getItem()));
    }
}
