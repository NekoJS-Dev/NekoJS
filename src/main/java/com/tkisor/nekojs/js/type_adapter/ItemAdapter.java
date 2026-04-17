package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.JSTypeAdapter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import graal.graalvm.polyglot.Value;

public class ItemAdapter implements JSTypeAdapter<Item> {
    @Override
    public Class<Item> getTargetClass() {
        return Item.class;
    }

    @Override
    public boolean canConvert(Value value) {
        if (value.isNull() || value.isString()) {
            return true;
        }
        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            return obj instanceof Item || obj instanceof ItemStack || obj instanceof Block;
        }
        return false;
    }

    @Override
    public Item convert(Value value) {
        if (value.isNull()) {
            return Items.AIR;
        }

        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            if (obj instanceof Item item) return item;
            if (obj instanceof ItemStack stack) return stack.getItem();
            if (obj instanceof Block block) return Item.byBlock(block);
        }

        if (value.isString()) {
            String rawId = value.asString();

            if (rawId == null || rawId.trim().isEmpty() || rawId.startsWith("#")) {
                return Items.AIR;
            }

            String id = formatId(rawId);
            Identifier loc = Identifier.tryParse(id);

            if (loc != null) {
                Item item = BuiltInRegistries.ITEM.getValue(loc);
                if (item != null) {
                    return item;
                }
            }
        }

        return Items.AIR;
    }

    private String formatId(String inputStr) {
        if (inputStr.indexOf(':') != -1) {
            return inputStr;
        }
        return "minecraft:" + inputStr;
    }
}