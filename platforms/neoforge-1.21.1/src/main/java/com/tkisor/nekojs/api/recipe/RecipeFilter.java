package com.tkisor.nekojs.api.recipe;

import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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

    record ByOutput(String rawId, @Nullable TagKey<Item> tagKey, @Nullable ResourceLocation itemID) implements RecipeFilter {
        public ByOutput(String itemOrTag) {
            this(
                    itemOrTag,
                    itemOrTag.startsWith("#") ? TagKey.create(Registries.ITEM, ResourceLocation.parse(itemOrTag.substring(1))) : null,
                    !itemOrTag.startsWith("#") ? ResourceLocation.tryParse(itemOrTag.contains(":") ? itemOrTag : "minecraft:" + itemOrTag) : null
            );
        }

        @Override
        public boolean test(RecipeHolder<?> holder, HolderLookup.Provider registries) {
            // 1.21.1: 补充 registries 参数
            String actualIdStr = RecipeEventJS.getRecipeOutputId(holder.value(), registries);
            if (actualIdStr == null) return false;

            ResourceLocation actualId = ResourceLocation.parse(actualIdStr);
            if (tagKey != null) {
                var itemRegistry = registries.lookupOrThrow(Registries.ITEM);
                var targetHolder = itemRegistry.get(ResourceKey.create(Registries.ITEM, actualId));
                return targetHolder.isPresent() && targetHolder.get().is(tagKey);
            }
            return actualId.equals(itemID);
        }
    }

    record ByInput(String rawId, @Nullable TagKey<Item> tagKey, @Nullable ResourceLocation itemID) implements RecipeFilter {
        public ByInput(String itemOrTag) {
            this(
                    itemOrTag,
                    itemOrTag.startsWith("#") ? TagKey.create(Registries.ITEM, ResourceLocation.parse(itemOrTag.substring(1))) : null,
                    !itemOrTag.startsWith("#") ? ResourceLocation.tryParse(itemOrTag.contains(":") ? itemOrTag : "minecraft:" + itemOrTag) : null
            );
        }

        @Override
        public boolean test(RecipeHolder<?> holder, HolderLookup.Provider registries) {
            List<Ingredient> ingredients = RecipeEventJS.getIngredients(holder.value());

            for (Ingredient ingredient : ingredients) {
                if (ingredient.isEmpty()) continue;

                if (tagKey != null) {
                    // 如果用标签过滤，只要材料中包含该标签下的任何物品即可
                    for (ItemStack stack : ingredient.getItems()) {
                        if (stack.is(tagKey)) return true;
                    }
                }
                else if (itemID != null) {
                    // 如果用特定物品过滤，直接利用 Ingredient 自带的 test 方法
                    Item targetItem = BuiltInRegistries.ITEM.get(itemID);
                    if (ingredient.test(new ItemStack(targetItem))) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    record ByMod(String modId) implements RecipeFilter {
        @Override
        public boolean test(RecipeHolder<?> holder, HolderLookup.Provider registries) {
            // 1.21.1: id() 直接就是 ResourceLocation
            return holder.id().getNamespace().equals(modId);
        }
    }

    record ByGroup(String group) implements RecipeFilter {
        @Override
        public boolean test(RecipeHolder<?> holder, HolderLookup.Provider registries) {
            return holder.value().getGroup().equals(group);
        }
    }

    record ById(String recipeId, ResourceLocation target) implements RecipeFilter {
        public ById(String recipeId) {
            this(recipeId, ResourceLocation.tryParse(recipeId.contains(":") ? recipeId : "minecraft:" + recipeId));
        }

        @Override
        public boolean test(RecipeHolder<?> holder, HolderLookup.Provider registries) {
            // 1.21.1: id() 直接就是 ResourceLocation
            return target != null && holder.id().equals(target);
        }
    }

    record ByIdStartsWith(String prefix) implements RecipeFilter {
        @Override
        public boolean test(RecipeHolder<?> holder, HolderLookup.Provider registries) {
            return holder.id().toString().startsWith(prefix);
        }
    }

    record ByIdEndsWith(String suffix) implements RecipeFilter {
        @Override
        public boolean test(RecipeHolder<?> holder, HolderLookup.Provider registries) {
            return holder.id().toString().endsWith(suffix);
        }
    }

    record ByIdContains(String text) implements RecipeFilter {
        @Override
        public boolean test(RecipeHolder<?> holder, HolderLookup.Provider registries) {
            return holder.id().toString().contains(text);
        }
    }

    record ByType(String type) implements RecipeFilter {
        @Override
        public boolean test(RecipeHolder<?> holder, HolderLookup.Provider registries) {
            return BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType()).toString().equals(type);
        }
    }
}