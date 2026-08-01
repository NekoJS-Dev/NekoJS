package com.tkisor.nekojs.core.api.facade;

import com.tkisor.nekojs.api.data.TextValue;
import com.tkisor.nekojs.api.data.TextArgument;
import com.tkisor.nekojs.api.data.TextStyle;
import com.tkisor.nekojs.api.data.TextClickEvent;
import com.tkisor.nekojs.api.data.TextHoverEvent;
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

    // —— 富文本样式：链式合并 ——
    // 若 receiver 已是 Styled，则在其 style 基础上派生；否则套用新空样式。

    @Override
    public TextValue bold(TextValue receiver, boolean value) {
        return applyStyle(receiver, b -> b.bold(value));
    }

    @Override
    public TextValue italic(TextValue receiver, boolean value) {
        return applyStyle(receiver, b -> b.italic(value));
    }

    @Override
    public TextValue underlined(TextValue receiver, boolean value) {
        return applyStyle(receiver, b -> b.underlined(value));
    }

    @Override
    public TextValue strikethrough(TextValue receiver, boolean value) {
        return applyStyle(receiver, b -> b.strikethrough(value));
    }

    @Override
    public TextValue obfuscated(TextValue receiver, boolean value) {
        return applyStyle(receiver, b -> b.obfuscated(value));
    }

    @Override
    public TextValue color(TextValue receiver, String color) {
        if (color == null || color.isBlank()) {
            throw new ApiInvocationException(
                    ApiErrorCodes.TYPE_MISMATCH,
                    "color must be a non-blank string (named color or #RRGGBB)",
                    Map.of());
        }
        return applyStyle(receiver, b -> b.color(color));
    }

    @Override
    public TextValue insertion(TextValue receiver, String insertion) {
        return applyStyle(receiver, b -> b.insertion(insertion));
    }

    @Override
    public TextValue font(TextValue receiver, String font) {
        return applyStyle(receiver, b -> b.font(font));
    }

    @Override
    public TextValue click(TextValue receiver, String action, String value) {
        TextClickEvent click = buildClickEvent(action, value);
        return applyStyle(receiver, b -> b.click(click));
    }

    @Override
    public TextValue hover(TextValue receiver, TextValue text) {
        return applyStyle(receiver, b -> b.hover(new TextHoverEvent.ShowText(text)));
    }

    private static TextValue applyStyle(TextValue receiver, java.util.function.Consumer<TextStyle.Builder> mutator) {
        TextValue inner = receiver;
        TextStyle.Builder builder;
        if (receiver instanceof TextValue.Styled styled) {
            inner = styled.value();
            builder = styled.style().toBuilder();
        } else {
            builder = TextStyle.empty().toBuilder();
        }
        mutator.accept(builder);
        return TextValue.styled(inner, builder.build());
    }

    private static TextClickEvent buildClickEvent(String action, String value) {
        if (action == null || value == null) {
            throw new ApiInvocationException(
                    ApiErrorCodes.TYPE_MISMATCH,
                    "click event requires non-null action and value",
                    Map.of());
        }
        return switch (action) {
            case "runCommand" -> new TextClickEvent.RunCommand(value);
            case "suggestCommand" -> new TextClickEvent.SuggestCommand(value);
            case "openUrl" -> new TextClickEvent.OpenUrl(value);
            case "openFile" -> new TextClickEvent.OpenFile(value);
            case "copyToClipboard" -> new TextClickEvent.CopyToClipboard(value);
            case "changePage" -> {
                int page;
                try {
                    page = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw new ApiInvocationException(
                            ApiErrorCodes.TYPE_MISMATCH,
                            "changePage value must be an integer string",
                            Map.of("value", value));
                }
                yield new TextClickEvent.ChangePage(page);
            }
            default -> throw new ApiInvocationException(
                    ApiErrorCodes.TYPE_MISMATCH,
                    "unknown click action: " + action,
                    Map.of("action", action));
        };
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
