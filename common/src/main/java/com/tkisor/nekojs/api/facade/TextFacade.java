package com.tkisor.nekojs.api.facade;

import com.tkisor.nekojs.api.data.TextValue;

import java.util.List;

/**
 * Text facade, exposed to scripts as the global object {@code Text}.
 *
 * <p>Builds and styles immutable {@link TextValue} trees. All factory methods return
 * new values; style methods never mutate the receiver but return a styled copy, so
 * calls can be chained. List arguments accept mixed {@code String} and
 * {@link TextValue} elements.
 */
public interface TextFacade {
    /** Creates a literal text value from the given string. */
    TextValue of(String text);

    /** Creates an empty text value (renders as nothing). */
    TextValue empty();

    /** Creates a translatable text for {@code key}; {@code arguments} may be empty but never {@code null}. */
    TextValue translatable(String key, List<Object> arguments);

    /** 可翻译文本带 fallback：翻译键缺失时显示 fallback 字面量。 */
    TextValue translateWithFallback(String key, String fallback, List<Object> arguments);

    /** 按键绑定文本（如 {@code "key.attack"} → 渲染为玩家当前攻击键）。 */
    TextValue keybind(String keybind);

    /** 记分板分数：{@code name} 可填 {@code "*"}（取触发者），{@code objective} 是计分项名。 */
    TextValue score(String name, String objective);

    /** 实体选择器文本（如 {@code "@p"}、{@code "@a[type=zombie]"}）。 */
    TextValue selector(String pattern);

    /** Joins the given values (String or {@link TextValue} elements) into one compound text. */
    TextValue ofValues(List<Object> values);

    /** Returns a copy of {@code receiver} with the given values (String or {@link TextValue}) appended as siblings. */
    TextValue append(TextValue receiver, List<Object> values);

    /** 用分隔符 {@code separator} 拼接多个文本值（{@code values} 元素为 String 或 TextValue）。 */
    TextValue join(TextValue separator, List<Object> values);

    // —— 富文本样式（链式合并，每次返回带样式的 TextValue）——
    /** Returns a copy with bold set to {@code value}; other styles of the receiver are kept. */
    TextValue bold(TextValue receiver, boolean value);

    /** Returns a copy with italic set to {@code value}; other styles of the receiver are kept. */
    TextValue italic(TextValue receiver, boolean value);

    /** Returns a copy with underline set to {@code value}; other styles of the receiver are kept. */
    TextValue underlined(TextValue receiver, boolean value);

    /** Returns a copy with strikethrough set to {@code value}; other styles of the receiver are kept. */
    TextValue strikethrough(TextValue receiver, boolean value);

    /** Returns a copy with the obfuscation effect set to {@code value}; other styles of the receiver are kept. */
    TextValue obfuscated(TextValue receiver, boolean value);

    /** Returns a copy colored with the named or hexadecimal ({@code #RRGGBB}) color; blank colors are rejected. */
    TextValue color(TextValue receiver, String color);

    // —— 16 色 KubeJS 风格快捷方法（等价于 .color('red') 等）——
    /** Shorthand for {@code color(receiver, "black")}. */
    TextValue black(TextValue receiver);

    /** Shorthand for {@code color(receiver, "dark_blue")}. */
    TextValue darkBlue(TextValue receiver);

    /** Shorthand for {@code color(receiver, "dark_green")}. */
    TextValue darkGreen(TextValue receiver);

    /** Shorthand for {@code color(receiver, "dark_aqua")}. */
    TextValue darkAqua(TextValue receiver);

    /** Shorthand for {@code color(receiver, "dark_red")}. */
    TextValue darkRed(TextValue receiver);

    /** Shorthand for {@code color(receiver, "dark_purple")}. */
    TextValue darkPurple(TextValue receiver);

    /** Shorthand for {@code color(receiver, "gold")}. */
    TextValue gold(TextValue receiver);

    /** Shorthand for {@code color(receiver, "gray")}. */
    TextValue gray(TextValue receiver);

    /** Shorthand for {@code color(receiver, "dark_gray")}. */
    TextValue darkGray(TextValue receiver);

    /** Shorthand for {@code color(receiver, "blue")}. */
    TextValue blue(TextValue receiver);

    /** Shorthand for {@code color(receiver, "green")}. */
    TextValue green(TextValue receiver);

    /** Shorthand for {@code color(receiver, "aqua")}. */
    TextValue aqua(TextValue receiver);

    /** Shorthand for {@code color(receiver, "red")}. */
    TextValue red(TextValue receiver);

    /** Shorthand for {@code color(receiver, "light_purple")}. */
    TextValue lightPurple(TextValue receiver);

    /** Shorthand for {@code color(receiver, "yellow")}. */
    TextValue yellow(TextValue receiver);

    /** Shorthand for {@code color(receiver, "white")}. */
    TextValue white(TextValue receiver);

    /** Returns a copy where shift-clicking the text inserts {@code insertion} into the chat box. */
    TextValue insertion(TextValue receiver, String insertion);

    /** Returns a copy rendered with the given font id (e.g. {@code "minecraft:default"}). */
    TextValue font(TextValue receiver, String font);

    /** 点击事件：{@code action} ∈ runCommand/suggestCommand/openUrl/openFile/copyToClipboard/changePage。 */
    TextValue click(TextValue receiver, String action, String value);

    /** 悬停显示文本（{@code text} 须为 TextValue）。 */
    TextValue hover(TextValue receiver, TextValue text);
}
