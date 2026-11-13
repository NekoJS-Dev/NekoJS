package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.annotation.ContractReceiver;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@ContractReceiver
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

    /** A translatable text with a fallback: when the translation key is missing, the fallback is
     * shown. A {@code null} fallback means there is none. */
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

    /** Wraps {@code value} in a {@link TextStyle}. */
    static TextValue styled(TextValue value, TextStyle style) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(style, "style");
        if (style.isEmpty()) {
            return value;
        }
        // Pass empty values through, but keep the Styled wrapper even when the inner value is empty,
        // so style information such as a click event alone is not lost
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

    /** A key binding such as {@code "key.attack"}. It resolves to the player's current key name when
     * rendered. */
    record Keybind(String keybind) implements TextValue {
        @Override
        public boolean isEmpty() {
            return keybind.isEmpty();
        }
    }

    /** A scoreboard score. {@code name} is the holder name ({@code "*"} means the triggering
     * entity) and {@code objective} is the objective name. */
    record Score(String name, String objective) implements TextValue {
        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    /** An entity selector such as {@code "@p"} or {@code "@a[type=zombie]"}. */
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
     * Text with a {@link TextStyle} (bold, color, click events and so on). It wraps a base
     * {@link TextValue} (literal, translatable or sequence). When it is serialized to an MC
     * component, the style is applied to that component.
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
