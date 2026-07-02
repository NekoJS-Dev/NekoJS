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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Block 适配器：接受 null -> {@link Blocks#AIR}、block id 字符串、以及 Block/Item/ItemStack/NekoId 宿主对象。
 */
public class BlockAdapter extends AbstractJSTypeAdapter<Block> {

    @Override
    public Class<Block> getTargetClass() {
        return Block.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                registry("Block"),
                host(Item.class),
                host(ItemStack.class),
                host(NekoId.class));
    }

    @Override
    protected Block defaultValue() {
        return Blocks.AIR;
    }

    @Override
    protected Block fromString(String rawId) {
        return blockFromId(ParseIds.parseItemOrBlockId(rawId));
    }

    @Override
    protected Block fromHostObject(Object host) {
        if (host instanceof Block block) return block;
        if (host instanceof Item item) return Block.byItem(item);
        if (host instanceof ItemStack stack) return Block.byItem(stack.getItem());
        if (host instanceof NekoId id) return blockFromId(ResourceLocation.fromNamespaceAndPath(id.namespace(), id.path()));
        return null;
    }

    private static Block blockFromId(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.getOptional(id)
            .orElseThrow(() -> new ValueConversionException(Block.class, "block id", id, "Block not found: " + id));
    }
}
