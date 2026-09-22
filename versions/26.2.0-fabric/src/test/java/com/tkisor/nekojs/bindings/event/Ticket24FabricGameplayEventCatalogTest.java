// fabric 节点本地 catalog 快照（票 24）：`//? if fabric` 守卫对 active 节点
// （26.1.2 NeoForge，直编共享树磁盘）是惰性注释，会在错误平台上编译执行，所以放节点本地
// test 树（FabricNetworkRegistrationOnceTest 的先例）。两 fabric 节点各一份同名同体测试。
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
 * 票 24 catalog-snapshot fixture（fabric 侧）：按<b>生产初始化次序</b>（先触发
 * {@code FabricBlockEventBindings}/{@code FabricBlockEventBindingsV2}/
 * {@code FabricLevelEventBindingsV2} 的类初始化——它们的总线字段把成员
 * {@code GROUP.add} 进共享组，早于插件引导冻结；再跑
 * {@link FabricCorePlugin#registerEvents} 的同名组合并）+ 真实 catalog 派生，冻结
 * fabric 节点上八个事件族的公开成员形状。
 *
 * <p><b>与票 33 基线的输入差异（有意记录，不是矛盾）</b>：{@code EventSurfaceDomainGateTest}
 * 只驱动 {@code registerEvents} 钩子，看不见上述 Adapter 类初始化追加的成员——其基线把
 * fabric {@code BlockEvents} 记为 {@code broken,modification}、{@code LevelEvents} 记为
 * 4 成员，而真实运行面还含 v1/v2 Adapter 的 10+5 个成员。本测试按真实运行面冻结；基线
 * 再生成归票 33/34 owner（本票不改只读基线文件）。
 *
 * <p><b>能力差异的显式记录</b>：CapabilityEvents 不在 fabric 注册面（NeoForge
 * {@code RegisterCapabilitiesEvent} 专属，票 33 基线 not-verified）——显式缺席，不是静默
 * no-op；可取消性来自孪生/Adapter 源码的显式 {@code EventBusJS.of(type, true, ...)}。
 */
class Ticket24FabricGameplayEventCatalogTest {

    /** 一条公开成员的期望形状：dispatchKey 为 {@code null} 表示非定向分发总线。 */
    private record Expected(String name, Class<?> payload, ScriptType side, Class<?> dispatchKey, boolean cancellable) {}

    private static Map<String, EventGroup> registeredGroups() {
        // 生产次序：mod entry 的各 register() 先让 Adapter 类初始化（成员 GROUP.add 进共享
        // 组、早于冻结）；这里只触发类初始化本身，不挂 fabric-api 回调（catalog 面与回调
        // 挂载无关，回调 source trace 见共享树 Ticket24GameplayEventPhaseTraceTest）
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
        EventGroupRegistry registry = new EventGroupRegistry.Impl();
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
        assertFalse(groups.containsKey("CapabilityEvents"),
                "CapabilityEvents must stay a neoforge-only family on fabric (explicit gap, never a silent no-op)");

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
                // broken：共享层声明（GROUP.server + predicate）——fabric 无 external
                // cancellability predicate，bus 为不可取消。已知接线矛盾：fabric 桥把
                // !BROKEN.post(...) 接进可取消的 PlayerBlockBreakEvents.BEFORE，但 post
                // 恒 false（不可取消总线），脚本取消实际被静默忽略——缺陷记录见票 24
                // REPORT（修复=行为变更，需维护者裁定，不属本票盘点范围）。
                new Expected("broken", BlockBrokenEventJS.class, ScriptType.SERVER, Block.class, false),
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
