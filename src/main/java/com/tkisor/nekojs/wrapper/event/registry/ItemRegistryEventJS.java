package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.bindings.event.NekoEvent;
import com.tkisor.nekojs.wrapper.registry.ItemBuilderJS;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.function.Consumer;

public class ItemRegistryEventJS implements NekoEvent {

    private final RegisterEvent rawEvent;

    public ItemRegistryEventJS(RegisterEvent rawEvent) {
        this.rawEvent = rawEvent;
    }

    public void create(String id, Consumer<ItemBuilderJS> consumer) {
        ItemBuilderJS builder = new ItemBuilderJS(id);
        consumer.accept(builder);

        Identifier location = id.contains(":")
                ? Identifier.parse(id)
                : Identifier.fromNamespaceAndPath("nekojs", id);

        rawEvent.register(Registries.ITEM, location, () -> builder.createItem(location));
    }
}