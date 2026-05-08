package com.tkisor.nekojs.api.recipe;

import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface RecipeFilter {

    boolean test(RecipeHolder<?> holder, HolderLookup.Provider registries);

    static boolean isItemInHolderSet(HolderSet<Item> set, Identifier targetId, HolderLookup.RegistryLookup<Item> registry) {
        return set.unwrap().map(
                key -> {
                    var targetHolder = registry.get(ResourceKey.create(Registries.ITEM, targetId));
                    return targetHolder.isPresent() && targetHolder.get().is(key);
                },
                list -> {
                    for (var h : list) {
                        if (BuiltInRegistries.ITEM.getKey(h.value()).equals(targetId)) return true;
                    }
                    return false;
                }
        );
    }

    record And(List<RecipeFilter> filters) implements RecipeFilter {
        @Override
        public boolean test(RecipeHolder<?> holder, HolderLookup.Provider registries) {
            for (RecipeFilter f : filters) if (!f.test(holder, registries)) return false;
            return true;
        }
    }

    record Or(List<RecipeFilter> filters) implements RecipeFilter {
        @Override
        public boolean test(RecipeHolder<?> holder, HolderLookup.Provider registries) {
            for (RecipeFilter f : filters) if (f.test(holder, registries)) return true;
            return false;
        }
    }

    record Not(RecipeFilter filter) implements RecipeFilter {
        @Override
        public boolean test(RecipeHolder<?> holder, HolderLookup.Provider registries) {
            return !filter.test(holder, registries);
        }
    }

    record ByOutput(String rawId, @Nullable TagKey<Item> tagKey, @Nullable Identifier itemID) implements RecipeFilter {
        public ByOutput(String itemOrTag) {
            this(
                    itemOrTag,
                    itemOrTag.startsWith("#") ? TagKey.create(Registries.ITEM, Identifier.parse(itemOrTag.substring(1))) : null,
                    !itemOrTag.startsWith("#") ? Identifier.tryParse(itemOrTag.contains(":") ? itemOrTag : "minecraft:" + itemOrTag) : null
            );
        }

        @Override
        public boolean test(RecipeHolder<?> holder, HolderLookup.Provider registries) {
            String actualIdStr = RecipeEventJS.getRecipeOutputId(holder.value());
            if (actualIdStr == null) return false;

            Identifier actualId = Identifier.parse(actualIdStr);
            if (tagKey != null) {
                var itemRegistry = registries.lookupOrThrow(Registries.ITEM);
                var targetHolder = itemRegistry.get(ResourceKey.create(Registries.ITEM, actualId));
                return targetHolder.isPresent() && targetHolder.get().is(tagKey);
            }
            return actualId.equals(itemID);
        }
    }

    record ByInput(String rawId, @Nullable TagKey<Item> tagKey, @Nullable Identifier itemID) implements RecipeFilter {
        public ByInput(String itemOrTag) {
            this(
                    itemOrTag,
                    itemOrTag.startsWith("#") ? TagKey.create(Registries.ITEM, Identifier.parse(itemOrTag.substring(1))) : null,
                    !itemOrTag.startsWith("#") ? Identifier.tryParse(itemOrTag.contains(":") ? itemOrTag : "minecraft:" + itemOrTag) : null
            );
        }

        @Override
        public boolean test(RecipeHolder<?> holder, HolderLookup.Provider registries) {
            List<Ingredient> ingredients = RecipeEventJS.getIngredients(holder.value());
            var itemRegistry = registries.lookupOrThrow(Registries.ITEM);

            for (Ingredient ingredient : ingredients) {
                if (tagKey != null) {
                    boolean matches = ingredient.values.unwrap().map(
                            key -> key.equals(tagKey),
                            list -> {
                                var targetTagSet = itemRegistry.get(tagKey);
                                if (targetTagSet.isPresent()) {
                                    for (var h : list) {
                                        Identifier hId = BuiltInRegistries.ITEM.getKey(h.value());
                                        for (var tagH : targetTagSet.get()) {
                                            if (BuiltInRegistries.ITEM.getKey(tagH.value()).equals(hId)) return true;
                                        }
                                    }
                                }
                                return false;
                            }
                    );
                    if (matches) return true;
                }
                else if (itemID != null) {
                    if (!ingredient.isCustom()) {
                        if (isItemInHolderSet(ingredient.values, itemID, itemRegistry)) return true;
                    } else {
                        var itemHolder = itemRegistry.get(ResourceKey.create(Registries.ITEM, itemID));
                        if (itemHolder.isPresent()) {
                            try {
                                if (ingredient.acceptsItem(itemHolder.get())) return true;
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
            return false;
        }
    }

    record ByMod(String modId) implements RecipeFilter {
        @Override
        public boolean test(RecipeHolder<?> holder, HolderLookup.Provider registries) {
            return holder.id().identifier().getNamespace().equals(modId);
        }
    }

    record ById(String recipeId, Identifier target) implements RecipeFilter {
        public ById(String recipeId) {
            this(recipeId, Identifier.tryParse(recipeId.contains(":") ? recipeId : "minecraft:" + recipeId));
        }

        @Override
        public boolean test(RecipeHolder<?> holder, HolderLookup.Provider registries) {
            return target != null && holder.id().identifier().equals(target);
        }
    }

    record ByType(String type) implements RecipeFilter {
        @Override
        public boolean test(RecipeHolder<?> holder, HolderLookup.Provider registries) {
            return BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType()).toString().equals(type);
        }
    }
}