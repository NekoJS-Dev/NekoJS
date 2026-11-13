package com.tkisor.nekojs.probe.ir;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutable method metadata shared by declaration backends, including constructors and Bean accessors.
 * Accessor flags retain the real method name and the optional Bean property's read/write types.
 * Each backend decides how to represent these aliases alongside real fields and overloads.
 */
public final class MethodDecl {
    /** Retains Java declaration identity when edits change parameters or inherited overloads. */
    public java.lang.reflect.Method sourceMethod;
    /** Distinguishes intentional edits of inherited copies from reflection's unedited overload copies. */
    public boolean explicitlyEdited;
    public String name;
    public String renameTo;             // null = use name
    public final List<MethodParam> params = new ArrayList<>();
    public TypeSlot returnType;         // may be null for a constructor
    public final List<String> typeParams = new ArrayList<>();
    /** Bound slots retain import dependencies for method type parameter declarations. */
    public final Map<String, List<TypeSlot>> typeParameterBounds = new LinkedHashMap<>();
    public boolean isStatic;
    public boolean isConstructor;
    public boolean isGetter;
    public boolean isSetter;
    public String property;             // property name for the getter/setter
    public TypeSlot setterParamType;    // type of the setter parameter paired with the getter; null = no setter
    public boolean hidden;
    public final List<String> docs = new ArrayList<>();
    /** A hand-written overload (from the {@code @Overload} annotation), rendered as an additional
     *  declaration with the same name. */
    public final List<Overload> overloads = new ArrayList<>();

    public MethodDecl(String name) {
        this.name = name;
    }

    /**
     * One hand-written overload declaration. Its parameter entries and return type are TypeScript fragments
     * emitted verbatim ({@code name: Type}); they do not go through the {@link TypeSlot} reflection chain,
     * because these signatures describe positions reflection cannot express.
     */
    public static final class Overload {
        public final List<String> params;
        /** Empty string = keep the method's reflected return type (a constructor has no return value). */
        public final String returns;
        public final List<String> docs;

        public Overload(List<String> params, String returns, List<String> docs) {
            this.params = List.copyOf(params);
            this.returns = returns == null ? "" : returns;
            this.docs = List.copyOf(docs);
        }
    }

    /** Name used when rendering; renameTo takes precedence when set. */
    public String effectiveName() {
        return renameTo != null ? renameTo : name;
    }

    /** A method parameter. Mutable so modify_type can edit individual parameters. */
    public static final class MethodParam {
        public String name;
        public TypeSlot type;
        public boolean varargs;
        /** Optional TypeScript parameter, rendered as {@code name?: type}; markOptional in modify_type sets it. */
        public boolean optional;

        public MethodParam(String name, TypeSlot type, boolean varargs) {
            this.name = name;
            this.type = type;
            this.varargs = varargs;
        }
    }
}
