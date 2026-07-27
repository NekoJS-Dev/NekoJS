package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.AdapterInputShape;

import java.util.List;
import java.util.Objects;

public abstract class AbstractJsTypeAdapter<T> implements JsTypeAdapter<T> {
    private final Class<T> targetType;

    protected AbstractJsTypeAdapter(Class<T> targetType) {
        this.targetType = Objects.requireNonNull(targetType, "targetType");
    }

    @Override
    public Class<T> targetType() {
        return targetType;
    }

    @Override
    public ConversionPrecedence precedence() {
        return ConversionPrecedence.LOWEST;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of();
    }

    protected boolean acceptNull() {
        return false;
    }

    protected T defaultValue() {
        return null;
    }

    protected boolean supportsString() {
        return true;
    }

    protected T fromString(String s) {
        throw new ValueConversionException(targetType, "string", s, "not supported");
    }

    protected abstract T fromHostObject(Object host);

    protected boolean acceptOther(JsValueView value) {
        return false;
    }

    protected T fromOther(JsValueView value) {
        throw new ValueConversionException(targetType, "other", value, "not supported");
    }

    @Override
    public boolean supports(JsValueView value, ConversionContext context) {
        if (value.isNull()) return acceptNull();
        if (value.isString()) return supportsString();
        if (value.isHostObject()) {
            Object host = value.asHostObject(Object.class);
            return host != null && targetType.isAssignableFrom(host.getClass());
        }
        return acceptOther(value);
    }

    @Override
    public T convert(JsValueView value, ConversionContext context) {
        if (value.isNull()) return defaultValue();
        if (value.isString()) return fromString(value.asString());
        if (value.isHostObject()) return fromHostObject(value.asHostObject(Object.class));
        return fromOther(value);
    }
}
