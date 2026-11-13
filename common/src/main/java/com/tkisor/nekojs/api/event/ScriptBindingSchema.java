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
     * Binding member schema. {@code valueClasses} supplies the member and return types to chained type
     * flows (the second-level member check of {@code Item.of(x).member}); pass an empty set when only
     * names are known.
     *
     * <p>{@code dynamicMembers} = true marks the binding as a dynamic key container (such as the
     * {@code global}/{@code shared} state containers): any top-level key is a valid member, so the
     * binding preflight must not report an unlisted member name as a typo.
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

        /** Dynamic key container: any member is valid, so the preflight skips the member-name check. */
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
     * Derives the {@link ScriptType} from a script file path. A flat layout is decided by the type root
     * prefix. Pack layouts (GLOBAL {@code <root>/packs/<id>/...}, WORLD
     * {@code <world>/nekojs_packs/<id>/...}, SERVER_CACHE {@code .../server_packs/...}) sit below no type
     * root, so the type comes from the {@code <type>_scripts} segment in the path, the same convention
     * used by {@code DefaultErrorTracker.extractScriptDisplayPath}. Without this, a pack script gets an
     * empty preflight schema and both member and event callback checks are skipped for it.
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
