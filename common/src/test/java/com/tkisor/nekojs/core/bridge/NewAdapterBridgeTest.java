package com.tkisor.nekojs.core.bridge;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.*;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NewAdapterBridgeTest {

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
    void getTargetClassDelegates() {
        JsTypeAdapter<Integer> neo = new StringToIntAdapter();
        NewAdapterBridge<Integer> bridge = new NewAdapterBridge<>(neo);

        assertEquals(Integer.class, bridge.getTargetClass());
    }

    @Test
    void testDelegatesToSupports() {
        JsTypeAdapter<Integer> neo = new StringToIntAdapter();
        NewAdapterBridge<Integer> bridge = new NewAdapterBridge<>(neo);

        Value v = context.eval("js", "'42'");
        assertTrue(bridge.test(v));

        Value nonString = context.eval("js", "true");
        assertFalse(bridge.test(nonString));
    }

    @Test
    void applyDelegatesToConvert() {
        JsTypeAdapter<Integer> neo = new StringToIntAdapter();
        NewAdapterBridge<Integer> bridge = new NewAdapterBridge<>(neo);

        Value v = context.eval("js", "'100'");
        assertEquals(100, bridge.apply(v));
    }

    @Test
    void getPrecedenceMapsToHostAccess() {
        JsTypeAdapter<Integer> neo = new JsTypeAdapter<>() {
            @Override public Class<Integer> targetType() { return Integer.class; }
            @Override public boolean supports(JsValueView v, ConversionContext c) { return v.isNumber(); }
            @Override public Integer convert(JsValueView v, ConversionContext c) { return v.asInt(); }
            @Override public ConversionPrecedence precedence() { return ConversionPrecedence.HIGH; }
        };

        NewAdapterBridge<Integer> bridge = new NewAdapterBridge<>(neo);
        assertEquals(ConversionPrecedence.HIGH, bridge.getPrecedence());
    }

    @Test
    void inputShapesDelegates() {
        JsTypeAdapter<Integer> neo = new StringToIntAdapter();
        NewAdapterBridge<Integer> bridge = new NewAdapterBridge<>(neo);

        assertEquals(2, bridge.inputShapes().size());
    }

    @Test
    void nullFromWrapReturnsNull() {
        JsTypeAdapter<Integer> neo = new StringToIntAdapter();
        NewAdapterBridge<Integer> bridge = new NewAdapterBridge<>(neo);

        Value v = context.eval("js", "null");
        assertTrue(v.isNull());
        assertNull(bridge.apply(v));
    }

    private static final class StringToIntAdapter implements JsTypeAdapter<Integer> {
        @Override public Class<Integer> targetType() { return Integer.class; }

        @Override public boolean supports(JsValueView value, ConversionContext context) {
            if (value.isNull()) return false;
            if (value.isString()) return true;
            if (value.isHostObject()) {
                Object host = value.asHostObject(Object.class);
                return host instanceof Number;
            }
            return false;
        }

        @Override public Integer convert(JsValueView value, ConversionContext context) {
            if (value.isNull()) return null;
            if (value.isString()) return Integer.parseInt(value.asString());
            if (value.isHostObject()) {
                Object host = value.asHostObject(Object.class);
                if (host instanceof Number n) return n.intValue();
            }
            throw new ValueConversionException(Integer.class, "string or number", value, "cannot convert");
        }

        @Override public ConversionPrecedence precedence() { return ConversionPrecedence.LOWEST; }

        @Override public List<AdapterInputShape> inputShapes() {
            return List.of(AdapterInputShape.string(), AdapterInputShape.number());
        }
    }
}
