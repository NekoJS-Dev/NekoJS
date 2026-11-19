package com.tkisor.nekojs.core.stage.gate;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.stage.StageReadView;
import com.tkisor.nekojs.api.stage.StageScope;
import com.tkisor.nekojs.api.stage.gate.RegionShape;
import com.tkisor.nekojs.api.stage.gate.StageGateDeclaration;
import com.tkisor.nekojs.api.stage.gate.StageGateDeclaration.BlockGate;
import com.tkisor.nekojs.api.stage.gate.StageGateDeclaration.ContainerGate;
import com.tkisor.nekojs.api.stage.gate.StageGateDeclaration.DimensionGate;
import com.tkisor.nekojs.api.stage.gate.StageGateDeclaration.EffectGate;
import com.tkisor.nekojs.api.stage.gate.StageGateDeclaration.EnchantGate;
import com.tkisor.nekojs.api.stage.gate.StageGateDeclaration.EntityGate;
import com.tkisor.nekojs.api.stage.gate.StageGateDeclaration.ItemGate;
import com.tkisor.nekojs.api.stage.gate.StageGateDeclaration.LootGate;
import com.tkisor.nekojs.api.stage.gate.StageGateDeclaration.RecipeGate;
import com.tkisor.nekojs.api.stage.gate.StageGateDeclaration.RegionGate;
import com.tkisor.nekojs.core.stage.gate.StageRegistryAccess.RecipeDescriptor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Immutable, pre-compiled O(1) stage gate index built from declarative {@link StageGateDeclaration}
 * rules, whitelisted container menu IDs, and inline recipe stage registrations.
 *
 * <p>Rules are deterministically ordered by {@code priority} descending, then declaration order
 * ascending. Higher-priority rules override lower-priority rules for any matching target: only
 * rules at the highest matching priority tier govern whether a target action is permitted.
 */
public final class CompiledStageGateSnapshot {

    /**
     * Decision returned by {@link #filterLootItem} when evaluating loot drops.
     *
     * @param allowed         {@code true} when the loot item may drop unchanged
     * @param replacementItem optional replacement item ID when {@code allowed} is {@code false}
     */
    public record LootDecision(boolean allowed, @Nullable NekoId replacementItem) {
        /** Shared constant allowing a loot item to drop unmodified. */
        public static final LootDecision ALLOW = new LootDecision(true, null);

        /** Creates a blocked loot decision with an optional replacement item. */
        public static LootDecision deny(@Nullable NekoId replacementItem) {
            return new LootDecision(false, replacementItem);
        }
    }

    public record RecipeLock(String stage, @Nullable RecipeGate declaration) {
    }

    record WireItemRule(String stage, StageScope scope, boolean showInViewer, String lockedName) {
    }

    record WireItemEntry(NekoId itemId, List<WireItemRule> winningRules, List<String> requiredStages) {
    }

    record WireRecipeRule(String stage, StageScope scope) {
    }

    record WireRecipeEntry(NekoId recipeId, List<WireRecipeRule> winningHiddenRules) {
        boolean isHidden(StageReadView playerStages, StageReadView serverStages) {
            for (WireRecipeRule rule : winningHiddenRules) {
                if (!isUnlocked(rule.stage(), rule.scope(), playerStages, serverStages)) {
                    return true;
                }
            }
            return false;
        }
    }

    private record IndexedRule<T>(int priority, int order, T rule) {
    }

    private record CompiledRecipeRule(
            int priority,
            int order,
            String stage,
            StageScope scope,
            boolean showInViewer,
            @Nullable RecipeGate declaration
    ) {
    }

    private record RecipeTier(List<CompiledRecipeRule> rules, List<WireRecipeRule> hiddenRules) {
        private static final RecipeTier EMPTY = new RecipeTier(List.of(), List.of());

        @Nullable RecipeLock lock(StageReadView playerStages, StageReadView serverStages) {
            for (var rule : rules) {
                if (!isUnlocked(rule.stage(), rule.scope(), playerStages, serverStages)) {
                    return new RecipeLock(rule.stage(), rule.declaration());
                }
            }
            return null;
        }
    }

    private record IndexedRecipe(@Nullable NekoId type, @Nullable NekoId output, RecipeTier tier) {
    }

    private record RegionCellBounds(int minX, int maxX, int minZ, int maxZ) {
        private static final int WIDTH = 64;

        static @Nullable RegionCellBounds of(RegionShape shape) {
            if (!cellCoordinateFits(shape.minX()) || !cellCoordinateFits(shape.maxX())
                    || !cellCoordinateFits(shape.minZ()) || !cellCoordinateFits(shape.maxZ())) return null;
            return new RegionCellBounds(cell(shape.minX()), cell(shape.maxX()), cell(shape.minZ()), cell(shape.maxZ()));
        }

        long area() {
            long width = (long) maxX - minX + 1;
            long depth = (long) maxZ - minZ + 1;
            return width > 4096 || depth > 4096 ? Long.MAX_VALUE : width * depth;
        }

        static boolean cellCoordinateFits(double coordinate) {
            double value = Math.floor(coordinate / WIDTH);
            return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
        }

        static int cell(double coordinate) {
            return (int) Math.floor(coordinate / WIDTH);
        }

        static long key(int x, int z) {
            return ((long) x << 32) | (z & 0xffffffffL);
        }
    }

    private record RegionBuckets(Map<Long, List<IndexedRule<RegionGate>>> byCell,
                                 List<IndexedRule<RegionGate>> large) {
        static RegionBuckets compile(NekoId dimension, List<IndexedRule<RegionGate>> rules) {
            Map<Long, List<IndexedRule<RegionGate>>> cells = new HashMap<>();
            List<IndexedRule<RegionGate>> large = new ArrayList<>();
            long remaining = 65536;
            for (var rule : rules) {
                var bounds = new ArrayList<RegionCellBounds>();
                long area = 0;
                for (RegionShape shape : rule.rule().shapes()) {
                    if (!dimension.equals(shape.dimension())) continue;
                    var range = RegionCellBounds.of(shape);
                    long available = Math.min(4096 - area, remaining - area);
                    if (range == null || range.area() > available) {
                        area = -1;
                        break;
                    }
                    bounds.add(range);
                    area += range.area();
                }
                if (area < 0) {
                    large.add(rule);
                    continue;
                }
                remaining -= area;
                for (var range : bounds) {
                    for (long x = range.minX(); x <= range.maxX(); x++) {
                        for (long z = range.minZ(); z <= range.maxZ(); z++) {
                            addIndexed(cells, RegionCellBounds.key((int) x, (int) z), rule);
                        }
                    }
                }
            }
            return new RegionBuckets(freezeIndexedMap(cells), List.copyOf(large));
        }

        List<IndexedRule<RegionGate>> at(double x, double z) {
            if (!RegionCellBounds.cellCoordinateFits(x) || !RegionCellBounds.cellCoordinateFits(z)) return List.of();
            return byCell.getOrDefault(RegionCellBounds.key(RegionCellBounds.cell(x), RegionCellBounds.cell(z)), List.of());
        }
    }

    private record CompiledLootRule(
            int priority,
            int order,
            LootGate gate,
            Set<NekoId> expandedItems,
            boolean hasItemFilter
    ) {
    }

    private static final Comparator<IndexedRule<?>> RULE_COMPARATOR = Comparator
            .<IndexedRule<?>>comparingInt(IndexedRule::priority).reversed()
            .thenComparingInt(IndexedRule::order);

    private static final Comparator<CompiledRecipeRule> RECIPE_RULE_COMPARATOR = Comparator
            .comparingInt(CompiledRecipeRule::priority).reversed()
            .thenComparingInt(CompiledRecipeRule::order);

    private static final Comparator<CompiledLootRule> LOOT_RULE_COMPARATOR = Comparator
            .comparingInt(CompiledLootRule::priority).reversed()
            .thenComparingInt(CompiledLootRule::order);

    private static final Comparator<NekoId> NEKO_ID_COMPARATOR = Comparator.comparing(NekoId::toString);

    // Snapshot compilation uses the comparators above even when there are no rules.
    /** Empty gate snapshot where all actions are allowed and nothing is hidden. */
    public static final CompiledStageGateSnapshot EMPTY = compile(
            List.of(),
            Set.of(),
            Map.of(),
            StageRegistryAccess.PERMISSIVE
    );

    private final List<StageGateDeclaration> rawDeclarations;
    private final List<StageGateDeclaration> sortedDeclarations;
    private final Map<NekoId, StageGateDeclaration> declarationsById;
    private final Set<NekoId> whitelistedContainers;
    private final Map<NekoId, Set<Integer>> whitelistedContainerSlots;
    private final Map<NekoId, InlineRecipeStageRegistry.InlineEntry> inlineRecipes;
    private final StageRegistryAccess registries;

    // O(1) item indices
    private final Map<NekoId, List<IndexedRule<ItemGate>>> itemRulesById;
    private final Map<String, List<IndexedRule<ItemGate>>> itemRulesByNamespace;
    private final Set<NekoId> viewerCandidateItems;

    // O(1) block indices
    private final Map<NekoId, List<IndexedRule<BlockGate>>> blockRulesById;
    private final Map<String, List<IndexedRule<BlockGate>>> blockRulesByNamespace;

    // O(1) recipe indices
    private final Map<NekoId, List<CompiledRecipeRule>> recipeRulesById;
    private final Map<String, List<CompiledRecipeRule>> recipeRulesByNamespace;
    private final Map<NekoId, List<CompiledRecipeRule>> recipeRulesByType;
    private final Map<NekoId, List<CompiledRecipeRule>> recipeRulesByOutputItem;
    private final Map<NekoId, IndexedRecipe> indexedRecipes;
    private final List<WireRecipeEntry> clientRecipeEntries;

    // O(1) container, dimension, region, entity, effect, enchant, and loot indices
    private final Map<NekoId, List<IndexedRule<ContainerGate>>> containerRulesByMenu;
    private final Map<NekoId, List<IndexedRule<DimensionGate>>> dimensionRulesById;
    private final Map<NekoId, RegionBuckets> regionRulesByDimension;
    private final Map<NekoId, List<IndexedRule<EntityGate>>> entityRulesById;
    private final Map<String, List<IndexedRule<EntityGate>>> entityRulesByNamespace;
    private final Map<NekoId, List<IndexedRule<EffectGate>>> effectRulesById;
    private final Map<NekoId, List<IndexedRule<EnchantGate>>> enchantRulesById;
    private final Map<NekoId, List<CompiledLootRule>> lootRulesByItem;
    private final List<CompiledLootRule> wildcardItemLootRules;

    /**
     * Compiles an immutable O(1) stage gate lookup snapshot.
     */
    public CompiledStageGateSnapshot(
            List<StageGateDeclaration> declarations,
            Set<NekoId> whitelistedContainers,
            Map<NekoId, InlineRecipeStageRegistry.InlineEntry> inlineRecipes,
            StageRegistryAccess registries
    ) {
        this(declarations, whitelistedContainers, Map.of(), inlineRecipes, registries);
    }

    public CompiledStageGateSnapshot(
            List<StageGateDeclaration> declarations,
            Set<NekoId> whitelistedContainers,
            Map<NekoId, Set<Integer>> whitelistedContainerSlots,
            Map<NekoId, InlineRecipeStageRegistry.InlineEntry> inlineRecipes,
            StageRegistryAccess registries
    ) {
        this.rawDeclarations = declarations == null ? List.of() : List.copyOf(declarations);
        this.whitelistedContainers = whitelistedContainers == null || whitelistedContainers.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(whitelistedContainers));
        if (whitelistedContainerSlots == null || whitelistedContainerSlots.isEmpty()) {
            this.whitelistedContainerSlots = Map.of();
        } else {
            Map<NekoId, Set<Integer>> copied = new LinkedHashMap<>();
            whitelistedContainerSlots.forEach((id, slots) -> copied.put(id,
                    Collections.unmodifiableSet(new LinkedHashSet<>(slots))));
            this.whitelistedContainerSlots = Collections.unmodifiableMap(copied);
        }
        this.inlineRecipes = inlineRecipes == null || inlineRecipes.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(inlineRecipes));
        this.registries = registries == null ? StageRegistryAccess.PERMISSIVE : registries;

        Map<NekoId, StageGateDeclaration> byRuleId = new LinkedHashMap<>(this.rawDeclarations.size());
        List<IndexedRule<StageGateDeclaration>> allIndexed = new ArrayList<>(this.rawDeclarations.size());

        Map<NekoId, List<IndexedRule<ItemGate>>> itemById = new HashMap<>();
        Map<String, List<IndexedRule<ItemGate>>> itemByNs = new HashMap<>();
        Set<NekoId> viewerItems = new LinkedHashSet<>();

        Map<NekoId, List<IndexedRule<BlockGate>>> blockById = new HashMap<>();
        Map<String, List<IndexedRule<BlockGate>>> blockByNs = new HashMap<>();

        Map<NekoId, List<CompiledRecipeRule>> recById = new HashMap<>();
        Map<String, List<CompiledRecipeRule>> recByNs = new HashMap<>();
        Map<NekoId, List<CompiledRecipeRule>> recByType = new HashMap<>();
        Map<NekoId, List<CompiledRecipeRule>> recByOut = new HashMap<>();

        Map<NekoId, List<IndexedRule<ContainerGate>>> contByMenu = new HashMap<>();
        Map<NekoId, List<IndexedRule<DimensionGate>>> dimById = new HashMap<>();
        Map<NekoId, List<IndexedRule<RegionGate>>> regByDim = new HashMap<>();
        Map<NekoId, List<IndexedRule<EntityGate>>> entById = new HashMap<>();
        Map<String, List<IndexedRule<EntityGate>>> entByNs = new HashMap<>();
        Map<NekoId, List<IndexedRule<EffectGate>>> effById = new HashMap<>();
        Map<NekoId, List<IndexedRule<EnchantGate>>> enchById = new HashMap<>();
        Map<NekoId, List<CompiledLootRule>> lootByItem = new HashMap<>();
        List<CompiledLootRule> wildcardLoot = new ArrayList<>();

        for (int order = 0; order < this.rawDeclarations.size(); order++) {
            StageGateDeclaration decl = Objects.requireNonNull(this.rawDeclarations.get(order), "declaration");
            byRuleId.put(decl.id(), decl);
            allIndexed.add(new IndexedRule<>(decl.priority(), order, decl));

            switch (decl) {
                case ItemGate itemGate -> indexItemGate(itemGate, order, itemById, itemByNs, viewerItems);
                case BlockGate blockGate -> indexBlockGate(blockGate, order, blockById, blockByNs);
                case RecipeGate recipeGate ->
                        indexRecipeGate(recipeGate, order, recById, recByNs, recByType, recByOut);
                case ContainerGate containerGate -> {
                    IndexedRule<ContainerGate> entry = new IndexedRule<>(containerGate.priority(), order, containerGate);
                    for (NekoId menuId : containerGate.menus()) {
                        addIndexed(contByMenu, menuId, entry);
                    }
                }
                case DimensionGate dimensionGate -> {
                    IndexedRule<DimensionGate> entry = new IndexedRule<>(dimensionGate.priority(), order, dimensionGate);
                    for (NekoId dimId : dimensionGate.dimensions()) {
                        addIndexed(dimById, dimId, entry);
                    }
                }
                case RegionGate regionGate -> {
                    IndexedRule<RegionGate> entry = new IndexedRule<>(regionGate.priority(), order, regionGate);
                    Set<NekoId> seenDims = new LinkedHashSet<>();
                    for (RegionShape shape : regionGate.shapes()) {
                        if (seenDims.add(shape.dimension())) {
                            addIndexed(regByDim, shape.dimension(), entry);
                        }
                    }
                }
                case EntityGate entityGate -> indexEntityGate(entityGate, order, entById, entByNs);
                case EffectGate effectGate -> {
                    IndexedRule<EffectGate> entry = new IndexedRule<>(effectGate.priority(), order, effectGate);
                    for (NekoId effectId : effectGate.effects()) {
                        addIndexed(effById, effectId, entry);
                    }
                }
                case EnchantGate enchantGate -> {
                    IndexedRule<EnchantGate> entry = new IndexedRule<>(enchantGate.priority(), order, enchantGate);
                    for (NekoId enchantId : enchantGate.enchants()) {
                        addIndexed(enchById, enchantId, entry);
                    }
                }
                case LootGate lootGate -> indexLootGate(lootGate, order, lootByItem, wildcardLoot);
            }
        }

        // Merge inline recipe stage entries after declarative rules at default priority 0.
        int inlineOrder = this.rawDeclarations.size();
        for (InlineRecipeStageRegistry.InlineEntry inline : this.inlineRecipes.values()) {
            CompiledRecipeRule rule = new CompiledRecipeRule(
                    0,
                    inlineOrder++,
                    inline.stage(),
                    inline.scope(),
                    false,
                    null
            );
            recById.computeIfAbsent(inline.recipeId(), k -> new ArrayList<>()).add(rule);
        }

        allIndexed.sort(RULE_COMPARATOR);
        List<StageGateDeclaration> sortedDecls = new ArrayList<>(allIndexed.size());
        for (IndexedRule<StageGateDeclaration> entry : allIndexed) {
            sortedDecls.add(entry.rule());
        }

        this.sortedDeclarations = Collections.unmodifiableList(sortedDecls);
        this.declarationsById = Collections.unmodifiableMap(byRuleId);

        this.itemRulesById = freezeSelectors(itemById, itemByNs);
        this.itemRulesByNamespace = freezeIndexedMap(itemByNs);
        this.viewerCandidateItems = Collections.unmodifiableSet(viewerItems);

        this.blockRulesById = freezeSelectors(blockById, blockByNs);
        this.blockRulesByNamespace = freezeIndexedMap(blockByNs);

        this.recipeRulesById = freezeRecipeMap(recById);
        this.recipeRulesByNamespace = freezeRecipeMap(recByNs);
        this.recipeRulesByType = freezeRecipeMap(recByType);
        this.recipeRulesByOutputItem = freezeRecipeMap(recByOut);
        this.indexedRecipes = indexRecipes();
        this.clientRecipeEntries = indexedRecipes.entrySet().stream()
                .filter(entry -> !entry.getValue().tier().hiddenRules().isEmpty())
                .sorted(Map.Entry.comparingByKey(NEKO_ID_COMPARATOR))
                .map(entry -> new WireRecipeEntry(entry.getKey(), entry.getValue().tier().hiddenRules()))
                .toList();

        this.containerRulesByMenu = freezeIndexedMap(contByMenu);
        this.dimensionRulesById = freezeIndexedMap(dimById);
        Map<NekoId, RegionBuckets> regionBuckets = new HashMap<>();
        freezeIndexedMap(regByDim).forEach((dimension, rules) -> regionBuckets.put(dimension, RegionBuckets.compile(dimension, rules)));
        this.regionRulesByDimension = Map.copyOf(regionBuckets);
        this.entityRulesById = freezeSelectors(entById, entByNs);
        this.entityRulesByNamespace = freezeIndexedMap(entByNs);
        this.effectRulesById = freezeIndexedMap(effById);
        this.enchantRulesById = freezeIndexedMap(enchById);

        wildcardLoot.sort(LOOT_RULE_COMPARATOR);
        this.wildcardItemLootRules = List.copyOf(wildcardLoot);
        this.lootRulesByItem = freezeLootMap(lootByItem);
    }

    /** Static factory building an immutable {@link CompiledStageGateSnapshot}. */
    public static CompiledStageGateSnapshot compile(
            List<StageGateDeclaration> declarations,
            Set<NekoId> whitelistedContainers,
            Map<NekoId, InlineRecipeStageRegistry.InlineEntry> inlineRecipes,
            StageRegistryAccess registries
    ) {
        return new CompiledStageGateSnapshot(declarations, whitelistedContainers, inlineRecipes, registries);
    }

    public static CompiledStageGateSnapshot compile(
            List<StageGateDeclaration> declarations, Set<NekoId> whitelistedContainers,
            Map<NekoId, Set<Integer>> whitelistedContainerSlots,
            Map<NekoId, InlineRecipeStageRegistry.InlineEntry> inlineRecipes, StageRegistryAccess registries) {
        return new CompiledStageGateSnapshot(declarations, whitelistedContainers, whitelistedContainerSlots, inlineRecipes, registries);
    }

    /**
     * Returns a new immutable {@link CompiledStageGateSnapshot} preserving this snapshot's declarative
     * rules, whitelisted containers, and registry access while merging updated inline recipe gates.
     */
    public CompiledStageGateSnapshot withInlineRecipes(
            Map<NekoId, InlineRecipeStageRegistry.InlineEntry> updatedInlineRecipes
    ) {
        return withInlineRecipes(updatedInlineRecipes, this.registries);
    }

    public CompiledStageGateSnapshot withInlineRecipes(
            Map<NekoId, InlineRecipeStageRegistry.InlineEntry> updatedInlineRecipes, StageRegistryAccess registryAccess
    ) {
        return new CompiledStageGateSnapshot(
                this.rawDeclarations,
                this.whitelistedContainers,
                this.whitelistedContainerSlots,
                updatedInlineRecipes,
                registryAccess
        );
    }

    boolean hasInlineRecipeEntries(Map<NekoId, InlineRecipeStageRegistry.InlineEntry> entries) {
        return inlineRecipes.equals(entries);
    }

    public boolean isContainerSlotWhitelisted(NekoId menuId, int slot) {
        Set<Integer> slots = whitelistedContainerSlots.get(menuId);
        return slots != null && slots.contains(slot);
    }

    /** Returns an unmodifiable set of whitelisted container menu IDs. */
    public Set<NekoId> whitelistedContainers() {
        return whitelistedContainers;
    }

    /** Returns an unmodifiable map of inline recipe stage entries merged into this snapshot. */
    public Map<NekoId, InlineRecipeStageRegistry.InlineEntry> inlineRecipes() {
        return inlineRecipes;
    }

    // ---- Item queries ----

    /** Checks whether right-click using {@code itemId} is allowed. */
    public boolean canUseItem(NekoId itemId, StageReadView playerStages, StageReadView serverStages) {
        return evaluateItemAction(itemId, playerStages, serverStages, ItemGate::allowUse);
    }

    /** Checks whether picking up {@code itemId} from the world is allowed. */
    public boolean canPickUpItem(NekoId itemId, StageReadView playerStages, StageReadView serverStages) {
        return evaluateItemAction(itemId, playerStages, serverStages, ItemGate::allowPickup);
    }

    /** Checks whether dropping {@code itemId} is allowed. */
    public boolean canDropItem(NekoId itemId, StageReadView playerStages, StageReadView serverStages) {
        return evaluateItemAction(itemId, playerStages, serverStages, ItemGate::allowDrop);
    }

    /** Checks whether equipping {@code itemId} in armor or offhand slots is allowed. */
    public boolean canEquipItem(NekoId itemId, StageReadView playerStages, StageReadView serverStages) {
        return evaluateItemAction(itemId, playerStages, serverStages, ItemGate::allowEquip);
    }

    /** Checks whether attacking with {@code itemId} is allowed. */
    public boolean canAttackWithItem(NekoId itemId, StageReadView playerStages, StageReadView serverStages) {
        return evaluateItemAction(itemId, playerStages, serverStages, ItemGate::allowAttack);
    }

    /** Checks whether holding {@code itemId} in player inventory slots is allowed. */
    public boolean canStoreInInventory(NekoId itemId, StageReadView playerStages, StageReadView serverStages) {
        return evaluateItemAction(itemId, playerStages, serverStages, ItemGate::allowInventoryStorage);
    }

    /**
     * Checks whether storing or transferring {@code itemId} inside {@code menuId} is allowed.
     * Whitelisted container menu types always permit storage regardless of stage state.
     */
    public boolean canStoreInContainer(
            NekoId itemId,
            @Nullable NekoId menuId,
            StageReadView playerStages,
            StageReadView serverStages
    ) {
        if (menuId != null && whitelistedContainers.contains(menuId)) {
            return true;
        }
        return evaluateItemAction(itemId, playerStages, serverStages, ItemGate::allowContainerStorage);
    }

    /** Checks whether {@code itemId} should be hidden in client recipe viewers. */
    public boolean isItemHiddenInViewer(NekoId itemId, StageReadView playerStages, StageReadView serverStages) {
        return !evaluateItemAction(itemId, playerStages, serverStages, ItemGate::showInViewer);
    }

    /**
     * Returns the primary winning {@link ItemGate} currently locking {@code itemId}, or {@code null}
     * if {@code itemId} is unlocked for the given stage views.
     */
    public @Nullable ItemGate lockedItemRule(
            NekoId itemId,
            StageReadView playerStages,
            StageReadView serverStages
    ) {
        if (itemId == null) {
            return null;
        }
        List<IndexedRule<ItemGate>> rules = selectorRules(itemId, itemRulesById, itemRulesByNamespace);
        if (isEmpty(rules)) {
            return null;
        }
        var winner = firstLockedAtPriority(rules, rules.getFirst().priority(), playerStages, serverStages);
        return winner == null ? null : winner.rule();
    }

    // ---- Block queries ----

    /** Checks whether breaking {@code blockId} (with optional {@code cropAge}) is allowed. */
    public boolean canBreakBlock(
            NekoId blockId,
            @Nullable Integer cropAge,
            StageReadView playerStages,
            StageReadView serverStages
    ) {
        return evaluateBlockAction(blockId, cropAge, playerStages, serverStages, BlockGate::allowBreak);
    }

    /** Checks whether breaking {@code blockId} (non-crop) is allowed. */
    public boolean canBreakBlock(NekoId blockId, StageReadView playerStages, StageReadView serverStages) {
        return canBreakBlock(blockId, null, playerStages, serverStages);
    }

    /** Checks whether placing {@code blockId} is allowed. */
    public boolean canPlaceBlock(NekoId blockId, StageReadView playerStages, StageReadView serverStages) {
        return evaluateBlockAction(blockId, 0, playerStages, serverStages, BlockGate::allowPlace);
    }

    /** Checks whether right-click interacting with {@code blockId} (with optional {@code cropAge}) is allowed. */
    public boolean canInteractBlock(
            NekoId blockId,
            @Nullable Integer cropAge,
            StageReadView playerStages,
            StageReadView serverStages
    ) {
        return evaluateBlockAction(blockId, cropAge, playerStages, serverStages, BlockGate::allowInteract);
    }

    /** Checks whether right-click interacting with {@code blockId} (non-crop) is allowed. */
    public boolean canInteractBlock(NekoId blockId, StageReadView playerStages, StageReadView serverStages) {
        return canInteractBlock(blockId, null, playerStages, serverStages);
    }

    /** Checks whether tool state modification (stripping, tilling, flattening) on {@code blockId} is allowed. */
    public boolean canToolModifyBlock(NekoId blockId, StageReadView playerStages, StageReadView serverStages) {
        return evaluateBlockAction(blockId, null, playerStages, serverStages, BlockGate::allowToolModify);
    }

    /** Returns the winning locked {@link BlockGate} for {@code blockId} and {@code cropAge}, or {@code null}. */
    public @Nullable BlockGate lockedBlockRule(
            NekoId blockId,
            @Nullable Integer cropAge,
            StageReadView playerStages,
            StageReadView serverStages
    ) {
        if (blockId == null) {
            return null;
        }
        List<IndexedRule<BlockGate>> rules = selectorRules(blockId, blockRulesById, blockRulesByNamespace);
        if (isEmpty(rules)) {
            return null;
        }
        Integer topPriority = maxBlockPriority(rules, null, cropAge);
        if (topPriority == null) return null;
        var winner = firstLockedBlockAtPriority(rules, topPriority, cropAge, playerStages, serverStages);
        return winner == null ? null : winner.rule();
    }

    // ---- Recipe & viewer queries ----

    /**
     * Checks whether assembling or processing {@code recipeId} (with optional {@code recipeTypeId}
     * and {@code outputItemId}) is allowed.
     */
    public boolean canAssembleRecipe(
            NekoId recipeId,
            @Nullable NekoId recipeTypeId,
            @Nullable NekoId outputItemId,
            StageReadView playerStages,
            StageReadView serverStages
    ) {
        return lockedRecipeRule(recipeId, recipeTypeId, outputItemId, playerStages, serverStages) == null;
    }

    public @Nullable RecipeLock lockedRecipeRule(
            @Nullable NekoId recipeId, @Nullable NekoId recipeTypeId, @Nullable NekoId outputItemId,
            StageReadView playerStages, StageReadView serverStages) {
        if (recipeId == null) return null;
        IndexedRecipe indexed = indexedRecipes.get(recipeId);
        if (indexed != null && (recipeRulesByType.isEmpty() || recipeTypeId == null || recipeTypeId.equals(indexed.type()))
                && (recipeRulesByOutputItem.isEmpty() || outputItemId == null || outputItemId.equals(indexed.output()))) {
            return indexed.tier().lock(playerStages, serverStages);
        }
        return lockDynamicRecipe(recipeId, recipeTypeId == null && indexed != null ? indexed.type() : recipeTypeId,
                outputItemId == null && indexed != null ? indexed.output() : outputItemId, playerStages, serverStages);
    }

    /**
     * Returns all explicit recipe IDs currently hidden from recipe viewers for the given stage state.
     */
    public Set<NekoId> hiddenRecipeIds(StageReadView playerStages, StageReadView serverStages) {
        Set<NekoId> hidden = new LinkedHashSet<>();
        for (WireRecipeEntry entry : exportClientRecipeEntries()) {
            if (entry.isHidden(playerStages, serverStages)) {
                hidden.add(entry.recipeId());
            }
        }
        return hidden.isEmpty() ? Set.of() : Collections.unmodifiableSet(hidden);
    }

    /**
     * Returns all known item IDs currently hidden from recipe viewers for the given stage state.
     */
    public Set<NekoId> hiddenViewerItems(StageReadView playerStages, StageReadView serverStages) {
        if (viewerCandidateItems.isEmpty()) {
            return Set.of();
        }
        Set<NekoId> hidden = new LinkedHashSet<>();
        for (NekoId itemId : viewerCandidateItems) {
            if (isItemHiddenInViewer(itemId, playerStages, serverStages)) {
                hidden.add(itemId);
            }
        }
        return hidden.isEmpty() ? Set.of() : Collections.unmodifiableSet(hidden);
    }

    // ---- Container & dimension queries ----

    /** Checks whether opening container menu type {@code menuId} is allowed. */
    public boolean canOpenContainer(NekoId menuId, StageReadView playerStages, StageReadView serverStages) {
        if (menuId == null) {
            return true;
        }
        List<IndexedRule<ContainerGate>> rules = containerRulesByMenu.get(menuId);
        if (isEmpty(rules)) {
            return true;
        }
        int topPriority = rules.get(0).priority();
        return firstLockedAtPriority(rules, topPriority, playerStages, serverStages) == null;
    }

    /** Returns the winning locked {@link ContainerGate} for {@code menuId}, or {@code null}. */
    public @Nullable ContainerGate lockedContainerRule(
            NekoId menuId,
            StageReadView playerStages,
            StageReadView serverStages
    ) {
        if (menuId == null) {
            return null;
        }
        List<IndexedRule<ContainerGate>> rules = containerRulesByMenu.get(menuId);
        if (isEmpty(rules)) {
            return null;
        }
        IndexedRule<ContainerGate> locked = firstLockedAtPriority(
                rules, rules.get(0).priority(), playerStages, serverStages);
        return locked == null ? null : locked.rule();
    }

    /** Checks whether entering or remaining in {@code dimensionId} is allowed. */
    public boolean canEnterDimension(NekoId dimensionId, StageReadView playerStages, StageReadView serverStages) {
        if (dimensionId == null) {
            return true;
        }
        List<IndexedRule<DimensionGate>> rules = dimensionRulesById.get(dimensionId);
        if (isEmpty(rules)) {
            return true;
        }
        int topPriority = rules.get(0).priority();
        return firstLockedAtPriority(rules, topPriority, playerStages, serverStages) == null;
    }

    /** Returns the winning locked {@link DimensionGate} for {@code dimensionId}, or {@code null}. */
    public @Nullable DimensionGate lockedDimensionRule(
            NekoId dimensionId,
            StageReadView playerStages,
            StageReadView serverStages
    ) {
        if (dimensionId == null) {
            return null;
        }
        List<IndexedRule<DimensionGate>> rules = dimensionRulesById.get(dimensionId);
        if (isEmpty(rules)) {
            return null;
        }
        IndexedRule<DimensionGate> locked = firstLockedAtPriority(
                rules, rules.get(0).priority(), playerStages, serverStages);
        return locked == null ? null : locked.rule();
    }

    // ---- Region queries ----

    public boolean hasRegionRules(NekoId dimension) {
        return dimension != null && regionRulesByDimension.containsKey(dimension);
    }

    /** Checks whether entering {@code (dim, x, y, z)} is allowed. */
    public boolean canEnterRegion(
            NekoId dim,
            double x,
            double y,
            double z,
            StageReadView playerStages,
            StageReadView serverStages
    ) {
        return evaluateRegionAction(dim, x, y, z, playerStages, serverStages, RegionGate::allowEnter);
    }

    /** Checks whether breaking blocks at {@code (dim, x, y, z)} is allowed. */
    public boolean canBreakInRegion(
            NekoId dim,
            double x,
            double y,
            double z,
            StageReadView playerStages,
            StageReadView serverStages
    ) {
        return evaluateRegionAction(dim, x, y, z, playerStages, serverStages, RegionGate::allowBreak);
    }

    /** Checks whether placing blocks at {@code (dim, x, y, z)} is allowed. */
    public boolean canPlaceInRegion(
            NekoId dim,
            double x,
            double y,
            double z,
            StageReadView playerStages,
            StageReadView serverStages
    ) {
        return evaluateRegionAction(dim, x, y, z, playerStages, serverStages, RegionGate::allowPlace);
    }

    /** Checks whether interacting at {@code (dim, x, y, z)} is allowed. */
    public boolean canInteractInRegion(
            NekoId dim,
            double x,
            double y,
            double z,
            StageReadView playerStages,
            StageReadView serverStages
    ) {
        return evaluateRegionAction(dim, x, y, z, playerStages, serverStages, RegionGate::allowInteract);
    }

    /** Returns the winning locked {@link RegionGate} containing {@code (dim, x, y, z)}, or {@code null}. */
    public @Nullable RegionGate lockedRegionRule(
            NekoId dim,
            double x,
            double y,
            double z,
            StageReadView playerStages,
            StageReadView serverStages
    ) {
        return deniedRegionRule(dim, x, y, z, playerStages, serverStages, null);
    }

    // ---- Entity, effect, enchant, and loot queries ----

    /** Checks whether attacking an entity of type {@code entityTypeId} is allowed. */
    public boolean canAttackEntity(NekoId entityTypeId, StageReadView playerStages, StageReadView serverStages) {
        return evaluateEntityAction(entityTypeId, playerStages, serverStages, EntityGate::allowAttack);
    }

    /** Checks whether right-click interacting with an entity of type {@code entityTypeId} is allowed. */
    public boolean canInteractEntity(NekoId entityTypeId, StageReadView playerStages, StageReadView serverStages) {
        return evaluateEntityAction(entityTypeId, playerStages, serverStages, EntityGate::allowInteract);
    }

    /** Checks whether mounting/riding an entity of type {@code entityTypeId} is allowed. */
    public boolean canMountEntity(NekoId entityTypeId, StageReadView playerStages, StageReadView serverStages) {
        return evaluateEntityAction(entityTypeId, playerStages, serverStages, EntityGate::allowMount);
    }

    /** Returns the winning locked {@link EntityGate} for {@code entityTypeId}, or {@code null}. */
    public @Nullable EntityGate lockedEntityRule(
            NekoId entityTypeId,
            StageReadView playerStages,
            StageReadView serverStages
    ) {
        if (entityTypeId == null) {
            return null;
        }
        List<IndexedRule<EntityGate>> rules = selectorRules(entityTypeId, entityRulesById, entityRulesByNamespace);
        if (isEmpty(rules)) {
            return null;
        }
        var winner = firstLockedAtPriority(rules, rules.getFirst().priority(), playerStages, serverStages);
        return winner == null ? null : winner.rule();
    }

    /** Checks whether applying or retaining mob effect {@code effectId} is allowed. */
    public boolean canApplyEffect(NekoId effectId, StageReadView playerStages, StageReadView serverStages) {
        return lockedEffectRule(effectId, playerStages, serverStages) == null;
    }

    public @Nullable EffectGate lockedEffectRule(NekoId effectId, StageReadView playerStages, StageReadView serverStages) {
        if (effectId == null) return null;
        List<IndexedRule<EffectGate>> rules = effectRulesById.get(effectId);
        if (isEmpty(rules)) return null;
        var winner = firstLockedAtPriority(rules, rules.getFirst().priority(), playerStages, serverStages);
        return winner == null ? null : winner.rule();
    }

    /** Checks whether using or applying enchantment {@code enchantId} at {@code level} is allowed. */
    public boolean canUseEnchantment(
            NekoId enchantId,
            int level,
            StageReadView playerStages,
            StageReadView serverStages
    ) {
        return lockedEnchantmentRule(enchantId, level, playerStages, serverStages) == null;
    }

    public @Nullable EnchantGate lockedEnchantmentRule(
            NekoId enchantId, int level, StageReadView playerStages, StageReadView serverStages) {
        if (enchantId == null || level <= 0) {
            return null;
        }
        List<IndexedRule<EnchantGate>> rules = enchantRulesById.get(enchantId);
        if (isEmpty(rules)) {
            return null;
        }
        Integer topPriority = null;
        for (IndexedRule<EnchantGate> entry : rules) {
            if (topPriority != null && entry.priority() < topPriority) {
                break;
            }
            if (level >= entry.rule().minLevel()) {
                if (topPriority == null) {
                    topPriority = entry.priority();
                }
                if (!isUnlocked(entry.rule().stage(), entry.rule().scope(), playerStages, serverStages)) {
                    return entry.rule();
                }
            }
        }
        return null;
    }

    /**
     * Evaluates whether dropped loot item {@code itemId} (originating from optional {@code lootTableId}
     * and {@code sourceEntityTypeId}) is allowed, removed, or replaced.
     */
    public LootDecision filterLootItem(
            NekoId itemId,
            @Nullable NekoId lootTableId,
            @Nullable NekoId sourceEntityTypeId,
            StageReadView playerStages,
            StageReadView serverStages
    ) {
        LootGate locked = lockedLootRule(itemId, lootTableId, sourceEntityTypeId, playerStages, serverStages);
        return locked == null ? LootDecision.ALLOW : LootDecision.deny(locked.replacementItem());
    }

    public @Nullable LootGate lockedLootRule(
            NekoId itemId, @Nullable NekoId lootTableId, @Nullable NekoId sourceEntityTypeId,
            StageReadView playerStages, StageReadView serverStages) {
        if (itemId == null) return null;
        List<CompiledLootRule> byItem = lootRulesByItem.get(itemId);
        if (isEmpty(byItem) && wildcardItemLootRules.isEmpty()) return null;

        Integer topPriority = maxLootPriority(byItem, wildcardItemLootRules, lootTableId, sourceEntityTypeId);
        if (topPriority == null) return null;

        CompiledLootRule itemLocked = firstLockedLootAtPriority(
                byItem, topPriority, lootTableId, sourceEntityTypeId, playerStages, serverStages);
        CompiledLootRule wildcardLocked = firstLockedLootAtPriority(
                wildcardItemLootRules, topPriority, lootTableId, sourceEntityTypeId, playerStages, serverStages);

        CompiledLootRule winner;
        if (itemLocked == null) {
            winner = wildcardLocked;
        } else if (wildcardLocked == null) {
            winner = itemLocked;
        } else {
            winner = itemLocked.order() <= wildcardLocked.order() ? itemLocked : wildcardLocked;
        }

        return winner == null ? null : winner.gate();
    }

    // ---- Inspection helpers (/nekojs stages hand & /nekojs stages inspect) ----

    /**
     * Returns all {@link ItemGate} rules matching {@code itemId}, sorted by {@code priority}
     * descending then declaration order ascending.
     */
    public List<ItemGate> matchingItemRules(NekoId itemId) {
        if (itemId == null) {
            return List.of();
        }
        List<IndexedRule<ItemGate>> merged = selectorRules(itemId, itemRulesById, itemRulesByNamespace);
        if (isEmpty(merged)) {
            return List.of();
        }
        List<ItemGate> out = new ArrayList<>(merged.size());
        for (IndexedRule<ItemGate> entry : merged) {
            out.add(entry.rule());
        }
        return Collections.unmodifiableList(out);
    }

    /** Returns all compiled declarations sorted by {@code priority} descending, then declaration order. */
    public List<StageGateDeclaration> allDeclarations() {
        return sortedDeclarations;
    }

    /** Finds a declaration by its unique rule {@code id}, or {@code null} if absent. */
    public @Nullable StageGateDeclaration findDeclaration(NekoId ruleId) {
        if (ruleId == null) {
            return null;
        }
        return declarationsById.get(ruleId);
    }

    /**
     * Returns the distinct stage names required by the winning-priority item and output-recipe rules
     * matching {@code itemId}, preserving deterministic priority and declaration order.
     */
    public List<String> requiredStagesForItem(NekoId itemId) {
        if (itemId == null) {
            return List.of();
        }
        Set<String> stages = new LinkedHashSet<>();

        List<IndexedRule<ItemGate>> mergedItems = selectorRules(itemId, itemRulesById, itemRulesByNamespace);
        if (!isEmpty(mergedItems)) {
            int topItemPriority = mergedItems.get(0).priority();
            for (IndexedRule<ItemGate> entry : mergedItems) {
                if (entry.priority() < topItemPriority) {
                    break;
                }
                stages.add(entry.rule().stage());
            }
        }

        List<CompiledRecipeRule> outputRules = recipeRulesByOutputItem.get(itemId);
        if (!isEmpty(outputRules)) {
            int topRecipePriority = outputRules.get(0).priority();
            for (CompiledRecipeRule rule : outputRules) {
                if (rule.priority() < topRecipePriority) {
                    break;
                }
                stages.add(rule.stage());
            }
        }

        return stages.isEmpty() ? List.of() : List.copyOf(stages);
    }

    // ---- Package-private wire summary exporters for StageWireProtocol ----

    List<WireItemEntry> exportClientItemEntries() {
        Set<NekoId> allItems = new TreeSet<>(NEKO_ID_COMPARATOR);
        allItems.addAll(itemRulesById.keySet());
        allItems.addAll(recipeRulesByOutputItem.keySet());
        if (allItems.isEmpty()) {
            return List.of();
        }

        List<WireItemEntry> entries = new ArrayList<>(allItems.size());
        for (NekoId itemId : allItems) {
            List<ItemGate> matching = matchingItemRules(itemId);
            List<WireItemRule> winningRules = new ArrayList<>();
            if (!matching.isEmpty()) {
                int topPriority = matching.get(0).priority();
                for (ItemGate gate : matching) {
                    if (gate.priority() < topPriority) {
                        break;
                    }
                    if (!gate.showInViewer() || !gate.lockedName().isEmpty()) {
                        winningRules.add(new WireItemRule(
                                gate.stage(),
                                gate.scope(),
                                gate.showInViewer(),
                                gate.lockedName()
                        ));
                    }
                }
            }
            List<String> required = requiredStagesForItem(itemId);
            if (!winningRules.isEmpty() || !required.isEmpty()) {
                entries.add(new WireItemEntry(itemId, List.copyOf(winningRules), required));
            }
        }
        return List.copyOf(entries);
    }

    List<WireRecipeEntry> exportClientRecipeEntries() {
        return clientRecipeEntries;
    }

    private Map<NekoId, IndexedRecipe> indexRecipes() {
        boolean broadSelectors = !recipeRulesByNamespace.isEmpty() || !recipeRulesByType.isEmpty()
                || !recipeRulesByOutputItem.isEmpty();
        if (!broadSelectors && recipeRulesById.isEmpty()) return Map.of();
        Map<NekoId, IndexedRecipe> recipes = new HashMap<>();
        for (RecipeDescriptor descriptor : registries.recipeDescriptors()) {
            NekoId id = descriptor.recipeId();
            if (broadSelectors || recipeRulesById.containsKey(id)) {
                NekoId type = descriptor.recipeTypeId();
                NekoId output = descriptor.outputItemId();
                recipes.put(id, new IndexedRecipe(type, output, compileRecipeTier(id, type, output)));
            }
        }
        for (NekoId id : recipeRulesById.keySet()) {
            recipes.computeIfAbsent(id, key -> new IndexedRecipe(null, null, compileRecipeTier(key, null, null)));
        }
        return Collections.unmodifiableMap(recipes);
    }

    // ---- Internal indexing helpers ----

    private void indexItemGate(
            ItemGate gate,
            int order,
            Map<NekoId, List<IndexedRule<ItemGate>>> itemById,
            Map<String, List<IndexedRule<ItemGate>>> itemByNs,
            Set<NekoId> viewerItems
    ) {
        IndexedRule<ItemGate> entry = new IndexedRule<>(gate.priority(), order, gate);
        Set<NekoId> expandedIds = new LinkedHashSet<>(gate.items());
        for (NekoId tagId : gate.tags()) {
            Set<NekoId> tagMembers = registries.expandItemTag(tagId);
            if (tagMembers != null) {
                expandedIds.addAll(tagMembers);
            }
        }
        for (String ns : gate.namespaces()) {
            addIndexed(itemByNs, ns, entry);
            Set<NekoId> nsMembers = registries.itemsInNamespace(ns);
            if (nsMembers != null) {
                expandedIds.addAll(nsMembers);
            }
        }
        for (NekoId id : expandedIds) {
            addIndexed(itemById, id, entry);
            if (!gate.showInViewer()) {
                viewerItems.add(id);
            }
        }
    }

    private void indexBlockGate(
            BlockGate gate,
            int order,
            Map<NekoId, List<IndexedRule<BlockGate>>> blockById,
            Map<String, List<IndexedRule<BlockGate>>> blockByNs
    ) {
        IndexedRule<BlockGate> entry = new IndexedRule<>(gate.priority(), order, gate);
        Set<NekoId> expandedIds = new LinkedHashSet<>(gate.blocks());
        for (NekoId tagId : gate.tags()) {
            Set<NekoId> tagMembers = registries.expandBlockTag(tagId);
            if (tagMembers != null) {
                expandedIds.addAll(tagMembers);
            }
        }
        for (String ns : gate.namespaces()) {
            addIndexed(blockByNs, ns, entry);
            Set<NekoId> nsMembers = registries.blocksInNamespace(ns);
            if (nsMembers != null) {
                expandedIds.addAll(nsMembers);
            }
        }
        for (NekoId id : expandedIds) {
            addIndexed(blockById, id, entry);
        }
    }

    private void indexRecipeGate(
            RecipeGate gate,
            int order,
            Map<NekoId, List<CompiledRecipeRule>> recById,
            Map<String, List<CompiledRecipeRule>> recByNs,
            Map<NekoId, List<CompiledRecipeRule>> recByType,
            Map<NekoId, List<CompiledRecipeRule>> recByOut
    ) {
        CompiledRecipeRule rule = new CompiledRecipeRule(
                gate.priority(),
                order,
                gate.stage(),
                gate.scope(),
                gate.showInViewer(),
                gate
        );
        for (NekoId recipeId : gate.recipeIds()) {
            recById.computeIfAbsent(recipeId, k -> new ArrayList<>()).add(rule);
        }
        for (String ns : gate.namespaces()) {
            recByNs.computeIfAbsent(ns, k -> new ArrayList<>()).add(rule);
        }
        for (NekoId typeId : gate.recipeTypes()) {
            recByType.computeIfAbsent(typeId, k -> new ArrayList<>()).add(rule);
        }
        Set<NekoId> expandedOutputs = new LinkedHashSet<>(gate.outputItems());
        for (NekoId tagId : gate.outputTags()) {
            Set<NekoId> tagMembers = registries.expandItemTag(tagId);
            if (tagMembers != null) {
                expandedOutputs.addAll(tagMembers);
            }
        }
        for (NekoId outId : expandedOutputs) {
            recByOut.computeIfAbsent(outId, k -> new ArrayList<>()).add(rule);
        }
    }

    private void indexEntityGate(
            EntityGate gate,
            int order,
            Map<NekoId, List<IndexedRule<EntityGate>>> entById,
            Map<String, List<IndexedRule<EntityGate>>> entByNs
    ) {
        IndexedRule<EntityGate> entry = new IndexedRule<>(gate.priority(), order, gate);
        Set<NekoId> expandedIds = new LinkedHashSet<>(gate.entityTypes());
        for (NekoId tagId : gate.tags()) {
            Set<NekoId> tagMembers = registries.expandEntityTypeTag(tagId);
            if (tagMembers != null) {
                expandedIds.addAll(tagMembers);
            }
        }
        for (String ns : gate.namespaces()) {
            addIndexed(entByNs, ns, entry);
            Set<NekoId> nsMembers = registries.entitiesInNamespace(ns);
            if (nsMembers != null) {
                expandedIds.addAll(nsMembers);
            }
        }
        for (NekoId id : expandedIds) {
            addIndexed(entById, id, entry);
        }
    }

    private void indexLootGate(
            LootGate gate,
            int order,
            Map<NekoId, List<CompiledLootRule>> lootByItem,
            List<CompiledLootRule> wildcardLoot
    ) {
        Set<NekoId> expandedItems = new LinkedHashSet<>(gate.items());
        for (NekoId tagId : gate.tags()) {
            Set<NekoId> tagMembers = registries.expandItemTag(tagId);
            if (tagMembers != null) {
                expandedItems.addAll(tagMembers);
            }
        }
        boolean hasItemFilter = !gate.items().isEmpty() || !gate.tags().isEmpty();
        CompiledLootRule compiled = new CompiledLootRule(
                gate.priority(),
                order,
                gate,
                expandedItems.isEmpty() ? Set.of() : Collections.unmodifiableSet(expandedItems),
                hasItemFilter
        );
        if (!hasItemFilter) {
            wildcardLoot.add(compiled);
        } else {
            for (NekoId itemId : expandedItems) {
                lootByItem.computeIfAbsent(itemId, k -> new ArrayList<>()).add(compiled);
            }
        }
    }

    private static <K, T> void addIndexed(Map<K, List<IndexedRule<T>>> map, K key, IndexedRule<T> entry) {
        map.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
    }

    private static <K, T> Map<K, List<IndexedRule<T>>> freezeIndexedMap(Map<K, List<IndexedRule<T>>> raw) {
        if (raw.isEmpty()) {
            return Map.of();
        }
        Map<K, List<IndexedRule<T>>> out = new HashMap<>(raw.size());
        for (Map.Entry<K, List<IndexedRule<T>>> e : raw.entrySet()) {
            List<IndexedRule<T>> list = e.getValue();
            list.sort(RULE_COMPARATOR);
            out.put(e.getKey(), List.copyOf(deduplicateByOrder(list)));
        }
        return Collections.unmodifiableMap(out);
    }

    private static <T> Map<NekoId, List<IndexedRule<T>>> freezeSelectors(
            Map<NekoId, List<IndexedRule<T>>> byId, Map<String, List<IndexedRule<T>>> byNamespace) {
        byId.forEach((id, rules) -> {
            List<IndexedRule<T>> namespaceRules = byNamespace.get(id.namespace());
            if (namespaceRules != null) rules.addAll(namespaceRules);
        });
        return freezeIndexedMap(byId);
    }

    private static <T> @Nullable List<IndexedRule<T>> selectorRules(
            NekoId id, Map<NekoId, List<IndexedRule<T>>> byId, Map<String, List<IndexedRule<T>>> byNamespace) {
        List<IndexedRule<T>> rules = byId.get(id);
        return rules == null ? byNamespace.get(id.namespace()) : rules;
    }

    private static <K> Map<K, List<CompiledRecipeRule>> freezeRecipeMap(Map<K, List<CompiledRecipeRule>> raw) {
        if (raw.isEmpty()) {
            return Map.of();
        }
        Map<K, List<CompiledRecipeRule>> out = new HashMap<>(raw.size());
        for (Map.Entry<K, List<CompiledRecipeRule>> e : raw.entrySet()) {
            List<CompiledRecipeRule> list = e.getValue();
            list.sort(RECIPE_RULE_COMPARATOR);
            out.put(e.getKey(), List.copyOf(list));
        }
        return Collections.unmodifiableMap(out);
    }

    private static <K> Map<K, List<CompiledLootRule>> freezeLootMap(Map<K, List<CompiledLootRule>> raw) {
        if (raw.isEmpty()) {
            return Map.of();
        }
        Map<K, List<CompiledLootRule>> out = new HashMap<>(raw.size());
        for (Map.Entry<K, List<CompiledLootRule>> e : raw.entrySet()) {
            List<CompiledLootRule> list = e.getValue();
            list.sort(LOOT_RULE_COMPARATOR);
            out.put(e.getKey(), List.copyOf(list));
        }
        return Collections.unmodifiableMap(out);
    }

    private static <T> List<IndexedRule<T>> deduplicateByOrder(List<IndexedRule<T>> sorted) {
        if (sorted.size() <= 1) {
            return sorted;
        }
        List<IndexedRule<T>> dedup = new ArrayList<>(sorted.size());
        int prevOrder = -1;
        for (IndexedRule<T> item : sorted) {
            if (item.order() != prevOrder) {
                dedup.add(item);
                prevOrder = item.order();
            }
        }
        return dedup;
    }

    // ---- Internal O(1) evaluation helpers ----

    private static boolean isUnlocked(
            String stage,
            StageScope scope,
            @Nullable StageReadView playerStages,
            @Nullable StageReadView serverStages
    ) {
        boolean playerHas = playerStages != null && playerStages.has(stage);
        boolean serverHas = serverStages != null && serverStages.has(stage);
        return scope.evaluate(playerHas, serverHas);
    }

    private boolean evaluateItemAction(
            NekoId itemId,
            StageReadView playerStages,
            StageReadView serverStages,
            Predicate<ItemGate> allowWhenLocked
    ) {
        if (itemId == null) {
            return true;
        }
        List<IndexedRule<ItemGate>> rules = selectorRules(itemId, itemRulesById, itemRulesByNamespace);
        if (isEmpty(rules)) {
            return true;
        }
        return checkTierAllowed(rules, rules.getFirst().priority(), playerStages, serverStages, allowWhenLocked);
    }

    private boolean evaluateBlockAction(
            NekoId blockId,
            @Nullable Integer cropAge,
            StageReadView playerStages,
            StageReadView serverStages,
            Predicate<BlockGate> allowWhenLocked
    ) {
        if (blockId == null) {
            return true;
        }
        List<IndexedRule<BlockGate>> rules = selectorRules(blockId, blockRulesById, blockRulesByNamespace);
        if (isEmpty(rules)) {
            return true;
        }
        Integer topPriority = maxBlockPriority(rules, null, cropAge);
        return topPriority == null || checkBlockTierAllowed(rules, topPriority, cropAge, playerStages, serverStages, allowWhenLocked);
    }

    private boolean evaluateEntityAction(
            NekoId entityTypeId,
            StageReadView playerStages,
            StageReadView serverStages,
            Predicate<EntityGate> allowWhenLocked
    ) {
        if (entityTypeId == null) {
            return true;
        }
        List<IndexedRule<EntityGate>> rules = selectorRules(entityTypeId, entityRulesById, entityRulesByNamespace);
        if (isEmpty(rules)) {
            return true;
        }
        return checkTierAllowed(rules, rules.getFirst().priority(), playerStages, serverStages, allowWhenLocked);
    }

    private boolean evaluateRegionAction(
            NekoId dim,
            double x,
            double y,
            double z,
            StageReadView playerStages,
            StageReadView serverStages,
            Predicate<RegionGate> allowWhenLocked
    ) {
        return deniedRegionRule(dim, x, y, z, playerStages, serverStages, allowWhenLocked) == null;
    }

    public @Nullable RegionGate deniedRegionRule(@Nullable NekoId dim, double x, double y, double z,
            StageReadView playerStages, StageReadView serverStages, @Nullable Predicate<RegionGate> allowWhenLocked) {
        if (dim == null) return null;
        RegionBuckets buckets = regionRulesByDimension.get(dim);
        if (buckets == null) return null;
        var local = buckets.at(x, z);
        var large = buckets.large();
        int i = 0;
        int j = 0;
        Integer tier = null;
        while (i < local.size() || j < large.size()) {
            var entry = j >= large.size() || (i < local.size() && RULE_COMPARATOR.compare(local.get(i), large.get(j)) <= 0)
                    ? local.get(i++) : large.get(j++);
            if (tier != null && entry.priority() < tier) break;
            var rule = entry.rule();
            if (!regionContains(rule, dim, x, y, z)) continue;
            tier = entry.priority();
            if ((allowWhenLocked == null || !allowWhenLocked.test(rule))
                    && !isUnlocked(rule.stage(), rule.scope(), playerStages, serverStages)) return rule;
        }
        return null;
    }

    private static boolean regionContains(RegionGate gate, NekoId dim, double x, double y, double z) {
        for (RegionShape shape : gate.shapes()) {
            if (shape.contains(dim, x, y, z)) {
                return true;
            }
        }
        return false;
    }

    private static boolean blockAppliesToAge(BlockGate gate, @Nullable Integer cropAge) {
        Integer minAge = gate.minAge();
        if (minAge == null) {
            return true;
        }
        if (cropAge == null) {
            return minAge == 0;
        }
        return cropAge >= minAge;
    }

    private static @Nullable Integer maxBlockPriority(
            @Nullable List<IndexedRule<BlockGate>> a,
            @Nullable List<IndexedRule<BlockGate>> b,
            @Nullable Integer cropAge
    ) {
        Integer pA = firstApplicableBlockPriority(a, cropAge);
        Integer pB = firstApplicableBlockPriority(b, cropAge);
        if (pA == null) return pB;
        if (pB == null) return pA;
        return Math.max(pA, pB);
    }

    private static @Nullable Integer firstApplicableBlockPriority(
            @Nullable List<IndexedRule<BlockGate>> list,
            @Nullable Integer cropAge
    ) {
        if (isEmpty(list)) {
            return null;
        }
        for (IndexedRule<BlockGate> entry : list) {
            if (blockAppliesToAge(entry.rule(), cropAge)) {
                return entry.priority();
            }
        }
        return null;
    }

    private static boolean checkBlockTierAllowed(
            @Nullable List<IndexedRule<BlockGate>> list,
            int topPriority,
            @Nullable Integer cropAge,
            StageReadView playerStages,
            StageReadView serverStages,
            Predicate<BlockGate> allowWhenLocked
    ) {
        if (isEmpty(list)) {
            return true;
        }
        for (IndexedRule<BlockGate> entry : list) {
            if (entry.priority() < topPriority) {
                break;
            }
            if (entry.priority() == topPriority && blockAppliesToAge(entry.rule(), cropAge)) {
                BlockGate gate = entry.rule();
                if (!allowWhenLocked.test(gate)
                        && !isUnlocked(gate.stage(), gate.scope(), playerStages, serverStages)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static @Nullable IndexedRule<BlockGate> firstLockedBlockAtPriority(
            @Nullable List<IndexedRule<BlockGate>> list,
            int topPriority,
            @Nullable Integer cropAge,
            StageReadView playerStages,
            StageReadView serverStages
    ) {
        if (isEmpty(list)) {
            return null;
        }
        for (IndexedRule<BlockGate> entry : list) {
            if (entry.priority() < topPriority) {
                break;
            }
            if (entry.priority() == topPriority
                    && blockAppliesToAge(entry.rule(), cropAge)
                    && !isUnlocked(entry.rule().stage(), entry.rule().scope(), playerStages, serverStages)) {
                return entry;
            }
        }
        return null;
    }

    private @Nullable RecipeLock lockDynamicRecipe(NekoId id, @Nullable NekoId type, @Nullable NekoId output,
                                                    StageReadView playerStages, StageReadView serverStages) {
        var byId = recipeRulesById.get(id);
        var byNamespace = recipeRulesByNamespace.isEmpty() ? null : recipeRulesByNamespace.get(id.namespace());
        var byType = type == null ? null : recipeRulesByType.get(type);
        var byOutput = output == null ? null : recipeRulesByOutputItem.get(output);
        int priority = Math.max(Math.max(recipePriority(byId), recipePriority(byNamespace)),
                Math.max(recipePriority(byType), recipePriority(byOutput)));
        var winner = earlierLockedRecipe(null, byId, priority, playerStages, serverStages);
        winner = earlierLockedRecipe(winner, byNamespace, priority, playerStages, serverStages);
        winner = earlierLockedRecipe(winner, byType, priority, playerStages, serverStages);
        winner = earlierLockedRecipe(winner, byOutput, priority, playerStages, serverStages);
        return winner == null ? null : new RecipeLock(winner.stage(), winner.declaration());
    }

    private static @Nullable CompiledRecipeRule earlierLockedRecipe(@Nullable CompiledRecipeRule winner,
            @Nullable List<CompiledRecipeRule> rules, int priority, StageReadView playerStages, StageReadView serverStages) {
        if (rules == null) return winner;
        for (var rule : rules) {
            if (rule.priority() < priority || (winner != null && rule.order() >= winner.order())) break;
            if (!isUnlocked(rule.stage(), rule.scope(), playerStages, serverStages)) return rule;
        }
        return winner;
    }

    private RecipeTier compileRecipeTier(NekoId id, @Nullable NekoId type, @Nullable NekoId output) {
        var byId = recipeRulesById.get(id);
        var byNamespace = recipeRulesByNamespace.isEmpty() ? null : recipeRulesByNamespace.get(id.namespace());
        var byType = type == null ? null : recipeRulesByType.get(type);
        var byOutput = output == null ? null : recipeRulesByOutputItem.get(output);
        if (isEmpty(byId) && isEmpty(byNamespace) && isEmpty(byType) && isEmpty(byOutput)) return RecipeTier.EMPTY;
        int priority = Math.max(Math.max(recipePriority(byId), recipePriority(byNamespace)),
                Math.max(recipePriority(byType), recipePriority(byOutput)));
        Map<Integer, CompiledRecipeRule> winning = new TreeMap<>();
        appendRecipeTier(byId, priority, winning);
        appendRecipeTier(byNamespace, priority, winning);
        appendRecipeTier(byType, priority, winning);
        appendRecipeTier(byOutput, priority, winning);
        var rules = List.copyOf(winning.values());
        var hidden = rules.stream().filter(rule -> !rule.showInViewer())
                .map(rule -> new WireRecipeRule(rule.stage(), rule.scope())).toList();
        return new RecipeTier(rules, hidden);
    }

    private static int recipePriority(@Nullable List<CompiledRecipeRule> rules) {
        return isEmpty(rules) ? Integer.MIN_VALUE : rules.getFirst().priority();
    }

    private static void appendRecipeTier(@Nullable List<CompiledRecipeRule> rules, int priority,
                                         Map<Integer, CompiledRecipeRule> winning) {
        if (rules == null) return;
        for (var rule : rules) {
            if (rule.priority() < priority) break;
            winning.putIfAbsent(rule.order(), rule);
        }
    }

    private static boolean lootRuleMatchesOrigin(
            LootGate gate,
            @Nullable NekoId lootTableId,
            @Nullable NekoId sourceEntityTypeId
    ) {
        if (!gate.fromLootTables().isEmpty()
                && (lootTableId == null || !gate.fromLootTables().contains(lootTableId))) {
            return false;
        }
        if (!gate.fromEntities().isEmpty()
                && (sourceEntityTypeId == null || !gate.fromEntities().contains(sourceEntityTypeId))) {
            return false;
        }
        return true;
    }

    private static @Nullable Integer maxLootPriority(
            @Nullable List<CompiledLootRule> byItem,
            List<CompiledLootRule> wildcard,
            @Nullable NekoId lootTableId,
            @Nullable NekoId sourceEntityTypeId
    ) {
        Integer pA = firstMatchingLootPriority(byItem, lootTableId, sourceEntityTypeId);
        Integer pB = firstMatchingLootPriority(wildcard, lootTableId, sourceEntityTypeId);
        if (pA == null) return pB;
        if (pB == null) return pA;
        return Math.max(pA, pB);
    }

    private static @Nullable Integer firstMatchingLootPriority(
            @Nullable List<CompiledLootRule> list,
            @Nullable NekoId lootTableId,
            @Nullable NekoId sourceEntityTypeId
    ) {
        if (isEmpty(list)) {
            return null;
        }
        for (CompiledLootRule rule : list) {
            if (lootRuleMatchesOrigin(rule.gate(), lootTableId, sourceEntityTypeId)) {
                return rule.priority();
            }
        }
        return null;
    }

    private static @Nullable CompiledLootRule firstLockedLootAtPriority(
            @Nullable List<CompiledLootRule> list,
            int topPriority,
            @Nullable NekoId lootTableId,
            @Nullable NekoId sourceEntityTypeId,
            StageReadView playerStages,
            StageReadView serverStages
    ) {
        if (isEmpty(list)) {
            return null;
        }
        for (CompiledLootRule rule : list) {
            if (rule.priority() < topPriority) {
                break;
            }
            if (rule.priority() == topPriority
                    && lootRuleMatchesOrigin(rule.gate(), lootTableId, sourceEntityTypeId)
                    && !isUnlocked(rule.gate().stage(), rule.gate().scope(), playerStages, serverStages)) {
                return rule;
            }
        }
        return null;
    }

    private static <T extends StageGateDeclaration> boolean checkTierAllowed(
            @Nullable List<IndexedRule<T>> list,
            int topPriority,
            StageReadView playerStages,
            StageReadView serverStages,
            Predicate<T> allowWhenLocked
    ) {
        if (isEmpty(list)) {
            return true;
        }
        for (IndexedRule<T> entry : list) {
            if (entry.priority() < topPriority) {
                break;
            }
            T gate = entry.rule();
            if (!allowWhenLocked.test(gate)
                    && !isUnlocked(gate.stage(), gate.scope(), playerStages, serverStages)) {
                return false;
            }
        }
        return true;
    }

    private static <T extends StageGateDeclaration> @Nullable IndexedRule<T> firstLockedAtPriority(
            @Nullable List<IndexedRule<T>> list,
            int topPriority,
            StageReadView playerStages,
            StageReadView serverStages
    ) {
        if (isEmpty(list)) {
            return null;
        }
        for (IndexedRule<T> entry : list) {
            if (entry.priority() < topPriority) {
                break;
            }
            T gate = entry.rule();
            if (!isUnlocked(gate.stage(), gate.scope(), playerStages, serverStages)) {
                return entry;
            }
        }
        return null;
    }

    private static boolean isEmpty(@Nullable List<?> list) {
        return list == null || list.isEmpty();
    }
}
