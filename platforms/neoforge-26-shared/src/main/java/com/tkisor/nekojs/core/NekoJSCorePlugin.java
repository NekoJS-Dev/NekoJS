package com.tkisor.nekojs.core;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.tkisor.nekojs.api.*;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.core.plugin.NekoCommonManualDeclarations;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.core.plugin.TypeDocsRegister;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegistry;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.api.event.ScriptEvents;
import com.tkisor.nekojs.probe.events.ProbeEvents;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.core.plugin.RecipeNamespaceRegister;
import com.tkisor.nekojs.bindings.event.*;
import com.tkisor.nekojs.bindings.event.client.ClientEvents;
import com.tkisor.nekojs.bindings.event.client.KeyBindEvents;
import com.tkisor.nekojs.bindings.recipe.MinecraftRecipeHandler;
import com.tkisor.nekojs.bindings.static_access.BlockJS;
import com.tkisor.nekojs.bindings.static_access.CapabilitiesJS;
import com.tkisor.nekojs.bindings.static_access.ColorJS;
import com.tkisor.nekojs.bindings.static_access.FluidJS;
import com.tkisor.nekojs.bindings.static_access.FluidIngredientJS;
import com.tkisor.nekojs.bindings.RecipeSchemaBinding;
import com.tkisor.nekojs.bindings.static_access.IngredientFactory;
import com.tkisor.nekojs.bindings.static_access.ItemJS;
import com.tkisor.nekojs.js.DelegatingBinding;
import com.tkisor.nekojs.api.data.Binding;
import java.util.Set;
import com.tkisor.nekojs.bindings.static_access.NativeEventsJS;
import com.tkisor.nekojs.bindings.static_access.StringUtilsJS;
import com.tkisor.nekojs.bindings.static_access.TestJS;
import com.tkisor.nekojs.bindings.static_access.TimeJS;
import com.tkisor.nekojs.bindings.static_access.UUIDJS;
import com.tkisor.nekojs.bindings.static_access.UtilsJS;
import com.tkisor.nekojs.bindings.static_access.NekoGlobal;
import com.tkisor.nekojs.js.type_adapter.*;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.wrapper.AssetGeneratorJS;
import com.tkisor.nekojs.wrapper.FluidAmounts;
import com.tkisor.nekojs.wrapper.clientdata.ClientDataSyncJS;
import com.tkisor.nekojs.wrapper.network.NetworkJS;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.TriState;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

@RegisterNekoJSPlugin(priority = NekoJSPlugin.CORE_PRIORITY)
public class NekoJSCorePlugin implements NekoJSPlugin {

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(PlayerEvents.GROUP);
        registry.register(ServerEvents.GROUP);
        registry.register(BlockEvents.GROUP);
        registry.register(ItemEvents.GROUP);
        registry.register(EntityEvents.GROUP);
        registry.register(GoalEvents.GROUP);
        registry.register(CommandEvents.GROUP);
        registry.register(RegistryEvents.GROUP);
        registry.register(CapabilityEvents.GROUP);
        registry.register(LevelEvents.GROUP);
        registry.register(ScriptEvents.GROUP);
        registry.register(NetworkEvents.GROUP);
        registry.register(ProbeEvents.GROUP);
    }

    @Override
    public void registerClientEvents(EventGroupRegistry registry) {
        registry.register(ClientEvents.GROUP);
        registry.register(KeyBindEvents.GROUP);
    }

    @Override
    public void registerBinding(BindingRegistry registry) {
        // 同名原版类组合绑定（与下方 Item/Block 代理委托同模式）：工厂方法走 helper，
        // 其余成员委托同名 vanilla/NeoForge 类的静态成员（如 Ingredient.empty、
        // Capabilities.ItemHandler.BLOCK）。Binding.of 的 valueType 声明 helper 类型供
        // preflight/probe 反射（代理动态成员反射不到）。
        registry.register(Binding.of("Ingredient", new DelegatingBinding(new IngredientFactory(),
                net.minecraft.world.item.crafting.Ingredient.class,
                Set.of("of", "item", "tag", "any", "all", "not")), IngredientFactory.class));
        registry.register("RecipeSchema", new RecipeSchemaBinding());
        registry.register(Binding.of("Fluid", new DelegatingBinding(new FluidJS(),
                net.minecraft.world.level.material.Fluid.class,
                Set.of("of", "water", "lava", "empty", "ingredient", "sizedIngredient")), FluidJS.class));
        registry.register(Binding.of("Capabilities", new DelegatingBinding(new CapabilitiesJS(),
                net.neoforged.neoforge.capabilities.Capabilities.class,
                Set.of("itemHandler", "energyStorage", "fluidTank")), CapabilitiesJS.class));
        registry.register(Binding.of("FluidIngredient", new DelegatingBinding(new FluidIngredientJS(),
                net.neoforged.neoforge.fluids.crafting.FluidIngredient.class,
                Set.of("of", "fluid", "tag", "sized")), FluidIngredientJS.class));
        registry.register("FluidAmounts", FluidAmounts.class);
        registry.register("Fluids", Fluids.class);
        registry.register("FluidStack", FluidStack.class);
        ItemJS itemHelper = new ItemJS();
        registry.register("Color", new ColorJS());
        registry.register("UUID", new UUIDJS());
        registry.register("StringUtils", new StringUtilsJS());
        registry.register("Time", new TimeJS());
        registry.register("Utils", new UtilsJS());
        // NativeEventsJS implements Binding so its close() (→ clear()) runs on STARTUP
        // reload, unregistering the previous round's native NeoForge event listeners
        // before the scripts re-register them. Avoids listeners accumulating on reload.
        if (registry.scriptType() == ScriptType.STARTUP) {
            NativeEventsJS nativeEvents = new NativeEventsJS();
            registry.register(nativeEvents);
        }
        registry.register(ScriptType.TEST, "Test", new TestJS());
        registry.register("TriState", TriState.class);
        registry.register("Network", NetworkJS.class);
        // 服务端→客户端键值推送（客户端侧只读视图 clientData 由 common 内置插件注册）
        registry.register("ClientData", ClientDataSyncJS.class);
        // 类型化资产生成（KubeJS 风格 blockState/blockModel/itemModel/texture），写入
        // <gameDir>/nekojs/assets 资源包，与 generateAssets 事件同目录，reload 时懒读生效
        registry.register("Assets", new AssetGeneratorJS());
        registry.register("global", NekoGlobal.shared());
        registry.register("ItemStack", ItemStack.class);
        registry.register("Items", Items.class);
        // 全局 Item 是 ProxyObject 代理委托（of/empty/id/idOf 走 ItemJS，其余委托 MC Item 类）。
        // 代理的动态成员 Java 反射不到，preflight 会误报 "has no member 'of'"，
        // 故用 Binding.of(name, value, valueType) 显式声明 valueType=ItemJS。
        registry.register(Binding.of("Item", new DelegatingBinding(itemHelper, Item.class, Set.of("id", "idOf", "of", "empty")), ItemJS.class));
        // 全局 Block 同样走代理委托（id/idOf 走 BlockJS，其余委托 MC Block 类）。
        BlockJS blockHelper = new BlockJS();
        registry.register(Binding.of("Block", new DelegatingBinding(blockHelper, net.minecraft.world.level.block.Block.class, Set.of("id", "idOf")), BlockJS.class));
        registry.register("BlockPos", BlockPos.class);
        registry.register("Direction", Direction.class);
        registry.register("Vec3", Vec3.class);
        registry.register("AABB", AABB.class);
        registry.register("MutableComponent", MutableComponent.class);
        registry.register("DyeColor", DyeColor.class);
        registry.register("SoundEvents", SoundEvents.class);
        registry.register("ParticleTypes", ParticleTypes.class);
        registry.register("Blocks", Blocks.class);
        registry.register("EntityType", EntityType.class);
        registry.register("CompoundTag", CompoundTag.class);
        registry.register("Identifier", Identifier.class);
        registry.register("MobEffects", MobEffects.class);
        registry.register("MobEffectInstance", MobEffectInstance.class);
        registry.register("DamageTypes", DamageTypes.class);
        registry.register("Component", Component.class);

        if (registry.scriptType() == ScriptType.CLIENT) {
            registry.register("Minecraft", Minecraft.class);
            registry.register("Screen", Screen.class);
            registry.register("Window", Window.class);
            registry.register("KeyMapping", KeyMapping.class);
            registry.register("InputConstants", InputConstants.class);
        }
    }

    @Override
    public void registerAdapters(JSTypeAdapterRegistry registry) {
        registry.register(new ItemStackAdapter());
        registry.register(new IngredientAdapter());
        registry.register(new SizedIngredientAdapter());
        registry.register(new FluidStackAdapter());
        registry.register(new FluidIngredientAdapter());
        registry.register(new SizedFluidIngredientAdapter());
        registry.register(new IdentifierAdapter());
        registry.register(new HolderAdapter());
        registry.register(new RecipeFilterAdapter());
        registry.register(new RecipeJsonValueAdapter());
        registry.register(new JsonObjectAdapter());
        registry.register(new ComponentAdapter());
        registry.register(new EntityTypeAdapter());
        registry.register(new BlockAdapter());
        registry.register(new com.tkisor.nekojs.js.type_adapter.BlockStateAdapter());
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
        // Codec 兜底适配器示范（precedence=LOWEST）：任意 JS 值 -> JsonElement -> codec.parse(JsonOps)
        TypeAdapterDsl.registerCodec(registry, Fireworks.class, Fireworks.CODEC);
    }

    @Override
    public void registerRecipeNamespaces(RecipeNamespaceRegister registry) {
//        registry.registerSchema();
        registry.register(new RecipeNamespaceEntry("minecraft", e -> new MinecraftRecipeHandler((com.tkisor.nekojs.wrapper.event.server.RecipeEventJS) e), MinecraftRecipeHandler.class));
    }

    @Override
    public void registerTypeDocs(TypeDocsRegister registry) {
        registry.register(TypeDocCatalogEntry.binding("Item", "NekoItemHelper", "Script-friendly ItemStack factory and helpers (KubeJS-style: Item.of(id)); delegates of/empty to the helper, rest to MC Item.", List.of("Item.of('minecraft:stone')", "Item.of('minecraft:stone', 4)", "Item.empty()")));
        registry.register(TypeDocCatalogEntry.binding("Ingredient", "NekoIngredientHelper", "Script-friendly Ingredient and IngredientJS helper.", List.of("Ingredient.of('minecraft:stone')", "Ingredient.tag('minecraft:planks')")));
        registry.register(TypeDocCatalogEntry.binding("Fluid", "NekoFluidHelper", "Script-friendly FluidStack helper.", List.of("Fluid.of('minecraft:water', FluidAmounts.BUCKET)", "Fluid.of({ fluid: 'minecraft:water', amount: 250 })")));
        registry.register(TypeDocCatalogEntry.binding("FluidIngredient", "NekoFluidIngredientHelper", "Script-friendly FluidIngredient and SizedFluidIngredient helper.", List.of("FluidIngredient.of('minecraft:water')", "FluidIngredient.sized('minecraft:water', 250)")));
        registry.register(TypeDocCatalogEntry.binding("Assets", "AssetGeneratorJS", "Typed asset generators writing into NekoJS's assets pack (blockState/blockModel/itemModel/texture); takes effect on the next resource reload.", List.of(
                "Assets.blockState('mymod:my_block', 'mymod:block/my_block')",
                "Assets.blockModel('mymod:my_block', { parent: 'minecraft:block/cube_all', textures: { all: 'my_block' } })",
                "Assets.itemModel('mymod:my_item', { parent: 'minecraft:item/generated', textures: { layer0: 'my_item' } })",
                "Assets.texture('mymod:item/my_item')")));
        registry.register(TypeDocCatalogEntry.binding("ServerEvents", null, "Server-side event group, including recipe editing.", List.of("ServerEvents.recipes(event => { })", "ServerEvents.afterRecipes(event => { })")));
        registry.register(TypeDocCatalogEntry.binding("ProbeEvents", null,
                "Probe generation customization events (probe.*). Listeners go in server_scripts; they run when /nekojs probe is invoked.",
                List.of(
                        "ProbeEvents.modifyType.listen(event => { event.forClass('net.minecraft.world.entity.player.Player').renameMethod('getX', 'getCustom'); })",
                        "ProbeEvents.assignType.listen(event => event.assign('net.minecraft.world.item.ItemStack', 'string'))",
                        "ProbeEvents.addGlobal.listen(event => event.add('MyFlag', 'boolean'))")));
        registry.register(TypeDocCatalogEntry.binding(ScriptType.TEST, "Test", "NekoTestHelper", "Test-script assertion and smoke test helper.", List.of("Test.section('recipes').assertTrue(true, 'ready').summary()")));
        registry.register(TypeDocCatalogEntry.binding(ScriptType.STARTUP, "NativeEvents", null, "Startup-side native NeoForge event bridge.", List.of("NativeEvents.onEvent('event.class.Name', event => { })")));
        registry.register(TypeDocCatalogEntry.binding(
                ScriptType.STARTUP,
                "ScriptEvents",
                null,
                "Startup-side custom server/client event method registration event group.",
                List.of("ScriptEvents.server(event => event.register('CustomServerEvents', 'playerTick', "
                        + "'net.neoforged.neoforge.event.tick.PlayerTickEvent.Post'))")));
        registry.register(TypeDocCatalogEntry.binding(ScriptType.STARTUP, "RegistryEvents", null, "Startup-side registry builders, including scripted entity types.", List.of("RegistryEvents.entityType(event => { })")));
        registry.register(TypeDocCatalogEntry.binding(ScriptType.STARTUP, "GoalEvents", null, "Startup-side goal registration for existing or scripted entity types.", List.of("GoalEvents.register(event => { })")));

        NekoCommonManualDeclarations.register(registry);
    }

}
