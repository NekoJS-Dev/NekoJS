package com.tkisor.nekojs.api;

import graal.graalvm.polyglot.Value;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Strict option fields shared by runtime conversion and generated input declarations. */
public final class ScriptOptionSchema implements ScriptInputShapeProvider {
    private enum Kind { STRING, NUMBER, INTEGER, STRING_MAP, CHOICE }

    public record Field(String name, AdapterInputShape shape, java.util.function.Function<Object, Object> validate) {}

    private final Map<String, Field> fields;

    public ScriptOptionSchema(Field... fields) {
        Map<String, Field> indexed = new LinkedHashMap<>();
        for (Field field : fields) {
            if (indexed.put(field.name(), field) != null) throw new IllegalArgumentException("[NEKO-4060] Duplicate option: " + field.name());
        }
        this.fields = java.util.Collections.unmodifiableMap(indexed);
    }

    public static Field string(String name) { return field(name, Kind.STRING, List.of(), 0, 0); }
    public static Field number(String name, double min, double max) { return field(name, Kind.NUMBER, List.of(), min, max); }
    public static Field integer(String name, int min, int max) { return field(name, Kind.INTEGER, List.of(), min, max); }
    public static Field strings(String name) { return field(name, Kind.STRING_MAP, List.of(), 0, 0); }
    public static Field choice(String name, String... values) { return field(name, Kind.CHOICE, List.of(values), 0, 0); }

    private static Field field(String name, Kind kind, List<String> choices, double min, double max) {
        AdapterInputShape shape = switch (kind) {
            case STRING -> AdapterInputShape.string();
            case NUMBER, INTEGER -> AdapterInputShape.number();
            case STRING_MAP -> AdapterInputShape.raw("Record<string, string>");
            case CHOICE -> AdapterInputShape.union(choices.stream().map(AdapterInputShape::literal).toArray(AdapterInputShape[]::new));
        };
        return new Field(name, shape, value -> {
            Object converted = value instanceof Value guest ? fromGuest(name, kind, guest) : value;
            return switch (kind) {
                case STRING -> {
                    if (!(converted instanceof String)) throw invalid(name, "must be a string");
                    yield converted;
                }
                case CHOICE -> {
                    if (!(converted instanceof String text)) throw invalid(name, "must be a layer name");
                    String normalized = text.trim().toLowerCase(Locale.ROOT);
                    if (!choices.contains(normalized)) throw invalid(name, "must be one of " + choices);
                    yield normalized;
                }
                case NUMBER, INTEGER -> {
                    if (!(converted instanceof Number number)) throw invalid(name, "must be a number");
                    double numeric = number.doubleValue();
                    if (!Double.isFinite(numeric) || numeric < min || numeric > max
                            || kind == Kind.INTEGER && numeric != Math.rint(numeric)) {
                        throw invalid(name, "must be " + (kind == Kind.INTEGER ? "an integer" : "finite") + " in [" + min + ", " + max + "]");
                    }
                    if (kind == Kind.INTEGER) yield (int) numeric;
                    yield numeric;
                }
                case STRING_MAP -> {
                    if (!(converted instanceof Map<?, ?> map)) throw invalid(name, "must be a string-to-string object");
                    Map<String, String> copy = new LinkedHashMap<>();
                    for (var entry : map.entrySet()) {
                        if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof String text)) {
                            throw invalid(name, "must contain only string keys and string values");
                        }
                        copy.put(key, text);
                    }
                    yield java.util.Collections.unmodifiableMap(copy);
                }
            };
        });
    }

    private static Object fromGuest(String name, Kind kind, Value value) {
        if (value == null || value.isNull()) throw invalid(name, "must not be null");
        return switch (kind) {
            case STRING, CHOICE -> {
                if (!value.isString()) throw invalid(name, "must be a string");
                yield value.asString();
            }
            case NUMBER, INTEGER -> {
                if (!value.isNumber()) throw invalid(name, "must be a number");
                yield value.asDouble();
            }
            case STRING_MAP -> {
                requireObject(value, name);
                Map<String, String> copy = new LinkedHashMap<>();
                for (String key : value.getMemberKeys()) {
                    Value child = value.getMember(key);
                    if (child == null || !child.isString()) throw invalid(name + "." + key, "must be a string");
                    copy.put(key, child.asString());
                }
                yield copy;
            }
        };
    }

    public Map<String, Object> read(Value value) {
        requireObject(value, "options");
        Map<String, Object> result = new LinkedHashMap<>();
        for (String name : value.getMemberKeys()) result.put(name, readField(name, value.getMember(name)));
        return java.util.Collections.unmodifiableMap(result);
    }

    public Map<String, Object> validate(Map<?, ?> value) {
        if (value == null) throw invalid("options", "must be an object");
        Map<String, Object> result = new LinkedHashMap<>();
        for (var entry : value.entrySet()) {
            if (!(entry.getKey() instanceof String name)) throw invalid("options", "must use string keys");
            result.put(name, validateField(name, entry.getValue()));
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    public Object readField(String name, Value value) { return validateField(name, value); }
    public Object validateField(String name, Object value) {
        Field field = fields.get(name);
        if (field == null) throw invalid(name, "is not a supported option; expected " + fields.keySet());
        return field.validate().apply(value);
    }
    public boolean hasField(String name) { return fields.containsKey(name); }
    public String[] fieldNames() { return fields.keySet().toArray(String[]::new); }
    public AdapterInputShape fieldShape(String name) { return fields.get(name).shape(); }

    @Override
    public AdapterInputShape inputShape() {
        return AdapterInputShape.object(fields.values().stream()
                .map(field -> AdapterInputShape.Slot.opt(field.name(), field.shape())).toList());
    }

    private static void requireObject(Value value, String name) {
        if (value == null || value.isNull() || !value.hasMembers() || value.canExecute() || value.hasArrayElements()) {
            throw invalid(name, "must be an object");
        }
    }
    private static IllegalArgumentException invalid(String name, String reason) {
        return new IllegalArgumentException("[NEKO-4060] Option '" + name + "' " + reason);
    }
}
