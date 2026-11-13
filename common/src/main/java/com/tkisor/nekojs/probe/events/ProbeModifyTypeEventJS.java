package com.tkisor.nekojs.probe.events;

import com.tkisor.nekojs.probe.backend.typescript.IndexFileGenerator;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.ir.FieldDecl;
import com.tkisor.nekojs.probe.ir.MethodDecl;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeSlot;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The {@code probe.modify_type} event object. It holds every {@link TypeDecl} IR collected by this probe run
 * (fqn -> decl), so scripts can get a {@link ClassEditor} through {@link #forClass(String)} and edit it in place.
 *
 * <p>The class also provides type builders ({@link #type}/{@link #array}/{@link #union}). Edit methods accept a
 * string or an {@link ApiTypeRef}: a string containing {@code .} is treated as a Java fully qualified name
 * (SYMBOL), anything else as a primitive type name ({@code "string"}, {@code "number"}, {@code "boolean"},
 * {@code "int"} ...).
 */
public final class ProbeModifyTypeEventJS {
    private final Map<String, TypeDecl> decls;

    public ProbeModifyTypeEventJS(Map<String, TypeDecl> decls) {
        this.decls = decls;
    }

    /** Whether this probe run collected the given class (which makes it editable). */
    public boolean hasClass(String fqn) {
        return decls.containsKey(fqn);
    }

    /**
     * Returns an editor for the given class, or {@code null} when the class is outside this probe run's collection
     * (scripts should null-check).
     */
    public ClassEditor forClass(String fqn) {
        TypeDecl d = decls.get(fqn);
        return d == null ? null : new ClassEditor(d);
    }

    // ------------------------------------------------------------------
    //  Type building helpers
    // ------------------------------------------------------------------

    /** Resolves a type descriptor (string or ApiTypeRef) to an {@link ApiTypeRef}. */
    public ApiTypeRef type(Object desc) {
        return resolveType(desc);
    }

    /** Array type. */
    public ApiTypeRef array(Object element) {
        return ApiTypeRef.array(resolveType(element));
    }

    /** Union type (two or more members). */
    public ApiTypeRef union(Object a, Object b, Object... rest) {
        List<ApiTypeRef> members = new ArrayList<>();
        members.add(resolveType(a));
        members.add(resolveType(b));
        for (Object r : rest) members.add(resolveType(r));
        return ApiTypeRef.union(members);
    }

    /** Turns a string or an ApiTypeRef into an ApiTypeRef (shared by the edit methods). */
    static ApiTypeRef resolveType(Object desc) {
        if (desc instanceof ApiTypeRef ref) return ref;
        if (desc instanceof String s) {
            if (s.isBlank()) throw new IllegalArgumentException("type must not be blank");
            if (s.indexOf('.') >= 0) return ApiTypeRef.symbol(new ApiSymbolId("java", s));
            return ApiTypeRef.primitive(s);
        }
        throw new IllegalArgumentException("type must be a String or ApiTypeRef, got: "
                + (desc == null ? "null" : desc.getClass().getName()));
    }

    /** Overrides a type slot with a new type: keeps the original sourceType as a record and sets
     *  overridden=true to force ref rendering. */
    static TypeSlot override(TypeSlot original, Object newType) {
        ApiTypeRef ref = resolveType(newType);
        TypeSlot s = new TypeSlot(original == null ? null : original.sourceType, ref);
        s.overridden = true;
        return s;
    }

    // ------------------------------------------------------------------
    //  Import collection (backends merge these when replacing the declaration cache)
    // ------------------------------------------------------------------

    /**
     * Collects the SYMBOL fully qualified names referenced by edited (overridden or synthesized) slots, so
     * {@code IndexFileGenerator} can merge the imports. Types in the same package are dropped because they would
     * import themselves; untouched members are covered by the reflection-side import collection and need no
     * handling here.
     */
    public static Set<String> collectEditedSymbolFqns(TypeDecl d, String classPackage) {
        Set<String> out = new LinkedHashSet<>();
        if (d.superType != null && d.superType.overridden) collectSyms(d.superType.ref, out, classPackage);
        for (TypeSlot s : d.interfaces) {
            if (s.overridden) collectSyms(s.ref, out, classPackage);
        }
        for (MethodDecl c : d.constructors) collectEditedParams(c, out, classPackage);
        for (MethodDecl m : d.methods) {
            if (m.returnType != null && m.returnType.overridden) collectSyms(m.returnType.ref, out, classPackage);
            if (m.setterParamType != null && m.setterParamType.overridden) collectSyms(m.setterParamType.ref, out, classPackage);
            collectEditedParams(m, out, classPackage);
        }
        for (FieldDecl f : d.fields) {
            if (f.type != null && f.type.overridden) collectSyms(f.type.ref, out, classPackage);
        }
        return out;
    }

    private static void collectEditedParams(MethodDecl m, Set<String> out, String pkg) {
        for (MethodDecl.MethodParam p : m.params) {
            if (p.type != null && p.type.overridden) collectSyms(p.type.ref, out, pkg);
        }
    }

    private static void collectSyms(ApiTypeRef ref, Set<String> out, String classPackage) {
        if (ref == null) return;
        switch (ref.kind()) {
            case SYMBOL -> {
                String name = ref.name();                 // Looks like "java:net.minecraft.Foo"
                int colon = name.indexOf(':');
                String fqn = colon >= 0 ? name.substring(colon + 1) : name;
                int dot = fqn.lastIndexOf('.');
                String pkg = dot >= 0 ? fqn.substring(0, dot) : "";
                if (!pkg.equals(classPackage)) out.add(fqn);   // Drop same-package self-imports
            }
            case ARRAY -> collectSyms(ref.arguments().get(0), out, classPackage);
            case UNION -> {
                for (ApiTypeRef a : ref.arguments()) collectSyms(a, out, classPackage);
            }
            default -> { /* PRIMITIVE, VOID, TYPE_VARIABLE and CALLBACK produce no SYMBOL import */ }
        }
    }
}
