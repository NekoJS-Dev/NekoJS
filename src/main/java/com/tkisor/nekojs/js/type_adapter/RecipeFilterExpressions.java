package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.data.ValueConversionException;
import com.tkisor.nekojs.api.recipe.RecipeFilter;
import com.tkisor.nekojs.api.recipe.RecipeMatchContext;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import graal.graalvm.polyglot.Value;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Shared expression parsing for native recipe filters; guest values remain owned by their caller generation. */
public final class RecipeFilterExpressions {
    private RecipeFilterExpressions() {}

    public static boolean isExpression(Value value) {
        return value != null && !value.isNull() && (nativeRegExp(value)
                || value.isHostObject() && value.asHostObject() instanceof Pattern
                || value.isString() && value.asString().startsWith("/"));
    }

    public static RecipeFilter forId(Value value) {
        if (value != null && value.isString() && !isExpression(value)) {
            String id = value.asString();
            if (id.equals("*")) return new RecipeFilter.And(List.of());
            if (id.equals("-")) return new RecipeFilter.Or(List.of());
            return new RecipeFilter.ById(id);
        }
        Predicate<String> expression = stringPredicate(value);
        return (holder, registries) -> expression.test(RecipeEventJS.recipeHolderId(holder).toString());
    }

    public static RecipeFilter forMod(Value value) {
        if (value != null && value.isString() && !isExpression(value)) return new RecipeFilter.ByMod(value.asString());
        Predicate<String> expression = stringPredicate(value);
        return (holder, registries) -> expression.test(RecipeEventJS.recipeHolderId(holder).getNamespace());
    }

    public static RecipeFilter forGroup(Value value) {
        if (value != null && value.isString() && !isExpression(value)) return new RecipeFilter.ByGroup(value.asString());
        Predicate<String> expression = stringPredicate(value);
        return (holder, registries) -> expression.test(RecipeEventJS.recipeGroup(holder.value()));
    }

    public static RecipeFilter contextualCallback(Value callback) {
        if (callback == null || !callback.canExecute()) throw invalid(callback, "Expected a recipe-context callback", null);
        return (holder, registries) -> {
            Value result = callback.execute(new RecipeMatchContext(holder));
            if (!result.isBoolean()) throw invalid(result, "Recipe filter callbacks must return a boolean", null);
            return result.asBoolean();
        };
    }

    public static Predicate<String> stringPredicate(Value value) {
        if (value == null || value.isNull()) throw invalid(value, "Expected a string or regular expression", null);
        if (nativeRegExp(value)) {
            return text -> {
                Value previous = value.getMember("lastIndex");
                try {
                    value.putMember("lastIndex", 0);
                    Value result = value.invokeMember("test", text);
                    if (!result.isBoolean()) throw invalid(result, "RegExp.test must return a boolean", null);
                    return result.asBoolean();
                } finally {
                    value.putMember("lastIndex", previous);
                }
            };
        }
        if (value.isHostObject() && value.asHostObject() instanceof Pattern pattern) {
            return text -> pattern.matcher(text).find();
        }
        if (!value.isString()) throw invalid(value, "Expected a string or regular expression", null);
        String text = value.asString();
        if (!text.startsWith("/")) return text::equals;
        try {
            int end = text.lastIndexOf('/');
            if (end == 0) throw new IllegalArgumentException("Unclosed slash regular expression");
            int flags = 0;
            for (char flag : text.substring(end + 1).toCharArray()) {
                flags |= switch (flag) {
                    case 'd' -> Pattern.UNIX_LINES;
                    case 'i' -> Pattern.CASE_INSENSITIVE;
                    case 'x' -> Pattern.COMMENTS;
                    case 'm' -> Pattern.MULTILINE;
                    case 's' -> Pattern.DOTALL;
                    case 'u' -> Pattern.UNICODE_CASE;
                    case 'U' -> Pattern.UNICODE_CHARACTER_CLASS;
                    default -> throw new IllegalArgumentException("Unsupported slash regular-expression flag: " + flag);
                };
            }
            Pattern pattern = Pattern.compile(text.substring(1, end), flags);
            return candidate -> pattern.matcher(candidate).find();
        } catch (IllegalArgumentException failure) {
            throw invalid(value, "Invalid slash regular expression: " + failure.getMessage(), failure);
        }
    }

    private static boolean nativeRegExp(Value value) {
        if (value == null || value.isNull() || value.isHostObject()) return false;
        Value meta = value.getMetaObject();
        return meta != null && meta.isMetaObject() && meta.getMetaSimpleName().equals("RegExp");
    }

    private static ValueConversionException invalid(Object value, String detail, Throwable cause) {
        Throwable failure = cause == null ? new IllegalArgumentException(detail) : cause;
        return new ValueConversionException(RecipeFilter.class, "recipe filter expression", value,
                "[NEKO-4054] " + detail, failure);
    }
}
