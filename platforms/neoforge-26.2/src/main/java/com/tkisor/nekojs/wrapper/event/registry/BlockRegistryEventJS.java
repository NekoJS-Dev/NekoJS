package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.wrapper.registry.BlockBuilderJS;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class BlockRegistryEventJS {
    public static final Map<Identifier, Block> PENDING_BLOCK_ITEMS = new HashMap<>();

    private final RegisterEvent rawEvent;

    private final List<BlockBuilderJS> builders = new ArrayList<>();

    public BlockRegistryEventJS(RegisterEvent rawEvent) {
        this.rawEvent = rawEvent;
    }

    public BlockBuilderJS create(String id) {
        Identifier location = id.contains(":") ? Identifier.parse(id) : Identifier.fromNamespaceAndPath("nekojs", id);
        BlockBuilderJS builder = new BlockBuilderJS(location);

        builders.add(builder);
        return builder;
    }

    public void create(String id, Consumer<BlockBuilderJS> consumer) {
        BlockBuilderJS builder = create(id);
        consumer.accept(builder);
    }

    public void registerAll() {
        for (BlockBuilderJS builder : builders) {
            Identifier location = builder.getLocation();

            rawEvent.register(Registries.BLOCK, location, () -> {
                Block block = builder.createBlock();

                if (builder.shouldGenerateItem()) {
                    PENDING_BLOCK_ITEMS.put(location, block);
                }
                return block;
            });
        }
    }
}