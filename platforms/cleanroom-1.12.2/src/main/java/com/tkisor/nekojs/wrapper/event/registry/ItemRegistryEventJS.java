package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.wrapper.registry.ItemBuilderJS;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Script-facing wrapper around {@link RegistryEvent.Register}{@code <Item>}.
 *
 * <p>Behavior mirrors {@link BlockRegistryEventJS}: scripts register items via
 * {@link #create(String)} and {@link #registerAll()} flushes them. In addition,
 * {@code registerAll()} drains {@link BlockRegistryEventJS#PENDING_BLOCK_ITEMS} and emits
 * a matching {@link ItemBlock} for each staged block (1.12.2 API: {@code new ItemBlock(block)}).
 */
public class ItemRegistryEventJS {

    private final RegistryEvent.Register<Item> rawEvent;
    private final List<ItemBuilderJS> builders = new ArrayList<>();

    public ItemRegistryEventJS(RegistryEvent.Register<Item> rawEvent) {
        this.rawEvent = rawEvent;
    }

    public ItemBuilderJS create(String id) {
        ItemBuilderJS builder = new ItemBuilderJS(id);
        builders.add(builder);
        return builder;
    }

    public void create(String id, Consumer<ItemBuilderJS> consumer) {
        ItemBuilderJS builder = create(id);
        consumer.accept(builder);
    }

    public IForgeRegistry<Item> getRegistry() {
        return rawEvent.getRegistry();
    }

    public void registerAll() {
        for (ItemBuilderJS builder : builders) {
            rawEvent.getRegistry().register(builder.build());
        }
        builders.clear();

        // Generate ItemBlock forms for any blocks staged during Register<Block>.
        if (!BlockRegistryEventJS.PENDING_BLOCK_ITEMS.isEmpty()) {
            for (Block block : BlockRegistryEventJS.PENDING_BLOCK_ITEMS.values()) {
                ResourceLocation rl = block.getRegistryName();
                if (rl == null) {
                    continue;
                }
                ItemBlock itemBlock = new ItemBlock(block);
                itemBlock.setRegistryName(rl);
                itemBlock.setTranslationKey(rl.getNamespace() + "." + rl.getPath());
                rawEvent.getRegistry().register(itemBlock);
            }
            BlockRegistryEventJS.PENDING_BLOCK_ITEMS.clear();
        }
    }
}
