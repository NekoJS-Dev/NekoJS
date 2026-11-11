package com.tkisor.nekujs.bridge;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import com.tkisor.nekojs.js.type_adapter.IngredientAdapter;
import dev.latvian.mods.kubejs.item.ItemPredicate;
import graal.graalvm.polyglot.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.crafting.Ingredient;

/** Converts legacy predicate arguments through native ingredients or the caller's Graal callback. */
public final class LegacyItemPredicateAdapter implements JSTypeAdapter<ItemPredicate> {
    private final IngredientAdapter ingredients = new IngredientAdapter();

    @Override public Class<ItemPredicate> getTargetClass() { return ItemPredicate.class; }

    @Override public ConversionPrecedence getPrecedence() { return ConversionPrecedence.HIGH; }

    @Override
    public boolean test(Value value) {
        return value == null || value.isNull()
                || value.isHostObject() && value.asHostObject() instanceof ItemPredicate
                || value.canExecute() || ingredients.test(value);
    }

    @Override
    public ItemPredicate apply(Value value) {
        if (value == null || value.isNull()) return ItemPredicate.NONE;
        if (value.isHostObject() && value.asHostObject() instanceof ItemPredicate predicate) return predicate;
        if (value.canExecute()) return stack -> value.execute(stack).asBoolean();
        if (value.isString()) {
            String input = value.asString();
            if (input.isBlank() || input.equals("-")) return ItemPredicate.NONE;
            if (input.equals("*")) return ItemPredicate.ALL;
        }
        Ingredient ingredient = ingredients.apply(value);
        return ingredient.isEmpty() ? ItemPredicate.NONE : ingredient::test;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        var shapes = new ArrayList<AdapterInputShape>();
        shapes.add(AdapterInputShape.self());
        ingredients.inputShapes().stream().map(LegacyItemPredicateAdapter::ingredientShape).forEach(shapes::add);
        shapes.add(AdapterInputShape.raw("((stack: $ItemStack) => boolean)"));
        return List.copyOf(shapes);
    }

    /** Delegated self references belong to Ingredient, even inside structured input shapes. */
    private static AdapterInputShape ingredientShape(AdapterInputShape shape) {
        return switch (shape) {
            case AdapterInputShape.SelfValue ignored -> AdapterInputShape.host(Ingredient.class);
            case AdapterInputShape.ArrayOfValue array -> AdapterInputShape.arrayOf(ingredientShape(array.element()));
            case AdapterInputShape.ObjectValue object -> AdapterInputShape.object(object.slots().stream()
                    .map(slot -> new AdapterInputShape.Slot(slot.name(), ingredientShape(slot.shape()), slot.required())).toList());
            case AdapterInputShape.UnionValue union -> new AdapterInputShape.UnionValue(
                    union.members().stream().map(LegacyItemPredicateAdapter::ingredientShape).toList());
            case AdapterInputShape.TemplateValue template -> AdapterInputShape.template(
                    template.prefix(), ingredientShape(template.hole()), template.suffix());
            default -> shape;
        };
    }

    @Override
    public Optional<String> syntaxDoc() {
        return Optional.of("Native ingredient input or (stack) => boolean; null, blank, and - select nothing; * selects all items.");
    }
}
