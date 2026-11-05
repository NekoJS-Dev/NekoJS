//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import com.tkisor.nekojs.api.ui.FontAdapter;
import com.tkisor.nekojs.api.ui.TextLayouter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UiTextLayoutCacheTest {
    @Test
    void reusesOnlyTheSameTextFontAndLayoutInputs() {
        var cache = new UiTextLayoutCache();
        var font = new CountingFont();
        var first = cache.layout(font, null, "sample", 9, 100, true, TextLayouter.Truncation.OFF, 1);
        int before = font.calls;
        assertSame(first, cache.layout(font, null, "sample", 9, 100, true, TextLayouter.Truncation.OFF, 1));
        assertSame(first, cache.layout(font, null, "sample", 0, 100, true, TextLayouter.Truncation.OFF, 1));
        assertEquals(before, font.calls);
        assertNotSame(first, cache.layout(font, "demo:font", "sample", 9, 100, true, TextLayouter.Truncation.OFF, 1));
        assertNotSame(first, cache.layout(font, null, "changed", 9, 100, true, TextLayouter.Truncation.OFF, 1));
        assertNotSame(first, cache.layout(font, null, "sample", 12, 100, true, TextLayouter.Truncation.OFF, 1));
        assertNotSame(first, cache.layout(font, null, "sample", 9, 80, true, TextLayouter.Truncation.OFF, 1));
        assertNotSame(first, cache.layout(font, null, "sample", 9, 100, false, TextLayouter.Truncation.OFF, 1));
        assertNotSame(first, cache.layout(font, null, "sample", 9, 100, true, TextLayouter.Truncation.ELLIPSIS, 1));
    }

    @Test
    void resourceChangesAndTerminalClearDiscardPriorLayouts() {
        var cache = new UiTextLayoutCache();
        var font = new CountingFont();
        var first = cache.layout(font, null, "sample", 9, 100, true, TextLayouter.Truncation.OFF, 1);
        var reloaded = cache.layout(font, null, "sample", 9, 100, true, TextLayouter.Truncation.OFF, 2);
        assertNotSame(first, reloaded);
        cache.clear();
        assertNotSame(reloaded, cache.layout(font, null, "sample", 9, 100, true, TextLayouter.Truncation.OFF, 2));
        assertNotSame(first, new UiTextLayoutCache().layout(font, null, "sample", 9, 100, true, TextLayouter.Truncation.OFF, 1));
    }

    @Test
    void evictsOldEntriesAndDoesNotRetainOversizedText() {
        var cache = new UiTextLayoutCache();
        var font = new CountingFont();
        var first = cache.layout(font, null, "value0", 9, 100, false, TextLayouter.Truncation.OFF, 1);
        for (int index = 1; index <= 256; index++) {
            cache.layout(font, null, "value" + index, 9, 100, false, TextLayouter.Truncation.OFF, 1);
        }
        assertNotSame(first, cache.layout(font, null, "value0", 9, 100, false, TextLayouter.Truncation.OFF, 1));
        String retained = "x".repeat(300_000);
        var weighted = cache.layout(font, null, retained, 9, 100, false, TextLayouter.Truncation.OFF, 1);
        cache.layout(font, null, "y".repeat(300_000), 9, 100, false, TextLayouter.Truncation.OFF, 1);
        assertNotSame(weighted, cache.layout(font, null, retained, 9, 100, false, TextLayouter.Truncation.OFF, 1));
        String oversized = "x".repeat(600_000);
        var large = cache.layout(font, null, oversized, 9, 100, false, TextLayouter.Truncation.OFF, 1);
        assertNotSame(large, cache.layout(font, null, oversized, 9, 100, false, TextLayouter.Truncation.OFF, 1));
    }

    @Test
    void repeatedLongPathsDoNotRemeasureEveryPrefixForEveryStackLine() {
        var cache = new UiTextLayoutCache();
        var font = new CountingFont();
        String path = "long_directory_name/".repeat(12) + "script.js:128";
        String text = ("at callback (" + path + ")\n").repeat(60);
        var layout = cache.layout(font, null, text, 9, 100, true, TextLayouter.Truncation.OFF, 1);
        assertEquals(TextLayouter.layoutScaled(new CountingFont(), text, 9, 100, true, TextLayouter.Truncation.OFF), layout);
        assertTrue(font.calls < 2000, () -> "Repeated stack prefixes triggered " + font.calls + " font measurements");
    }

    private static final class CountingFont implements FontAdapter {
        int calls;

        @Override
        public int stringWidth(String text) { calls++; return text.length() * 6; }

        @Override
        public int lineHeight() { return 9; }

        @Override
        public int ascent() { return 7; }
    }
}
//?}
