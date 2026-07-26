package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.surface.ApiSurfaceSnapshot;
import com.tkisor.nekojs.api.surface.ApiSymbolId;

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

    public static BindingMembers fromSurface(ApiSurfaceSnapshot snapshot, ApiSymbolId typeId) {
        if (snapshot == null || typeId == null) return new BindingMembers(Set.of());
        Set<String> members = snapshot.symbols().stream()
                .filter(s -> s.id().kind().equals("member")
                        && s.id().qualifiedName().startsWith(typeId.qualifiedName() + "."))
                .map(s -> {
                    String qn = s.id().qualifiedName();
                    int dot = qn.indexOf('.', typeId.qualifiedName().length() + 1);
                    return dot > 0
                            ? qn.substring(typeId.qualifiedName().length() + 1, dot)
                            : qn.substring(typeId.qualifiedName().length() + 1);
                })
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new BindingMembers(members);
    }
}
