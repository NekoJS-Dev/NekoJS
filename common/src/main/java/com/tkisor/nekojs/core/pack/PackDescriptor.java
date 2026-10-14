package com.tkisor.nekojs.core.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.fs.NekoJSPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Opt-in versioned author contract; legacy manifests have no descriptor. */
public record PackDescriptor(Map<ScriptType, List<String>> entries, Map<String, String> dependencies,
                             Map<String, String> mods, List<String> loaders, String minecraft,
                             List<String> provides, Map<String, List<String>> requires) {
    private static final Set<String> FIELDS = Set.of("formatVersion", "id", "name", "version", "description",
            "authors", "enabled", "clientSync", "signature", "config", "entries", "dependencies", "environment", "capabilities");
    public static final String ID_PATTERN = "[a-z0-9][a-z0-9_-]*";
    private static final String SELECTOR_PATTERN = "(?:(?:GLOBAL|WORLD|MOD):|SERVER_CACHE:(?:(?:packs|worldpacks):)?)?" + ID_PATTERN;

    public PackDescriptor {
        EnumMap<ScriptType, List<String>> copied = new EnumMap<>(ScriptType.class);
        entries.forEach((type, paths) -> copied.put(type, List.copyOf(paths)));
        entries = java.util.Collections.unmodifiableMap(copied);
        dependencies = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(dependencies));
        mods = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(mods));
        loaders = List.copyOf(loaders);
        provides = List.copyOf(provides);
        Map<String, List<String>> requirements = new LinkedHashMap<>();
        requires.forEach((key, names) -> requirements.put(key, List.copyOf(names)));
        requires = java.util.Collections.unmodifiableMap(requirements);
    }

    public static PackDescriptor parse(JsonObject root) {
        if (!root.has("formatVersion")) return null;
        JsonElement format = root.get("formatVersion");
        require(format.isJsonPrimitive() && format.getAsJsonPrimitive().isNumber()
                && format.getAsBigDecimal().compareTo(java.math.BigDecimal.ONE) == 0, "formatVersion", "expected 1");
        for (String key : root.keySet()) require(FIELDS.contains(key), key, "unknown field");
        require(string(root.get("id"), "id").matches(ID_PATTERN), "id", "invalid identifier");
        String version = string(root.get("version"), "version");
        require(version.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+].*)?"), "version", "expected a three-part version");
        PackVersion.parse(version);
        for (String key : List.of("name", "description")) if (root.has(key)) string(root.get(key), key);
        for (String key : List.of("enabled", "clientSync")) if (root.has(key)) {
            JsonElement value = root.get(key);
            require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean(), key, "expected boolean");
        }
        if (root.has("authors")) strings(root.get("authors"), "authors");
        for (String key : List.of("signature", "config")) if (root.has(key)) object(root.get(key), key);
        JsonObject declared = object(root.get("entries"), "entries");
        Map<ScriptType, List<String>> entries = new EnumMap<>(ScriptType.class);
        for (var entry : declared.entrySet()) {
            ScriptType type = ScriptType.all().stream().filter(candidate -> candidate.name.equals(entry.getKey())).findFirst()
                    .orElseThrow(() -> error("entries." + entry.getKey(), "unknown script type"));
            List<String> paths = strings(entry.getValue(), "entries." + entry.getKey());
            for (String path : paths) validatePath(path, "entries." + entry.getKey());
            require(new LinkedHashSet<>(paths).size() == paths.size(), "entries." + entry.getKey(), "duplicate entry");
            entries.put(type, paths);
        }
        Map<String, String> dependencies = ranges(root.get("dependencies"), "dependencies", true);
        Map<String, String> mods = Map.of();
        List<String> loaders = List.of();
        String minecraft = "*";
        if (root.has("environment")) {
            JsonObject environment = object(root.get("environment"), "environment");
            for (String key : environment.keySet()) require(Set.of("mods", "loaders", "minecraft").contains(key),
                    "environment." + key, "unknown field");
            mods = ranges(environment.get("mods"), "environment.mods", false);
            if (environment.has("loaders")) loaders = strings(environment.get("loaders"), "environment.loaders");
            if (environment.has("minecraft")) {
                minecraft = string(environment.get("minecraft"), "environment.minecraft");
                PackVersion.validateRange(minecraft);
            }
        }
        List<String> provides = List.of();
        Map<String, List<String>> requires = new LinkedHashMap<>();
        if (root.has("capabilities")) {
            JsonObject capabilities = object(root.get("capabilities"), "capabilities");
            for (String key : capabilities.keySet()) require(Set.of("provides", "requires").contains(key),
                    "capabilities." + key, "unknown field");
            if (capabilities.has("provides")) provides = capabilityNames(capabilities.get("provides"), "capabilities.provides");
            if (capabilities.has("requires")) {
                for (var item : object(capabilities.get("requires"), "capabilities.requires").entrySet()) {
                    require(item.getKey().matches(SELECTOR_PATTERN),
                            "capabilities.requires." + item.getKey(), "invalid provider selector");
                    requires.put(item.getKey(), capabilityNames(item.getValue(), "capabilities.requires." + item.getKey()));
                }
            }
        }
        return new PackDescriptor(entries, dependencies, mods, loaders, minecraft, provides, requires);
    }

    /** All declared entries are validated before any type is activated. Libraries remain import-only. */
    public void validateFiles(Path packRoot, ScriptFilePolicy policy) {
        for (var group : entries.entrySet()) {
            Set<Path> seen = new LinkedHashSet<>();
            Path scripts = packRoot.resolve(group.getKey().scriptsDirectoryName());
            for (String entry : group.getValue()) {
                Path file = scripts.resolve(entry);
                require(NekoJSPaths.isInside(file, scripts) && NekoJSPaths.isInside(file, packRoot),
                        "entries." + group.getKey().name + "." + entry, "path escapes its script directory");
                require(Files.isRegularFile(file) && policy.isSupportedScriptFile(file),
                        "entries." + group.getKey().name + "." + entry, "missing or unsupported script file");
                require(seen.add(NekoJSPaths.canonicalForm(file)), "entries." + group.getKey().name + "." + entry,
                        "duplicate filesystem entry");
            }
        }
    }

    /** Generates the published schema from the same field names and identifier rules as parsing. */
    public static JsonObject schema() {
        JsonObject schema = typed("object");
        schema.addProperty("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.addProperty("$id", "urn:nekojs:script-pack:1");
        schema.addProperty("additionalProperties", false);
        var required = new com.google.gson.JsonArray();
        for (String field : List.of("formatVersion", "id", "version", "entries")) required.add(field);
        schema.add("required", required);
        JsonObject properties = new JsonObject();
        for (String field : FIELDS.stream().sorted().toList()) properties.add(field, typed("string"));
        JsonObject format = typed("integer"); format.addProperty("const", 1); properties.add("formatVersion", format);
        JsonObject id = typed("string"); id.addProperty("pattern", "^" + ID_PATTERN + "$"); properties.add("id", id);
        JsonObject version = typed("string");
        version.addProperty("pattern", "^" + PackVersion.pattern(true) + "$");
        properties.add("version", version);
        for (String field : List.of("enabled", "clientSync")) properties.add(field, typed("boolean"));
        for (String field : List.of("config", "signature")) properties.add(field, typed("object"));
        properties.add("authors", stringArray());
        JsonObject entries = typed("object"), types = new JsonObject();
        entries.addProperty("additionalProperties", false);
        for (ScriptType type : ScriptType.all()) {
            JsonObject paths = stringArray(); paths.addProperty("uniqueItems", true);
            JsonObject path = typed("string");
            path.addProperty("pattern", "^(?!/)(?!.*[\\\\:])(?!(?:.*/)?(?:\\.\\.?|node_modules)(?:/|$))[^/]+(?:/[^/]+)*$");
            paths.add("items", path); types.add(type.name, paths);
        }
        entries.add("properties", types); properties.add("entries", entries);
        properties.add("dependencies", rangeMap(true));
        JsonObject environment = typed("object"), conditions = new JsonObject();
        environment.addProperty("additionalProperties", false);
        conditions.add("mods", rangeMap(false)); conditions.add("loaders", stringArray()); conditions.add("minecraft", rangeSchema());
        environment.add("properties", conditions); properties.add("environment", environment);
        JsonObject capabilities = typed("object"), capabilityFields = new JsonObject();
        capabilities.addProperty("additionalProperties", false);
        JsonObject names = stringArray(); names.addProperty("uniqueItems", true);
        JsonObject capabilityName = typed("string"); capabilityName.addProperty("pattern", "^" + ID_PATTERN + "$");
        names.add("items", capabilityName);
        capabilityFields.add("provides", names);
        JsonObject requirements = typed("object");
        JsonObject selectors = typed("string");
        selectors.addProperty("pattern", "^" + SELECTOR_PATTERN + "$");
        requirements.add("propertyNames", selectors); requirements.add("additionalProperties", names);
        capabilityFields.add("requires", requirements); capabilities.add("properties", capabilityFields);
        properties.add("capabilities", capabilities);
        schema.add("properties", properties);
        return schema;
    }

    private static JsonObject typed(String type) {
        JsonObject result = new JsonObject(); result.addProperty("type", type); return result;
    }

    private static JsonObject stringArray() {
        JsonObject result = typed("array"); result.add("items", typed("string")); return result;
    }

    private static JsonObject rangeSchema() {
        JsonObject result = typed("string");
        String version = PackVersion.pattern(false);
        String term = "(?:>=|<=|>|<|=)?" + version;
        result.addProperty("pattern", "^(?:\\*|" + term + "(?: +" + term + ")*)$");
        return result;
    }

    private static JsonObject rangeMap(boolean selectors) {
        JsonObject result = typed("object"), keys = typed("string");
        keys.addProperty("pattern", "^" + (selectors ? SELECTOR_PATTERN : ID_PATTERN) + "$");
        result.add("propertyNames", keys); result.add("additionalProperties", rangeSchema()); return result;
    }

    private static void validatePath(String path, String field) {
        require(!path.isBlank() && !path.startsWith("/") && !path.contains("\\") && !path.contains(":"), field, "invalid relative path");
        for (String part : path.split("/", -1)) require(!part.isEmpty() && !part.equals(".")
                && !part.equals("..") && !part.equals("node_modules"), field, "invalid relative path segment");
    }

    private static Map<String, String> ranges(JsonElement element, String field, boolean selectors) {
        if (element == null) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        for (var item : object(element, field).entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            String id = item.getKey();
            require(id.matches(selectors ? SELECTOR_PATTERN : ID_PATTERN), field + "." + id,
                    "invalid dependency identifier");
            String range = string(item.getValue(), field + "." + id);
            try { PackVersion.validateRange(range); } catch (IllegalArgumentException invalid) {
                throw error(field + "." + id, invalid.getMessage());
            }
            result.put(id, range);
        }
        return result;
    }

    private static JsonObject object(JsonElement value, String field) {
        require(value != null && value.isJsonObject(), field, "expected object");
        return value.getAsJsonObject();
    }

    private static String string(JsonElement value, String field) {
        require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(), field, "expected string");
        return value.getAsString();
    }

    private static List<String> strings(JsonElement value, String field) {
        require(value != null && value.isJsonArray(), field, "expected string array");
        List<String> result = new ArrayList<>();
        for (JsonElement item : value.getAsJsonArray()) result.add(string(item, field));
        return result;
    }

    private static List<String> capabilityNames(JsonElement value, String field) {
        List<String> names = strings(value, field);
        require(new LinkedHashSet<>(names).size() == names.size(), field, "duplicate capability");
        for (String name : names) require(name.matches(ID_PATTERN), field, "invalid capability name");
        return names;
    }

    private static void require(boolean condition, String field, String reason) {
        if (!condition) throw error(field, reason);
    }

    private static IllegalArgumentException error(String field, String reason) {
        return new IllegalArgumentException("[NEKO-1016] Pack manifest field " + field + " rejected: " + reason);
    }
}
