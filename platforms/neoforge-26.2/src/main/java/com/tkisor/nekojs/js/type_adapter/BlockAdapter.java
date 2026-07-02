package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoId;
import java.util.List;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

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
    protected Block fromString(String s) {
        return blockFromId(ParseIds.parseItemOrBlockId(s));
    }

    @Override
    protected Block fromHostObject(Object host) {
        if (host instanceof Block block) return block;
        if (host instanceof Item item) return Block.byItem(item);
        if (host instanceof ItemStack stack) return Block.byItem(stack.getItem());
        if (host instanceof NekoId id) {
            return blockFromId(Identifier.fromNamespaceAndPath(id.namespace(), id.path()));
        }
        return null; // 不识别
    }

    private Block blockFromId(Identifier id) {
        return BuiltInRegistries.BLOCK.getOptional(id)
            .orElseThrow(() -> new com.tkisor.nekojs.api.data.ValueConversionException(
                Block.class, "registered block id", id, "block not found: " + id));
    }
}
