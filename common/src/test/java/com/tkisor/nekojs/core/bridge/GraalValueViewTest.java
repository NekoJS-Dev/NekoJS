package com.tkisor.nekojs.core.bridge;

import com.tkisor.nekojs.api.data.JsValueView;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GraalValueViewTest {

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
    void wrapsNullValue() {
        Value v = context.eval("js", "null");
        JsValueView view = GraalValueView.wrap(v);

        assertTrue(view.isNull());
    }

    @Test
    void wrapsStringValue() {
        Value v = context.eval("js", "'hello'");
        JsValueView view = GraalValueView.wrap(v);

        assertTrue(view.isString());
        assertFalse(view.isNull());
        assertEquals("hello", view.asString());
    }

    @Test
    void wrapsNumberValue() {
        Value v = context.eval("js", "42");
        JsValueView view = GraalValueView.wrap(v);

        assertTrue(view.isNumber());
        assertEquals(42, view.asInt());
        assertEquals(42.0, view.asDouble(), 0.001);
    }

    @Test
    void wrapsBooleanValue() {
        Value v = context.eval("js", "true");
        JsValueView view = GraalValueView.wrap(v);

        assertTrue(view.isBoolean());
        assertTrue(view.asBoolean());
    }

    @Test
    void wrapsHostObject() {
        Value v = context.eval("js", "new java.util.ArrayList()");
        JsValueView view = GraalValueView.wrap(v);

        assertTrue(view.isHostObject());
        Object result = view.asHostObject(Object.class);
        assertNotNull(result);
    }

    @Test
    void hasMemberReturnsTrueForExistingProperty() {
        Value v = context.eval("js", "({ a: 1, b: 'x' })");
        JsValueView view = GraalValueView.wrap(v);

        assertTrue(view.hasMember("a"));
        assertTrue(view.hasMember("b"));
        assertFalse(view.hasMember("c"));
    }

    @Test
    void getMemberReturnsWrappedValue() {
        Value v = context.eval("js", "({ name: 'NekoJS' })");
        JsValueView view = GraalValueView.wrap(v);
        JsValueView member = view.getMember("name");

        assertTrue(member.isString());
        assertEquals("NekoJS", member.asString());
    }

    @Test
    void getArrayElementAccessesByIndex() {
        Value v = context.eval("js", "[10, 20, 30]");
        JsValueView view = GraalValueView.wrap(v);

        assertTrue(view.isArray());
        assertEquals(3, view.getArraySize());
        assertEquals(10, view.getArrayElement(0).asInt());
        assertEquals(20, view.getArrayElement(1).asInt());
        assertEquals(30, view.getArrayElement(2).asInt());
    }

    @Test
    void wrapNullValueReturnsNull() {
        assertNull(GraalValueView.wrap(null));
    }

    @Test
    void unwrapReturnsOriginalValue() {
        Value original = context.eval("js", "42");
        GraalValueView view = (GraalValueView) GraalValueView.wrap(original);

        assertSame(original, view.unwrap());
    }
}
