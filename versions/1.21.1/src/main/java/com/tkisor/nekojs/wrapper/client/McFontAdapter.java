// 1.21.1 实现，与版本树 src/ 下的同名 26.x 文件成对。内容等于那份文件在本节点求值后的形态
//（可用 tools/extract_evaluated.py 重新提取核对）；26.x 侧行为变更时须同步本文件。
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
