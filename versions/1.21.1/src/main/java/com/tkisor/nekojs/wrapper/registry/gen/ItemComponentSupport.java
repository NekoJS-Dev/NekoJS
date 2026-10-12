package com.tkisor.nekojs.wrapper.registry.gen;

import com.google.gson.Gson;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonElement;
import com.tkisor.nekojs.core.registry.ItemDefinitionConfig;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/** Decodes frozen JSON during native item construction; codecs requiring registry operations fail explicitly. */
final class ItemComponentSupport {
    private static final Gson JSON = new Gson();
    private ItemComponentSupport() {}

    static void apply(Item.Properties properties, ResourceLocation item, ItemDefinitionConfig definition) {
        if (definition.jukeboxPlayable() != null) properties.jukeboxPlayable(
                ResourceKey.create(Registries.JUKEBOX_SONG, ResourceLocation.parse(definition.jukeboxPlayable())));
        definition.components().forEach((id, value) -> {
            DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.parse(id));
            if (type == null || type.codec() == null) throw failure(item, id, "unknown or non-serializable component", null);
            install(properties, item, id, type, JSON.toJsonTree(value));
        });
    }

    private static <T> void install(Item.Properties properties, ResourceLocation item, String id,
                                    DataComponentType<T> type, JsonElement json) {
        properties.component(type, decode(item, id, type, JsonOps.INSTANCE, json));
    }

    private static <T> T decode(ResourceLocation item, String id, DataComponentType<T> type,
                               DynamicOps<JsonElement> ops, JsonElement json) {
        try {
            return type.codec().parse(ops, json).getOrThrow();
        } catch (RuntimeException error) {
            throw failure(item, id, "component codec rejected its JSON value", error);
        }
    }

    private static IllegalArgumentException failure(ResourceLocation item, String id, String reason, Throwable cause) {
        String detail = cause == null || cause.getMessage() == null ? reason : reason + ": " + cause.getMessage();
        return new IllegalArgumentException("[NEKO-4036] Item '" + item + "' component '" + id
                + "' rejected on " + net.minecraft.SharedConstants.getCurrentVersion().getName() + ": " + detail, cause);
    }
}
