package com.tkisor.nekojs.core.registry;

import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Immutable resource inputs published only for successfully registered items. */
public record ItemAssetDefinition(String id, String parentModel, Map<String, String> textures) {
    public ItemAssetDefinition {
        id = ItemDefinitionConfig.resourceId(id);
        parentModel = ItemDefinitionConfig.resourceId(parentModel);
        textures = Collections.unmodifiableMap(new TreeMap<>(textures));
        textures.values().forEach(ItemDefinitionConfig::resourceId);
    }

    public Map<String, String> files(boolean clientItemDefinitions) {
        String[] parts = id.split(":", 2);
        JsonObject model = new JsonObject();
        model.addProperty("parent", parentModel);
        JsonObject slots = new JsonObject();
        textures.forEach(slots::addProperty);
        model.add("textures", slots);
        Map<String, String> files = new TreeMap<>();
        files.put(parts[0] + "/models/item/" + parts[1] + ".json", model.toString());
        if (clientItemDefinitions) {
            JsonObject reference = new JsonObject();
            reference.addProperty("type", "minecraft:model");
            reference.addProperty("model", parts[0] + ":item/" + parts[1]);
            JsonObject definition = new JsonObject();
            definition.add("model", reference);
            files.put(parts[0] + "/items/" + parts[1] + ".json", definition.toString());
        }
        return Collections.unmodifiableMap(files);
    }
}
