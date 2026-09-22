// TODO(fabric): fabric 侧的 catalog 快照在 fabric 节点本地测试树
// （versions/<fabric-node>/src/test/.../Ticket24FabricGameplayEventCatalogTest.java）——
// `//? if fabric` 守卫对 active 节点是惰性注释，共享树放不下加载器专属表。
//? if neoforge {
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.core.NekoJSCorePlugin;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import com.tkisor.nekojs.event.level.BlockEntityTickEvent;
import com.tkisor.nekojs.event.level.RandomTickEvent;
import com.tkisor.nekojs.wrapper.event.block.BlockBrokenEventJS;
import com.tkisor.nekojs.wrapper.event.player.InventoryChangedEventJS;
import com.tkisor.nekojs.wrapper.event.registry.CapabilityRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.entity.GoalRegisterEventJS;
import com.tkisor.nekojs.wrapper.event.server.ItemModificationEventJS;
//? if >=26 {
import com.tkisor.nekojs.wrapper.event.server.BlockModificationEventJS;
//?}
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 24 catalog-snapshot fixture（NeoForge 侧）：Block/Item/Level/Player/Command/Capability/
 * Goal/Entity 八个事件族的公开成员经<b>真实注册入口 + 真实 catalog 派生</b>
 * （{@link NekoScriptCatalog#events}）冻结成契约快照——成员名、payload 类、side、
 * dispatch key、cancellable。新增/删除/改形任何公开成员都会让本表 diff（契约变更），
 * 而不是静默 drift；家族缺席（注册入口漏注册整个组）同样直接红。
 *
 * <p>与票 33 的 {@code EventSurfaceDomainGateTest}（跨节点 bus 名基线）互补：那边冻结
 * 「每节点注册了哪些成员名」，本测试在 catalog 维度补上 payload/side/dispatch/cancel 形状，
 * 并把 wrapper 公开 payload 类（{@code BlockBrokenEventJS} 等加载器中立载荷）纳入同一
 * managed contract 视图。fabric 侧同款快照在 fabric 节点本地测试树（见文件头注释）。
 *
 * <p>生产初始化次序复刻：先装 external cancellability predicate
 * （{@code NekoJSMod} 构造器里的 {@code NeoForgeRuntimeBootstrap.setup()} 同款），再
 * bootstrap 适配层、再注册组——次序反了会把可取消总线冻成不可取消。
 */
class Ticket24GameplayEventCatalogTest {

    /** 一条公开成员的期望形状：dispatchKey 为 {@code null} 表示非定向分发总线。 */
    private record Expected(String name, Class<?> payload, ScriptType side, Class<?> dispatchKey) {}

    private static final List<String> FAMILIES = List.of(
            "BlockEvents", "ItemEvents", "LevelEvents", "PlayerEvents",
            "CommandEvents", "CapabilityEvents", "GoalEvents", "EntityEvents");

    private static Map<String, EventGroup> registeredGroups() {
        // production init order: the cancellability predicate precedes any family class-init
        EventBusJS.setExternalCancellabilityPredicate(ICancellableEvent.class::isAssignableFrom);
        EventGroupRegistry registry = new EventGroupRegistry.Impl();
        new NekoJSCorePlugin().registerEvents(registry);
        new NekoJSCorePlugin().registerClientEvents(registry);
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
                    return com.tkisor.nekojs.TestGameDirs.unique("nekojs-ticket24-catalog");
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
    void gameplayFamiliesAreCompleteInTheCatalogWithoutSilentDrift() {
        Map<String, EventGroup> groups = registeredGroups();
        Map<String, Map<String, Expected>> expected = expectedFamilies();

        List<String> missing = expected.keySet().stream().filter(f -> !groups.containsKey(f)).toList();
        assertTrue(missing.isEmpty(), "gameplay event families missing from the registration entry: " + missing);

        Map<String, Map<String, EventCatalogEntry>> catalog = catalogByFamily(groups);

        List<String> failures = new ArrayList<>();
        for (String family : expected.keySet()) {
            Map<String, Expected> wanted = expected.get(family);
            Map<String, EventCatalogEntry> actual = catalog.getOrDefault(family, Map.of());
            Set<String> wantedNames = wanted.keySet();
            Set<String> actualNames = actual.keySet();

            Set<String> lost = new LinkedHashSet<>(wantedNames);
            lost.removeAll(actualNames);
            for (String name : lost) {
                failures.add(family + "." + name + " lost from the catalog (member removal = contract change)");
            }
            Set<String> unlisted = new LinkedHashSet<>(actualNames);
            unlisted.removeAll(wantedNames);
            for (String name : unlisted) {
                failures.add(family + "." + name + " not in the ticket-24 snapshot"
                        + " (new member needs an intentional table update)");
            }

            for (String name : wantedNames) {
                if (!actualNames.contains(name)) continue;
                Expected want = wanted.get(name);
                EventCatalogEntry entry = actual.get(name);
                boolean wantCancellable = ICancellableEvent.class.isAssignableFrom(want.payload());
                if (entry.eventType() != want.payload()) {
                    failures.add(family + "." + name + " payload drifted: expected "
                            + want.payload().getSimpleName() + " but catalog carries "
                            + (entry.eventType() == null ? "null(script-defined)" : entry.eventType().getSimpleName()));
                }
                if (!entry.scriptType().test(want.side())) {
                    failures.add(family + "." + name + " is not visible to " + want.side() + " scripts");
                }
                if (entry.cancellable() != wantCancellable) {
                    failures.add(family + "." + name + " cancellability drifted: expected " + wantCancellable
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
                assertFalse(entry.scriptDefined(),
                        family + "." + name + " is a native payload bus, not a script-defined event");
            }
        }
        assertTrue(failures.isEmpty(),
                "ticket-24 gameplay event catalog snapshot diff:\n  " + String.join("\n  ", failures));
    }

    /** 每条 bus 在 catalog 派生中恰出现一次（与票 14 的跨组唯一性互为旁证）。 */
    @Test
    void eachFamilyBusYieldsExactlyOneCatalogEntry() {
        Map<String, EventGroup> groups = registeredGroups();
        List<EventCatalogEntry> entries = NekoScriptCatalog.events(new StubRuntime(groups));
        Map<String, Long> counts = entries.stream()
                .filter(e -> FAMILIES.contains(e.group()))
                .collect(Collectors.groupingBy(e -> e.group() + "." + e.name(), TreeMap::new, Collectors.counting()));
        List<String> duplicated = counts.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(e -> e.getKey() + " x" + e.getValue())
                .toList();
        assertTrue(duplicated.isEmpty(), "duplicate catalog entries: " + duplicated);
    }

    // ---- catalog derivation ----

    private static Map<String, Map<String, EventCatalogEntry>> catalogByFamily(Map<String, EventGroup> groups) {
        List<EventCatalogEntry> entries = NekoScriptCatalog.events(new StubRuntime(groups));
        return entries.stream()
                .filter(e -> FAMILIES.contains(e.group()))
                .collect(Collectors.groupingBy(EventCatalogEntry::group,
                        TreeMap::new, Collectors.toMap(EventCatalogEntry::name, e -> e, (a, b) -> a, TreeMap::new)));
    }

    // ---- expected table ----

    private static Map<String, Map<String, Expected>> expectedFamilies() {
        Map<String, Map<String, Expected>> families = new LinkedHashMap<>();
        putNeoforgeFamilies(families);
        return families;
    }

    private static void put(Map<String, Map<String, Expected>> families, String family, Expected... members) {
        Map<String, Expected> map = new LinkedHashMap<>();
        for (Expected member : members) {
            map.put(member.name(), member);
        }
        families.put(family, map);
    }

    private static void putNeoforgeFamilies(Map<String, Map<String, Expected>> families) {
        // BlockEvents：中立声明（broken/modification）+ NeoForge 适配层成员，全部 SERVER。
        // known defect (recorded, not fixed here): broken is documented cancellable
        // (wiki 事件参考 + BlockBrokenEventJS @Doc) but its neutral payload does not
        // implement ICancellableEvent, so the predicate freezes the bus non-cancellable
        // on every loader — script cancellation of broken is a silent no-op. Fixing it is
        // a public-behavior change that needs a maintainer ruling (ticket 24 REPORT).
        put(families, "BlockEvents",
                new Expected("broken", BlockBrokenEventJS.class, ScriptType.SERVER, Block.class),
                new Expected("entityPlaced", BlockEvent.EntityPlaceEvent.class, ScriptType.SERVER, Block.class),
                new Expected("entityMultiPlaced", BlockEvent.EntityMultiPlaceEvent.class, ScriptType.SERVER, Block.class),
                new Expected("neighborNotify", BlockEvent.NeighborNotifyEvent.class, ScriptType.SERVER, Block.class),
                new Expected("fluidPlaced", BlockEvent.FluidPlaceBlockEvent.class, ScriptType.SERVER, Block.class),
                new Expected("farmlandTrample", BlockEvent.FarmlandTrampleEvent.class, ScriptType.SERVER, Block.class),
                new Expected("portalSpawn", BlockEvent.PortalSpawnEvent.class, ScriptType.SERVER, Block.class),
                new Expected("toolModification", BlockEvent.BlockToolModificationEvent.class, ScriptType.SERVER, Block.class),
                new Expected("rightClicked", PlayerInteractEvent.RightClickBlock.class, ScriptType.SERVER, Block.class),
                new Expected("placed", BlockEvent.EntityPlaceEvent.class, ScriptType.SERVER, Block.class),
                new Expected("leftClicked", PlayerInteractEvent.LeftClickBlock.class, ScriptType.SERVER, Block.class),
                new Expected("randomTick", RandomTickEvent.class, ScriptType.SERVER, Block.class),
                new Expected("blockEntityTick", BlockEntityTickEvent.class, ScriptType.SERVER, BlockEntityType.class)
//? if >=26 {
                , new Expected("modification", BlockModificationEventJS.class, ScriptType.SERVER, null)
//?}
        );

        put(families, "ItemEvents",
                new Expected("rightClicked", PlayerInteractEvent.RightClickItem.class, ScriptType.SERVER, Item.class),
                new Expected("modification", ItemModificationEventJS.class, ScriptType.SERVER, null),
                new Expected("tooltip", ItemTooltipEvent.class, ScriptType.CLIENT, Item.class),
                new Expected("canPickUp", ItemEntityPickupEvent.Pre.class, ScriptType.SERVER, Item.class),
                new Expected("pickedUpPre", ItemEntityPickupEvent.Pre.class, ScriptType.SERVER, Item.class),
                new Expected("pickedUp", ItemEntityPickupEvent.Post.class, ScriptType.SERVER, Item.class),
                new Expected("dropped", ItemTossEvent.class, ScriptType.SERVER, Item.class),
                new Expected("entityInteracted", PlayerInteractEvent.EntityInteract.class, ScriptType.SERVER, Item.class),
                new Expected("foodEaten", LivingEntityUseItemEvent.Finish.class, ScriptType.SERVER, Item.class)
        );

        put(families, "LevelEvents",
                new Expected("loaded", LevelEvent.Load.class, ScriptType.SERVER, null),
                new Expected("unloaded", LevelEvent.Unload.class, ScriptType.SERVER, null),
                new Expected("saved", LevelEvent.Save.class, ScriptType.SERVER, null),
                new Expected("tickPre", LevelTickEvent.Pre.class, ScriptType.SERVER, null),
                new Expected("tickPost", LevelTickEvent.Post.class, ScriptType.SERVER, null),
                new Expected("tick", LevelTickEvent.Post.class, ScriptType.SERVER, null),
                new Expected("explosionStart", ExplosionEvent.Start.class, ScriptType.SERVER, null),
                new Expected("beforeExplosion", ExplosionEvent.Start.class, ScriptType.SERVER, null),
                new Expected("explosionDetonate", ExplosionEvent.Detonate.class, ScriptType.SERVER, null),
                new Expected("afterExplosion", ExplosionEvent.Detonate.class, ScriptType.SERVER, null)
        );

        put(families, "PlayerEvents",
                new Expected("loggedIn", PlayerEvent.PlayerLoggedInEvent.class, ScriptType.SERVER, null),
                new Expected("loggedOut", PlayerEvent.PlayerLoggedOutEvent.class, ScriptType.SERVER, null),
                new Expected("chat", ServerChatEvent.class, ScriptType.SERVER, null),
                new Expected("tickPost", PlayerTickEvent.Post.class, ScriptType.SERVER, null),
                new Expected("tickPre", PlayerTickEvent.Pre.class, ScriptType.SERVER, null),
                new Expected("cloned", PlayerEvent.Clone.class, ScriptType.SERVER, null),
                new Expected("respawned", PlayerEvent.PlayerRespawnEvent.class, ScriptType.SERVER, null),
                new Expected("changedDimension", PlayerEvent.PlayerChangedDimensionEvent.class, ScriptType.SERVER, null),
                new Expected("advancement", AdvancementEvent.AdvancementEarnEvent.class, ScriptType.SERVER, null),
                new Expected("containerOpened", PlayerContainerEvent.Open.class, ScriptType.SERVER, null),
                new Expected("inventoryOpened", PlayerContainerEvent.Open.class, ScriptType.SERVER, null),
                new Expected("containerClosed", PlayerContainerEvent.Close.class, ScriptType.SERVER, null),
                new Expected("inventoryClosed", PlayerContainerEvent.Close.class, ScriptType.SERVER, null),
                new Expected("entityInteract", PlayerInteractEvent.EntityInteract.class, ScriptType.SERVER, null),
                new Expected("crafted", PlayerEvent.ItemCraftedEvent.class, ScriptType.SERVER, Item.class),
                new Expected("smelted", PlayerEvent.ItemSmeltedEvent.class, ScriptType.SERVER, Item.class),
                new Expected("destroyed", PlayerDestroyItemEvent.class, ScriptType.SERVER, Item.class),
                new Expected("inventoryChanged", InventoryChangedEventJS.class, ScriptType.SERVER, Item.class)
        );

        put(families, "CommandEvents",
                new Expected("register", RegisterCommandsEvent.class, ScriptType.SERVER, null),
                new Expected("command", CommandEvent.class, ScriptType.SERVER, null)
        );

        put(families, "CapabilityEvents",
                new Expected("register", CapabilityRegistryEventJS.class, ScriptType.STARTUP, null)
        );

        put(families, "GoalEvents",
                new Expected("register", GoalRegisterEventJS.class, ScriptType.STARTUP, null)
        );

        put(families, "EntityEvents",
                new Expected("damagePre", LivingDamageEvent.Pre.class, ScriptType.SERVER, EntityType.class),
                new Expected("damagePost", LivingDamageEvent.Post.class, ScriptType.SERVER, EntityType.class),
                new Expected("death", LivingDeathEvent.class, ScriptType.SERVER, EntityType.class),
                new Expected("drops", LivingDropsEvent.class, ScriptType.SERVER, EntityType.class),
                new Expected("finalizeSpawn", FinalizeSpawnEvent.class, ScriptType.SERVER, EntityType.class),
                new Expected("tickPre", EntityTickEvent.Pre.class, ScriptType.SERVER, EntityType.class),
                new Expected("tickPost", EntityTickEvent.Post.class, ScriptType.SERVER, EntityType.class),
                new Expected("joinLevel", EntityJoinLevelEvent.class, ScriptType.SERVER, EntityType.class),
                new Expected("leaveLevel", EntityLeaveLevelEvent.class, ScriptType.SERVER, EntityType.class),
                new Expected("useItemStarted", LivingEntityUseItemEvent.Start.class, ScriptType.SERVER, Item.class),
                new Expected("useItemStopped", LivingEntityUseItemEvent.Stop.class, ScriptType.SERVER, Item.class),
                new Expected("useItemFinished", LivingEntityUseItemEvent.Finish.class, ScriptType.SERVER, Item.class),
                new Expected("useItemTick", LivingEntityUseItemEvent.Tick.class, ScriptType.SERVER, Item.class)
        );
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
//?}
