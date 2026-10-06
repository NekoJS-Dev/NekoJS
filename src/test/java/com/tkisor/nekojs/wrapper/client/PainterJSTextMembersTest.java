package com.tkisor.nekojs.wrapper.client;

import com.tkisor.nekojs.api.ui.FontAdapter;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct contracts of the {@code PainterJS} text measurement members (ticket 44):
 * {@code textWidth}, {@code wrapText} and {@code lineHeight} delegate to the shared
 * {@code TextLayouter} over the painter's {@link FontAdapter}. Runs without a live client
 * through the measurement-only constructor; real-client execution is NOT RUN here.
 */
class PainterJSTextMembersTest {

    /** Deterministic fake: every glyph is 6px wide at a 12px line box with a 10px baseline. */
    private static final FontAdapter FAKE_FONT = new FontAdapter() {
        @Override
        public int stringWidth(String text) {
            return text == null ? 0 : text.length() * 6;
        }

        @Override
        public int lineHeight() {
            return 12;
        }

        @Override
        public int ascent() {
            return 10;
        }
    };

    private static PainterJS painter() {
        return new PainterJS(null, FAKE_FONT, 0F);
    }

    @Test
    void textWidthMeasuresThroughTheFontAdapter() {
        PainterJS painter = painter();
        assertEquals(0, painter.textWidth(null), "null text measures 0");
        assertEquals(0, painter.textWidth(""), "empty text measures 0");
        assertEquals(4 * 6, painter.textWidth("abcd"), "4 glyphs at 6px each");
    }

    @Test
    void wrapTextSharesTheTextLayouterAlgorithm() {
        PainterJS painter = painter();
        assertEquals(List.of("alpha beta", "gamma delta"),
                painter.wrapText("alpha beta gamma delta", 11 * 6),
                "greedy wrap at the widest fitting line");
        assertEquals(List.of("one", "two"), painter.wrapText("one\ntwo", 100),
                "explicit newlines are paragraph breaks");
    }

    @Test
    void publicSurfaceKeepsConstructorParityAndChainableDrawCalls() throws ReflectiveOperationException {
        assertTrue(Arrays.stream(PainterJS.class.getConstructors())
                        .anyMatch(constructor -> constructor.getParameterCount() == 1),
                "PainterJS exposes a graphics-only constructor on every supported version");

        assertEquals(PainterJS.class, PainterJS.class.getMethod("color", Number.class).getReturnType());
        assertEquals(PainterJS.class, PainterJS.class.getMethod("resetColor").getReturnType());
        assertEquals(PainterJS.class, PainterJS.class.getMethod(
                "rect", int.class, int.class, int.class, int.class).getReturnType());
        assertEquals(PainterJS.class, PainterJS.class.getMethod(
                "text", String.class, int.class, int.class).getReturnType());
        assertEquals(PainterJS.class, PainterJS.class.getMethod(
                "texture", String.class, int.class, int.class, int.class, int.class).getReturnType());
        assertEquals(PainterJS.class, PainterJS.class.getMethod(
                "push").getReturnType());
        assertEquals(PainterJS.class, PainterJS.class.getMethod(
                "translate", float.class, float.class).getReturnType());
    }

    @Test
    void lineHeightComesFromTheFontAdapter() {
        assertEquals(12, painter().lineHeight());
    }
}
