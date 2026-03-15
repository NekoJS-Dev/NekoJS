package com.tkisor.nekojs.wrapper.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class BlockWrapper {

    private final LevelAccessor level;
    private final BlockPos pos;
    private BlockState state;

    public BlockWrapper(LevelAccessor level, BlockPos pos, BlockState state) {
        this.level = level;
        this.pos = pos;
        this.state = state;
    }

    public String getId() {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    public int getX() { return pos.getX(); }
    public int getY() { return pos.getY(); }
    public int getZ() { return pos.getZ(); }

    public Map<String, String> getProperties() {
        Map<String, String> map = new HashMap<>();
        for (Property<?> prop : state.getValues().keySet()) {
            map.put(prop.getName(), getPropertyValue(prop));
        }
        return map;
    }

    private <T extends Comparable<T>> String getPropertyValue(Property<T> property) {
        return property.getName(state.getValue(property));
    }

    public void set(String blockId) {
        if (level == null) return;

        Identifier identifier = Identifier.parse(blockId);
        Optional<Holder.Reference<Block>> newBlock = BuiltInRegistries.BLOCK.get(identifier);

        if (newBlock.isPresent()) {
            this.state = newBlock.get().value().defaultBlockState();
            // 3 是 Minecraft 原版的方块更新 Flag，代表通知客户端更新并触发周围方块更新
            level.setBlock(pos, this.state, 3);
        }
    }
}