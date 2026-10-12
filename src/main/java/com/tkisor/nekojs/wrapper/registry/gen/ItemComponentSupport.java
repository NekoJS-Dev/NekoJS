package com.tkisor.nekojs.wrapper.registry.gen;

import com.google.gson.Gson;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.tkisor.nekojs.core.registry.ItemDefinitionConfig;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/** Decodes frozen JSON with the registry provider supplied by native component initialization. */
final class ItemComponentSupport {
    private static final Gson JSON = new Gson();
    private ItemComponentSupport() {}

    static void apply(Item.Properties properties, Identifier item, ItemDefinitionConfig definition) {
        if (definition.jukeboxPlayable() != null) install(properties, item, "minecraft:jukebox_playable",
                DataComponents.JUKEBOX_PLAYABLE, new JsonPrimitive(definition.jukeboxPlayable()));
        if (definition.trimMaterial() != null) install(properties, item, "minecraft:provides_trim_material",
                DataComponents.PROVIDES_TRIM_MATERIAL, new JsonPrimitive(definition.trimMaterial()));
        definition.components().forEach((id, value) -> {
            if (id.equals("minecraft:item_name") || id.equals("minecraft:item_model")) {
                throw failure(item, id, "native item identity components cannot be overridden", null);
            }
            DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(Identifier.parse(id))
                    .map(net.minecraft.core.Holder::value).orElse(null);
            if (type == null || type.codec() == null) throw failure(item, id, "unknown or non-serializable component", null);
            install(properties, item, id, type, JSON.toJsonTree(value));
        });
    }

    private static <T> void install(Item.Properties properties, Identifier item, String id,
                                    DataComponentType<T> type, JsonElement json) {
        properties.delayedComponent(type, registries -> decode(item, id, type, registries.createSerializationContext(JsonOps.INSTANCE), json));
    }

    private static <T> T decode(Identifier item, String id, DataComponentType<T> type,
                               DynamicOps<JsonElement> ops, JsonElement json) {
        try {
            return type.codec().parse(ops, json).getOrThrow();
        } catch (RuntimeException error) {
            throw failure(item, id, "component codec rejected its JSON value", error);
        }
    }

    private static IllegalArgumentException failure(Identifier item, String id, String reason, Throwable cause) {
        String detail = cause == null || cause.getMessage() == null ? reason : reason + ": " + cause.getMessage();
        return new IllegalArgumentException("[NEKO-4036] Item '" + item + "' component '" + id
                + "' rejected on " + net.minecraft.SharedConstants.getCurrentVersion().name() + ": " + detail, cause);
    }
}
