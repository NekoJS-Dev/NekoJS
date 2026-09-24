package com.tkisor.nekojs.api.ui;

import java.util.List;

/**
 * Immutable measured layout of a text run: pure data produced by {@link TextLayouter}
 * from {@link FontAdapter} measurements. Widths and heights are in logical pixels.
 *
 * @param width      width of the widest line
 * @param height     total height ({@code lines.size() * lineHeight})
 * @param lines      wrapped lines in draw order, never empty and never null entries
 * @param truncated  true when an ellipsis replaced content that did not fit
 * @param lineHeight height of a single line box
 * @param ascent     baseline offset from the top of a line box
 */
public record TextLayout(int width, int height, List<String> lines, boolean truncated, int lineHeight, int ascent) {
    public TextLayout {
        lines = List.copyOf(lines);
        if (lines.isEmpty()) throw new IllegalArgumentException("TextLayout requires at least one line");
        if (width < 0 || height < 0 || lineHeight <= 0 || ascent < 0) {
            throw new IllegalArgumentException("TextLayout requires non-negative size and positive line height");
        }
    }
}
