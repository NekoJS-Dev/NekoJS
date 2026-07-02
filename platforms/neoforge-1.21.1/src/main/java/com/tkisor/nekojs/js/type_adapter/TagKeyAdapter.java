package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import java.util.List;
import java.util.Map;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import graal.graalvm.polyglot.Value;

/**
 * TagKey 适配器（保留独立实现）。
 *
 * <p>修复：
 * <ul>
 *   <li>B2：可变 static {@code HashMap} 改为不可变 {@link Map#of}。</li>
 *   <li>B6：{@code "registry|tag"} 字符串拆分后校验长度/空段，非法格式抛
 *       {@link ValueConversionException}。</li>
 *   <li>B9：中文错误信息改英文，并统一走 {@link ValueConversionException}。</li>
 * </ul>
 */
public class TagKeyAdapter implements JSTypeAdapter<TagKey> {

    // B2: 不可变映射（旧实现是可变 HashMap）
    private static final Map<String, ResourceKey<?>> REGISTRY_MAP = Map.of(
        "item", Registries.ITEM,
        "block", Registries.BLOCK,
        "fluid", Registries.FLUID,
        "entity", Registries.ENTITY_TYPE,
        "biome", Registries.BIOME
    );

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
        return value.hasMembers() && value.hasMember("tag");
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
                // B6: 校验拆分结果
                if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
                    throw new ValueConversionException(TagKey.class, "'registry|tag' string", str,
                        "malformed format, expected '<registry>|<tag>'");
                }
                registryName = parts[0].trim();
                tagPath = parts[1].trim();
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
        ResourceLocation id = ResourceLocation.tryParse(tagPath);

        if (id == null) {
            // B9: 英文错误信息，统一异常
            throw new ValueConversionException(TagKey.class, "valid tag id", tagPath,
                "invalid tag identifier: " + tagPath);
        }

        // 强转并创建 TagKey
        return TagKey.create(registryKey, id);
    }
}
