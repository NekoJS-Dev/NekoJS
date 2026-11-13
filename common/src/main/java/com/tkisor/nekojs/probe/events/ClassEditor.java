package com.tkisor.nekojs.probe.events;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.probe.ir.FieldDecl;
import com.tkisor.nekojs.probe.ir.MethodDecl;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Chainable editor for a single {@link TypeDecl} in the {@code probe.modify_type} event. You get one from
 * {@link ProbeModifyTypeEventJS#forClass(String)}. Any edit marks the owning TypeDecl as
 * {@code mutated}, and the probe backend then re-renders only those classes and overwrites their cached
 * declarations.
 *
 * <p><b>Addressing by method name</b>: an operation by name applies to every method or constructor of
 * that class with the same name, overloads included, which matches the "rename the whole class"
 * semantics. When no target member exists the call is a silent no-op that only logs at DEBUG, so you can
 * check {@link #hasMethod}/{@link #hasField} first.
 *
 * <p><b>Type arguments</b>: a type argument is either a string or an
 * {@link com.tkisor.nekojs.api.surface.ApiTypeRef}. A string containing {@code .} is treated as a Java
 * fully qualified name (SYMBOL); any other string is treated as a primitive type name
 * ({@code "string"}/{@code "number"}/{@code "boolean"} ...). Build complex types with
 * {@link ProbeModifyTypeEventJS#array}/{@link ProbeModifyTypeEventJS#union} and pass the result.
 */
public final class ClassEditor {
    private final TypeDecl decl;

    ClassEditor(TypeDecl decl) {
        this.decl = decl;
    }

    private void touched() {
        decl.mutated = true;
    }

    // ==================== Class level ====================

    /** Hides the whole class (rendered as an empty declaration). */
    public ClassEditor hide() {
        touched();
        decl.hidden = true;
        return this;
    }

    /** Replaces the class documentation. */
    public ClassEditor setDoc(String doc) {
        touched();
        decl.docs.clear();
        if (doc != null) decl.docs.add(doc);
        return this;
    }

    /** Renames the class: the TS/Python output uses the new name, and an enum's self-references are renamed too. */
    public ClassEditor renameClass(String newName) {
        touched();
        decl.renameTo = newName;
        return this;
    }

    /** Changes the superclass type. */
    public ClassEditor changeSuper(Object type) {
        touched();
        decl.superType = ProbeModifyTypeEventJS.override(decl.superType, type);
        return this;
    }

    // ==================== Method level ====================

    public boolean hasMethod(String name) {
        return !matchingMethods(name).isEmpty();
    }

    /** Renames a method (affects every overload with that name). */
    public ClassEditor renameMethod(String name, String newName) {
        return editMethods(name, m -> m.renameTo = newName);
    }

    /** Hides a method (all overloads with that name). */
    public ClassEditor hideMethod(String name) {
        return editMethods(name, m -> m.hidden = true);
    }

    /** Replaces a method's documentation (all overloads with that name). */
    public ClassEditor setMethodDoc(String name, String doc) {
        return editMethods(name, m -> {
            m.docs.clear();
            if (doc != null) m.docs.add(doc);
        });
    }

    /** Changes a method's return type (all overloads with that name). */
    public ClassEditor changeReturnType(String name, Object type) {
        return editMethods(name, m -> m.returnType = ProbeModifyTypeEventJS.override(m.returnType, type));
    }

    /** Changes the type of the parameter at the given position (every overload with that name that has a parameter there). */
    public ClassEditor changeParamType(String name, int index, Object type) {
        return editMethods(name, m -> index >= 0 && index < m.params.size(), m -> {
            if (index >= 0 && index < m.params.size()) {
                var p = m.params.get(index);
                p.type = ProbeModifyTypeEventJS.override(p.type, type);
            }
        });
    }

    /** Changes a parameter type by name (every parameter with that name in all overloads). */
    public ClassEditor changeParamType(String name, String paramName, Object type) {
        return editMethods(name, m -> m.params.stream().anyMatch(p -> paramName.equals(p.name)), m -> {
            for (var p : m.params) {
                if (paramName.equals(p.name)) {
                    p.type = ProbeModifyTypeEventJS.override(p.type, type);
                }
            }
        });
    }

    /** Renames a method parameter by name. */
    public ClassEditor renameParam(String name, String paramName, String newName) {
        return editMethods(name, m -> m.params.stream().anyMatch(p -> paramName.equals(p.name)), m -> {
            for (var p : m.params) if (paramName.equals(p.name)) p.name = newName;
        });
    }

    /** Removes a method parameter by index. */
    public ClassEditor removeParam(String name, int index) {
        return editMethods(name, m -> index >= 0 && index < m.params.size(), m -> {
            if (index >= 0 && index < m.params.size()) m.params.remove(index);
        });
    }

    /**
     * Marks the named parameter as optional in TypeScript (rendered as {@code name?: type}). Optional
     * parameters must come last: if a required parameter follows, the signature would be invalid, as in
     * {@code (a: string, b?: number, c: string)}, so this throws {@link IllegalStateException}
     * immediately. Varargs count as optional too (rendered as {@code name?: T[]}).
     */
    public ClassEditor markOptional(String name, String paramName) {
        return editMethods(name, m -> m.params.stream().anyMatch(p -> paramName.equals(p.name)), m -> {
            for (var p : m.params) if (paramName.equals(p.name)) p.optional = true;
            validateOptionalParamsTrail(m, "markOptional('" + name + "', '" + paramName + "')");
        });
    }

    /** Appends a parameter to the end of the method (all overloads with that name). The new parameter
     *  is required: if an optional or varargs parameter already comes before it, the signature would be
     *  invalid, so this throws {@link IllegalStateException} immediately. */
    public ClassEditor addParam(String name, String paramName, Object type) {
        return editMethods(name, m -> {
            TypeSlot slot = ProbeModifyTypeEventJS.override(null, type);
            m.params.add(new MethodDecl.MethodParam(paramName, slot, false));
            validateOptionalParamsTrail(m, "addParam('" + name + "', '" + paramName + "')");
        });
    }

    /**
     * Checks that no required parameter follows an optional one (varargs count as optional, rendered as
     * {@code name?: T[]}). Failing during the edit beats silently emitting a declaration that neither TS
     * nor Python can parse. The outer try/catch of the probe.modify_type event records the exception as
     * an error and the game keeps running.
     */
    private void validateOptionalParamsTrail(MethodDecl m, String op) {
        boolean seenOptional = false;
        for (var p : m.params) {
            if (p.optional || p.varargs) {
                seenOptional = true;
            } else if (seenOptional) {
                throw new IllegalStateException("probe.modify_type " + op + " on " + decl.fqn
                        + " would place required parameter '" + p.name + "' of method '" + m.name
                        + "' after an optional parameter; optional parameters must be trailing");
            }
        }
    }

    /**
     * Adds an instance method. Each {@code paramSpecs} entry is either {@code "type"} (the parameter name
     * becomes arg0/arg1...) or {@code "name:type"} (split at the first ':' ; without a ':' the text is a
     * plain type and the name is generated). Parameter names with illegal characters are not validated
     * because the host trusts the caller, but an empty name or empty type throws IllegalArgumentException.
     * A null {@code paramSpecs} means no parameters.
     */
    public ClassEditor addMethod(String name, Object returnType, Object... paramSpecs) {
        return addMethodInternal(name, returnType, false, paramSpecs);
    }

    /** Adds a static method (same semantics as {@link #addMethod}, with isStatic = true). */
    public ClassEditor addStaticMethod(String name, Object returnType, Object... paramSpecs) {
        return addMethodInternal(name, returnType, true, paramSpecs);
    }

    private ClassEditor addMethodInternal(String name, Object returnType, boolean isStatic, Object[] paramSpecs) {
        touched();
        MethodDecl m = new MethodDecl(name);
        m.isStatic = isStatic;
        m.returnType = ProbeModifyTypeEventJS.override(null, returnType);
        if (paramSpecs != null) {
            for (int i = 0; i < paramSpecs.length; i++) {
                Object spec = paramSpecs[i];
                if (!(spec instanceof String text)) {
                    throw new IllegalArgumentException("param spec must be a String, got: "
                            + (spec == null ? "null" : spec.getClass().getName()));
                }
                // Split at the first ':' ; without a ':' the text is a plain type and the name becomes arg<index>
                int colon = text.indexOf(':');
                String pName;
                String pType;
                if (colon >= 0) {
                    pName = text.substring(0, colon);
                    pType = text.substring(colon + 1);
                } else {
                    pName = "arg" + i;
                    pType = text;
                }
                if (pName.isEmpty()) {
                    throw new IllegalArgumentException("param name must not be empty: '" + text + "'");
                }
                if (pType.isBlank()) {
                    throw new IllegalArgumentException("param type must not be blank: '" + text + "'");
                }
                TypeSlot slot = ProbeModifyTypeEventJS.override(null, pType);
                m.params.add(new MethodDecl.MethodParam(pName, slot, false));
            }
        }
        decl.methods.add(m);
        return this;
    }

    // ==================== Field level ====================

    public boolean hasField(String name) {
        for (FieldDecl f : decl.fields) if (f.name.equals(name)) return true;
        return false;
    }

    public ClassEditor renameField(String name, String newName) {
        return editField(name, f -> f.renameTo = newName);
    }

    public ClassEditor hideField(String name) {
        return editField(name, f -> f.hidden = true);
    }

    public ClassEditor changeFieldType(String name, Object type) {
        return editField(name, f -> f.type = ProbeModifyTypeEventJS.override(f.type, type));
    }

    public ClassEditor setFieldDoc(String name, String doc) {
        return editField(name, f -> {
            f.docs.clear();
            if (doc != null) f.docs.add(doc);
        });
    }

    // ==================== Internals ====================

    private List<MethodDecl> matchingMethods(String name) {
        List<MethodDecl> out = new ArrayList<>();
        for (MethodDecl m : decl.methods) if (m.name.equals(name)) out.add(m);
        for (MethodDecl c : decl.constructors) if (c.name.equals(name)) out.add(c);
        return out;
    }

    private ClassEditor editMethods(String name, Consumer<MethodDecl> op) {
        return editMethods(name, method -> true, op);
    }

    private ClassEditor editMethods(String name, Predicate<MethodDecl> hasTarget, Consumer<MethodDecl> op) {
        List<MethodDecl> matches = matchingMethods(name);
        if (matches.isEmpty()) {
            NekoJS.LOGGER.debug("probe.modify_type: method '{}' not found in {}", name, decl.fqn);
            return this;
        }
        touched();
        boolean matched = false;
        for (MethodDecl m : matches) {
            // Missing parameter selectors remain no-ops, while existing edit validation still runs.
            if (hasTarget.test(m)) {
                m.explicitlyEdited = true;
                matched = true;
            }
            op.accept(m);
        }
        if (!matched) {
            NekoJS.LOGGER.debug("probe.modify_type: no overload of '{}' in {} matches the selector", name, decl.fqn);
        }
        return this;
    }

    private ClassEditor editField(String name, Consumer<FieldDecl> op) {
        FieldDecl found = null;
        for (FieldDecl f : decl.fields) if (f.name.equals(name)) { found = f; break; }
        if (found == null) {
            NekoJS.LOGGER.debug("probe.modify_type: field '{}' not found in {}", name, decl.fqn);
            return this;
        }
        touched();
        op.accept(found);
        return this;
    }
}
