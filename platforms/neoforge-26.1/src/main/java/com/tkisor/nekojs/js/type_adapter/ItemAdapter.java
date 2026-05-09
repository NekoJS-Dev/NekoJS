package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoId;
import graal.graalvm.polyglot.Value;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

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
            return obj instanceof Item || obj instanceof ItemStack || obj instanceof Block || obj instanceof NekoId;
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
            if (obj instanceof NekoId id) return itemFromId(Identifier.fromNamespaceAndPath(id.namespace(), id.path()));
        }

        if (value.isString()) {
            return itemFromId(parseId(value.asString()));
        }

        throw new IllegalArgumentException("Unsupported item value: " + value);
    }

    private Item itemFromId(Identifier id) {
        return BuiltInRegistries.ITEM.getOptional(id).orElseThrow(() -> new IllegalArgumentException("Item not found: " + id));
    }

    private Identifier parseId(String rawId) {
        if (rawId == null || rawId.isBlank()) return Identifier.withDefaultNamespace("air");
        String id = rawId.trim();
        if (id.startsWith("#")) throw new IllegalArgumentException("Expected item id but got tag id: " + rawId);
        if (!id.contains(":")) id = "minecraft:" + id;
        Identifier location = Identifier.tryParse(id);
        if (location == null) throw new IllegalArgumentException("Invalid item id: " + rawId);
        return location;
    }
}
