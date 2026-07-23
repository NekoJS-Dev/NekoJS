package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.wrapper.registry.BlockBuilderJS;
import net.minecraft.block.Block;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Script-facing wrapper around {@link RegistryEvent.Register}{@code <Block>}.
 *
 * <p>Scripts create blocks via {@link #create(String)} (optionally configuring them through
 * a {@link Consumer}) and the listener calls {@link #registerAll()} at the end of the
 * event to flush every builder into the Forge registry.
 *
 * <p>Block-item generation is deferred: blocks whose builder wants an item form are
 * parked in {@link #PENDING_BLOCK_ITEMS} so {@link ItemRegistryEventJS} can materialize
 * the matching {@code ItemBlock} during the subsequent {@code Register<Item>} event.
 */
public class BlockRegistryEventJS {

    /** Blocks awaiting item-form generation, keyed by registry name. */
    public static final java.util.Map<String, Block> PENDING_BLOCK_ITEMS = new java.util.HashMap<>();

    private final RegistryEvent.Register<Block> rawEvent;
    private final List<BlockBuilderJS> builders = new ArrayList<>();

    public BlockRegistryEventJS(RegistryEvent.Register<Block> rawEvent) {
        this.rawEvent = rawEvent;
    }

    public BlockBuilderJS create(String id) {
        BlockBuilderJS builder = new BlockBuilderJS(id);
        builders.add(builder);
        return builder;
    }

    public void create(String id, Consumer<BlockBuilderJS> consumer) {
        BlockBuilderJS builder = create(id);
        consumer.accept(builder);
    }

    /** Expose the raw Forge registry for advanced script use. */
    public IForgeRegistry<Block> getRegistry() {
        return rawEvent.getRegistry();
    }

    /** Flush all builders into the Forge registry and stage block-item generation. */
    public void registerAll() {
        for (BlockBuilderJS builder : builders) {
            Block block = builder.build();
            rawEvent.getRegistry().register(block);
            if (builder.shouldGenerateItem()) {
                PENDING_BLOCK_ITEMS.put(builder.getRegistryName(), block);
            }
        }
        builders.clear();
    }
}
