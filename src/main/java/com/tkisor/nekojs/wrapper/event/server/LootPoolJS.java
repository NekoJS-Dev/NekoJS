package com.tkisor.nekojs.wrapper.event.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * JSON-backed wrapper for a loot pool, exposing {@code rolls}, {@code addEntry}, and {@code when}.
 * Less common fields (such as {@code functions} or {@code bonus_rolls}) can be edited directly on
 * {@link #toJson()}.
 */
@Doc("Wrapper for one loot pool inside a loot table.")
@Doc("JSON-first: edits are applied directly to the underlying JsonObject.")
public class LootPoolJS {
    private final JsonObject json;

    public LootPoolJS(JsonObject json) {
        this.json = json;
    }

    /** Sets the roll count to a number or a {@code [min, max]} range array. */
    @Doc("Sets how many times this pool rolls.")
    @Param(name = "rolls", value = "a number, or a [min, max] array for a random roll count")
    @Return("this, for chaining")
    public LootPoolJS rolls(Object rolls) {
        json.add("rolls", LootTableJS.toJsonElement(rolls));
        return this;
    }

    /** Returns live wrappers over the existing entries; edits write directly to the underlying JSON. */
    @Doc("Gets the entries of this pool.")
    @Return("live LootEntryJS wrappers over the existing 'entries' array; edits write straight through to the JSON")
    public List<LootEntryJS> getEntries() {
        List<LootEntryJS> entries = new ArrayList<>();
        for (JsonElement element : entriesArray()) {
            entries.add(new LootEntryJS(element.getAsJsonObject()));
        }
        return entries;
    }

    /** Appends an entry from an item id, {@code '#tag'}, {@code ItemStack}, JS object, or {@link LootEntryJS}. */
    @Doc("Appends one loot entry.")
    @Param(name = "entry", value = "item id, '#tag', ItemStack, entry JS object, or a LootEntryJS.of(...) result")
    @Return("this, for chaining")
    public LootPoolJS addEntry(Object entry) {
        entriesArray().add(LootEntryJS.of(entry).toJson());
        return this;
    }

    @Doc("Appends an empty loot entry and lets the callback fill it in.")
    @Param(name = "consumer", value = "callback receiving the new LootEntryJS")
    @Return("this, for chaining")
    public LootPoolJS addEntry(Consumer<LootEntryJS> consumer) {
        JsonObject entry = new JsonObject();
        consumer.accept(new LootEntryJS(entry));
        entriesArray().add(entry);
        return this;
    }

    /** Appends a pool condition JSON object (for example, {@code {condition: 'minecraft:random_chance', chance: 0.5}}). */
    @Doc("Appends a condition that must pass for this pool to roll.")
    @Param(name = "condition", value = "complete condition JSON, e.g. {condition: 'minecraft:random_chance', chance: 0.5}")
    @Return("this, for chaining")
    public LootPoolJS when(Object condition) {
        conditionsArray().add(LootTableJS.toJsonElement(condition));
        return this;
    }

    @Doc("Gets the raw loot pool JSON.")
    @Return("the underlying JsonObject; mutations apply directly to this pool")
    public JsonObject toJson() {
        return json;
    }

    JsonArray entriesArray() {
        if (!json.has("entries")) {
            json.add("entries", new JsonArray());
        }
        return json.getAsJsonArray("entries");
    }

    private JsonArray conditionsArray() {
        if (!json.has("conditions")) {
            json.add("conditions", new JsonArray());
        }
        return json.getAsJsonArray("conditions");
    }
}
