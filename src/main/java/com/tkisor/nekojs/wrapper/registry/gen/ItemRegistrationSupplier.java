package com.tkisor.nekojs.wrapper.registry.gen;

import com.tkisor.nekojs.core.registry.ItemAssetDefinition;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;

/** Carries a nested BlockItem's frozen asset definition through the existing additional pass. */
record ItemRegistrationSupplier(Supplier<Item> factory, ItemAssetDefinition assets) implements Supplier<Item> {
    @Override public Item get() { return factory.get(); }
}
