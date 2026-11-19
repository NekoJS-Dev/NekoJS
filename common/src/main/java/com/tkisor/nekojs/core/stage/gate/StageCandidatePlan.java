package com.tkisor.nekojs.core.stage.gate;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.stage.StageDefinition;
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
import com.tkisor.nekojs.core.stage.StageStore;
import com.tkisor.nekojs.core.state.CandidateStatePlan;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Candidate generation plan for {@code StageEvents.customize} definitions and
 * {@code StageEvents.gates} declarative stage gate rules.
 *
 * <p>Participates in joint {@link CandidateStatePlan} preflight and publication:
 * <ul>
 *   <li>{@link #preflight()} validates unique stage definition names, unique gate rule IDs,
 *       non-blank stage identifiers, and explicit registry targets against {@link StageRegistryAccess}
 *       without mutating active server state.</li>
 *   <li>{@link #publish()} atomically installs customized {@link StageDefinition}s into
 *       {@link StageStore} and swaps the active {@link CompiledStageGateSnapshot} via the commit
 *       callback.</li>
 * </ul>
 */
public final class StageCandidatePlan implements CandidateStatePlan {

    /** Plan domain identifier reported in structured reload failure diagnostics. */
    public static final String DOMAIN = "stage-gates";

    private final List<StageDefinition> definitions = new ArrayList<>();
    private final List<StageGateDeclaration> declarations = new ArrayList<>();
    private final Set<NekoId> whitelistedContainers = new LinkedHashSet<>();
    private final Map<NekoId, Set<Integer>> whitelistedContainerSlots = new LinkedHashMap<>();

    private final @Nullable StageStore stageStore;
    private final InlineRecipeStageRegistry inlineRecipes;
    private final StageRegistryAccess registries;
    private final @Nullable BiConsumer<List<StageDefinition>, CompiledStageGateSnapshot> commitCallback;

    private volatile @Nullable String collectionFailure;
    private volatile @Nullable Throwable collectionError;
    private volatile @Nullable CompiledStageGateSnapshot preflightSnapshot;
    private volatile boolean published;
    private volatile boolean discarded;

    /**
     * Creates a candidate stage plan wired to a {@link StageStore}, {@link InlineRecipeStageRegistry},
     * {@link StageRegistryAccess}, and active snapshot commit callback.
     */
    public StageCandidatePlan(
            @Nullable Collection<StageDefinition> definitions,
            @Nullable Collection<StageGateDeclaration> declarations,
            @Nullable Collection<NekoId> whitelistedContainers,
            @Nullable StageStore stageStore,
            @Nullable InlineRecipeStageRegistry inlineRecipes,
            @Nullable StageRegistryAccess registries,
            @Nullable Consumer<CompiledStageGateSnapshot> snapshotConsumer
    ) {
        this(
                definitions,
                declarations,
                whitelistedContainers,
                stageStore,
                inlineRecipes,
                registries,
                snapshotConsumer == null ? null : (defs, snapshot) -> snapshotConsumer.accept(snapshot)
        );
    }

    /**
     * Creates a candidate stage plan using {@link InlineRecipeStageRegistry#SHARED}.
     */
    public StageCandidatePlan(
            @Nullable Collection<StageDefinition> definitions,
            @Nullable Collection<StageGateDeclaration> declarations,
            @Nullable Collection<NekoId> whitelistedContainers,
            @Nullable StageStore stageStore,
            @Nullable StageRegistryAccess registries,
            @Nullable Consumer<CompiledStageGateSnapshot> snapshotConsumer
    ) {
        this(
                definitions,
                declarations,
                whitelistedContainers,
                stageStore,
                InlineRecipeStageRegistry.SHARED,
                registries,
                snapshotConsumer
        );
    }

    /**
     * Creates a candidate stage plan with a combined definitions + compiled snapshot commit callback.
     */
    public StageCandidatePlan(
            @Nullable Collection<StageDefinition> definitions,
            @Nullable Collection<StageGateDeclaration> declarations,
            @Nullable Collection<NekoId> whitelistedContainers,
            @Nullable StageStore stageStore,
            @Nullable InlineRecipeStageRegistry inlineRecipes,
            @Nullable StageRegistryAccess registries,
            @Nullable BiConsumer<List<StageDefinition>, CompiledStageGateSnapshot> commitCallback
    ) {
        if (definitions != null) {
            this.definitions.addAll(definitions);
        }
        if (declarations != null) {
            this.declarations.addAll(declarations);
        }
        if (whitelistedContainers != null) {
            this.whitelistedContainers.addAll(whitelistedContainers);
        }
        this.stageStore = stageStore;
        this.inlineRecipes = inlineRecipes != null ? inlineRecipes : InlineRecipeStageRegistry.SHARED;
        this.registries = registries != null ? registries : StageRegistryAccess.PERMISSIVE;
        this.commitCallback = commitCallback;
    }

    /**
     * Creates an initially empty candidate stage plan for incremental collection before preflight.
     */
    public StageCandidatePlan(
            @Nullable StageStore stageStore,
            @Nullable InlineRecipeStageRegistry inlineRecipes,
            @Nullable StageRegistryAccess registries,
            @Nullable Consumer<CompiledStageGateSnapshot> snapshotConsumer
    ) {
        this(null, null, null, stageStore, inlineRecipes, registries, snapshotConsumer);
    }

    @Override
    public String domain() {
        return DOMAIN;
    }

    /** Appends candidate stage definitions during collection. */
    public synchronized void addDefinitions(Collection<StageDefinition> items) {
        if (collectionFailure != null || items == null) {
            return;
        }
        this.definitions.addAll(items);
        this.preflightSnapshot = null;
    }

    /** Appends a single candidate stage definition during collection. */
    public synchronized void addDefinition(StageDefinition definition) {
        if (collectionFailure != null) {
            return;
        }
        this.definitions.add(Objects.requireNonNull(definition, "definition"));
        this.preflightSnapshot = null;
    }

    /** Appends candidate stage gate declarations during collection. */
    public synchronized void addDeclarations(Collection<StageGateDeclaration> items) {
        if (collectionFailure != null || items == null) {
            return;
        }
        this.declarations.addAll(items);
        this.preflightSnapshot = null;
    }

    /** Appends a single candidate stage gate declaration during collection. */
    public synchronized void addDeclaration(StageGateDeclaration declaration) {
        if (collectionFailure != null) {
            return;
        }
        this.declarations.add(Objects.requireNonNull(declaration, "declaration"));
        this.preflightSnapshot = null;
    }

    /** Appends whitelisted container menu IDs during collection. */
    public synchronized void addWhitelistedContainers(Collection<NekoId> menuIds) {
        if (collectionFailure != null || menuIds == null) {
            return;
        }
        this.whitelistedContainers.addAll(menuIds);
        this.preflightSnapshot = null;
    }

    public synchronized void addWhitelistedContainerSlots(Map<NekoId, Set<Integer>> menuSlots) {
        if (collectionFailure != null || menuSlots == null) {
            return;
        }
        this.preflightSnapshot = null;
        for (var entry : menuSlots.entrySet()) {
            NekoId menuId = entry.getKey();
            Set<Integer> slots = entry.getValue();
            if (slots == null || slots.isEmpty()) {
                throw new IllegalArgumentException("[NEKO-4071] Whitelisted container requires at least one slot ID: " + menuId);
            }
            whitelistedContainerSlots.computeIfAbsent(menuId, key -> new LinkedHashSet<>()).addAll(slots);
        }
    }

    /** Records a collection failure so {@link #preflight()} rejects the entire candidate batch. */
    public synchronized void fail(String reason, @Nullable Throwable error) {
        if (collectionFailure == null) {
            collectionFailure = reason != null ? reason : "stage candidate collection failed";
            collectionError = error;
        }
    }

    /** Returns an immutable snapshot of candidate stage definitions. */
    public synchronized List<StageDefinition> definitions() {
        return List.copyOf(definitions);
    }

    /** Returns an immutable snapshot of candidate stage gate declarations. */
    public synchronized List<StageGateDeclaration> declarations() {
        return List.copyOf(declarations);
    }

    /** Returns an immutable snapshot of candidate whitelisted container menu IDs. */
    public synchronized Set<NekoId> whitelistedContainers() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(whitelistedContainers));
    }

    /** Returns {@code true} if this plan has already published at commit time. */
    public boolean isPublished() {
        return published;
    }

    @Override
    public synchronized void preflight() {
        if (collectionFailure != null) {
            if (collectionError instanceof IllegalArgumentException iae) {
                throw iae;
            }
            throw new IllegalStateException(
                    "[NEKO-4071] Stage candidate collection failed: " + collectionFailure,
                    collectionError
            );
        }

        validateDefinitions(this.definitions);
        validateWhitelistedContainers(this.whitelistedContainers, this.registries);
        validateWhitelistedContainerSlots(this.whitelistedContainerSlots, this.registries);
        validateDeclarations(this.declarations, this.registries);

        var inline = this.inlineRecipes.snapshot();
        if (this.registries.recipeTableBound()) {
            for (NekoId recipeId : inline.keySet()) {
                if (!this.registries.recipeExists(recipeId)) {
                    throw new IllegalArgumentException("[NEKO-4071] Inline stage targets an unknown recipe: " + recipeId);
                }
            }
        }
        this.preflightSnapshot = CompiledStageGateSnapshot.compile(
                this.declarations,
                this.whitelistedContainers,
                this.whitelistedContainerSlots,
                inline,
                this.registries
        );
    }

    @Override
    public synchronized void publish() {
        if (published || discarded) {
            return;
        }
        List<StageDefinition> frozenDefinitions = List.copyOf(this.definitions);
        CompiledStageGateSnapshot compiled = this.preflightSnapshot;
        if (compiled == null) {
            preflight();
            compiled = Objects.requireNonNull(this.preflightSnapshot);
        } else {
            // Ensure any inline recipe gates registered since preflight are merged atomically.
            var latest = this.inlineRecipes.snapshot();
            if (!compiled.hasInlineRecipeEntries(latest)) {
                preflight();
                compiled = Objects.requireNonNull(this.preflightSnapshot);
            }
        }

        if (this.stageStore != null) {
            this.stageStore.setDefinitions(frozenDefinitions);
        }
        if (this.commitCallback != null) {
            this.commitCallback.accept(frozenDefinitions, compiled);
        }
        this.preflightSnapshot = compiled;
        this.published = true;
    }

    /** Convenience alias executing {@link #publish()} and returning the committed snapshot. */
    public synchronized CompiledStageGateSnapshot apply() {
        publish();
        return Objects.requireNonNullElse(this.preflightSnapshot, CompiledStageGateSnapshot.EMPTY);
    }

    @Override
    public synchronized void discard() {
        if (published || discarded) {
            return;
        }
        this.discarded = true;
        this.preflightSnapshot = null;
    }

    // ---- Static preflight validation helpers ----

    private static void validateDefinitions(List<StageDefinition> definitions) {
        Set<String> seenStages = new LinkedHashSet<>(definitions.size());
        for (StageDefinition def : definitions) {
            if (def == null) {
                throw new IllegalArgumentException("[NEKO-4071] Stage definition entry cannot be null");
            }
            String stage = validateNonBlankStage(def.stage(), "stage customization");
            if (!seenStages.add(stage)) {
                throw new IllegalArgumentException(
                        "[NEKO-4071] Duplicate stage customization for stage: " + stage
                );
            }
        }
    }

    private static void validateWhitelistedContainers(
            Set<NekoId> whitelistedContainers,
            StageRegistryAccess registries
    ) {
        for (NekoId menuId : whitelistedContainers) {
            if (menuId == null) {
                throw new IllegalArgumentException(
                        "[NEKO-4071] Whitelisted container menu ID cannot be null"
                );
            }
            if (!registries.menuExists(menuId)) {
                throw new IllegalArgumentException(
                        "[NEKO-4071] Unknown menu '" + menuId + "' in stage gate whitelistedContainers"
                );
            }
        }
    }

    private static void validateWhitelistedContainerSlots(
            Map<NekoId, Set<Integer>> menuSlots,
            StageRegistryAccess registries
    ) {
        validateWhitelistedContainers(menuSlots.keySet(), registries);
        for (var entry : menuSlots.entrySet()) {
            for (Integer slotId : entry.getValue()) {
                if (slotId == null || slotId < 0) {
                    throw new IllegalArgumentException(
                            "[NEKO-4071] Invalid whitelisted container slot ID " + slotId + " for " + entry.getKey()
                    );
                }
            }
        }
    }

    private static void validateDeclarations(
            List<StageGateDeclaration> declarations,
            StageRegistryAccess registries
    ) {
        Set<NekoId> seenRuleIds = new LinkedHashSet<>(declarations.size());
        boolean worldRegistriesBound = registries.worldRegistriesBound();
        boolean recipeTableBound = registries.recipeTableBound();
        for (StageGateDeclaration decl : declarations) {
            if (decl == null) {
                throw new IllegalArgumentException("[NEKO-4071] Stage gate declaration cannot be null");
            }
            NekoId ruleId = decl.id();
            if (ruleId == null) {
                throw new IllegalArgumentException("[NEKO-4071] Stage gate rule ID cannot be null");
            }
            if (!seenRuleIds.add(ruleId)) {
                throw new IllegalArgumentException(
                        "[NEKO-4071] Duplicate stage gate id: " + ruleId
                );
            }
            validateNonBlankStage(decl.stage(), "stage gate '" + ruleId + "'");

            switch (decl) {
                case ItemGate itemGate -> {
                    for (NekoId itemId : itemGate.items()) {
                        requireExists(registries.itemExists(itemId), "item", itemId, ruleId);
                    }
                }
                case BlockGate blockGate -> {
                    for (NekoId blockId : blockGate.blocks()) {
                        requireExists(registries.blockExists(blockId), "block", blockId, ruleId);
                    }
                }
                case RecipeGate recipeGate -> {
                    if (recipeTableBound) {
                        for (NekoId recipeId : recipeGate.recipeIds()) {
                            requireExists(registries.recipeExists(recipeId), "recipe", recipeId, ruleId);
                        }
                    }
                    for (NekoId recipeTypeId : recipeGate.recipeTypes()) {
                        requireExists(registries.recipeTypeExists(recipeTypeId), "recipe type", recipeTypeId, ruleId);
                    }
                    for (NekoId outItem : recipeGate.outputItems()) {
                        requireExists(registries.itemExists(outItem), "item", outItem, ruleId);
                    }
                }
                case ContainerGate containerGate -> {
                    for (NekoId menuId : containerGate.menus()) {
                        requireExists(registries.menuExists(menuId), "menu", menuId, ruleId);
                    }
                }
                case DimensionGate dimensionGate -> {
                    if (worldRegistriesBound) {
                        for (NekoId dimensionId : dimensionGate.dimensions()) {
                            requireExists(registries.dimensionExists(dimensionId), "dimension", dimensionId, ruleId);
                        }
                    }
                }
                case RegionGate regionGate -> {
                    if (worldRegistriesBound) {
                        for (RegionShape shape : regionGate.shapes()) {
                            NekoId dimensionId = shape.dimension();
                            requireExists(registries.dimensionExists(dimensionId), "dimension", dimensionId, ruleId);
                        }
                    }
                }
                case EntityGate entityGate -> {
                    for (NekoId entityId : entityGate.entityTypes()) {
                        requireExists(registries.entityTypeExists(entityId), "entity", entityId, ruleId);
                    }
                }
                case EffectGate effectGate -> {
                    for (NekoId effectId : effectGate.effects()) {
                        requireExists(registries.effectExists(effectId), "effect", effectId, ruleId);
                    }
                }
                case EnchantGate enchantGate -> {
                    if (worldRegistriesBound) {
                        for (NekoId enchantId : enchantGate.enchants()) {
                            requireExists(registries.enchantmentExists(enchantId), "enchantment", enchantId, ruleId);
                        }
                    }
                }
                case LootGate lootGate -> {
                    for (NekoId itemId : lootGate.items()) {
                        requireExists(registries.itemExists(itemId), "item", itemId, ruleId);
                    }
                    if (lootGate.replacementItem() != null) {
                        requireExists(
                                registries.itemExists(lootGate.replacementItem()),
                                "item",
                                lootGate.replacementItem(),
                                ruleId
                        );
                    }
                    for (NekoId entityId : lootGate.fromEntities()) {
                        requireExists(registries.entityTypeExists(entityId), "entity", entityId, ruleId);
                    }
                }
            }
        }
    }

    private static String validateNonBlankStage(@Nullable String stage, String context) {
        if (stage == null || stage.isBlank()) {
            throw new IllegalArgumentException(
                    "[NEKO-4071] Blank stage name in " + context
            );
        }
        String trimmed = stage.trim();
        if (trimmed.length() > StageStore.MAX_STAGE_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "[NEKO-4071] Stage name exceeds " + StageStore.MAX_STAGE_NAME_LENGTH + " chars in " + context
            );
        }
        return trimmed;
    }

    private static void requireExists(boolean exists, String kind, NekoId targetId, NekoId ruleId) {
        if (!exists) {
            throw new IllegalArgumentException(
                    "[NEKO-4071] Unknown " + kind + " '" + targetId + "' in stage gate '" + ruleId + "'"
            );
        }
    }
}
