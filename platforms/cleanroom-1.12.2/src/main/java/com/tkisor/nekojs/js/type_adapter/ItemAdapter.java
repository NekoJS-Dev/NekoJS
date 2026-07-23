package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import java.util.List;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import com.tkisor.nekojs.api.data.NekoId;
import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * Item 适配器（1.12.2 版）：接受 null -> {@link Items#AIR}、item id 字符串、
 * 以及 Item/ItemStack/Block/NekoId 宿主对象。
 *
 * <p>1.12.2 适配：使用 {@link ForgeRegistries#ITEMS} 替代 BuiltInRegistries，
 * 使用 {@link Item#getItemFromBlock(Block)} 替代 {@code block.asItem()}。</p>
 */
public class ItemAdapter extends AbstractJSTypeAdapter<Item> {

    @Override
    public Class<Item> getTargetClass() {
        return Item.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                registry("Item"),
                host(ItemStack.class),
                host(Block.class),
                host(NekoId.class));
    }

    @Override
    protected Item defaultValue() {
        return Items.AIR;
    }

    @Override
    protected Item fromString(String rawId) {
        return itemFromId(ParseIds.parseItemOrBlockId(rawId));
    }

    @Override
    protected Item fromHostObject(Object host) {
        if (host instanceof Item item) return item;
        if (host instanceof ItemStack stack) return stack.getItem();
        // 1.12.2: Item.getItemFromBlock(block) 替代 block.asItem()
        if (host instanceof Block block) return Item.getItemFromBlock(block);
        if (host instanceof NekoId id) return itemFromId(new ResourceLocation(id.namespace(), id.path()));
        return null;
    }

    private static Item itemFromId(ResourceLocation id) {
        // 1.12.2: ForgeRegistries.ITEMS.containsKey + getValue 替代 BuiltInRegistries.ITEM.getOptional
        if (!ForgeRegistries.ITEMS.containsKey(id)) {
            throw new ValueConversionException(Item.class, "item id", id, "Item not found: " + id);
        }
        return ForgeRegistries.ITEMS.getValue(id);
    }
}
