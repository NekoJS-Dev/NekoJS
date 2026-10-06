package com.tkisor.nekojs.wrapper.pdata;

import com.tkisor.nekojs.testfixture.VanillaRegistryProbe;
import com.tkisor.nekojs.wrapper.item.PersistentDataJS;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Verifies ItemStack pdata stays in the vanilla custom-data component. */
class ItemStackPersistentDataTest {

    @Test
    void itemStackPdataRoundTripsAndClearsItsCustomDataComponent() {
        assumeTrue(VanillaRegistryProbe.available(), "vanilla registries require the ModDev runtime");
        ItemStack stack = new ItemStack(Items.STONE);
        PersistentDataJS pdata = new PersistentDataJS(stack);

        pdata.putInt("durability", 7);
        assertEquals(7, new PersistentDataJS(stack).getInt("durability"));

        pdata.clear();
        assertFalse(stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA),
                "clearing empty item pdata removes the custom data component");
    }
}
