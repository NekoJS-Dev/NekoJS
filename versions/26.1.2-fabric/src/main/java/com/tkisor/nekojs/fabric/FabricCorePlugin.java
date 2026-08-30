package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegistry;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.bindings.event.BlockEvents;
import com.tkisor.nekojs.core.plugin.AdaptersPoint;
import com.tkisor.nekojs.core.plugin.EventsPoint;
import com.tkisor.nekojs.fabric.event.FabricEntityEventBindings;
import com.tkisor.nekojs.fabric.event.FabricItemEventBindings;
import com.tkisor.nekojs.fabric.event.FabricServerEventBindings;
import com.tkisor.nekojs.js.type_adapter.BlockAdapter;
import com.tkisor.nekojs.js.type_adapter.BlockEntityTypeAdapter;
import com.tkisor.nekojs.js.type_adapter.BlockPosAdapter;
import com.tkisor.nekojs.js.type_adapter.BlockStateAdapter;
import com.tkisor.nekojs.js.type_adapter.ComponentAdapter;
import com.tkisor.nekojs.js.type_adapter.CompoundTagAdapter;
import com.tkisor.nekojs.js.type_adapter.CreativeModeTabAdapter;
import com.tkisor.nekojs.js.type_adapter.EntityTypeAdapter;
import com.tkisor.nekojs.js.type_adapter.HolderAdapter;
import com.tkisor.nekojs.js.type_adapter.IdentifierAdapter;
import com.tkisor.nekojs.js.type_adapter.ItemAdapter;
import com.tkisor.nekojs.js.type_adapter.MobEffectAdapter;
import com.tkisor.nekojs.js.type_adapter.ParticleTypeAdapter;
import com.tkisor.nekojs.js.type_adapter.PotionAdapter;
import com.tkisor.nekojs.js.type_adapter.SoundEventAdapter;
import com.tkisor.nekojs.js.type_adapter.TagKeyAdapter;
import com.tkisor.nekojs.js.type_adapter.Vec3Adapter;

/**
 * Fabric 侧核心插件 v1：注册<b>当前已有 fabric 桥</b>的事件组与平台无关类型适配器。
 *
 * <p>与 NeoForge 侧 {@code NekoJSCorePlugin} 的关系：那份清单里 ServerEvents /
 * PlayerEvents 等主体组与载荷类都是 NeoForge 面（整文件守卫），fabric 端随
 * fabric 侧各事件桥落地逐个加入本清单——脚本写法跨加载器一致的前提是
 * 总线与载荷中立（{@code BlockEvents.BROKEN} 即样板）。服务端生命周期/tick/
 * 进出服/chat 与实体 joinLevel/death 已由 fabric 桥以同名组 + 中立 payload 接通。
 *
 * <p>类型适配器只登记平台无关子集（配方/流体簇的 ItemStack/Ingredient 等适配器
 * 是 NeoForge 面的 crafting 类型，fabric 等价物随配方面移植再定）——适配器是
 * dispatch 字符串键（如 {@code EntityEvents.joinLevel('minecraft:zombie', ...)}）
 * 落到注册表类型的必要通道，缺失时报 "Unsupported target type"。
 */
public final class FabricCorePlugin implements NekoJSPlugin, EventsPoint.Contributor,
        com.tkisor.nekojs.core.plugin.ClientEventsPoint.Contributor,
        com.tkisor.nekojs.core.plugin.BindingsPoint.Contributor, AdaptersPoint.Contributor {

    @Override
    public void registerBinding(com.tkisor.nekojs.api.data.BindingRegistry registry) {
        // 服务端→客户端键值推送（只读侧 clientData 由 common 内置插件注册）
        registry.register("ClientData", com.tkisor.nekojs.wrapper.clientdata.ClientDataSyncJS.class);
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        // broken：FabricBlockEventBindings 已接 PlayerBlockBreakEvents.BEFORE
        registry.register(BlockEvents.GROUP);
        // 自定义脚本事件的声明面（STARTUP 的 ScriptEvents.server/client，平台无关）
        registry.register(com.tkisor.nekojs.api.event.ScriptEvents.GROUP);
        // 服务端生命周期 / tick / 玩家进出服 + chat：中立 payload 子集
        registry.register(FabricServerEventBindings.SERVER_EVENTS);
        registry.register(FabricServerEventBindings.PLAYER_EVENTS);
        // 实体 joinLevel / death / damagePre / damagePost（按实体类型 dispatch）
        registry.register(FabricEntityEventBindings.ENTITY_EVENTS);
        // 物品 rightClicked（按物品 id dispatch）
        registry.register(FabricItemEventBindings.ITEM_EVENTS);
    }

    @Override
    public void registerClientEvents(EventGroupRegistry registry) {
        // 客户端 tick 事件（CLIENT 脚本由 NekoJSFabricClient 在 CLIENT_STARTED 加载）
        registry.register(com.tkisor.nekojs.fabric.event.FabricClientEventBindings.CLIENT_EVENTS);
    }

    @Override
    public void registerAdapters(JSTypeAdapterRegistry registry) {
        registry.register(new IdentifierAdapter());
        registry.register(new HolderAdapter());
        registry.register(new ComponentAdapter());
        registry.register(new EntityTypeAdapter());
        registry.register(new BlockAdapter());
        registry.register(new BlockStateAdapter());
        registry.register(new BlockPosAdapter());
        registry.register(new Vec3Adapter());
        registry.register(new CompoundTagAdapter());
        registry.register(new TagKeyAdapter());
        registry.register(new ItemAdapter());
        registry.register(new MobEffectAdapter());
        registry.register(new PotionAdapter());
        registry.register(new SoundEventAdapter());
        registry.register(new ParticleTypeAdapter());
        registry.register(new BlockEntityTypeAdapter());
        registry.register(new CreativeModeTabAdapter());
    }
}
