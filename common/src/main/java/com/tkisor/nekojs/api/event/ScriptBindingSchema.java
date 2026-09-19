package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.surface.ApiSurfaceSnapshot;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;

public final class ScriptBindingSchema {
    private static final Map<ScriptType, Map<String, BindingMembers>> SCHEMAS = new ConcurrentHashMap<>();
    /**
     * 每脚本类型的「已知全局标识符」全集：从运行中 Context 的全局绑定键收割
     * （JS 内置 Math/JSON/console/… + 引擎/平台装的所有绑定），供未定义标识符
     * 静态检查使用——以运行时真实可见集合为准，杜绝手写内置名单的误报/漏报。
     */
    private static final Map<ScriptType, Set<String>> GLOBALS = new ConcurrentHashMap<>();
    private static final Map<Object, Candidate> CANDIDATES = new ConcurrentHashMap<>();

    private ScriptBindingSchema() {}

    /** Immutable schema view owned by one active or candidate generation. */
    public record View(Map<String, BindingMembers> schemas, Set<String> globals,
                       Consumer<Diagnostic> reporter) {
        public View(Map<String, BindingMembers> schemas, Set<String> globals) {
            this(schemas == null ? Map.of() : Map.copyOf(schemas),
                    globals == null ? Set.of() : Set.copyOf(globals), null);
        }

        public View {
            schemas = schemas == null ? Map.of() : Map.copyOf(schemas);
            globals = globals == null ? Set.of() : Set.copyOf(globals);
        }

        public Map<String, BindingMembers> lookup() {
            return schemas;
        }

        public Set<String> knownGlobals() {
            return globals;
        }

        /** Report through the generation-owned diagnostic route when one is installed. */
        public void report(ScriptType type, String callbackKind, Throwable throwable) {
            if (reporter != null) {
                reporter.accept(new Diagnostic(type, callbackKind, throwable));
            } else {
                ScriptErrorReporter.recordCallbackError(type, callbackKind, throwable);
            }
        }
    }

    public record Diagnostic(ScriptType type, String callbackKind, Throwable throwable) {}

    /** Immutable active schema/global state captured before a candidate transaction starts. */
    public record Snapshot(View view) {
        public Snapshot {
            view = view == null ? new View(Map.of(), Set.of()) : view;
        }
    }

    private record Candidate(ScriptType type, View view, Snapshot activeBefore) {}

    /**
     * 绑定成员 schema。{@code valueClasses} 供链式类型流（{@code Item.of(x).member}
     * 的第二级成员检查）取成员/返回值类型；只有名字没有类信息时传空集合。
     *
     * <p>{@code dynamicMembers} = true 表示该绑定是动态键容器（如工单 10 的
     * {@code global}/{@code shared} 状态容器）：任意顶层 key 都是合法成员，binding
     * preflight 不得把未列出的成员名当拼写错误上报。
     */
    public record BindingMembers(Set<String> memberNames, Set<Class<?>> valueClasses, boolean dynamicMembers) {
        public BindingMembers {
            memberNames = memberNames == null ? Set.of() : Set.copyOf(memberNames);
            valueClasses = valueClasses == null ? Set.of() : Set.copyOf(valueClasses);
        }

        public BindingMembers(Set<String> memberNames, Set<Class<?>> valueClasses) {
            this(memberNames, valueClasses, false);
        }

        public BindingMembers(Set<String> memberNames) {
            this(memberNames, Set.of());
        }

        /** 动态键容器（任意成员合法，preflight 跳过成员枚举检查）。 */
        public static BindingMembers dynamicContainer() {
            return new BindingMembers(Set.of(), Set.of(), true);
        }

        public boolean contains(String member) {
            return dynamicMembers || memberNames.contains(member);
        }
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
        CANDIDATES.clear();
    }

    public static Map<String, BindingMembers> lookup(ScriptType type) {
        return type == null ? Map.of() : SCHEMAS.getOrDefault(type, Map.of());
    }

    /** Snapshot the active view without exposing mutable static maps to a generation. */
    public static View activeView(ScriptType type) {
        if (type == null) return new View(Map.of(), Set.of());
        return new View(lookup(type), knownGlobals(type));
    }

    /** Capture the active schema/global values before a candidate starts mutating its own view. */
    public static Snapshot snapshot(ScriptType type) {
        return new Snapshot(activeView(type));
    }

    /** Install candidate schema/global values without changing the active view. */
    public static View installCandidate(Object context, ScriptType type,
                                        Map<String, BindingMembers> schemas, Set<String> globals) {
        return installCandidate(context, type, schemas, globals, null);
    }

    public static View installCandidate(Object context, ScriptType type,
                                        Map<String, BindingMembers> schemas, Set<String> globals,
                                        Consumer<Diagnostic> reporter) {
        if (context == null || type == null) throw new NullPointerException("candidate schema context/type");
        View view = new View(schemas, globals, reporter);
        CANDIDATES.put(context, new Candidate(type, view, snapshot(type)));
        return view;
    }

    /** Publish exactly one candidate view at the generation commit point. */
    public static View publishCandidate(Object context) {
        Candidate candidate = context == null ? null : CANDIDATES.remove(context);
        if (candidate == null) return null;
        installActive(candidate.type(), candidate.view());
        return candidate.view();
    }

    /** Discard a candidate schema after any preparation/binding/execution failure. */
    public static void discardCandidate(Object context) {
        if (context == null) return;
        Candidate candidate = CANDIDATES.remove(context);
        if (candidate != null) {
            // Schema transactions are serialized with generation commit by the owning manager;
            // restore the captured active view when a candidate is discarded.
            installActive(candidate.type(), candidate.activeBefore().view());
        }
    }

    /** Restore an active schema/global snapshot for an owning reload transaction. */
    public static void restore(ScriptType type, Snapshot snapshot) {
        if (type == null || snapshot == null) return;
        installActive(type, snapshot.view());
    }

    /** Resolve a generation view for preparation/validation. */
    public static View view(Object context, ScriptType type) {
        Candidate candidate = context == null ? null : CANDIDATES.get(context);
        if (candidate != null && candidate.type() == type) return candidate.view();
        return activeView(type);
    }

    /**
     * 从脚本文件路径推导所属 {@link ScriptType}。平铺布局直接按类型根前缀判定；脚本包布局
     * （GLOBAL {@code <root>/packs/<id>/…}、WORLD {@code <world>/nekojs_packs/<id>/…}、
     * SERVER_CACHE {@code …/server_packs/…}）不在任何类型根之下，按路径中的
     * {@code <type>_scripts} 段推导——与 {@code DefaultErrorTracker.extractScriptDisplayPath}
     * 同一约定。缺了这一步，包内脚本的预检 schema 恒为空，成员/事件回调校验对包脚本
     * 整体静默失效。
     */
    public static ScriptType inferType(Path path) {
        if (path == null) return null;
        return typeByScriptsDirSegment(path.toAbsolutePath().normalize());
    }

    private static ScriptType typeByScriptsDirSegment(Path norm) {
        for (Path segment : norm) {
            ScriptType type = ScriptType.fromScriptsDirectoryName(segment.toString());
            if (type != null) return type;
        }
        return null;
    }

    public static Map<String, BindingMembers> schemaForPath(Path path) {
        return lookup(inferType(path));
    }

    public static Map<String, BindingMembers> schemaForPath(Path path, View view) {
        if (view == null) return schemaForPath(path);
        return view.lookup();
    }

    private static void installActive(ScriptType type, View view) {
        if (type == null || view == null) return;
        register(type, view.schemas());
        GLOBALS.put(type, view.globals());
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
