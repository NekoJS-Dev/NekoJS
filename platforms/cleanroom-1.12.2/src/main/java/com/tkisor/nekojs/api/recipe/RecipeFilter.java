package com.tkisor.nekojs.api.recipe;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;

import java.util.List;

/**
 * 1.12.2 配方过滤器：脚本传给 {@code event.find/remove/dump/...} 的结构化过滤条件。
 *
 * <p>与 datapack 时代平台的 {@code RecipeFilter} 结构对齐（And/Or/Not/ByXxx），但
 * {@link #test} 的入参是 cleanroom 自己的 {@link RecipeEntryJS}（1.12.2 无
 * {@code RecipeHolder}/{@code HolderLookup}）。匹配语义与 {@code RecipeFilterAdapter}
 * 的历史实现逐条一致：顶层数组为 Or，扁平对象为 And，{@code output}/{@code input}
 * 支持 {@code #tag} 前缀（物品走 {@link OreDictionary}）。
 */
public interface RecipeFilter {

    boolean test(RecipeEntryJS entry);

    record And(List<RecipeFilter> filters) implements RecipeFilter {
        @Override
        public boolean test(RecipeEntryJS entry) {
            for (RecipeFilter f : filters) if (!f.test(entry)) return false;
            return true;
        }
    }

    record Or(List<RecipeFilter> filters) implements RecipeFilter {
        @Override
        public boolean test(RecipeEntryJS entry) {
            for (RecipeFilter f : filters) if (f.test(entry)) return true;
            return false;
        }
    }

    record Not(RecipeFilter filter) implements RecipeFilter {
        @Override
        public boolean test(RecipeEntryJS entry) {
            return !filter.test(entry);
        }
    }

    record ByOutput(String itemOrTag) implements RecipeFilter {
        @Override
        public boolean test(RecipeEntryJS entry) {
            ItemStack output = entry.output();
            if (output.isEmpty()) return false;
            if (itemOrTag.startsWith("#")) {
                String oreName = itemOrTag.substring(1);
                for (ItemStack ore : OreDictionary.getOres(oreName)) {
                    if (ore != null && !ore.isEmpty() && output.isItemEqual(ore)) return true;
                }
                return false;
            }
            ResourceLocation target = parseId(itemOrTag);
            ResourceLocation actual = output.getItem().getRegistryName();
            return target != null && target.equals(actual);
        }
    }

    record ByInput(String itemOrTag) implements RecipeFilter {
        @Override
        public boolean test(RecipeEntryJS entry) {
            boolean isTag = itemOrTag.startsWith("#");
            String body = isTag ? itemOrTag.substring(1) : itemOrTag;
            ResourceLocation target = isTag ? null : parseId(body);
            for (Ingredient ing : entry.ingredients()) {
                if (ing == null || ing == Ingredient.EMPTY) continue;
                for (ItemStack stack : ing.getMatchingStacks()) {
                    if (stack == null || stack.isEmpty()) continue;
                    if (isTag) {
                        int[] ids = OreDictionary.getOreIDs(stack);
                        for (int id : ids) {
                            if (body.equals(OreDictionary.getOreName(id))) return true;
                        }
                    } else if (target != null) {
                        ResourceLocation rl = stack.getItem().getRegistryName();
                        if (target.equals(rl)) return true;
                    }
                }
            }
            return false;
        }
    }

    record ByMod(String modId) implements RecipeFilter {
        @Override
        public boolean test(RecipeEntryJS entry) {
            return entry.id().startsWith(modId + ":") || modId.equals(modOf(entry.id()));
        }
    }

    record ByGroup(String group) implements RecipeFilter {
        @Override
        public boolean test(RecipeEntryJS entry) {
            return group.equals(entry.group());
        }
    }

    record ById(String recipeId) implements RecipeFilter {
        @Override
        public boolean test(RecipeEntryJS entry) {
            return entry.id().equals(normalizeId(recipeId));
        }
    }

    record ByIdStartsWith(String prefix) implements RecipeFilter {
        @Override
        public boolean test(RecipeEntryJS entry) {
            return entry.id().startsWith(prefix);
        }
    }

    record ByIdEndsWith(String suffix) implements RecipeFilter {
        @Override
        public boolean test(RecipeEntryJS entry) {
            return entry.id().endsWith(suffix);
        }
    }

    record ByIdContains(String text) implements RecipeFilter {
        @Override
        public boolean test(RecipeEntryJS entry) {
            return entry.id().contains(text);
        }
    }

    record ByType(String type) implements RecipeFilter {
        @Override
        public boolean test(RecipeEntryJS entry) {
            return type.equals(entry.type());
        }
    }

    // ---- id 解析辅助（自 RecipeEventJS 迁移，见其历史 matchesFilter 实现） ----

    private static ResourceLocation parseId(String raw) {
        try {
            String full = raw.contains(":") ? raw : "minecraft:" + raw;
            return new ResourceLocation(full);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String modOf(String id) {
        int colon = id.indexOf(':');
        return colon <= 0 ? "" : id.substring(0, colon);
    }

    private static String normalizeId(String raw) {
        ResourceLocation rl = parseId(raw);
        return rl == null ? raw : rl.toString();
    }
}
