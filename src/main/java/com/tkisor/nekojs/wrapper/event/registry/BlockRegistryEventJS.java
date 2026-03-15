package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.bindings.event.NekoEvent;
import com.tkisor.nekojs.wrapper.registry.BlockBuilderJS;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class BlockRegistryEventJS implements NekoEvent {
    public static final Map<Identifier, Block> PENDING_BLOCK_ITEMS = new HashMap<>();

    private final RegisterEvent rawEvent;

    public BlockRegistryEventJS(RegisterEvent rawEvent) {
        this.rawEvent = rawEvent;
    }

    public void create(String id, Consumer<BlockBuilderJS> consumer) {
        BlockBuilderJS builder = new BlockBuilderJS(id);
        consumer.accept(builder);

        Identifier location = id.contains(":")
                ? Identifier.parse(id)
                : Identifier.fromNamespaceAndPath("nekojs", id);

        rawEvent.register(Registries.BLOCK, location, () -> {
            Block block = builder.createBlock(location);

            if (builder.shouldGenerateItem()) {
                PENDING_BLOCK_ITEMS.put(location, block);
            }
            return block;
        });
    }
}