package com.tkisor.nekojs.api.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Numeric layout assertions over a deterministic fake font: every character is
 * 2 px wide, line height 9, ascent 7. Fixed inputs, exact expected boxes.
 */
class TextLayouterTest {
    private static final FontAdapter FONT = new FontAdapter() {
        @Override
        public int stringWidth(String text) {
            return text == null ? 0 : text.length() * 2;
        }

        @Override
        public int lineHeight() {
            return 9;
        }

        @Override
        public int ascent() {
            return 7;
        }
    };

    @Test
    void singleLineMeasuresExactBox() {
        TextLayout layout = TextLayouter.layout(FONT, "hello", 100, false, TextLayouter.Truncation.OFF);
        assertEquals(10, layout.width());
        assertEquals(9, layout.height());
        assertEquals(List.of("hello"), layout.lines());
        assertFalse(layout.truncated());
        assertEquals(9, layout.lineHeight());
        assertEquals(7, layout.ascent());
    }

    @Test
    void wrapsGreedyOnSpaces() {
        TextLayout layout = TextLayouter.layout(FONT, "aaa bbb ccc", 10, true, TextLayouter.Truncation.OFF);
        // 5 characters fit into 10 px; every word plus a space exceeds that.
        assertEquals(List.of("aaa", "bbb", "ccc"), layout.lines());
        assertEquals(6, layout.width());
        assertEquals(27, layout.height());
        assertFalse(layout.truncated());
    }

    @Test
    void keepsWordsTogetherWhenTheyFit() {
        TextLayout layout = TextLayouter.layout(FONT, "ab cd", 10, true, TextLayouter.Truncation.OFF);
        // "ab cd" is exactly 10 px wide, so it stays one line.
        assertEquals(List.of("ab cd"), layout.lines());
        assertEquals(10, layout.width());
        assertEquals(9, layout.height());
    }

    @Test
    void breaksOverlongWordsByCharacters() {
        TextLayout layout = TextLayouter.layout(FONT, "aaaaaa", 10, true, TextLayouter.Truncation.OFF);
        assertEquals(List.of("aaaaa", "a"), layout.lines());
        assertEquals(10, layout.width());
        assertEquals(18, layout.height());
    }

    @Test
    void explicitNewlinesSplitParagraphs() {
        TextLayout layout = TextLayouter.layout(FONT, "ab\ncd", 100, true, TextLayouter.Truncation.OFF);
        assertEquals(List.of("ab", "cd"), layout.lines());
        assertEquals(4, layout.width());
        assertEquals(18, layout.height());
    }

    @Test
    void ellipsisTruncatesSingleLineToLimit() {
        TextLayout layout = TextLayouter.layout(FONT, "aaaaaa", 10, false, TextLayouter.Truncation.ELLIPSIS);
        // "aa..." is exactly 10 px; everything beyond that is replaced.
        assertEquals(List.of("aa..."), layout.lines());
        assertEquals(10, layout.width());
        assertEquals(9, layout.height());
        assertTrue(layout.truncated());
    }

    @Test
    void textWithinLimitIsNotMarkedTruncated() {
        TextLayout layout = TextLayouter.layout(FONT, "ab", 10, false, TextLayouter.Truncation.ELLIPSIS);
        assertEquals(List.of("ab"), layout.lines());
        assertFalse(layout.truncated());
    }

    @Test
    void emptyTextStillHasOneLineBox() {
        TextLayout layout = TextLayouter.layout(FONT, "", 100, true, TextLayouter.Truncation.OFF);
        assertEquals(List.of(""), layout.lines());
        assertEquals(0, layout.width());
        assertEquals(9, layout.height());
        TextLayout nullLayout = TextLayouter.layout(FONT, null, 100, true, TextLayouter.Truncation.OFF);
        assertEquals(List.of(""), nullLayout.lines());
    }

    @Test
    void zeroMaxWidthTerminatesWithCharacterLines() {
        TextLayout layout = TextLayouter.layout(FONT, "ab cd", 0, true, TextLayouter.Truncation.OFF);
        assertEquals(List.of("a", "b", "c", "d"), layout.lines());
    }

    @Test
    void scaledLayoutDoublesEveryBoxDimension() {
        TextLayout layout = TextLayouter.layoutScaled(FONT, "hello", 18, 100, false, TextLayouter.Truncation.OFF);
        assertEquals(20, layout.width());
        assertEquals(18, layout.height());
        assertEquals(18, layout.lineHeight());
        assertEquals(14, layout.ascent());
    }

    @Test
    void scaledLayoutWrapsAgainstScaledDownBudget() {
        // At 18 px the 100 px budget is 50 px at base size: "aaa bbb" (14 px) fits one
        // line; the reported box is scaled back up (ceil), so 14 px becomes 28 px.
        TextLayout layout = TextLayouter.layoutScaled(FONT, "aaa bbb", 18, 100, true, TextLayouter.Truncation.OFF);
        assertEquals(List.of("aaa bbb"), layout.lines());
        assertEquals(28, layout.width());
        assertEquals(18, layout.height());
    }

    @Test
    void baseFontSizeMatchesUnscaledLayout() {
        TextLayout base = TextLayouter.layout(FONT, "aaa bbb ccc", 10, true, TextLayouter.Truncation.OFF);
        TextLayout scaled = TextLayouter.layoutScaled(FONT, "aaa bbb ccc", 9, 10, true, TextLayouter.Truncation.OFF);
        assertEquals(base, scaled);
    }
}
