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

import static org.junit.jupiter.api.Assertions.*;

class RegistryBridgeIntegrationTest {

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
    void registerNewAdapterAndUseViaLegacyView() {
        JSTypeAdapterRegistry.Impl registry = new JSTypeAdapterRegistry.Impl();

        JsTypeAdapter<String> neo = new JsTypeAdapter<>() {
            @Override public Class<String> targetType() { return String.class; }
            @Override public boolean supports(JsValueView value, ConversionContext context) {
                return value.isString();
            }
            @Override public String convert(JsValueView value, ConversionContext context) {
                return "[" + value.asString() + "]";
            }
            @Override public ConversionPrecedence precedence() { return ConversionPrecedence.LOWEST; }
        };

        registry.register(neo);
        Collection<JSTypeAdapter<?>> view = registry.view();
        assertEquals(1, view.size());

        JSTypeAdapter<?> legacy = view.iterator().next();
        Value v = context.eval("js", "'hello'");
        assertTrue(legacy.test(v));
        assertEquals("[hello]", legacy.apply(v));
    }

    @Test
    void oldAndNewAdaptersCoexistInSameRegistry() {
        JSTypeAdapterRegistry.Impl registry = new JSTypeAdapterRegistry.Impl();

        registry.register(new JsTypeAdapter<Integer>() {
            @Override public Class<Integer> targetType() { return Integer.class; }
            @Override public boolean supports(JsValueView value, ConversionContext context) {
                return value.isString();
            }
            @Override public Integer convert(JsValueView value, ConversionContext context) {
                return Integer.parseInt(value.asString());
            }
            @Override public ConversionPrecedence precedence() { return ConversionPrecedence.LOWEST; }
        });

        registry.register(Integer.class,
            v -> v.isNumber(),
            v -> v.asInt() * 2);

        assertEquals(2, registry.view().size());
    }
}
