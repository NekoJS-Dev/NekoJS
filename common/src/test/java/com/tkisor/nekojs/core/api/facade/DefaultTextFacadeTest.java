package com.tkisor.nekojs.core.api.facade;

import com.tkisor.nekojs.api.data.TextValue;
import com.tkisor.nekojs.api.error.ApiInvocationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultTextFacadeTest {
    private final DefaultTextFacade text = new DefaultTextFacade();

    @Test
    void normalizesPortableArgumentsAndAppendsImmutably() {
        TextValue original = text.ofValues(List.of("Count: ", 2, true));
        TextValue appended = text.append(original, List.of("!"));

        assertInstanceOf(TextValue.Sequence.class, original);
        TextValue.Sequence sequence = assertInstanceOf(TextValue.Sequence.class, appended);
        assertEquals(TextValue.literal("!"), sequence.values().getLast());
    }

    @Test
    void rejectsUnsupportedAndNonFiniteValues() {
        assertThrows(ApiInvocationException.class, () -> text.ofValues(List.of(new Object())));
        assertThrows(ApiInvocationException.class, () -> text.ofValues(List.of(Double.NaN)));
    }

    @Test
    void translationArgumentsPreservePrimitiveKinds() {
        TextValue.Translatable translated = assertInstanceOf(
                TextValue.Translatable.class,
                text.translatable("key", List.of("name", 2L, true, TextValue.literal("nested"))));

        assertInstanceOf(com.tkisor.nekojs.api.data.TextArgument.StringValue.class, translated.arguments().get(0));
        assertInstanceOf(com.tkisor.nekojs.api.data.TextArgument.NumberValue.class, translated.arguments().get(1));
        assertInstanceOf(com.tkisor.nekojs.api.data.TextArgument.BooleanValue.class, translated.arguments().get(2));
        assertInstanceOf(com.tkisor.nekojs.api.data.TextArgument.NestedText.class, translated.arguments().get(3));
    }
}
