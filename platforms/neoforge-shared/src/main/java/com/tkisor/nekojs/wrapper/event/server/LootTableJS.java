package com.tkisor.nekojs.wrapper.event.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.core.JsonObjectAdapter;
import graal.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@code ServerEvents.lootTables} 中 {@code modify} 回调的战利品表包装：
 * JSON-first 操作（改动直接反映到底层 {@link JsonObject}，最后经 {@code setJson} 生效）。
 * 冷门字段（functions / conditions 等）直接操作返回的 {@code toJson()} 或传 raw JSON。
 */
public class LootTableJS {
    private final JsonObject json;

    public LootTableJS(JsonObject json) {
        this.json = json;
    }

    /** 表类型（如 {@code minecraft:chest}）。 */
    public String getType() {
        return json.has("type") ? json.get("type").getAsString() : null;
    }

    public void setType(String type) {
        json.addProperty("type", type);
    }

    /** 现有池列表（包装对象，改动直接写回底层 JSON）。 */
    public List<LootPoolJS> getPools() {
        List<LootPoolJS> pools = new ArrayList<>();
        for (JsonElement element : poolsArray()) {
            pools.add(new LootPoolJS(element.getAsJsonObject()));
        }
        return pools;
    }

    /** 追加一个池：传 JS 对象 / JSON 字符串（完整 pool JSON），或 builder 回调。 */
    public LootTableJS addPool(Object pool) {
        poolsArray().add(LootTableJS.toJsonObject(pool));
        return this;
    }

    public LootTableJS addPool(Consumer<LootPoolJS> consumer) {
        JsonObject pool = new JsonObject();
        consumer.accept(new LootPoolJS(pool));
        poolsArray().add(pool);
        return this;
    }

    public JsonObject toJson() {
        return json;
    }

    JsonArray poolsArray() {
        if (!json.has("pools")) {
            json.add("pools", new JsonArray());
        }
        return json.getAsJsonArray("pools");
    }

    /** 任意 JS/Java 值 → JsonElement（对象/数组/字符串/数字）。 */
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
        throw new IllegalArgumentException("无法转换为 JSON: " + value);
    }

    /** 任意 JS/Java 值 → JsonObject（要求对象形态）。 */
    static JsonObject toJsonObject(Object value) {
        JsonElement element = toJsonElement(value);
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("JSON 必须是对象，得到: " + element);
        }
        return element.getAsJsonObject();
    }
}
