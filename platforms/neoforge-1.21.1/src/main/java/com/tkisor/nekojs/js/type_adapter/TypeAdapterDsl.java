package com.tkisor.nekojs.js.type_adapter;

import com.mojang.serialization.Codec;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegistry;

/**
 * 平台侧 adapter 注册的便利 DSL，集中"按 Codec 注册一类"这类高频用法，
 * 供 {@code NekoJSCorePlugin.registerAdapters} 等调用点使用。
 */
public final class TypeAdapterDsl {
    private TypeAdapterDsl() {}

    public static <T> void registerCodec(JSTypeAdapterRegistry registry, Class<T> target, Codec<T> codec) {
        registry.register(new CodecAdapter<>(target, codec));
    }
}
