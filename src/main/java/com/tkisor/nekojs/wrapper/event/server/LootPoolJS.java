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
 * 战利品池包装（JSON-first）：{@code rolls} / {@code addEntry} / {@code when}。
 * 冷门字段（functions / bonus_rolls 等）直接操作 {@code toJson()}。
 */
@Doc("Wrapper for one loot pool inside a loot table.")
@Doc("JSON-first: edits are applied directly to the underlying JsonObject.")
public class LootPoolJS {
    private final JsonObject json;

    public LootPoolJS(JsonObject json) {
        this.json = json;
    }

    /** 随机次数：数字或 [min, max] 数组。 */
    @Doc("Sets how many times this pool rolls.")
    @Param(name = "rolls", value = "a number, or a [min, max] array for a random roll count")
    @Return("this, for chaining")
    public LootPoolJS rolls(Object rolls) {
        json.add("rolls", LootTableJS.toJsonElement(rolls));
        return this;
    }

    /** 现有条目列表（包装对象，改动直接写回）。 */
    @Doc("Gets the entries of this pool.")
    @Return("live LootEntryJS wrappers over the existing 'entries' array; edits write straight through to the JSON")
    public List<LootEntryJS> getEntries() {
        List<LootEntryJS> entries = new ArrayList<>();
        for (JsonElement element : entriesArray()) {
            entries.add(new LootEntryJS(element.getAsJsonObject()));
        }
        return entries;
    }

    /** 追加条目：物品 id / {@code '#tag'} / ItemStack / JS 对象 / {@code LootEntryJS.of(...)}。 */
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

    /** 追加池条件（完整 condition JSON，如 {@code {condition: 'minecraft:random_chance', chance: 0.5}}）。 */
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
