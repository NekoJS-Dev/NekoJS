package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.fs.ScriptPathProvider;
import com.tkisor.nekojs.api.surface.ApiSurfaceSnapshot;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Public read-only binding-schema contract. The mutable active/candidate store lives in
 * {@code com.tkisor.nekojs.core.module.BindingSchemaStore} and is owned by
 * {@code NekoModulePipelineCache}; consumers only ever see immutable {@link View} /
 * {@link Snapshot} values.
 */
public final class ScriptBindingSchema {
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

    /** Empty view used by a generation before its bindings have been installed. */
    public static View emptyView() {
        return new View(Map.of(), Set.of());
    }

    /**
     * Generation-owned diagnostic route used by candidate {@link View}s. The supplied generation
     * context (a Graal {@code Context} at runtime, typed as {@code Object} so preparation-layer
     * signatures stay Context-free) routes preflight diagnostics to that generation's error
     * tracker. This is the reporting path consumed by {@link View#report}; it neither exposes nor
     * drives schema transactions.
     */
    public static Consumer<Diagnostic> diagnosticReporter(Object diagnosticContext) {
        return diagnostic -> ScriptErrorReporter.recordCallbackError(
                diagnosticContext, diagnostic.type(), diagnostic.callbackKind(), diagnostic.throwable());
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
        return ScriptPathProvider.typeOf(path.normalize());
    }

    public static Map<String, BindingMembers> schemaForPath(Path path, View view) {
        return view == null ? Map.of() : view.lookup();
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
