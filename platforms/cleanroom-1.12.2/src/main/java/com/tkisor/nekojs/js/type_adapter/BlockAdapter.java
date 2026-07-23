package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import java.util.List;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import com.tkisor.nekojs.api.data.NekoId;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * Block 适配器（1.12.2 版）：接受 null -> {@link Blocks#AIR}、block id 字符串、
 * 以及 Block/Item/ItemStack/NekoId 宿主对象。
 *
 * <p>1.12.2 适配：使用 {@link ForgeRegistries#BLOCKS} 替代 BuiltInRegistries，
 * 使用 {@link Block#getBlockFromItem(Item)} 替代 {@code Block.byItem()}。</p>
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
        // 1.12.2: Block.getBlockFromItem(item) 替代 Block.byItem(item)
        if (host instanceof Item item) return Block.getBlockFromItem(item);
        if (host instanceof ItemStack stack) return Block.getBlockFromItem(stack.getItem());
        if (host instanceof NekoId id) return blockFromId(new ResourceLocation(id.namespace(), id.path()));
        return null;
    }

    private static Block blockFromId(ResourceLocation id) {
        // 1.12.2: ForgeRegistries.BLOCKS.containsKey + getValue 替代 BuiltInRegistries.BLOCK.getOptional
        if (!ForgeRegistries.BLOCKS.containsKey(id)) {
            throw new ValueConversionException(Block.class, "block id", id, "Block not found: " + id);
        }
        return ForgeRegistries.BLOCKS.getValue(id);
    }
}
