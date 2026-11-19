package com.tkisor.nekojs.stage;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.core.stage.gate.StageRegistryAccess;
import com.tkisor.nekojs.platform.NekoIdCompat;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Live game-registry implementation of {@link StageRegistryAccess} shared across NeoForge and Fabric.
 */
public final class MinecraftStageRegistryAccess implements StageRegistryAccess {

    private record RecipeTable(List<RecipeDescriptor> descriptors, Set<NekoId> ids) {
        private RecipeTable(List<RecipeDescriptor> descriptors) {
            this(List.copyOf(descriptors), Set.copyOf(descriptors.stream().map(RecipeDescriptor::recipeId).toList()));
        }
    }

    private final @Nullable RecipeTable candidateRecipes;
    private volatile @Nullable RegistryAccess registryAccess;
    private volatile @Nullable RecipeTable publishedRecipes;

    /** Creates a stage registry access backed by built-in registries with no dynamic registries bound yet. */
    public MinecraftStageRegistryAccess() {
        this(null);
    }

    /** Creates a stage registry access initialized with an optional dynamic {@link RegistryAccess}. */
    public MinecraftStageRegistryAccess(@Nullable RegistryAccess access) {
        this.candidateRecipes = null;
        this.registryAccess = access;
    }

    private MinecraftStageRegistryAccess(@Nullable RegistryAccess access, RecipeTable candidateRecipes) {
        this.registryAccess = access;
        this.candidateRecipes = candidateRecipes;
    }

    public StageRegistryAccess withRecipeDescriptors(@Nullable RegistryAccess access, List<RecipeDescriptor> descriptors) {
        return new MinecraftStageRegistryAccess(access, new RecipeTable(descriptors));
    }

    public void bindRecipeDescriptors(@Nullable List<RecipeDescriptor> descriptors) {
        this.publishedRecipes = descriptors == null ? null : new RecipeTable(descriptors);
    }

    /** Binds or clears the server's dynamic {@link RegistryAccess} used for enchantment lookups. */
    public void bindRegistries(@Nullable RegistryAccess access) {
        this.registryAccess = access;
        if (access == null) {
            this.publishedRecipes = null;
        }
    }

    /** Returns the currently bound dynamic {@link RegistryAccess}, or {@code null} when unbound. */
    public @Nullable RegistryAccess boundRegistries() {
        return this.registryAccess;
    }

    /**
     * Converts a portable {@link NekoId} to a platform {@link Identifier} via {@link NekoIdCompat},
     * falling back to direct construction when {@link NekoIdCompat} has not been initialized in isolated tests.
     */
    public static Identifier toMcId(NekoId id) {
        Objects.requireNonNull(id, "id");
        try {
            return Identifier.class.cast(NekoIdCompat.toPlatformId(id));
        } catch (IllegalStateException uninitialized) {
            return Identifier.fromNamespaceAndPath(id.namespace(), id.path());
        }
    }

    /**
     * Converts a nullable {@link NekoId} to a platform {@link Identifier}, returning {@code null}
     * if the identifier is null or rejected by resource location syntax validation.
     */
    public static @Nullable Identifier toMcIdOrNull(@Nullable NekoId id) {
        if (id == null) {
            return null;
        }
        try {
            return toMcId(id);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    /**
     * Converts a platform {@link Identifier} to a portable {@link NekoId} via {@link NekoIdCompat},
     * falling back to {@link NekoId#resource(String, String)} in isolated tests.
     */
    public static NekoId fromMcId(Identifier id) {
        Objects.requireNonNull(id, "id");
        try {
            return NekoIdCompat.fromPlatformId(id);
        } catch (IllegalStateException uninitialized) {
            return NekoId.resource(id.getNamespace(), id.getPath());
        }
    }

    /** Extracts the platform {@link Identifier} from a {@link ResourceKey} across 1.21.1 and 26.x. */
    public static Identifier keyLocation(ResourceKey<?> key) {
        Objects.requireNonNull(key, "key");
//? if >=26 {
        return key.identifier();
//?} else {
        /*return key.location();
*///?}
    }

    /** Converts a {@link ResourceKey}'s target location into a portable {@link NekoId}. */
    public static NekoId fromResourceKey(ResourceKey<?> key) {
        return fromMcId(keyLocation(key));
    }

    @Override
    public boolean itemExists(NekoId id) {
        Identifier mcId = toMcIdOrNull(id);
        return mcId != null && BuiltInRegistries.ITEM.containsKey(mcId);
    }

    @Override
    public boolean blockExists(NekoId id) {
        Identifier mcId = toMcIdOrNull(id);
        return mcId != null && BuiltInRegistries.BLOCK.containsKey(mcId);
    }

    @Override
    public boolean menuExists(NekoId id) {
        Identifier mcId = toMcIdOrNull(id);
        return mcId != null && BuiltInRegistries.MENU.containsKey(mcId);
    }

    @Override
    public boolean entityTypeExists(NekoId id) {
        Identifier mcId = toMcIdOrNull(id);
        return mcId != null && BuiltInRegistries.ENTITY_TYPE.containsKey(mcId);
    }

    @Override
    public boolean effectExists(NekoId id) {
        Identifier mcId = toMcIdOrNull(id);
        return mcId != null && BuiltInRegistries.MOB_EFFECT.containsKey(mcId);
    }

    @Override
    public boolean worldRegistriesBound() {
        return boundRegistries() != null;
    }

    @Override
    public boolean dimensionExists(NekoId id) {
        return worldEntryExists(Registries.LEVEL_STEM, id);
    }

    @Override
    public boolean enchantmentExists(NekoId id) {
        return worldEntryExists(Registries.ENCHANTMENT, id);
    }

    private <T> boolean worldEntryExists(ResourceKey<? extends Registry<T>> registry, NekoId id) {
        Identifier mcId = toMcIdOrNull(id);
        HolderLookup.Provider access = boundRegistries();
        return mcId != null && access != null && access.lookup(registry)
                .flatMap(lookup -> lookup.get(ResourceKey.create(registry, mcId)))
                .isPresent();
    }

    @Override
    public boolean recipeTableBound() {
        return this.candidateRecipes != null || this.publishedRecipes != null;
    }

    @Override
    public boolean recipeExists(NekoId id) {
        RecipeTable table = this.candidateRecipes != null ? this.candidateRecipes : this.publishedRecipes;
        return table != null && table.ids().contains(id);
    }

    @Override
    public boolean recipeTypeExists(NekoId id) {
        Identifier mcId = toMcIdOrNull(id);
        return mcId != null && BuiltInRegistries.RECIPE_TYPE.containsKey(mcId);
    }

    @Override
    public List<RecipeDescriptor> recipeDescriptors() {
        RecipeTable table = this.candidateRecipes != null ? this.candidateRecipes : this.publishedRecipes;
        return table == null ? List.of() : table.descriptors();
    }

    @Override
    public Set<NekoId> expandItemTag(NekoId tagId) {
        return expandRegistryTag(BuiltInRegistries.ITEM, Registries.ITEM, tagId);
    }

    @Override
    public Set<NekoId> expandBlockTag(NekoId tagId) {
        return expandRegistryTag(BuiltInRegistries.BLOCK, Registries.BLOCK, tagId);
    }

    @Override
    public Set<NekoId> expandEntityTypeTag(NekoId tagId) {
        return expandRegistryTag(BuiltInRegistries.ENTITY_TYPE, Registries.ENTITY_TYPE, tagId);
    }

    @Override
    public Set<NekoId> itemsInNamespace(String namespace) {
        return entriesInNamespace(BuiltInRegistries.ITEM, namespace);
    }

    @Override
    public Set<NekoId> blocksInNamespace(String namespace) {
        return entriesInNamespace(BuiltInRegistries.BLOCK, namespace);
    }

    @Override
    public Set<NekoId> entitiesInNamespace(String namespace) {
        return entriesInNamespace(BuiltInRegistries.ENTITY_TYPE, namespace);
    }

    private static <T> Set<NekoId> expandRegistryTag(
            Registry<T> registry,
            ResourceKey<? extends Registry<T>> registryKey,
            @Nullable NekoId tagId
    ) {
        Identifier mcTagId = toMcIdOrNull(tagId);
        if (mcTagId == null) {
            return Set.of();
        }
        TagKey<T> tagKey = TagKey.create(registryKey, mcTagId);
        Set<NekoId> expanded = new LinkedHashSet<>();
        for (Holder<T> holder : registry.getTagOrEmpty(tagKey)) {
            holder.unwrapKey().ifPresent(key -> expanded.add(fromResourceKey(key)));
        }
        return expanded.isEmpty() ? Set.of() : Collections.unmodifiableSet(expanded);
    }

    private static <T> Set<NekoId> entriesInNamespace(Registry<T> registry, @Nullable String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return Set.of();
        }
        String targetNamespace = namespace.trim();
        Set<NekoId> matched = new LinkedHashSet<>();
        for (Identifier key : registry.keySet()) {
            if (targetNamespace.equals(key.getNamespace())) {
                matched.add(fromMcId(key));
            }
        }
        return matched.isEmpty() ? Set.of() : Collections.unmodifiableSet(matched);
    }
}
