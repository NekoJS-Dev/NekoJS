package com.tkisor.nekojs.core.api.facade;

import com.tkisor.nekojs.api.data.TextValue;
import com.tkisor.nekojs.api.data.TextArgument;
import com.tkisor.nekojs.api.error.ApiErrorCodes;
import com.tkisor.nekojs.api.error.ApiInvocationException;
import com.tkisor.nekojs.api.facade.TextFacade;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DefaultTextFacade implements TextFacade {
    @Override
    public TextValue of(String text) {
        return TextValue.literal(text);
    }

    @Override
    public TextValue empty() {
        return TextValue.empty();
    }

    @Override
    public TextValue translatable(String key, List<Object> arguments) {
        return TextValue.translatable(key, normalizeArguments(arguments));
    }

    @Override
    public TextValue ofValues(List<Object> values) {
        return TextValue.sequence(normalize(values));
    }

    @Override
    public TextValue append(TextValue receiver, List<Object> values) {
        List<TextValue> combined = new ArrayList<>();
        combined.add(receiver);
        combined.addAll(normalize(values));
        return TextValue.sequence(combined);
    }

    private static List<TextValue> normalize(List<Object> values) {
        List<TextValue> normalized = new ArrayList<>();
        for (Object value : values) {
            TextArgument argument = normalizeArgument(value);
            normalized.add(argument instanceof TextArgument.NestedText nested
                    ? nested.value()
                    : TextValue.literal(argument.displayString()));
        }
        return List.copyOf(normalized);
    }

    private static List<TextArgument> normalizeArguments(List<Object> values) {
        return values.stream().map(DefaultTextFacade::normalizeArgument).toList();
    }

    private static TextArgument normalizeArgument(Object value) {
        if (value instanceof TextValue text) return new TextArgument.NestedText(text);
        if (value instanceof String string) return new TextArgument.StringValue(string);
        if (value instanceof Boolean bool) return new TextArgument.BooleanValue(bool);
        if (value instanceof Number number && !isNonFiniteNumber(number)) {
            return new TextArgument.NumberValue(number);
        }
        throw invalidValue(value);
    }

    private static boolean isNonFiniteNumber(Object value) {
        if (value instanceof Double) {
            return !Double.isFinite((Double) value);
        }
        if (value instanceof Float) {
            return !Float.isFinite((Float) value);
        }
        return false;
    }

    private static ApiInvocationException invalidValue(Object value) {
        return new ApiInvocationException(
                ApiErrorCodes.TYPE_MISMATCH,
                "Text argument must be a string, finite number, boolean, or TextValue",
                Map.of("actualType", value == null ? "null" : value.getClass().getName()));
    }
}
