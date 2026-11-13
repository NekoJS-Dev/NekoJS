package com.tkisor.nekojs.wrapper.event.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;
import java.util.Map;

/**
 * JSON-backed wrapper for a loot entry. Create entries via {@link #of(Object)} from an item id,
 * {@code '#tag'}, {@link ItemStack}, or JS object, or configure them in builder callbacks with
 * {@code item}, {@code type}, {@code weight}, {@code when}, and {@code group}.
 */
public class LootEntryJS {
    private final JsonObject json;

    public LootEntryJS(JsonObject json) {
        this.json = json;
    }

    /**
     * Creates a loot entry from {@code item}:
     * <ul>
     *   <li>a string such as {@code 'minecraft:diamond'} creates an item entry (a {@code #} prefix creates a tag entry)</li>
     *   <li>an {@link ItemStack} creates an item entry</li>
     *   <li>a JS object is copied as full entry JSON (with {@code type}, {@code name}, etc. supplied by the caller)</li>
     * </ul>
     */
    public static LootEntryJS of(Object item) {
        if (item instanceof LootEntryJS entry) {
            return entry;
        }
        JsonObject json = new JsonObject();
        LootEntryJS entry = new LootEntryJS(json);
        if (item instanceof String text) {
            if (text.startsWith("#")) {
                entry.type("minecraft:tag").name(text.substring(1));
            } else {
                entry.type("minecraft:item").name(text);
            }
        } else if (item instanceof ItemStack stack) {
            entry.type("minecraft:item").name(itemId(stack));
        } else {
            JsonObject object = LootTableJS.toJsonObject(item);
            for (Map.Entry<String, JsonElement> field : object.entrySet()) {
                json.add(field.getKey(), field.getValue());
            }
        }
        return entry;
    }

    /** Sets the entry type (such as {@code minecraft:item}, {@code minecraft:tag}, or {@code minecraft:group}). */
    public LootEntryJS type(String type) {
        json.addProperty("type", type);
        return this;
    }

    /** Sets the target id (item, tag, or loot table, depending on {@code type}). */
    public LootEntryJS name(String id) {
        json.addProperty("name", id);
        return this;
    }

    /** Points this entry at the given item, tag, or {@link ItemStack}, overwriting {@code type} and {@code name}. */
    public LootEntryJS item(Object item) {
        JsonObject other = of(item).toJson();
        if (other.has("type")) {
            json.addProperty("type", other.get("type").getAsString());
        }
        if (other.has("name")) {
            json.addProperty("name", other.get("name").getAsString());
        }
        return this;
    }

    public LootEntryJS weight(int weight) {
        json.addProperty("weight", weight);
        return this;
    }

    /** Appends a condition object to this entry's {@code conditions} array. */
    public LootEntryJS when(Object condition) {
        if (!json.has("conditions")) {
            json.add("conditions", new JsonArray());
        }
        json.getAsJsonArray("conditions").add(LootTableJS.toJsonElement(condition));
        return this;
    }

    /** Turns this entry into a {@code minecraft:group} containing {@code children}. */
    public LootEntryJS group(Object... children) {
        json.addProperty("type", "minecraft:group");
        JsonArray array = new JsonArray();
        for (Object child : children) {
            array.add(of(child).toJson());
        }
        json.add("children", array);
        return this;
    }

    public JsonObject toJson() {
        return json;
    }

    private static String itemId(ItemStack stack) {
//? if >=26 {
        return stack.typeHolder().unwrapKey().map(key -> key.identifier().toString()).orElse(null);
//?} else {
        /*return stack.getItemHolder().unwrapKey().map(key -> key.location().toString()).orElse(null);
*///?}
    }
}
