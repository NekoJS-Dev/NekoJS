package com.tkisor.nekojs.js.type_adapter;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.tkisor.nekojs.api.data.ValueConversionException;
import com.tkisor.nekojs.core.JsonObjectAdapter;
import graal.graalvm.polyglot.Value;
import net.minecraft.core.component.DataComponentPatch;

/** Decodes component patches through the native codec with registries resolved for this call. */
public final class DataComponentsAdapter {
    private DataComponentsAdapter() {}

    public static DataComponentPatch toPatch(Value value) {
        if (value == null || value.isNull()) return DataComponentPatch.EMPTY;
        try {
            JsonElement json = JsonObjectAdapter.convertValueToJson(value);
            return DataComponentPatch.CODEC.parse(
                    ItemStackStringRegistries.current().createSerializationContext(JsonOps.INSTANCE), json)
                    .getOrThrow();
        } catch (RuntimeException failure) {
            throw new ValueConversionException(DataComponentPatch.class,
                    "DataComponentPatch object (keys = full component ids like 'minecraft:max_stack_size')",
                    value, "[NEKO-4054] Invalid item component patch: " + failure.getMessage(), failure);
        }
    }
}
