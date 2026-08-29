package com.tkisor.nekojs.core.bridge;

import com.tkisor.nekojs.api.data.JsValueView;
import graal.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.Collection;

public final class GraalValueView implements JsValueView {
    private final Value delegate;

    private GraalValueView(Value delegate) {
        this.delegate = delegate;
    }

    public static JsValueView wrap(Value value) {
        if (value == null) {
            return null;
        }
        return new GraalValueView(value);
    }

    public Value unwrap() {
        return delegate;
    }

    @Override
    public boolean isNull() {
        return delegate.isNull();
    }

    @Override
    public boolean isString() {
        return delegate.isString();
    }

    @Override
    public boolean isNumber() {
        return delegate.isNumber();
    }

    @Override
    public boolean isBoolean() {
        return delegate.isBoolean();
    }

    /**
     * Delegates to the GraalVM polyglot runtime's {@link Value#isHostObject()},
     * which is the most reliable indicator of whether this value represents a
     * host-language (Java) object.
     */
    @Override
    public boolean isHostObject() {
        return delegate.isHostObject();
    }

    @Override
    public boolean isArray() {
        return delegate.hasArrayElements();
    }

    @Override
    public boolean isProxyObject() {
        return delegate.isProxyObject();
    }

    @Override
    public Object asProxyObject() {
        return delegate.asProxyObject();
    }

    @Override
    public String asString() {
        return delegate.asString();
    }

    @Override
    public int asInt() {
        return delegate.asInt();
    }

    @Override
    public double asDouble() {
        return delegate.asDouble();
    }

    @Override
    public boolean asBoolean() {
        return delegate.asBoolean();
    }

    @Override
    public <T> T asHostObject(Class<T> type) {
        try {
            return type.cast(delegate.asHostObject());
        } catch (ClassCastException | UnsupportedOperationException e) {
            return delegate.as(type);
        }
    }

    @Override
    public boolean hasMember(String key) {
        return delegate.hasMember(key);
    }

    @Override
    public JsValueView getMember(String key) {
        return wrap(delegate.getMember(key));
    }

    @Override
    public JsValueView getArrayElement(long index) {
        return wrap(delegate.getArrayElement(index));
    }

    @Override
    public long getArraySize() {
        return delegate.getArraySize();
    }

    @Override
    public Collection<String> getMemberKeys() {
        return new ArrayList<>(delegate.getMemberKeys());
    }
}
