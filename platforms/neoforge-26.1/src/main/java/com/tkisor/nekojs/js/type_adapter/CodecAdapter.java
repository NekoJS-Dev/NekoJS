package com.tkisor.nekojs.js.type_adapter;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import graal.graalvm.polyglot.HostAccess;
import graal.graalvm.polyglot.Value;
import java.util.List;

import static com.tkisor.nekojs.api.AdapterInputShape.*;

/**
 * 通用 Codec 适配器：任意 JS 值 → {@link JsonElement} → {@code codec.parse(JsonOps)}。
 *
 * <p>precedence={@link HostAccess.TargetMappingPrecedence#LOWEST}，作为兜底映射，避免抢占更具体的 adapter。
 */
public final class CodecAdapter<T> extends AbstractJSTypeAdapter<T> {
    private final Class<T> target;
    private final Codec<T> codec;

    public CodecAdapter(Class<T> target, Codec<T> codec) {
        this.target = target;
        this.codec = codec;
    }

    @Override
    public Class<T> getTargetClass() {
        return target;
    }

    @Override
    protected T fromString(String s) {
        return parse(JsonObjectAdapter.primitiveJson(s));
    }

    @Override
    protected T fromHostObject(Object host) {
        // null 表示不识别（test 据此返回 false）
        return target.isInstance(host) ? target.cast(host) : null;
    }

    @Override
    protected boolean acceptOther(Value v) {
        return v.hasMembers() || v.hasArrayElements();
    }

    @Override
    protected T fromOther(Value v) {
        return parse(JsonObjectAdapter.convertValueToJson(v));
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(self(), object(), arrayOf(self()), string());
    }

    @Override
    public HostAccess.TargetMappingPrecedence getPrecedence() {
        return HostAccess.TargetMappingPrecedence.LOWEST;
    }

    private T parse(JsonElement json) {
        var r = codec.parse(JsonOps.INSTANCE, json);
        return r.result().orElseThrow(() -> new ValueConversionException(target, "codec-compatible value", json,
            r.error().map(e -> e.message()).orElse("codec parse failed")));
    }
}
