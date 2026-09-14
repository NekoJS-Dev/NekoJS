//? if neoforge {
// 本文件只在 26.2.0 节点编译（stonecutter >=26 守卫 + 节点本地源根）：NeoForge 26.2 的
// Screen 投影是版本特有形状（Gui#screen/setScreen、KeyEvent/CharacterEvent/MouseButtonEvent、
// GuiGraphicsExtractor），不放共享树以免污染 1.21.1。
package com.tkisor.nekojs.client.gui;

import com.tkisor.nekojs.api.ui.UiEventType;
import com.tkisor.nekojs.api.ui.UiHostAdapter;
import com.tkisor.nekojs.api.ui.UiPrimitive;
import com.tkisor.nekojs.api.ui.UiProp;
import com.tkisor.nekojs.api.ui.UiProps;
import com.tkisor.nekojs.api.ui.UiRect;
import com.tkisor.nekojs.api.ui.UiSize;
import com.tkisor.nekojs.api.ui.UiTextMeasure;
import com.tkisor.nekojs.api.ui.UiTextMeasurer;

import java.util.EnumSet;
import java.util.Set;

/**
 * NeoForge 26.2 的文本测量 Adapter（jsx-ui-spec「文本与资源」）：
 * label 的文本测量与换行经原生 {@code Font} 完成，common 侧只消费逻辑像素尺寸。
 *
 * <p>实现不持有 Minecraft 对象之外的状态，也不跨帧缓存测量结果——测量只发生在
 * root 的 measure 阶段（jsx-ui-spec「性能边界」）。
 */
public final class Nf262UiTextMeasurer implements UiTextMeasurer {

    private final net.minecraft.client.gui.Font font;

    public Nf262UiTextMeasurer(net.minecraft.client.gui.Font font) {
        this.font = font;
    }

    @Override
    public UiSize measure(UiTextMeasure request) {
        if (font == null) {
            return UiSize.zero();
        }
        String text = request.text() == null ? "" : request.text();
        double scale = request.fontSize() / 8.0;
        if (scale <= 0) {
            scale = 1.0;
        }
        double width = font.width(text) * scale;
        double height = font.lineHeight * scale;
        if (request.wrap() && request.availableWidth() > 0 && width > request.availableWidth()) {
            // 换行高度用原生 split 的真实行数，不用字符数估算。
            int lines = font.split(net.minecraft.network.chat.FormattedText.of(text),
                    Math.max(1, (int) Math.floor(request.availableWidth() / scale))).size();
            return new UiSize(request.availableWidth(), Math.max(height, lines * height));
        }
        return new UiSize(width, height);
    }
}
