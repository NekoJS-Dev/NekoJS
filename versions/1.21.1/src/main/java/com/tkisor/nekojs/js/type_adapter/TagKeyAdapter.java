// 1.21.1 implementation, paired with the same-named 26.x file under the version tree's src/. The content
// is what that file becomes once evaluated for this node. Keep this file in sync when the 26.x behavior
// changes.
package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import java.util.List;
import static com.tkisor.nekojs.api.AdapterInputShape.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import graal.graalvm.polyglot.Value;
import java.util.Map;

// TagKey is a generic class (TagKey<T>), so only the raw type appears here: the T of JSTypeAdapter<T>
// cannot express a wildcard, and getTargetClass must return Class<TagKey>. The raw usage is limited to
// the REGISTRY_MAP lookups and TagKey.create.
@SuppressWarnings("rawtypes")
public class TagKeyAdapter implements JSTypeAdapter<TagKey> {

    // Immutable map, so no mutable static initializer block is needed.
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
                str = str.substring(1); // The leading # is dropped here.
            }

            // Check for a registry prefix such as "block|minecraft:logs".
            if (str.contains("|")) {
                String[] parts = str.split("\\|", 2);
                // Validate the split result before assigning; otherwise "block|" would throw an AIOOBE.
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
        // === Parse a JS object ===
        else if (value.hasMembers()) {
            if (value.hasMember("type")) {
                registryName = value.getMember("type").asString();
            }
            tagPath = value.getMember("tag").asString();
            if (tagPath.startsWith("#")) {
                tagPath = tagPath.substring(1);
            }
        }

        // === Assemble the TagKey ===
        ResourceKey registryKey = REGISTRY_MAP.getOrDefault(registryName, Registries.ITEM);
        ResourceLocation id = ResourceLocation.tryParse(tagPath);

        if (id == null) {
            // English message, thrown as the same exception type used elsewhere.
            throw new ValueConversionException(TagKey.class, "valid tag id", tagPath,
                "invalid tag identifier: " + tagPath);
        }

        // Cast the raw registry key and create the TagKey.
        return TagKey.create(registryKey, id);
    }
}
