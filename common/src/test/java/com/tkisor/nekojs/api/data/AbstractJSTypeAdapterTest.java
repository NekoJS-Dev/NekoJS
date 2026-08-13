package com.tkisor.nekojs.api.data;

import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AbstractJSTypeAdapter} test/apply 一致性回归：
 * 字符串输入形状只有在适配器真正覆盖 {@code fromString} 时才应被接受，
 * 不得出现「test 通过但 apply 抛异常」的不一致。
 */
class AbstractJSTypeAdapterTest {

    /** 不接受字符串：未覆盖 fromString，也不识别任何宿主对象。 */
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

    /** 接受字符串：覆盖 fromString。 */
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
