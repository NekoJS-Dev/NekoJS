package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.fs.ScriptPathLayout;
import com.tkisor.nekojs.api.surface.ApiSurfaceSnapshot;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;

public final class ScriptBindingSchema {
    private final Map<ScriptType, View> active = new ConcurrentHashMap<>();
    /**
     * 每脚本类型的「已知全局标识符」全集：从运行中 Context 的全局绑定键收割
     * （JS 内置 Math/JSON/console/… + 引擎/平台装的所有绑定），供未定义标识符
     * 静态检查使用——以运行时真实可见集合为准，杜绝手写内置名单的误报/漏报。
     */
    private final Map<Object, Candidate> candidates = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public ScriptBindingSchema() {}

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

    /** Install the active view owned by this runtime root. */
    public void installActive(ScriptType type, Map<String, BindingMembers> nameToMembers, Set<String> globals) {
        ensureOpen();
        if (type == null) return;
        active.put(type, new View(nameToMembers, globals));
    }

    public void clear(ScriptType type) {
        if (type != null) active.remove(type);
    }

    /** Close the runtime owner and drop active/candidate views together. */
    public void close() {
        closed = true;
        active.clear();
        candidates.clear();
    }

    public Map<String, BindingMembers> lookup(ScriptType type) {
        return activeView(type).lookup();
    }

    /** Snapshot the active view without exposing mutable static maps to a generation. */
    public View activeView(ScriptType type) {
        if (type == null) return new View(Map.of(), Set.of());
        return active.getOrDefault(type, new View(Map.of(), Set.of()));
    }

    /** Empty view used by a generation before its bindings have been installed. */
    public static View emptyView() {
        return new View(Map.of(), Set.of());
    }

    /** Capture the active schema/global values before a candidate starts mutating its own view. */
    public Snapshot snapshot(ScriptType type) {
        return new Snapshot(activeView(type));
    }

    /** Install candidate values under one generation/session token. */
    public View beginCandidate(Object ownerToken, ScriptType type,
                               Map<String, BindingMembers> schemas, Set<String> globals) {
        return beginCandidate(ownerToken, type, schemas, globals, (Consumer<Diagnostic>) null);
    }

    public View beginCandidate(Object ownerToken, ScriptType type,
                               Map<String, BindingMembers> schemas, Set<String> globals,
                               Consumer<Diagnostic> reporter) {
        ensureOpen();
        if (ownerToken == null || type == null) throw new NullPointerException("candidate schema owner/type");
        View view = new View(schemas, globals, reporter);
        candidates.put(ownerToken, new Candidate(type, view, snapshot(type)));
        return view;
    }

    /** Candidate diagnostics stay attached to the generation without exposing Context here. */
    public View beginCandidate(Object ownerToken, ScriptType type,
                               Map<String, BindingMembers> schemas, Set<String> globals,
                               Object diagnosticContext) {
        return beginCandidate(ownerToken, type, schemas, globals,
                diagnostic -> ScriptErrorReporter.recordCallbackError(
                        diagnosticContext, diagnostic.type(), diagnostic.callbackKind(), diagnostic.throwable()));
    }

    /** Publish exactly one candidate view at the generation commit point. */
    public View commitCandidate(Object ownerToken) {
        Candidate candidate = ownerToken == null ? null : candidates.remove(ownerToken);
        if (candidate == null) return null;
        active.put(candidate.type(), candidate.view());
        return candidate.view();
    }

    /** Discard a candidate schema after any preparation/binding/execution failure. */
    public void discardCandidate(Object ownerToken) {
        if (ownerToken == null) return;
        Candidate candidate = candidates.remove(ownerToken);
        if (candidate != null) {
            // Schema transactions are serialized with generation commit by the owning manager;
            // restore the captured active view only if no newer same-type generation published.
            // This keeps a late failure from an older candidate from rolling back a committed one.
            if (activeView(candidate.type()).equals(candidate.activeBefore().view())) {
                active.put(candidate.type(), candidate.activeBefore().view());
            }
        }
    }

    /** Restore an active schema/global snapshot for an owning reload transaction. */
    public void restore(ScriptType type, Snapshot snapshot) {
        if (type == null || snapshot == null) return;
        active.put(type, snapshot.view());
    }

    /** Resolve a generation view for preparation/validation. */
    public View view(Object ownerToken, ScriptType type) {
        if (ownerToken == null) return activeView(type);
        Candidate candidate = candidates.get(ownerToken);
        return candidate != null && candidate.type() == type ? candidate.view() : emptyView();
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
        return ScriptPathLayout.typeOf(path.toAbsolutePath().normalize());
    }

    public static Map<String, BindingMembers> schemaForPath(Path path, View view) {
        return view == null ? Map.of() : view.lookup();
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("ScriptBindingSchema owner is closed");
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
