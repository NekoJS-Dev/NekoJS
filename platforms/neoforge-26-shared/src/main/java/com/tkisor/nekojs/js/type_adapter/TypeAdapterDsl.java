package com.tkisor.nekojs.js.type_adapter;

import com.mojang.serialization.Codec;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegistry;

/**
 * platform 侧 adapter 注册 DSL。Codec 是 MC 依赖，不能放在 common 模块，故提供此入口。
 */
public final class TypeAdapterDsl {
    private TypeAdapterDsl() {}

    /** 注册一个 {@link CodecAdapter}（precedence=LOWEST 的通用兜底映射）。 */
    public static <T> void registerCodec(JSTypeAdapterRegistry registry, Class<T> target, Codec<T> codec) {
        registry.register(new CodecAdapter<>(target, codec));
    }
}
