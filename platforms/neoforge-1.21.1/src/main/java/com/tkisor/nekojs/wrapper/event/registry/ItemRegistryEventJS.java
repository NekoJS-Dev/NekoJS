package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.wrapper.registry.ItemBuilderJS;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class ItemRegistryEventJS  {

    /**
     * 已分配创造标签页的物品：物品 id → 标签页 id。
     * 由 {@code BuildCreativeModeTabContentsEvent} 监听器消费（注册完成后才解析 Item）。
     */
    public static final Map<ResourceLocation, ResourceLocation> GROUP_ASSIGNMENTS = new HashMap<>();

    private final RegisterEvent rawEvent;

    private final List<ItemBuilderJS> builders = new ArrayList<>();
    private final Map<ResourceLocation, Supplier<Item>> customItems = new HashMap<>();

    public ItemRegistryEventJS(RegisterEvent rawEvent) {
        this.rawEvent = rawEvent;
    }

    public ItemBuilderJS create(ResourceLocation id) {
        ItemBuilderJS builder = new ItemBuilderJS(id);

        builders.add(builder);
        return builder;
    }

    public void createCustom(ResourceLocation id, Supplier<Item> itemSupplier) {
        customItems.put(id, itemSupplier);
    }

    public void registerAll() {
        for (ItemBuilderJS builder : builders) {
            rawEvent.register(Registries.ITEM, builder.getLocation(), builder::createItem);
            if (builder.getGroupTab() != null) {
                GROUP_ASSIGNMENTS.put(builder.getLocation(), builder.getGroupTab());
            }
        }

        customItems.forEach((location, supplier) -> {
            rawEvent.register(Registries.ITEM, location, supplier);
        });
    }
}