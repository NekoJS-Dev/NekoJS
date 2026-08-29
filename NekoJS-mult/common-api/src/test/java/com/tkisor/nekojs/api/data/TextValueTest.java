package com.tkisor.nekojs.api.data;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TextValueTest {
    @Test
    void sequencesFlattenAndDiscardEmptyLiterals() {
        TextValue first = TextValue.literal("first");
        TextValue nested = TextValue.sequence(List.of(first, TextValue.literal("second")));
        TextValue.Sequence result = (TextValue.Sequence) TextValue.sequence(
                List.of(TextValue.empty(), nested, TextValue.literal("third")));

        assertEquals(List.of(first, TextValue.literal("second"), TextValue.literal("third")), result.values());
    }

    @Test
    void emptyAndSingleValueSequencesAreCanonical() {
        assertTrue(TextValue.sequence(List.of()).isEmpty());
        TextValue value = TextValue.literal("value");
        assertSame(value, TextValue.sequence(List.of(value)));
        assertFalse(TextValue.translatable("key", List.of()).isEmpty());
    }

    @Test
    void directSequenceConstructionCannotBypassCanonicalForm() {
        assertThrows(IllegalArgumentException.class,
                () -> new TextValue.Sequence(List.of(TextValue.empty(), TextValue.literal("value"))));
        assertThrows(IllegalArgumentException.class, () -> new TextValue.Sequence(List.of(
                TextValue.literal("a"),
                TextValue.sequence(List.of(TextValue.literal("b"), TextValue.literal("c"))))));
    }

    @Test
    void numbersUseJavaScriptCompatibleTextForm() {
        assertEquals("2", new TextArgument.NumberValue(2L).displayString());
        assertEquals("2.5", new TextArgument.NumberValue(2.5d).displayString());
        assertEquals("1e+21", new TextArgument.NumberValue(1.0e21).displayString());
        assertEquals("1000000000000000100",
                new TextArgument.NumberValue(
                        new JsNumber(1000000000000000128d, "1000000000000000100")).displayString());
    }

    @Test
    void nativeNumbersDoNotOverflowAtLongBoundary() {
        assertEquals(Long.valueOf((long) (0x1.0p63 - 2048.0d)),
                new JsNumber(0x1.0p63 - 2048.0d, "9223372036854773760").nativeNumber());
        assertEquals(Double.valueOf(0x1.0p63),
                new JsNumber(0x1.0p63, "9223372036854776000").nativeNumber());
    }
}
