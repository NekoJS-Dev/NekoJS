package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import java.util.List;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import graal.graalvm.polyglot.Value;

import java.util.Map;

public class TagKeyAdapter implements JSTypeAdapter<TagKey> {

    // B2: immutable map + 去掉 mutable static 块
    private static final Map<String, ResourceKey<?>> REGISTRY_MAP = Map.of(
        "item", Registries.ITEM,
        "block", Registries.BLOCK,
        "fluid", Registries.FLUID,
        "entity", Registries.ENTITY_TYPE,
        "biome", Registries.BIOME);

    @Override
    public Class<TagKey> getTargetClass() {
        return TagKey.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                string(),
                object(
                        Slot.opt("type", string()),
                        Slot.req("tag", string())));
    }

    @Override
    public boolean test(Value value) {
        if (value.isString()) {
            return true;
        }
        // { registry: "block", tag: "minecraft:logs" }
        if (value.hasMembers() && value.hasMember("tag")) {
            return true;
        }
        return false;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public TagKey apply(Value value) {
        String registryName = "item";
        String tagPath = "";

        if (value.isString()) {
            String str = value.asString();
            if (str.startsWith("#")) {
                str = str.substring(1); // 忽略 # 前缀
            }

            // 检查是否包含注册表前缀，例如 "block|minecraft:logs"
            if (str.contains("|")) {
                String[] parts = str.split("\\|", 2);
                registryName = parts[0];
                tagPath = parts[1];
                // B6: 校验分割结果完整性
                if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
                    throw new ValueConversionException(TagKey.class, "'registry|tag' string", str,
                        "malformed format");
                }
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
            // B9: 英文信息 + 统一异常
            throw new ValueConversionException(TagKey.class, "valid tag identifier", tagPath,
                "invalid tag identifier: " + tagPath);
        }

        // 强转并创建 TagKey
        return TagKey.create(registryKey, id);
    }
}
