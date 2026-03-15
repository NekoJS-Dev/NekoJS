package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.wrapper.item.ItemStackWrapper;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.graalvm.polyglot.Value;

import java.util.Optional;

public final class ItemStackWrapperAdapter implements JSTypeAdapter<ItemStackWrapper> {
    @Override
    public Class<ItemStackWrapper> getTargetClass() {
        return ItemStackWrapper.class;
    }

    @Override
    public boolean canConvert(Value value) {
        return value.isString();
    }

    @Override
    public ItemStackWrapper convert(Value value) {
        return ItemStackWrapperAdapter.stringToWrapperItemStack(value.asString());
    }

    /**
     * 将字符串转换为 ItemStack。
     * 支持 "Nx item_id" 格式（例如 "2x minecraft:stick"），默认数量为 1。
     */
    static ItemStackWrapper stringToWrapperItemStack(String str) {
        if (str == null || str.isEmpty()) return new ItemStackWrapper(new ItemStack(Items.AIR));

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

        return new ItemStackWrapper(new ItemStack(item.get(), count));
    }
}

