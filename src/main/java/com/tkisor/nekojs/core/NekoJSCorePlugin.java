//? if neoforge {
// Version guards retained in this file are true differences: KeyBindEvents / Assets / IdentifierAdapter are 26.x-unique
// symbols; TriState moved to net.minecraft.util in 26.x (1.21.1 is in neoforge common util).
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
//? if >=26 {
import com.tkisor.nekojs.bindings.event.client.KeyBindEvents;
//?}
import com.tkisor.nekojs.bindings.recipe.MinecraftRecipeHandler;
import com.tkisor.nekojs.bindings.static_access.BlockJS;
import com.tkisor.nekojs.bindings.static_access.CapabilitiesJS;
import com.tkisor.nekojs.bindings.static_access.ColorJS;
//? if neoforge && >=26 {
import com.tkisor.nekojs.bindings.static_access.ClientUiJS;
//?}
import com.tkisor.nekojs.bindings.static_access.DamageSourceJS;
import com.tkisor.nekojs.bindings.static_access.DataMapJS;
import com.tkisor.nekojs.bindings.static_access.FluidJS;
import com.tkisor.nekojs.bindings.static_access.FluidIngredientJS;
import com.tkisor.nekojs.bindings.RecipeSchemaBinding;
import com.tkisor.nekojs.bindings.static_access.IngredientFactory;
import com.tkisor.nekojs.bindings.static_access.ItemJS;
import com.tkisor.nekojs.bindings.static_access.KMathJS;
import com.tkisor.nekojs.bindings.static_access.ParticleOptionsJS;
import com.tkisor.nekojs.bindings.static_access.TextIcons;
import com.tkisor.nekojs.js.DelegatingBinding;
import com.tkisor.nekojs.api.data.Binding;
import java.util.Set;
import com.tkisor.nekojs.bindings.static_access.NativeEventsJS;
import com.tkisor.nekojs.bindings.static_access.StringUtilsJS;
import com.tkisor.nekojs.bindings.static_access.TestJS;
import com.tkisor.nekojs.bindings.static_access.TimeJS;
import com.tkisor.nekojs.bindings.static_access.UUIDJS;
import com.tkisor.nekojs.bindings.static_access.UtilsJS;
import com.tkisor.nekojs.js.type_adapter.*;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.script.prop.ScriptProperty;
//? if >=26 {
import com.tkisor.nekojs.wrapper.AssetGeneratorJS;
//?}
import com.tkisor.nekojs.wrapper.FluidAmounts;
import com.tkisor.nekojs.wrapper.clientdata.ClientDataSyncJS;
import com.tkisor.nekojs.wrapper.network.NetworkJS;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
//? if >=26 {
import net.minecraft.util.TriState;
//?}
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import java.util.List;
import java.util.Map;

import static com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry.TypeReference.host;
import static com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry.TypeReference.input;

@RegisterNekoJSPlugin(priority = NekoJSPlugin.CORE_PRIORITY)
public class NekoJSCorePlugin implements NekoJSPlugin, com.tkisor.nekojs.core.plugin.BindingsPoint.Contributor, com.tkisor.nekojs.core.plugin.TypeDocsPoint.Contributor, com.tkisor.nekojs.core.plugin.NodeTypeDocsPoint.Contributor, com.tkisor.nekojs.core.plugin.EventsPoint.Contributor, com.tkisor.nekojs.core.plugin.ClientEventsPoint.Contributor, com.tkisor.nekojs.core.plugin.AdaptersPoint.Contributor, com.tkisor.nekojs.core.plugin.RecipeNamespacesPoint.Contributor {

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(PlayerEvents.GROUP);
        registry.register(PlayerEvents.GROUP, expose -> expose.versioned());
        registry.register(ServerEvents.GROUP);
        registry.register(BlockEvents.GROUP);
        registry.register(BlockEvents.GROUP, expose -> expose.versioned());
        registry.register(ItemEvents.GROUP);
        registry.register(ItemEvents.GROUP, expose -> expose.versioned());
        registry.register(EntityEvents.GROUP);
        registry.register(EntityEvents.GROUP, expose -> expose.versioned());
        registry.register(GoalEvents.GROUP);
        registry.register(GoalEvents.GROUP, expose -> expose.versioned());
        registry.register(CommandEvents.GROUP);
        registry.register(CommandEvents.GROUP, expose -> expose.versioned());
        registry.register(CapabilityEvents.GROUP);
        registry.register(CapabilityEvents.GROUP, expose -> expose.versioned());
        registry.register(LevelEvents.GROUP);
        registry.register(LevelEvents.GROUP, expose -> expose.versioned());
        registry.register(ScriptEvents.GROUP);
        registry.register(ScriptEvents.GROUP, expose -> expose.versioned());
        registry.register(NetworkEvents.GROUP);
        registry.register(NetworkEvents.GROUP, expose -> expose.versioned());
        registry.register(ProbeEvents.GROUP);
        registry.register(ProbeEvents.GROUP, expose -> expose.versioned());
    }

    @Override
    public void registerClientEvents(EventGroupRegistry registry) {
        registry.register(ClientEvents.GROUP);
        registry.register(ClientEvents.GROUP, expose -> expose.versioned());
//? if >=26 {
        registry.register(KeyBindEvents.GROUP);
        registry.register(KeyBindEvents.GROUP, expose -> expose.versioned());
//?}
    }

    @Override
    public void registerBinding(BindingRegistry registry) {
        registry.register(com.tkisor.nekojs.api.data.Binding.of("Native",
                new com.tkisor.nekojs.bindings.static_access.NativeBridgeJS()), expose -> expose.stable());
        registry.register(com.tkisor.nekojs.api.data.Binding.of("Item",
                new com.tkisor.nekojs.bindings.static_access.ItemJS()), expose -> expose.versioned());
        // Same-name vanilla class combination binding (same pattern as Item/Block proxy delegation below): factory methods go to helper,
        // other members delegate to static members of the same-name vanilla/NeoForge classes (e.g., Ingredient.empty,
        // Capabilities.ItemHandler.BLOCK). Binding.of's valueType declares helper type for
        // preflight/probe reflection (dynamic members of the proxy cannot be reflected).
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
        // Math/Icons/Damage/Particles: KJS wrapper alignment (JavaMath directly bound to java.lang.Math)
        registry.register("JavaMath", Math.class);
        registry.register("KMath", KMathJS.class);
        registry.register("TextIcons", TextIcons.class);
        registry.register(Binding.of("DamageSource", new DelegatingBinding(new DamageSourceJS(),
                net.minecraft.world.damagesource.DamageSource.class,
                Set.of("of", "generic", "magic", "explosion", "drowning", "fall", "lava", "lightning",
                        "starvation", "outOfWorld")),
                DamageSourceJS.class));
        registry.register(Binding.of("ParticleOptions", new DelegatingBinding(new ParticleOptionsJS(),
                net.minecraft.core.particles.ParticleOptions.class,
                Set.of("of")),
                ParticleOptionsJS.class));
        // Quick query for NeoForge data maps (other maps via Registry.get(...).dataMapValue(...)).
        // Register an **instance**, not the class: class binding (StaticClass) does not expose
        // instance methods, so script-side `DataMap.furnaceFuel` is `typeof === 'undefined'`
        // and calling it reports `Unknown identifier: furnaceFuel`.
        // DataMapJS has no state, so one singleton instance serves the queries.
        registry.register("DataMap", new DataMapJS());
        // NativeEventsJS implements Binding so its close() (→ clear()) runs on STARTUP
        // reload, unregistering the previous round's native NeoForge event listeners
        // before the scripts re-register them. Avoids listeners accumulating on reload.
        if (registry.scriptType() == ScriptType.STARTUP) {
            NativeEventsJS nativeEvents = new NativeEventsJS();
            registry.register(nativeEvents);
        }
        registry.register(ScriptType.TEST, "Test", new TestJS());
//? if >=26 {
        registry.register("TriState", TriState.class);
//?}
        registry.register("Network", NetworkJS.class);
        // Server→client key-value push (client-side read-only view clientData registered by common built-in plugin)
        registry.register("ClientData", ClientDataSyncJS.class);
        // Typed asset generation (KubeJS style blockState/blockModel/itemModel/texture), written to
        // <gameDir>/nekojs/assets resource pack, in same directory as generateAssets event, takes effect on reload (lazy read)
//? if >=26 {
        registry.register("Assets", new AssetGeneratorJS());
//?}
// (global/shared is not registered here: ScriptEnvironmentFactory installs root-owned
// views per generation—a private store per ScriptType plus an explicit shared entry,
// instead of one process-level shared Map)
        registry.register("ItemStack", ItemStack.class);
        registry.register("Items", Items.class);
        // Global Item is ProxyObject proxy delegation (of/empty/id/idOf via ItemJS, others delegate to MC Item class).
        // Proxy dynamic members cannot be reflected in Java, preflight would falsely report "has no member 'of'",
        // so Binding.of(name, value, valueType) is used to explicitly declare valueType=ItemJS.
        registry.register(Binding.of("Item", new DelegatingBinding(itemHelper, Item.class, Set.of("id", "idOf", "of", "empty")), ItemJS.class));
        // Global Block also uses proxy delegation (id/idOf via BlockJS, others delegate to MC Block class).
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
            var i18n = Binding.of("I18n", new com.tkisor.nekojs.bindings.static_access.I18nJS(),
                    com.tkisor.nekojs.api.facade.I18nFacade.class);
            registry.register(i18n);
            registry.register(i18n, expose -> expose.stable());
//? if neoforge && >=26 {
            registry.register("ClientUI", new ClientUiJS());
//?}
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
//? if >=26 {
        registry.register(new IdentifierAdapter());
//?} else {
        /*registry.register(new ResourceLocationAdapter());
*///?}
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
        // alias: ItemLike arguments reuse Item adapter input shapes (id string etc.), converted to Item then asItem-adapted
        registry.registerAlias(ItemLike.class, Item.class, Item::asItem);
        registry.register(new MobEffectAdapter());
        registry.register(new PotionAdapter());
        registry.register(new SoundEventAdapter());
        registry.register(new ParticleTypeAdapter());
        registry.register(new BlockEntityTypeAdapter());
        registry.register(new CreativeModeTabAdapter());
        // Codec fallback adapter example (precedence=LOWEST): arbitrary JS value -> JsonElement -> codec.parse(JsonOps)
        TypeAdapterDsl.registerCodec(registry, Fireworks.class, Fireworks.CODEC);
        // Automatic registry adapter fallback: for vanilla registry types without hand-written adapters (Fluid, Attribute,
        // VillagerProfession etc.) dynamically supplement SimpleRegistryBasedAdapter, string IDs pass through.
        // Registries (ResourceKey holder) and BuiltInRegistries (Registry holder) cover the same batch of
        // vanilla types, de-duplicated set guarantees only one registration; mod registry contributes additional holder classes via plugins
        RegistryAutoAdapterScanner.installInto(registry, List.of(BuiltInRegistries.class, Registries.class));
    }

    @Override
    public void registerRecipeNamespaces(RecipeNamespaceRegister registry) {
//        registry.registerSchema();
        // RecipeEventJS is a split pair (26.x version in version tree + 1.21.1 version in versions/1.21.1), two eras of FQCN
        // are identical—unified via FQN, no need to switch import/simple names by era.
        registry.register(new RecipeNamespaceEntry("minecraft", e -> new MinecraftRecipeHandler((com.tkisor.nekojs.wrapper.event.server.RecipeEventJS) e), MinecraftRecipeHandler.class));
    }

    @Override
    public void registerTypeDocs(TypeDocsRegister registry) {
        registry.register(TypeDocCatalogEntry.binding("Item", "NekoItemHelper", "Script-friendly ItemStack factory and helpers (KubeJS-style: Item.of(id)); delegates of/empty to the helper, rest to MC Item.", List.of("Item.of('minecraft:stone')", "Item.of('minecraft:stone', 4)", "Item.empty()")));
        registry.register(TypeDocCatalogEntry.binding("Ingredient", "NekoIngredientHelper", "Script-friendly Ingredient and IngredientJS helper.", List.of("Ingredient.of('minecraft:stone')", "Ingredient.tag('minecraft:planks')")));
        registry.register(TypeDocCatalogEntry.binding("Fluid", "NekoFluidHelper", "Script-friendly FluidStack helper.", List.of("Fluid.of('minecraft:water', FluidAmounts.BUCKET)", "Fluid.of({ fluid: 'minecraft:water', amount: 250 })")));
        registry.register(TypeDocCatalogEntry.binding("FluidIngredient", "NekoFluidIngredientHelper", "Script-friendly FluidIngredient and SizedFluidIngredient helper.", List.of("FluidIngredient.of('minecraft:water')", "FluidIngredient.sized('minecraft:water', 250)")));
//? if >=26 {
        registry.register(TypeDocCatalogEntry.binding("Assets", "AssetGeneratorJS", "Typed asset generators writing into NekoJS's assets pack (blockState/blockModel/itemModel/texture); takes effect on the next resource reload.", List.of(
                "Assets.blockState('mymod:my_block', 'mymod:block/my_block')",
                "Assets.blockModel('mymod:my_block', { parent: 'minecraft:block/cube_all', textures: { all: 'my_block' } })",
                "Assets.itemModel('mymod:my_item', { parent: 'minecraft:item/generated', textures: { layer0: 'my_item' } })",
                "Assets.texture('mymod:item/my_item')")));
//?}
        registry.register(TypeDocCatalogEntry.binding("ServerEvents", null, "Server-side event group, including recipe editing.", List.of("ServerEvents.recipes(event => { })", "ServerEvents.afterRecipes(event => { })")));
        registry.register(TypeDocCatalogEntry.binding("ProbeEvents", null,
                "Probe generation customization events (probe.*). Listeners go in server_scripts; they run when /nekojs probe is invoked.",
                List.of(
                        "ProbeEvents.modifyType(event => { event.forClass('net.minecraft.world.entity.player.Player').renameMethod('getX', 'getCustom'); })",
                        "ProbeEvents.assignType(event => event.assign('net.minecraft.world.item.ItemStack', 'string'))",
                        "ProbeEvents.addGlobal(event => event.add('MyFlag', 'boolean'))")));
        registry.register(TypeDocCatalogEntry.binding(ScriptType.TEST, "Test", "NekoTestHelper", "Test-script assertion and smoke test helper.", List.of("Test.section('recipes').assertTrue(true, 'ready').summary()")));
        registry.register(TypeDocCatalogEntry.binding(ScriptType.STARTUP, "NativeEvents", null, "Startup-side native NeoForge event bridge.", List.of("NativeEvents.onEvent('event.class.Name', event => { })")));
        registry.register(TypeDocCatalogEntry.binding(
                ScriptType.STARTUP,
                "ScriptEvents",
                null,
                "Startup-side custom server/client event declaration event group.",
                List.of("ScriptEvents.server(event => event.register('MyEvents', 'bossKilled'))")));
        registry.register(TypeDocCatalogEntry.binding(ScriptType.STARTUP, "RegistryEvents", null, "Startup-side registry builders, including scripted entity types.", List.of("RegistryEvents.register(event => event.entityType('mymod:cat', b => { }))")));
        registry.register(TypeDocCatalogEntry.binding(ScriptType.STARTUP, "GoalEvents", null, "Startup-side goal registration for existing or scripted entity types.", List.of("GoalEvents.register(event => { })")));

        NekoCommonManualDeclarations.register(registry, Map.ofEntries(
                Map.entry("JsonObject", host(com.google.gson.JsonObject.class)),
                Map.entry("JsonArray", host(com.google.gson.JsonArray.class)),
                Map.entry("SizedIngredientJS", host(com.tkisor.nekojs.wrapper.item.SizedIngredientJS.class)),
                Map.entry("ItemStack", host(ItemStack.class)),
                Map.entry("FluidStack", host(FluidStack.class)),
                Map.entry("FluidIngredientJS", host(com.tkisor.nekojs.wrapper.fluid.FluidIngredientJS.class)),
                Map.entry("SizedFluidIngredient", host(net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient.class)),
                Map.entry("IngredientInput", input(net.minecraft.world.item.crafting.Ingredient.class)),
                Map.entry("Ingredient", host(net.minecraft.world.item.crafting.Ingredient.class)),
                Map.entry("CompoundTag", host(CompoundTag.class)),
                Map.entry("EntityType", host(EntityType.class))));
    }

}
//?}
