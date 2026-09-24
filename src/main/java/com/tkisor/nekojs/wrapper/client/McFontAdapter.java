package com.tkisor.nekojs.wrapper.client;

import com.tkisor.nekojs.api.ui.FontAdapter;
import net.minecraft.client.gui.Font;

/**
 * {@link FontAdapter} over the client's Minecraft font: the single host measurement
 * source shared by the JSX UI host painter and {@code PainterJS}, so both agree with
 * the shared {@code TextLayouter} algorithm.
 */
public final class McFontAdapter implements FontAdapter {
    private final Font font;

    public McFontAdapter(Font font) {
        this.font = font;
    }

    @Override
    public int stringWidth(String text) {
        return text == null || text.isEmpty() ? 0 : font.width(text);
    }

    @Override
    public int lineHeight() {
        return font.lineHeight;
    }

    // ponytail: ascent approximated as lineHeight - 2 (vanilla latin baseline is 7 of 9);
    // derive from the font set when per-font baseline accuracy matters.
    @Override
    public int ascent() {
        return font.lineHeight - 2;
    }
}
