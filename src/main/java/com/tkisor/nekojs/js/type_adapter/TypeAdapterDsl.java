package com.tkisor.nekojs.js.type_adapter;

import com.mojang.serialization.Codec;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegistry;
import com.tkisor.nekojs.api.data.RecordJSTypeAdapter;

import java.util.Map;

/**
 * Adapter registration DSL for the platform side. Codec is an MC dependency and cannot live in the
 * common module, so this entry point is provided here.
 */
public final class TypeAdapterDsl {
    private TypeAdapterDsl() {}

    /** Registers a {@link CodecAdapter} (a generic fallback mapping with precedence=LOWEST). */
    public static <T> void registerCodec(JSTypeAdapterRegistry registry, Class<T> target, Codec<T> codec) {
        registry.register(new CodecAdapter<>(target, codec));
    }

    /**
     * Registers the generic record adapter ({@link RecordJSTypeAdapter}): a JS object literal is built by
     * component name, and all fields are required.
     */
    public static <T extends Record> void registerRecord(JSTypeAdapterRegistry registry, Class<T> recordClass) {
        registerRecord(registry, recordClass, Map.of());
    }

    /**
     * Same as above, but declares defaults for the given components (used when a field is missing; the
     * probe marks those slots optional).
     */
    public static <T extends Record> void registerRecord(JSTypeAdapterRegistry registry, Class<T> recordClass,
                                                         Map<String, Object> defaults) {
        registry.register(new RecordJSTypeAdapter<>(recordClass, defaults));
    }
}
