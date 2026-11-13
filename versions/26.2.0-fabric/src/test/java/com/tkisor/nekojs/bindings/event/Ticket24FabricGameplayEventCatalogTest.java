// Fabric node-local catalog snapshot. The `//? if fabric` guard is a lazy comment for the active
// node (26.1.2 NeoForge, which compiles the shared tree from disk), so it would compile and run on
// the wrong platform; the test therefore lives in the node-local test tree, following the
// FabricNetworkRegistrationOnceTest precedent. Both fabric nodes carry one copy of the same test.
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.fabric.FabricCorePlugin;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import com.tkisor.nekojs.wrapper.event.block.BlockBrokenEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockEntityTickEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockFarmlandTrampleEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockFluidPlacedEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockLeftClickEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockNeighborNotifyEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockPlacedEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockPortalSpawnEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockRandomTickEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockRightClickEventJS;
import com.tkisor.nekojs.wrapper.event.entity.EntityJoinLevelEventJS;
import com.tkisor.nekojs.wrapper.event.entity.EntityLeaveLevelEventJS;
import com.tkisor.nekojs.wrapper.event.entity.EntityTickEventJS;
import com.tkisor.nekojs.wrapper.event.entity.LivingDamageEventJS;
import com.tkisor.nekojs.wrapper.event.entity.LivingDeathEventJS;
import com.tkisor.nekojs.wrapper.event.entity.LivingDropsEventJS;
import com.tkisor.nekojs.wrapper.event.entity.MobFinalizeSpawnEventJS;
import com.tkisor.nekojs.wrapper.event.entity.GoalRegisterEventJS;
import com.tkisor.nekojs.wrapper.event.level.LevelEventJS;
import com.tkisor.nekojs.wrapper.event.level.LevelExplosionEventJS;
import com.tkisor.nekojs.wrapper.event.level.LevelSavedEventJS;
import com.tkisor.nekojs.wrapper.event.player.InventoryChangedEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerAdvancementEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerChangedDimensionEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerContainerEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerCraftedEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerDestroyItemEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerEntityInteractEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerLifecycleEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerTickEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerCloneEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerRespawnEventJS;
import com.tkisor.nekojs.wrapper.event.player.ServerChatEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemDroppedEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemEntityPickupEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemRightClickEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemTooltipEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemUseFinishedEventJS;
import com.tkisor.nekojs.wrapper.event.server.BlockModificationEventJS;
import com.tkisor.nekojs.wrapper.event.server.CommandRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.server.ItemModificationEventJS;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Catalog snapshot fixture for the fabric side. It follows production initialization order: first
 * it triggers class initialization of {@code FabricBlockEventBindings},
 * {@code FabricBlockEventBindingsV2} and {@code FabricLevelEventBindingsV2}, whose bus fields add
 * members with {@code GROUP.add} to the shared group before the plugin bootstrap freezes it; then
 * it runs the same-name combination from {@link FabricCorePlugin#registerEvents} and derives the
 * real catalog. That freezes the public member shape of the eight event families on the fabric
 * node.
 *
 * <p>Input difference from the recorded baseline (intentional, not a contradiction):
 * {@code EventSurfaceDomainGateTest} only drives the {@code registerEvents} hook, so it cannot see
 * the members appended by the Adapter class initialization above. Its baseline records fabric
 * {@code BlockEvents} as {@code broken,modification} and {@code LevelEvents} as 4 members, while
 * the real runtime set also has the 10+5 members from the v1/v2 Adapters. This test freezes the
 * real runtime set; regenerating the baseline is up to its owners, and this test does not change
 * the read-only baseline file.
 *
 * <p>Capability difference, recorded explicitly: CapabilityEvents is not registered on fabric.
 * {@code RegisterCapabilitiesEvent} is NeoForge-only and the baseline marks it not-verified, so it
 * is absent on purpose rather than a silent no-op. Cancellability comes from the explicit
 * {@code EventBusJS.of(type, true, ...)} in the twin/Adapter sources.
 */
class Ticket24FabricGameplayEventCatalogTest {

    /** Expected shape of one public member: a {@code null} dispatchKey means a non-directed dispatch bus. */
    private record Expected(String name, Class<?> payload, ScriptType side, Class<?> dispatchKey, boolean cancellable) {}

    private static Map<String, EventGroup> registeredGroups() {
        // Production order: the mod entry's register() calls first run the Adapter class initialization,
        // whose members call GROUP.add into the shared group before it is frozen. Only the class
        // initialization runs here; no fabric-api callbacks are attached, because the catalog does not
        // depend on callback mounting. For the callback source trace see Ticket24GameplayEventPhaseTraceTest
        // in the shared tree.
        for (String adapter : List.of(
                "com.tkisor.nekojs.fabric.event.FabricBlockEventBindings",
                "com.tkisor.nekojs.fabric.event.FabricBlockEventBindingsV2",
                "com.tkisor.nekojs.fabric.event.FabricLevelEventBindingsV2")) {
            try {
                Class.forName(adapter, true, Ticket24FabricGameplayEventCatalogTest.class.getClassLoader());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("cannot initialize fabric adapter " + adapter, e);
            }
        }
        EventGroupRegistry registry = new EventGroupRegistry.Impl().scopedTo("nekojs");
        new FabricCorePlugin().registerEvents(registry);
        new FabricCorePlugin().registerClientEvents(registry);
        return registry.view();
    }

    @BeforeAll
    static void initPlatformStub() {
        try {
            com.tkisor.nekojs.platform.Platform.init(new com.tkisor.nekojs.platform.IPlatform() {
                @Override public boolean isClient() { return false; }
                @Override public boolean isDevelopment() { return true; }
                @Override public String getMcVersion() { return "test"; }
                @Override public java.nio.file.Path getGameDir() {
                    return com.tkisor.nekojs.TestGameDirs.unique("nekojs-ticket24-fabric-catalog");
                }
                @Override public Map<String, com.tkisor.nekojs.platform.IModInfo> getMods() { return Map.of(); }
                @Override public com.tkisor.nekojs.platform.IModInfo getInfo(String modID) { return null; }
                @Override public String getLoaderId() { return "test"; }
                @Override public String getLoaderVersion() { return "0"; }
            });
        } catch (IllegalStateException alreadyInitialized) {
            // same-JVM reuse of an already initialized platform stub is fine
        }
    }

    @Test
    void gameplayFamiliesAreCompleteInTheFabricCatalogWithoutSilentDrift() {
        Map<String, EventGroup> groups = registeredGroups();
        Map<String, Map<String, Expected>> expected = expectedFamilies();

        List<String> missing = expected.keySet().stream().filter(f -> !groups.containsKey(f)).toList();
        assertTrue(missing.isEmpty(), "gameplay event families missing from the fabric registration entry: " + missing);
        assertTrue(groups.containsKey("CapabilityEvents"),
                "Fabric native lookup capability registration must be present");

        List<EventCatalogEntry> entries = NekoScriptCatalog.events(new StubRuntime(groups));
        Map<String, Map<String, EventCatalogEntry>> catalog = entries.stream()
                .filter(e -> expected.containsKey(e.group()))
                .collect(Collectors.groupingBy(EventCatalogEntry::group,
                        java.util.TreeMap::new,
                        Collectors.toMap(EventCatalogEntry::name, e -> e, (a, b) -> a, java.util.TreeMap::new)));

        List<String> failures = new ArrayList<>();
        for (String family : expected.keySet()) {
            Map<String, Expected> wanted = expected.get(family);
            Map<String, EventCatalogEntry> actual = catalog.getOrDefault(family, Map.of());
            Set<String> wantedNames = wanted.keySet();
            Set<String> actualNames = actual.keySet();

            Set<String> lost = new LinkedHashSet<>(wantedNames);
            lost.removeAll(actualNames);
            for (String name : lost) {
                failures.add(family + "." + name + " lost from the fabric catalog (member removal = contract change)");
            }
            Set<String> unlisted = new LinkedHashSet<>(actualNames);
            unlisted.removeAll(wantedNames);
            for (String name : unlisted) {
                failures.add(family + "." + name + " not in the ticket-24 fabric snapshot"
                        + " (new member needs an intentional table update)");
            }

            for (String name : wantedNames) {
                if (!actualNames.contains(name)) continue;
                Expected want = wanted.get(name);
                EventCatalogEntry entry = actual.get(name);
                if (entry.eventType() != want.payload()) {
                    failures.add(family + "." + name + " payload drifted: expected "
                            + want.payload().getSimpleName() + " but catalog carries "
                            + (entry.eventType() == null ? "null(script-defined)" : entry.eventType().getSimpleName()));
                }
                if (!entry.scriptType().test(want.side())) {
                    failures.add(family + "." + name + " is not visible to " + want.side() + " scripts");
                }
                if (entry.cancellable() != want.cancellable()) {
                    failures.add(family + "." + name + " cancellability drifted: expected " + want.cancellable()
                            + " but catalog says " + entry.cancellable());
                }
                if (entry.dispatchable() != (want.dispatchKey() != null)) {
                    failures.add(family + "." + name + " dispatchability drifted: expected "
                            + (want.dispatchKey() != null) + " but catalog says " + entry.dispatchable());
                }
                if (want.dispatchKey() != null && entry.dispatchKeyType() != want.dispatchKey()) {
                    failures.add(family + "." + name + " dispatch key drifted: expected "
                            + want.dispatchKey().getSimpleName() + " but catalog carries "
                            + (entry.dispatchKeyType() == null ? "null" : entry.dispatchKeyType().getSimpleName()));
                }
            }
        }
        assertTrue(failures.isEmpty(),
                "ticket-24 fabric gameplay event catalog snapshot diff:\n  " + String.join("\n  ", failures));
    }

    private static Map<String, Map<String, Expected>> expectedFamilies() {
        Map<String, Map<String, Expected>> families = new LinkedHashMap<>();
        put(families, "BlockEvents",
                // broken explicitly uses a cancellable bus; Fabric BEFORE negates the cancellation result.
                new Expected("broken", BlockBrokenEventJS.class, ScriptType.SERVER, Block.class, true),
                new Expected("rightClicked", BlockRightClickEventJS.class, ScriptType.SERVER, Block.class, true),
                new Expected("leftClicked", BlockLeftClickEventJS.class, ScriptType.SERVER, Block.class, true),
                new Expected("portalSpawn", BlockPortalSpawnEventJS.class, ScriptType.SERVER, Block.class, true),
                new Expected("neighborNotify", BlockNeighborNotifyEventJS.class, ScriptType.SERVER, Block.class, true),
                new Expected("farmlandTrample", BlockFarmlandTrampleEventJS.class, ScriptType.SERVER, Block.class, true),
                new Expected("placed", BlockPlacedEventJS.class, ScriptType.SERVER, Block.class, true),
                new Expected("entityPlaced", BlockPlacedEventJS.class, ScriptType.SERVER, Block.class, true),
                new Expected("fluidPlaced", BlockFluidPlacedEventJS.class, ScriptType.SERVER, Block.class, true),
                new Expected("randomTick", BlockRandomTickEventJS.class, ScriptType.SERVER, Block.class, false),
                new Expected("blockEntityTick", BlockEntityTickEventJS.class, ScriptType.SERVER, BlockEntityType.class, false),
                new Expected("modification", BlockModificationEventJS.class, ScriptType.SERVER, null, false)
        );

        put(families, "ItemEvents",
                new Expected("rightClicked", ItemRightClickEventJS.class, ScriptType.SERVER, Item.class, true),
                new Expected("tooltip", ItemTooltipEventJS.class, ScriptType.CLIENT, Item.class, false),
                new Expected("canPickUp", ItemEntityPickupEventJS.class, ScriptType.SERVER, Item.class, true),
                new Expected("pickedUpPre", ItemEntityPickupEventJS.class, ScriptType.SERVER, Item.class, true),
                new Expected("pickedUp", ItemEntityPickupEventJS.class, ScriptType.SERVER, Item.class, false),
                new Expected("dropped", ItemDroppedEventJS.class, ScriptType.SERVER, Item.class, false),
                new Expected("entityInteracted",
                        com.tkisor.nekojs.wrapper.event.item.PlayerEntityInteractEventJS.class,
                        ScriptType.SERVER, Item.class, true),
                new Expected("foodEaten", ItemUseFinishedEventJS.class, ScriptType.SERVER, Item.class, false),
                new Expected("modification", ItemModificationEventJS.class, ScriptType.SERVER, null, false)
        );

        put(families, "LevelEvents",
                new Expected("loaded", LevelEventJS.class, ScriptType.SERVER, null, false),
                new Expected("unloaded", LevelEventJS.class, ScriptType.SERVER, null, false),
                new Expected("saved", LevelSavedEventJS.class, ScriptType.SERVER, null, false),
                new Expected("tickPre", LevelEventJS.class, ScriptType.SERVER, null, false),
                new Expected("tickPost", LevelEventJS.class, ScriptType.SERVER, null, false),
                new Expected("explosionStart", LevelExplosionEventJS.class, ScriptType.SERVER, null, true),
                new Expected("beforeExplosion", LevelExplosionEventJS.class, ScriptType.SERVER, null, true),
                new Expected("explosionDetonate", LevelExplosionEventJS.class, ScriptType.SERVER, null, false),
                new Expected("afterExplosion", LevelExplosionEventJS.class, ScriptType.SERVER, null, false)
        );

        put(families, "PlayerEvents",
                new Expected("loggedIn", PlayerLifecycleEventJS.class, ScriptType.SERVER, null, false),
                new Expected("loggedOut", PlayerLifecycleEventJS.class, ScriptType.SERVER, null, false),
                new Expected("chat", ServerChatEventJS.class, ScriptType.SERVER, null, false),
                new Expected("tickPre", PlayerTickEventJS.class, ScriptType.SERVER, null, false),
                new Expected("tickPost", PlayerTickEventJS.class, ScriptType.SERVER, null, false),
                new Expected("cloned", PlayerCloneEventJS.class, ScriptType.SERVER, null, false),
                new Expected("respawned", PlayerRespawnEventJS.class, ScriptType.SERVER, null, false),
                new Expected("containerOpened", PlayerContainerEventJS.class, ScriptType.SERVER, null, false),
                new Expected("inventoryOpened", PlayerContainerEventJS.class, ScriptType.SERVER, null, false),
                new Expected("containerClosed", PlayerContainerEventJS.class, ScriptType.SERVER, null, false),
                new Expected("inventoryClosed", PlayerContainerEventJS.class, ScriptType.SERVER, null, false),
                new Expected("crafted", PlayerCraftedEventJS.class, ScriptType.SERVER, Item.class, false),
                new Expected("smelted", PlayerCraftedEventJS.class, ScriptType.SERVER, Item.class, false),
                new Expected("destroyed", PlayerDestroyItemEventJS.class, ScriptType.SERVER, Item.class, false),
                new Expected("advancement", PlayerAdvancementEventJS.class, ScriptType.SERVER, null, false),
                new Expected("entityInteract", PlayerEntityInteractEventJS.class, ScriptType.SERVER, null, true),
                new Expected("changedDimension", PlayerChangedDimensionEventJS.class, ScriptType.SERVER, null, false),
                new Expected("inventoryChanged", InventoryChangedEventJS.class, ScriptType.SERVER, Item.class, false)
        );

        put(families, "CommandEvents",
                new Expected("register", CommandRegistryEventJS.class, ScriptType.SERVER, null, false)
        );

        put(families, "GoalEvents",
                new Expected("register", GoalRegisterEventJS.class, ScriptType.STARTUP, null, false)
        );

        put(families, "EntityEvents",
                new Expected("joinLevel", EntityJoinLevelEventJS.class, ScriptType.SERVER, EntityType.class, false),
                new Expected("death", LivingDeathEventJS.class, ScriptType.SERVER, EntityType.class, false),
                new Expected("damagePre", LivingDamageEventJS.class, ScriptType.SERVER, EntityType.class, true),
                new Expected("damagePost", LivingDamageEventJS.class, ScriptType.SERVER, EntityType.class, false),
                new Expected("drops", LivingDropsEventJS.class, ScriptType.SERVER, EntityType.class, true),
                new Expected("finalizeSpawn", MobFinalizeSpawnEventJS.class, ScriptType.SERVER, EntityType.class, false),
                new Expected("tickPre", EntityTickEventJS.class, ScriptType.SERVER, EntityType.class, false),
                new Expected("tickPost", EntityTickEventJS.class, ScriptType.SERVER, EntityType.class, false),
                new Expected("leaveLevel", EntityLeaveLevelEventJS.class, ScriptType.SERVER, EntityType.class, false)
        );
        return families;
    }

    private static void put(Map<String, Map<String, Expected>> families, String family, Expected... members) {
        Map<String, Expected> map = new LinkedHashMap<>();
        for (Expected member : members) {
            map.put(member.name(), member);
        }
        families.put(family, map);
    }

    private static final class StubRuntime implements IPluginRuntime {
        private final Map<String, EventGroup> eventGroups;

        StubRuntime(Map<String, EventGroup> eventGroups) {
            this.eventGroups = new LinkedHashMap<>(eventGroups);
        }

        @Override public Map<String, Binding> bindings(ScriptType type) { return Map.of(); }
        @Override public Map<String, EventGroup> eventGroups() { return eventGroups; }
        @Override public List<com.tkisor.nekojs.api.JSTypeAdapter<?>> adapters() { return List.of(); }
        @Override public List<com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry> typeDocs() { return List.of(); }
        @Override public List<com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry> manualDeclarations() { return List.of(); }
        @Override public List<com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry> registryBuilderSurfaces() { return List.of(); }
        @Override public Map<String, String> nodeModules() { return Map.of(); }
        @Override public Map<String, com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry> recipeNamespaces() { return Map.of(); }
        @Override public void beforeRecipeLoading(com.tkisor.nekojs.api.recipe.RecipeLifecycleContext context) {}
        @Override public void afterRecipes(com.tkisor.nekojs.api.recipe.RecipeLifecycleContext context) {}
        @Override public void fireInit() {}
        @Override public void fireInitStartup() {}
        @Override public void fireAfterInit() {}
        @Override public void fireBeforeScriptsLoaded(ScriptType type) {}
        @Override public void fireAfterScriptsLoaded(ScriptType type) {}
        @Override public com.tkisor.nekojs.api.surface.ApiRuntimeView apiRuntime(com.tkisor.nekojs.api.surface.EnvironmentKey environment) { return null; }
        @Override public Object managedApiImplementation(com.tkisor.nekojs.api.surface.ApiSymbolId globalId) { return null; }
    }
}
