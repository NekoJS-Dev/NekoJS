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
        // 配方面（与 NeoForge 侧 NekoJSCorePlugin 同写法）：Ingredient 工厂 + 同名 vanilla 类
        // 静态成员委托 + RecipeSchema 查看器绑定
        registry.register(com.tkisor.nekojs.api.data.Binding.of("Ingredient",
                new com.tkisor.nekojs.js.DelegatingBinding(new com.tkisor.nekojs.bindings.static_access.IngredientFactory(),
                        net.minecraft.world.item.crafting.Ingredient.class,
                        java.util.Set.of("of", "item", "tag", "any", "all", "not")),
                com.tkisor.nekojs.bindings.static_access.IngredientFactory.class));
        registry.register("RecipeSchema", new com.tkisor.nekojs.bindings.RecipeSchemaBinding());
        // 脚本自定义网络通道（Network.sendToServer/sendToPlayer/sendToAll；payload 双向
        // 注册与 receiver 在 FabricPlayNetwork）
        registry.register("Network", com.tkisor.nekojs.wrapper.network.NetworkJS.class);
    }

    @Override
    public void registerRecipeNamespaces(com.tkisor.nekojs.core.plugin.RecipeNamespaceRegister registry) {
        // minecraft 命名空间配方类型（shaped/shapeless/smeling 等 handler，与 NeoForge 侧同款）
        registry.register(new com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry("minecraft",
                e -> new com.tkisor.nekojs.bindings.recipe.MinecraftRecipeHandler(
                        (com.tkisor.nekojs.wrapper.event.server.RecipeEventJS) e),
                com.tkisor.nekojs.bindings.recipe.MinecraftRecipeHandler.class));
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
        // 配方脚本（recipes/afterRecipes）：总线定义在节点孪生 ServerEvents，由
        // RecipeManagerMixin 孪生 fire；同名 "ServerEvents" 组经合并与上面的生命周期总线同组
        registry.register(com.tkisor.nekojs.bindings.event.ServerEvents.GROUP);
        // AI goal 注册（STARTUP）：消费端 FabricEntityEventBindings.postJoinLevel 在跑，
        // postRegister 由 NekoJSFabricMod.initializeScripts 在 STARTUP 脚本加载后调用
        registry.register(com.tkisor.nekojs.bindings.event.GoalEvents.GROUP);
        // 实体 joinLevel / death / damagePre / damagePost（按实体类型 dispatch）
        registry.register(FabricEntityEventBindings.ENTITY_EVENTS);
        // 物品 rightClicked（按物品 id dispatch）
        registry.register(FabricItemEventBindings.ITEM_EVENTS);
        // 脚本自定义网络通道：server/client 总线按 channel dispatch（receiver 在
        // FabricPlayNetwork，发送面 NetworkJS → PlayPacketDispatchers）
        registry.register(com.tkisor.nekojs.bindings.event.NetworkEvents.GROUP);
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
        // 配方面（共享树已随配方面移植去守卫）：物品栈/原料/过滤器/配方 JSON 值
        registry.register(new com.tkisor.nekojs.js.type_adapter.ItemStackAdapter());
        registry.register(new com.tkisor.nekojs.js.type_adapter.IngredientAdapter());
        registry.register(new com.tkisor.nekojs.js.type_adapter.RecipeFilterAdapter());
        registry.register(new com.tkisor.nekojs.js.type_adapter.RecipeJsonValueAdapter());
    }
}
