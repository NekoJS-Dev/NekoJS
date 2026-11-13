package com.tkisor.nekojs.js.type_adapter;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import com.tkisor.nekojs.core.JsonObjectAdapter;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import graal.graalvm.polyglot.Value;
import java.util.List;
import static com.tkisor.nekojs.api.AdapterInputShape.*;

/**
 * Generic codec adapter: any JS value → {@link JsonElement} → {@code codec.parse(JsonOps)}.
 *
 * <p>precedence={@link ConversionPrecedence#LOWEST}: it is the fallback mapping, so it does
 * not take precedence over a more specific adapter.
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
        // A null return means not recognized (tests use it to return false)
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
    public ConversionPrecedence getPrecedence() {
        return ConversionPrecedence.LOWEST;
    }

    private T parse(JsonElement json) {
        var r = codec.parse(JsonOps.INSTANCE, json);
        return r.result().orElseThrow(() -> new ValueConversionException(target, "codec-compatible value", json,
            r.error().map(e -> e.message()).orElse("codec parse failed")));
    }
}
