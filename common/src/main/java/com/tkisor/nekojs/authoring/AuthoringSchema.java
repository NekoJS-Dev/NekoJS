package com.tkisor.nekojs.authoring;

import com.google.gson.*;

import java.util.List;

/** Structural request schemas; field catalogs provide the resource-specific semantic constraints. */
public final class AuthoringSchema {
    private AuthoringSchema() {}

    private static JsonObject object(JsonObject properties, String... required) {
        return AuthoringJson.obj("type", "object", "properties", properties, "required", required, "additionalProperties", false);
    }
    private static JsonObject string() { return AuthoringJson.obj("type", "string", "minLength", 1); }
    private static JsonObject enumeration(String... values) { return AuthoringJson.obj("type", "string", "enum", values); }
    private static JsonObject array(JsonObject items, int min, int max) { return AuthoringJson.obj("type", "array", "items", items, "minItems", min, "maxItems", max); }
    private static JsonObject integer(int min, int max) { return AuthoringJson.obj("type", "integer", "minimum", min, "maximum", max); }
    private static JsonObject predicate() {
        JsonObject ref = AuthoringJson.obj("$ref", "#/$defs/predicate");
        JsonObject comparison = object(AuthoringJson.obj("field", string(), "op", enumeration("eq", "ne", "in", "notIn", "lt", "lte", "gt", "gte", "prefix", "hasValue"), "value", new JsonObject()), "field", "op");
        comparison.add("if", AuthoringJson.obj("properties", AuthoringJson.obj("op", AuthoringJson.obj("const", "hasValue"))));
        comparison.add("then", AuthoringJson.obj("not", AuthoringJson.obj("required", List.of("value"))));
        comparison.add("else", AuthoringJson.obj("required", List.of("value")));
        return AuthoringJson.obj("oneOf", List.of(
                object(AuthoringJson.obj("all", array(ref, 1, 32)), "all"),
                object(AuthoringJson.obj("any", array(ref, 1, 32)), "any"),
                object(AuthoringJson.obj("not", ref), "not"),
                object(AuthoringJson.obj("some", object(AuthoringJson.obj("path", string(), "where", ref), "path", "where")), "some"), comparison));
    }
    public static JsonObject input(String operation) {
        if (List.of("context", "capabilities", "schema").contains(operation)) return object(new JsonObject());
        JsonObject scope = object(AuthoringJson.obj("environmentId", string(), "snapshotId", string(),
                "scriptType", enumeration("STARTUP", "SERVER", "CLIENT", "TEST"), "language", enumeration("typescript"),
                "ownerSymbolId", string(), "memberView", enumeration("declared")), "environmentId", "scriptType", "language");
        JsonObject page = object(AuthoringJson.obj("size", integer(1, 100), "cursor", string()));
        JsonObject properties = AuthoringJson.obj("scope", scope);
        if (operation.equals("batch")) {
            JsonObject child = input("query");
            child.getAsJsonObject("properties").remove("scope");
            child.add("required", AuthoringJson.GSON.toJsonTree(List.of("from")));
            // The recursive predicate reference resolves at the enclosing document root.
            JsonObject defs = child.remove("$defs").getAsJsonObject();
            properties.add("queries", array(child, 1, 10));
            JsonObject schema = object(properties, "scope", "queries");
            schema.add("$defs", defs);
            return schema;
        }
        properties.add("select", array(string(), 1, 32));
        properties.add("page", page);
        if (operation.equals("query")) {
            properties.add("from", enumeration("symbols", "members", "events", "inputAdapters", "declarations"));
            properties.add("where", AuthoringJson.obj("$ref", "#/$defs/predicate"));
            properties.add("orderBy", array(object(AuthoringJson.obj("field", string(), "direction", enumeration("asc", "desc")), "field", "direction"), 0, 3));
            properties.add("explain", AuthoringJson.obj("type", "boolean"));
            JsonObject schema = object(properties, "scope", "from");
            schema.add("$defs", AuthoringJson.obj("predicate", predicate()));
            return schema;
        }
        properties.add("name", string());
        properties.add("kind", string());
        properties.add("match", enumeration("exact", "prefix"));
        if (operation.equals("members")) properties.add("arity", object(AuthoringJson.obj("min", integer(0, 10000), "max", integer(0, 10000)), "min", "max"));
        return object(properties, "scope");
    }
    public static JsonObject document() {
        JsonObject operations = new JsonObject();
        for (String op : List.of("context", "capabilities", "schema", "resolve", "members", "query", "batch")) operations.add(op, input(op));
        return AuthoringJson.obj("schemaVersion", "1", "jsonSchemaDialect", "https://json-schema.org/draft/2020-12/schema", "operations", operations, "fields", AuthoringFields.catalog());
    }
}
