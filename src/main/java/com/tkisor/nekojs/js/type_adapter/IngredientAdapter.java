package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.JSTypeAdapter;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import org.graalvm.polyglot.Value;

import java.util.Optional;
import java.util.stream.Stream;

public final class IngredientAdapter implements JSTypeAdapter<Ingredient> {
    @Override
    public Class<Ingredient> getTargetClass() {
        return Ingredient.class;
    }

    @Override
    public boolean canConvert(Value value) {
        return value.isString();
    }

    @Override
    public Ingredient convert(Value value) {
        return IngredientAdapter.stringToIngredient(value.asString());
    }

    static Ingredient stringToIngredient(String str) {
        if (str == null || str.isEmpty()) return Ingredient.of(Stream.empty());

        Identifier id = Identifier.tryParse(str);
        Optional<Holder.Reference<Item>> item = BuiltInRegistries.ITEM.get(id);
        if (item.isEmpty()) throw new IllegalArgumentException("找不到物品: " + str);

        return Ingredient.of(item.get().value());
    }
}

