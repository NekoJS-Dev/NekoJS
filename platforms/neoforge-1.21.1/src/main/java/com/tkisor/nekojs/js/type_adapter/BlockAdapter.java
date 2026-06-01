package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoId;
import graal.graalvm.polyglot.Value;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class BlockAdapter implements JSTypeAdapter<Block> {

    @Override
    public Class<Block> getTargetClass() {
        return Block.class;
    }

    @Override
    public boolean test(Value value) {
        if (value.isNull() || value.isString()) {
            return true;
        }
        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            return obj instanceof Block || obj instanceof Item || obj instanceof ItemStack || obj instanceof NekoId;
        }
        return false;
    }

    @Override
    public Block apply(Value value) {
        if (value.isNull()) {
            return Blocks.AIR;
        }

        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            if (obj instanceof Block block) return block;
            if (obj instanceof Item item) return Block.byItem(item);
            if (obj instanceof ItemStack stack) return Block.byItem(stack.getItem());
            if (obj instanceof NekoId id) return blockFromId(ResourceLocation.fromNamespaceAndPath(id.namespace(), id.path()));
        }

        if (value.isString()) {
            return blockFromId(parseId(value.asString()));
        }

        throw new IllegalArgumentException("Unsupported block value: " + value);
    }

    private Block blockFromId(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.getOptional(id).orElseThrow(() -> new IllegalArgumentException("Block not found: " + id));
    }

    private ResourceLocation parseId(String rawId) {
        if (rawId == null || rawId.isBlank()) return ResourceLocation.withDefaultNamespace("air");
        String id = rawId.trim();
        if (id.startsWith("#")) throw new IllegalArgumentException("Expected block id but got tag id: " + rawId);
        if (!id.contains(":")) id = "minecraft:" + id;
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) throw new IllegalArgumentException("Invalid block id: " + rawId);
        return location;
    }
}
