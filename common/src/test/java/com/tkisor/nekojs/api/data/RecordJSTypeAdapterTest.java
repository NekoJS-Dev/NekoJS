package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.AdapterInputShape;

import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyObject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RecordJSTypeAdapter}：对象字面量 → record 的字段映射、默认值、严格性、probe 形状。
 *
 * <p>嵌套字段用 host 对象直传（裸 JVM 无 targetTypeMapping 管线，ID 字符串等深转换由
 * in-game 引擎覆盖——见 BlockPosAdapterTest 的同款接缝说明）。
 */
class RecordJSTypeAdapterTest {

    enum Flavor { SWEET, SOUR }

    record Point(int x, int y) {}

    record Sample(int count, String label, Flavor flavor, Point at) {}

    record Lenient(int count, String label) {}

    /** 非 record 夹具：构造期拒绝路径用。 */
    static class NonRecord {}

    private static Value literal(Context context, Object... kv) {
        var map = new java.util.LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) map.put((String) kv[i], kv[i + 1]);
        return context.asValue(ProxyObject.fromMap(map));
    }

    @Test
    void convertsObjectLiteralByComponentName() {
        var adapter = new RecordJSTypeAdapter<>(Sample.class, Map.of());
        try (Context context = Context.newBuilder().build()) {
            Value value = literal(context, "count", 3, "label", "hi", "flavor", "sweet",
                    "at", Value.asValue(new Point(1, 2)));

            assertTrue(adapter.test(value));
            Sample sample = adapter.apply(value);
            assertEquals(3, sample.count());
            assertEquals("hi", sample.label());
            assertEquals(Flavor.SWEET, sample.flavor());
            assertEquals(new Point(1, 2), sample.at());
        }
    }

    @Test
    void defaultsFillMissingFieldsAndBecomeOptionalSlots() {
        var adapter = new RecordJSTypeAdapter<>(Lenient.class, Map.of("label", "fallback"));
        try (Context context = Context.newBuilder().build()) {
            Value value = literal(context, "count", 7);

            assertTrue(adapter.test(value));
            Lenient lenient = adapter.apply(value);
            assertEquals(7, lenient.count());
            assertEquals("fallback", lenient.label());
        }
    }

    @Test
    void missingFieldWithoutDefaultIsRejected() {
        var adapter = new RecordJSTypeAdapter<>(Lenient.class, Map.of());
        try (Context context = Context.newBuilder().build()) {
            Value value = literal(context, "count", 7);

            ValueConversionException e = assertThrows(ValueConversionException.class, () -> adapter.apply(value));
            assertTrue(e.getMessage().contains("label"), e.getMessage());
        }
    }

    @Test
    void unknownFieldIsRejectedWithExpectedNames() {
        var adapter = new RecordJSTypeAdapter<>(Lenient.class, Map.of());
        try (Context context = Context.newBuilder().build()) {
            Value value = literal(context, "count", 1, "label", "x", "nmae", "typo");

            ValueConversionException e = assertThrows(ValueConversionException.class, () -> adapter.apply(value));
            assertTrue(e.getMessage().contains("nmae"), e.getMessage());
            assertTrue(e.getMessage().contains("count"), e.getMessage());
        }
    }

    @Test
    void hostRecordInstancePassesThrough() {
        var adapter = new RecordJSTypeAdapter<>(Point.class, Map.of());
        Value value = Value.asValue(new Point(4, 5));

        assertTrue(adapter.test(value));
        assertEquals(new Point(4, 5), adapter.apply(value));
    }

    @Test
    void nonObjectInputsAreRejected() {
        var adapter = new RecordJSTypeAdapter<>(Point.class, Map.of());

        assertFalse(adapter.test(null));
        assertFalse(adapter.test(Value.asValue("not an object")));
        assertFalse(adapter.test(Value.asValue(42)));
        assertThrows(ValueConversionException.class, () -> adapter.apply(null));
        assertThrows(ValueConversionException.class, () -> adapter.apply(Value.asValue("str")));
    }

    @Test
    void rejectsNonRecordTargetAtConstruction() {
        @SuppressWarnings({"unchecked", "rawtypes"})
        Class<? extends Record> fake = (Class) NonRecord.class;
        assertThrows(IllegalArgumentException.class, () -> new RecordJSTypeAdapter<>(fake, Map.of()));
    }

    @Test
    void unknownDefaultKeyIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> new RecordJSTypeAdapter<>(Lenient.class, Map.of("nope", 1)));
    }

    @Test
    void probeShapeMirrorsComponentsAndDefaults() {
        var strict = new RecordJSTypeAdapter<>(Lenient.class, Map.of());
        var lenient = new RecordJSTypeAdapter<>(Lenient.class, Map.of("label", "x"));

        var strictShape = assertInstanceOf(AdapterInputShape.ObjectValue.class,
                strict.inputShapes().get(0));
        assertTrue(strictShape.slots().stream().allMatch(AdapterInputShape.Slot::required));

        var lenientShape = assertInstanceOf(AdapterInputShape.ObjectValue.class,
                lenient.inputShapes().get(0));
        assertTrue(lenientShape.slots().stream()
                .filter(s -> s.name().equals("label"))
                .allMatch(s -> !s.required()));
    }
}
