// 1.21.1 实现，与版本树 src/ 下的同名 26.x 文件成对。内容等于那份文件在本节点求值后的形态
//（可用 tools/extract_evaluated.py 重新提取核对）；26.x 侧行为变更时须同步本文件。
package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoId;
import java.util.List;
import static com.tkisor.nekojs.api.AdapterInputShape.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import com.tkisor.nekojs.api.data.ValueConversionException;

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
