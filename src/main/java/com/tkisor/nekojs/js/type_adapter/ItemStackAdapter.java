package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.JSTypeAdapter;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.graalvm.polyglot.Value;

import java.util.Optional;

public final class ItemStackAdapter implements JSTypeAdapter<ItemStack> {

    @Override
    public Class<ItemStack> getTargetClass() {
        return ItemStack.class;
    }

    @Override
    public boolean canConvert(Value value) {
        if (value.isNull()) {
            return true;
        }
        if (value.isString()) {
            return true;
        }
        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            return obj instanceof ItemStack;
        }
        return false;
    }

    @Override
    public ItemStack convert(Value value) {
        if (value.isNull()) {
            return ItemStack.EMPTY;
        }

        if (value.isString()) {
            return stringToItemStack(value.asString());
        }

        if (value.isHostObject()) {
            Object obj = value.asHostObject();

            if (obj instanceof ItemStack stack) {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }

    /**
     * 将字符串转换为 ItemStack。
     * 支持 "Nx item_id" 格式（例如 "2x minecraft:stick"），默认数量为 1。
     */
    static ItemStack stringToItemStack(String str) {
        if (str == null || str.trim().isEmpty()) return ItemStack.EMPTY;

        int count = 1;
        str = str.trim();

        if (str.matches("^(\\d+)x\\s+(\\S+)$")) {
            int xIndex = str.indexOf('x');
            try {
                count = Integer.parseInt(str.substring(0, xIndex).trim());
                str = str.substring(xIndex + 1).trim();
            } catch (NumberFormatException e) {
                count = 1;
            }
        }

        Identifier id = Identifier.tryParse(str);
        Optional<Holder.Reference<Item>> item = BuiltInRegistries.ITEM.get(id);
        if (item.isEmpty()) throw new IllegalArgumentException("Not found item: " + str);

        return new ItemStack(item.get(), count);
    }
}