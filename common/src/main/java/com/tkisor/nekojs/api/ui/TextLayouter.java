package com.tkisor.nekojs.api.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Text measurement and wrapping over a {@link FontAdapter}: the single algorithm the
 * UI contract uses for line breaking, so every node and the host painter agree on how
 * text turns into lines. Consumes only adapter measurements; contains no glyph
 * width guesses.
 */
public final class TextLayouter {
    /** Whether text that exceeds the available width is cut with an ellipsis. */
    public enum Truncation { OFF, ELLIPSIS }

    private TextLayouter() {
    }

    /**
     * Lays text out at the adapter's base size.
     *
     * <p>Explicit {@code \n} breaks paragraphs; within a paragraph, greedy word wrap
     * breaks on spaces, and a single word wider than {@code maxWidth} is broken by
     * characters. {@code maxWidth <= 0} is treated as 1 so layout always terminates.
     * With {@code wrap} false each paragraph stays one line and may overflow
     * {@code maxWidth}. {@code truncated} is true only when an ellipsis replaced
     * content.
     *
     * @param font        measurement source
     * @param text        text to lay out; {@code null} is treated as empty
     * @param maxWidth    maximum line width in logical pixels
     * @param wrap        true to wrap lines at {@code maxWidth}
     * @param truncation  whether overlong single results get an ellipsis
     * @return measured layout, never null
     */
    public static TextLayout layout(FontAdapter font, String text, int maxWidth, boolean wrap, Truncation truncation) {
        int limit = Math.max(1, maxWidth);
        List<String> lines = new ArrayList<>();
        boolean truncated = false;
        for (String paragraph : paragraphs(text)) {
            if (wrap) {
                appendWrapped(font, paragraph, limit, lines);
            } else {
                lines.add(paragraph);
            }
            if (truncation == Truncation.ELLIPSIS) {
                int last = lines.size() - 1;
                String fit = fitWithEllipsis(font, lines.get(last), limit);
                if (!fit.equals(lines.get(last))) truncated = true;
                // Ellipsis shortens only the last line of this paragraph; earlier wrapped
                // lines already fit, so unwrap-free truncation keeps them untouched.
                lines.set(last, fit);
            }
        }
        int width = 0;
        for (String line : lines) width = Math.max(width, font.stringWidth(line));
        int lineHeight = font.lineHeight();
        return new TextLayout(width, lineHeight * lines.size(), lines, truncated, lineHeight, font.ascent());
    }

    /**
     * Lays text out for a requested font size, scaling base measurements by
     * {@code fontSize / lineHeight}. Layout runs at the base size with the scaled-down
     * width budget, then dimensions are scaled back up (ceil for boxes, round for
     * ascent) so pixels are never lost to rounding.
     *
     * @param font       measurement source
     * @param text       text to lay out; {@code null} is treated as empty
     * @param fontSize   requested font size in logical pixels; {@code <= 0} uses the base size
     * @param maxWidth   maximum line width in logical pixels
     * @param wrap       true to wrap lines at {@code maxWidth}
     * @param truncation whether overlong single results get an ellipsis
     * @return measured layout at the requested size
     */
    public static TextLayout layoutScaled(
            FontAdapter font, String text, double fontSize, int maxWidth, boolean wrap, Truncation truncation) {
        double scale = fontSize <= 0 ? 1.0 : fontSize / font.lineHeight();
        if (scale == 1.0) return layout(font, text, maxWidth, wrap, truncation);
        TextLayout base = layout(font, text, maxWidth <= 0 ? 0 : (int) Math.floor(maxWidth / scale), wrap, truncation);
        return new TextLayout(
                (int) Math.ceil(base.width() * scale),
                (int) Math.ceil(base.height() * scale),
                base.lines(),
                base.truncated(),
                (int) Math.ceil(base.lineHeight() * scale),
                (int) Math.round(base.ascent() * scale));
    }

    private static List<String> paragraphs(String text) {
        String normalized = text == null ? "" : text;
        List<String> paragraphs = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= normalized.length(); i++) {
            if (i == normalized.length() || normalized.charAt(i) == '\n') {
                if (i > start) paragraphs.add(normalized.substring(start, i));
                start = i + 1;
            }
        }
        // An entirely empty input still yields one empty line so callers always have a line box.
        if (paragraphs.isEmpty()) paragraphs.add("");
        return paragraphs;
    }

    private static void appendWrapped(FontAdapter font, String paragraph, int limit, List<String> lines) {
        String[] words = paragraph.trim().split("\\s+");
        if (words.length == 0 || (words.length == 1 && words[0].isEmpty())) {
            lines.add("");
            return;
        }
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (current.length() > 0) {
                String candidate = current + " " + word;
                if (font.stringWidth(candidate) <= limit) {
                    current = new StringBuilder(candidate);
                    continue;
                }
                lines.add(current.toString());
                current = new StringBuilder();
            }
            // A word wider than the limit is broken by characters; a single char may
            // still overflow the limit, which is reported through the layout width.
            while (font.stringWidth(word) > limit && word.length() > 1) {
                int fit = 1;
                while (fit + 1 <= word.length() && font.stringWidth(word.substring(0, fit + 1)) <= limit) fit++;
                lines.add(word.substring(0, fit));
                word = word.substring(fit);
            }
            current.append(word);
        }
        if (current.length() > 0) lines.add(current.toString());
    }

    private static String fitWithEllipsis(FontAdapter font, String line, int limit) {
        if (font.stringWidth(line) <= limit) return line;
        String ellipsis = "...";
        String base = line;
        while (base.length() > 0
                && font.stringWidth(base + ellipsis) > limit) {
            base = base.substring(0, base.length() - 1);
        }
        return base + ellipsis;
    }
}
