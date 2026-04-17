package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.JSTypeAdapter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import graal.graalvm.polyglot.Value;

import java.util.HashMap;
import java.util.Map;

public class TagKeyAdapter implements JSTypeAdapter<TagKey> {

    private static Map<String, ResourceKey<?>> REGISTRY_MAP = new HashMap<>();

    static {
        REGISTRY_MAP.put("item", Registries.ITEM);
        REGISTRY_MAP.put("block", Registries.BLOCK);
        REGISTRY_MAP.put("fluid", Registries.FLUID);
        REGISTRY_MAP.put("entity", Registries.ENTITY_TYPE);
        REGISTRY_MAP.put("biome", Registries.BIOME);
    }

    @Override
    public Class<TagKey> getTargetClass() {
        return TagKey.class;
    }

    @Override
    public boolean canConvert(Value value) {
        // 1. 支持纯字符串: "#minecraft:logs" 或 "block|minecraft:logs"
        if (value.isString()) {
            return true;
        }
        // 2. 支持 JS 对象: { registry: "block", tag: "minecraft:logs" }
        if (value.hasMembers() && value.hasMember("tag")) {
            return true;
        }
        return false;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public TagKey convert(Value value) {
        String registryName = "item"; // 默认假设为物品标签
        String tagPath = "";

        // === 解析字符串 ===
        if (value.isString()) {
            String str = value.asString();
            if (str.startsWith("#")) {
                str = str.substring(1); // 自动忽略 # 前缀
            }

            // 检查是否包含注册表前缀，例如 "block|minecraft:logs"
            if (str.contains("|")) {
                String[] parts = str.split("\\|", 2);
                registryName = parts[0];
                tagPath = parts[1];
            } else {
                tagPath = str;
            }
        }
        // === 解析 JS 对象 ===
        else if (value.hasMembers()) {
            if (value.hasMember("type")) {
                registryName = value.getMember("type").asString();
            }
            tagPath = value.getMember("tag").asString();
            if (tagPath.startsWith("#")) {
                tagPath = tagPath.substring(1);
            }
        }

        // === 组装 TagKey ===
        ResourceKey registryKey = REGISTRY_MAP.getOrDefault(registryName, Registries.ITEM);
        Identifier id = Identifier.tryParse(tagPath);

        if (id == null) {
            throw new IllegalArgumentException("[NekoJS] 无法解析非法的 Tag 标识符: " + tagPath);
        }

        // 强转并创建 TagKey
        return TagKey.create(registryKey, id);
    }
}