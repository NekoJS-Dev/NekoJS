package com.tkisor.nekojs.authoring;

import com.google.gson.*;

import java.util.*;

/** The typed query field catalog; transports and validation consume the same definitions. */
final class AuthoringFields {
    enum Kind { STRING, NUMBER, BOOLEAN, BOUND }
    record Field(Kind kind) {
        Set<String> operators() {
            Set<String> ops = new LinkedHashSet<>(List.of("eq", "ne", "in", "notIn", "hasValue"));
            if (kind == Kind.STRING) ops.add("prefix");
            if (kind == Kind.NUMBER || kind == Kind.BOUND) ops.addAll(List.of("lt", "lte", "gt", "gte"));
            return Collections.unmodifiableSet(ops);
        }
        boolean accepts(JsonElement value) {
            if (value == null || !value.isJsonPrimitive()) return false;
            JsonPrimitive p = value.getAsJsonPrimitive();
            return switch (kind) {
                case STRING -> p.isString();
                case BOOLEAN -> p.isBoolean();
                case NUMBER, BOUND -> {
                    if (!p.isNumber()) yield false;
                    try {
                        java.math.BigDecimal number = p.getAsBigDecimal();
                        yield kind != Kind.BOUND || number.signum() >= 0 && number.stripTrailingZeros().scale() <= 0;
                    } catch (NumberFormatException e) { yield false; }
                }
            };
        }
    }
    private static final Map<String, Map<String, Field>> FIELDS = new LinkedHashMap<>();
    private static final Map<String, Map<String, String>> ARRAYS = Map.of(
            "members", Map.of("parameters", "parameters"));
    static {
        for (String resource : List.of("symbols", "members", "events", "inputAdapters", "declarations")) {
            FIELDS.put(resource, new LinkedHashMap<>());
            add(resource, Kind.STRING, "symbolId", "name", "kind", "coverage", "source.path", "source.sha256");
        }
        add("symbols", Kind.STRING, "qualifiedName", "package", "typeId", "declarationId", "moduleId", "category", "owner");
        add("symbols", Kind.BOOLEAN, "staticClass");
        add("members", Kind.STRING, "ownerSymbolId", "declaringType.id", "returnType.id", "returnType.display", "type.id", "type.display", "signature");
        add("members", Kind.BOOLEAN, "isStatic", "readable", "writable");
        add("members", Kind.NUMBER, "callArity.min", "parameterCount");
        add("members", Kind.BOUND, "callArity.max");
        add("events", Kind.STRING, "group", "payloadType.id", "moduleId", "category", "owner");
        add("events", Kind.BOOLEAN, "cancellable");
        add("inputAdapters", Kind.STRING, "targetType", "syntax", "precedence");
        add("declarations", Kind.STRING, "path", "sha256");
        FIELDS.put("parameters", new LinkedHashMap<>());
        add("parameters", Kind.STRING, "name", "type.id", "type.display");
        add("parameters", Kind.NUMBER, "position");
        add("parameters", Kind.BOOLEAN, "optional", "varargs");
    }
    private static void add(String resource, Kind kind, String... names) {
        for (String name : names) FIELDS.get(resource).put(name, new Field(kind));
    }
    static Field field(String resource, String name, String path) {
        Field field = FIELDS.getOrDefault(resource, Map.of()).get(name);
        if (field == null) throw AuthoringJson.invalid(path, "Unknown or unsupported query field.");
        return field;
    }
    static String array(String resource, String array, String path) {
        String element = ARRAYS.getOrDefault(resource, Map.of()).get(array);
        if (element == null) throw AuthoringJson.invalid(path, "Unknown correlated array field.");
        return element;
    }
    static Set<String> selectable(String resource) {
        Set<String> names = new LinkedHashSet<>(FIELDS.getOrDefault(resource, Map.of()).keySet());
        names.addAll(List.of("source", "scriptTypes", "docs", "incompleteReasons"));
        switch (resource) {
            case "members" -> names.addAll(List.of("parameters", "returnType", "type", "callArity", "declaringType"));
            case "declarations" -> names.add("text");
            case "inputAdapters" -> names.add("shapes");
            default -> { }
        }
        return names;
    }
    static JsonObject catalog() {
        JsonObject result = new JsonObject();
        FIELDS.forEach((resource, fields) -> {
            JsonObject entries = new JsonObject();
            fields.forEach((name, field) -> {
                JsonObject entry = AuthoringJson.obj("type", field.kind() == Kind.BOUND ? "arityUpperBound" : field.kind().name().toLowerCase(Locale.ROOT),
                        "operators", field.operators(), "mayBeUnknown", true);
                if (field.kind() == Kind.BOUND) {
                    entry.add("operandSchema", AuthoringJson.obj("type", "integer", "minimum", 0));
                    entry.add("valueSchema", AuthoringJson.obj("oneOf", List.of(
                            AuthoringJson.obj("type", "object", "properties", AuthoringJson.obj("kind", AuthoringJson.obj("const", "finite"), "value", AuthoringJson.obj("type", "integer", "minimum", 0)), "required", List.of("kind", "value"), "additionalProperties", false),
                            AuthoringJson.obj("type", "object", "properties", AuthoringJson.obj("kind", AuthoringJson.obj("const", "unbounded")), "required", List.of("kind"), "additionalProperties", false))));
                }
                entries.add(name, entry);
            });
            result.add(resource, AuthoringJson.obj("fields", entries, "arrays", ARRAYS.getOrDefault(resource, Map.of()),
                    "select", selectable(resource)));
        });
        return result;
    }
    static JsonElement value(JsonObject record, String field) {
        JsonElement current = record;
        for (String part : field.split("\\.")) {
            if (current == null || !current.isJsonObject()) return null;
            current = current.getAsJsonObject().get(part);
        }
        return current;
    }
}
