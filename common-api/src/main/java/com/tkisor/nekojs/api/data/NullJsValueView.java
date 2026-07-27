package com.tkisor.nekojs.api.data;

import java.util.Collection;
import java.util.Collections;

public final class NullJsValueView implements JsValueView {
    public static final NullJsValueView INSTANCE = new NullJsValueView();

    private NullJsValueView() {}

    @Override public boolean isNull() { return true; }
    @Override public boolean isString() { return false; }
    @Override public boolean isNumber() { return false; }
    @Override public boolean isBoolean() { return false; }
    @Override public boolean isHostObject() { return false; }
    @Override public boolean isArray() { return false; }

    @Override public String asString() { throw new UnsupportedOperationException(); }
    @Override public int asInt() { throw new UnsupportedOperationException(); }
    @Override public double asDouble() { throw new UnsupportedOperationException(); }
    @Override public boolean asBoolean() { throw new UnsupportedOperationException(); }
    @Override public <T> T asHostObject(Class<T> type) { return null; }

    @Override public boolean hasMember(String key) { return false; }
    @Override public JsValueView getMember(String key) { throw new UnsupportedOperationException(); }
    @Override public JsValueView getArrayElement(long index) { throw new UnsupportedOperationException(); }
    @Override public long getArraySize() { return 0; }
    @Override public Collection<String> getMemberKeys() { return Collections.emptyList(); }
}
