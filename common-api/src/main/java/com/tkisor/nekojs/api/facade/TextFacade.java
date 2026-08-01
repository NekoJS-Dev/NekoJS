package com.tkisor.nekojs.api.facade;

import com.tkisor.nekojs.api.data.TextValue;

import java.util.List;

public interface TextFacade {
    TextValue of(String text);

    TextValue empty();

    TextValue translatable(String key, List<Object> arguments);

    TextValue ofValues(List<Object> values);

    TextValue append(TextValue receiver, List<Object> values);

    // —— 富文本样式（链式合并，每次返回带样式的 TextValue）——
    TextValue bold(TextValue receiver, boolean value);

    TextValue italic(TextValue receiver, boolean value);

    TextValue underlined(TextValue receiver, boolean value);

    TextValue strikethrough(TextValue receiver, boolean value);

    TextValue obfuscated(TextValue receiver, boolean value);

    TextValue color(TextValue receiver, String color);

    TextValue insertion(TextValue receiver, String insertion);

    TextValue font(TextValue receiver, String font);

    /** 点击事件：{@code action} ∈ runCommand/suggestCommand/openUrl/openFile/copyToClipboard/changePage。 */
    TextValue click(TextValue receiver, String action, String value);

    /** 悬停显示文本（{@code text} 须为 TextValue）。 */
    TextValue hover(TextValue receiver, TextValue text);
}
