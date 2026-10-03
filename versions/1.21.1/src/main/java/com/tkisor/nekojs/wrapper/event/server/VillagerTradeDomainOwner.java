// 1.21.1 实现，与版本树 src/ 下的同名 26.x 文件成对（票 22）。26.x 文件带 //? if >=26 守卫，
// 本节点的注册表形状是经典静态池（VillagerTrades.TRADES / WANDERING_TRADER_TRADES），
// 与 26.x 的 VILLAGER_TRADE / TRADE_SET 可重载注册表不同，故本节点成对文件独立实现。
// 可用 tools/extract_evaluated.py 比对两文件在本节点求值后的差异。
package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.bindings.event.ServerEvents;
import com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector;
import com.tkisor.nekojs.core.villager.VillagerTradeApplier;
import com.tkisor.nekojs.core.villager.VillagerTradeCandidatePlan;
import com.tkisor.nekojs.core.villager.VillagerTradeDeclaration;
import com.tkisor.nekojs.core.villager.VillagerTradeDeclarationEventJS;
import com.tkisor.nekojs.core.villager.VillagerTradeDomainState;
import com.tkisor.nekojs.core.villager.VillagerTradeReloadEventJS;
import com.tkisor.nekojs.core.villager.VillagerTradeSetSnapshot;
import com.tkisor.nekojs.core.villager.VillagerTradeUnavailableException;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Villager trade domain owner and 1.21.1 adapter (ticket 22): the 1.21.1 twin of the 26.x owner.
 *
 * <p>Registry shape difference: 1.21.1 keeps trades in the two static pools
 * {@code VillagerTrades.TRADES} (profession -> level -> listings) and
 * {@code VillagerTrades.WANDERING_TRADER_TRADES} (level -> listings), so the trade set id is the
 * pool id ({@code '<profession>/level_<n>'} or {@code 'wandering_trader/<pool>'}); the pools are
 * plain static fields, so they are replaced with rebuilt immutable copies instead of reflective
 * registry surgery.
 *
 * <p>Everything else follows the shared contract: collection is inert
 * ({@link CandidateDomainCollector}), the joint preflight/publish boundary decides commit, and
 * the commit restores the captured vanilla pools before applying the complete new plan, so a
 * declaration that disappeared never keeps stale listings and an ordinary reload never deletes
 * vanilla trades.
 */
public final class VillagerTradeDomainOwner
        implements CandidateDomainCollector, VillagerTradeApplier, AutoCloseable {

    /** Collector identity (aligned with {@code VillagerTradeCandidatePlan.DOMAIN}). */
    public static final String DOMAIN = VillagerTradeCandidatePlan.DOMAIN;

    /** Vanilla pool baseline captured on first touch (never mutated afterwards). */
    private volatile Map<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> originalTrades;
    private volatile Int2ObjectMap<VillagerTrades.ItemListing[]> originalWanderingTrades;
    /** Registry epoch of the captured baseline (bumped when the server instance changes). */
    private long registryEpoch = -1L;
    private volatile MinecraftServer boundServer;
    private volatile VillagerTradeSetSnapshot committedSnapshot = VillagerTradeSetSnapshot.EMPTY;
    private final VillagerTradeDomainState state = new VillagerTradeDomainState(adapterId(), true);
    private volatile Diagnostics lastDiagnostics = new Diagnostics(Outcome.INITIAL, "none", 0, 0, List.of(), null);

    public VillagerTradeDomainOwner() {
    }

    /** Domain bookkeeping installed into the query facade by the platform assembly. */
    public VillagerTradeDomainState state() {
        return state;
    }

    /** Binds the running server (pools die with the instance, so the baseline is re-captured). */
    public void bindServer(MinecraftServer server) {
        this.boundServer = server;
    }

    /** Drops the server binding and the pool baseline (a new server instance starts from vanilla). */
    public void clearServer() {
        this.boundServer = null;
        this.originalTrades = null;
        this.originalWanderingTrades = null;
    }

    // ---- CandidateDomainCollector (DOMAIN_PLAN phase) ----

    @Override
    public String domain() {
        return DOMAIN;
    }

    @Override
    public ScriptType scriptType() {
        return ScriptType.SERVER;
    }

    @Override
    public void collect(CandidateDomainCollector.Handle handle) {
        VillagerTradeCandidatePlan plan = new VillagerTradeCandidatePlan(this, state);
        // The initial server resource reload can precede the running server and its trade pools.
        // Keep this candidate inert until applyInitialPlan runs after the server is ready.
        if (boundServer == null) {
            handle.registerPlan(plan);
            return;
        }
        handle.dispatch(ServerEvents.TRADE_DECLARATION, new VillagerTradeDeclarationEventJS(plan));
        handle.dispatch(ServerEvents.TRADE_RELOAD,
                new VillagerTradeReloadEventJS(plan, state.committedSnapshot()));
        handle.registerPlan(plan);
    }

    /** Initial generation (server start): collect from the active bus, then preflight + apply. */
    public void applyInitialPlan(MinecraftServer server) {
        bindServer(server);
        VillagerTradeCandidatePlan plan = new VillagerTradeCandidatePlan(this, state);
        try {
            ServerEvents.TRADE_DECLARATION.post(new VillagerTradeDeclarationEventJS(plan));
            ServerEvents.TRADE_RELOAD.post(new VillagerTradeReloadEventJS(plan, state.committedSnapshot()));
        } catch (Throwable t) {
            lastDiagnostics = new Diagnostics(Outcome.COLLECTION_FAILED, "startup-dispatch",
                    0, 0, List.of(), String.valueOf(t));
            NekoJS.LOGGER.error("VillagerTrades: startup declaration dispatch failed; keeping existing trades", t);
            return;
        }
        try {
            plan.preflight();
        } catch (VillagerTradeUnavailableException unavailable) {
            lastDiagnostics = new Diagnostics(Outcome.UNAVAILABLE, "startup",
                    plan.declarations().size(), 0, List.of(), unavailable.reason());
            NekoJS.LOGGER.error("VillagerTrades: trade registry mutation is unavailable at startup ({})",
                    unavailable.reason());
            return;
        } catch (Throwable rejected) {
            lastDiagnostics = new Diagnostics(Outcome.BLOCKED, "startup",
                    plan.declarations().size(), 0, List.of(), String.valueOf(rejected));
            NekoJS.LOGGER.error("VillagerTrades: startup batch rejected as a whole (keeping existing trades): {}",
                    String.valueOf(rejected));
            return;
        }
        try {
            plan.publish();
            int applied = committedSnapshot.total();
            lastDiagnostics = new Diagnostics(applied == 0 ? Outcome.RESTORED : Outcome.APPLIED, "startup",
                    plan.declarations().size(), applied, committedSnapshot.tradeSetIds(), null);
            if (applied > 0) {
                NekoJS.LOGGER.info("VillagerTrades: committed {} trade declaration(s) across {} pool(s) at startup",
                        applied, committedSnapshot.tradeSetIds().size());
            }
        } catch (Throwable failure) {
            lastDiagnostics = new Diagnostics(Outcome.RECOVERY_FAILED, "startup",
                    plan.declarations().size(), 0, List.of(), String.valueOf(failure));
            NekoJS.LOGGER.error("VillagerTrades: startup apply failed; registry state is reported as failed, not active",
                    failure);
        }
    }

    // ---- VillagerTradeApplier: joint preflight / apply ----

    @Override
    public String adapterId() {
        return "1.21.1-classic-pools";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public void preflight(List<VillagerTradeDeclaration> declarations) {
        snapshotOriginals();
        for (VillagerTradeDeclaration declaration : declarations) {
            Target target = targetOf(declaration.tradeSetId());
            if (target.wandering()) {
                if (!originalWanderingTrades.containsKey(target.level())) {
                    throw new IllegalArgumentException(
                            "Unknown wandering trader pool: " + declaration.tradeSetId());
                }
            } else {
                VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.get(target.professionId());
                if (profession == null) {
                    throw new IllegalArgumentException("Unknown profession: " + declaration.tradeSetId());
                }
                Int2ObjectMap<VillagerTrades.ItemListing[]> levels = originalTrades.get(profession);
                if (levels == null || levels.get(target.level()) == null) {
                    throw new IllegalArgumentException("Unknown trade pool level: " + declaration.tradeSetId());
                }
            }
            resolveItem(declaration.wantsId());
            if (declaration.additionalWantsId() != null) {
                resolveItem(declaration.additionalWantsId());
            }
            resolveItem(declaration.givesId());
        }
    }

    /**
     * Commit point: restore the captured vanilla pools, then append the whole declaration list
     * to fresh copies. Vanilla listings are never removed — a declaration that disappeared just
     * does not come back.
     */
    @Override
    public void apply(List<VillagerTradeDeclaration> declarations, Set<String> obsoleteTradeSets) {
        snapshotOriginals();
        Map<String, Integer> appliedCounts = new LinkedHashMap<>();
        Map<VillagerProfession, List<VillagerTradeDeclaration>> byProfession = new LinkedHashMap<>();
        Int2ObjectMap<List<VillagerTradeDeclaration>> wandering = new Int2ObjectOpenHashMap<>();
        for (VillagerTradeDeclaration declaration : declarations) {
            Target target = targetOf(declaration.tradeSetId());
            if (target.wandering()) {
                wandering.computeIfAbsent(target.level(), level -> new ArrayList<>()).add(declaration);
            } else {
                VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.get(target.professionId());
                if (profession == null) {
                    throw new IllegalStateException("profession vanished between preflight and apply: "
                            + declaration.tradeSetId());
                }
                byProfession.computeIfAbsent(profession, key -> new ArrayList<>()).add(declaration);
            }
        }

        if (!byProfession.isEmpty()) {
            Map<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> next =
                    new HashMap<>(originalTrades);
            for (Map.Entry<VillagerProfession, List<VillagerTradeDeclaration>> entry : byProfession.entrySet()) {
                Int2ObjectMap<VillagerTrades.ItemListing[]> levels = next.get(entry.getKey());
                if (levels == null) {
                    throw new IllegalStateException("profession has no trade pools: " + entry.getKey());
                }
                Int2ObjectMap<VillagerTrades.ItemListing[]> nextLevels = new Int2ObjectOpenHashMap<>(levels);
                for (VillagerTradeDeclaration declaration : entry.getValue()) {
                    int level = targetOf(declaration.tradeSetId()).level();
                    VillagerTrades.ItemListing[] base = nextLevels.get(level);
                    nextLevels.put(level, append(base, buildListing(declaration)));
                    appliedCounts.merge(declaration.tradeSetId(), 1, Integer::sum);
                }
                next.put(entry.getKey(), nextLevels);
            }
            setStaticField(VillagerTrades.class, "TRADES", Map.copyOf(next));
        }

        if (!wandering.isEmpty()) {
            Int2ObjectMap<VillagerTrades.ItemListing[]> next = new Int2ObjectOpenHashMap<>(originalWanderingTrades);
            for (Int2ObjectMap.Entry<List<VillagerTradeDeclaration>> entry : wandering.int2ObjectEntrySet()) {
                VillagerTrades.ItemListing[] base = next.get(entry.getIntKey());
                if (base == null) {
                    throw new IllegalStateException("wandering trader has no level " + entry.getIntKey() + " pool");
                }
                VillagerTrades.ItemListing[] extended = base;
                for (VillagerTradeDeclaration declaration : entry.getValue()) {
                    extended = append(extended, buildListing(declaration));
                    appliedCounts.merge(declaration.tradeSetId(), 1, Integer::sum);
                }
                next.put(entry.getIntKey(), extended);
            }
            setStaticField(VillagerTrades.class, "WANDERING_TRADER_TRADES", next);
        }

        committedSnapshot = VillagerTradeSetSnapshot.of(registryEpoch, appliedCounts);
        if (!obsoleteTradeSets.isEmpty()) {
            NekoJS.LOGGER.info("VillagerTrades: {} pool(s) explicitly released by script declaration",
                    obsoleteTradeSets.size());
        }
    }

    @Override
    public VillagerTradeSetSnapshot committedSnapshot() {
        return committedSnapshot;
    }

    private void snapshotOriginals() {
        if (originalTrades == null) {
            originalTrades = VillagerTrades.TRADES;
            registryEpoch = Math.max(registryEpoch, 0L) + 1L;
        }
        if (originalWanderingTrades == null) {
            originalWanderingTrades = VillagerTrades.WANDERING_TRADER_TRADES;
        }
    }

    private static VillagerTrades.ItemListing[] append(VillagerTrades.ItemListing[] base,
            VillagerTrades.ItemListing listing) {
        VillagerTrades.ItemListing[] extended = new VillagerTrades.ItemListing[base.length + 1];
        System.arraycopy(base, 0, extended, 0, base.length);
        extended[base.length] = listing;
        return extended;
    }

    private static VillagerTrades.ItemListing buildListing(VillagerTradeDeclaration declaration) {
        Item wants = resolveItem(declaration.wantsId());
        Item gives = resolveItem(declaration.givesId());
        Item additional = declaration.additionalWantsId() == null
                ? null : resolveItem(declaration.additionalWantsId());
        return new NekoTradeListing(wants, declaration.wantsCount(), additional,
                declaration.additionalWantsCount(), gives, declaration.givesCount(),
                declaration.maxUses(), declaration.xp(), (float) declaration.priceMultiplier());
    }

    private static Item resolveItem(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) {
            throw new IllegalArgumentException("Unknown item: " + itemId);
        }
        return item;
    }

    /** Parsed trade set id: profession pool or wandering trader pool. */
    private record Target(ResourceLocation professionId, int level, boolean wandering) {
    }

    private static Target targetOf(String tradeSetId) {
        ResourceLocation id = ResourceLocation.tryParse(tradeSetId);
        if (id == null || !id.getPath().contains("/")) {
            throw new IllegalArgumentException("Trade set id must look like '<profession>/level_<n>': " + tradeSetId);
        }
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        String poolText = path.substring(slash + 1).toLowerCase(java.util.Locale.ROOT);
        int level = switch (poolText) {
            case "level_1", "buying", "common" -> 1;
            case "level_2", "uncommon", "rare" -> 2;
            default -> {
                if (poolText.startsWith("level_")) {
                    try {
                        yield Integer.parseInt(poolText.substring("level_".length()));
                    } catch (NumberFormatException ignored) {
                        yield -1;
                    }
                }
                yield -1;
            }
        };
        if (level < 1) {
            throw new IllegalArgumentException("Cannot parse a trade pool level from '" + tradeSetId + "'");
        }
        String professionPath = path.substring(0, slash);
        ResourceLocation professionId = ResourceLocation.tryParse(id.getNamespace() + ":" + professionPath);
        if (professionId == null) {
            throw new IllegalArgumentException("Invalid profession id in '" + tradeSetId + "'");
        }
        return new Target(professionId, level, professionPath.equals("wandering_trader"));
    }

    /** Vanilla-shaped listing backed by a declaration (built lazily per restock, like vanilla). */
    private static final class NekoTradeListing implements VillagerTrades.ItemListing {
        private final Item wants;
        private final int wantsCount;
        private final Item additionalWants;
        private final int additionalWantsCount;
        private final Item gives;
        private final int givesCount;
        private final int maxUses;
        private final int xp;
        private final float priceMultiplier;

        NekoTradeListing(Item wants, int wantsCount, Item additionalWants, int additionalWantsCount,
                Item gives, int givesCount, int maxUses, int xp, float priceMultiplier) {
            this.wants = wants;
            this.wantsCount = wantsCount;
            this.additionalWants = additionalWants;
            this.additionalWantsCount = additionalWantsCount;
            this.gives = gives;
            this.givesCount = givesCount;
            this.maxUses = maxUses;
            this.xp = xp;
            this.priceMultiplier = priceMultiplier;
        }

        @Override
        public MerchantOffer getOffer(Entity entity, RandomSource random) {
            Optional<ItemCost> additional = additionalWants == null
                    ? Optional.empty()
                    : Optional.of(new ItemCost(additionalWants, additionalWantsCount));
            return new MerchantOffer(
                    new ItemCost(wants, wantsCount),
                    additional,
                    new ItemStack(gives, givesCount),
                    maxUses,
                    xp,
                    priceMultiplier);
        }
    }

    // ---- diagnostics / lifecycle ----

    public Diagnostics lastDiagnostics() {
        return lastDiagnostics;
    }

    /** Root close: restores the captured vanilla pools and clears all records. */
    @Override
    public void close() {
        try {
            if (originalTrades != null) {
                setStaticField(VillagerTrades.class, "TRADES", originalTrades);
            }
            if (originalWanderingTrades != null) {
                setStaticField(VillagerTrades.class, "WANDERING_TRADER_TRADES", originalWanderingTrades);
            }
        } catch (Throwable t) {
            NekoJS.LOGGER.warn("VillagerTrades: restoring trade pools during root close failed", t);
        } finally {
            originalTrades = null;
            originalWanderingTrades = null;
            registryEpoch = -1L;
            committedSnapshot = VillagerTradeSetSnapshot.EMPTY;
            boundServer = null;
            state.reset();
            com.tkisor.nekojs.core.villager.VillagerTradesFacade.uninstall(state);
        }
    }

    /** Domain outcomes (same vocabulary as the 26.x owner). */
    public enum Outcome {
        INITIAL,
        APPLIED,
        RESTORED,
        BLOCKED,
        UNAVAILABLE,
        COLLECTION_FAILED,
        RECOVERY_FAILED
    }

    /** Last collection/commit outcome (public observation surface). */
    public record Diagnostics(Outcome outcome, String source, int declared, int applied,
                              List<String> tradeSets, String detail) {
    }

    /** Replaces a static pool field (vanilla declares them final; the port needs the swap). */
    @SuppressWarnings("removal")
    private static void setStaticField(Class<?> owner, String name, Object value) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            unsafe.putObject(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field), value);
        } catch (Exception e) {
            NekoJS.LOGGER.warn("VillagerTrades: failed to replace static field {}.{}", owner.getSimpleName(), name, e);
        }
    }
}
