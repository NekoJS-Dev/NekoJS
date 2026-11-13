package com.tkisor.nekojs.api.data;

import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for test/apply consistency in {@link AbstractJSTypeAdapter}: a string input shape
 * is accepted only when the adapter actually overrides {@code fromString}, so test must never pass
 * while apply throws.
 */
class AbstractJSTypeAdapterTest {

    /** Accepts no strings: it does not override fromString and recognizes no host object. */
    static final class NoStringAdapter extends AbstractJSTypeAdapter<String> {
        @Override
        public Class<String> getTargetClass() {
            return String.class;
        }

        @Override
        protected String fromHostObject(Object host) {
            return null;
        }
    }

    /** Accepts strings: it overrides fromString. */
    static final class StringAdapter extends AbstractJSTypeAdapter<String> {
        @Override
        public Class<String> getTargetClass() {
            return String.class;
        }

        @Override
        protected String fromHostObject(Object host) {
            return null;
        }

        @Override
        protected String fromString(String s) {
            return "parsed:" + s;
        }
    }

    @Test
    void stringShapeIsRejectedWhenAdapterDoesNotAcceptStrings() {
        NoStringAdapter adapter = new NoStringAdapter();
        Value value = Value.asValue("abc");

        assertFalse(adapter.test(value),
                "adapter that does not override fromString must reject the string shape");
        assertThrows(ValueConversionException.class, () -> adapter.apply(value));
    }

    @Test
    void stringShapeIsAcceptedWhenAdapterOverridesFromString() {
        StringAdapter adapter = new StringAdapter();
        Value value = Value.asValue("abc");

        assertTrue(adapter.test(value));
        assertEquals("parsed:abc", adapter.apply(value));
    }
}
