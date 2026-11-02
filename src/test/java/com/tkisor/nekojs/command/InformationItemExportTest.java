package com.tkisor.nekojs.command;

import com.mojang.serialization.JsonOps;
import com.tkisor.nekojs.testfixture.VanillaRegistryProbe;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class InformationItemExportTest {
    @BeforeAll
    static void requireItemComponents() {
        assumeTrue(VanillaRegistryProbe.available(), "Vanilla item components require a loader-backed registry environment");
    }

    @Test
    void fullJsonRetainsCountComponentsAndComponentRemoval() throws Exception {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        ItemStack original = new ItemStack(Items.DIAMOND_SWORD, 3);
        original.set(DataComponents.CUSTOM_NAME, Component.literal("a'b\\c 物品"));
        original.set(DataComponents.DAMAGE, 17);
        original.remove(DataComponents.ATTRIBUTE_MODIFIERS);
        var data = InformationCommands.stackData(registries, original);
        ItemStack restored = ItemStack.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), data).getOrThrow();
        assertEquals(3, restored.getCount());
        assertTrue(ItemStack.isSameItemSameComponents(original, restored));
        String exported = InformationCommands.itemString(registries, original);
        ItemStack parsed = com.tkisor.nekojs.js.type_adapter.ItemStackStrings.parse(exported, registries);
        assertEquals(3, parsed.getCount());
        assertTrue(ItemStack.isSameItemSameComponents(original, parsed));
        assertEquals(17, original.getDamageValue(), "Inspection must not change the live stack");
        assertTrue(data.toString().contains("!minecraft:attribute_modifiers"));
    }

    @Test
    void hotbarAndInventoryKeepOrderAndDuplicatesButExcludeEquipmentAndEmptySlots() {
        var slots = new ArrayList<>(Collections.nCopies(41, ItemStack.EMPTY));
        ItemStack first = new ItemStack(Items.STONE, 2);
        ItemStack lastHotbar = new ItemStack(Items.STONE, 2);
        ItemStack firstInventory = new ItemStack(Items.APPLE);
        ItemStack lastInventory = new ItemStack(Items.DIRT);
        slots.set(0, first);
        slots.set(8, lastHotbar);
        slots.set(9, firstInventory);
        slots.set(35, lastInventory);
        slots.set(36, new ItemStack(Items.DIAMOND_HELMET));
        slots.set(40, new ItemStack(Items.SHIELD));
        assertEquals(List.of(first, lastHotbar), InformationCommands.selectedItems(slots::get, 9));
        assertEquals(List.of(first, lastHotbar, firstInventory, lastInventory),
                InformationCommands.selectedItems(slots::get, 36));
        assertTrue(InformationCommands.selectedItems(index -> ItemStack.EMPTY, 36).isEmpty());
    }
}
