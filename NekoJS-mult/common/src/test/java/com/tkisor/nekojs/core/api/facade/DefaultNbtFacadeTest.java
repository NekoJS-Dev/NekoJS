package com.tkisor.nekojs.core.api.facade;

import com.tkisor.nekojs.api.data.NbtValue;
import com.tkisor.nekojs.api.error.ApiInvocationException;
import com.tkisor.nekojs.api.nbt.NbtBinaryCodec;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultNbtFacadeTest {
    private final DefaultNbtFacade nbt = new DefaultNbtFacade(
            java.nio.file.Path.of("."), NbtBinaryCodec.unsupported());

    @Test
    void rendersDeterministicSnbtAndPreservesExplicitWidths() {
        LinkedHashMap<String, NbtValue> values = new LinkedHashMap<>();
        values.put("count", nbt.byteValue(2));
        values.put("long", nbt.longValue("9223372036854775807"));
        values.put("items", NbtValue.list(List.of(NbtValue.intValue(1), NbtValue.intValue(2))));

        assertEquals("{count:2b,long:9223372036854775807l,items:[1,2]}", nbt.toSnbt(NbtValue.compound(values)));
        assertEquals("9223372036854775807", nbt.scalar(nbt.longValue("9223372036854775807")));
        assertEquals("[B;1B,-2B]", nbt.toSnbt(nbt.byteArray(List.of(1, -2))));
        assertEquals("\"line1\nline2\"", nbt.toSnbt(NbtValue.string("line1\nline2")));
    }

    @Test
    void rejectsOutOfRangeExplicitWidths() {
        assertThrows(ApiInvocationException.class, () -> nbt.byteValue(128));
        assertThrows(ApiInvocationException.class, () -> nbt.longValue("not-a-long"));
        assertThrows(ApiInvocationException.class, () -> nbt.floatValue(3.5e38));
    }

    @Test
    void parseRoundTripsThroughSnbtSerializer() {
        // serialize -> parse -> serialize 应稳定（解析器读回序列化器产物）
        LinkedHashMap<String, NbtValue> values = new LinkedHashMap<>();
        values.put("count", nbt.byteValue(2));
        values.put("short", nbt.shortValue(100));
        values.put("name", NbtValue.string("hello"));
        values.put("items", NbtValue.list(List.of(NbtValue.intValue(1), NbtValue.intValue(2))));
        values.put("bytes", nbt.byteArray(List.of(1, -2, 3)));
        values.put("ints", nbt.intArray(List.of(10, 20)));
        NbtValue.CompoundValue original = NbtValue.compound(values);

        String snbt = nbt.toSnbt(original);
        NbtValue parsed = nbt.parse(snbt);
        assertInstanceOf(NbtValue.CompoundValue.class, parsed);
        // 解析后再序列化应与原序列化串一致
        assertEquals(snbt, nbt.toSnbt(parsed));
    }

    @Test
    void parseHandlesQuotedKeysAndEscapes() {
        NbtValue parsed = nbt.parse("{\"quoted key\":1b, msg:\"a\\\"b\"}");
        assertInstanceOf(NbtValue.CompoundValue.class, parsed);
        NbtValue.CompoundValue compound = (NbtValue.CompoundValue) parsed;
        assertEquals(NbtValue.byteValue((byte) 1), compound.values().get("quoted key"));
        assertEquals(NbtValue.string("a\"b"), compound.values().get("msg"));
    }

    @Test
    void parseInfersNumericWidthsFromSuffixes() {
        NbtValue parsed = nbt.parse("{b:5b,s:5s,i:5,l:5l,f:5.0f,d:5.0d}");
        NbtValue.CompoundValue compound = (NbtValue.CompoundValue) parsed;
        assertEquals(NbtValue.Kind.BYTE, compound.values().get("b").kind());
        assertEquals(NbtValue.Kind.SHORT, compound.values().get("s").kind());
        assertEquals(NbtValue.Kind.INT, compound.values().get("i").kind());
        assertEquals(NbtValue.Kind.LONG, compound.values().get("l").kind());
        assertEquals(NbtValue.Kind.FLOAT, compound.values().get("f").kind());
        assertEquals(NbtValue.Kind.DOUBLE, compound.values().get("d").kind());
    }

    @Test
    void parseRejectsMalformedSnbt() {
        assertThrows(ApiInvocationException.class, () -> nbt.parse("{unterminated"));
        assertThrows(ApiInvocationException.class, () -> nbt.parse("[1,2,"));
        assertThrows(ApiInvocationException.class, () -> nbt.parse("trailing {a:1} junk"));
    }

    @Test
    void toObjectFlattensToPlainJavaValues() {
        LinkedHashMap<String, NbtValue> values = new LinkedHashMap<>();
        values.put("name", NbtValue.string("x"));
        values.put("count", nbt.intValue(3));
        values.put("list", NbtValue.list(List.of(NbtValue.string("a"), NbtValue.string("b"))));

        Object result = nbt.toObject(NbtValue.compound(values));
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertEquals("x", map.get("name"));
        assertEquals(3, map.get("count"));
        assertEquals(List.of("a", "b"), map.get("list"));
    }

    @Test
    void fromObjectBuildsNbtFromPlainJavaValues() {
        LinkedHashMap<String, Object> nested = new LinkedHashMap<>();
        nested.put("flag", true);
        nested.put("amount", 5L);
        Map<String, Object> source = Map.of("name", "x", "nested", nested);

        NbtValue result = nbt.fromObject(source);
        assertInstanceOf(NbtValue.CompoundValue.class, result);
        NbtValue.CompoundValue compound = (NbtValue.CompoundValue) result;
        assertEquals(NbtValue.string("x"), compound.values().get("name"));
        NbtValue.CompoundValue nestedNbt = (NbtValue.CompoundValue) compound.values().get("nested");
        assertEquals(NbtValue.byteValue((byte) 1), nestedNbt.values().get("flag"));
        assertEquals(NbtValue.longValue(5L), nestedNbt.values().get("amount"));
    }

    @Test
    void fromObjectRejectsNull() {
        assertThrows(ApiInvocationException.class, () -> nbt.fromObject(null));
    }

    @Test
    void fromObjectRoundTripsThroughToObject() {
        LinkedHashMap<String, Object> source = new LinkedHashMap<>();
        source.put("n", 42);
        source.put("s", "hi");
        source.put("arr", List.of(1, 2, 3));

        NbtValue nbtValue = assertDoesNotThrow(() -> nbt.fromObject(source));
        Object back = nbt.toObject(nbtValue);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) back;
        assertEquals(42, map.get("n"));
        assertEquals("hi", map.get("s"));
    }
}
