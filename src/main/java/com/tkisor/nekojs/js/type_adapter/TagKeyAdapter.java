// 26.x implementation. This file must not contain version guards again. The 1.21.1 implementation is
// the same-named file under versions/1.21.1/src, and changing behavior here requires updating it too.
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

// TagKey is generic (TagKey<T>), but this adapter carries only the raw type: the T of JSTypeAdapter<T>
// cannot express a wildcard, and getTargetClass must return Class<TagKey>. Raw usage is confined to the
// REGISTRY_MAP lookup and TagKey.create
@SuppressWarnings("rawtypes")
public class TagKeyAdapter implements JSTypeAdapter<TagKey> {

    // Immutable map instead of a mutable static block
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
                str = str.substring(1); // Drop the # prefix
            }

            // Check for a registry prefix, for example "block|minecraft:logs"
            if (str.contains("|")) {
                String[] parts = str.split("\\|", 2);
                // Validate the split result before assigning it: "block|" would otherwise throw AIOOBE
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
        // ---- JS object form ----
        else if (value.hasMembers()) {
            if (value.hasMember("type")) {
                registryName = value.getMember("type").asString();
            }
            tagPath = value.getMember("tag").asString();
            if (tagPath.startsWith("#")) {
                tagPath = tagPath.substring(1);
            }
        }

        // ---- Build the TagKey ----
        ResourceKey registryKey = REGISTRY_MAP.getOrDefault(registryName, Registries.ITEM);
        Identifier id = Identifier.tryParse(tagPath);

        if (id == null) {
            // Message in English, built with the shared exception type
            throw new ValueConversionException(TagKey.class, "valid tag identifier", tagPath,
                "invalid tag identifier: " + tagPath);
        }

        // Cast and create the TagKey
        return TagKey.create(registryKey, id);
    }
}
