package com.tkisor.nekojs.api.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public sealed interface TextValue permits TextValue.Literal, TextValue.Translatable, TextValue.Keybind, TextValue.Score, TextValue.Selector, TextValue.Sequence, TextValue.Styled {
    static TextValue literal(String text) {
        return new Literal(Objects.requireNonNull(text, "text"));
    }

    static TextValue empty() {
        return new Literal("");
    }

    static TextValue translatable(String key, List<TextArgument> arguments) {
        return new Translatable(key, null, arguments);
    }

    /** 可翻译文本带 fallback：翻译键缺失时显示 fallback（null 表示无 fallback）。 */
    static TextValue translatable(String key, String fallback, List<TextArgument> arguments) {
        return new Translatable(key, fallback, arguments);
    }

    static TextValue keybind(String keybind) {
        return new Keybind(Objects.requireNonNull(keybind, "keybind"));
    }

    static TextValue score(String name, String objective) {
        return new Score(Objects.requireNonNull(name, "name"), Objects.requireNonNull(objective, "objective"));
    }

    static TextValue selector(String pattern) {
        return new Selector(Objects.requireNonNull(pattern, "pattern"));
    }

    static TextValue sequence(List<TextValue> values) {
        Objects.requireNonNull(values, "values");
        List<TextValue> flattened = new ArrayList<>();
        for (TextValue value : values) {
            Objects.requireNonNull(value, "text value");
            if (value instanceof Sequence sequence) {
                flattened.addAll(sequence.values());
            } else if (!value.isEmpty()) {
                flattened.add(value);
            }
        }
        if (flattened.isEmpty()) return empty();
        if (flattened.size() == 1) return flattened.getFirst();
        return new Sequence(flattened);
    }

    /** 给 {@code value} 套上一层 {@link TextStyle}。 */
    static TextValue styled(TextValue value, TextStyle style) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(style, "style");
        if (style.isEmpty()) {
            return value;
        }
        // 透传空值；保留 Styled 包装即使内部为空，以便样式信息（如纯点击事件）不丢失
        return new Styled(value, style);
    }

    boolean isEmpty();

    record Literal(String text) implements TextValue {
        public Literal {
            Objects.requireNonNull(text, "text");
        }

        @Override
        public boolean isEmpty() {
            return text.isEmpty();
        }
    }

    record Translatable(String key, String fallback, List<TextArgument> arguments) implements TextValue {
        public Translatable {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("translation key must not be blank");
            }
            arguments = List.copyOf(arguments == null ? List.of() : arguments);
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    /** 按键绑定（如 {@code "key.attack"}），渲染时解析为玩家当前的按键名。 */
    record Keybind(String keybind) implements TextValue {
        @Override
        public boolean isEmpty() {
            return keybind.isEmpty();
        }
    }

    /** 记分板分数：{@code name} 是持有者名（可填 {@code "*"} 取触发者），{@code objective} 是目标名。 */
    record Score(String name, String objective) implements TextValue {
        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    /** 实体选择器（如 {@code "@p"}、{@code "@a[type=zombie]"}）。 */
    record Selector(String pattern) implements TextValue {
        @Override
        public boolean isEmpty() {
            return pattern.isEmpty();
        }
    }

    record Sequence(List<TextValue> values) implements TextValue {
        public Sequence {
            values = List.copyOf(values);
            if (values.size() < 2) {
                throw new IllegalArgumentException("sequence requires at least two values");
            }
            if (values.stream().anyMatch(value -> value == null || value.isEmpty() || value instanceof Sequence)) {
                throw new IllegalArgumentException("sequence children must be non-empty canonical values");
            }
        }

        @Override
        public boolean isEmpty() {
            return values.stream().allMatch(TextValue::isEmpty);
        }
    }

    /**
     * 带 {@link TextStyle} 的文本（粗体/颜色/点击事件等）。包装一个基础 {@link TextValue}
     * （字面量 / 可翻译 / 序列）。序列化到 MC 组件时，把样式应用到对应的组件上。
     */
    record Styled(TextValue value, TextStyle style) implements TextValue {
        public Styled {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(style, "style");
        }

        @Override
        public boolean isEmpty() {
            return value.isEmpty();
        }
    }
}
