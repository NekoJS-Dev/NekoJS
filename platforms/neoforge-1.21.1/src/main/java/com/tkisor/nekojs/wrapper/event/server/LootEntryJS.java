package com.tkisor.nekojs.wrapper.event.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * 战利品条目包装（JSON-first）。用 {@link #of(Object)} 从物品 id / {@code '#tag'} /
 * ItemStack / JS 对象创建；builder 回调里用 {@code item/type/weight/when/group} 链式设置。
 */
public class LootEntryJS {
    private final JsonObject json;

    public LootEntryJS(JsonObject json) {
        this.json = json;
    }

    /**
     * 创建条目：
     * <ul>
     *   <li>字符串 {@code 'minecraft:diamond'} → 物品条目（{@code #} 前缀 → 标签条目）</li>
     *   <li>{@code ItemStack} → 物品条目</li>
     *   <li>JS 对象 → 按完整 entry JSON 处理（type/name 等字段由脚本给全）</li>
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

    /** 设条目类型（{@code minecraft:item} / {@code minecraft:tag} / {@code minecraft:group} / ...）。 */
    public LootEntryJS type(String type) {
        json.addProperty("type", type);
        return this;
    }

    /** 设条目指向的 id（物品 / 标签 / 战利品表，取决于 type）。 */
    public LootEntryJS name(String id) {
        json.addProperty("name", id);
        return this;
    }

    /** 改为指向某个物品 / 标签 / ItemStack（覆盖 type 与 name）。 */
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

    /** 追加条目条件（完整 condition JSON）。 */
    public LootEntryJS when(Object condition) {
        if (!json.has("conditions")) {
            json.add("conditions", new JsonArray());
        }
        json.getAsJsonArray("conditions").add(LootTableJS.toJsonElement(condition));
        return this;
    }

    /** 组条目：把所有子条目包进 {@code minecraft:group}。 */
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
        return stack.getItemHolder().unwrapKey().map(key -> key.location().toString()).orElse(null);
    }
}
