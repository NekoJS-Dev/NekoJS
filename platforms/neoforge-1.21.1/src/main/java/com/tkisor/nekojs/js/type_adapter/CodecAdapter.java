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
 * 通用 Codec 适配器：任意带 {@link Codec} 的 MC 数据类都可通过它注册成 JS 可转换类型。
 *
 * <p>接受 string / object / array(string) / 自身类型宿主对象。string 与 object 路径走
 * {@code codec.parse(JsonOps.INSTANCE, jsonElement)}。
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
