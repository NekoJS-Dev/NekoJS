package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import com.tkisor.nekojs.wrapper.item.IngredientResolver;
import java.util.List;
import java.util.Optional;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.wrapper.item.IngredientJS;
import graal.graalvm.polyglot.Value;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public final class IngredientAdapter implements JSTypeAdapter<Ingredient> {

    @Override
    public Class<Ingredient> getTargetClass() {
        return Ingredient.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                // 逐个字符串语法单列（而不是一个裸 string）：裸 string 会吞掉联合里的字面量补全，
                // 导致原料位置完全没有 id 提示；模板字面量类型不会
                registry("Item"),
                template("#", registryTag("Item")),
                template("@", namespace()),
                literal("*"),
                template("/", string()),
                arrayOf(self()),
                host(ItemStack.class),
                host(Item.class),
                host(NekoId.class),
                object(
                        Slot.opt("item", registry("Item")),
                        Slot.opt("tag", registryTag("Item")),
                        Slot.opt("mod", namespace()),
                        Slot.opt("regex", string()),
                        Slot.opt("wildcard", bool()),
                        Slot.opt("filter", raw("((item: $ItemStack) => boolean)")),
                        Slot.opt("any", arrayOf(self())),
                        Slot.opt("all", arrayOf(self())),
                        Slot.opt("not", self())));
    }

    @Override
    public Optional<String> syntaxDoc() {
        return Optional.of("item:id | #tag | @mod | * | /regex/ | { item?|tag?|mod?|regex?|wildcard?|filter?|any?|all?|not? }");
    }

    @Override
    public boolean test(Value value) {
        if (value.isNull() || value.isString() || value.hasArrayElements() || value.hasMembers()) {
            return true;
        }
        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            return obj instanceof IngredientJS || obj instanceof Ingredient || obj instanceof ItemStack || obj instanceof Item || obj instanceof NekoId;
        }
        return false;
    }

    @Override
    public Ingredient apply(Value value) {
        try {
            return IngredientResolver.fromValue(value);
        } catch (ValueConversionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ValueConversionException(Ingredient.class, "item / item id / ingredient object", value,
                e.getMessage(), e);
        }
    }
}
