package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
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

    /** Wraps the raw Forge Register<Item> event. */
    public ItemRegistryEventJS(RegistryEvent.Register<Item> rawEvent) {
        this.rawEvent = rawEvent;
    }

    /** Creates an item builder. */
    @Doc("Creates a new item builder.")
    @Param(name = "id", value = "registry id like 'my_item' or 'mymod:my_item'")
    @Return("a new ItemBuilderJS for chaining; the item is registered when the event completes")
    public ItemBuilderJS create(String id) {
        ItemBuilderJS builder = new ItemBuilderJS(id);
        builders.add(builder);
        return builder;
    }

    /** Creates an item builder and configures it in one call. */
    @Doc("Creates a new item builder and configures it in one call.")
    @Param(name = "id", value = "registry id like 'my_item' or 'mymod:my_item'")
    @Param(name = "consumer", value = "callback receiving the builder for configuration")
    public void create(String id, Consumer<ItemBuilderJS> consumer) {
        ItemBuilderJS builder = create(id);
        consumer.accept(builder);
    }

    /** Exposes the raw Forge item registry. */
    @Doc("Exposes the raw Forge item registry for advanced use.")
    @Return("the Forge IForgeRegistry<Item> backing this event")
    public IForgeRegistry<Item> getRegistry() {
        return rawEvent.getRegistry();
    }

    /** Registers all items and emits ItemBlocks for staged blocks. */
    @Doc("Registers all items created in this event and generates ItemBlock forms for staged blocks.")
    @Doc("Called automatically when the event completes; manual calls are normally unnecessary.")
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
