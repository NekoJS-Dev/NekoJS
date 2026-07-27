package com.tkisor.nekojs.core.bridge;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.*;
import graal.graalvm.polyglot.HostAccess;
import graal.graalvm.polyglot.Value;

import java.util.List;
import java.util.Objects;

public final class NewAdapterBridge<T> implements JSTypeAdapter<T> {
    private final JsTypeAdapter<T> delegate;

    public NewAdapterBridge(JsTypeAdapter<T> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public Class<T> getTargetClass() {
        return delegate.targetType();
    }

    @Override
    public boolean test(Value value) {
        JsValueView view = GraalValueView.wrap(value);
        if (view == null) return false;
        return delegate.supports(view, ConversionContext.empty());
    }

    @Override
    public T apply(Value value) {
        JsValueView view = GraalValueView.wrap(value);
        if (view == null) return null;
        return delegate.convert(view, ConversionContext.empty());
    }

    @Override
    public HostAccess.TargetMappingPrecedence getPrecedence() {
        return switch (delegate.precedence()) {
            case LOWEST -> HostAccess.TargetMappingPrecedence.LOWEST;
            case LOW -> HostAccess.TargetMappingPrecedence.LOW;
            case HIGH -> HostAccess.TargetMappingPrecedence.HIGH;
            case HIGHEST -> HostAccess.TargetMappingPrecedence.HIGHEST;
        };
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return delegate.inputShapes();
    }
}
