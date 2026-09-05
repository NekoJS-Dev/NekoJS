package com.tkisor.nekojs.js.type_adapter;

import com.mojang.serialization.Codec;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegistry;
import com.tkisor.nekojs.api.data.RecordJSTypeAdapter;

import java.util.Map;

/**
 * platform 侧 adapter 注册 DSL。Codec 是 MC 依赖，不能放在 common 模块，故提供此入口。
 */
public final class TypeAdapterDsl {
    private TypeAdapterDsl() {}

    /** 注册一个 {@link CodecAdapter}（precedence=LOWEST 的通用兜底映射）。 */
    public static <T> void registerCodec(JSTypeAdapterRegistry registry, Class<T> target, Codec<T> codec) {
        registry.register(new CodecAdapter<>(target, codec));
    }

    /** 注册通用 record 适配器（{@link RecordJSTypeAdapter}）：JS 对象字面量按 component 名构造，字段全部必填。 */
    public static <T extends Record> void registerRecord(JSTypeAdapterRegistry registry, Class<T> recordClass) {
        registerRecord(registry, recordClass, Map.of());
    }

    /** 同上，但为指定 component 声明默认值（缺字段时使用；probe 里对应可选槽位）。 */
    public static <T extends Record> void registerRecord(JSTypeAdapterRegistry registry, Class<T> recordClass,
                                                         Map<String, Object> defaults) {
        registry.register(new RecordJSTypeAdapter<>(recordClass, defaults));
    }
}
