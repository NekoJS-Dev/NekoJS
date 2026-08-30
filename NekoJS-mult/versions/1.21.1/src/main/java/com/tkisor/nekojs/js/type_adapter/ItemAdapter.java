// 1.21.1 实现，与版本树 src/ 下的同名 26.x 文件成对。内容等于那份文件在本节点求值后的形态
//（可用 tools/extract_evaluated.py 重新提取核对）；26.x 侧行为变更时须同步本文件。
package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.data.ValueConversionException;
import java.util.List;
import static com.tkisor.nekojs.api.AdapterInputShape.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

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
