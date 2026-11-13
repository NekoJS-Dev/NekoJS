package com.tkisor.nekojs.probe.events;

import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.ir.FieldDecl;
import com.tkisor.nekojs.probe.ir.MethodDecl;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeSlot;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code probe.assign_type} event: redirects types globally. Scripts call {@link #assign(String, Object)} to
 * register "Java fully qualified name -> custom type" mappings, and after building the IR the coordinator uses
 * {@link #applyTo} to apply them to the type slots of every TypeDecl: wherever a SYMBOL references an assigned
 * FQN, its {@code ref} is replaced, {@code overridden} is set and the owning TypeDecl is marked {@code mutated}.
 *
 * <p>Effect: TS re-renders the classes that were touched (an assigned slot takes the renderRef path and shows the
 * new type); Python sees the new type directly, because it renders every class.
 * {@code assign_type} changes the type produced by reflection; if {@code modify_type} sets a type afterwards, that
 * later value wins because it overrides explicitly.
 *
 * <p>Requires the IR: an assign_type listener makes the coordinator build the IR (counted in {@code needIr}).
 */
public final class ProbeAssignTypeEventJS {
    private final Map<String, ApiTypeRef> assignments = new LinkedHashMap<>();

    /** Registers a redirect: rewrites every occurrence of the Java fully qualified name {@code javaFqn} to
     *  {@code typeDesc} (a string or an ApiTypeRef). */
    public void assign(String javaFqn, Object typeDesc) {
        if (javaFqn == null || javaFqn.isBlank()) return;
        assignments.put(javaFqn, ProbeModifyTypeEventJS.resolveType(typeDesc));
    }

    public boolean has(String javaFqn) {
        return assignments.containsKey(javaFqn);
    }

    public Map<String, ApiTypeRef> assignments() {
        return assignments;
    }

    /**
     * Applies the mappings to every type slot of a TypeDecl. Returns the number of replacements (0 means this
     * class has no affected slot). Each replaced slot marks its owning TypeDecl {@code mutated} so TS re-renders it.
     */
    public static int applyTo(TypeDecl d, Map<String, ApiTypeRef> map) {
        if (map == null || map.isEmpty() || d == null) return 0;
        int count = 0;
        if (d.superType != null) count += applySlot(d, d.superType, map);
        for (TypeSlot s : d.interfaces) count += applySlot(d, s, map);
        for (MethodDecl c : d.constructors) count += applyMethod(d, c, map);
        for (MethodDecl m : d.methods) count += applyMethod(d, m, map);
        for (FieldDecl f : d.fields) if (f.type != null) count += applySlot(d, f.type, map);
        for (TypeDecl.TypeParam parameter : d.typeParams) {
            for (TypeSlot bound : parameter.bounds()) count += applySlot(d, bound, map);
        }
        return count;
    }

    private static int applyMethod(TypeDecl d, MethodDecl m, Map<String, ApiTypeRef> map) {
        int c = 0;
        if (m.returnType != null) c += applySlot(d, m.returnType, map);
        if (m.setterParamType != null) c += applySlot(d, m.setterParamType, map);
        for (MethodDecl.MethodParam p : m.params) if (p.type != null) c += applySlot(d, p.type, map);
        for (var bounds : m.typeParameterBounds.values()) {
            for (TypeSlot bound : bounds) c += applySlot(d, bound, map);
        }
        return c;
    }

    private static int applySlot(TypeDecl d, TypeSlot slot, Map<String, ApiTypeRef> map) {
        if (slot.ref == null || slot.ref.kind() != ApiTypeRef.Kind.SYMBOL) return 0;
        String fqn = extractFqn(slot.ref.name());
        ApiTypeRef replacement = map.get(fqn);
        if (replacement == null) return 0;
        slot.ref = replacement;
        slot.overridden = true;
        d.mutated = true;
        return 1;
    }

    private static String extractFqn(String symbolName) {
        int colon = symbolName.indexOf(':');
        return colon >= 0 ? symbolName.substring(colon + 1) : symbolName;
    }
}
