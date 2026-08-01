package com.tkisor.nekojs.core.api.facade;

import com.tkisor.nekojs.api.data.TextClickEvent;
import com.tkisor.nekojs.api.data.TextStyle;
import com.tkisor.nekojs.api.data.TextValue;
import com.tkisor.nekojs.api.error.ApiInvocationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void styleMethodsProduceStyledValueAndChainMerges() {
        TextValue base = text.of("hi");
        // 单个样式产生 Styled，内部值保留原字面量
        TextValue.Styled bold = assertInstanceOf(TextValue.Styled.class, text.bold(base, true));
        assertEquals(TextValue.literal("hi"), bold.value());
        assertTrue(bold.style().bold());

        // 链式调用在已有样式上合并，不丢失 bold
        TextValue.Styled boldRed = assertInstanceOf(TextValue.Styled.class, text.color(bold, "red"));
        assertTrue(boldRed.style().bold());
        assertEquals("red", boldRed.style().color());
        assertEquals(TextValue.literal("hi"), boldRed.value());
    }

    @Test
    void styleDefaultsToTrueWhenCalledWithNoValue() {
        TextValue.Styled styled = assertInstanceOf(TextValue.Styled.class, text.italic(text.of("x"), true));
        assertTrue(styled.style().italic());
        assertNull(styled.style().bold());
    }

    @Test
    void emptyStyleIsNoOp() {
        // 全空样式判定为空；任何显式样式（哪怕 bold=false）都使样式非空
        assertTrue(TextStyle.empty().isEmpty());
        TextValue.Styled styled = assertInstanceOf(TextValue.Styled.class, text.bold(text.of("x"), false));
        assertFalse(styled.style().isEmpty());
    }

    @Test
    void clickBuildsAllActions() {
        TextValue.Styled run = assertInstanceOf(TextValue.Styled.class,
                text.click(text.of("c"), "runCommand", "/say hi"));
        assertInstanceOf(TextClickEvent.RunCommand.class, run.style().clickEvent());

        TextValue.Styled page = assertInstanceOf(TextValue.Styled.class,
                text.click(text.of("c"), "changePage", "3"));
        assertEquals(3, ((TextClickEvent.ChangePage) page.style().clickEvent()).page());
    }

    @Test
    void clickRejectsBadActionAndNonNumericPage() {
        assertThrows(ApiInvocationException.class, () -> text.click(text.of("c"), "bogus", "v"));
        assertThrows(ApiInvocationException.class, () -> text.click(text.of("c"), "changePage", "notanint"));
    }

    @Test
    void colorRejectsBlank() {
        assertThrows(ApiInvocationException.class, () -> text.color(text.of("c"), "  "));
    }

    @Test
    void sequenceAcceptsStyledChildren() {
        // Styled 子项应作为单个规范元素进入 Sequence（不被展开、不被拒绝）
        TextValue red = text.color(text.of("a"), "red");
        TextValue seq = text.ofValues(List.of(red, text.of("b")));
        assertInstanceOf(TextValue.Sequence.class, seq);
        assertEquals(2, ((TextValue.Sequence) seq).values().size());
    }
}
