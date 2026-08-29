package com.tkisor.nekojs.core.bridge;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.*;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LegacyAdapterBridgeTest {

    private Context context;

    @BeforeEach
    void setUp() {
        context = Context.newBuilder("js").allowAllAccess(true).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void targetTypeDelegatesToWrappedAdapter() {
        JSTypeAdapter<String> legacy = new StringLengthAdapter();
        JsTypeAdapter<String> bridge = new LegacyAdapterBridge<>(legacy);

        assertEquals(String.class, bridge.targetType());
    }

    @Test
    void supportsStringViaGraalValueView() {
        JSTypeAdapter<String> legacy = new StringLengthAdapter();
        JsTypeAdapter<String> bridge = new LegacyAdapterBridge<>(legacy);

        Value v = context.eval("js", "'hello world'");
        JsValueView view = GraalValueView.wrap(v);

        assertTrue(bridge.supports(view, ConversionContext.empty()));
    }

    @Test
    void convertReturnsAdaptedValue() {
        JSTypeAdapter<String> legacy = new StringLengthAdapter();
        JsTypeAdapter<String> bridge = new LegacyAdapterBridge<>(legacy);

        Value v = context.eval("js", "'abc'");
        JsValueView view = GraalValueView.wrap(v);

        assertEquals("3", bridge.convert(view, ConversionContext.empty()));
    }

    @Test
    void precedenceMapsFromHostAccess() {
        JSTypeAdapter<String> legacy = new HighPrecedenceAdapter();
        JsTypeAdapter<String> bridge = new LegacyAdapterBridge<>(legacy);

        assertEquals(ConversionPrecedence.HIGH, bridge.precedence());
    }

    @Test
    void supportsReturnsFalseForNonGraalValueView() {
        JsTypeAdapter<String> bridge = new LegacyAdapterBridge<>(new StringLengthAdapter());
        JsValueView mock = new JsValueView() {
            @Override public boolean isNull() { return false; }
            @Override public boolean isString() { return true; }
            @Override public boolean isNumber() { return false; }
            @Override public boolean isBoolean() { return false; }
            @Override public boolean isHostObject() { return false; }
            @Override public boolean isArray() { return false; }

            @Override public String asString() { return "test"; }
            @Override public int asInt() { return 0; }
            @Override public double asDouble() { return 0; }
            @Override public boolean asBoolean() { return false; }

            @Override public <T> T asHostObject(Class<T> type) { return null; }

            @Override public boolean hasMember(String key) { return false; }
            @Override public JsValueView getMember(String key) { return null; }
            @Override public JsValueView getArrayElement(long index) { return null; }
            @Override public long getArraySize() { return 0; }
            @Override public Collection<String> getMemberKeys() { return Collections.emptyList(); }
        };

        assertFalse(bridge.supports(mock, ConversionContext.empty()));
    }

    @Test
    void inputShapesDelegates() {
        final class ShapedAdapter implements JSTypeAdapter<String> {
            @Override public Class<String> getTargetClass() { return String.class; }
            @Override public boolean test(Value value) { return value.isString(); }
            @Override public String apply(Value value) { return value.asString(); }
            @Override public List<AdapterInputShape> inputShapes() {
                return List.of(AdapterInputShape.string());
            }
        }

        JsTypeAdapter<String> bridge = new LegacyAdapterBridge<>(new ShapedAdapter());
        assertEquals(1, bridge.inputShapes().size());
    }

    private static final class StringLengthAdapter implements JSTypeAdapter<String> {
        @Override public Class<String> getTargetClass() { return String.class; }
        @Override public boolean test(Value value) { return value.isString(); }
        @Override public String apply(Value value) { return String.valueOf(value.asString().length()); }
    }

    private static final class HighPrecedenceAdapter implements JSTypeAdapter<String> {
        @Override public Class<String> getTargetClass() { return String.class; }
        @Override public boolean test(Value value) { return value.isString(); }
        @Override public String apply(Value value) { return value.asString(); }
        @Override public ConversionPrecedence getPrecedence() {
            return ConversionPrecedence.HIGH;
        }
    }
}
