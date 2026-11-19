package com.tkisor.nekojs.api.stage.gate;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.stage.StageScope;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Sealed immutable declaration hierarchy for all stage gate rule kinds collected during
 * {@code StageEvents.gates}.
 */
@Doc("Immutable declaration model for a declarative stage gate rule.")
public sealed interface StageGateDeclaration permits
        StageGateDeclaration.ItemGate,
        StageGateDeclaration.BlockGate,
        StageGateDeclaration.RecipeGate,
        StageGateDeclaration.ContainerGate,
        StageGateDeclaration.DimensionGate,
        StageGateDeclaration.RegionGate,
        StageGateDeclaration.EntityGate,
        StageGateDeclaration.EffectGate,
        StageGateDeclaration.EnchantGate,
        StageGateDeclaration.LootGate {

    /** Unique rule identifier. */
    NekoId id();

    /** Required stage identifier. */
    String stage();

    /** Gate category discriminator. */
    StageGateKind kind();

    /** Rule evaluation priority (higher values win on specificity ties; defaults to {@code 0}). */
    int priority();

    /** Stage evaluation scope (defaults to {@link StageScope#PLAYER}). */
    StageScope scope();

    /** Optional custom feedback message shown when locked, or {@code ""} to use default feedback. */
    String lockedMessage();

    /**
     * Item stage gate declaration (covers both standard item gates and armor presets).
     */
    record ItemGate(
            NekoId id,
            String stage,
            int priority,
            StageScope scope,
            String lockedMessage,
            Set<NekoId> items,
            Set<NekoId> tags,
            Set<String> namespaces,
            boolean allowInventoryStorage,
            boolean allowContainerStorage,
            boolean allowPickup,
            boolean allowDrop,
            boolean allowEquip,
            boolean allowAttack,
            boolean allowUse,
            boolean showInViewer,
            String lockedName
    ) implements StageGateDeclaration {
        public ItemGate {
            StageGateKind.requireRuleId(id);
            StageGateKind.requireStage(stage);
            scope = scope == null ? StageScope.PLAYER : scope;
            lockedMessage = StageGateKind.normalizeText(lockedMessage);
            items = StageGateKind.immutableIdSet(items, "items");
            tags = StageGateKind.immutableTagSet(tags);
            namespaces = StageGateKind.immutableNamespaceSet(namespaces);
            lockedName = StageGateKind.normalizeText(lockedName);
            StageGateKind.requireTarget(id, !items.isEmpty() || !tags.isEmpty() || !namespaces.isEmpty());
        }

        @Override
        public StageGateKind kind() {
            return StageGateKind.ITEM;
        }
    }

    /**
     * Block and crop stage gate declaration.
     */
    record BlockGate(
            NekoId id,
            String stage,
            int priority,
            StageScope scope,
            String lockedMessage,
            Set<NekoId> blocks,
            Set<NekoId> tags,
            Set<String> namespaces,
            boolean allowBreak,
            boolean allowPlace,
            boolean allowInteract,
            boolean allowToolModify,
            @Nullable Integer minAge
    ) implements StageGateDeclaration {
        public BlockGate {
            StageGateKind.requireRuleId(id);
            StageGateKind.requireStage(stage);
            scope = scope == null ? StageScope.PLAYER : scope;
            lockedMessage = StageGateKind.normalizeText(lockedMessage);
            blocks = StageGateKind.immutableIdSet(blocks, "blocks");
            tags = StageGateKind.immutableTagSet(tags);
            namespaces = StageGateKind.immutableNamespaceSet(namespaces);
            if (minAge != null && minAge < 0) {
                throw new IllegalArgumentException(
                        "[NEKO-4071] Stage gate '" + id + "' minAge cannot be negative: " + minAge);
            }
            StageGateKind.requireTarget(id, !blocks.isEmpty() || !tags.isEmpty() || !namespaces.isEmpty());
        }

        @Override
        public StageGateKind kind() {
            return StageGateKind.BLOCK;
        }
    }

    /**
     * Recipe crafting/processing stage gate declaration.
     */
    record RecipeGate(
            NekoId id,
            String stage,
            int priority,
            StageScope scope,
            String lockedMessage,
            Set<NekoId> recipeIds,
            Set<NekoId> outputItems,
            Set<NekoId> outputTags,
            Set<NekoId> recipeTypes,
            Set<String> namespaces,
            boolean showInViewer
    ) implements StageGateDeclaration {
        public RecipeGate {
            StageGateKind.requireRuleId(id);
            StageGateKind.requireStage(stage);
            scope = scope == null ? StageScope.PLAYER : scope;
            lockedMessage = StageGateKind.normalizeText(lockedMessage);
            recipeIds = StageGateKind.immutableIdSet(recipeIds, "recipeIds");
            outputItems = StageGateKind.immutableIdSet(outputItems, "outputItems");
            outputTags = StageGateKind.immutableTagSet(outputTags);
            recipeTypes = StageGateKind.immutableIdSet(recipeTypes, "recipeTypes");
            namespaces = StageGateKind.immutableNamespaceSet(namespaces);
            StageGateKind.requireTarget(
                    id,
                    !recipeIds.isEmpty()
                            || !outputItems.isEmpty()
                            || !outputTags.isEmpty()
                            || !recipeTypes.isEmpty()
                            || !namespaces.isEmpty()
            );
        }

        @Override
        public StageGateKind kind() {
            return StageGateKind.RECIPE;
        }
    }

    /**
     * Container menu stage gate declaration.
     */
    record ContainerGate(
            NekoId id,
            String stage,
            int priority,
            StageScope scope,
            String lockedMessage,
            Set<NekoId> menus
    ) implements StageGateDeclaration {
        public ContainerGate {
            StageGateKind.requireRuleId(id);
            StageGateKind.requireStage(stage);
            scope = scope == null ? StageScope.PLAYER : scope;
            lockedMessage = StageGateKind.normalizeText(lockedMessage);
            menus = StageGateKind.immutableIdSet(menus, "menus");
            StageGateKind.requireTarget(id, !menus.isEmpty());
        }

        @Override
        public StageGateKind kind() {
            return StageGateKind.CONTAINER;
        }
    }

    /**
     * Dimension entry/presence stage gate declaration.
     */
    record DimensionGate(
            NekoId id,
            String stage,
            int priority,
            StageScope scope,
            String lockedMessage,
            Set<NekoId> dimensions
    ) implements StageGateDeclaration {
        public DimensionGate {
            StageGateKind.requireRuleId(id);
            StageGateKind.requireStage(stage);
            scope = scope == null ? StageScope.PLAYER : scope;
            lockedMessage = StageGateKind.normalizeText(lockedMessage);
            dimensions = StageGateKind.immutableIdSet(dimensions, "dimensions");
            StageGateKind.requireTarget(id, !dimensions.isEmpty());
        }

        @Override
        public StageGateKind kind() {
            return StageGateKind.DIMENSION;
        }
    }

    /**
     * Spatial region stage gate declaration.
     */
    record RegionGate(
            NekoId id,
            String stage,
            int priority,
            StageScope scope,
            String lockedMessage,
            List<RegionShape> shapes,
            boolean allowEnter,
            boolean allowBreak,
            boolean allowPlace,
            boolean allowInteract
    ) implements StageGateDeclaration {
        public RegionGate {
            StageGateKind.requireRuleId(id);
            StageGateKind.requireStage(stage);
            scope = scope == null ? StageScope.PLAYER : scope;
            lockedMessage = StageGateKind.normalizeText(lockedMessage);
            if (shapes == null) {
                throw new IllegalArgumentException("[NEKO-4071] Stage gate shapes list cannot be null");
            }
            List<RegionShape> copy = new ArrayList<>(shapes.size());
            for (RegionShape shape : shapes) {
                if (shape == null) {
                    throw new IllegalArgumentException("[NEKO-4071] Stage gate shape entry cannot be null");
                }
                copy.add(shape);
            }
            shapes = Collections.unmodifiableList(copy);
            StageGateKind.requireTarget(id, !shapes.isEmpty());
        }

        @Override
        public StageGateKind kind() {
            return StageGateKind.REGION;
        }
    }

    /**
     * Entity interaction/combat/mounting stage gate declaration.
     */
    record EntityGate(
            NekoId id,
            String stage,
            int priority,
            StageScope scope,
            String lockedMessage,
            Set<NekoId> entityTypes,
            Set<NekoId> tags,
            Set<String> namespaces,
            boolean allowAttack,
            boolean allowInteract,
            boolean allowMount
    ) implements StageGateDeclaration {
        public EntityGate {
            StageGateKind.requireRuleId(id);
            StageGateKind.requireStage(stage);
            scope = scope == null ? StageScope.PLAYER : scope;
            lockedMessage = StageGateKind.normalizeText(lockedMessage);
            entityTypes = StageGateKind.immutableIdSet(entityTypes, "entityTypes");
            tags = StageGateKind.immutableTagSet(tags);
            namespaces = StageGateKind.immutableNamespaceSet(namespaces);
            StageGateKind.requireTarget(id, !entityTypes.isEmpty() || !tags.isEmpty() || !namespaces.isEmpty());
        }

        @Override
        public StageGateKind kind() {
            return StageGateKind.ENTITY;
        }
    }

    /**
     * Mob effect application/retention stage gate declaration.
     */
    record EffectGate(
            NekoId id,
            String stage,
            int priority,
            StageScope scope,
            String lockedMessage,
            Set<NekoId> effects
    ) implements StageGateDeclaration {
        public EffectGate {
            StageGateKind.requireRuleId(id);
            StageGateKind.requireStage(stage);
            scope = scope == null ? StageScope.PLAYER : scope;
            lockedMessage = StageGateKind.normalizeText(lockedMessage);
            effects = StageGateKind.immutableIdSet(effects, "effects");
            StageGateKind.requireTarget(id, !effects.isEmpty());
        }

        @Override
        public StageGateKind kind() {
            return StageGateKind.EFFECT;
        }
    }

    /**
     * Enchantment usage/application stage gate declaration.
     */
    record EnchantGate(
            NekoId id,
            String stage,
            int priority,
            StageScope scope,
            String lockedMessage,
            Set<NekoId> enchants,
            int minLevel
    ) implements StageGateDeclaration {
        public EnchantGate {
            StageGateKind.requireRuleId(id);
            StageGateKind.requireStage(stage);
            scope = scope == null ? StageScope.PLAYER : scope;
            lockedMessage = StageGateKind.normalizeText(lockedMessage);
            enchants = StageGateKind.immutableIdSet(enchants, "enchants");
            if (minLevel < 1) {
                throw new IllegalArgumentException(
                        "[NEKO-4071] Stage gate '" + id + "' minLevel must be >= 1, got: " + minLevel);
            }
            StageGateKind.requireTarget(id, !enchants.isEmpty());
        }

        @Override
        public StageGateKind kind() {
            return StageGateKind.ENCHANT;
        }
    }

    /**
     * Loot drop filtering/replacement stage gate declaration.
     */
    record LootGate(
            NekoId id,
            String stage,
            int priority,
            StageScope scope,
            String lockedMessage,
            Set<NekoId> items,
            Set<NekoId> tags,
            Set<NekoId> fromLootTables,
            Set<NekoId> fromEntities,
            @Nullable NekoId replacementItem
    ) implements StageGateDeclaration {
        public LootGate {
            StageGateKind.requireRuleId(id);
            StageGateKind.requireStage(stage);
            scope = scope == null ? StageScope.PLAYER : scope;
            lockedMessage = StageGateKind.normalizeText(lockedMessage);
            items = StageGateKind.immutableIdSet(items, "items");
            tags = StageGateKind.immutableTagSet(tags);
            fromLootTables = StageGateKind.immutableIdSet(fromLootTables, "fromLootTables");
            fromEntities = StageGateKind.immutableIdSet(fromEntities, "fromEntities");
            StageGateKind.requireTarget(
                    id,
                    !items.isEmpty()
                            || !tags.isEmpty()
                            || !fromLootTables.isEmpty()
                            || !fromEntities.isEmpty()
            );
        }

        @Override
        public StageGateKind kind() {
            return StageGateKind.LOOT;
        }
    }
}
