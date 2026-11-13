package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.surface.ApiTypeRef;

import java.util.ArrayList;
import java.util.List;

/**
 * A language-neutral type declaration node (mutable). {@code TypeReflector} builds it by
 * reflecting a Java {@code Class<?>}. The {@code TypeScriptClassRenderer} (TS) and the Python
 * renderer consume it, and the {@code probe.modify_type} event edits it in place.
 *
 * <p>Fields are exposed directly, readable by the package and the rest of the IR subsystem.
 * Naming, such as the TS {@code $Parent$Child} form, is computed by each renderer from
 * {@link #sourceClass}; this IR fixes no language-specific names.
 */
public final class TypeDecl {
    public enum Kind { CLASS, INTERFACE, ENUM }

    public Kind kind;
    public final Class<?> sourceClass;     // Reflected source class; null for purely synthetic declarations
    public final String fqn;               // Fully qualified name: sourceClass.getName(), or a synthetic name
    public final List<TypeParam> typeParams = new ArrayList<>();   // Class-level type parameters, such as <T extends Bound>
    public TypeSlot superType;             // Superclass; null means Object or none
    public final List<TypeSlot> interfaces = new ArrayList<>();
    public final List<MethodDecl> constructors = new ArrayList<>();
    public final List<MethodDecl> methods = new ArrayList<>();     // Includes getters and setters, told apart by their flags
    public final List<FieldDecl> fields = new ArrayList<>();
    public final List<String> docs = new ArrayList<>();
    /** Native member groups unavailable to reflection; retained declarations remain explicitly incomplete. */
    public final List<String> memberOmissions = new ArrayList<>();
    public boolean hidden;
    /**
     * Renamed class name; null means use the name from sourceClass. Set by {@code renameClass} in
     * {@code probe.modify_type}. Renderers prefer it when emitting the class, interface or enum
     * name; TS output keeps the {@code $} prefix.
     */
    public String renameTo;
    /**
     * Whether a {@code probe.modify_type} event has touched this declaration. Any edit through
     * {@code ClassEditor} sets it to true. The probe backend uses it to decide which classes go
     * through the renderer again and overwrite the cached declaration. Untouched classes keep the
     * existing path.
     */
    public boolean mutated;

    public TypeDecl(Kind kind, Class<?> sourceClass, String fqn) {
        this.kind = kind;
        this.sourceClass = sourceClass;
        this.fqn = fqn;
    }

    /** Class name used when rendering: renameTo when set, otherwise the sourceClass name. */
    public String effectiveTypeName() {
        return renameTo;
    }

    /** Graal reads a same-name Bean getter before the public instance field. */
    public MethodDecl beanGetterForField(FieldDecl field) {
        if (field.hidden || field.isStatic) return null;
        return methods.stream().filter(method -> !method.hidden && method.isGetter
                && field.effectiveName().equals(method.property)).findFirst().orElse(null);
    }

    /** A Bean setter owns writes when present; otherwise a non-final field remains writable. */
    public TypeSlot beanFieldWriteType(FieldDecl field, MethodDecl getter) {
        return getter.setterParamType != null ? getter.setterParamType : field.isFinal ? null : field.type;
    }

    /** A class type parameter with its reflected intersection bounds and editable legacy constraint. */
    public static final class TypeParam {
        public final String name;
        public TypeSlot bound;       // null means no upper bound
        private final TypeSlot originalBound;
        private final ApiTypeRef originalBoundRef;
        private final List<TypeSlot> additionalBounds;

        public TypeParam(String name, TypeSlot bound) {
            this(name, bound, List.of());
        }

        TypeParam(String name, TypeSlot bound, List<TypeSlot> additionalBounds) {
            this.name = name;
            this.bound = bound;
            this.originalBound = bound;
            this.originalBoundRef = bound == null ? null : bound.ref;
            this.additionalBounds = List.copyOf(additionalBounds);
        }

        /**
         * Returns the effective constraint. Replacing, clearing or editing the legacy bound replaces
         * the whole constraint, so reflected secondary bounds cannot reappear after an explicit edit.
         */
        public List<TypeSlot> bounds() {
            if (bound == null) return List.of();
            if (additionalBounds.isEmpty() || bound != originalBound || bound.overridden
                    || !java.util.Objects.equals(bound.ref, originalBoundRef)) return List.of(bound);
            List<TypeSlot> bounds = new ArrayList<>();
            bounds.add(bound);
            bounds.addAll(additionalBounds);
            return List.copyOf(bounds);
        }
    }
}
