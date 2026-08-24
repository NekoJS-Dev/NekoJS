package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import com.tkisor.nekojs.wrapper.item.SizedIngredientJS;
import graal.graalvm.polyglot.Value;
import net.minecraft.item.crafting.Ingredient;

import java.util.List;
import java.util.Optional;

import static com.tkisor.nekojs.api.AdapterInputShape.*;

/**
 * 1.12.2 SizedIngredient 适配器。
 *
 * <p>1.12.2 has no native {@code SizedIngredient} class, so this adapter resolves to
 * {@link SizedIngredientJS} (a small value class holding {@link Ingredient} + count)
 * instead of the legacy {@code Object[]} destructure tuple. Scripts get a proper typed
 * host object with {@code ingredient()}/{@code count()}/{@code unwrap()} accessors.
 *
 * <h2>Accepted inputs</h2>
 * <ul>
 *   <li>{@link SizedIngredientJS} host object (passthrough)</li>
 *   <li>{@link Ingredient} host object (count defaults to 1)</li>
 *   <li>string ({@code "minecraft:stone"}) / array / object ingredient shapes —
 *       resolved via {@link IngredientAdapter}, count defaults to 1</li>
 *   <li>{@code { ingredient|item|tag|mod|regex|wildcard|filter|any|all|not, count }}
 *       — ingredient resolved via {@link IngredientAdapter}, count from {@code count}</li>
 * </ul>
 */
public final class SizedIngredientAdapter implements JSTypeAdapter<SizedIngredientJS> {

    @Override
    public Class<SizedIngredientJS> getTargetClass() {
        return SizedIngredientJS.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                host(SizedIngredientJS.class),
                host(Ingredient.class),
                // 字符串语法逐个单列：裸 string 会吞掉联合里的 id 字面量补全
                registry("Item"),
                template("#", registryTag("Item")),
                template("@", namespace()),
                literal("*"),
                template("/", string()),
                arrayOf(self()),
                object(
                        Slot.opt("ingredient", host(Ingredient.class)),
                        Slot.opt("item", registry("Item")),
                        Slot.opt("tag", registryTag("Item")),
                        Slot.opt("mod", namespace()),
                        Slot.opt("regex", string()),
                        Slot.opt("wildcard", bool()),
                        Slot.opt("filter", raw("((item: $ItemStack) => boolean)")),
                        Slot.opt("any", arrayOf(self())),
                        Slot.opt("all", arrayOf(self())),
                        Slot.opt("not", self()),
                        Slot.opt("count", number())));
    }

    @Override
    public Optional<String> syntaxDoc() {
        return Optional.of("item:id | #tag | $Ingredient | { ingredient|item|tag|mod|regex|wildcard|filter|any|all|not, count }");
    }

    @Override
    public boolean test(Value value) {
        if (value == null || value.isNull()) return false;
        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            return obj instanceof SizedIngredientJS || obj instanceof Ingredient;
        }
        return value.isString() || value.hasArrayElements() || value.hasMembers();
    }

    @Override
    public SizedIngredientJS apply(Value value) {
        try {
            if (value == null || value.isNull()) {
                throw new ValueConversionException(SizedIngredientJS.class, "ingredient / sized ingredient object", value,
                    "sized ingredient cannot be null");
            }

            // Host passthrough
            if (value.isHostObject()) {
                Object obj = value.asHostObject();
                if (obj instanceof SizedIngredientJS sized) return sized;
                if (obj instanceof Ingredient ingredient) return new SizedIngredientJS(ingredient, 1);
            }

            // Object form: look for an explicit count, then resolve the ingredient from
            // either a dedicated 'ingredient' field or the whole object (which lets
            // { item, tag, mod, regex, wildcard, any, all, not } work directly).
            if (value.hasMembers()) {
                int count = 1;
                if (value.hasMember("count")) {
                    count = parseCount(value.getMember("count"));
                }
                Value ingredientValue;
                if (value.hasMember("ingredient")) {
                    ingredientValue = value.getMember("ingredient");
                } else {
                    ingredientValue = value;
                }
                Ingredient ingredient = IngredientAdapter.fromValue(ingredientValue);
                return new SizedIngredientJS(ingredient, count);
            }

            // String / array / scalar — resolve as a plain ingredient with count 1.
            Ingredient ingredient = IngredientAdapter.fromValue(value);
            return new SizedIngredientJS(ingredient, 1);
        } catch (ValueConversionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ValueConversionException(SizedIngredientJS.class, "ingredient / ingredient object with count", value,
                e.getMessage(), e);
        }
    }

    private static int parseCount(Value value) {
        if (!value.isNumber() || !value.fitsInInt()) {
            throw new ValueConversionException(SizedIngredientJS.class, "integer count", value,
                "SizedIngredient count must be an integer");
        }
        int count = value.asInt();
        if (count <= 0) {
            throw new ValueConversionException(SizedIngredientJS.class, "positive integer count", count,
                "SizedIngredient count must be positive: " + count);
        }
        return count;
    }
}
