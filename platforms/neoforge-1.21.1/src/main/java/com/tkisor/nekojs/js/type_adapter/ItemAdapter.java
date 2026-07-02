package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import java.util.List;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import com.tkisor.nekojs.api.data.NekoId;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * Item 适配器：接受 null -> {@link Items#AIR}、item id 字符串、以及 Item/ItemStack/Block/NekoId 宿主对象。
 *
 * <p>注：Block 转 Item 用 {@code block.asItem()}（1.21.1 中与 {@code Item.byBlock(block)} 等价），
 * 与原实现保持一致。
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
        if (host instanceof Block block) return block.asItem();
        if (host instanceof NekoId id) return itemFromId(ResourceLocation.fromNamespaceAndPath(id.namespace(), id.path()));
        return null;
    }

    private static Item itemFromId(ResourceLocation id) {
        return BuiltInRegistries.ITEM.getOptional(id)
            .orElseThrow(() -> new ValueConversionException(Item.class, "item id", id, "Item not found: " + id));
    }
}
