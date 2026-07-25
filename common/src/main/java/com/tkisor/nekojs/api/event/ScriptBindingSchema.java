package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ScriptBindingSchema {
    private static final Map<ScriptType, Map<String, BindingMembers>> SCHEMAS = new ConcurrentHashMap<>();

    private ScriptBindingSchema() {}

    public record BindingMembers(Set<String> memberNames) {
        public boolean contains(String member) { return memberNames.contains(member); }
    }

    public static void register(ScriptType type, Map<String, BindingMembers> nameToMembers) {
        if (type == null) return;
        SCHEMAS.put(type, nameToMembers == null ? Map.of() : Map.copyOf(nameToMembers));
    }

    public static void clear(ScriptType type) {
        if (type != null) SCHEMAS.remove(type);
    }

    public static void clearAll() {
        SCHEMAS.clear();
    }

    public static Map<String, BindingMembers> lookup(ScriptType type) {
        return type == null ? Map.of() : SCHEMAS.getOrDefault(type, Map.of());
    }

    public static ScriptType inferType(Path path) {
        if (path == null) return null;
        Path norm = path.toAbsolutePath().normalize();
        for (ScriptType type : ScriptType.values()) {
            if (type.path == null) continue;
            if (norm.startsWith(type.path.toAbsolutePath().normalize())) return type;
        }
        return null;
    }

    public static Map<String, BindingMembers> schemaForPath(Path path) {
        return lookup(inferType(path));
    }
}
