// 26.x 共享实现（1.21.1 是 versions/1.21.1 下的成对文件）。本文件只依赖 net.minecraft.*，
// 无 loader import，因此 26.x 的两个 loader 节点共用同一份 Adapter；fabric 节点当前不装配它
// （改用显式 unavailable owner，票 22 AC9）。
//? if >=26 {
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
import com.tkisor.nekojs.core.villager.VillagerTradesFacade;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.trading.TradeCost;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Villager trade domain owner and platform adapter for 26.x (ticket 22):
 * <b>a root-authorised domain owner</b> — the platform assembly creates the instance, registers
 * it through {@code NekoRuntimeRoot#registerDomainCollector} (the root owns its lifecycle and
 * closes it) and installs its {@link VillagerTradeDomainState} into {@link VillagerTradesFacade}
 * so the {@code VillagerTrades} query binding can read it. It is not a new public Villager Trade
 * Runtime and not a second registry path: it implements the shared
 * {@link CandidateDomainCollector} (collection mount point) and {@link VillagerTradeApplier}
 * (apply seam).
 *
 * <p>Two legal collection points:
 * <ul>
 *   <li><b>transactional reload ({@code DOMAIN_PLAN})</b>: {@link #collect} dispatches the
 *       declaration/reload events into the <b>candidate's</b> pending listeners and registers an
 *       inert {@link VillagerTradeCandidatePlan} on the joint boundary — nothing touches the
 *       live registries before the commit point;</li>
 *   <li><b>initial generation (server about-to-start)</b>: {@link #applyInitialPlan} dispatches to
 *       the active bus, preflights and applies in the same owner (no old active plan means the
 *       baseline is vanilla).</li>
 * </ul>
 *
 * <p>Registry surgery is deliberately confined here: the shared event surface and
 * {@code common} contain no Minecraft/loader type. Reloadable registries are frozen after the
 * datapack load, so re-registering entries needs a temporary unfreeze plus direct internal-map
 * surgery (the same semantics the legacy manager used; kept because there is no public API for
 * it). Note that registration order matters: {@code MappedRegistry} assigns ids on registration,
 * so entries are removed before new ones are inserted, which reassigns ids deterministically and
 * keeps the id-map consistent.
 */
public final class VillagerTradeDomainOwner
        implements CandidateDomainCollector, VillagerTradeApplier, AutoCloseable {

    /** Collector identity (aligned with {@code VillagerTradeCandidatePlan.DOMAIN}). */
    public static final String DOMAIN = VillagerTradeCandidatePlan.DOMAIN;

    /** Baseline of every trade set NekoJS replaced (first touch wins, per registry epoch). */
    private final Map<ResourceKey<TradeSet>, TradeSet> tradeSetBaselines = new LinkedHashMap<>();
    /** Trades registered by the previous commit, removed before the new batch is registered. */
    private final List<ResourceKey<VillagerTrade>> injectedTrades = new ArrayList<>();
    /**
     * Registry instance the baselines above were taken from. Every resource reload builds fresh
     * {@code MappedRegistry} instances for the reloadable layer; a mismatch invalidates all
     * snapshot state so stale holders never leak into the new registry.
     */
    private MappedRegistry<TradeSet> snapshotEpoch;
    /** Monotonic counter of registry epochs this adapter observed (diagnostics + query epoch). */
    private long registryEpochSequence = -1L;
    /**
     * Server bound at about-to-start. The reloadable registry access is read from it through the
     * platform lifecycle hook (same source the legacy path used) so a detached owner instance
     * cannot keep a stale server reference alive.
     */
    private volatile MinecraftServer boundServer;
    private final VillagerTradeDomainState state = new VillagerTradeDomainState(adapterId(), true);
    private volatile Diagnostics lastDiagnostics = new Diagnostics(Outcome.INITIAL, "none", 0, 0, List.of(), null);

    public VillagerTradeDomainOwner() {
    }

    /** Snapshot published by the most recent successful commit (read-only query input). */
    private volatile VillagerTradeSetSnapshot committedSnapshot = VillagerTradeSetSnapshot.EMPTY;

    /** Domain bookkeeping installed into the query facade by the platform assembly. */
    public VillagerTradeDomainState state() {
        return state;
    }

    // ---- server binding ----

    /** Binds the running server (server about-to-start); earlier commits have no registry access. */
    public void bindServer(MinecraftServer server) {
        this.boundServer = server;
    }

    /** Drops the server binding (server stopped: reloadable registries die with the instance). */
    public void clearServer() {
        this.boundServer = null;
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
        // The initial server resource reload can precede the running server and its reloadable
        // trade registries. Keep this candidate inert; applyInitialPlan collects active listeners
        // after the server is ready and performs the first real preflight.
        if (boundServer == null) {
            handle.registerPlan(plan);
            return;
        }
        handle.dispatch(ServerEvents.TRADE_DECLARATION, new VillagerTradeDeclarationEventJS(plan));
        handle.dispatch(ServerEvents.TRADE_RELOAD,
                new VillagerTradeReloadEventJS(plan, state.committedSnapshot()));
        handle.registerPlan(plan);
    }

    // ---- initial generation (server start collection point) ----

    /**
     * Initial generation: dispatch both events to the active bus, preflight and apply in this
     * owner. A rejected or unavailable batch keeps the vanilla/previous registries untouched and
     * is reported through {@link #lastDiagnostics()} — never as a successful no-op.
     */
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
            int applied = state.committedSnapshot().total();
            lastDiagnostics = new Diagnostics(
                    applied == 0 ? Outcome.RESTORED : Outcome.APPLIED, "startup",
                    plan.declarations().size(), applied,
                    state.committedSnapshot().tradeSetIds(), null);
            if (applied > 0) {
                NekoJS.LOGGER.info("VillagerTrades: committed {} trade declaration(s) across {} trade set(s) at startup",
                        applied, state.committedSnapshot().tradeSetIds().size());
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
        return "26.x-trade-set";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public void preflight(List<VillagerTradeDeclaration> declarations) {
        if (declarations.isEmpty()) {
            // Nothing was requested: an unused domain must never fail a reload. (The adapter is
            // still registered, so a later declaration in the same session is verified normally.)
            return;
        }
        MappedRegistry<TradeSet> tradeSets = tradeSetRegistry();
        if (tradeSets == null) {
            throw new VillagerTradeUnavailableException(
                    "reloadable trade registries are not ready yet (no bound running server)");
        }
        for (VillagerTradeDeclaration declaration : declarations) {
            ResourceKey<TradeSet> key = tradeSetKey(declaration.tradeSetId());
            if (tradeSets.getValue(key) == null) {
                throw new IllegalArgumentException("Unknown trade set: " + declaration.tradeSetId());
            }
            resolveItem(declaration.wantsId());
            if (declaration.additionalWantsId() != null) {
                resolveItem(declaration.additionalWantsId());
            }
            resolveItem(declaration.givesId());
        }
    }

    /**
     * Commit point: restore the trade sets NekoJS replaced, drop the entries NekoJS registered,
     * then register and attach the whole declaration list. Declarations no longer present simply
     * do not come back — ordinary reload never deletes a vanilla entry.
     */
    @Override
    public void apply(List<VillagerTradeDeclaration> declarations, Set<String> obsoleteTradeSets) {
        MappedRegistry<TradeSet> tradeSetRegistry = tradeSetRegistry();
        MappedRegistry<VillagerTrade> tradeRegistry = villagerTradeRegistry();
        if (tradeSetRegistry == null || tradeRegistry == null) {
            if (declarations.isEmpty() && tradeSetBaselines.isEmpty() && injectedTrades.isEmpty()) {
                // Nothing requested and nothing NekoJS-owned to restore: a no-op, not a failure.
                committedSnapshot = VillagerTradeSetSnapshot.EMPTY;
                return;
            }
            throw new VillagerTradeUnavailableException(
                    "reloadable trade registries are not ready at the commit point");
        }
        if (snapshotEpoch != tradeSetRegistry) {
            // Resource reload rebuilt the registries: the old snapshots are meaningless, the new
            // registry's vanilla content is the baseline (same rule the legacy path used).
            tradeSetBaselines.clear();
            injectedTrades.clear();
            snapshotEpoch = tradeSetRegistry;
            registryEpochSequence++;
        }

        // (1) restore every replaced trade set and drop every injected trade
        if (!tradeSetBaselines.isEmpty()) {
            Map<ResourceKey<TradeSet>, TradeSet> baselines = new LinkedHashMap<>(tradeSetBaselines);
            withUnfrozen(tradeSetRegistry,
                    () -> baselines.forEach((key, original) -> replaceTradeSet(tradeSetRegistry, key, original)));
        }
        if (!injectedTrades.isEmpty()) {
            List<ResourceKey<VillagerTrade>> previous = List.copyOf(injectedTrades);
            withUnfrozen(tradeRegistry, () -> previous.forEach(key -> unregister(tradeRegistry, key)));
            injectedTrades.clear();
        }

        // (2) group the declarations by trade set, preserving declaration order
        Map<ResourceKey<TradeSet>, List<VillagerTradeDeclaration>> bySet = new LinkedHashMap<>();
        for (VillagerTradeDeclaration declaration : declarations) {
            bySet.computeIfAbsent(tradeSetKey(declaration.tradeSetId()), key -> new ArrayList<>()).add(declaration);
        }

        // (3) register the new trades and attach them to their sets
        Map<String, Integer> appliedCounts = new LinkedHashMap<>();
        List<Holder<VillagerTrade>> newHolders = new ArrayList<>();
        for (Map.Entry<ResourceKey<TradeSet>, List<VillagerTradeDeclaration>> entry : bySet.entrySet()) {
            ResourceKey<TradeSet> setKey = entry.getKey();
            TradeSet original = tradeSetRegistry.getValue(setKey);
            if (original == null) {
                throw new IllegalStateException("trade set vanished between preflight and apply: "
                        + setKey.identifier());
            }
            tradeSetBaselines.putIfAbsent(setKey, original);
            HolderSet<VillagerTrade> baselineHolders = original.getTrades();
            int index = 0;
            for (VillagerTradeDeclaration declaration : entry.getValue()) {
                index++;
                VillagerTrade trade = buildTrade(declaration);
                Identifier tradeId = Identifier.fromNamespaceAndPath(
                        NekoJS.MODID, "trade/" + setKey.identifier().getPath() + "/" + index);
                ResourceKey<VillagerTrade> tradeKey = ResourceKey.create(Registries.VILLAGER_TRADE, tradeId);
                Holder.Reference<VillagerTrade> holder =
                        withUnfrozenFor(tradeRegistry, () -> Registry.registerForHolder(tradeRegistry, tradeKey, trade));
                injectedTrades.add(tradeKey);
                newHolders.add(holder);
            }
            List<Holder<VillagerTrade>> combined = new ArrayList<>(baselineHolders.size() + newHolders.size());
            baselineHolders.forEach(combined::add);
            combined.addAll(newHolders);
            newHolders.clear();
            TradeSet replacement = new TradeSet(
                    HolderSet.direct(combined),
                    amountOf(original),
                    original.allowDuplicates(),
                    original.randomSequence());
            withUnfrozen(tradeSetRegistry, () -> replaceTradeSet(tradeSetRegistry, setKey, replacement));
            appliedCounts.put(setKey.identifier().toString(), entry.getValue().size());
        }
        if (!obsoleteTradeSets.isEmpty()) {
            // Those sets were already restored by (1); the record is what the query reports.
            NekoJS.LOGGER.info("VillagerTrades: {} trade set(s) explicitly released by script declaration",
                    obsoleteTradeSets.size());
        }
        committedSnapshot = VillagerTradeSetSnapshot.of(registryEpochSequence, appliedCounts);
    }

    @Override
    public VillagerTradeSetSnapshot committedSnapshot() {
        return committedSnapshot;
    }

    private VillagerTrade buildTrade(VillagerTradeDeclaration declaration) {
        Item wants = resolveItem(declaration.wantsId());
        Item gives = resolveItem(declaration.givesId());
        Optional<TradeCost> additional = Optional.empty();
        if (declaration.additionalWantsId() != null) {
            Item extra = resolveItem(declaration.additionalWantsId());
            additional = Optional.of(new TradeCost(extra, declaration.additionalWantsCount()));
        }
        return new VillagerTrade(
                new TradeCost(wants, declaration.wantsCount()),
                additional,
                new ItemStackTemplate(gives, declaration.givesCount()),
                declaration.maxUses(),
                declaration.xp(),
                (float) declaration.priceMultiplier(),
                Optional.empty(),
                List.of());
    }

    private static ResourceKey<TradeSet> tradeSetKey(String tradeSetId) {
        Identifier id = Identifier.tryParse(tradeSetId);
        if (id == null) {
            throw new IllegalArgumentException("Invalid trade set id: " + tradeSetId);
        }
        return ResourceKey.create(Registries.TRADE_SET, id);
    }

    private static Item resolveItem(String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) {
            throw new IllegalArgumentException("Unknown item: " + itemId);
        }
        return item;
    }

    private MappedRegistry<TradeSet> tradeSetRegistry() {
        return mappedRegistry(Registries.TRADE_SET);
    }

    private MappedRegistry<VillagerTrade> villagerTradeRegistry() {
        return mappedRegistry(Registries.VILLAGER_TRADE);
    }

    /**
     * Reloadable registry as a mutable {@code MappedRegistry}, or {@code null} when the server is
     * not bound / the lookup is not a mapped registry. The lookup is streamed into the expected
     * {@link RegistryAccess} shape exactly like the legacy manager did; without a bound server the
     * adapters report unavailability instead of guessing.
     */
    @SuppressWarnings("unchecked")
    private <T> MappedRegistry<T> mappedRegistry(ResourceKey<? extends Registry<T>> key) {
        MinecraftServer server = boundServer;
        if (server == null) {
            return null;
        }
        return server.reloadableRegistries().lookup() instanceof RegistryAccess access
                ? (access.lookup(key).orElse(null) instanceof MappedRegistry<T> mapped ? mapped : null)
                : null;
    }

    private static NumberProvider amountOf(TradeSet set) {
        Object raw = readField(set, "amount");
        return raw instanceof NumberProvider provider ? provider : ConstantValue.exactly(2.0f);
    }

    private static void replaceTradeSet(MappedRegistry<TradeSet> registry, ResourceKey<TradeSet> key, TradeSet value) {
        unregister(registry, key);
        Registry.register(registry, key, value);
    }

    // ---- diagnostics / lifecycle ----

    public Diagnostics lastDiagnostics() {
        return lastDiagnostics;
    }

    /** Root close: restores every replaced trade set, drops every injected trade, clears records. */
    @Override
    public void close() {
        try {
            MappedRegistry<TradeSet> tradeSets = snapshotEpoch;
            MappedRegistry<VillagerTrade> trades = villagerTradeRegistry();
            if (tradeSets != null && !tradeSetBaselines.isEmpty()) {
                Map<ResourceKey<TradeSet>, TradeSet> baselines = new LinkedHashMap<>(tradeSetBaselines);
                withUnfrozen(tradeSets,
                        () -> baselines.forEach((key, original) -> replaceTradeSet(tradeSets, key, original)));
            }
            if (trades != null && !injectedTrades.isEmpty()) {
                List<ResourceKey<VillagerTrade>> previous = List.copyOf(injectedTrades);
                withUnfrozen(trades, () -> previous.forEach(key -> unregister(trades, key)));
            }
        } catch (Throwable t) {
            NekoJS.LOGGER.warn("VillagerTrades: restoring trade baselines during root close failed", t);
        } finally {
            tradeSetBaselines.clear();
            injectedTrades.clear();
            snapshotEpoch = null;
            registryEpochSequence = -1L;
            committedSnapshot = VillagerTradeSetSnapshot.EMPTY;
            boundServer = null;
            state.reset();
            com.tkisor.nekojs.core.villager.VillagerTradesFacade.uninstall(state);
        }
    }

    /** Domain outcomes (diagnostics distinguish active / restored / blocked / unavailable / failed). */
    public enum Outcome {
        /** Nothing collected or applied yet. */
        INITIAL,
        /** The new batch is active (the registry carries exactly these declarations). */
        APPLIED,
        /** Empty batch applied: NekoJS entries were removed and baselines restored. */
        RESTORED,
        /** Preflight rejected the batch as a whole; existing trades keep serving. */
        BLOCKED,
        /** The node cannot mutate the trade registries at this point (explicit, not a no-op). */
        UNAVAILABLE,
        /** Collection itself failed; nothing was applied. */
        COLLECTION_FAILED,
        /** Apply failed after preflight; the adapter does not claim success. */
        RECOVERY_FAILED
    }

    /** Last collection/commit outcome (public observation surface). */
    public record Diagnostics(Outcome outcome, String source, int declared, int applied,
                              List<String> tradeSets, String detail) {
    }

    // ------------------------------------------------------------------
    // Registry reflection: reloadable registries are frozen after the datapack
    // load, so registration/removal needs a temporary unfreeze plus direct
    // internal-map surgery (no public API exists for it).
    // ------------------------------------------------------------------

    @FunctionalInterface
    private interface RegistryJob<R> {
        R run();
    }

    private static void withUnfrozen(MappedRegistry<?> registry, Runnable job) {
        withUnfrozenFor(registry, () -> {
            job.run();
            return null;
        });
    }

    private static <R> R withUnfrozenFor(MappedRegistry<?> registry, RegistryJob<R> job) {
        boolean wasFrozen = isFrozen(registry);
        try {
            if (wasFrozen) {
                writeField(registry, "frozen", false);
            }
            return job.run();
        } finally {
            if (wasFrozen) {
                writeField(registry, "frozen", true);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> boolean unregister(MappedRegistry<T> registry, ResourceKey<T> key) {
        Holder.Reference<T> holder = registry.get(key.identifier()).orElse(null);
        if (holder == null) {
            return false;
        }
        T value = holder.value();
        Map<ResourceKey<T>, Holder.Reference<T>> byKey =
                (Map<ResourceKey<T>, Holder.Reference<T>>) readField(registry, "byKey");
        Map<Identifier, Holder.Reference<T>> byLocation =
                (Map<Identifier, Holder.Reference<T>>) readField(registry, "byLocation");
        Map<T, Holder.Reference<T>> byValue = (Map<T, Holder.Reference<T>>) readField(registry, "byValue");
        List<Holder.Reference<T>> byId = (List<Holder.Reference<T>>) readField(registry, "byId");
        Reference2IntMap<T> toId = (Reference2IntMap<T>) readField(registry, "toId");
        Map<ResourceKey<T>, Object> registrationInfos =
                (Map<ResourceKey<T>, Object>) readField(registry, "registrationInfos");
        if (byKey == null || byLocation == null || byValue == null || byId == null || toId == null
                || registrationInfos == null) {
            NekoJS.LOGGER.warn("VillagerTrades: cannot access MappedRegistry internals; unregister of {} skipped",
                    key.identifier());
            return false;
        }
        int removedIndex = toId.removeInt(value);
        byKey.remove(key);
        byLocation.remove(key.identifier());
        byValue.remove(value);
        registrationInfos.remove(key);
        if (removedIndex >= 0 && removedIndex < byId.size() && byId.get(removedIndex) == holder) {
            byId.remove(removedIndex);
        } else {
            byId.remove(holder);
        }
        toId.clear();
        for (int i = 0; i < byId.size(); i++) {
            toId.put(byId.get(i).value(), i);
        }
        writeField(holder, "tags", java.util.Set.of());
        return true;
    }

    private static boolean isFrozen(MappedRegistry<?> registry) {
        Object raw = readField(registry, "frozen");
        return !(raw instanceof Boolean frozen) || frozen;
    }

    // ------------------------------------------------------------------
    // Minimal reflective field IO (plain reflection first, sun.misc.Unsafe
    // fallback for final / JPMS-restricted fields).
    // ------------------------------------------------------------------

    private static Object readField(Object target, String name) {
        Field field = findField(target.getClass(), name);
        if (field == null) {
            return null;
        }
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception e) {
            try {
                return unsafeGet(field, target);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private static void writeField(Object target, String name, Object value) {
        Field field = findField(target.getClass(), name);
        if (field == null) {
            return;
        }
        try {
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            try {
                if (value instanceof Boolean flag) {
                    unsafePutBoolean(field, target, flag);
                } else {
                    unsafePutObject(field, target, value);
                }
            } catch (Exception ex) {
                NekoJS.LOGGER.warn("VillagerTrades: failed to write field '{}' on {}", name,
                        target.getClass().getName(), ex);
            }
        }
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // keep walking the hierarchy
            }
        }
        return null;
    }

    @SuppressWarnings("removal")
    private static sun.misc.Unsafe theUnsafe() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (sun.misc.Unsafe) unsafeField.get(null);
    }

    @SuppressWarnings("removal")
    private static Object unsafeGet(Field field, Object target) throws Exception {
        long offset = theUnsafe().objectFieldOffset(field);
        return theUnsafe().getObject(target, offset);
    }

    @SuppressWarnings("removal")
    private static void unsafePutObject(Field field, Object target, Object value) throws Exception {
        long offset = theUnsafe().objectFieldOffset(field);
        theUnsafe().putObject(target, offset, value);
    }

    @SuppressWarnings("removal")
    private static void unsafePutBoolean(Field field, Object target, boolean value) throws Exception {
        long offset = theUnsafe().objectFieldOffset(field);
        theUnsafe().putBoolean(target, offset, value);
    }
}
//?}
