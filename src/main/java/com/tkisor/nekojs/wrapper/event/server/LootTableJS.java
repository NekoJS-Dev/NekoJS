package com.tkisor.nekojs.wrapper.event.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.core.JsonObjectAdapter;
import graal.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * JSON-backed loot table wrapper passed to {@code ServerEvents.lootTables} {@code modify} callbacks.
 * Edits mutate the underlying {@link JsonObject} in place and are saved via {@code setJson} when
 * the callback returns. Less common fields (such as {@code functions} or {@code conditions}) can be
 * edited directly on {@link #toJson()} or passed as raw JSON.
 */
@Doc("Wrapper for the loot table being modified in a ServerEvents.lootTables.modify callback.")
@Doc("JSON-first: edits are applied directly to the underlying JsonObject and take effect when the callback returns.")
public class LootTableJS {
    private final JsonObject json;

    public LootTableJS(JsonObject json) {
        this.json = json;
    }

    /** Loot table type id (such as {@code minecraft:chest}). */
    @Doc("Gets the loot table type id.")
    @Return("the type id like 'minecraft:chest', or null when the table has no type set")
    public String getType() {
        return json.has("type") ? json.get("type").getAsString() : null;
    }

    @Doc("Sets the loot table type id.")
    @Param(name = "type", value = "type id like 'minecraft:chest' or 'minecraft:block'")
    public void setType(String type) {
        json.addProperty("type", type);
    }

    /** Returns live wrappers over the existing pools; edits write directly to the underlying JSON. */
    @Doc("Gets the pools of this loot table.")
    @Return("live LootPoolJS wrappers over the existing 'pools' array; edits write straight through to the JSON")
    public List<LootPoolJS> getPools() {
        List<LootPoolJS> pools = new ArrayList<>();
        for (JsonElement element : poolsArray()) {
            pools.add(new LootPoolJS(element.getAsJsonObject()));
        }
        return pools;
    }

    /** Appends a loot pool from a JS object or JSON string containing complete pool JSON. */
    @Doc("Appends a loot pool from raw JSON: a JS object, a JSON string, or a prebuilt pool object.")
    @Param(name = "pool", value = "complete pool JSON as a JS object or JSON string")
    @Return("this, for chaining")
    public LootTableJS addPool(Object pool) {
        poolsArray().add(LootTableJS.toJsonObject(pool));
        return this;
    }

    @Doc("Appends an empty loot pool and lets the callback fill it in.")
    @Param(name = "consumer", value = "callback receiving the new LootPoolJS")
    @Return("this, for chaining")
    public LootTableJS addPool(Consumer<LootPoolJS> consumer) {
        JsonObject pool = new JsonObject();
        consumer.accept(new LootPoolJS(pool));
        poolsArray().add(pool);
        return this;
    }

    @Doc("Gets the raw loot table JSON.")
    @Return("the underlying JsonObject; mutations apply directly to this table")
    public JsonObject toJson() {
        return json;
    }

    JsonArray poolsArray() {
        if (!json.has("pools")) {
            json.add("pools", new JsonArray());
        }
        return json.getAsJsonArray("pools");
    }

    /** Converts a JS or Java value (object, array, JSON string, or primitive) into a {@link JsonElement}. */
    static JsonElement toJsonElement(Object value) {
        if (value instanceof JsonElement element) {
            return element;
        }
        if (value instanceof Value graalValue) {
            return JsonObjectAdapter.convertValueToJson(graalValue);
        }
        if (value instanceof String text) {
            return JsonParser.parseString(text);
        }
        throw new IllegalArgumentException("[NEKO-5001] 无法转换为 JSON — could not convert value to JSON: " + value);
    }

    /** Converts a JS or Java value into a {@link JsonObject}, throwing if it is not a JSON object. */
    static JsonObject toJsonObject(Object value) {
        JsonElement element = toJsonElement(value);
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("[NEKO-5002] JSON 必须是对象，得到 — JSON must be an object: " + element);
        }
        return element.getAsJsonObject();
    }
}
