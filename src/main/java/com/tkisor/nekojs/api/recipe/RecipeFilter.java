// 版本差异下沉到 RecipeEventJS 的版本中立静态助手（getRecipeOutputId 的双参重载 /
// recipeHolderId / recipeGroup / ingredientMatches），本文件因此零版本守卫。
package com.tkisor.nekojs.api.recipe;

import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import net.minecraft.core.HolderLookup;
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

/**
 * Filter for querying/removing recipes. Supports nested logical combinators.
 *
 * <h2>JS usage</h2>
 * <pre>
 * { mod: "minecraft" }                          — match by mod ID
 * { type: "minecraft:crafting_shaped" }         — match by recipe type
 * { output: "minecraft:stick" }                 — match by output item
 * { input: "#minecraft:planks" }                — match by input ingredient
 * { and: [{mod:"minecraft"}, {output:"stick"}] }— logical AND
 * { or: [...] }                                  — logical OR
 * { not: {...} }                                 — logical NOT
 * { idStartsWith: "minecraft:chest" }           — ID prefix match
 * </pre>
 *
 * <p>Filters are created from JS objects by {@link RecipeFilterAdapter}.
 */
public interface RecipeFilter {

    boolean test(RecipeHolder<?> holder, HolderLookup.Provider registries);

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
            String actualIdStr = RecipeEventJS.getRecipeOutputId(holder.value(), registries);
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

            for (Ingredient ingredient : ingredients) {
                if (RecipeEventJS.ingredientMatches(ingredient, tagKey, itemID, registries)) return true;
            }
            return false;
        }
    }

    record ByMod(String modId) implements RecipeFilter {
        @Override
        public boolean test(RecipeHolder<?> holder, HolderLookup.Provider registries) {
            return RecipeEventJS.recipeHolderId(holder).getNamespace().equals(modId);
        }
    }

    record ByGroup(String group) implements RecipeFilter {
        @Override
        public boolean test(RecipeHolder<?> holder, HolderLookup.Provider registries) {
            return RecipeEventJS.recipeGroup(holder.value()).equals(group);
        }
    }

    record ById(String recipeId, Identifier target) implements RecipeFilter {
        public ById(String recipeId) {
            this(recipeId, Identifier.tryParse(recipeId.contains(":") ? recipeId : "minecraft:" + recipeId));
        }

        @Override
        public boolean test(RecipeHolder<?> holder, HolderLookup.Provider registries) {
            return target != null && RecipeEventJS.recipeHolderId(holder).equals(target);
        }
    }

    record ByIdStartsWith(String prefix) implements RecipeFilter {
        @Override
        public boolean test(RecipeHolder<?> holder, HolderLookup.Provider registries) {
            return RecipeEventJS.recipeHolderId(holder).toString().startsWith(prefix);
        }
    }

    record ByIdEndsWith(String suffix) implements RecipeFilter {
        @Override
        public boolean test(RecipeHolder<?> holder, HolderLookup.Provider registries) {
            return RecipeEventJS.recipeHolderId(holder).toString().endsWith(suffix);
        }
    }

    record ByIdContains(String text) implements RecipeFilter {
        @Override
        public boolean test(RecipeHolder<?> holder, HolderLookup.Provider registries) {
            return RecipeEventJS.recipeHolderId(holder).toString().contains(text);
        }
    }

    record ByType(String type) implements RecipeFilter {
        @Override
        public boolean test(RecipeHolder<?> holder, HolderLookup.Provider registries) {
            return BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType()).toString().equals(type);
        }
    }
}
