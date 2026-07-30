package com.tkisor.nekojs.api.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public sealed interface TextValue permits TextValue.Literal, TextValue.Translatable, TextValue.Sequence {
    static TextValue literal(String text) {
        return new Literal(Objects.requireNonNull(text, "text"));
    }

    static TextValue empty() {
        return new Literal("");
    }

    static TextValue translatable(String key, List<TextArgument> arguments) {
        return new Translatable(key, arguments);
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

    record Translatable(String key, List<TextArgument> arguments) implements TextValue {
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
}
