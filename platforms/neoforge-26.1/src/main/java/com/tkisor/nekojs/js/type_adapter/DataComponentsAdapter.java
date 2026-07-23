package com.tkisor.nekojs.js.type_adapter;

import com.google.gson.JsonElement;
import com.tkisor.nekojs.api.data.ValueConversionException;
import com.tkisor.nekojs.core.JsonObjectAdapter;
import com.mojang.serialization.JsonOps;
import graal.graalvm.polyglot.Value;
import net.minecraft.core.component.DataComponentPatch;

/**
 * 把 JS 对象（{@code { "minecraft:max_stack_size": 64, ... }}）解析成
 * {@link DataComponentPatch}。借 {@link DataComponentPatch#CODEC} 一次性解析，
 * 零逐组件字段映射，键必须为完整 component id（{@code namespace:path}）。
 */
public final class DataComponentsAdapter {
    private DataComponentsAdapter() {}

    public static DataComponentPatch toPatch(Value value) {
        if (value == null || value.isNull()) return DataComponentPatch.EMPTY;
        JsonElement json = JsonObjectAdapter.convertValueToJson(value);
        return DataComponentPatch.CODEC.parse(JsonOps.INSTANCE, json)
            .result()
            .orElseThrow(() -> new ValueConversionException(DataComponentPatch.class,
                "DataComponentPatch object (keys = full component ids like 'minecraft:max_stack_size')",
                value, "failed to parse components via DataComponentPatch.CODEC"));
    }
}
