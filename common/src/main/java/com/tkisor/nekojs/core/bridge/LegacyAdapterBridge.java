package com.tkisor.nekojs.core.bridge;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.*;

import java.util.List;
import java.util.Objects;

public final class LegacyAdapterBridge<T> implements JsTypeAdapter<T> {
    private final JSTypeAdapter<T> legacy;

    public LegacyAdapterBridge(JSTypeAdapter<T> legacy) {
        this.legacy = Objects.requireNonNull(legacy, "legacy");
    }

    @Override
    public Class<T> targetType() {
        return legacy.getTargetClass();
    }

    @Override
    public boolean supports(JsValueView value, ConversionContext context) {
        if (!(value instanceof GraalValueView gv)) return false;
        return legacy.test(gv.unwrap());
    }

    @Override
    public T convert(JsValueView value, ConversionContext context) {
        if (!(value instanceof GraalValueView gv)) {
            throw new ValueConversionException(targetType(), "Graal Value", value,
                "LegacyAdapterBridge requires a GraalValueView");
        }
        return legacy.apply(gv.unwrap());
    }

    @Override
    public ConversionPrecedence precedence() {
        return switch (legacy.getPrecedence()) {
            case LOWEST -> ConversionPrecedence.LOWEST;
            case LOW -> ConversionPrecedence.LOW;
            case HIGH -> ConversionPrecedence.HIGH;
            case HIGHEST -> ConversionPrecedence.HIGHEST;
        };
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return legacy.inputShapes();
    }
}
