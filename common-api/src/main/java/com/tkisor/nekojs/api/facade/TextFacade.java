package com.tkisor.nekojs.api.facade;

import com.tkisor.nekojs.api.data.TextValue;

import java.util.List;

public interface TextFacade {
    TextValue of(String text);

    TextValue empty();

    TextValue translatable(String key, List<Object> arguments);

    /** 可翻译文本带 fallback：翻译键缺失时显示 fallback 字面量。 */
    TextValue translateWithFallback(String key, String fallback, List<Object> arguments);

    /** 按键绑定文本（如 {@code "key.attack"} → 渲染为玩家当前攻击键）。 */
    TextValue keybind(String keybind);

    /** 记分板分数：{@code name} 可填 {@code "*"}（取触发者），{@code objective} 是计分项名。 */
    TextValue score(String name, String objective);

    /** 实体选择器文本（如 {@code "@p"}、{@code "@a[type=zombie]"}）。 */
    TextValue selector(String pattern);

    TextValue ofValues(List<Object> values);

    TextValue append(TextValue receiver, List<Object> values);

    /** 用分隔符 {@code separator} 拼接多个文本值（{@code values} 元素为 String 或 TextValue）。 */
    TextValue join(TextValue separator, List<Object> values);

    // —— 富文本样式（链式合并，每次返回带样式的 TextValue）——
    TextValue bold(TextValue receiver, boolean value);

    TextValue italic(TextValue receiver, boolean value);

    TextValue underlined(TextValue receiver, boolean value);

    TextValue strikethrough(TextValue receiver, boolean value);

    TextValue obfuscated(TextValue receiver, boolean value);

    TextValue color(TextValue receiver, String color);

    // —— 16 色 KubeJS 风格快捷方法（等价于 .color('red') 等）——
    TextValue black(TextValue receiver);

    TextValue darkBlue(TextValue receiver);

    TextValue darkGreen(TextValue receiver);

    TextValue darkAqua(TextValue receiver);

    TextValue darkRed(TextValue receiver);

    TextValue darkPurple(TextValue receiver);

    TextValue gold(TextValue receiver);

    TextValue gray(TextValue receiver);

    TextValue darkGray(TextValue receiver);

    TextValue blue(TextValue receiver);

    TextValue green(TextValue receiver);

    TextValue aqua(TextValue receiver);

    TextValue red(TextValue receiver);

    TextValue lightPurple(TextValue receiver);

    TextValue yellow(TextValue receiver);

    TextValue white(TextValue receiver);

    TextValue insertion(TextValue receiver, String insertion);

    TextValue font(TextValue receiver, String font);

    /** 点击事件：{@code action} ∈ runCommand/suggestCommand/openUrl/openFile/copyToClipboard/changePage。 */
    TextValue click(TextValue receiver, String action, String value);

    /** 悬停显示文本（{@code text} 须为 TextValue）。 */
    TextValue hover(TextValue receiver, TextValue text);
}
