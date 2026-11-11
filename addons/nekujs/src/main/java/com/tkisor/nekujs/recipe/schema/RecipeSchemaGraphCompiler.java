package com.tkisor.nekujs.recipe.schema;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldDefinition;
import com.tkisor.nekojs.api.recipe.RecipeExecutionFactory;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldKind;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldRole;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldValueCodec;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Compiles source graphs without resource IO, registry mutation, or script evaluation. */
public final class RecipeSchemaGraphCompiler {
    /** Owned IDs remain enumerable; fallback parents are graph dependencies, not additional published recipes. */
    public Compilation compile(List<SchemaDocument> documents, SemanticComponents components) {
        Objects.requireNonNull(documents, "documents");
        Objects.requireNonNull(components, "components");
        return new Graph(documents, components).compile();
    }

    private static final class Graph {
        private final Map<String, SchemaDocument> documents = new LinkedHashMap<>();
        private final LinkedHashSet<String> ownedIds = new LinkedHashSet<>();
        private final Map<String, Resolved> resolved = new LinkedHashMap<>();
        private final Map<String, Failure> failures = new LinkedHashMap<>();
        private final LinkedHashSet<String> visiting = new LinkedHashSet<>();
        private final SemanticComponents components;

        private Graph(List<SchemaDocument> inputs, SemanticComponents components) {
            this.components = components;
            for (var document : inputs) {
                ownedIds.add(document.id());
                var previous = documents.putIfAbsent(document.id(), document);
                if (previous != null) {
                    failures.put(document.id(), new Location(document, "$").fail("NEKO-5241",
                            "Duplicate schema ID: " + document.id() + "; first source=" + previous.source()
                                    + ":" + previous.resourcePath()));
                }
            }
            for (var parent : NativeRecipeSchemaParents.documents()) documents.putIfAbsent(parent.id(), parent);
        }

        private Compilation compile() {
            var definitions = new LinkedHashMap<String, RecipeTypeDefinition>();
            var rejections = new ArrayList<SchemaRejection>();
            for (var document : documents.values()) {
                if (!ownedIds.contains(document.id())) continue;
                try {
                    var schema = resolve(document.id());
                    var definition = emit(schema);
                    var name = document.id().substring(document.id().indexOf(':') + 1);
                    if (name.startsWith("base/")) {
                        throw new Location(document, "$").fail("NEKO-5251", "Abstract schema dependency is not callable");
                    }
                    definitions.put(document.id(), definition);
                } catch (Failure failure) {
                    var rejection = new SchemaRejection(failure.code, document.id(), failure.location.document().source(),
                            failure.location.document().resourcePath(), failure.location.path(), failure.getMessage());
                    rejections.add(rejection);
                    var id = splitId(document.id());
                    definitions.put(document.id(), RecipeTypeDefinition.unsupported(id[0], id[1], document.id(), rejection.reason()));
                }
            }
            return new Compilation(definitions, rejections);
        }

        private Resolved resolve(String id) {
            if (failures.containsKey(id)) throw failures.get(id);
            if (resolved.containsKey(id)) return resolved.get(id);
            var document = documents.get(id);
            var root = new Location(document, "$");
            if (!visiting.add(id)) {
                throw root.child(".parent").fail("NEKO-5243", "Schema parent cycle: " + String.join(" -> ", visiting) + " -> " + id);
            }
            try {
                resourceId(id, root);
                var json = document.json();
                Resolved parent = null;
                if (json.has("parent")) {
                    var parentLocation = root.child(".parent");
                    String parentId = resourceId(string(json.get("parent"), parentLocation), parentLocation);
                    if (!documents.containsKey(parentId)) {
                        String kind = parentId.equals("minecraft:shaped") || parentId.equals("minecraft:special")
                                ? "Unsupported inherited schema: " : "Missing parent schema: ";
                        throw parentLocation.fail("NEKO-5242", kind + parentId);
                    }
                    parent = resolve(parentId);
                }
                var merge = json.has("merge") ? object(json.get("merge"), root.child(".merge")) : new JsonObject();
                var keys = mergeKeys(document, parent, json, merge);
                var constructors = inheritList(document, json, "constructors", parent == null ? List.of() : parent.constructors(),
                        flag(merge, "constructors", false, root.child(".merge")));
                var functions = new LinkedHashMap<String, Located>();
                if (parent != null) functions.putAll(parent.functions());
                if (json.has("functions")) {
                    var local = object(json.get("functions"), root.child(".functions"));
                    for (var entry : local.entrySet()) {
                        functions.put(entry.getKey(), new Located(root.child(".functions[" + entry.getKey() + "]"), entry.getValue()));
                    }
                }
                var overrides = new LinkedHashMap<String, Located>();
                // Alias-only children share the parent schema; a new definition owns its own override_keys.
                boolean ownsSchema = List.of("override_type", "keys", "constructors", "functions", "factory", "recipe_factory", "unique", "override_keys")
                        .stream().anyMatch(json::has);
                if (!ownsSchema && parent != null) overrides.putAll(parent.overrides());
                if (json.has("override_keys")) {
                    var local = object(json.get("override_keys"), root.child(".override_keys"));
                    for (var entry : local.entrySet()) {
                        overrides.put(entry.getKey(), new Located(root.child(".override_keys[" + entry.getKey() + "]"), entry.getValue()));
                    }
                }
                var unique = inheritList(document, json, "unique", parent == null ? List.of() : parent.unique(),
                        flag(merge, "unique", false, root.child(".merge")));
                var processors = inheritList(document, json, "post_processors", parent == null ? List.of() : parent.processors(),
                        flag(merge, "post_processors", false, root.child(".merge")));
                var mappings = inheritList(document, json, "mappings", List.of(), false);
                String type = json.has("override_type")
                        ? resourceId(string(json.get("override_type"), root.child(".override_type")), root.child(".override_type"))
                        : !ownsSchema && parent != null ? parent.type() : null;
                if (json.has("factory") && json.has("recipe_factory") && !json.get("factory").equals(json.get("recipe_factory"))) {
                    throw root.child(".factory").fail("NEKO-5250", "Conflicting factory and recipe_factory declarations");
                }
                String factoryProperty = json.has("factory") ? "factory" : "recipe_factory";
                Located factory = json.has(factoryProperty) ? new Located(root.child("." + factoryProperty), json.get(factoryProperty))
                        : parent == null ? null : parent.factory();
                var result = new Resolved(document, keys, constructors, functions, overrides, unique, processors, mappings,
                        flag(json, "hidden", parent != null && parent.hidden(), root), type, factory);
                resolved.put(id, result);
                return result;
            } catch (Failure failure) {
                failures.put(id, failure);
                throw failure;
            } finally {
                visiting.remove(id);
            }
        }

        private LinkedHashMap<String, Located> mergeKeys(SchemaDocument document, Resolved parent, JsonObject json, JsonObject merge) {
            var root = new Location(document, "$");
            var result = new LinkedHashMap<String, Located>();
            boolean merges = flag(merge, "keys", false, root.child(".merge"));
            if (parent != null && (!json.has("keys") || merges)) result.putAll(parent.keys());
            if (!json.has("keys")) return result;
            var array = array(json.get("keys"), root.child(".keys"));
            var localNames = new LinkedHashSet<String>();
            int newOptionals = 0;
            for (int i = 0; i < array.size(); i++) {
                var location = root.child(".keys[" + i + "]");
                var key = object(array.get(i), location);
                String name = requiredString(key, "name", location);
                if (!localNames.add(name)) throw location.child(".name").fail("NEKO-5244", "Duplicate key: " + name);
                if (!key.has("type")) throw location.child(".type").fail("NEKO-5240", "Missing component type");
                var value = new Located(location, key);
                if (merges) {
                    boolean optional = optional(value);
                    boolean oldOptional = result.containsKey(name) && optional(result.get(name));
                    if (newOptionals != 0 && !optional) {
                        throw location.fail("NEKO-5244", "Required key must precede newly added optional keys: " + name);
                    }
                    if (!optional && oldOptional) {
                        throw location.fail("NEKO-5244", "Optional parent key cannot become required: " + name);
                    }
                    if (optional && !oldOptional) newOptionals++;
                }
                result.put(name, value);
            }
            if (merges) {
                var ordered = new LinkedHashMap<String, Located>();
                result.forEach((name, value) -> { if (!optional(value)) ordered.put(name, value); });
                result.forEach((name, value) -> { if (optional(value)) ordered.put(name, value); });
                return ordered;
            }
            return result;
        }

        private RecipeTypeDefinition emit(Resolved schema) {
            RecipeExecutionFactory executionFactory = null;
            if (schema.factory() != null || !schema.processors().isEmpty()) {
                var capabilityLocation = schema.factory() == null ? schema.processors().getFirst().location() : schema.factory().location();
                try {
                    executionFactory = components.executionFactory(
                            schema.factory() == null ? null : schema.factory().value().deepCopy(),
                            schema.processors().stream().map(processor -> processor.value().deepCopy()).toList());
                } catch (IllegalArgumentException unsupported) {
                    throw capabilityLocation.fail("NEKO-5250", "Recipe execution capability is unsupported: " + unsupported.getMessage(), unsupported);
                }
                if (executionFactory == null) throw capabilityLocation.fail("NEKO-5250", "Recipe execution resolver returned no factory");
            }
            if (!schema.mappings().isEmpty()) throw schema.mappings().getFirst().location().fail("NEKO-5250", "Schema mappings are not supported");
            var fields = new LinkedHashMap<String, RecipeFieldDefinition>();
            var aliases = new LinkedHashMap<String, String>();
            var keyOverrides = new LinkedHashMap<String, JsonElement>();
            for (var entry : schema.keys().entrySet()) {
                String name = entry.getKey();
                var located = entry.getValue();
                var key = located.value().getAsJsonObject();
                var expression = key.get("type");
                RecipeFieldValueCodec codec;
                try {
                    codec = components.resolve(expression.deepCopy());
                } catch (IllegalArgumentException unsupported) {
                    throw located.location().child(".type").fail("NEKO-5246", "Unsupported component: " + unsupported.getMessage(), unsupported);
                }
                if (codec == null) throw located.location().child(".type").fail("NEKO-5246", "Component resolver returned no semantic codec");
                boolean absentDefault = flag(key, "default_optional", false, located.location());
                boolean optional = optional(located);
                boolean alwaysWrite = flag(key, "always_write", false, located.location());
                if (absentDefault && alwaysWrite) throw located.location().child(".always_write").fail("NEKO-5247", "default_optional cannot use always_write");
                JsonElement defaultValue = !absentDefault && key.has("optional")
                        ? constant(expression, new Located(located.location().child(".optional"), key.get("optional"))) : null;
                RecipeFieldRole role = switch (key.has("role") ? string(key.get("role"), located.location().child(".role")) : "other") {
                    case "input" -> RecipeFieldRole.INPUT;
                    case "output" -> RecipeFieldRole.OUTPUT;
                    case "other" -> RecipeFieldRole.OTHER;
                    default -> throw located.location().child(".role").fail("NEKO-5240", "Unknown field role");
                };
                fields.put(name, new RecipeFieldDefinition(name, name, RecipeFieldKind.JSON, false, optional, defaultValue,
                        role, codec, alwaysWrite));
                boolean explicitFunctions = key.has("function_names") && !key.get("function_names").isJsonNull();
                String selectedProperty = explicitFunctions ? "function_names" : "alternative_names";
                var selectedNames = new ArrayList<Located>();
                if (!explicitFunctions) selectedNames.add(new Located(located.location().child(".name"), key.get("name")));
                for (String property : List.of("alternative_names", "function_names")) {
                    if (key.has(property) && !key.get(property).isJsonNull()) {
                        var values = array(key.get(property), located.location().child("." + property));
                        for (int i = 0; i < values.size(); i++) {
                            var aliasLocation = located.location().child("." + property + "[" + i + "]");
                            String alias = string(values.get(i), aliasLocation);
                            addAlias(schema, aliases, name, alias, aliasLocation);
                            if (property.equals(selectedProperty)) selectedNames.add(new Located(aliasLocation, values.get(i)));
                        }
                    }
                }
                for (var selected : selectedNames) {
                    String alias = fluentName(selected.value().getAsString());
                    if (alias != null) addAlias(schema, aliases, name, alias, selected.location());
                }
            }
            for (var entry : schema.overrides().entrySet()) {
                var key = requireKey(schema, entry.getKey(), entry.getValue().location());
                keyOverrides.put(entry.getKey(), constant(key.value().getAsJsonObject().get("type"), entry.getValue()));
            }
            var functions = new LinkedHashMap<String, Map<String, JsonElement>>();
            for (var entry : schema.functions().entrySet()) {
                var location = entry.getValue().location();
                if (fields.containsKey(entry.getKey())) {
                    throw location.fail("NEKO-5249", "Function conflicts with a field setter: " + entry.getKey());
                }
                var function = object(entry.getValue().value(), location);
                if (!requiredString(function, "type", location).equals("set")) {
                    throw location.child(".type").fail("NEKO-5250", "Only declarative set functions are supported");
                }
                String name = requiredString(function, "key", location);
                var key = requireKey(schema, name, location.child(".key"));
                if (!function.has("value")) throw location.child(".value").fail("NEKO-5240", "Missing function value");
                functions.put(entry.getKey(), Map.of(name, constant(key.value().getAsJsonObject().get("type"),
                        new Located(location.child(".value"), function.get("value")))));
                // Schema functions replace component setters in the reference name-to-function map.
                aliases.remove(entry.getKey());
            }
            var unique = new LinkedHashSet<String>();
            for (var value : schema.unique()) {
                String name = string(value.value(), value.location());
                requireKey(schema, name, value.location());
                unique.add(name);
            }
            var constructorKeys = new ArrayList<List<String>>();
            var constructorOverrides = new LinkedHashMap<List<String>, Map<String, JsonElement>>();
            var arities = new LinkedHashSet<Integer>();
            var constructors = schema.constructors();
            if (constructors.isEmpty()) {
                var included = new ArrayList<String>();
                boolean sawOptional = false;
                int firstOptional = -1;
                for (var entry : schema.keys().entrySet()) {
                    var key = entry.getValue().value().getAsJsonObject();
                    boolean optional = optional(entry.getValue());
                    if (keyOverrides.containsKey(entry.getKey()) || optional && flag(key, "excluded", false, entry.getValue().location())) continue;
                    if (optional) {
                        sawOptional = true;
                        if (firstOptional < 0) firstOptional = included.size();
                    } else if (sawOptional) {
                        throw entry.getValue().location().fail("NEKO-5244", "Required key must precede optional keys: " + entry.getKey());
                    }
                    included.add(entry.getKey());
                }
                // The reference uses zero as an unset sentinel: all-optional rotary starts at one, not zero.
                int minimum = firstOptional < 0 ? included.size() : firstOptional == 0 && !included.isEmpty() ? 1 : firstOptional;
                for (int arity = minimum; arity <= included.size(); arity++) {
                    addConstructor(List.copyOf(included.subList(0, arity)), keyOverrides, constructorKeys, constructorOverrides, arities,
                            new Location(schema.document(), "$.constructors"));
                }
            } else {
                for (var located : constructors) {
                    var constructor = object(located.value(), located.location());
                    var names = array(required(constructor, "keys", located.location()), located.location().child(".keys"));
                    var keys = new ArrayList<String>();
                    var namesSeen = new LinkedHashSet<String>();
                    for (int i = 0; i < names.size(); i++) {
                        var location = located.location().child(".keys[" + i + "]");
                        String name = string(names.get(i), location);
                        requireKey(schema, name, location);
                        if (!namesSeen.add(name)) throw location.fail("NEKO-5248", "Duplicate constructor key: " + name);
                        keys.add(name);
                    }
                    var overrides = new LinkedHashMap<String, JsonElement>();
                    if (constructor.has("overrides")) {
                        var values = object(constructor.get("overrides"), located.location().child(".overrides"));
                        for (var entry : values.entrySet()) {
                            var location = located.location().child(".overrides[" + entry.getKey() + "]");
                            var key = requireKey(schema, entry.getKey(), location);
                            overrides.put(entry.getKey(), constant(key.value().getAsJsonObject().get("type"), new Located(location, entry.getValue())));
                        }
                    }
                    overrides.putAll(keyOverrides);
                    addConstructor(List.copyOf(keys), overrides, constructorKeys, constructorOverrides, arities, located.location());
                }
            }
            var id = splitId(schema.document().id());
            return new RecipeTypeDefinition(id[0], id[1], schema.type() == null ? schema.document().id() : schema.type(), null,
                    List.copyOf(constructorKeys), Collections.unmodifiableMap(fields), List.copyOf(unique),
                    Collections.unmodifiableMap(aliases), Collections.unmodifiableMap(functions), Collections.unmodifiableMap(constructorOverrides),
                    null, null, executionFactory);
        }

        private JsonElement constant(JsonElement expression, Located literal) {
            JsonElement encoded;
            try {
                encoded = components.encodeConstant(expression.deepCopy(), literal.value().deepCopy());
            } catch (IllegalArgumentException invalid) {
                throw literal.location().fail("NEKO-5247", "Invalid component constant: " + invalid.getMessage(), invalid);
            }
            if (encoded == null) throw literal.location().fail("NEKO-5247", "Constant encoder returned no JSON value");
            return encoded.deepCopy();
        }

        private Located requireKey(Resolved schema, String name, Location location) {
            var key = schema.keys().get(name);
            if (key == null) throw location.fail("NEKO-5245", "Unknown schema key: " + name);
            return key;
        }
    }

    private static void addAlias(Resolved schema, Map<String, String> aliases, String name, String alias, Location location) {
        if (alias.equals(name)) return;
        if (schema.keys().containsKey(alias) || aliases.containsKey(alias) && !aliases.get(alias).equals(name)) {
            throw location.fail("NEKO-5249", "Ambiguous field alias: " + alias);
        }
        aliases.put(alias, name);
    }

    private static String fluentName(String name) {
        var result = new StringBuilder();
        for (String part : name.split("[:_/]")) {
            if (part.isEmpty()) continue;
            if (result.isEmpty()) result.append(part);
            else result.append(Character.toUpperCase(part.charAt(0))).append(part, 1, part.length());
        }
        if (result.isEmpty() || !Character.isJavaIdentifierStart(result.charAt(0))) return null;
        for (int i = 1; i < result.length(); i++) {
            if (!Character.isJavaIdentifierPart(result.charAt(i))) return null;
        }
        return result.toString();
    }

    private static void addConstructor(List<String> keys, Map<String, JsonElement> overrides, List<List<String>> constructors,
                                       Map<List<String>, Map<String, JsonElement>> constructorOverrides, LinkedHashSet<Integer> arities,
                                       Location location) {
        if (!arities.add(keys.size())) throw location.fail("NEKO-5248", "Duplicate constructor arity: " + keys.size());
        constructors.add(keys);
        if (!overrides.isEmpty()) constructorOverrides.put(keys, Collections.unmodifiableMap(new LinkedHashMap<>(overrides)));
    }

    private static List<Located> inheritList(SchemaDocument document, JsonObject json, String property, List<Located> parent, boolean merge) {
        if (!json.has(property)) return parent;
        var location = new Location(document, "$." + property);
        var values = array(json.get(property), location);
        var result = new ArrayList<Located>();
        if (merge) result.addAll(parent);
        for (int i = 0; i < values.size(); i++) result.add(new Located(location.child("[" + i + "]"), values.get(i)));
        return List.copyOf(result);
    }

    private static boolean optional(Located key) {
        var object = key.value().getAsJsonObject();
        return flag(object, "default_optional", false, key.location()) || object.has("optional");
    }

    private static String[] splitId(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? new String[]{"minecraft", id} : new String[]{id.substring(0, colon), id.substring(colon + 1)};
    }

    private static String resourceId(String value, Location location) {
        String id = value.contains(":") ? value : "minecraft:" + value;
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw location.fail("NEKO-5240", "Invalid resource ID: " + value);
        return id;
    }

    private static JsonElement required(JsonObject object, String name, Location location) {
        if (!object.has(name)) throw location.child("." + name).fail("NEKO-5240", "Missing property: " + name);
        return object.get(name);
    }

    private static String requiredString(JsonObject object, String name, Location location) {
        return string(required(object, name, location), location.child("." + name));
    }

    private static String string(JsonElement value, Location location) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank()) {
            throw location.fail("NEKO-5240", "Expected a non-blank string");
        }
        return value.getAsString();
    }

    private static boolean flag(JsonObject object, String name, boolean fallback, Location location) {
        if (!object.has(name)) return fallback;
        var value = object.get(name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw location.child("." + name).fail("NEKO-5240", "Expected a boolean");
        return value.getAsBoolean();
    }

    private static JsonObject object(JsonElement value, Location location) {
        if (value == null || !value.isJsonObject()) throw location.fail("NEKO-5240", "Expected an object");
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonElement value, Location location) {
        if (value == null || !value.isJsonArray()) throw location.fail("NEKO-5240", "Expected an array");
        return value.getAsJsonArray();
    }

    private record Located(Location location, JsonElement value) { }

    private record Resolved(SchemaDocument document, LinkedHashMap<String, Located> keys, List<Located> constructors,
                            LinkedHashMap<String, Located> functions, LinkedHashMap<String, Located> overrides,
                            List<Located> unique, List<Located> processors, List<Located> mappings,
                            boolean hidden, String type, Located factory) { }

    private record Location(SchemaDocument document, String path) {
        private Location child(String suffix) { return new Location(document, path + suffix); }
        private Failure fail(String code, String message) { return fail(code, message, null); }
        private Failure fail(String code, String message, Throwable cause) { return new Failure(this, code, message, cause); }
    }

    private static final class Failure extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private final Location location;
        private final String code;

        private Failure(Location location, String code, String message, Throwable cause) {
            super(message, cause);
            this.location = location;
            this.code = code;
        }
    }
}
