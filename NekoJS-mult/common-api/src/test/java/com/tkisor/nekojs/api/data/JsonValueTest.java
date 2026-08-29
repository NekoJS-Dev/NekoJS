package com.tkisor.nekojs.api.data;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonValueTest {
    @Test
    void objectCopiesValuesAndPreservesInsertionOrder() {
        Map<String, JsonValue> source = new LinkedHashMap<>();
        source.put("first", JsonValue.number("1"));
        source.put("second", JsonValue.string("two"));

        JsonValue.ObjectValue value = JsonValue.object(source);
        source.put("later", JsonValue.nullValue());

        assertEquals(List.of("first", "second"), value.values().keySet().stream().toList());
        assertThrows(UnsupportedOperationException.class,
                () -> value.values().put("third", JsonValue.bool(true)));
    }

    @Test
    void rejectsInvalidNumericLexemesAndUnpairedSurrogates() {
        assertThrows(IllegalArgumentException.class, () -> JsonValue.number("01"));
        assertThrows(IllegalArgumentException.class, () -> JsonValue.string("\uD800"));
    }
}
