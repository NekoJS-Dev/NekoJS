package com.tkisor.nekojs.core.stage.gate;

import com.tkisor.nekojs.api.data.NekoId;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Platform-agnostic registry query contract used by stage gate preflight validation and
 * tag/namespace selector expansion across NeoForge 1.21.1/26.x and Fabric 26.x.
 *
 * <p>This interface resides in {@code common} with zero Minecraft or loader dependencies.
 * Platform adapters supply live implementations backed by game registries, while unit tests
 * without a running game server can use {@link #PERMISSIVE}.
 */
public interface StageRegistryAccess {

    record RecipeDescriptor(NekoId recipeId, @Nullable NekoId recipeTypeId, @Nullable NekoId outputItemId) {
    }

    /**
     * Permissive fallback for standalone unit tests where no Minecraft registries are bound.
     * All existence checks return {@code true} and all tag/namespace expansions return {@link Set#of()}.
     */
    StageRegistryAccess PERMISSIVE = new StageRegistryAccess() {
        @Override
        public boolean itemExists(NekoId id) {
            return true;
        }

        @Override
        public boolean blockExists(NekoId id) {
            return true;
        }

        @Override
        public boolean menuExists(NekoId id) {
            return true;
        }

        @Override
        public boolean entityTypeExists(NekoId id) {
            return true;
        }

        @Override
        public boolean effectExists(NekoId id) {
            return true;
        }

        @Override
        public boolean enchantmentExists(NekoId id) {
            return true;
        }

        @Override
        public boolean worldRegistriesBound() {
            return false;
        }

        @Override
        public boolean recipeTypeExists(NekoId id) {
            return true;
        }

        @Override
        public Set<NekoId> expandItemTag(NekoId tagId) {
            return Set.of();
        }

        @Override
        public Set<NekoId> expandBlockTag(NekoId tagId) {
            return Set.of();
        }

        @Override
        public Set<NekoId> expandEntityTypeTag(NekoId tagId) {
            return Set.of();
        }

        @Override
        public Set<NekoId> itemsInNamespace(String namespace) {
            return Set.of();
        }

        @Override
        public Set<NekoId> blocksInNamespace(String namespace) {
            return Set.of();
        }

        @Override
        public Set<NekoId> entitiesInNamespace(String namespace) {
            return Set.of();
        }
    };

    /** Returns {@code true} if an item with the given registry ID exists. */
    boolean itemExists(NekoId id);

    /** Returns {@code true} if a block with the given registry ID exists. */
    boolean blockExists(NekoId id);

    /** Returns {@code true} if a menu/container type with the given registry ID exists. */
    boolean menuExists(NekoId id);

    /** Returns {@code true} if an entity type with the given registry ID exists. */
    boolean entityTypeExists(NekoId id);

    /** Returns {@code true} if a mob/status effect with the given registry ID exists. */
    boolean effectExists(NekoId id);

    default boolean enchantmentExists(NekoId id) {
        return false;
    }

    default boolean worldRegistriesBound() {
        return true;
    }

    default boolean dimensionExists(NekoId id) {
        return false;
    }

    default boolean recipeTableBound() {
        return false;
    }

    default boolean recipeExists(NekoId id) {
        return false;
    }

    default boolean recipeTypeExists(NekoId id) {
        return false;
    }

    default List<RecipeDescriptor> recipeDescriptors() {
        return List.of();
    }

    /** Expands an item tag ID (without leading {@code '#'}) to all matching item IDs. */
    Set<NekoId> expandItemTag(NekoId tagId);

    /** Expands a block tag ID (without leading {@code '#'}) to all matching block IDs. */
    Set<NekoId> expandBlockTag(NekoId tagId);

    /** Expands an entity type tag ID (without leading {@code '#'}) to all matching entity type IDs. */
    Set<NekoId> expandEntityTypeTag(NekoId tagId);

    /** Returns all registered item IDs belonging to the given namespace. */
    Set<NekoId> itemsInNamespace(String namespace);

    /** Returns all registered block IDs belonging to the given namespace. */
    Set<NekoId> blocksInNamespace(String namespace);

    /** Returns all registered entity type IDs belonging to the given namespace. */
    Set<NekoId> entitiesInNamespace(String namespace);
}
