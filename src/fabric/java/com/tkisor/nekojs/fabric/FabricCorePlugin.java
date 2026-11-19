package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegistry;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.api.stage.StageEvents;
import com.tkisor.nekojs.api.stage.StagePlayerRef;
import com.tkisor.nekojs.api.stage.StagesFacade;
import com.tkisor.nekojs.bindings.event.BlockEvents;
import com.tkisor.nekojs.core.plugin.AdaptersPoint;
import com.tkisor.nekojs.core.plugin.EventsPoint;
import com.tkisor.nekojs.core.stage.NekoIdAdapter;
import com.tkisor.nekojs.core.stage.StageNotifyOptionsAdapter;
import com.tkisor.nekojs.core.stage.StagePlayerRefAdapter;
import com.tkisor.nekojs.core.stage.StagesBinding;
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

import java.util.List;

/**
 * Fabric-side core plugin: registers the Fabric bridge event groups, core bindings, and
 * platform-independent type adapters.
 *
 * <p>Compared with {@code NekoJSCorePlugin} in the shared tree, each Fabric event group is
 * registered here and merged by group name in {@link EventGroupRegistry} while keeping neutral
 * payloads across loaders.
 *
 * <p>Adapters map dispatch string keys and script inputs onto registry and stage types; without
 * the matching adapter, dispatch or conversion reports an unsupported target type.
 */
public final class FabricCorePlugin implements NekoJSPlugin, EventsPoint.Contributor,
        com.tkisor.nekojs.core.plugin.ClientEventsPoint.Contributor,
        com.tkisor.nekojs.core.plugin.BindingsPoint.Contributor, AdaptersPoint.Contributor {

    static {
        StagePlayerRefAdapter.registerHostResolver(host -> {
            if (host instanceof net.minecraft.world.entity.player.Player player) {
                if (player.getClass().getName().contains("FakePlayer")) {
                    return StagePlayerRef.fake(player.getScoreboardName());
                }
                boolean client = player.level() != null && player.level().isClientSide();
                return client
                        ? StagePlayerRef.client(player.getStringUUID(), player.getScoreboardName())
                        : StagePlayerRef.of(player.getStringUUID(), player.getScoreboardName());
            }
            return null;
        });
    }

    @Override
    public void registerBinding(com.tkisor.nekojs.api.data.BindingRegistry registry) {
        registry.register(com.tkisor.nekojs.api.data.Binding.of("Native",
                new com.tkisor.nekojs.bindings.static_access.NativeBridgeJS()), expose -> expose.stable());
        registry.register(com.tkisor.nekojs.api.data.Binding.of("Item",
                new com.tkisor.nekojs.bindings.static_access.ItemJS()), expose -> expose.versioned());
        var stages = com.tkisor.nekojs.api.data.Binding.of("Stages",
                new StagesBinding(registry.scriptType()),
                StagesFacade.class);
        registry.register(stages);
        registry.register(stages, expose -> expose.stable());
        // Server-to-client key-value push (the read-only clientData side is registered by the common
        // built-in plugin)
        registry.register("Capabilities", new com.tkisor.nekojs.bindings.static_access.CapabilitiesJS());
        registry.register("ClientData", com.tkisor.nekojs.wrapper.clientdata.ClientDataSyncJS.class);
        // Recipe side: the Ingredient factory, the same-named vanilla class with its static members
        // delegated, and the RecipeSchema viewer binding
        registry.register(com.tkisor.nekojs.api.data.Binding.of("Ingredient",
                new com.tkisor.nekojs.js.DelegatingBinding(new com.tkisor.nekojs.bindings.static_access.IngredientFactory(),
                        net.minecraft.world.item.crafting.Ingredient.class,
                        java.util.Set.of("of", "item", "tag", "any", "all", "not")),
                com.tkisor.nekojs.bindings.static_access.IngredientFactory.class));
        registry.register("RecipeSchema", new com.tkisor.nekojs.bindings.RecipeSchemaBinding());
        // Script-defined network channel (Network.sendToServer/sendToPlayer/sendToAll; the payload types
        // and the receivers are registered in FabricPlayNetwork)
        registry.register("Network", com.tkisor.nekojs.wrapper.network.NetworkJS.class);
        // ---- Basic static bindings: plain vanilla or neutral helpers ----
        // Engine-side helpers (common/static_access)
        registry.register("Color", new com.tkisor.nekojs.bindings.static_access.ColorJS());
        registry.register("UUID", new com.tkisor.nekojs.bindings.static_access.UUIDJS());
        registry.register("StringUtils", new com.tkisor.nekojs.bindings.static_access.StringUtilsJS());
        registry.register("Time", new com.tkisor.nekojs.bindings.static_access.TimeJS());
        registry.register("Utils", new com.tkisor.nekojs.bindings.static_access.UtilsJS());
        // Math, icons, damage and particles
        registry.register("JavaMath", Math.class);
        registry.register("KMath", com.tkisor.nekojs.bindings.static_access.KMathJS.class);
        registry.register("TextIcons", com.tkisor.nekojs.bindings.static_access.TextIcons.class);
        registry.register(com.tkisor.nekojs.api.data.Binding.of("DamageSource",
                new com.tkisor.nekojs.js.DelegatingBinding(
                        new com.tkisor.nekojs.bindings.static_access.DamageSourceJS(),
                        net.minecraft.world.damagesource.DamageSource.class,
                        java.util.Set.of("of", "generic", "magic", "explosion", "drowning", "fall", "lava",
                                "lightning", "starvation", "outOfWorld")),
                com.tkisor.nekojs.bindings.static_access.DamageSourceJS.class));
        registry.register(com.tkisor.nekojs.api.data.Binding.of("ParticleOptions",
                new com.tkisor.nekojs.js.DelegatingBinding(
                        new com.tkisor.nekojs.bindings.static_access.ParticleOptionsJS(),
                        net.minecraft.core.particles.ParticleOptions.class,
                        java.util.Set.of("of")),
                com.tkisor.nekojs.bindings.static_access.ParticleOptionsJS.class));
        registry.register(com.tkisor.nekojs.api.ScriptType.TEST, "Test",
                new com.tkisor.nekojs.bindings.static_access.TestJS());
        // global/shared are not registered here: ScriptEnvironmentFactory installs a root-owned view per
        // generation (a private store per ScriptType plus an explicit shared entry)
        // Vanilla types and constant classes
        registry.register("ItemStack", net.minecraft.world.item.ItemStack.class);
        registry.register("Items", net.minecraft.world.item.Items.class);
        registry.register(com.tkisor.nekojs.api.data.Binding.of("Item",
                new com.tkisor.nekojs.js.DelegatingBinding(new com.tkisor.nekojs.bindings.static_access.ItemJS(),
                        net.minecraft.world.item.Item.class,
                        java.util.Set.of("id", "idOf", "of", "empty")),
                com.tkisor.nekojs.bindings.static_access.ItemJS.class));
        registry.register(com.tkisor.nekojs.api.data.Binding.of("Block",
                new com.tkisor.nekojs.js.DelegatingBinding(new com.tkisor.nekojs.bindings.static_access.BlockJS(),
                        net.minecraft.world.level.block.Block.class,
                        java.util.Set.of("id", "idOf")),
                com.tkisor.nekojs.bindings.static_access.BlockJS.class));
        registry.register("Blocks", net.minecraft.world.level.block.Blocks.class);
        registry.register("BlockPos", net.minecraft.core.BlockPos.class);
        registry.register("Direction", net.minecraft.core.Direction.class);
        registry.register("Vec3", net.minecraft.world.phys.Vec3.class);
        registry.register("AABB", net.minecraft.world.phys.AABB.class);
        registry.register("MutableComponent", net.minecraft.network.chat.MutableComponent.class);
        registry.register("Component", net.minecraft.network.chat.Component.class);
        registry.register("DyeColor", net.minecraft.world.item.DyeColor.class);
        registry.register("SoundEvents", net.minecraft.sounds.SoundEvents.class);
        registry.register("ParticleTypes", net.minecraft.core.particles.ParticleTypes.class);
        registry.register("EntityType", net.minecraft.world.entity.EntityType.class);
        registry.register("CompoundTag", net.minecraft.nbt.CompoundTag.class);
        registry.register("Identifier", net.minecraft.resources.Identifier.class);
        registry.register("MobEffects", net.minecraft.world.effect.MobEffects.class);
        registry.register("MobEffectInstance", net.minecraft.world.effect.MobEffectInstance.class);
        registry.register("DamageTypes", net.minecraft.world.damagesource.DamageTypes.class);
        registry.register("TriState", net.minecraft.util.TriState.class);
        // Client class bindings (not registered on a dedicated server)
        if (registry.scriptType() == com.tkisor.nekojs.api.ScriptType.CLIENT) {
            var i18n = com.tkisor.nekojs.api.data.Binding.of("I18n", new com.tkisor.nekojs.bindings.static_access.I18nJS(),
                    com.tkisor.nekojs.api.facade.I18nFacade.class);
            registry.register(i18n);
            registry.register(i18n, expose -> expose.stable());
            registry.register("Minecraft", net.minecraft.client.Minecraft.class);
            registry.register("Screen", net.minecraft.client.gui.screens.Screen.class);
            registry.register("Window", com.mojang.blaze3d.platform.Window.class);
            registry.register("KeyMapping", net.minecraft.client.KeyMapping.class);
            registry.register("InputConstants", com.mojang.blaze3d.platform.InputConstants.class);
        }
    }

    @Override
    public void registerRecipeNamespaces(com.tkisor.nekojs.core.plugin.RecipeNamespaceRegister registry) {
        // minecraft namespace recipe types (shaped/shapeless/smelting and related handlers)
        registry.register(new com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry("minecraft",
                e -> new com.tkisor.nekojs.bindings.recipe.MinecraftRecipeHandler(
                        (com.tkisor.nekojs.wrapper.event.server.RecipeEventJS) e),
                com.tkisor.nekojs.bindings.recipe.MinecraftRecipeHandler.class));
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        // Ensure Fabric block and level v2 event bindings initialize their EventHandler fields on
        // BlockEvents.GROUP and LevelEvents.GROUP even when registerEvents runs standalone in tests or catalogs.
        com.tkisor.nekojs.fabric.event.FabricBlockEventBindings.bootstrap();
        com.tkisor.nekojs.fabric.event.FabricLevelEventBindingsV2.bootstrap();
        // broken / rightClicked / leftClicked / block v2: FabricBlockEventBindings hooks PlayerBlockBreakEvents.BEFORE and bootstraps v2
        registry.register(BlockEvents.GROUP);
        // Declaration side of custom script events (ScriptEvents.server/client from STARTUP, platform
        // independent)
        registry.register(com.tkisor.nekojs.api.event.ScriptEvents.GROUP);
        // Built-in portable StageEvents group (server customize/gates/added/removed/expired and clientAdded/clientRemoved)
        registry.register(StageEvents.GROUP);
        // Server lifecycle, tick, player join/leave and chat: the neutral payload subset
        registry.register(FabricServerEventBindings.SERVER_EVENTS);
        registry.register(FabricServerEventBindings.PLAYER_EVENTS);
        // Recipe scripts (recipes/afterRecipes): the bus is declared on the node's twin ServerEvents and
        // fired by the RecipeManagerMixin twin; merging puts the same-named "ServerEvents" group in the
        // same group as the lifecycle bus above
        registry.register(com.tkisor.nekojs.bindings.event.ServerEvents.GROUP);
        // AI goal registration (STARTUP): the consumer FabricEntityEventBindings.postJoinLevel is running,
        // and NekoJSFabricMod.initializeScripts calls postRegister after the STARTUP scripts load
        registry.register(com.tkisor.nekojs.bindings.event.CapabilityEvents.GROUP);
        registry.register(com.tkisor.nekojs.bindings.event.GoalEvents.GROUP);
        // Entity joinLevel / death / damagePre / damagePost (dispatched by entity type)
        registry.register(FabricEntityEventBindings.ENTITY_EVENTS);
        // Item rightClicked (dispatched by item id)
        registry.register(FabricItemEventBindings.ITEM_EVENTS);
        // Script-defined network channel: the server/client buses dispatch by channel (the receivers live in
        // FabricPlayNetwork, the send side is NetworkJS to PlayPacketDispatchers)
        registry.register(com.tkisor.nekojs.bindings.event.NetworkEvents.GROUP);
        // Dimension loaded/unloaded/tickPre/tickPost and v2 save/explosion events (FabricLevelEventBindings + V2, neutral payloads)
        registry.register(com.tkisor.nekojs.bindings.event.LevelEvents.GROUP);
        // Command registration (FabricCommandEventBindings: scripts register commands on event.dispatcher)
        registry.register(com.tkisor.nekojs.bindings.event.CommandEvents.GROUP);
        // Item tooltip (client; FabricClientEventBindings hooks ItemTooltipCallback; the group name
        // "ItemEvents" merges with the rightClicked group from FabricItemEventBindings)
        registry.register(com.tkisor.nekojs.bindings.event.ItemEvents.GROUP);
        // Mixin-side player event subset (container open/close, crafting, smelting, break, advancement,
        // entity interaction, dimension change; neutral payloads; merges with the same-named
        // FabricServerEventBindings.PLAYER_EVENTS)
        registry.register(com.tkisor.nekojs.bindings.event.PlayerEvents.GROUP);
        // Mixin-side entity event subset (drops/finalizeSpawn/tickPre/tickPost/leaveLevel; merges with the
        // same-named FabricEntityEventBindings.ENTITY_EVENTS)
        registry.register(com.tkisor.nekojs.bindings.event.EntityEvents.GROUP);
    }

    @Override
    public void registerClientEvents(EventGroupRegistry registry) {
        // Client tick event (CLIENT scripts are loaded by NekoJSFabricClient on CLIENT_STARTED)
        registry.register(com.tkisor.nekojs.fabric.event.FabricClientEventBindings.CLIENT_EVENTS);
        // Script key bindings (register/pressed/released/tick; twin KeyBindEvents, polled by
        // NekoJSFabricClient)
        registry.register(com.tkisor.nekojs.bindings.event.client.KeyBindEvents.GROUP);
    }

    @Override
    public void registerAdapters(JSTypeAdapterRegistry registry) {
        registry.register(new IdentifierAdapter());
        registry.register(new NekoIdAdapter());
        registry.register(new StagePlayerRefAdapter());
        registry.register(new StageNotifyOptionsAdapter());
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
        // alias: ItemLike parameters reuse the Item adapter input shape (id strings and so on); the value is
        // converted to an Item first and then adapted through asItem
        registry.registerAlias(net.minecraft.world.level.ItemLike.class,
                net.minecraft.world.item.Item.class, net.minecraft.world.item.Item::asItem);
        registry.register(new MobEffectAdapter());
        registry.register(new PotionAdapter());
        registry.register(new SoundEventAdapter());
        registry.register(new ParticleTypeAdapter());
        registry.register(new BlockEntityTypeAdapter());
        registry.register(new CreativeModeTabAdapter());
        // Recipe-side: ItemStack/Ingredient/Filter/Recipe JSON values
        registry.register(new com.tkisor.nekojs.js.type_adapter.ItemStackAdapter());
        registry.register(new com.tkisor.nekojs.js.type_adapter.IngredientAdapter());
        registry.register(new com.tkisor.nekojs.js.type_adapter.RecipeFilterAdapter());
        registry.register(new com.tkisor.nekojs.js.type_adapter.RecipeJsonValueAdapter());
        // Automatic registry adapter fallback: dynamically adds string ID conversion for vanilla registry types without manual adapters
        com.tkisor.nekojs.js.type_adapter.RegistryAutoAdapterScanner.installInto(
                registry, List.of(net.minecraft.core.registries.BuiltInRegistries.class));
    }
}
