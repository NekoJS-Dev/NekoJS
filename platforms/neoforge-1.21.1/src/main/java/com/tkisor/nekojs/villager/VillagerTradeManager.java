package com.tkisor.nekojs.villager;

import com.tkisor.nekojs.NekoJS;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntSet;
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

/**
 * Stages and applies script mutations to villager / wandering trader trades on MC 1.21.1.
 *
 * <p>1.21.1 predates the trade registries: trades live in the static
 * {@link VillagerTrades#TRADES} map ({@code Map<VillagerProfession, Int2ObjectMap<ItemListing[]>>})
 * plus {@link VillagerTrades#WANDERING_TRADER_TRADES}, read by {@code Villager.updateTrades()}
 * each restock. The maps are immutable guava maps, so mutation replaces the static final
 * fields wholesale: the pristine vanilla maps are snapshotted on first touch (eternal
 * baseline), each apply restores the snapshot first, then installs a copy whose touched
 * level arrays are extended with {@link NekoTradeListing} instances. Trailing listings are
 * visible to villagers on their next restock cycle.</p>
 */
public final class VillagerTradeManager {

    private VillagerTradeManager() {}

    /** Trade target: either a profession registry id + villager level, or the wandering trader pools. */
    public static final class PendingTrade {
        public final ResourceLocation professionId; // null => wandering trader
        public final int level;
        public final ResourceLocation wantsId;
        public final int wantsCount;
        public final ResourceLocation additionalWantsId; // nullable
        public final int additionalWantsCount;
        public final ResourceLocation givesId;
        public final int givesCount;
        public final int maxUses;
        public final int xp;
        public final float priceMultiplier;

        public PendingTrade(
                ResourceLocation professionId, int level,
                ResourceLocation wantsId, int wantsCount,
                ResourceLocation additionalWantsId, int additionalWantsCount,
                ResourceLocation givesId, int givesCount,
                int maxUses, int xp, float priceMultiplier
        ) {
            this.professionId = professionId;
            this.level = level;
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

    /** A script-defined 1.21.1 trade listing; offers are built lazily at restock like vanilla listings. */
    public static final class NekoTradeListing implements VillagerTrades.ItemListing {
        private final Item wants;
        private final int wantsCount;
        private final Item additionalWants; // nullable
        private final int additionalWantsCount;
        private final Item gives;
        private final int givesCount;
        private final int maxUses;
        private final int xp;
        private final float priceMultiplier;

        NekoTradeListing(PendingTrade pending, Item wants, Item additionalWants, Item gives) {
            this.wants = wants;
            this.wantsCount = pending.wantsCount;
            this.additionalWants = additionalWants;
            this.additionalWantsCount = pending.additionalWantsCount;
            this.gives = gives;
            this.givesCount = pending.givesCount;
            this.maxUses = pending.maxUses;
            this.xp = pending.xp;
            this.priceMultiplier = pending.priceMultiplier;
        }

        @Override
        public MerchantOffer getOffer(Entity entity, RandomSource random) {
            Optional<ItemCost> additional = additionalWants != null
                    ? Optional.of(new ItemCost(additionalWants, additionalWantsCount))
                    : Optional.empty();
            return new MerchantOffer(
                    new ItemCost(wants, wantsCount),
                    additional,
                    new ItemStack(gives, givesCount),
                    maxUses,
                    xp,
                    priceMultiplier);
        }
    }

    private static final List<PendingTrade> PENDING = new ArrayList<>();
    /** Pristine vanilla maps, captured before the first mutation (static state survives server restarts). */
    private static volatile Map<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> originalTrades;
    private static volatile Int2ObjectMap<VillagerTrades.ItemListing[]> originalWanderingTrades;

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

    /** Restores the pristine vanilla maps and drops staged state (server stopped / world unload). */
    public static void reset() {
        synchronized (PENDING) {
            PENDING.clear();
            if (originalTrades != null) {
                setStaticField(VillagerTrades.class, "TRADES", originalTrades);
            }
            if (originalWanderingTrades != null) {
                setStaticField(VillagerTrades.class, "WANDERING_TRADER_TRADES", originalWanderingTrades);
            }
        }
    }

    /**
     * Flushes staged trades into the static trade maps. Idempotent: restores the vanilla
     * snapshot first, then installs extended copies of only the touched profession/level
     * arrays. Returns true when anything changed.
     */
    public static boolean apply(MinecraftServer server) {
        List<PendingTrade> pending;
        synchronized (PENDING) {
            if (PENDING.isEmpty() && originalTrades == null && originalWanderingTrades == null) {
                return false;
            }
            pending = new ArrayList<>(PENDING);
            PENDING.clear();
        }

        snapshotOriginals();
        restoreOriginals();
        if (pending.isEmpty()) {
            return true; // cleanup was the only work
        }

        Map<VillagerProfession, List<PendingTrade>> byProfession = new LinkedHashMap<>();
        List<PendingTrade> wandering = new ArrayList<>();
        for (PendingTrade t : pending) {
            if (t.professionId == null) {
                wandering.add(t);
            } else if (!BuiltInRegistries.VILLAGER_PROFESSION.containsKey(t.professionId)) {
                NekoJS.LOGGER.warn("VillagerTrades: profession {} is not registered; trade skipped", t.professionId);
            } else {
                VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.get(t.professionId);
                byProfession.computeIfAbsent(profession, k -> new ArrayList<>()).add(t);
            }
        }

        int applied = 0;
        int skipped = 0;
        if (!byProfession.isEmpty()) {
            Map<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> current = originalTrades;
            Map<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> next = new HashMap<>(current);
            for (Map.Entry<VillagerProfession, List<PendingTrade>> entry : byProfession.entrySet()) {
                Int2ObjectMap<VillagerTrades.ItemListing[]> levels = next.get(entry.getKey());
                if (levels == null) {
                    NekoJS.LOGGER.warn("VillagerTrades: profession {} has no trade pools; trades skipped", entry.getKey());
                    skipped += entry.getValue().size();
                    continue;
                }
                Int2ObjectMap<VillagerTrades.ItemListing[]> nextLevels = new Int2ObjectOpenHashMap<>(levels);
                for (PendingTrade t : entry.getValue()) {
                    VillagerTrades.ItemListing[] base = nextLevels.get(t.level);
                    if (base == null) {
                        NekoJS.LOGGER.warn("VillagerTrades: profession {} has no level {} pool; trade skipped", t.professionId, t.level);
                        skipped++;
                        continue;
                    }
                    VillagerTrades.ItemListing listing = buildListing(t);
                    if (listing == null) {
                        skipped++;
                        continue;
                    }
                    VillagerTrades.ItemListing[] extended = new VillagerTrades.ItemListing[base.length + 1];
                    System.arraycopy(base, 0, extended, 0, base.length);
                    extended[base.length] = listing;
                    nextLevels.put(t.level, extended);
                    applied++;
                }
                next.put(entry.getKey(), nextLevels);
            }
            setStaticField(VillagerTrades.class, "TRADES", Map.copyOf(next));
        }

        if (!wandering.isEmpty()) {
            Int2ObjectMap<VillagerTrades.ItemListing[]> next = new Int2ObjectOpenHashMap<>(originalWanderingTrades);
            for (PendingTrade t : wandering) {
                VillagerTrades.ItemListing[] base = next.get(t.level);
                if (base == null) {
                    NekoJS.LOGGER.warn("VillagerTrades: wandering trader has no level {} pool; trade skipped", t.level);
                    skipped++;
                    continue;
                }
                VillagerTrades.ItemListing listing = buildListing(t);
                if (listing == null) {
                    skipped++;
                    continue;
                }
                VillagerTrades.ItemListing[] extended = new VillagerTrades.ItemListing[base.length + 1];
                System.arraycopy(base, 0, extended, 0, base.length);
                extended[base.length] = listing;
                next.put(t.level, extended);
                applied++;
            }
            setStaticField(VillagerTrades.class, "WANDERING_TRADER_TRADES", next);
        }

        if (applied > 0 || skipped > 0) {
            NekoJS.LOGGER.info("VillagerTrades: applied {} trade addition(s) ({} skipped); villagers offer them on next restock",
                    applied, skipped);
        }
        return applied > 0;
    }

    private static VillagerTrades.ItemListing buildListing(PendingTrade t) {
        Item wants = resolveItem(t.wantsId);
        if (wants == null) {
            NekoJS.LOGGER.warn("VillagerTrades: cost item {} is not registered", t.wantsId);
            return null;
        }
        Item additional = t.additionalWantsId != null ? resolveItem(t.additionalWantsId) : null;
        if (t.additionalWantsId != null && additional == null) {
            NekoJS.LOGGER.warn("VillagerTrades: secondary cost item {} is not registered", t.additionalWantsId);
            return null;
        }
        Item gives = resolveItem(t.givesId);
        if (gives == null) {
            NekoJS.LOGGER.warn("VillagerTrades: result item {} is not registered", t.givesId);
            return null;
        }
        return new NekoTradeListing(t, wants, additional, gives);
    }

    private static Item resolveItem(ResourceLocation id) {
        return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }

    private static void snapshotOriginals() {
        if (originalTrades == null) {
            originalTrades = VillagerTrades.TRADES;
        }
        if (originalWanderingTrades == null) {
            originalWanderingTrades = VillagerTrades.WANDERING_TRADER_TRADES;
        }
    }

    private static void restoreOriginals() {
        if (originalTrades != null) {
            setStaticField(VillagerTrades.class, "TRADES", originalTrades);
        }
        if (originalWanderingTrades != null) {
            setStaticField(VillagerTrades.class, "WANDERING_TRADER_TRADES", originalWanderingTrades);
        }
    }

    /** Levels present in the wandering trader pools (for binding validation / docs). */
    public static IntSet wanderingTraderLevels() {
        snapshotOriginals();
        return originalWanderingTrades.keySet();
    }

    // ------------------------------------------------------------------
    // Static final field replacement via sun.misc.Unsafe — the same strategy
    // Katton's ReflectUtil.setStaticFinal uses on this toolchain.
    // ------------------------------------------------------------------

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
