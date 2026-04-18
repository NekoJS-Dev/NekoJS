package com.tkisor.nekojs.wrapper.item;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.wrapper.NekoWrapper;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class IngredientJS implements NekoWrapper<Ingredient> {

    private final List<String> rawIds = new ArrayList<>();

    public IngredientJS() {}

    public IngredientJS(String... ids) {
        for (String id : ids) {
            this.rawIds.add(formatId(id));
        }
    }

    public IngredientJS or(String id) {
        this.rawIds.add(formatId(id));
        return this;
    }

    public IngredientJS or(IngredientJS other) {
        this.rawIds.addAll(other.rawIds);
        return this;
    }

    private String formatId(String inputStr) {
        if (inputStr == null || inputStr.isBlank()) {
            throw new IllegalArgumentException("[NekoJS] Ingredient ID cannot be empty!");
        }
        if (inputStr.startsWith("#")) {
            return inputStr.contains(":") ? inputStr : "#minecraft:" + inputStr.substring(1);
        } else {
            return inputStr.contains(":") ? inputStr : "minecraft:" + inputStr;
        }
    }

    @Override
    public Ingredient unwrap() {
        if (this.rawIds.isEmpty()) return Ingredient.of(Items.BARRIER);

        List<Holder<Item>> allHolders = new ArrayList<>();

        for (String rawId : this.rawIds) {
            boolean isTag = rawId.startsWith("#");
            String cleanId = isTag ? rawId.substring(1) : rawId;
            Identifier loc = Identifier.tryParse(cleanId);

            if (loc == null) {
                NekoJS.LOGGER.debug("[IngredientJS] Invalid ingredient ID format: {}", rawId);
                continue;
            }

            if (isTag) {
                TagKey<Item> tagKey = TagKey.create(Registries.ITEM, loc);
                Optional<HolderSet.Named<Item>> tagHolders = BuiltInRegistries.ITEM.get(tagKey);

                if (tagHolders.isPresent()) {
                    tagHolders.get().forEach(allHolders::add);
                } else {
                    NekoJS.LOGGER.debug("[IngredientJS] Item tag not found: {}. Please check for typos!", rawId);
                }
            } else {
                Item item = BuiltInRegistries.ITEM.getValue(loc);
                if (item != Items.AIR) {
                    allHolders.add(item.builtInRegistryHolder());
                } else {
                    NekoJS.LOGGER.debug("[IngredientJS] Item not found: {}. Please check for typos!", rawId);
                }
            }
        }

        if (allHolders.isEmpty()) {
            NekoJS.LOGGER.debug("[IngredientJS] Ingredient is empty, using Barrier as fallback.");
            return Ingredient.of(Items.BARRIER);
        }

        return Ingredient.of(HolderSet.direct(allHolders));
    }
}