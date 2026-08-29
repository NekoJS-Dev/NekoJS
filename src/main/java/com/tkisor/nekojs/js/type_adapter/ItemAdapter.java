package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.data.ValueConversionException;
import java.util.List;
import static com.tkisor.nekojs.api.AdapterInputShape.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

public class ItemAdapter extends AbstractJSTypeAdapter<Item> {

    @Override
    public Class<Item> getTargetClass() {
        return Item.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                registry("Item"),
                host(ItemStack.class),
                host(Block.class),
                host(NekoId.class));
    }

    @Override
    protected Item defaultValue() {
        return Items.AIR;
    }

    @Override
//? if >=26 {
    protected Item fromString(String s) {
        return itemFromId(ParseIds.parseItemOrBlockId(s));
//?} else {
/*    protected Item fromString(String rawId) {
        return itemFromId(ParseIds.parseItemOrBlockId(rawId));
*///?}
    }

    @Override
    protected Item fromHostObject(Object host) {
        if (host instanceof Item item) return item;
        if (host instanceof ItemStack stack) return stack.getItem();
        if (host instanceof Block block) return block.asItem();
//? if >=26 {
        if (host instanceof NekoId id) {
            return itemFromId(Identifier.fromNamespaceAndPath(id.namespace(), id.path()));
        }
        return null; // 不识别
//?} else {
/*        if (host instanceof NekoId id) return itemFromId(Identifier.fromNamespaceAndPath(id.namespace(), id.path()));
        return null;
*///?}
    }

//? if >=26 {
    private Item itemFromId(Identifier id) {
//?} else {
/*    private static Item itemFromId(Identifier id) {
*///?}
        return BuiltInRegistries.ITEM.getOptional(id)
//? if >=26 {
            .orElseThrow(() -> new ValueConversionException(Item.class, "registered item id", id,
                "item not found: " + id));
//?} else {
/*            .orElseThrow(() -> new ValueConversionException(Item.class, "item id", id, "Item not found: " + id));
*///?}
    }
}
