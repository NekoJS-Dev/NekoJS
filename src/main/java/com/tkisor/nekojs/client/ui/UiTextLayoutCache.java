//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import com.tkisor.nekojs.api.ui.FontAdapter;
import com.tkisor.nekojs.api.ui.TextLayout;
import com.tkisor.nekojs.api.ui.TextLayouter;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Owner-thread text layouts retained by one UI host, bounded by entries and UTF-16 content size. */
final class UiTextLayoutCache {
    private static final int MAX_ENTRIES = 256;
    private static final int MAX_CHARACTERS = 1024 * 1024;
    private final LinkedHashMap<Key, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long revision = Long.MIN_VALUE;
    private int characters;

    TextLayout layout(FontAdapter font, String fontId, String text, double fontSize, int width,
                      boolean wrap, TextLayouter.Truncation truncation, long resourceRevision) {
        if (revision != resourceRevision) {
            clear();
            revision = resourceRevision;
        }
        String value = text == null ? "" : text;
        double size = fontSize <= 0 ? font.lineHeight() : fontSize;
        Key key = new Key(fontId, value, size, width, wrap, truncation);
        Entry previous = entries.get(key);
        if (previous != null) return previous.layout();
        TextLayout result = TextLayouter.layoutScaled(new MeasuringFont(font), value, size, width, wrap, truncation);
        long weight = value.length();
        for (String line : result.lines()) weight += line.length();
        if (weight > MAX_CHARACTERS) return result;
        while (!entries.isEmpty() && (entries.size() >= MAX_ENTRIES || characters + weight > MAX_CHARACTERS)) {
            characters -= entries.pollFirstEntry().getValue().characters();
        }
        entries.put(key, new Entry(result, (int) weight));
        characters += (int) weight;
        return result;
    }

    void clear() {
        entries.clear();
        characters = 0;
        revision = Long.MIN_VALUE;
    }

    private record Key(String font, String text, double size, int width, boolean wrap,
                       TextLayouter.Truncation truncation) { }

    private record Entry(TextLayout layout, int characters) { }

    /** Repeated stack paths share prefix measurements only while one layout is being constructed. */
    private static final class MeasuringFont implements FontAdapter {
        private static final int MAX_PREFIX_LENGTH = 256;
        private static final int MAX_PREFIX_MEASUREMENTS = 2048;
        private final FontAdapter source;
        private final Map<String, Integer> widths = new HashMap<>();

        private MeasuringFont(FontAdapter source) { this.source = source; }

        @Override
        public int stringWidth(String text) {
            if (text.length() > MAX_PREFIX_LENGTH) return source.stringWidth(text);
            Integer cached = widths.get(text);
            if (cached != null) return cached;
            int width = source.stringWidth(text);
            if (widths.size() < MAX_PREFIX_MEASUREMENTS) widths.put(text, width);
            return width;
        }

        @Override
        public int lineHeight() { return source.lineHeight(); }

        @Override
        public int ascent() { return source.ascent(); }
    }
}
//?}
