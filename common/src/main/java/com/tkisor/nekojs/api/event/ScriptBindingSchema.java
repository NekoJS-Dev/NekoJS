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
    /**
     * 每脚本类型的「已知全局标识符」全集：从运行中 Context 的全局绑定键收割
     * （JS 内置 Math/JSON/console/… + 引擎/平台装的所有绑定），供未定义标识符
     * 静态检查使用——以运行时真实可见集合为准，杜绝手写内置名单的误报/漏报。
     */
    private static final Map<ScriptType, Set<String>> GLOBALS = new ConcurrentHashMap<>();

    private ScriptBindingSchema() {}

    /**
     * 绑定成员 schema。{@code valueClasses} 供链式类型流（{@code Item.of(x).member}
     * 的第二级成员检查）取成员/返回值类型；只有名字没有类信息时传空集合。
     */
    public record BindingMembers(Set<String> memberNames, Set<Class<?>> valueClasses) {
        public BindingMembers(Set<String> memberNames) {
            this(memberNames, Set.of());
        }

        public boolean contains(String member) { return memberNames.contains(member); }
    }

    public static void register(ScriptType type, Map<String, BindingMembers> nameToMembers) {
        if (type == null) return;
        SCHEMAS.put(type, nameToMembers == null ? Map.of() : Map.copyOf(nameToMembers));
    }

    public static void clear(ScriptType type) {
        if (type != null) SCHEMAS.remove(type);
    }

    /** 登记某脚本类型的已知全局标识符全集（环境工厂在 Context 组装完成后收割）。 */
    public static void registerGlobals(ScriptType type, Set<String> names) {
        if (type == null || names == null || names.isEmpty()) return;
        GLOBALS.put(type, Set.copyOf(names));
    }

    /** 某脚本类型的已知全局标识符全集；未登记返回空集合（未定义标识符检查须据此自行降级跳过）。 */
    public static Set<String> knownGlobals(ScriptType type) {
        return type == null ? Set.of() : GLOBALS.getOrDefault(type, Set.of());
    }

    public static void clearAll() {
        SCHEMAS.clear();
        GLOBALS.clear();
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
