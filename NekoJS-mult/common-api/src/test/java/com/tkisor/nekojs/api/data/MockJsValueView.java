package com.tkisor.nekojs.api.data;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

interface MockJsValueView extends JsValueView {

    default boolean isNull() { return false; }
    default boolean isString() { return false; }
    default boolean isNumber() { return false; }
    default boolean isBoolean() { return false; }
    default boolean isHostObject() { return false; }
    default boolean isArray() { return false; }

    default String asString() { throw new UnsupportedOperationException(); }
    default int asInt() { throw new UnsupportedOperationException(); }
    default double asDouble() { throw new UnsupportedOperationException(); }
    default boolean asBoolean() { throw new UnsupportedOperationException(); }

    default <T> T asHostObject(Class<T> type) { throw new UnsupportedOperationException(); }

    default boolean hasMember(String key) { return false; }
    default JsValueView getMember(String key) { throw new UnsupportedOperationException(); }
    default JsValueView getArrayElement(long index) { throw new UnsupportedOperationException(); }
    default long getArraySize() { return 0; }
    default Collection<String> getMemberKeys() { return Collections.emptyList(); }

    final class NullMock implements MockJsValueView {
        @Override public boolean isNull() { return true; }
    }

    final class StringMock implements MockJsValueView {
        private final String value;
        StringMock(String value) { this.value = value; }
        @Override public boolean isString() { return true; }
        @Override public String asString() { return value; }
    }

    final class HostMock implements MockJsValueView {
        private final Object value;
        HostMock(Object value) { this.value = value; }
        @Override public boolean isHostObject() { return true; }
        @Override public <T> T asHostObject(Class<T> type) { return type.cast(value); }
    }

    final class NumberMock implements MockJsValueView {
        private final int value;
        NumberMock(int value) { this.value = value; }
        @Override public boolean isNumber() { return true; }
        @Override public int asInt() { return value; }
        @Override public double asDouble() { return value; }
    }

    final class ArrayMock implements MockJsValueView {
        private final List<JsValueView> elements;
        ArrayMock(List<JsValueView> elements) { this.elements = elements; }
        @Override public boolean isArray() { return true; }
        @Override public JsValueView getArrayElement(long index) { return elements.get((int) index); }
        @Override public long getArraySize() { return elements.size(); }
    }
}
