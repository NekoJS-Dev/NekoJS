package com.tkisor.nekojs.wrapper.client;

import com.tkisor.nekojs.api.ui.FontAdapter;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * {@link FontAdapter} over the client's Minecraft font: the single host measurement
 * source shared by the JSX UI host painter and {@code PainterJS}, so both agree with
 * the shared {@code TextLayouter} algorithm.
 */
public final class McFontAdapter implements FontAdapter {
    private final Font font;
    private final Style style;

    public McFontAdapter(Font font) {
        this(font, Style.EMPTY);
    }

    public McFontAdapter(Font font, Style style) {
        this.font = font;
        this.style = style;
    }

    public Component text(String value) {
        return Component.literal(value).setStyle(style);
    }

    @Override
    public int stringWidth(String text) {
        if (text == null || text.isEmpty()) return 0;
        return style.isEmpty() ? font.width(text) : font.width(text(text));
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
