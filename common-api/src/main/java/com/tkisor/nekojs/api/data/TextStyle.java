package com.tkisor.nekojs.api.data;

import java.util.Objects;

/**
 * 富文本样式（跨平台、MC 版本无关）。描述字体修饰、颜色、插入文本与点击/悬停事件。
 *
 * <p>所有字段为 {@code null} 表示「未指定」（沿用父组件或默认）；颜色用字符串表达
 * （命名色 {@code 'red'} 或 hex {@code '#FF0000'}），由各平台 adapter 解析为本地颜色类型。
 *
 * <p>不可变。通过 {@link #toBuilder()} 派生。
 */
public final class TextStyle {
    private final Boolean bold;
    private final Boolean italic;
    private final Boolean underlined;
    private final Boolean strikethrough;
    private final Boolean obfuscated;
    private final String color;
    private final String insertion;
    private final String font;
    private final TextClickEvent clickEvent;
    private final TextHoverEvent hoverEvent;

    public TextStyle(Boolean bold, Boolean italic, Boolean underlined, Boolean strikethrough,
                     Boolean obfuscated, String color, String insertion, String font,
                     TextClickEvent clickEvent, TextHoverEvent hoverEvent) {
        this.bold = bold;
        this.italic = italic;
        this.underlined = underlined;
        this.strikethrough = strikethrough;
        this.obfuscated = obfuscated;
        this.color = color;
        this.insertion = insertion;
        this.font = font;
        this.clickEvent = clickEvent;
        this.hoverEvent = hoverEvent;
    }

    public static TextStyle empty() {
        return new TextStyle(null, null, null, null, null, null, null, null, null, null);
    }

    public Boolean bold() { return bold; }
    public Boolean italic() { return italic; }
    public Boolean underlined() { return underlined; }
    public Boolean strikethrough() { return strikethrough; }
    public Boolean obfuscated() { return obfuscated; }
    public String color() { return color; }
    public String insertion() { return insertion; }
    public String font() { return font; }
    public TextClickEvent clickEvent() { return clickEvent; }
    public TextHoverEvent hoverEvent() { return hoverEvent; }

    /** 是否没有任何样式（可跳过样式应用）。 */
    public boolean isEmpty() {
        return bold == null && italic == null && underlined == null && strikethrough == null
                && obfuscated == null && color == null && insertion == null && font == null
                && clickEvent == null && hoverEvent == null;
    }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.bold = bold; b.italic = italic; b.underlined = underlined; b.strikethrough = strikethrough;
        b.obfuscated = obfuscated; b.color = color; b.insertion = insertion; b.font = font;
        b.clickEvent = clickEvent; b.hoverEvent = hoverEvent;
        return b;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TextStyle other)) return false;
        return Objects.equals(bold, other.bold) && Objects.equals(italic, other.italic)
                && Objects.equals(underlined, other.underlined) && Objects.equals(strikethrough, other.strikethrough)
                && Objects.equals(obfuscated, other.obfuscated) && Objects.equals(color, other.color)
                && Objects.equals(insertion, other.insertion) && Objects.equals(font, other.font)
                && Objects.equals(clickEvent, other.clickEvent) && Objects.equals(hoverEvent, other.hoverEvent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bold, italic, underlined, strikethrough, obfuscated, color, insertion, font, clickEvent, hoverEvent);
    }

    /** 可变构建器；链式调用后用 {@link #build()} 产出不可变 {@link TextStyle}。 */
    public static final class Builder {
        private Boolean bold;
        private Boolean italic;
        private Boolean underlined;
        private Boolean strikethrough;
        private Boolean obfuscated;
        private String color;
        private String insertion;
        private String font;
        private TextClickEvent clickEvent;
        private TextHoverEvent hoverEvent;

        public Builder bold(boolean v) { this.bold = v; return this; }
        public Builder italic(boolean v) { this.italic = v; return this; }
        public Builder underlined(boolean v) { this.underlined = v; return this; }
        public Builder strikethrough(boolean v) { this.strikethrough = v; return this; }
        public Builder obfuscated(boolean v) { this.obfuscated = v; return this; }
        public Builder color(String color) { this.color = color; return this; }
        public Builder insertion(String insertion) { this.insertion = insertion; return this; }
        public Builder font(String font) { this.font = font; return this; }
        public Builder click(TextClickEvent clickEvent) { this.clickEvent = clickEvent; return this; }
        public Builder hover(TextHoverEvent hoverEvent) { this.hoverEvent = hoverEvent; return this; }

        public TextStyle build() {
            return new TextStyle(bold, italic, underlined, strikethrough, obfuscated, color, insertion, font, clickEvent, hoverEvent);
        }
    }
}
