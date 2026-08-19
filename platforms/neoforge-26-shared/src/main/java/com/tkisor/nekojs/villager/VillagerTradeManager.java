package com.tkisor.nekojs.villager;

import com.tkisor.nekojs.NekoJS;
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
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stages and applies script mutations to villager / wandering trader trade sets on MC 26.x.
 *
 * <p>26.x removed the legacy {@code VillagerTrades.TRADES} static map: trades live in two
 * reloadable registries — {@link Registries#VILLAGER_TRADE} holds individual
 * {@link VillagerTrade} instances, {@link Registries#TRADE_SET} holds {@link TradeSet}
 * entries aggregating them via {@link HolderSet}. Vanilla professions reference trade sets
 * by {@code ResourceKey<TradeSet>} per level.</p>
 *
 * <p>Strategy (same shape Katton uses on 26.1.2): scripts stage {@link PendingTrade}s
 * through {@link #stageAdd}; {@link #apply} then (1) removes trades injected during the
 * previous apply and restores touched TradeSets from an originals snapshot (vanilla is the
 * eternal baseline), (2) registers new {@link VillagerTrade} entries under
 * {@code nekojs:trade/<set>/<n>}, (3) replaces each affected TradeSet with one whose
 * HolderSet is original holders plus the new trades. Registry mutation uses reflective
 * unfreeze + entry removal because reloadable registries are frozen after datapack load.</p>
 */
public final class VillagerTradeManager {

    private VillagerTradeManager() {}

    /** Description of a trade a script asked to append to a trade set; resolved at apply time. */
    public static final class PendingTrade {
        public final ResourceKey<TradeSet> tradeSet;
        public final Identifier wantsId;
        public final int wantsCount;
        public final Identifier additionalWantsId; // nullable
        public final int additionalWantsCount;
        public final Identifier givesId;
        public final int givesCount;
        public final int maxUses;
        public final int xp;
        public final float priceMultiplier;

        public PendingTrade(
                ResourceKey<TradeSet> tradeSet,
                Identifier wantsId, int wantsCount,
                Identifier additionalWantsId, int additionalWantsCount,
                Identifier givesId, int givesCount,
                int maxUses, int xp, float priceMultiplier
        ) {
            this.tradeSet = tradeSet;
            this.wantsId = wantsId;
            this.wantsCount = wantsCount;
            this.additionalWantsId = additionalWantsId;
            this.additionalWantsCount = additionalWantsCount;
            this.givesId = givesId;
            this.givesCount = givesCount;
            this.maxUses = maxUses;
            this.xp = xp;
            this.priceMultiplier = priceMultiplier;
        }
    }

    private static final List<PendingTrade> PENDING = new ArrayList<>();
    /** TradeSet key -> original HolderSet snapshot (first touch wins). */
    private static final Map<ResourceKey<TradeSet>, HolderSet<VillagerTrade>> HOLDER_SNAPSHOTS = new HashMap<>();
    /** TradeSet key -> original TradeSet instance (first touch wins). */
    private static final Map<ResourceKey<TradeSet>, TradeSet> ORIGINALS = new LinkedHashMap<>();
    /** Trades injected during the previous apply pass. */
    private static final List<ResourceKey<VillagerTrade>> PREVIOUSLY_REGISTERED = new ArrayList<>();
    /**
     * Registry instance the snapshots above were taken from. Every resource reload builds
     * fresh MappedRegistry instances for the RELOADABLE layer; a mismatch invalidates all
     * snapshot/restore state so stale holders never leak into the new registry.
     */
    private static MappedRegistry<TradeSet> snapshotEpoch;

    public static void stageAdd(PendingTrade trade) {
        synchronized (PENDING) {
            PENDING.add(trade);
        }
    }

    /**
     * Drops staged trades ahead of a full SERVER script reload. Scripts always re-run in
     * full, so re-staging supersedes anything staged earlier — this prevents duplicate
     * additions when several reload cycles happen between two flushes.
     */
    public static void beginReload() {
        synchronized (PENDING) {
            PENDING.clear();
        }
    }

    public static int pendingCount() {
        synchronized (PENDING) {
            return PENDING.size();
        }
    }

    /** Clears all staged / snapshot state (server stopped: reloadable registries die with the server). */
    public static void reset() {
        synchronized (PENDING) {
            PENDING.clear();
            HOLDER_SNAPSHOTS.clear();
            ORIGINALS.clear();
            PREVIOUSLY_REGISTERED.clear();
            snapshotEpoch = null;
        }
    }

    /**
     * Flushes staged trades into the live registries. Safe to call repeatedly: each pass
     * first removes the previous pass's injected entries and restores touched TradeSets,
     * then re-applies the current pending list. Returns true when anything changed.
     */
    public static boolean apply(MinecraftServer server) {
        List<PendingTrade> pending;
        List<ResourceKey<VillagerTrade>> previous;
        synchronized (PENDING) {
            if (PENDING.isEmpty() && PREVIOUSLY_REGISTERED.isEmpty()) {
                return false;
            }
            pending = new ArrayList<>(PENDING);
            previous = new ArrayList<>(PREVIOUSLY_REGISTERED);
            PENDING.clear();
        }

        if (!(server.reloadableRegistries().lookup() instanceof RegistryAccess access)) {
            NekoJS.LOGGER.warn("VillagerTrades: reloadable registry access unavailable; dropping {} staged trades", pending.size());
            return false;
        }
        MappedRegistry<VillagerTrade> tradeRegistry = mappedRegistry(access, Registries.VILLAGER_TRADE);
        MappedRegistry<TradeSet> tradeSetRegistry = mappedRegistry(access, Registries.TRADE_SET);
        if (tradeRegistry == null || tradeSetRegistry == null) {
            NekoJS.LOGGER.warn("VillagerTrades: VILLAGER_TRADE / TRADE_SET registries not mapped; dropping {} staged trades", pending.size());
            return false;
        }

        synchronized (PENDING) {
            if (snapshotEpoch != tradeSetRegistry) {
                // New registry epoch (resource reload built fresh instances): snapshots of the
                // dead registry are meaningless — vanilla content of the new one is the baseline.
                HOLDER_SNAPSHOTS.clear();
                ORIGINALS.clear();
                PREVIOUSLY_REGISTERED.clear();
                snapshotEpoch = tradeSetRegistry;
                previous.clear();
            }
        }

        // Step 1: drop trades registered by the previous apply.
        if (!previous.isEmpty()) {
            withUnfrozen(tradeRegistry, () -> previous.forEach(key -> unregister(tradeRegistry, key)));
            synchronized (PENDING) {
                PREVIOUSLY_REGISTERED.removeAll(previous);
            }
        }
        // Step 2: restore previously modified TradeSets from originals (vanilla is the eternal baseline).
        Map<ResourceKey<TradeSet>, TradeSet> originals;
        synchronized (PENDING) {
            originals = new LinkedHashMap<>(ORIGINALS);
        }
        if (!originals.isEmpty()) {
            withUnfrozen(tradeSetRegistry, () ->
                    originals.forEach((key, original) -> replaceTradeSet(tradeSetRegistry, key, original)));
        }
        if (pending.isEmpty()) {
            return true; // cleanup of the previous round was the only work
        }

        // Step 3: snapshot every TradeSet about to be mutated.
        Map<ResourceKey<TradeSet>, List<PendingTrade>> bySet = new LinkedHashMap<>();
        for (PendingTrade t : pending) {
            bySet.computeIfAbsent(t.tradeSet, k -> new ArrayList<>()).add(t);
        }
        synchronized (PENDING) {
            for (ResourceKey<TradeSet> key : bySet.keySet()) {
                TradeSet existing = tradeSetRegistry.getValue(key);
                if (existing == null) {
                    NekoJS.LOGGER.warn("VillagerTrades: trade set {} vanished from the registry; its trades are skipped", key.identifier());
                    continue;
                }
                HOLDER_SNAPSHOTS.putIfAbsent(key, existing.getTrades());
                ORIGINALS.putIfAbsent(key, existing);
            }
        }

        // Step 4: register the new VillagerTrade entries (single unfreeze batch).
        Map<ResourceKey<TradeSet>, List<Holder<VillagerTrade>>> newHoldersBySet = new LinkedHashMap<>();
        int index = 0;
        int skipped = 0;
        List<ResourceKey<VillagerTrade>> registeredNow = new ArrayList<>();
        for (PendingTrade t : pending) {
            index++;
            VillagerTrade trade = buildTrade(t);
            if (trade == null) {
                skipped++;
                continue;
            }
            Identifier tradeId = Identifier.fromNamespaceAndPath(
                        NekoJS.MODID, "trade/" + t.tradeSet.identifier().getPath() + "/" + index);
            ResourceKey<VillagerTrade> key = ResourceKey.create(Registries.VILLAGER_TRADE, tradeId);
            Holder.Reference<VillagerTrade> holder =
                    withUnfrozenFor(tradeRegistry, () -> Registry.registerForHolder(tradeRegistry, key, trade));
            if (holder == null) {
                skipped++;
                continue;
            }
            registeredNow.add(key);
            newHoldersBySet.computeIfAbsent(t.tradeSet, k -> new ArrayList<>()).add(holder);
        }
        synchronized (PENDING) {
            PREVIOUSLY_REGISTERED.addAll(registeredNow);
        }

        // Step 5: replace each affected TradeSet with the original holders plus the new ones.
        withUnfrozen(tradeSetRegistry, () -> newHoldersBySet.forEach((key, added) -> {
            TradeSet baseline;
            HolderSet<VillagerTrade> originalHolders;
            synchronized (PENDING) {
                baseline = ORIGINALS.get(key);
                originalHolders = HOLDER_SNAPSHOTS.get(key);
            }
            if (baseline == null) {
                return;
            }
            if (originalHolders == null) {
                originalHolders = baseline.getTrades();
            }
            List<Holder<VillagerTrade>> combined = new ArrayList<>(originalHolders.size() + added.size());
            originalHolders.forEach(combined::add);
            combined.addAll(added);
            TradeSet replacement = new TradeSet(
                    HolderSet.direct(combined),
                    amountOf(baseline),
                    baseline.allowDuplicates(),
                    baseline.randomSequence());
            replaceTradeSet(tradeSetRegistry, key, replacement);
        }));

        NekoJS.LOGGER.info("VillagerTrades: applied {} trade addition(s) across {} trade set(s) ({} skipped)",
                pending.size() - skipped, newHoldersBySet.size(), skipped);
        return true;
    }

    private static VillagerTrade buildTrade(PendingTrade t) {
        Item wants = resolveItem(t.wantsId);
        if (wants == null) {
            NekoJS.LOGGER.warn("VillagerTrades: cost item {} is not registered", t.wantsId);
            return null;
        }
        Item gives = resolveItem(t.givesId);
        if (gives == null) {
            NekoJS.LOGGER.warn("VillagerTrades: result item {} is not registered", t.givesId);
            return null;
        }
        Optional<TradeCost> additional;
        if (t.additionalWantsId != null) {
            Item extra = resolveItem(t.additionalWantsId);
            if (extra == null) {
                NekoJS.LOGGER.warn("VillagerTrades: secondary cost item {} is not registered", t.additionalWantsId);
                return null;
            }
            additional = Optional.of(new TradeCost(extra, t.additionalWantsCount));
        } else {
            additional = Optional.empty();
        }
        return new VillagerTrade(
                new TradeCost(wants, t.wantsCount),
                additional,
                new ItemStackTemplate(gives, t.givesCount),
                t.maxUses,
                t.xp,
                t.priceMultiplier,
                Optional.empty(),
                List.of()
        );
    }

    private static Item resolveItem(Identifier id) {
        return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }

    private static NumberProvider amountOf(TradeSet set) {
        Object raw = readField(set, "amount");
        return raw instanceof NumberProvider provider ? provider : ConstantValue.exactly(2.0f);
    }

    private static void replaceTradeSet(MappedRegistry<TradeSet> registry, ResourceKey<TradeSet> key, TradeSet value) {
        unregister(registry, key);
        Registry.register(registry, key, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> MappedRegistry<T> mappedRegistry(RegistryAccess access, ResourceKey<? extends Registry<T>> key) {
        return access.lookup(key).orElse(null) instanceof MappedRegistry<T> mapped ? mapped : null;
    }

    // ------------------------------------------------------------------
    // Registry reflection: reloadable registries are frozen after datapack
    // load, so registration/removal needs a temporary unfreeze plus direct
    // internal-map surgery (port of Katton's RegistryMutationUtil semantics).
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

        Map<ResourceKey<T>, Holder.Reference<T>> byKey = (Map<ResourceKey<T>, Holder.Reference<T>>) readField(registry, "byKey");
        Map<Identifier, Holder.Reference<T>> byLocation = (Map<Identifier, Holder.Reference<T>>) readField(registry, "byLocation");
        Map<T, Holder.Reference<T>> byValue = (Map<T, Holder.Reference<T>>) readField(registry, "byValue");
        List<Holder.Reference<T>> byId = (List<Holder.Reference<T>>) readField(registry, "byId");
        Reference2IntMap<T> toId = (Reference2IntMap<T>) readField(registry, "toId");
        Map<ResourceKey<T>, Object> registrationInfos = (Map<ResourceKey<T>, Object>) readField(registry, "registrationInfos");
        if (byKey == null || byLocation == null || byValue == null || byId == null || toId == null || registrationInfos == null) {
            NekoJS.LOGGER.warn("VillagerTrades: cannot access MappedRegistry internals; unregister of {} skipped", key.identifier());
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
            Holder.Reference<T> reference = byId.get(i);
            toId.put(reference.value(), i);
        }
        writeField(holder, "tags", java.util.Set.of());
        return true;
    }

    private static boolean isFrozen(MappedRegistry<?> registry) {
        Object raw = readField(registry, "frozen");
        return raw instanceof Boolean frozen ? frozen : true;
    }

    // ------------------------------------------------------------------
    // Minimal reflective field IO (plain reflection first, sun.misc.Unsafe
    // fallback for final / JPMS-restricted fields — same strategy Katton's
    // ReflectUtil uses on this toolchain).
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
                if (value instanceof Boolean b) {
                    unsafePutBoolean(field, target, b);
                } else {
                    unsafePutObject(field, target, value);
                }
            } catch (Exception ex) {
                NekoJS.LOGGER.warn("VillagerTrades: failed to write field '{}' on {}", name, target.getClass().getName(), ex);
            }
        }
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
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
